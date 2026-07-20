package tv.kaya.turksat;

import android.graphics.Color;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@UnstableApi
public final class MainActivity extends AppCompatActivity {
    private static final String PREFS = "tv_settings";
    private static final String LAST_CHANNEL_KEY = "last_channel";
    private static final String ONBOARDING_KEY = "onboarding_complete";
    private static final String STARTUP_MODE_KEY = "startup_mode";
    private static final String STARTUP_AUTOPLAY_KEY = "startup_autoplay";
    private static final String INFO_SECONDS_KEY = "info_seconds";
    private static final String SITE_ORIGIN = "https://www.canlitv.diy";
    private static final String PLAYER_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 12; Android TV) "
                    + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/138.0.0.0 Safari/537.36";

    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final StringBuilder numberBuffer = new StringBuilder();
    private final List<Channel> channels = new ArrayList<>();
    private final List<TextView> channelRows = new ArrayList<>();
    private final List<TextView> settingsRows = new ArrayList<>();
    private final Map<Integer, String> resolvedStreams = new HashMap<>();

    private FrameLayout root;
    private PlayerView playerView;
    private ExoPlayer player;
    private WebView webView;
    private LinearLayout channelPanel;
    private LinearLayout settingsPanel;
    private ScrollView channelScroll;
    private LinearLayout channelList;
    private TextView channelCount;
    private TextView channelInfo;
    private TextView numberEntry;
    private TextView loadingMessage;
    private TextView startupSetting;
    private TextView autoplaySetting;
    private TextView infoSetting;

    private int currentIndex;
    private int tuneGeneration;
    private boolean waitingForManualStart;
    private View customWebView;
    private WebChromeClient.CustomViewCallback customViewCallback;

    private final Runnable tuneEnteredNumber = () -> {
        if (numberBuffer.length() == 0) {
            return;
        }
        int number = Integer.parseInt(numberBuffer.toString());
        numberBuffer.setLength(0);
        numberEntry.setVisibility(View.GONE);
        tuneNumber(number);
    };

    private final Runnable hideChannelInfo = () -> channelInfo.setVisibility(View.GONE);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        enterImmersiveMode();
        buildUi();
        loadChannels();
    }

    private void buildUi() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        root.setFocusable(true);
        root.setFocusableInTouchMode(true);
        root.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);

        playerView = new PlayerView(this);
        playerView.setUseController(false);
        playerView.setKeepContentOnPlayerReset(false);
        playerView.setShutterBackgroundColor(Color.BLACK);
        root.addView(playerView, fullScreenParams());

        configureWebView();
        root.addView(webView, fullScreenParams());

        loadingMessage = text("Kanallar hazırlanıyor…", 24);
        loadingMessage.setGravity(Gravity.CENTER);
        loadingMessage.setBackgroundResource(R.drawable.status_background);
        FrameLayout.LayoutParams loadingParams = new FrameLayout.LayoutParams(dp(420), dp(92), Gravity.CENTER);
        root.addView(loadingMessage, loadingParams);

        channelInfo = text("", 24);
        channelInfo.setBackgroundResource(R.drawable.status_background);
        FrameLayout.LayoutParams infoParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.START);
        infoParams.setMargins(dp(32), dp(24), dp(32), dp(34));
        root.addView(channelInfo, infoParams);

        numberEntry = text("", 42);
        numberEntry.setGravity(Gravity.CENTER);
        numberEntry.setBackgroundResource(R.drawable.number_background);
        numberEntry.setVisibility(View.GONE);
        root.addView(numberEntry, new FrameLayout.LayoutParams(dp(190), dp(104), Gravity.CENTER));

        buildChannelPanel();
        root.addView(channelPanel, new FrameLayout.LayoutParams(dp(390),
                ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START));
        channelPanel.setVisibility(View.GONE);

        buildSettingsPanel();
        root.addView(settingsPanel, new FrameLayout.LayoutParams(dp(430),
                ViewGroup.LayoutParams.MATCH_PARENT, Gravity.END));
        settingsPanel.setVisibility(View.GONE);

        setContentView(root);
        buildPlayer();
        root.requestFocus();
    }

    private void configureWebView() {
        webView = new WebView(this);
        webView.setBackgroundColor(Color.BLACK);
        webView.setVisibility(View.GONE);
        webView.setFocusable(true);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setSupportMultipleWindows(false);
        settings.setUserAgentString(PLAYER_USER_AGENT);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (url.contains("canlitv.diy") && !url.contains("/player/")) {
                    view.evaluateJavascript(
                            "(function(){var p=document.getElementById('Player');"
                                    + "if(p&&p.src){window.location.replace(p.src);}})();",
                            null);
                    return;
                }
                view.evaluateJavascript(
                        "(function(){document.documentElement.style.background='#000';"
                                + "document.body.style.background='#000';"
                                + "document.body.style.margin='0';"
                                + "document.body.style.overflow='hidden';"
                                + "var tries=0;var isolate=setInterval(function(){tries++;"
                                + "var t=document.querySelector('video,iframe[src*=youtube],"
                                + "iframe[src*=vimeo],iframe[src*=player],iframe[src*=live]');"
                                + "if(t&&!location.hostname.includes('youtube.com')){"
                                + "clearInterval(isolate);document.body.innerHTML='';"
                                + "t.style.position='fixed';t.style.inset='0';"
                                + "t.style.width='100vw';t.style.height='100vh';t.style.border='0';"
                                + "document.body.appendChild(t);if(t.tagName==='VIDEO'){"
                                + "t.controls=false;t.autoplay=true;try{t.play();}catch(e){}}}"
                                + "if(tries>20){clearInterval(isolate);}},250);"
                                + "setTimeout(function(){if(typeof manualStart==='function')"
                                + "{try{manualStart();}catch(e){}}},700);})()",
                        null);
                uiHandler.postDelayed(() -> showLoading(null), 900);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (request.isForMainFrame()) {
                    showPlaybackError("Yayın sayfası açılamadı");
                }
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                hideCustomWebView();
                customWebView = view;
                customViewCallback = callback;
                root.addView(customWebView, fullScreenParams());
                customWebView.requestFocus();
            }

            @Override
            public void onHideCustomView() {
                hideCustomWebView();
            }
        });
    }

    private void buildPlayer() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Referer", SITE_ORIGIN + "/");
        headers.put("Origin", SITE_ORIGIN);

        DefaultHttpDataSource.Factory dataSourceFactory = new DefaultHttpDataSource.Factory()
                .setUserAgent(PLAYER_USER_AGENT)
                .setAllowCrossProtocolRedirects(true)
                .setDefaultRequestProperties(headers);
        player = new ExoPlayer.Builder(this)
                .setMediaSourceFactory(new DefaultMediaSourceFactory(dataSourceFactory))
                .build();
        playerView.setPlayer(player);
        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_READY) {
                    showLoading(null);
                }
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                showPlaybackError("Bu kanalın yayını şu anda açılamıyor");
            }
        });
    }

    private void buildChannelPanel() {
        channelPanel = new LinearLayout(this);
        channelPanel.setOrientation(LinearLayout.VERTICAL);
        channelPanel.setPadding(dp(22), dp(22), dp(18), dp(22));
        channelPanel.setBackgroundResource(R.drawable.panel_background);

        TextView title = text(getString(R.string.app_name), 27);
        title.setTextColor(Color.WHITE);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        channelPanel.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        channelCount = text("Kanallar yükleniyor", 16);
        channelCount.setTextColor(0xffaab7c9);
        LinearLayout.LayoutParams countParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        countParams.setMargins(0, dp(2), 0, dp(16));
        channelPanel.addView(channelCount, countParams);

        channelScroll = new ScrollView(this);
        channelScroll.setFillViewport(true);
        channelScroll.setVerticalScrollBarEnabled(false);
        channelScroll.setClipToPadding(false);
        channelScroll.setPadding(0, dp(4), dp(4), dp(20));

        channelList = new LinearLayout(this);
        channelList.setOrientation(LinearLayout.VERTICAL);
        channelScroll.addView(channelList, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        channelPanel.addView(channelScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView settingsButton = text("⚙  Ayarlar", 18);
        settingsButton.setFocusable(true);
        settingsButton.setBackgroundResource(R.drawable.channel_row_background);
        settingsButton.setOnFocusChangeListener(this::styleFocusedRow);
        settingsButton.setOnClickListener(view -> showSettingsPanel(true));
        LinearLayout.LayoutParams settingsButtonParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
        settingsButtonParams.setMargins(0, dp(6), dp(4), dp(8));
        channelPanel.addView(settingsButton, settingsButtonParams);

        TextView help = text("OK  Seç     →  Kapat     0–9  Kanal", 14);
        help.setTextColor(0xffaab7c9);
        channelPanel.addView(help, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void buildSettingsPanel() {
        settingsPanel = new LinearLayout(this);
        settingsPanel.setOrientation(LinearLayout.VERTICAL);
        settingsPanel.setPadding(dp(24), dp(24), dp(24), dp(24));
        settingsPanel.setBackgroundResource(R.drawable.panel_background);

        TextView title = text("Ayarlar", 27);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        settingsPanel.addView(title);

        TextView subtitle = text("OK ile değiştir", 16);
        subtitle.setTextColor(0xffaab7c9);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subtitleParams.setMargins(0, 0, 0, dp(18));
        settingsPanel.addView(subtitle, subtitleParams);

        startupSetting = addSettingRow(() -> {
            String current = getSharedPreferences(PREFS, 0)
                    .getString(STARTUP_MODE_KEY, "last");
            String next = "last".equals(current) ? "first" : "first".equals(current) ? "menu" : "last";
            getSharedPreferences(PREFS, 0).edit().putString(STARTUP_MODE_KEY, next).apply();
            refreshSettingLabels();
        });
        autoplaySetting = addSettingRow(() -> {
            boolean current = getSharedPreferences(PREFS, 0)
                    .getBoolean(STARTUP_AUTOPLAY_KEY, true);
            getSharedPreferences(PREFS, 0).edit().putBoolean(STARTUP_AUTOPLAY_KEY, !current).apply();
            refreshSettingLabels();
        });
        infoSetting = addSettingRow(() -> {
            int current = getSharedPreferences(PREFS, 0).getInt(INFO_SECONDS_KEY, 5);
            int next = current == 5 ? 8 : current == 8 ? 0 : 5;
            getSharedPreferences(PREFS, 0).edit().putInt(INFO_SECONDS_KEY, next).apply();
            refreshSettingLabels();
        });
        addSettingRow(() -> {
            showSettingsPanel(false);
            loadChannels();
        }).setText("Kanal listesini yenile");
        addSettingRow(() -> Toast.makeText(this,
                "Türkiye TV 3.0 • 285 kanallı Android TV oynatıcısı",
                Toast.LENGTH_LONG).show()).setText("Uygulama hakkında");
        refreshSettingLabels();
    }

    private TextView addSettingRow(Runnable action) {
        TextView row = text("", 19);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setFocusable(true);
        row.setBackgroundResource(R.drawable.channel_row_background);
        row.setOnFocusChangeListener(this::styleFocusedRow);
        row.setOnClickListener(view -> action.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(66));
        params.setMargins(0, 0, 0, dp(7));
        settingsPanel.addView(row, params);
        settingsRows.add(row);
        return row;
    }

    private void refreshSettingLabels() {
        String mode = getSharedPreferences(PREFS, 0).getString(STARTUP_MODE_KEY, "last");
        String modeLabel = "first".equals(mode) ? "1. kanal" : "menu".equals(mode)
                ? "Kanal seçimi" : "Son izlenen";
        startupSetting.setText("Açılış  ·  " + modeLabel);
        autoplaySetting.setText("Açılışta oynat  ·  "
                + (getSharedPreferences(PREFS, 0).getBoolean(STARTUP_AUTOPLAY_KEY, true)
                ? "Açık" : "Kapalı"));
        int seconds = getSharedPreferences(PREFS, 0).getInt(INFO_SECONDS_KEY, 5);
        infoSetting.setText("Kanal bilgisi  ·  " + (seconds == 0 ? "Kapalı" : seconds + " saniye"));
    }

    private void loadChannels() {
        showLoading("Kanallar hazırlanıyor…");
        new Thread(() -> {
            List<Channel> loaded = ChannelRepository.load(this);
            runOnUiThread(() -> {
                channels.clear();
                channels.addAll(loaded);
                rebuildChannelList();

                if (channels.isEmpty()) {
                    showPlaybackError("Kanal listesi alınamadı");
                    return;
                }
                startAfterCatalogLoad();
            });
        }, "channel-catalog").start();
    }

    private void rebuildChannelList() {
        channelList.removeAllViews();
        channelRows.clear();
        channelCount.setText(String.format(Locale.forLanguageTag("tr-TR"),
                "%d kanal • OK ile seç", channels.size()));

        for (int i = 0; i < channels.size(); i++) {
            Channel channel = channels.get(i);
            TextView row = text(String.format(Locale.ROOT, "%03d   %s", channel.number, channel.name), 21);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setSingleLine(true);
            row.setFocusable(true);
            row.setTag(i);
            row.setBackgroundResource(R.drawable.channel_row_background);
            row.setOnFocusChangeListener(this::styleFocusedRow);
            row.setOnClickListener(view -> {
                tune((Integer) view.getTag());
                showChannelPanel(false);
            });

            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(58));
            rowParams.setMargins(0, 0, 0, dp(5));
            channelList.addView(row, rowParams);
            channelRows.add(row);
        }
    }

    private void styleFocusedRow(View view, boolean hasFocus) {
        view.animate().scaleX(hasFocus ? 1.025f : 1f).scaleY(hasFocus ? 1.025f : 1f)
                .setDuration(110).start();
        ((TextView) view).setTextColor(hasFocus ? 0xff07111f : Color.WHITE);
    }

    private void startAfterCatalogLoad() {
        if (!getSharedPreferences(PREFS, 0).getBoolean(ONBOARDING_KEY, false)) {
            showFirstLaunchDialog();
            return;
        }
        launchFromPreferences();
    }

    private void showFirstLaunchDialog() {
        String[] options = {"Son izlenen kanalı aç", "Her zaman 1. kanalı aç", "Kanal listesini aç"};
        final int[] selection = {0};
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Türkiye TV'ye hoş geldiniz")
                .setSingleChoiceItems(options, 0, (itemDialog, which) -> selection[0] = which)
                .setPositiveButton("Başla", (itemDialog, which) -> {
                    String mode = selection[0] == 1 ? "first" : selection[0] == 2 ? "menu" : "last";
                    getSharedPreferences(PREFS, 0).edit()
                            .putBoolean(ONBOARDING_KEY, true)
                            .putString(STARTUP_MODE_KEY, mode)
                            .apply();
                    refreshSettingLabels();
                    launchFromPreferences();
                })
                .setCancelable(false)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).requestFocus());
        dialog.show();
    }

    private void launchFromPreferences() {
        String mode = getSharedPreferences(PREFS, 0).getString(STARTUP_MODE_KEY, "last");
        int lastNumber = getSharedPreferences(PREFS, 0).getInt(LAST_CHANNEL_KEY, 1);
        int index = "first".equals(mode) ? 0 : findChannelIndex(lastNumber);
        boolean autoplay = getSharedPreferences(PREFS, 0).getBoolean(STARTUP_AUTOPLAY_KEY, true);
        if ("menu".equals(mode)) {
            tune(index, false);
            showChannelPanel(true);
        } else {
            tune(index, autoplay);
        }
    }

    private void tune(int index) {
        tune(index, true);
    }

    private void tune(int index, boolean startPlayback) {
        if (index < 0 || index >= channels.size()) {
            return;
        }
        currentIndex = index;
        tuneGeneration++;
        int generation = tuneGeneration;
        Channel channel = channels.get(index);

        hideCustomWebView();
        player.stop();
        webView.stopLoading();
        webView.setVisibility(View.GONE);
        playerView.setVisibility(View.VISIBLE);
        getSharedPreferences(PREFS, 0).edit().putInt(LAST_CHANNEL_KEY, channel.number).apply();
        showChannelInfo(channel);
        if (!startPlayback) {
            waitingForManualStart = true;
            showLoading("▶ tuşuna basarak yayını başlatın");
            return;
        }
        startChannelPlayback(channel, generation);
    }

    private void startChannelPlayback(Channel channel, int generation) {
        waitingForManualStart = false;
        showLoading("Yayın hazırlanıyor…");

        if (channel.isDirectStream()) {
            playStream(channel.playbackUrl);
            return;
        }

        String cachedStream = resolvedStreams.get(channel.number);
        if (cachedStream != null) {
            playStream(cachedStream);
            return;
        }

        new Thread(() -> {
            String resolvedUrl = ChannelRepository.resolvePlaybackUrl(channel);
            runOnUiThread(() -> {
                if (generation != tuneGeneration) {
                    return;
                }
                if (resolvedUrl.contains(".m3u8") || resolvedUrl.contains(".mpd")) {
                    resolvedStreams.put(channel.number, resolvedUrl);
                    playStream(resolvedUrl);
                } else {
                    playInWebView(channel, resolvedUrl);
                }
            });
        }, "stream-resolver").start();
    }

    private void playStream(String url) {
        webView.onPause();
        webView.setVisibility(View.GONE);
        playerView.setVisibility(View.VISIBLE);
        player.setMediaItem(MediaItem.fromUri(url));
        player.prepare();
        player.play();
        root.requestFocus();
    }

    private void playInWebView(Channel channel, String resolvedUrl) {
        player.stop();
        playerView.setVisibility(View.GONE);
        webView.setVisibility(View.VISIBLE);
        webView.onResume();
        showLoading("Uyumlu oynatıcı açılıyor…");
        Map<String, String> headers = new HashMap<>();
        headers.put("Referer", channel.pageUrl);
        webView.loadUrl(resolvedUrl, headers);
        root.requestFocus();
    }

    private void showChannelInfo(Channel channel) {
        showChannelInfo(channel, false);
    }

    private void showChannelInfo(Channel channel, boolean force) {
        channelInfo.setText(String.format(Locale.forLanguageTag("tr-TR"),
                "%03d  %s\nOK: kanal listesi   P+ / P-: kanal değiştir",
                channel.number, channel.name));
        channelInfo.setVisibility(View.VISIBLE);
        uiHandler.removeCallbacks(hideChannelInfo);
        int seconds = getSharedPreferences(PREFS, 0).getInt(INFO_SECONDS_KEY, 5);
        if (seconds == 0 && !force) {
            channelInfo.setVisibility(View.GONE);
        } else {
            uiHandler.postDelayed(hideChannelInfo, (seconds == 0 ? 5 : seconds) * 1000L);
        }
    }

    private void showPlaybackError(String message) {
        showLoading(message + "\nP+ / P- ile başka kanal deneyin");
    }

    private void showLoading(String message) {
        if (message == null) {
            loadingMessage.setVisibility(View.GONE);
            return;
        }
        loadingMessage.setText(message);
        loadingMessage.setVisibility(View.VISIBLE);
        loadingMessage.bringToFront();
        numberEntry.bringToFront();
        channelPanel.bringToFront();
        settingsPanel.bringToFront();
    }

    private void showSettingsPanel(boolean show) {
        if (show) {
            hideCustomWebView();
            channelPanel.setVisibility(View.GONE);
            settingsPanel.setVisibility(View.VISIBLE);
            settingsPanel.bringToFront();
            refreshSettingLabels();
            if (!settingsRows.isEmpty()) {
                settingsRows.get(0).requestFocus();
            }
        } else {
            settingsPanel.setVisibility(View.GONE);
            root.requestFocus();
        }
    }

    private void showChannelPanel(boolean show) {
        if (show) {
            hideCustomWebView();
            channelPanel.setVisibility(View.VISIBLE);
            channelPanel.bringToFront();
            if (!channelRows.isEmpty()) {
                TextView row = channelRows.get(Math.min(currentIndex, channelRows.size() - 1));
                row.requestFocus();
                channelScroll.post(() -> channelScroll.smoothScrollTo(0,
                        Math.max(0, row.getTop() - dp(150))));
            }
        } else {
            channelPanel.setVisibility(View.GONE);
            root.requestFocus();
        }
    }

    private void tuneNumber(int number) {
        int index = findChannelIndex(number);
        if (index >= 0 && index < channels.size() && channels.get(index).number == number) {
            showChannelPanel(false);
            tune(index);
        } else {
            Toast.makeText(this, "Kanal " + number + " listede yok", Toast.LENGTH_SHORT).show();
        }
    }

    private int findChannelIndex(int number) {
        for (int i = 0; i < channels.size(); i++) {
            if (channels.get(i).number == number) {
                return i;
            }
        }
        return 0;
    }

    private void hideCustomWebView() {
        if (customWebView != null) {
            root.removeView(customWebView);
            customWebView = null;
        }
        if (customViewCallback != null) {
            customViewCallback.onCustomViewHidden();
            customViewCallback = null;
        }
    }

    private TextView text(String value, int sizeSp) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextColor(Color.WHITE);
        view.setTextSize(sizeSp);
        view.setPadding(dp(18), dp(12), dp(18), dp(12));
        return view;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private FrameLayout.LayoutParams fullScreenParams() {
        return new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
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

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN) {
            return super.dispatchKeyEvent(event);
        }

        int key = event.getKeyCode();
        if (key >= KeyEvent.KEYCODE_0 && key <= KeyEvent.KEYCODE_9) {
            numberBuffer.append(key - KeyEvent.KEYCODE_0);
            if (numberBuffer.length() > 3) {
                numberBuffer.deleteCharAt(0);
            }
            numberEntry.setText(numberBuffer.toString());
            numberEntry.setVisibility(View.VISIBLE);
            numberEntry.bringToFront();
            uiHandler.removeCallbacks(tuneEnteredNumber);
            uiHandler.postDelayed(tuneEnteredNumber, 1200);
            return true;
        }

        if (customWebView != null && key == KeyEvent.KEYCODE_BACK) {
            hideCustomWebView();
            return true;
        }

        if (settingsPanel.getVisibility() == View.VISIBLE) {
            if (key == KeyEvent.KEYCODE_BACK || key == KeyEvent.KEYCODE_DPAD_RIGHT
                    || key == KeyEvent.KEYCODE_SETTINGS || key == KeyEvent.KEYCODE_PROG_BLUE) {
                showSettingsPanel(false);
                return true;
            }
            if (key == KeyEvent.KEYCODE_DPAD_LEFT) {
                return true;
            }
            if (key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_DPAD_DOWN
                    || key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER) {
                return super.dispatchKeyEvent(event);
            }
        }

        if (channelPanel.getVisibility() == View.VISIBLE) {
            if (key == KeyEvent.KEYCODE_BACK || key == KeyEvent.KEYCODE_DPAD_RIGHT
                    || key == KeyEvent.KEYCODE_MENU || key == KeyEvent.KEYCODE_GUIDE) {
                showChannelPanel(false);
                return true;
            }
            if (key == KeyEvent.KEYCODE_DPAD_LEFT) {
                return true;
            }
            if (key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_DPAD_DOWN
                    || key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER) {
                return super.dispatchKeyEvent(event);
            }
        }

        if (!channels.isEmpty() && key == KeyEvent.KEYCODE_CHANNEL_UP) {
            tune((currentIndex + 1) % channels.size());
            return true;
        }
        if (!channels.isEmpty() && key == KeyEvent.KEYCODE_CHANNEL_DOWN) {
            tune((currentIndex - 1 + channels.size()) % channels.size());
            return true;
        }
        if (key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_DPAD_DOWN
                || key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT) {
            return true;
        }
        if (key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER
                || key == KeyEvent.KEYCODE_MENU || key == KeyEvent.KEYCODE_GUIDE) {
            showChannelPanel(true);
            return true;
        }
        if (key == KeyEvent.KEYCODE_INFO && !channels.isEmpty()) {
            showChannelInfo(channels.get(currentIndex), true);
            return true;
        }
        if (key == KeyEvent.KEYCODE_SETTINGS || key == KeyEvent.KEYCODE_PROG_BLUE) {
            showSettingsPanel(true);
            return true;
        }
        if (key == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                || key == KeyEvent.KEYCODE_MEDIA_PLAY
                || key == KeyEvent.KEYCODE_MEDIA_PAUSE) {
            if (waitingForManualStart && !channels.isEmpty()) {
                startChannelPlayback(channels.get(currentIndex), tuneGeneration);
                return true;
            }
            if (playerView.getVisibility() == View.VISIBLE) {
                if (player.isPlaying()) {
                    player.pause();
                } else {
                    player.play();
                }
            } else {
                if (webView.getVisibility() != View.VISIBLE && !channels.isEmpty()) {
                    startChannelPlayback(channels.get(currentIndex), tuneGeneration);
                } else {
                    webView.evaluateJavascript(
                        "(function(){var v=document.querySelector('video');"
                                + "if(v){v.paused?v.play():v.pause();}"
                                + "else if(typeof manualStart==='function'){manualStart();}})()",
                        null);
                }
            }
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onStart() {
        super.onStart();
        webView.onResume();
        if (player != null && playerView.getVisibility() == View.VISIBLE) {
            player.play();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        enterImmersiveMode();
    }

    @Override
    protected void onStop() {
        super.onStop();
        player.pause();
        webView.onPause();
    }

    @Override
    protected void onDestroy() {
        uiHandler.removeCallbacksAndMessages(null);
        hideCustomWebView();
        webView.stopLoading();
        webView.loadUrl("about:blank");
        webView.destroy();
        player.release();
        super.onDestroy();
    }
}
