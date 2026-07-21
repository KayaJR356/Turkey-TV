package tv.kaya.turksat;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@UnstableApi
public final class MainActivity extends AppCompatActivity {
    private static final float COMPACT_LAYOUT_SCALE = 0.82f;
    private static final float COMPACT_TEXT_SCALE = 0.88f;
    private static final int WEB_PLAYER_REQUEST = 3402;
    private static final String PREFS = "tv_settings";
    private static final String LAST_CHANNEL_KEY = "last_channel";
    private static final String ONBOARDING_KEY = "onboarding_complete";
    private static final String STARTUP_MODE_KEY = "startup_mode";
    private static final String STARTUP_AUTOPLAY_KEY = "startup_autoplay";
    private static final String AUTO_LAUNCH_KEY = "auto_launch_on_boot";
    private static final String INFO_SECONDS_KEY = "info_seconds";
    private static final String ASPECT_MODE_KEY = "aspect_mode";
    private static final long TOP_STATUS_VISIBLE_MS = 2_500L;
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
    private final Map<Integer, Set<String>> failedStreams = new HashMap<>();
    private final Set<Integer> resolvingChannels = Collections.synchronizedSet(new HashSet<>());
    private final ExecutorService resolverExecutor = Executors.newFixedThreadPool(3);
    private final ExecutorService healthExecutor = Executors.newFixedThreadPool(4);

    private FrameLayout root;
    private PlayerView playerView;
    private ExoPlayer player;
    private FrameLayout loadingOverlay;
    private LinearLayout topStatusBar;
    private LinearLayout channelPanel;
    private LinearLayout settingsPanel;
    private LinearLayout settingsList;
    private ScrollView channelScroll;
    private LinearLayout channelList;
    private TextView channelCount;
    private TextView channelInfo;
    private TextView numberEntry;
    private TextView loadingMessage;
    private ProgressBar loadingSpinner;
    private TextView clockView;
    private TextView panelClockView;
    private TextView nowPlayingNumber;
    private TextView nowPlayingTitle;
    private TextView liveBadge;
    private TextView channelSearchButton;
    private TextView channelSettingsButton;
    private TextView startupSetting;
    private TextView autoplaySetting;
    private TextView autoLaunchSetting;
    private TextView infoSetting;
    private TextView aspectSetting;
    private AlertDialog searchDialog;
    private AppUpdateManager appUpdateManager;
    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;

    private int currentIndex;
    private int tuneGeneration;
    private int automaticRetryCount;
    private int loadingAnimationGeneration;
    private int healthSweepGeneration;
    private boolean waitingForManualStart;
    private boolean resumePlaybackOnStart;
    private boolean webFallbackOpen;
    private volatile boolean networkUnavailable;
    private String searchQuery = "";
    private String activeStreamUrl;

    private final Runnable tuneEnteredNumber = () -> {
        if (numberBuffer.length() == 0) {
            return;
        }
        int number = Integer.parseInt(numberBuffer.toString());
        numberBuffer.setLength(0);
        numberEntry.setVisibility(View.GONE);
        tuneNumber(number);
    };

    private final Runnable hideChannelInfo = () -> channelInfo.animate()
            .alpha(0f)
            .translationY(dp(12))
            .setDuration(160)
            .withEndAction(() -> channelInfo.setVisibility(View.GONE))
            .start();
    private final Runnable hideTopStatusBar = () -> {
        if (topStatusBar == null || player == null
                || player.getPlaybackState() != Player.STATE_READY
                || channelPanel.getVisibility() == View.VISIBLE
                || settingsPanel.getVisibility() == View.VISIBLE) {
            return;
        }
        topStatusBar.animate().cancel();
        topStatusBar.animate().alpha(0f).translationY(-dp(24)).setDuration(180)
                .withEndAction(() -> topStatusBar.setVisibility(View.GONE))
                .start();
    };
    private final Runnable updateClock = new Runnable() {
        @Override
        public void run() {
            String time = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
            if (clockView != null) {
                clockView.setText(time);
            }
            if (panelClockView != null) {
                panelClockView.setText(time);
            }
            uiHandler.postDelayed(this, 30_000L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        appUpdateManager = new AppUpdateManager(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        enterImmersiveMode();
        buildUi();
        registerNetworkMonitoring();
        loadChannels();
        appUpdateManager.checkForUpdates(false);
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

        View screenScrim = new View(this);
        screenScrim.setBackgroundResource(R.drawable.screen_scrim);
        root.addView(screenScrim, fullScreenParams());

        buildTopStatusBar();
        FrameLayout.LayoutParams topBarParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP);
        topBarParams.setMargins(dp(26), dp(20), dp(26), 0);
        root.addView(topStatusBar, topBarParams);

        buildLoadingOverlay();
        root.addView(loadingOverlay, fullScreenParams());

        channelInfo = text("", 24);
        channelInfo.setBackgroundResource(R.drawable.status_background);
        channelInfo.setVisibility(View.GONE);
        FrameLayout.LayoutParams infoParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.START);
        infoParams.setMargins(dp(36), dp(24), dp(36), dp(36));
        root.addView(channelInfo, infoParams);

        numberEntry = text("", 46);
        numberEntry.setGravity(Gravity.CENTER);
        numberEntry.setBackgroundResource(R.drawable.number_background);
        numberEntry.setVisibility(View.GONE);
        root.addView(numberEntry, new FrameLayout.LayoutParams(dp(210), dp(116), Gravity.CENTER));

        buildChannelPanel();
        root.addView(channelPanel, new FrameLayout.LayoutParams(
                dp(520), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START));
        channelPanel.setVisibility(View.GONE);

        buildSettingsPanel();
        root.addView(settingsPanel, new FrameLayout.LayoutParams(
                dp(510), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.END));
        settingsPanel.setVisibility(View.GONE);

        setContentView(root);
        buildPlayer();
        applyAspectMode();
        uiHandler.post(updateClock);
        root.requestFocus();
    }

    private void buildTopStatusBar() {
        topStatusBar = new LinearLayout(this);
        topStatusBar.setOrientation(LinearLayout.HORIZONTAL);
        topStatusBar.setGravity(Gravity.CENTER_VERTICAL);
        topStatusBar.setPadding(dp(22), dp(14), dp(22), dp(14));
        topStatusBar.setBackgroundResource(R.drawable.top_bar_background);

        TextView brandMark = text("TR", 16);
        brandMark.setGravity(Gravity.CENTER);
        brandMark.setTypeface(brandMark.getTypeface(), android.graphics.Typeface.BOLD);
        brandMark.setBackgroundResource(R.drawable.brand_mark_background);
        brandMark.setPadding(0, 0, 0, 0);
        topStatusBar.addView(brandMark, new LinearLayout.LayoutParams(dp(48), dp(36)));

        LinearLayout channelIdentity = new LinearLayout(this);
        channelIdentity.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams identityParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        identityParams.setMargins(dp(14), 0, dp(20), 0);

        nowPlayingNumber = text("KANAL ---", 12);
        nowPlayingNumber.setTextColor(0xffff5b66);
        nowPlayingNumber.setTypeface(nowPlayingNumber.getTypeface(), android.graphics.Typeface.BOLD);
        nowPlayingNumber.setPadding(0, 0, 0, dp(1));
        channelIdentity.addView(nowPlayingNumber);

        nowPlayingTitle = text("Türkiye Canlı TV", 24);
        nowPlayingTitle.setTypeface(nowPlayingTitle.getTypeface(), android.graphics.Typeface.BOLD);
        nowPlayingTitle.setSingleLine(true);
        nowPlayingTitle.setPadding(0, 0, 0, 0);
        channelIdentity.addView(nowPlayingTitle);
        topStatusBar.addView(channelIdentity, identityParams);

        liveBadge = text("HAZIRLANIYOR", 12);
        liveBadge.setGravity(Gravity.CENTER);
        liveBadge.setTypeface(liveBadge.getTypeface(), android.graphics.Typeface.BOLD);
        liveBadge.setBackgroundResource(R.drawable.live_badge_background);
        liveBadge.setPadding(dp(14), 0, dp(14), 0);
        topStatusBar.addView(liveBadge, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(34)));

        clockView = text("--:--", 21);
        clockView.setGravity(Gravity.CENTER);
        clockView.setTypeface(clockView.getTypeface(), android.graphics.Typeface.BOLD);
        clockView.setPadding(dp(20), 0, 0, 0);
        topStatusBar.addView(clockView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void buildLoadingOverlay() {
        loadingOverlay = new FrameLayout(this);
        loadingOverlay.setBackgroundColor(0xd9000000);
        loadingOverlay.setFocusable(false);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER);
        content.setPadding(dp(42), dp(34), dp(42), dp(34));
        content.setBackgroundResource(R.drawable.loading_card_background);

        loadingSpinner = new ProgressBar(this);
        loadingSpinner.getIndeterminateDrawable().setTint(0xffe30a17);
        LinearLayout.LayoutParams spinnerParams = new LinearLayout.LayoutParams(dp(58), dp(58));
        spinnerParams.gravity = Gravity.CENTER_HORIZONTAL;
        spinnerParams.setMargins(0, 0, 0, dp(16));
        content.addView(loadingSpinner, spinnerParams);

        loadingMessage = text("Kanallar hazırlanıyor…", 24);
        loadingMessage.setGravity(Gravity.CENTER);
        loadingMessage.setPadding(0, dp(4), 0, 0);
        content.addView(loadingMessage, new LinearLayout.LayoutParams(dp(520),
                ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout.LayoutParams loadingParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER);
        loadingOverlay.addView(content, loadingParams);
    }

    private void buildPlayer() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Referer", SITE_ORIGIN + "/");

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
                if (playbackState == Player.STATE_BUFFERING && !waitingForManualStart) {
                    setLiveState("YÜKLENİYOR");
                    showLoading("Yayın yükleniyor…");
                } else if (playbackState == Player.STATE_READY) {
                    automaticRetryCount = 0;
                    setLiveState("● CANLI");
                    showLoading(null);
                    scheduleTopStatusBarHide();
                    if (!channels.isEmpty()) {
                        showChannelInfo(channels.get(currentIndex));
                        prefetchAdjacentStreams(currentIndex);
                    }
                    root.requestFocus();
                } else if (playbackState == Player.STATE_ENDED) {
                    setLiveState("SONA ERDİ");
                    retryAfterPlayerError();
                }
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                retryAfterPlayerError();
            }
        });
    }

    private void buildChannelPanel() {
        channelPanel = new LinearLayout(this);
        channelPanel.setOrientation(LinearLayout.VERTICAL);
        channelPanel.setPadding(dp(28), dp(26), dp(22), dp(22));
        channelPanel.setBackgroundResource(R.drawable.panel_background);

        TextView eyebrow = text("CANLI KANAL REHBERİ", 12);
        eyebrow.setTextColor(0xffff5b66);
        eyebrow.setTypeface(eyebrow.getTypeface(), android.graphics.Typeface.BOLD);
        eyebrow.setPadding(0, 0, 0, dp(4));
        channelPanel.addView(eyebrow);

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = text("Türkiye Canlı TV", 30);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        titleRow.addView(title, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        panelClockView = text("--:--", 19);
        panelClockView.setGravity(Gravity.CENTER);
        panelClockView.setTextColor(0xffd9e2ef);
        titleRow.addView(panelClockView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        channelPanel.addView(titleRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        channelCount = text("Canlı kanallar hazırlanıyor", 15);
        channelCount.setTextColor(0xffaab7c9);
        LinearLayout.LayoutParams countParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        countParams.setMargins(0, 0, 0, dp(14));
        channelPanel.addView(channelCount, countParams);

        channelSearchButton = addChannelAction("⌕  Kanal ara                         YEŞİL", this::showSearchDialog);
        channelSettingsButton = addChannelAction("⚙  Oynatıcı ayarları                 MAVİ", () -> showSettingsPanel(true));
        channelSearchButton.setNextFocusDownId(channelSettingsButton.getId());
        channelSettingsButton.setNextFocusUpId(channelSearchButton.getId());

        channelScroll = new ScrollView(this);
        channelScroll.setFillViewport(true);
        channelScroll.setVerticalScrollBarEnabled(false);
        channelScroll.setClipToPadding(false);
        channelScroll.setPadding(0, dp(4), dp(4), dp(10));

        channelList = new LinearLayout(this);
        channelList.setOrientation(LinearLayout.VERTICAL);
        channelScroll.addView(channelList, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        scrollParams.setMargins(0, dp(4), 0, 0);
        channelPanel.addView(channelScroll, scrollParams);

        TextView help = text("OK Seç  ·  SAĞ Kapat  ·  0–9 Kanal", 14);
        help.setTextColor(0xffaab7c9);
        channelPanel.addView(help, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private TextView addChannelAction(String label, Runnable action) {
        TextView row = text(label, 18);
        row.setId(View.generateViewId());
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setFocusable(true);
        row.setBackgroundResource(R.drawable.channel_row_background);
        row.setOnFocusChangeListener(this::styleFocusedRow);
        row.setOnClickListener(view -> action.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
        params.setMargins(0, 0, dp(4), dp(5));
        channelPanel.addView(row, params);
        return row;
    }

    private void buildSettingsPanel() {
        settingsPanel = new LinearLayout(this);
        settingsPanel.setOrientation(LinearLayout.VERTICAL);
        settingsPanel.setPadding(dp(28), dp(28), dp(28), dp(24));
        settingsPanel.setBackgroundResource(R.drawable.panel_background);

        TextView title = text("Oynatıcı Ayarları", 30);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        settingsPanel.addView(title);

        TextView subtitle = text("Seçeneği değiştirmek için OK'a basın", 15);
        subtitle.setTextColor(0xffaab7c9);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subtitleParams.setMargins(0, 0, 0, dp(18));
        settingsPanel.addView(subtitle, subtitleParams);

        ScrollView settingsScroll = new ScrollView(this);
        settingsScroll.setFillViewport(true);
        settingsScroll.setVerticalScrollBarEnabled(false);
        settingsList = new LinearLayout(this);
        settingsList.setOrientation(LinearLayout.VERTICAL);
        settingsScroll.addView(settingsList, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        settingsPanel.addView(settingsScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        startupSetting = addSettingRow(() -> {
            String current = getSharedPreferences(PREFS, 0).getString(STARTUP_MODE_KEY, "last");
            String next = "last".equals(current) ? "first" : "first".equals(current) ? "menu" : "last";
            getSharedPreferences(PREFS, 0).edit().putString(STARTUP_MODE_KEY, next).apply();
            refreshSettingLabels();
        });
        autoplaySetting = addSettingRow(() -> {
            boolean current = getSharedPreferences(PREFS, 0).getBoolean(STARTUP_AUTOPLAY_KEY, true);
            getSharedPreferences(PREFS, 0).edit().putBoolean(STARTUP_AUTOPLAY_KEY, !current).apply();
            refreshSettingLabels();
        });
        autoLaunchSetting = addSettingRow(() -> {
            boolean current = getSharedPreferences(PREFS, 0).getBoolean(AUTO_LAUNCH_KEY, false);
            getSharedPreferences(PREFS, 0).edit().putBoolean(AUTO_LAUNCH_KEY, !current).apply();
            BootReceiver.updateEnabled(this, !current);
            refreshSettingLabels();
            Toast.makeText(this, !current
                            ? "Cihaz yeniden açıldığında Türkiye Canlı TV başlatılacak"
                            : "Otomatik başlatma kapatıldı",
                    Toast.LENGTH_SHORT).show();
        });
        infoSetting = addSettingRow(() -> {
            int current = getSharedPreferences(PREFS, 0).getInt(INFO_SECONDS_KEY, 5);
            int next = current == 5 ? 8 : current == 8 ? 0 : 5;
            getSharedPreferences(PREFS, 0).edit().putInt(INFO_SECONDS_KEY, next).apply();
            refreshSettingLabels();
        });
        aspectSetting = addSettingRow(() -> {
            String current = getSharedPreferences(PREFS, 0).getString(ASPECT_MODE_KEY, "fit");
            String next = "fit".equals(current) ? "zoom" : "zoom".equals(current) ? "fill" : "fit";
            getSharedPreferences(PREFS, 0).edit().putString(ASPECT_MODE_KEY, next).apply();
            applyAspectMode();
            refreshSettingLabels();
        });
        addSettingRow(() -> {
            showSettingsPanel(false);
            retryCurrentChannel();
        }).setText("Geçerli yayını yeniden başlat");
        addSettingRow(() -> {
            showSettingsPanel(false);
            loadChannels();
        }).setText("Kanal listesini yenile");
        addSettingRow(() -> {
            showSettingsPanel(false);
            appUpdateManager.checkForUpdates(true);
        }).setText("Uygulama güncellemesini denetle");
        addSettingRow(() -> Toast.makeText(this,
                "Kırmızı: yenile  ·  Yeşil: ara  ·  Sarı: kanallar  ·  Mavi: ayarlar",
                Toast.LENGTH_LONG).show()).setText("Kumanda tuş rehberi");
        addSettingRow(() -> Toast.makeText(this,
                "Türkiye Canlı TV " + BuildConfig.VERSION_NAME + " · Yerel Android TV oynatıcısı",
                Toast.LENGTH_LONG).show()).setText("Uygulama hakkında");
        refreshSettingLabels();

        TextView footer = text("SAĞ veya GERİ ile kapat", 14);
        footer.setTextColor(0xffaab7c9);
        settingsPanel.addView(footer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private TextView addSettingRow(Runnable action) {
        TextView row = text("", 18);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setFocusable(true);
        row.setBackgroundResource(R.drawable.channel_row_background);
        row.setOnFocusChangeListener(this::styleFocusedRow);
        row.setOnClickListener(view -> action.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(62));
        params.setMargins(0, 0, 0, dp(6));
        settingsList.addView(row, params);
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
        autoLaunchSetting.setText("Cihaz açılışı  ·  "
                + (getSharedPreferences(PREFS, 0).getBoolean(AUTO_LAUNCH_KEY, false)
                ? "Bu uygulamayı başlat" : "Başlatma"));
        int seconds = getSharedPreferences(PREFS, 0).getInt(INFO_SECONDS_KEY, 5);
        infoSetting.setText("Kanal bilgisi  ·  " + (seconds == 0 ? "Kapalı" : seconds + " saniye"));
        String aspect = getSharedPreferences(PREFS, 0).getString(ASPECT_MODE_KEY, "fit");
        String aspectLabel = "zoom".equals(aspect) ? "Yakınlaştır" : "fill".equals(aspect)
                ? "Doldur" : "Orijinal";
        aspectSetting.setText("Görüntü oranı  ·  " + aspectLabel);
    }

    private void applyAspectMode() {
        if (playerView == null) {
            return;
        }
        String aspect = getSharedPreferences(PREFS, 0).getString(ASPECT_MODE_KEY, "fit");
        int resizeMode = "zoom".equals(aspect)
                ? AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                : "fill".equals(aspect)
                ? AspectRatioFrameLayout.RESIZE_MODE_FILL
                : AspectRatioFrameLayout.RESIZE_MODE_FIT;
        playerView.setResizeMode(resizeMode);
    }

    private void loadChannels() {
        int sweepGeneration = ++healthSweepGeneration;
        showLoading("Kanallar hazırlanıyor…");
        new Thread(() -> {
            List<Channel> loaded = ChannelRepository.load(this);
            runOnUiThread(() -> {
                channels.clear();
                channels.addAll(loaded);
                resolvedStreams.clear();
                searchQuery = "";
                rebuildChannelList();

                if (channels.isEmpty()) {
                    showPlaybackError("Kanal listesi alınamadı");
                    return;
                }
                startAfterCatalogLoad();
                uiHandler.postDelayed(() -> startChannelHealthSweep(sweepGeneration), 4_000L);
            });
        }, "channel-catalog").start();
    }

    private void rebuildChannelList() {
        channelList.removeAllViews();
        channelRows.clear();
        String foldedQuery = foldSearch(searchQuery);
        int visibleCount = 0;

        for (int i = 0; i < channels.size(); i++) {
            Channel channel = channels.get(i);
            if (!foldedQuery.isEmpty() && !foldSearch(channel.name).contains(foldedQuery)) {
                continue;
            }
            visibleCount++;
            TextView row = text(formatChannelRow(i), 19);
            row.setId(View.generateViewId());
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setSingleLine(true);
            row.setFocusable(true);
            row.setTag(i);
            row.setBackgroundResource(R.drawable.channel_row_background);
            row.setOnFocusChangeListener(this::styleFocusedRow);
            row.setOnClickListener(view -> {
                int selectedIndex = (Integer) view.getTag();
                if (selectedIndex != currentIndex || !isCurrentChannelActive()) {
                    tune(selectedIndex);
                }
                showChannelPanel(false);
            });

            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(62));
            rowParams.setMargins(0, 0, 0, dp(5));
            channelList.addView(row, rowParams);
            channelRows.add(row);
        }

        if (visibleCount == 0) {
            TextView empty = text("Eşleşen kanal bulunamadı", 18);
            empty.setTextColor(0xffaab7c9);
            empty.setGravity(Gravity.CENTER);
            channelList.addView(empty, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(100)));
        }

        if (searchQuery.isEmpty()) {
            channelCount.setText(String.format(Locale.forLanguageTag("tr-TR"),
                    "%d kanal", channels.size()));
            channelSearchButton.setText("Kanal ara  ·  YEŞİL");
        } else {
            channelCount.setText(String.format(Locale.forLanguageTag("tr-TR"),
                    "%d/%d sonuç", visibleCount, channels.size()));
            channelSearchButton.setText("Arama: " + searchQuery + "  ·  YEŞİL");
        }
    }

    private String formatChannelRow(int index) {
        Channel channel = channels.get(index);
        return String.format(Locale.ROOT, "%03d    %s%s",
                channel.number, channel.name, index == currentIndex ? "    • CANLI" : "");
    }

    private void refreshChannelRows() {
        for (TextView row : channelRows) {
            if (row.getTag() instanceof Integer) {
                int index = (Integer) row.getTag();
                row.setText(formatChannelRow(index));
                if (!row.hasFocus()) {
                    row.setTextColor(index == currentIndex ? 0xffff6973 : Color.WHITE);
                }
            }
        }
    }

    private void styleFocusedRow(View view, boolean hasFocus) {
        view.animate().scaleX(hasFocus ? 1.025f : 1f).scaleY(hasFocus ? 1.025f : 1f)
                .setDuration(110).start();
        boolean current = view.getTag() instanceof Integer
                && (Integer) view.getTag() == currentIndex;
        ((TextView) view).setTextColor(hasFocus
                ? Color.WHITE
                : current ? 0xffff6973 : Color.WHITE);
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
                .setTitle("Türkiye Canlı TV'ye hoş geldiniz")
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
        if (index == currentIndex && startPlayback && isCurrentChannelActive()) {
            showChannelPanel(false);
            showSettingsPanel(false);
            scheduleTopStatusBarHide();
            return;
        }
        currentIndex = index;
        refreshChannelRows();
        tuneGeneration++;
        automaticRetryCount = 0;
        Channel channel = channels.get(index);
        updateNowPlaying(channel);
        setLiveState(startPlayback ? "YÜKLENİYOR" : "HAZIR");

        uiHandler.removeCallbacks(hideChannelInfo);
        channelInfo.setVisibility(View.GONE);
        player.stop();
        player.clearMediaItems();
        activeStreamUrl = null;
        getSharedPreferences(PREFS, 0).edit().putInt(LAST_CHANNEL_KEY, channel.number).apply();

        if (!startPlayback) {
            waitingForManualStart = true;
            showLoading("▶ tuşuna basarak yayını başlatın");
            loadingSpinner.setVisibility(View.GONE);
            return;
        }
        startChannelPlayback(channel, tuneGeneration);
    }

    private void startChannelPlayback(Channel channel, int generation) {
        waitingForManualStart = false;
        showLoading("Yayın yükleniyor…");

        String cachedStream = resolvedStreams.get(channel.number);
        Set<String> excluded = failedStreams.get(channel.number);
        if (cachedStream != null && (excluded == null || !excluded.contains(cachedStream))) {
            playStream(cachedStream);
            return;
        }

        Set<String> excludedSnapshot = excluded == null
                ? Collections.emptySet() : new HashSet<>(excluded);
        resolverExecutor.execute(() -> {
            String resolvedUrl = ChannelRepository.resolvePlaybackUrl(channel, excludedSnapshot);
            runOnUiThread(() -> {
                if (generation != tuneGeneration) {
                    return;
                }
                if (resolvedUrl != null && isPlayableStream(resolvedUrl)) {
                    resolvedStreams.put(channel.number, resolvedUrl);
                    playStream(resolvedUrl);
                } else {
                    openWebFallback(channel, generation);
                }
            });
        });
    }

    private void playStream(String url) {
        activeStreamUrl = url;
        String value = url.toLowerCase(Locale.ROOT);
        MediaItem.Builder item = new MediaItem.Builder().setUri(url);
        if (value.contains(".m3u8") || value.contains("format=m3u8")) {
            item.setMimeType(MimeTypes.APPLICATION_M3U8);
        } else if (value.contains(".mpd") || value.contains("format=mpd")) {
            item.setMimeType(MimeTypes.APPLICATION_MPD);
        }
        player.setMediaItem(item.build());
        player.prepare();
        player.play();
        root.requestFocus();
    }

    private void retryAfterPlayerError() {
        if (channels.isEmpty()) {
            showPlaybackError("Yayın açılamadı");
            return;
        }
        Channel channel = channels.get(currentIndex);
        if (activeStreamUrl != null) {
            Set<String> failed = failedStreams.get(channel.number);
            if (failed == null) {
                failed = new HashSet<>();
                failedStreams.put(channel.number, failed);
            }
            failed.add(activeStreamUrl);
        }
        resolvedStreams.remove(channel.number);
        if (automaticRetryCount < 2) {
            automaticRetryCount++;
            int generation = tuneGeneration;
            showLoading(automaticRetryCount == 1
                    ? "Alternatif yayın deneniyor…"
                    : "Kanal kaynağı yenileniyor…");
            uiHandler.postDelayed(() -> {
                if (generation == tuneGeneration) {
                    startChannelPlayback(channel, generation);
                }
            }, 350L);
        } else {
            openWebFallback(channel, tuneGeneration);
        }
    }

    private void openWebFallback(Channel channel, int generation) {
        if (generation != tuneGeneration || channels.isEmpty()
                || channels.get(currentIndex).number != channel.number) {
            return;
        }
        tuneGeneration++;
        automaticRetryCount = 0;
        activeStreamUrl = null;
        player.stop();
        player.clearMediaItems();
        showLoading(null);
        setLiveState("WEB OYNATICI");
        Toast.makeText(this, channel.name + " web oynatıcısıyla açılıyor",
                Toast.LENGTH_SHORT).show();
        webFallbackOpen = true;
        startActivityForResult(WebPlayerActivity.createIntent(this, channel), WEB_PLAYER_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != WEB_PLAYER_REQUEST) {
            return;
        }
        webFallbackOpen = false;
        int delta = resultCode == RESULT_OK ? WebPlayerActivity.channelDelta(data) : 0;
        if (delta != 0 && !channels.isEmpty()) {
            tune((currentIndex + delta + channels.size()) % channels.size());
            return;
        }
        setLiveState("HAZIR");
        showChannelPanel(true);
    }

    private void registerNetworkMonitoring() {
        connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null) {
            return;
        }
        networkUnavailable = !isNetworkConnected();
        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                boolean shouldRetry = networkUnavailable;
                networkUnavailable = false;
                if (shouldRetry) {
                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed() || webFallbackOpen) {
                            return;
                        }
                        Toast.makeText(MainActivity.this,
                                "Bağlantı geri geldi, yayın yenileniyor", Toast.LENGTH_SHORT).show();
                        if (channels.isEmpty()) {
                            loadChannels();
                        } else {
                            retryCurrentChannel();
                        }
                    });
                }
            }

            @Override
            public void onLost(Network network) {
                if (!isNetworkConnected()) {
                    networkUnavailable = true;
                    runOnUiThread(() -> {
                        if (!isFinishing() && !webFallbackOpen) {
                            setLiveState("BAĞLANTI YOK");
                        }
                    });
                }
            }
        };
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                connectivityManager.registerDefaultNetworkCallback(networkCallback);
            } else {
                NetworkRequest request = new NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .build();
                connectivityManager.registerNetworkCallback(request, networkCallback);
            }
        } catch (RuntimeException ignored) {
            networkCallback = null;
        }
    }

    private boolean isNetworkConnected() {
        if (connectivityManager == null) {
            return false;
        }
        Network active = connectivityManager.getActiveNetwork();
        NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(active);
        return capabilities != null
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    private void retryCurrentChannel() {
        if (channels.isEmpty()) {
            return;
        }
        resolvedStreams.remove(channels.get(currentIndex).number);
        failedStreams.remove(channels.get(currentIndex).number);
        player.stop();
        tune(currentIndex);
    }

    private boolean isCurrentChannelActive() {
        return player != null
                && player.getCurrentMediaItem() != null
                && player.getPlaybackState() != Player.STATE_IDLE
                && player.getPlaybackState() != Player.STATE_ENDED
                && player.getPlayerError() == null;
    }

    private void prefetchAdjacentStreams(int centerIndex) {
        if (channels.size() < 2) {
            return;
        }
        int[] indexes = {
                (centerIndex + 1) % channels.size(),
                (centerIndex - 1 + channels.size()) % channels.size()
        };
        for (int index : indexes) {
            Channel channel = channels.get(index);
            if (resolvedStreams.containsKey(channel.number)
                    || !resolvingChannels.add(channel.number)) {
                continue;
            }
            Set<String> excluded = failedStreams.get(channel.number);
            Set<String> excludedSnapshot = excluded == null
                    ? Collections.emptySet() : new HashSet<>(excluded);
            resolverExecutor.execute(() -> {
                try {
                    String resolvedUrl = ChannelRepository.resolvePlaybackUrl(channel, excludedSnapshot);
                    if (resolvedUrl != null && isPlayableStream(resolvedUrl)) {
                        runOnUiThread(() -> resolvedStreams.put(channel.number, resolvedUrl));
                    }
                } finally {
                    resolvingChannels.remove(channel.number);
                }
            });
        }
    }

    private void startChannelHealthSweep(int generation) {
        if (generation != healthSweepGeneration || channels.isEmpty()) {
            return;
        }
        List<Channel> snapshot = new ArrayList<>(channels);
        for (Channel channel : snapshot) {
            if (resolvedStreams.containsKey(channel.number)) {
                continue;
            }
            healthExecutor.execute(() -> {
                String resolvedUrl = ChannelRepository.resolvePlaybackUrl(channel);
                if (generation != healthSweepGeneration) {
                    return;
                }
                if (resolvedUrl != null && isPlayableStream(resolvedUrl)) {
                    runOnUiThread(() -> {
                        if (generation == healthSweepGeneration
                                && findChannelIndexExact(channel.number) >= 0) {
                            resolvedStreams.put(channel.number, resolvedUrl);
                        }
                    });
                }
            });
        }
    }

    private boolean isPlayableStream(String url) {
        String value = url.toLowerCase(Locale.ROOT);
        return value.contains(".m3u8")
                || value.contains(".mpd")
                || value.contains(".mp4")
                || value.contains("format=m3u8")
                || value.contains("format=mpd");
    }

    private void updateNowPlaying(Channel channel) {
        if (nowPlayingNumber != null) {
            nowPlayingNumber.setText(String.format(Locale.forLanguageTag("tr-TR"),
                    "KANAL %03d", channel.number));
        }
        if (nowPlayingTitle != null) {
            nowPlayingTitle.setText(channel.name);
        }
    }

    private void setLiveState(String state) {
        showTopStatusBar();
        if (liveBadge != null) {
            liveBadge.setText(state);
        }
    }

    private void showChannelInfo(Channel channel) {
        showChannelInfo(channel, false);
    }

    private void showChannelInfo(Channel channel, boolean force) {
        showTopStatusBar();
        updateNowPlaying(channel);
        channelInfo.setText(String.format(Locale.forLanguageTag("tr-TR"),
                "%03d  %s\nP+ / P-  Kanal    •    OK  Rehber    •    INFO  Detay",
                channel.number, channel.name));
        channelInfo.animate().cancel();
        channelInfo.setAlpha(0f);
        channelInfo.setTranslationY(dp(14));
        channelInfo.setVisibility(View.VISIBLE);
        channelInfo.bringToFront();
        channelInfo.animate().alpha(1f).translationY(0f).setDuration(190).start();
        uiHandler.removeCallbacks(hideChannelInfo);
        int seconds = getSharedPreferences(PREFS, 0).getInt(INFO_SECONDS_KEY, 5);
        if (seconds == 0 && !force) {
            channelInfo.setVisibility(View.GONE);
            scheduleTopStatusBarHide();
        } else {
            uiHandler.postDelayed(hideChannelInfo, (seconds == 0 ? 5 : seconds) * 1000L);
            uiHandler.removeCallbacks(hideTopStatusBar);
            uiHandler.postDelayed(hideTopStatusBar,
                    Math.max(TOP_STATUS_VISIBLE_MS, (seconds == 0 ? 5 : seconds) * 1000L));
        }
    }

    private void showPlaybackError(String message) {
        setLiveState("BAĞLANTI YOK");
        showLoading(message + "\nKIRMIZI ile yeniden deneyin veya P+ / P- kullanın");
        loadingSpinner.setVisibility(View.GONE);
    }

    private void showLoading(String message) {
        int animationGeneration = ++loadingAnimationGeneration;
        if (message == null) {
            loadingOverlay.animate().cancel();
            loadingOverlay.animate().alpha(0f).setDuration(180).withEndAction(() -> {
                if (animationGeneration == loadingAnimationGeneration) {
                    loadingOverlay.setVisibility(View.GONE);
                    loadingOverlay.setAlpha(1f);
                }
            }).start();
            return;
        }
        loadingOverlay.animate().cancel();
        loadingOverlay.setAlpha(1f);
        loadingSpinner.setVisibility(View.VISIBLE);
        loadingMessage.setText(message);
        loadingOverlay.setVisibility(View.VISIBLE);
        loadingOverlay.bringToFront();
        numberEntry.bringToFront();
        channelPanel.bringToFront();
        settingsPanel.bringToFront();
    }

    private void showTopStatusBar() {
        if (topStatusBar == null
                || channelPanel.getVisibility() == View.VISIBLE
                || settingsPanel.getVisibility() == View.VISIBLE) {
            return;
        }
        uiHandler.removeCallbacks(hideTopStatusBar);
        topStatusBar.animate().cancel();
        topStatusBar.setVisibility(View.VISIBLE);
        topStatusBar.setAlpha(1f);
        topStatusBar.setTranslationY(0f);
        topStatusBar.bringToFront();
        loadingOverlay.bringToFront();
        channelInfo.bringToFront();
        channelPanel.bringToFront();
        settingsPanel.bringToFront();
    }

    private void scheduleTopStatusBarHide() {
        showTopStatusBar();
        uiHandler.postDelayed(hideTopStatusBar, TOP_STATUS_VISIBLE_MS);
    }

    private void hideTopStatusBarNow() {
        if (topStatusBar == null) {
            return;
        }
        uiHandler.removeCallbacks(hideTopStatusBar);
        topStatusBar.animate().cancel();
        topStatusBar.setVisibility(View.GONE);
        topStatusBar.setAlpha(1f);
        topStatusBar.setTranslationY(0f);
    }

    private void showSettingsPanel(boolean show) {
        if (show) {
            hideTopStatusBarNow();
            channelPanel.animate().cancel();
            channelPanel.setVisibility(View.GONE);
            settingsPanel.animate().cancel();
            settingsPanel.setAlpha(0f);
            settingsPanel.setTranslationX(dp(120));
            settingsPanel.setVisibility(View.VISIBLE);
            settingsPanel.bringToFront();
            refreshSettingLabels();
            settingsPanel.animate().alpha(1f).translationX(0f).setDuration(190).start();
            if (!settingsRows.isEmpty()) {
                settingsRows.get(0).requestFocus();
            }
        } else {
            if (player != null && player.getPlaybackState() == Player.STATE_READY) {
                scheduleTopStatusBarHide();
            }
            settingsPanel.animate().cancel();
            if (settingsPanel.getVisibility() == View.VISIBLE) {
                settingsPanel.animate().alpha(0f).translationX(dp(100)).setDuration(150)
                        .withEndAction(() -> {
                            settingsPanel.setVisibility(View.GONE);
                            settingsPanel.setTranslationX(0f);
                            settingsPanel.setAlpha(1f);
                            root.requestFocus();
                        }).start();
            } else {
                root.requestFocus();
            }
        }
    }

    private void showChannelPanel(boolean show) {
        if (show) {
            hideTopStatusBarNow();
            settingsPanel.animate().cancel();
            settingsPanel.setVisibility(View.GONE);
            channelPanel.animate().cancel();
            channelPanel.setAlpha(0f);
            channelPanel.setTranslationX(-dp(120));
            channelPanel.setVisibility(View.VISIBLE);
            channelPanel.bringToFront();
            channelPanel.animate().alpha(1f).translationX(0f).setDuration(190).start();

            TextView targetRow = null;
            for (TextView row : channelRows) {
                if (row.getTag() instanceof Integer && (Integer) row.getTag() == currentIndex) {
                    targetRow = row;
                    break;
                }
            }
            if (targetRow == null && !channelRows.isEmpty()) {
                targetRow = channelRows.get(0);
            }
            if (targetRow != null) {
                targetRow.setNextFocusUpId(channelSettingsButton.getId());
                channelSettingsButton.setNextFocusDownId(targetRow.getId());
                TextView focusTarget = targetRow;
                channelScroll.post(() -> {
                    channelScroll.smoothScrollTo(0, Math.max(0,
                            focusTarget.getTop() - channelScroll.getHeight() / 3));
                    focusTarget.requestFocus();
                });
            } else {
                channelSearchButton.requestFocus();
            }
        } else {
            if (player != null && player.getPlaybackState() == Player.STATE_READY) {
                scheduleTopStatusBarHide();
            }
            channelPanel.animate().cancel();
            if (channelPanel.getVisibility() == View.VISIBLE) {
                channelPanel.animate().alpha(0f).translationX(-dp(100)).setDuration(150)
                        .withEndAction(() -> {
                            channelPanel.setVisibility(View.GONE);
                            channelPanel.setTranslationX(0f);
                            channelPanel.setAlpha(1f);
                            root.requestFocus();
                        }).start();
            } else {
                root.requestFocus();
            }
        }
    }

    private void showSearchDialog() {
        if (searchDialog != null && searchDialog.isShowing()) {
            return;
        }
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("Kanal adı");
        input.setText(searchQuery);
        input.setSelectAllOnFocus(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        int padding = dp(20);
        input.setPadding(padding, padding, padding, padding);

        searchDialog = new AlertDialog.Builder(this)
                .setTitle("Kanal ara")
                .setView(input)
                .setPositiveButton("Ara", (dialog, which) -> applySearch(input.getText().toString()))
                .setNeutralButton("Temizle", (dialog, which) -> applySearch(""))
                .setNegativeButton("İptal", null)
                .create();
        searchDialog.setOnShowListener(ignored -> {
            input.requestFocus();
            searchDialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            InputMethodManager keyboard = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            keyboard.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
        });
        searchDialog.setOnDismissListener(ignored -> searchDialog = null);
        searchDialog.show();
    }

    private void applySearch(String query) {
        searchQuery = query == null ? "" : query.trim();
        rebuildChannelList();
        showChannelPanel(true);
    }

    private String foldSearch(String value) {
        return value.toUpperCase(Locale.forLanguageTag("tr-TR"))
                .replace("İ", "I")
                .replace("Ş", "S")
                .replace("Ğ", "G")
                .replace("Ü", "U")
                .replace("Ö", "O")
                .replace("Ç", "C")
                .replaceAll("[^A-Z0-9]", "");
    }

    private void tuneNumber(int number) {
        int index = findChannelIndex(number);
        if (index >= 0 && index < channels.size() && channels.get(index).number == number) {
            searchQuery = "";
            rebuildChannelList();
            showChannelPanel(false);
            tune(index);
        } else {
            Toast.makeText(this, "Kanal " + number + " listede yok", Toast.LENGTH_SHORT).show();
        }
    }

    private int findChannelIndex(int number) {
        int exact = findChannelIndexExact(number);
        return exact >= 0 ? exact : 0;
    }

    private int findChannelIndexExact(int number) {
        for (int i = 0; i < channels.size(); i++) {
            if (channels.get(i).number == number) {
                return i;
            }
        }
        return -1;
    }

    private TextView text(String value, int sizeSp) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextColor(Color.WHITE);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp * compactTextScale());
        view.setFontFeatureSettings("kern");
        view.setLineSpacing(0f, 1.08f);
        view.setPadding(dp(18), dp(12), dp(18), dp(12));
        return view;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density
                * compactLayoutScale());
    }

    private float compactLayoutScale() {
        int widthDp = getResources().getConfiguration().screenWidthDp;
        int heightDp = getResources().getConfiguration().screenHeightDp;
        float viewportScale = Math.min(1f, Math.min(widthDp / 960f, heightDp / 540f));
        return COMPACT_LAYOUT_SCALE * Math.max(0.82f, viewportScale);
    }

    private float compactTextScale() {
        return COMPACT_TEXT_SCALE * (compactLayoutScale() / COMPACT_LAYOUT_SCALE);
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
        if (searchDialog != null && searchDialog.isShowing()) {
            return super.dispatchKeyEvent(event);
        }
        if (event.getAction() != KeyEvent.ACTION_DOWN) {
            return super.dispatchKeyEvent(event);
        }

        int key = event.getKeyCode();
        if (key == KeyEvent.KEYCODE_PROG_RED) {
            showChannelPanel(false);
            showSettingsPanel(false);
            retryCurrentChannel();
            return true;
        }
        if (key == KeyEvent.KEYCODE_PROG_GREEN || key == KeyEvent.KEYCODE_SEARCH) {
            showSearchDialog();
            return true;
        }
        if (key == KeyEvent.KEYCODE_PROG_YELLOW) {
            showChannelPanel(channelPanel.getVisibility() != View.VISIBLE);
            return true;
        }
        if (key == KeyEvent.KEYCODE_PROG_BLUE || key == KeyEvent.KEYCODE_SETTINGS) {
            showSettingsPanel(settingsPanel.getVisibility() != View.VISIBLE);
            return true;
        }

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

        if (settingsPanel.getVisibility() == View.VISIBLE) {
            if (key == KeyEvent.KEYCODE_BACK || key == KeyEvent.KEYCODE_DPAD_RIGHT) {
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
        if (key == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                || key == KeyEvent.KEYCODE_MEDIA_PLAY
                || key == KeyEvent.KEYCODE_MEDIA_PAUSE) {
            if (waitingForManualStart && !channels.isEmpty()) {
                startChannelPlayback(channels.get(currentIndex), tuneGeneration);
                return true;
            }
            if (player.isPlaying()) {
                player.pause();
            } else {
                player.play();
            }
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (player != null && resumePlaybackOnStart) {
            player.play();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        enterImmersiveMode();
        if (appUpdateManager != null) {
            appUpdateManager.onResume();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        resumePlaybackOnStart = player != null && player.isPlaying();
        if (player != null) {
            player.pause();
        }
    }

    @Override
    protected void onDestroy() {
        healthSweepGeneration++;
        uiHandler.removeCallbacksAndMessages(null);
        if (searchDialog != null) {
            searchDialog.dismiss();
        }
        if (player != null) {
            player.release();
        }
        if (appUpdateManager != null) {
            appUpdateManager.destroy();
        }
        if (connectivityManager != null && networkCallback != null) {
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback);
            } catch (RuntimeException ignored) {
                // Callback may already have been removed by the system.
            }
        }
        resolverExecutor.shutdownNow();
        healthExecutor.shutdownNow();
        super.onDestroy();
    }
}
