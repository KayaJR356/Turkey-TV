package tv.kaya.turksat;

import android.annotation.SuppressLint;
import android.app.PictureInPictureParams;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.util.Rational;
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
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Locale;

public final class WebPlayerActivity extends AppCompatActivity {
    private static final String EXTRA_URL = "channel_web_url";
    private static final String EXTRA_FALLBACK_URL = "channel_fallback_url";
    private static final String EXTRA_NAME = "channel_name";
    private static final String EXTRA_CHANNEL_DELTA = "channel_delta";
    private static final String EXTRA_PLAYBACK_FAILED = "playback_failed";

    private FrameLayout root;
    private WebView webView;
    private ProgressBar progress;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private String fallbackUrl;
    private boolean fallbackAttempted;

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
        String url = getIntent().getStringExtra(EXTRA_URL);
        fallbackUrl = getIntent().getStringExtra(EXTRA_FALLBACK_URL);
        if (!isAllowedTopLevelUrl(fallbackUrl) || fallbackUrl.equals(url)) {
            fallbackUrl = null;
        }
        String channelName = getIntent().getStringExtra(EXTRA_NAME);
        if (!isAllowedTopLevelUrl(url)) {
            Toast.makeText(this, "Güvenli kanal adresi bulunamadı", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        enterImmersiveMode();
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        try {
            webView = new WebView(this);
        } catch (Throwable unavailable) {
            Toast.makeText(this, "Bu cihazda web oynatıcı kullanılamıyor",
                    Toast.LENGTH_LONG).show();
            finish();
            return;
        }
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

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(false);
        progress.setMax(100);
        progress.getProgressDrawable().setTint(0xffe30a17);

        webView.setWebViewClient(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Api26SafePlayerClient() : new SafePlayerClient());
        webView.setWebChromeClient(new PlayerChromeClient());
        root.addView(webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(5));
        progressParams.gravity = android.view.Gravity.TOP;
        root.addView(progress, progressParams);
        setContentView(root);

        setTitle(channelName == null ? getString(R.string.app_name) : channelName);
        webView.loadUrl(url);
        webView.requestFocus();
    }

    private class SafePlayerClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            Uri uri = request.getUrl();
            if (!request.isForMainFrame()) {
                return !isSafeEmbeddedUrl(uri);
            }
            if (isAllowedTopLevelUrl(uri == null ? null : uri.toString())) {
                return false;
            }
            handleMainFrameFailure(uri == null ? null : uri.toString());
            return true;
        }

        @SuppressWarnings("deprecation")
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            if (isAllowedTopLevelUrl(url)) {
                return false;
            }
            handleMainFrameFailure(url);
            return true;
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            progress.setVisibility(View.VISIBLE);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            progress.setVisibility(View.GONE);
            view.evaluateJavascript(
                    "(function(){document.querySelectorAll('video').forEach(function(v){"
                            + "v.setAttribute('playsinline','');v.play().catch(function(){});});})();",
                    null);
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request,
                                    WebResourceError error) {
            if (request.isForMainFrame()) {
                handleMainFrameFailure(request.getUrl().toString());
            }
        }

        @Override
        public void onReceivedHttpError(WebView view, WebResourceRequest request,
                                        WebResourceResponse errorResponse) {
            if (request.isForMainFrame() && errorResponse.getStatusCode() >= 400) {
                handleMainFrameFailure(request.getUrl().toString());
            }
        }

        @Override
        public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
            handler.cancel();
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
                    "didCrash=" + detail.didCrash() + ", priority="
                            + detail.rendererPriorityAtExit());
            setResult(RESULT_CANCELED,
                    new Intent().putExtra(EXTRA_PLAYBACK_FAILED, true));
            if (webView != null) {
                root.removeView(webView);
                webView.destroy();
                webView = null;
            }
            finish();
            return true;
        }
    }

    private void handleMainFrameFailure(String failedUrl) {
        AppDiagnostics.record(this, "web/main-frame",
                failedUrl == null ? "Bilinmeyen adres" : failedUrl);
        if (!fallbackAttempted && fallbackUrl != null
                && (failedUrl == null || !fallbackUrl.equals(failedUrl))) {
            fallbackAttempted = true;
            Toast.makeText(this, "Alternatif kanal sayfası deneniyor",
                    Toast.LENGTH_SHORT).show();
            webView.stopLoading();
            webView.loadUrl(fallbackUrl);
            return;
        }
        if (fallbackAttempted && failedUrl != null && !fallbackUrl.equals(failedUrl)) {
            return;
        }
        Toast.makeText(this, "Yayın şu anda yanıt vermiyor; kanal listede tutuldu",
                Toast.LENGTH_LONG).show();
        setResult(RESULT_CANCELED, new Intent().putExtra(EXTRA_PLAYBACK_FAILED, true));
        finish();
    }

    private final class PlayerChromeClient extends WebChromeClient {
        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            progress.setProgress(newProgress);
            progress.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
        }

        @Override
        public void onPermissionRequest(PermissionRequest request) {
            request.deny();
        }

        @Override
        public void onShowCustomView(View view, CustomViewCallback callback) {
            if (customView != null) {
                callback.onCustomViewHidden();
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

    private void hideCustomView() {
        if (customView == null) {
            return;
        }
        root.removeView(customView);
        customView = null;
        webView.setVisibility(View.VISIBLE);
        if (customViewCallback != null) {
            customViewCallback.onCustomViewHidden();
            customViewCallback = null;
        }
        webView.requestFocus();
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

    private void enterImmersiveMode() {
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
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN
                && event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            if (customView != null) {
                hideCustomView();
                return true;
            }
            if (webView != null && webView.canGoBack()) {
                webView.goBack();
                return true;
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
    }

    @Override
    protected void onPause() {
        if (webView != null && (Build.VERSION.SDK_INT < Build.VERSION_CODES.O
                || !isInPictureInPictureMode())) {
            webView.onPause();
        }
        super.onPause();
    }

    @Override
    public void onUserLeaveHint() {
        super.onUserLeaveHint();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && webView != null
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
        if (!inPictureInPictureMode) {
            enterImmersiveMode();
        }
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            hideCustomView();
            webView.stopLoading();
            webView.loadUrl("about:blank");
            webView.setWebChromeClient(null);
            webView.setWebViewClient(null);
            webView.removeAllViews();
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
