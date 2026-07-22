package tv.kaya.turksat;

import android.annotation.SuppressLint;
import android.app.PictureInPictureParams;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Rational;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.PermissionRequest;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@UnstableApi
public final class WebPlayerActivity extends AppCompatActivity {
    private static final String EXTRA_URL = "channel_web_url";
    private static final String EXTRA_FALLBACK_URL = "channel_fallback_url";
    private static final String EXTRA_NAME = "channel_name";
    private static final String EXTRA_CHANNEL_DELTA = "channel_delta";
    private static final String EXTRA_PLAYBACK_FAILED = "playback_failed";
    private static final String LOADING_TEXT = "Yayın yükleniyor…";
    private static final String PLAYBACK_RECOVERY_SCRIPT =
            "(function(){try{"
                    + "if(typeof showGame==='function'){showGame();}"
                    + "else if(typeof after_ads==='function'){after_ads();}"
                    + "var buttons=document.querySelectorAll('.vjs-big-play-button,.jw-icon-playback,"
                    + "#play-overlay,.play-button,[aria-label=\"Play\"]');"
                    + "for(var i=0;i<buttons.length;i++){try{buttons[i].click();}catch(e){}}"
                    + "var videos=document.querySelectorAll('video');"
                    + "for(var j=0;j<videos.length;j++){try{var v=videos[j];"
                    + "v.setAttribute('playsinline','');v.muted=false;var p=v.play();"
                    + "if(p&&p.catch){p.catch(function(){});}}catch(e){}}"
                    + "}catch(e){}})();";

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService resolverExecutor = Executors.newSingleThreadExecutor();

    private FrameLayout root;
    private WebView webView;
    private PlayerView nativePlayerView;
    private ExoPlayer nativePlayer;
    private ProgressBar progress;
    private TextView loadingLabel;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private String sourceUrl;
    private String fallbackUrl;
    private String currentUrl;
    private boolean sourceWrapperAttempted;
    private boolean fallbackAttempted;
    private boolean destroyed;
    private boolean nativePlaybackStarted;
    private int playbackRecoveryCount;

    static Intent createIntent(Context context, Channel channel) {
        String url = channel.isDirectStream() ? channel.pageUrl : channel.playbackUrl;
        return new Intent(context, WebPlayerActivity.class)
                .putExtra(EXTRA_URL, url)
                .putExtra(EXTRA_FALLBACK_URL, channel.pageUrl)
                .putExtra(EXTRA_NAME, channel.name);
    }

    static int channelDelta(Intent result) {
        return result == null ? 0 : result.getIntExtra(EXTRA_CHANNEL_DELTA, 0);
    }

    static boolean playbackFailed(Intent result) {
        return result != null && result.getBooleanExtra(EXTRA_PLAYBACK_FAILED, false);
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sourceUrl = getIntent().getStringExtra(EXTRA_URL);
        fallbackUrl = getIntent().getStringExtra(EXTRA_FALLBACK_URL);
        if (!isAllowedTopLevelUrl(fallbackUrl) || fallbackUrl.equals(sourceUrl)) {
            fallbackUrl = null;
        }
        if (!isAllowedTopLevelUrl(sourceUrl)) {
            finishWithPlaybackFailure("web/invalid-url", "Güvenli kanal adresi bulunamadı");
            return;
        }

        try {
            initializePlayerUi();
            String channelName = getIntent().getStringExtra(EXTRA_NAME);
            setTitle(channelName == null ? getString(R.string.app_name) : channelName);
            resolveAndLoad(sourceUrl);
        } catch (Throwable unavailable) {
            AppDiagnostics.record(this, "web/init", unavailable);
            finishWithPlaybackFailure("web/init", "Yayın bu cihazda başlatılamadı");
        }
    }

    private void initializePlayerUi() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        enterImmersiveMode();
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(false);
        progress.setMax(100);
        Drawable progressDrawable = progress.getProgressDrawable();
        if (progressDrawable != null) {
            progressDrawable.setTint(0xffe30a17);
        }

        loadingLabel = new TextView(this);
        loadingLabel.setText(LOADING_TEXT);
        loadingLabel.setTextColor(Color.WHITE);
        loadingLabel.setTextSize(18);
        loadingLabel.setGravity(Gravity.CENTER);
        loadingLabel.setBackgroundColor(0xcc090d14);
        loadingLabel.setPadding(dp(24), dp(14), dp(24), dp(14));

        FrameLayout.LayoutParams labelParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        labelParams.gravity = Gravity.CENTER;
        root.addView(loadingLabel, labelParams);

        FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(5));
        progressParams.gravity = Gravity.TOP;
        root.addView(progress, progressParams);
        setContentView(root);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void ensureWebView() {
        if (webView != null) {
            return;
        }
        webView = new WebView(this);
        webView.setBackgroundColor(Color.BLACK);
        webView.setFocusable(true);
        webView.setFocusableInTouchMode(true);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        settings.setSupportMultipleWindows(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setUserAgentString(settings.getUserAgentString() + " TurkiyeCanliTV/"
                + BuildConfig.VERSION_NAME);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            settings.setSafeBrowsingEnabled(true);
        }

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(webView, true);
        webView.setWebViewClient(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Api26SafePlayerClient() : new SafePlayerClient());
        webView.setWebChromeClient(new PlayerChromeClient());
        root.addView(webView, 0, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void resolveAndLoad(String url) {
        showLoading(true);
        if (!isCanliTvPlayerWrapper(url)) {
            loadTrustedUrl(url);
            return;
        }
        resolverExecutor.execute(() -> {
            String resolved = ChannelRepository.resolveWebPlayerUrl(url, fallbackUrl);
            mainHandler.post(() -> {
                if (destroyed || isFinishing()) {
                    return;
                }
                String directMedia = ChannelRepository.extractNestedMediaUrl(resolved);
                if (directMedia != null) {
                    startNativePlayback(directMedia);
                } else if (isAllowedTopLevelUrl(resolved) && !url.equals(resolved)) {
                    loadTrustedUrl(resolved);
                } else {
                    sourceWrapperAttempted = true;
                    AppDiagnostics.record(this, "web/resolve",
                            "Doğrudan yayın hedefi bulunamadı; kaynak sayfa deneniyor");
                    loadTrustedUrl(url);
                }
            });
        });
    }

    private void loadTrustedUrl(String url) {
        if (destroyed || isFinishing() || !isAllowedTopLevelUrl(url)) {
            return;
        }
        try {
            releaseNativePlayer();
            ensureWebView();
            currentUrl = url;
            showLoading(true);
            Map<String, String> headers = new HashMap<>();
            String referer = isAllowedTopLevelUrl(sourceUrl) ? sourceUrl : fallbackUrl;
            if (referer != null && !referer.equals(url)) {
                headers.put("Referer", referer);
            }
            webView.loadUrl(url, headers);
            webView.requestFocus();
        } catch (Throwable failure) {
            AppDiagnostics.record(this, "web/load", failure);
            handleMainFrameFailure(url);
        }
    }

    private void startNativePlayback(String mediaUrl) {
        if (destroyed || isFinishing() || root == null) {
            return;
        }
        try {
            destroyWebViewSafely();
            releaseNativePlayer();
            currentUrl = mediaUrl;
            nativePlaybackStarted = true;
            showLoading(true);

            Map<String, String> headers = new HashMap<>();
            headers.put("Referer", sourceUrl);
            DefaultHttpDataSource.Factory dataSourceFactory = new DefaultHttpDataSource.Factory()
                    .setUserAgent("Mozilla/5.0 (Linux; Android 12; Android TV) "
                            + "AppleWebKit/537.36 (KHTML, like Gecko) "
                            + "Chrome/138.0.0.0 Safari/537.36")
                    .setAllowCrossProtocolRedirects(true)
                    .setDefaultRequestProperties(headers);
            nativePlayer = new ExoPlayer.Builder(this)
                    .setMediaSourceFactory(new DefaultMediaSourceFactory(dataSourceFactory))
                    .build();
            nativePlayerView = new PlayerView(this);
            nativePlayerView.setBackgroundColor(Color.BLACK);
            nativePlayerView.setUseController(true);
            nativePlayerView.setPlayer(nativePlayer);
            root.addView(nativePlayerView, 0, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            nativePlayer.addListener(new Player.Listener() {
                @Override
                public void onPlaybackStateChanged(int playbackState) {
                    if (playbackState == Player.STATE_READY) {
                        showLoading(false);
                    }
                }

                @Override
                public void onPlayerError(PlaybackException error) {
                    AppDiagnostics.record(WebPlayerActivity.this, "native-fallback/playback", error);
                    mainHandler.postDelayed(() -> {
                        if (!destroyed && nativePlayer != null) {
                            releaseNativePlayer();
                            handleMainFrameFailure(mediaUrl);
                        }
                    }, 700L);
                }
            });
            nativePlayer.setMediaItem(MediaItem.fromUri(mediaUrl));
            nativePlayer.prepare();
            nativePlayer.play();
            nativePlayerView.requestFocus();
        } catch (Throwable failure) {
            AppDiagnostics.record(this, "native-fallback/init", failure);
            releaseNativePlayer();
            handleMainFrameFailure(mediaUrl);
        }
    }

    private void releaseNativePlayer() {
        PlayerView view = nativePlayerView;
        nativePlayerView = null;
        if (view != null) {
            view.setPlayer(null);
            if (root != null) {
                root.removeView(view);
            }
        }
        ExoPlayer player = nativePlayer;
        nativePlayer = null;
        if (player != null) {
            try {
                player.release();
            } catch (RuntimeException ignored) {
                // Decoder cleanup differs across Android TV vendors.
            }
        }
    }

    private class SafePlayerClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            Uri uri = request == null ? null : request.getUrl();
            if (request != null && !request.isForMainFrame()) {
                return !isSafeEmbeddedUrl(uri);
            }
            String url = uri == null ? null : uri.toString();
            if (isAllowedTopLevelUrl(url)) {
                currentUrl = url;
                return false;
            }
            handleMainFrameFailure(url);
            return true;
        }

        @SuppressWarnings("deprecation")
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            if (isAllowedTopLevelUrl(url)) {
                currentUrl = url;
                return false;
            }
            // Android 6 does not identify sub-frame navigations here. Block unknown targets
            // without treating an advertisement iframe as a failure of the channel itself.
            return true;
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            if (!isActive(view)) {
                return;
            }
            currentUrl = url;
            showLoading(true);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            if (!isActive(view)) {
                return;
            }
            currentUrl = url;
            runPlaybackRecovery(view, 0L);
            runPlaybackRecovery(view, 1200L);
            runPlaybackRecovery(view, 4000L);
            mainHandler.postDelayed(() -> showLoading(false), 900L);
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request,
                                    WebResourceError error) {
            if (request != null && request.isForMainFrame()) {
                handleMainFrameFailure(request.getUrl() == null
                        ? null : request.getUrl().toString());
            }
        }

        @Override
        public void onReceivedHttpError(WebView view, WebResourceRequest request,
                                        WebResourceResponse errorResponse) {
            if (request != null && request.isForMainFrame() && errorResponse != null
                    && errorResponse.getStatusCode() >= 400) {
                handleMainFrameFailure(request.getUrl() == null
                        ? null : request.getUrl().toString());
            }
        }

        @Override
        public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
            if (handler != null) {
                handler.cancel();
            }
            String failedUrl = error == null ? null : error.getUrl();
            if (isCurrentPage(failedUrl, view == null ? null : view.getUrl())) {
                handleMainFrameFailure(failedUrl);
            } else {
                AppDiagnostics.record(WebPlayerActivity.this, "web/subresource-ssl",
                        failedUrl == null ? "Bilinmeyen alt kaynak" : failedUrl);
            }
        }
    }

    @android.annotation.TargetApi(Build.VERSION_CODES.O)
    private final class Api26SafePlayerClient extends SafePlayerClient {
        @Override
        public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
            AppDiagnostics.record(WebPlayerActivity.this, "web/renderer",
                    "didCrash=" + (detail != null && detail.didCrash()) + ", priority="
                            + (detail == null ? "unknown" : detail.rendererPriorityAtExit()));
            destroyWebViewSafely();
            finishWithPlaybackFailure("web/renderer", "Yayın görüntüsü yeniden başlatılamadı");
            return true;
        }
    }

    private void runPlaybackRecovery(WebView view, long delayMs) {
        mainHandler.postDelayed(() -> {
            if (!isActive(view)) {
                return;
            }
            try {
                playbackRecoveryCount++;
                view.evaluateJavascript(PLAYBACK_RECOVERY_SCRIPT, null);
            } catch (Throwable failure) {
                AppDiagnostics.record(this, "web/recovery", failure);
            }
        }, delayMs);
    }

    private void handleMainFrameFailure(String failedUrl) {
        if (destroyed || isFinishing()) {
            return;
        }
        AppDiagnostics.record(this, "web/main-frame",
                failedUrl == null ? "Bilinmeyen adres" : failedUrl);
        if (!sourceWrapperAttempted && isAllowedTopLevelUrl(sourceUrl)
                && (failedUrl == null || !sourceUrl.equals(failedUrl))) {
            sourceWrapperAttempted = true;
            stopLoadingSafely();
            loadTrustedUrl(sourceUrl);
            return;
        }
        if (!fallbackAttempted && fallbackUrl != null
                && (failedUrl == null || !fallbackUrl.equals(failedUrl))) {
            fallbackAttempted = true;
            stopLoadingSafely();
            loadTrustedUrl(fallbackUrl);
            return;
        }
        if (fallbackAttempted && failedUrl != null && !fallbackUrl.equals(failedUrl)) {
            return;
        }
        finishWithPlaybackFailure("web/main-frame",
                "Yayın şu anda yanıt vermiyor; kanal listede tutuldu");
    }

    private final class PlayerChromeClient extends WebChromeClient {
        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            if (progress == null || destroyed) {
                return;
            }
            progress.setProgress(newProgress);
            progress.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
        }

        @Override
        public void onPermissionRequest(PermissionRequest request) {
            if (request != null) {
                request.deny();
            }
        }

        @Override
        public void onShowCustomView(View view, CustomViewCallback callback) {
            if (destroyed || root == null || webView == null) {
                if (callback != null) {
                    callback.onCustomViewHidden();
                }
                return;
            }
            if (customView != null) {
                if (callback != null) {
                    callback.onCustomViewHidden();
                }
                return;
            }
            customView = view;
            customViewCallback = callback;
            webView.setVisibility(View.GONE);
            root.addView(view, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            enterImmersiveMode();
        }

        @Override
        public void onHideCustomView() {
            hideCustomView();
        }
    }

    private void showLoading(boolean visible) {
        if (loadingLabel != null) {
            loadingLabel.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
        if (progress != null) {
            progress.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    private void hideCustomView() {
        if (customView == null) {
            return;
        }
        if (root != null) {
            root.removeView(customView);
        }
        customView = null;
        if (webView != null) {
            webView.setVisibility(View.VISIBLE);
        }
        if (customViewCallback != null) {
            try {
                customViewCallback.onCustomViewHidden();
            } catch (RuntimeException ignored) {
                // Some vendor WebView versions call this callback more than once.
            }
            customViewCallback = null;
        }
        if (webView != null) {
            webView.requestFocus();
        }
    }

    static boolean isAllowedTopLevelUrl(String value) {
        try {
            Uri uri = Uri.parse(value);
            String host = uri.getHost();
            if (host == null || !"https".equalsIgnoreCase(uri.getScheme())) {
                return false;
            }
            host = host.toLowerCase(Locale.ROOT);
            return "canlitv.diy".equals(host) || "www.canlitv.diy".equals(host)
                    || isPlaybackProviderHost(host);
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean isPlaybackProviderHost(String host) {
        return "youtu.be".equals(host) || host.endsWith(".youtube.com")
                || "youtube.com".equals(host) || host.endsWith(".youtube-nocookie.com")
                || "youtube-nocookie.com".equals(host)
                || host.endsWith(".canlitv.top") || "canlitv.top".equals(host)
                || host.endsWith(".radyolar.top") || "radyolar.top".equals(host)
                || host.endsWith(".hipodrom.com") || "hipodrom.com".equals(host)
                || host.endsWith(".yoltv.com") || "yoltv.com".equals(host)
                || host.endsWith(".castr.net") || "castr.net".equals(host)
                || host.endsWith(".castr.com") || "castr.com".equals(host)
                || host.endsWith(".maksnet.tv") || "maksnet.tv".equals(host);
    }

    private static boolean isCanliTvPlayerWrapper(String value) {
        try {
            Uri uri = Uri.parse(value);
            String host = uri.getHost();
            String path = uri.getPath();
            return host != null && path != null
                    && ("canlitv.diy".equalsIgnoreCase(host)
                    || "www.canlitv.diy".equalsIgnoreCase(host))
                    && path.toLowerCase(Locale.ROOT).contains("/player/index.php");
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean isCurrentPage(String first, String second) {
        try {
            Uri firstUri = Uri.parse(first);
            Uri secondUri = Uri.parse(second);
            return firstUri.getHost() != null && secondUri.getHost() != null
                    && firstUri.getHost().equalsIgnoreCase(secondUri.getHost())
                    && firstUri.getPath() != null && secondUri.getPath() != null
                    && firstUri.getPath().equals(secondUri.getPath());
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean isSafeEmbeddedUrl(Uri uri) {
        if (uri == null || uri.getScheme() == null) {
            return false;
        }
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        return "https".equals(scheme) || "data".equals(scheme)
                || "blob".equals(scheme) || "about".equals(scheme);
    }

    private boolean isActive(WebView view) {
        return !destroyed && !isFinishing() && view != null && view == webView;
    }

    private void stopLoadingSafely() {
        if (webView != null) {
            try {
                webView.stopLoading();
            } catch (RuntimeException ignored) {
                // Renderer may already be shutting down.
            }
        }
    }

    private void finishWithPlaybackFailure(String area, String message) {
        AppDiagnostics.record(this, area, message);
        if (!isFinishing()) {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            setResult(RESULT_CANCELED,
                    new Intent().putExtra(EXTRA_PLAYBACK_FAILED, true));
            finish();
        }
    }

    private void enterImmersiveMode() {
        if (getWindow() == null) {
            return;
        }
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    int playbackRecoveryCountForTest() {
        return playbackRecoveryCount;
    }

    String currentPlayerUrlForTest() {
        return currentUrl;
    }

    boolean nativePlaybackStartedForTest() {
        return nativePlaybackStarted;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (event.getKeyCode() == KeyEvent.KEYCODE_CHANNEL_UP) {
                finishForChannelChange(1);
                return true;
            }
            if (event.getKeyCode() == KeyEvent.KEYCODE_CHANNEL_DOWN) {
                finishForChannelChange(-1);
                return true;
            }
            if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
                if (customView != null) {
                    hideCustomView();
                    return true;
                }
                if (webView != null && webView.canGoBack()) {
                    webView.goBack();
                    return true;
                }
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private void finishForChannelChange(int delta) {
        setResult(RESULT_OK, new Intent().putExtra(EXTRA_CHANNEL_DELTA, delta));
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        enterImmersiveMode();
        if (webView != null) {
            webView.onResume();
        }
        if (nativePlayer != null) {
            nativePlayer.play();
        }
    }

    @Override
    protected void onPause() {
        if (webView != null && (Build.VERSION.SDK_INT < Build.VERSION_CODES.O
                || !isInPictureInPictureMode())) {
            webView.onPause();
        }
        if (nativePlayer != null && (Build.VERSION.SDK_INT < Build.VERSION_CODES.O
                || !isInPictureInPictureMode())) {
            nativePlayer.pause();
        }
        super.onPause();
    }

    @Override
    public void onUserLeaveHint() {
        super.onUserLeaveHint();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && (webView != null || nativePlayer != null)
                && getSharedPreferences(ChannelUserData.PREFS, 0)
                .getBoolean("pip_enabled", true)
                && getPackageManager().hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            try {
                enterPictureInPictureMode(new PictureInPictureParams.Builder()
                        .setAspectRatio(new Rational(16, 9)).build());
            } catch (RuntimeException ignored) {
                // PiP support varies across Android TV firmware.
            }
        }
    }

    @Override
    public void onPictureInPictureModeChanged(boolean inPictureInPictureMode,
                                              Configuration newConfig) {
        super.onPictureInPictureModeChanged(inPictureInPictureMode, newConfig);
        if (progress != null) {
            progress.setVisibility(inPictureInPictureMode ? View.GONE : View.VISIBLE);
        }
        if (loadingLabel != null && inPictureInPictureMode) {
            loadingLabel.setVisibility(View.GONE);
        }
        if (!inPictureInPictureMode) {
            enterImmersiveMode();
        }
    }

    private void destroyWebViewSafely() {
        WebView view = webView;
        webView = null;
        if (view == null) {
            return;
        }
        try {
            if (root != null) {
                root.removeView(view);
            }
            view.stopLoading();
            view.setWebChromeClient(null);
            view.setWebViewClient(null);
            view.removeAllViews();
            view.destroy();
        } catch (Throwable ignored) {
            // A dead vendor WebView renderer can throw during any cleanup operation.
        }
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        mainHandler.removeCallbacksAndMessages(null);
        resolverExecutor.shutdownNow();
        hideCustomView();
        releaseNativePlayer();
        destroyWebViewSafely();
        super.onDestroy();
    }
}
