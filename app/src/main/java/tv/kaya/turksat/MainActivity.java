package tv.kaya.turksat;

import android.app.AlertDialog;
import android.app.PictureInPictureParams;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.TypedValue;
import android.util.Rational;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.Tracks;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;
import androidx.media3.session.MediaSession;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

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
    private static final int EXPORT_SETTINGS_REQUEST = 3403;
    private static final int IMPORT_SETTINGS_REQUEST = 3404;
    private static final String PREFS = "tv_settings";
    private static final String LAST_CHANNEL_KEY = "last_channel";
    private static final String ONBOARDING_KEY = "onboarding_complete";
    private static final String STARTUP_MODE_KEY = "startup_mode";
    private static final String STARTUP_AUTOPLAY_KEY = "startup_autoplay";
    private static final String AUTO_LAUNCH_KEY = "auto_launch_on_boot";
    private static final String INFO_SECONDS_KEY = "info_seconds";
    private static final String ASPECT_MODE_KEY = "aspect_mode";
    private static final String QUALITY_MODE_KEY = "quality_mode";
    private static final String AUDIO_LANGUAGE_KEY = "audio_language";
    private static final String SUBTITLE_LANGUAGE_KEY = "subtitle_language";
    private static final String PIP_ENABLED_KEY = "pip_enabled";
    private static final String[] CATEGORIES = {
            "Tümü", "Favoriler", "Son İzlenen", "Ulusal", "Haber",
            "Spor", "Çocuk", "Belgesel", "Müzik", "Dini", "Yerel"
    };
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
    private final Map<Integer, ChannelStatus> channelStatuses = new HashMap<>();
    private final Set<Integer> resolvingChannels = Collections.synchronizedSet(new HashSet<>());
    private final Set<String> unlockedChannels = new HashSet<>();
    private final ExecutorService resolverExecutor = Executors.newFixedThreadPool(3);
    private final ExecutorService healthExecutor = Executors.newFixedThreadPool(4);

    private FrameLayout root;
    private PlayerView playerView;
    private ExoPlayer player;
    private MediaSession mediaSession;
    private FrameLayout loadingOverlay;
    private LinearLayout topStatusBar;
    private LinearLayout channelPanel;
    private LinearLayout settingsPanel;
    private LinearLayout settingsList;
    private ScrollView channelScroll;
    private LinearLayout channelList;
    private LinearLayout categoryTabs;
    private TextView channelCount;
    private TextView channelInfo;
    private TextView numberEntry;
    private TextView loadingMessage;
    private ProgressBar loadingSpinner;
    private TextView clockView;
    private TextView panelClockView;
    private TextView nowPlayingNumber;
    private TextView nowPlayingTitle;
    private TextView nowPlayingProgram;
    private TextView liveBadge;
    private TextView channelSearchButton;
    private TextView channelSettingsButton;
    private TextView channelFavoriteButton;
    private TextView channelGuideButton;
    private TextView startupSetting;
    private TextView autoplaySetting;
    private TextView autoLaunchSetting;
    private TextView infoSetting;
    private TextView aspectSetting;
    private TextView qualitySetting;
    private TextView audioSetting;
    private TextView subtitleSetting;
    private TextView pipSetting;
    private TextView favoriteSetting;
    private TextView lockSetting;
    private TextView epgSetting;
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
    private String selectedCategory = "Tümü";
    private EpgRepository.GuideData epgGuide;

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
            if (!channels.isEmpty()) {
                updateNowPlaying(channels.get(currentIndex));
                refreshChannelRows();
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

        nowPlayingTitle = text("Türkiye Canlı TV", 21);
        nowPlayingTitle.setTypeface(nowPlayingTitle.getTypeface(), android.graphics.Typeface.BOLD);
        nowPlayingTitle.setSingleLine(true);
        nowPlayingTitle.setPadding(0, 0, 0, 0);
        channelIdentity.addView(nowPlayingTitle);

        nowPlayingProgram = text("Program rehberi hazırlanıyor", 12);
        nowPlayingProgram.setTextColor(0xffb8c4d3);
        nowPlayingProgram.setSingleLine(true);
        nowPlayingProgram.setPadding(0, dp(1), 0, 0);
        channelIdentity.addView(nowPlayingProgram);
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
        loadingOverlay.setBackgroundColor(0x33000000);
        loadingOverlay.setFocusable(false);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER);
        content.setPadding(dp(28), dp(20), dp(28), dp(20));
        content.setBackgroundResource(R.drawable.loading_card_background);

        loadingSpinner = new ProgressBar(this);
        loadingSpinner.getIndeterminateDrawable().setTint(0xffe30a17);
        LinearLayout.LayoutParams spinnerParams = new LinearLayout.LayoutParams(dp(38), dp(38));
        spinnerParams.gravity = Gravity.CENTER_HORIZONTAL;
        spinnerParams.setMargins(0, 0, 0, dp(16));
        content.addView(loadingSpinner, spinnerParams);

        loadingMessage = text("Kanallar hazırlanıyor…", 18);
        loadingMessage.setGravity(Gravity.CENTER);
        loadingMessage.setPadding(0, dp(4), 0, 0);
        content.addView(loadingMessage, new LinearLayout.LayoutParams(dp(420),
                ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout.LayoutParams loadingParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        loadingParams.setMargins(0, 0, 0, dp(40));
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
        mediaSession = new MediaSession.Builder(this, player).build();
        playerView.setPlayer(player);
        applyTrackPreferences();
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
                        Channel readyChannel = channels.get(currentIndex);
                        channelStatuses.put(readyChannel.number, ChannelStatus.NATIVE);
                        ChannelUserData.recordRecent(MainActivity.this, readyChannel);
                        refreshChannelRows();
                        showChannelInfo(readyChannel);
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

            @Override
            public void onTracksChanged(Tracks tracks) {
                refreshSettingLabels();
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

        HorizontalScrollView categoryScroll = new HorizontalScrollView(this);
        categoryScroll.setHorizontalScrollBarEnabled(false);
        categoryScroll.setFillViewport(false);
        categoryTabs = new LinearLayout(this);
        categoryTabs.setOrientation(LinearLayout.HORIZONTAL);
        categoryScroll.addView(categoryTabs, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        channelPanel.addView(categoryScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
        rebuildCategoryTabs();

        channelSearchButton = addChannelAction("⌕  Kanal ara  ·  YEŞİL", this::showSearchDialog);
        channelFavoriteButton = addChannelAction("★  Geçerli kanalı favorile", this::toggleCurrentFavorite);
        channelGuideButton = addChannelAction("▦  Geçerli kanal program akışı",
                this::showCurrentProgramGuide);
        channelSettingsButton = addChannelAction("⚙  Ayarlar  ·  MAVİ", () -> showSettingsPanel(true));
        channelSearchButton.setNextFocusDownId(channelFavoriteButton.getId());
        channelFavoriteButton.setNextFocusUpId(channelSearchButton.getId());
        channelFavoriteButton.setNextFocusDownId(channelGuideButton.getId());
        channelGuideButton.setNextFocusUpId(channelFavoriteButton.getId());
        channelGuideButton.setNextFocusDownId(channelSettingsButton.getId());
        channelSettingsButton.setNextFocusUpId(channelGuideButton.getId());

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

        TextView help = text("OK Seç  ·  OK basılı Favori  ·  SAĞ Kapat", 12);
        help.setTextColor(0xffaab7c9);
        channelPanel.addView(help, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private TextView addChannelAction(String label, Runnable action) {
        TextView row = text(label, 16);
        row.setId(View.generateViewId());
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setFocusable(true);
        row.setBackgroundResource(R.drawable.channel_row_background);
        row.setOnFocusChangeListener(this::styleFocusedRow);
        row.setOnClickListener(view -> action.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        params.setMargins(0, 0, dp(4), dp(5));
        channelPanel.addView(row, params);
        return row;
    }

    private void rebuildCategoryTabs() {
        if (categoryTabs == null) {
            return;
        }
        categoryTabs.removeAllViews();
        for (String category : CATEGORIES) {
            TextView tab = text(category, 14);
            tab.setGravity(Gravity.CENTER);
            tab.setFocusable(true);
            tab.setTag(category);
            tab.setSingleLine(true);
            tab.setBackgroundResource(R.drawable.channel_row_background);
            tab.setTextColor(category.equals(selectedCategory) ? 0xffff6973 : Color.WHITE);
            tab.setOnFocusChangeListener(this::styleFocusedRow);
            tab.setOnClickListener(view -> {
                selectedCategory = category;
                searchQuery = "";
                rebuildCategoryTabs();
                rebuildChannelList();
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(42));
            params.setMargins(0, 0, dp(5), dp(4));
            categoryTabs.addView(tab, params);
        }
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

        addSettingSection("OYNATMA");
        qualitySetting = addSettingRow(this::cycleQualityMode);
        audioSetting = addSettingRow(this::showAudioLanguageDialog);
        subtitleSetting = addSettingRow(this::showSubtitleLanguageDialog);
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

        addSettingSection("TV DENEYİMİ");
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
        pipSetting = addSettingRow(() -> {
            boolean enabled = getSharedPreferences(PREFS, 0)
                    .getBoolean(PIP_ENABLED_KEY, true);
            getSharedPreferences(PREFS, 0).edit().putBoolean(PIP_ENABLED_KEY, !enabled).apply();
            refreshSettingLabels();
        });

        addSettingSection("KÜTÜPHANE VE GÜVENLİK");
        favoriteSetting = addSettingRow(this::toggleCurrentFavorite);
        lockSetting = addSettingRow(this::requestToggleCurrentLock);
        addSettingRow(this::requestPinChange).setText("Ebeveyn PIN'ini ayarla / değiştir");
        addSettingRow(this::exportSettings).setText("Ayarları ve favorileri yedekle");
        addSettingRow(this::importSettings).setText("Ayar yedeğini geri yükle");

        addSettingSection("VERİ VE SİSTEM");
        epgSetting = addSettingRow(() -> refreshEpg(true));
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

    private void addSettingSection(String label) {
        TextView header = text(label, 12);
        header.setTextColor(0xffff6973);
        header.setTypeface(header.getTypeface(), android.graphics.Typeface.BOLD);
        header.setFocusable(false);
        header.setPadding(dp(8), dp(14), dp(8), dp(6));
        settingsList.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
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
        String quality = getSharedPreferences(PREFS, 0).getString(QUALITY_MODE_KEY, "auto");
        String qualityLabel = "low".equals(quality) ? "Düşük · 480p" : "medium".equals(quality)
                ? "Orta · 720p" : "high".equals(quality) ? "Yüksek · 1080p" : "Otomatik";
        qualitySetting.setText("Yayın kalitesi  ·  " + qualityLabel);
        String audio = getSharedPreferences(PREFS, 0).getString(AUDIO_LANGUAGE_KEY, "auto");
        audioSetting.setText("Ses dili  ·  " + languageLabel(audio, "Otomatik"));
        String subtitle = getSharedPreferences(PREFS, 0)
                .getString(SUBTITLE_LANGUAGE_KEY, "off");
        subtitleSetting.setText("Altyazı  ·  " + ("off".equals(subtitle)
                ? "Kapalı" : languageLabel(subtitle, "Otomatik")));
        pipSetting.setText("Görüntü içinde görüntü  ·  "
                + (getSharedPreferences(PREFS, 0).getBoolean(PIP_ENABLED_KEY, true)
                ? "Açık" : "Kapalı"));
        if (!channels.isEmpty()) {
            Channel channel = channels.get(currentIndex);
            favoriteSetting.setText((ChannelUserData.isFavorite(this, channel) ? "★" : "☆")
                    + "  Geçerli kanal favorisi");
            lockSetting.setText((ChannelUserData.isLocked(this, channel) ? "🔒" : "🔓")
                    + "  Geçerli kanal kilidi");
        } else {
            favoriteSetting.setText("☆  Geçerli kanal favorisi");
            lockSetting.setText("🔓  Geçerli kanal kilidi");
        }
        epgSetting.setText("Program rehberini yenile  ·  "
                + (epgGuide == null ? "Bekliyor" : epgGuide.channelCount() + " kanal"));
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

    private void cycleQualityMode() {
        String current = getSharedPreferences(PREFS, 0).getString(QUALITY_MODE_KEY, "auto");
        String next = "auto".equals(current) ? "low" : "low".equals(current)
                ? "medium" : "medium".equals(current) ? "high" : "auto";
        getSharedPreferences(PREFS, 0).edit().putString(QUALITY_MODE_KEY, next).apply();
        applyTrackPreferences();
        refreshSettingLabels();
    }

    private void applyTrackPreferences() {
        if (player == null) {
            return;
        }
        TrackSelectionParameters.Builder builder = player.getTrackSelectionParameters().buildUpon();
        String quality = getSharedPreferences(PREFS, 0).getString(QUALITY_MODE_KEY, "auto");
        if ("low".equals(quality)) {
            builder.setMaxVideoSize(854, 480).setMaxVideoBitrate(1_800_000);
        } else if ("medium".equals(quality)) {
            builder.setMaxVideoSize(1280, 720).setMaxVideoBitrate(4_500_000);
        } else if ("high".equals(quality)) {
            builder.setMaxVideoSize(1920, 1080).setMaxVideoBitrate(10_000_000);
        } else {
            builder.setMaxVideoSize(Integer.MAX_VALUE, Integer.MAX_VALUE)
                    .setMaxVideoBitrate(Integer.MAX_VALUE);
        }
        String audio = getSharedPreferences(PREFS, 0).getString(AUDIO_LANGUAGE_KEY, "auto");
        builder.setPreferredAudioLanguage("auto".equals(audio) ? null : audio);
        String subtitle = getSharedPreferences(PREFS, 0)
                .getString(SUBTITLE_LANGUAGE_KEY, "off");
        builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, "off".equals(subtitle));
        builder.setPreferredTextLanguage("off".equals(subtitle) || "auto".equals(subtitle)
                ? null : subtitle);
        player.setTrackSelectionParameters(builder.build());
    }

    private void showAudioLanguageDialog() {
        List<String> codes = availableTrackLanguages(C.TRACK_TYPE_AUDIO);
        ArrayList<String> choices = new ArrayList<>();
        choices.add("Otomatik");
        for (String code : codes) {
            choices.add(languageLabel(code, code));
        }
        new AlertDialog.Builder(this)
                .setTitle("Ses dili")
                .setItems(choices.toArray(new String[0]), (dialog, which) -> {
                    String value = which == 0 ? "auto" : codes.get(which - 1);
                    getSharedPreferences(PREFS, 0).edit()
                            .putString(AUDIO_LANGUAGE_KEY, value).apply();
                    applyTrackPreferences();
                    refreshSettingLabels();
                })
                .setNegativeButton("İptal", null)
                .show();
    }

    private void showSubtitleLanguageDialog() {
        List<String> codes = availableTrackLanguages(C.TRACK_TYPE_TEXT);
        ArrayList<String> choices = new ArrayList<>();
        choices.add("Kapalı");
        choices.add("Otomatik");
        for (String code : codes) {
            choices.add(languageLabel(code, code));
        }
        new AlertDialog.Builder(this)
                .setTitle("Altyazı")
                .setItems(choices.toArray(new String[0]), (dialog, which) -> {
                    String value = which == 0 ? "off" : which == 1
                            ? "auto" : codes.get(which - 2);
                    getSharedPreferences(PREFS, 0).edit()
                            .putString(SUBTITLE_LANGUAGE_KEY, value).apply();
                    applyTrackPreferences();
                    refreshSettingLabels();
                })
                .setNegativeButton("İptal", null)
                .show();
    }

    private List<String> availableTrackLanguages(int trackType) {
        ArrayList<String> values = new ArrayList<>();
        if (player == null) {
            return values;
        }
        for (Tracks.Group group : player.getCurrentTracks().getGroups()) {
            if (group.getType() != trackType) {
                continue;
            }
            for (int i = 0; i < group.length; i++) {
                Format format = group.getTrackFormat(i);
                String language = format.language;
                if (language != null && !language.trim().isEmpty()
                        && !"und".equalsIgnoreCase(language) && !values.contains(language)) {
                    values.add(language);
                }
            }
        }
        return values;
    }

    private String languageLabel(String code, String fallback) {
        if (code == null || "auto".equals(code)) {
            return fallback;
        }
        Locale locale = Locale.forLanguageTag(code);
        String label = locale.getDisplayLanguage(Locale.forLanguageTag("tr-TR"));
        return label == null || label.trim().isEmpty() ? fallback : label;
    }

    private void refreshEpg(boolean force) {
        if (force) {
            Toast.makeText(this, "Program rehberi yenileniyor", Toast.LENGTH_SHORT).show();
        }
        EpgRepository.load(this, force, (guide, fresh) -> {
            if (isFinishing() || isDestroyed()) {
                return;
            }
            epgGuide = guide;
            rebuildChannelList();
            if (!channels.isEmpty()) {
                updateNowPlaying(channels.get(currentIndex));
            }
            refreshSettingLabels();
            if (force && fresh) {
                Toast.makeText(this, guide.channelCount()
                        + " kanallık program rehberi güncellendi", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showCurrentProgramGuide() {
        if (channels.isEmpty() || epgGuide == null) {
            Toast.makeText(this, "Program rehberi henüz hazır değil", Toast.LENGTH_SHORT).show();
            return;
        }
        Channel channel = channels.get(currentIndex);
        List<EpgProgram> programs = epgGuide.upcoming(
                channel.name, System.currentTimeMillis(), 8);
        if (programs.isEmpty()) {
            Toast.makeText(this, channel.name + " için program bilgisi bulunamadı",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        String[] items = new String[programs.size()];
        for (int i = 0; i < programs.size(); i++) {
            EpgProgram program = programs.get(i);
            items[i] = program.timeRange() + "  " + program.title;
        }
        new AlertDialog.Builder(this)
                .setTitle(channel.name + " · Program akışı")
                .setItems(items, (dialog, which) -> {
                    EpgProgram program = programs.get(which);
                    if (!program.description.isEmpty()) {
                        new AlertDialog.Builder(this)
                                .setTitle(program.title)
                                .setMessage(program.timeRange() + "\n\n" + program.description)
                                .setPositiveButton("Kapat", null)
                                .show();
                    }
                })
                .setNegativeButton("Kapat", null)
                .show();
    }

    private String epgTitle(Channel channel) {
        EpgProgram current = epgGuide == null ? null
                : epgGuide.current(channel.name, System.currentTimeMillis());
        return current == null ? "Canlı yayın" : current.title;
    }

    private void toggleCurrentFavorite() {
        if (!channels.isEmpty()) {
            toggleFavorite(channels.get(currentIndex));
        }
    }

    private void toggleFavorite(Channel channel) {
        boolean enabled = ChannelUserData.toggleFavorite(this, channel);
        Toast.makeText(this, channel.name + (enabled
                ? " favorilere eklendi" : " favorilerden çıkarıldı"), Toast.LENGTH_SHORT).show();
        rebuildCategoryTabs();
        rebuildChannelList();
        refreshSettingLabels();
    }

    private void updateFavoriteLabels() {
        if (channelFavoriteButton == null || channels.isEmpty()) {
            return;
        }
        boolean favorite = ChannelUserData.isFavorite(this, channels.get(currentIndex));
        channelFavoriteButton.setText((favorite ? "★" : "☆")
                + "  Geçerli kanal favorisi");
    }

    private void requestToggleCurrentLock() {
        if (channels.isEmpty()) {
            return;
        }
        Runnable toggle = () -> {
            Channel channel = channels.get(currentIndex);
            boolean locked = ChannelUserData.toggleLocked(this, channel);
            if (!locked) {
                unlockedChannels.remove(channel.key());
            }
            Toast.makeText(this, channel.name + (locked ? " kilitlendi" : " kilidi kaldırıldı"),
                    Toast.LENGTH_SHORT).show();
            rebuildChannelList();
            refreshSettingLabels();
        };
        if (ChannelUserData.hasPin(this)) {
            requestPinVerification("Kanal kilidini değiştir", toggle);
        } else {
            requestNewPin(toggle);
        }
    }

    private void requestPinChange() {
        if (ChannelUserData.hasPin(this)) {
            requestPinVerification("Mevcut PIN", () -> requestNewPin(null));
        } else {
            requestNewPin(null);
        }
    }

    private void requestPinVerification(String title, Runnable onVerified) {
        EditText input = pinInput();
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage("Ebeveyn PIN'ini girin")
                .setView(input)
                .setPositiveButton("Doğrula", null)
                .setNegativeButton("İptal", null)
                .create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                if (ChannelUserData.verifyPin(this, input.getText().toString())) {
                    dialog.dismiss();
                    onVerified.run();
                } else {
                    input.setError("PIN yanlış");
                    input.selectAll();
                }
            });
            input.requestFocus();
        });
        dialog.show();
    }

    private void requestNewPin(Runnable afterSave) {
        EditText input = pinInput();
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Yeni ebeveyn PIN'i")
                .setMessage("4–8 rakam belirleyin")
                .setView(input)
                .setPositiveButton("Kaydet", null)
                .setNegativeButton("İptal", null)
                .create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                if (ChannelUserData.setPin(this, input.getText().toString())) {
                    dialog.dismiss();
                    Toast.makeText(this, "Ebeveyn PIN'i kaydedildi", Toast.LENGTH_SHORT).show();
                    if (afterSave != null) {
                        afterSave.run();
                    }
                } else {
                    input.setError("PIN 4–8 rakam olmalı");
                    input.selectAll();
                }
            });
            input.requestFocus();
        });
        dialog.show();
    }

    private EditText pinInput() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        input.setPadding(dp(20), dp(16), dp(20), dp(16));
        return input;
    }

    private void exportSettings() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/json")
                .putExtra(Intent.EXTRA_TITLE, "turkiye-tv-ayarlar.json");
        startActivityForResult(intent, EXPORT_SETTINGS_REQUEST);
    }

    private void importSettings() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/json");
        startActivityForResult(intent, IMPORT_SETTINGS_REQUEST);
    }

    private void writeSettingsBackup(Uri uri) {
        try (OutputStream output = getContentResolver().openOutputStream(uri, "wt")) {
            if (output == null) {
                throw new IllegalStateException("Dosya açılamadı");
            }
            output.write(ChannelUserData.exportJson(this).getBytes(StandardCharsets.UTF_8));
            Toast.makeText(this, "Ayar yedeği kaydedildi", Toast.LENGTH_SHORT).show();
        } catch (Exception error) {
            Toast.makeText(this, "Yedek kaydedilemedi", Toast.LENGTH_LONG).show();
        }
    }

    private void readSettingsBackup(Uri uri) {
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            if (input == null) {
                throw new IllegalStateException("Dosya açılamadı");
            }
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(input, StandardCharsets.UTF_8));
            StringBuilder json = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (json.length() + line.length() > 1_000_000) {
                    throw new IllegalStateException("Yedek dosyası çok büyük");
                }
                json.append(line).append('\n');
            }
            ChannelUserData.importJson(this, json.toString());
            unlockedChannels.clear();
            applyAspectMode();
            applyTrackPreferences();
            rebuildCategoryTabs();
            rebuildChannelList();
            refreshSettingLabels();
            Toast.makeText(this, "Ayarlar ve favoriler geri yüklendi",
                    Toast.LENGTH_SHORT).show();
        } catch (Exception error) {
            Toast.makeText(this, "Geçerli bir Türkiye Canlı TV yedeği seçin",
                    Toast.LENGTH_LONG).show();
        }
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
                channelStatuses.clear();
                for (Channel channel : channels) {
                    channelStatuses.put(channel.number, ChannelStatus.CHECKING);
                }
                searchQuery = "";
                rebuildChannelList();

                if (channels.isEmpty()) {
                    showPlaybackError("Kanal listesi alınamadı");
                    return;
                }
                startAfterCatalogLoad();
                refreshEpg(false);
                uiHandler.postDelayed(() -> startChannelHealthSweep(sweepGeneration), 4_000L);
            });
        }, "channel-catalog").start();
    }

    private void rebuildChannelList() {
        channelList.removeAllViews();
        channelRows.clear();
        String foldedQuery = foldSearch(searchQuery);
        List<Integer> visibleIndexes = filteredChannelIndexes(foldedQuery);

        for (int i : visibleIndexes) {
            Channel channel = channels.get(i);
            TextView row = text(formatChannelRow(i), 16);
            row.setId(View.generateViewId());
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setSingleLine(false);
            row.setMaxLines(2);
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
            row.setOnLongClickListener(view -> {
                int selectedIndex = (Integer) view.getTag();
                toggleFavorite(channels.get(selectedIndex));
                return true;
            });
            row.setTextColor(i == currentIndex ? 0xffff6973 : statusColor(channel));

            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(68));
            rowParams.setMargins(0, 0, 0, dp(5));
            channelList.addView(row, rowParams);
            channelRows.add(row);
        }

        if (visibleIndexes.isEmpty()) {
            TextView empty = text("Eşleşen kanal bulunamadı", 18);
            empty.setTextColor(0xffaab7c9);
            empty.setGravity(Gravity.CENTER);
            channelList.addView(empty, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(100)));
        }

        if (searchQuery.isEmpty() && "Tümü".equals(selectedCategory)) {
            channelCount.setText(String.format(Locale.forLanguageTag("tr-TR"), "%d kanal",
                    channels.size()));
            channelSearchButton.setText("Kanal ara  ·  YEŞİL");
        } else {
            channelCount.setText(String.format(Locale.forLanguageTag("tr-TR"),
                    "%s  ·  %d/%d", selectedCategory, visibleIndexes.size(), channels.size()));
            channelSearchButton.setText(searchQuery.isEmpty() ? "Kanal ara  ·  YEŞİL"
                    : "Arama: " + searchQuery + "  ·  YEŞİL");
        }
        updateFavoriteLabels();
    }

    private String formatChannelRow(int index) {
        Channel channel = channels.get(index);
        ChannelStatus status = statusFor(channel);
        String favorite = ChannelUserData.isFavorite(this, channel) ? "★" : " ";
        String locked = ChannelUserData.isLocked(this, channel) ? " 🔒" : "";
        EpgProgram current = epgGuide == null ? null
                : epgGuide.current(channel.name, System.currentTimeMillis());
        String programme = current == null ? channel.category()
                : current.timeRange() + "  " + current.title;
        return String.format(Locale.forLanguageTag("tr-TR"), "%s %03d  %s%s    %s %s\n     %s",
                favorite, channel.number, channel.name, locked, status.symbol, status.label, programme);
    }

    private List<Integer> filteredChannelIndexes(String foldedQuery) {
        ArrayList<Integer> result = new ArrayList<>();
        List<String> recent = ChannelUserData.recent(this);
        for (int i = 0; i < channels.size(); i++) {
            Channel channel = channels.get(i);
            if (!foldedQuery.isEmpty() && !foldSearch(channel.name).contains(foldedQuery)) {
                continue;
            }
            boolean matches = "Tümü".equals(selectedCategory)
                    || ("Favoriler".equals(selectedCategory)
                    && ChannelUserData.isFavorite(this, channel))
                    || ("Son İzlenen".equals(selectedCategory) && recent.contains(channel.key()))
                    || selectedCategory.equals(channel.category());
            if (matches) {
                result.add(i);
            }
        }
        if ("Son İzlenen".equals(selectedCategory)) {
            Collections.sort(result, (left, right) -> Integer.compare(
                    recent.indexOf(channels.get(left).key()),
                    recent.indexOf(channels.get(right).key())));
        }
        return result;
    }

    private void refreshChannelRows() {
        for (TextView row : channelRows) {
            if (row.getTag() instanceof Integer) {
                int index = (Integer) row.getTag();
                row.setText(formatChannelRow(index));
                if (!row.hasFocus()) {
                    row.setTextColor(index == currentIndex
                            ? 0xffff6973 : statusColor(channels.get(index)));
                }
            }
        }
    }

    private void styleFocusedRow(View view, boolean hasFocus) {
        view.animate().scaleX(hasFocus ? 1.025f : 1f).scaleY(hasFocus ? 1.025f : 1f)
                .setDuration(110).start();
        boolean current = view.getTag() instanceof Integer
                && (Integer) view.getTag() == currentIndex;
        int defaultColor = Color.WHITE;
        if (view.getTag() instanceof String) {
            defaultColor = view.getTag().equals(selectedCategory) ? 0xffff6973 : Color.WHITE;
        }
        if (view.getTag() instanceof Integer) {
            int index = (Integer) view.getTag();
            if (index >= 0 && index < channels.size()) {
                defaultColor = statusColor(channels.get(index));
            }
        }
        ((TextView) view).setTextColor(hasFocus
                ? Color.WHITE
                : current ? 0xffff6973 : defaultColor);
    }

    private int statusColor(Channel channel) {
        ChannelStatus status = statusFor(channel);
        if (status == ChannelStatus.NATIVE) {
            return 0xffbceac7;
        }
        if (status == ChannelStatus.WEB) {
            return 0xffffd27a;
        }
        if (status == ChannelStatus.UNAVAILABLE) {
            return 0xff93a0b2;
        }
        return 0xffe6edf7;
    }

    private ChannelStatus statusFor(Channel channel) {
        ChannelStatus status = channelStatuses.get(channel.number);
        return status == null ? ChannelStatus.CHECKING : status;
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
        Channel requested = channels.get(index);
        if (ChannelUserData.isLocked(this, requested)
                && !unlockedChannels.contains(requested.key())) {
            requestPinVerification("Kilitli kanal · " + requested.name, () -> {
                unlockedChannels.add(requested.key());
                tuneUnlocked(index, startPlayback);
            });
            return;
        }
        tuneUnlocked(index, startPlayback);
    }

    private void tuneUnlocked(int index, boolean startPlayback) {
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
        ChannelUserData.recordRecent(this, channel);
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
        channelStatuses.put(channel.number, ChannelStatus.CHECKING);
        refreshChannelRows();

        String cachedStream = resolvedStreams.get(channel.number);
        Set<String> excluded = failedStreams.get(channel.number);
        if (cachedStream != null && (excluded == null || !excluded.contains(cachedStream))) {
            channelStatuses.put(channel.number, ChannelStatus.NATIVE);
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
                    channelStatuses.put(channel.number, ChannelStatus.NATIVE);
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
        Channel channel = channels.get(currentIndex);
        MediaMetadata metadata = new MediaMetadata.Builder()
                .setTitle(channel.name)
                .setArtist("Türkiye Canlı TV")
                .setAlbumTitle(epgTitle(channel))
                .build();
        MediaItem.Builder item = new MediaItem.Builder()
                .setMediaId(String.valueOf(channel.number))
                .setMediaMetadata(metadata)
                .setUri(url);
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
        channelStatuses.put(channel.number, ChannelStatus.WEB);
        refreshChannelRows();
        Toast.makeText(this, channel.name + " web oynatıcısıyla açılıyor",
                Toast.LENGTH_SHORT).show();
        webFallbackOpen = true;
        startActivityForResult(WebPlayerActivity.createIntent(this, channel), WEB_PLAYER_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == EXPORT_SETTINGS_REQUEST || requestCode == IMPORT_SETTINGS_REQUEST) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                if (requestCode == EXPORT_SETTINGS_REQUEST) {
                    writeSettingsBackup(data.getData());
                } else {
                    readSettingsBackup(data.getData());
                }
            }
            return;
        }
        if (requestCode != WEB_PLAYER_REQUEST) {
            return;
        }
        webFallbackOpen = false;
        if (!channels.isEmpty() && WebPlayerActivity.playbackFailed(data)) {
            channelStatuses.put(channels.get(currentIndex).number, ChannelStatus.UNAVAILABLE);
            refreshChannelRows();
        }
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
                            if (!channels.isEmpty()) {
                                channelStatuses.put(channels.get(currentIndex).number,
                                        ChannelStatus.UNAVAILABLE);
                                refreshChannelRows();
                            }
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
                            channelStatuses.put(channel.number, ChannelStatus.NATIVE);
                            refreshChannelRows();
                        }
                    });
                } else {
                    runOnUiThread(() -> {
                        if (generation == healthSweepGeneration
                                && findChannelIndexExact(channel.number) >= 0) {
                            channelStatuses.put(channel.number, ChannelStatus.WEB);
                            refreshChannelRows();
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
        if (nowPlayingProgram != null) {
            long now = System.currentTimeMillis();
            EpgProgram current = epgGuide == null ? null : epgGuide.current(channel.name, now);
            if (current == null) {
                nowPlayingProgram.setText(channel.category() + "  ·  Program bilgisi bekleniyor");
            } else {
                nowPlayingProgram.setText(current.timeRange() + "  " + current.title
                        + "  ·  %" + current.progressPercent(now));
            }
        }
        updateFavoriteLabels();
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
        long now = System.currentTimeMillis();
        EpgProgram current = epgGuide == null ? null : epgGuide.current(channel.name, now);
        EpgProgram next = epgGuide == null ? null : epgGuide.next(channel.name, now);
        ChannelStatus status = statusFor(channel);
        String currentText = current == null ? "Program bilgisi yok"
                : current.timeRange() + "  " + current.title;
        String nextText = next == null ? "Sonraki program bilgisi yok"
                : next.timeRange() + "  " + next.title;
        channelInfo.setText(String.format(Locale.forLanguageTag("tr-TR"),
                "%03d  %s  ·  %s %s\nŞimdi  %s\nSonra  %s\nP+ / P- Kanal  ·  OK Rehber  ·  INFO Detay",
                channel.number, channel.name, status.symbol, status.label,
                currentText, nextText));
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
    public void onUserLeaveHint() {
        super.onUserLeaveHint();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && getSharedPreferences(PREFS, 0).getBoolean(PIP_ENABLED_KEY, true)
                && player != null && player.isPlaying()
                && getPackageManager().hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            try {
                PictureInPictureParams params = new PictureInPictureParams.Builder()
                        .setAspectRatio(new Rational(16, 9))
                        .build();
                enterPictureInPictureMode(params);
            } catch (RuntimeException ignored) {
                // Some TV firmware advertises PiP but rejects the transition.
            }
        }
    }

    @Override
    public void onPictureInPictureModeChanged(boolean inPictureInPictureMode,
                                              Configuration newConfig) {
        super.onPictureInPictureModeChanged(inPictureInPictureMode, newConfig);
        if (inPictureInPictureMode) {
            topStatusBar.setVisibility(View.GONE);
            channelInfo.setVisibility(View.GONE);
            loadingOverlay.setVisibility(View.GONE);
            channelPanel.setVisibility(View.GONE);
            settingsPanel.setVisibility(View.GONE);
        } else {
            enterImmersiveMode();
            if (player != null && player.getPlaybackState() == Player.STATE_READY) {
                scheduleTopStatusBarHide();
            }
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        resumePlaybackOnStart = player != null && player.isPlaying();
        if (player != null && (Build.VERSION.SDK_INT < Build.VERSION_CODES.O
                || !isInPictureInPictureMode())) {
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
        if (mediaSession != null) {
            mediaSession.release();
            mediaSession = null;
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
