package tv.kaya.turksat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
@LargeTest
public final class StartupCatalogInstrumentedTest {
    @Test
    public void bundledCatalogAlwaysProvidesCompleteChannelList() {
        Context context = ApplicationProvider.getApplicationContext();

        List<Channel> channels = ChannelRepository.loadBundledCatalog(context);

        assertEquals(285, channels.size());
        assertEquals("TRT 1", channels.get(0).name);
        assertEquals("Türkmen Sport Tv", channels.get(channels.size() - 1).name);
    }

    @Test
    public void mainActivityStartsAndLoadsChannelsOnMinimumAndroidVersion() {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Context context = ApplicationProvider.getApplicationContext();
        Intent launchIntent = context.getPackageManager()
                .getLaunchIntentForPackage(context.getPackageName());
        assertNotNull("App launcher intent should exist", launchIntent);
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        AtomicReference<MainActivity> activityReference = new AtomicReference<>();
        AtomicInteger channelCount = new AtomicInteger();
        context.startActivity(launchIntent);
        try {
            for (int attempt = 0; attempt < 120; attempt++) {
                instrumentation.runOnMainSync(() -> {
                    for (Activity activity : ActivityLifecycleMonitorRegistry.getInstance()
                            .getActivitiesInStage(Stage.RESUMED)) {
                        if (activity instanceof MainActivity) {
                            MainActivity mainActivity = (MainActivity) activity;
                            activityReference.set(mainActivity);
                            channelCount.set(mainActivity.loadedChannelCountForTest());
                        }
                    }
                });
                if (activityReference.get() != null && channelCount.get() >= 250) {
                    break;
                }
                SystemClock.sleep(250L);
            }
        } finally {
            if (activityReference.get() != null) {
                instrumentation.runOnMainSync(() -> activityReference.get().finish());
            }
        }

        assertNotNull("Splash should open MainActivity", activityReference.get());
        assertTrue("MainActivity should load the bundled channel catalog",
                channelCount.get() >= 250);
    }

    @Test
    public void rightKeyNavigatesWithoutClosingChannelPanel() {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Context context = ApplicationProvider.getApplicationContext();
        Intent launchIntent = context.getPackageManager()
                .getLaunchIntentForPackage(context.getPackageName());
        assertNotNull(launchIntent);
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        AtomicReference<MainActivity> activityReference = new AtomicReference<>();
        context.startActivity(launchIntent);
        try {
            for (int attempt = 0; attempt < 80 && activityReference.get() == null; attempt++) {
                instrumentation.runOnMainSync(() -> {
                    for (Activity activity : ActivityLifecycleMonitorRegistry.getInstance()
                            .getActivitiesInStage(Stage.RESUMED)) {
                        if (activity instanceof MainActivity) {
                            activityReference.set((MainActivity) activity);
                        }
                    }
                });
                SystemClock.sleep(250L);
            }
            assertNotNull(activityReference.get());

            instrumentation.runOnMainSync(
                    () -> activityReference.get().openChannelPanelForTest());
            SystemClock.sleep(300L);
            instrumentation.runOnMainSync(() -> assertTrue(
                    "Menu key should open the channel panel",
                    activityReference.get().channelPanelVisibleForTest()));

            instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_DPAD_RIGHT);
            SystemClock.sleep(300L);
            instrumentation.runOnMainSync(() -> assertTrue(
                    "Right key should navigate instead of closing the channel panel",
                    activityReference.get().channelPanelVisibleForTest()));
        } finally {
            if (activityReference.get() != null) {
                instrumentation.runOnMainSync(() -> activityReference.get().finish());
            }
        }
    }

    @Test
    public void liveWebChannelStartsPlayerActivityOnMinimumAndroidVersion() {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Context context = ApplicationProvider.getApplicationContext();
        Channel testChannel = new Channel(4, "Show TV",
                "https://www.canlitv.diy/player/index.php?id=4&mobile=1",
                "https://www.canlitv.diy/tr/show-tv");
        Intent intent = WebPlayerActivity.createIntent(context, testChannel)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        AtomicReference<WebPlayerActivity> activityReference = new AtomicReference<>();
        AtomicInteger recoveryCount = new AtomicInteger();
        AtomicInteger nativeStartCount = new AtomicInteger();
        AtomicReference<String> playerUrl = new AtomicReference<>();
        context.startActivity(intent);
        try {
            for (int attempt = 0; attempt < 160; attempt++) {
                instrumentation.runOnMainSync(() -> {
                    for (Activity activity : ActivityLifecycleMonitorRegistry.getInstance()
                            .getActivitiesInStage(Stage.RESUMED)) {
                        if (activity instanceof WebPlayerActivity) {
                            WebPlayerActivity playerActivity = (WebPlayerActivity) activity;
                            activityReference.set(playerActivity);
                            recoveryCount.set(playerActivity.playbackRecoveryCountForTest());
                            nativeStartCount.set(playerActivity.nativePlaybackStartedForTest()
                                    ? 1 : 0);
                            playerUrl.set(playerActivity.currentPlayerUrlForTest());
                        }
                    }
                });
                if (activityReference.get() != null
                        && (nativeStartCount.get() > 0 || recoveryCount.get() > 0)) {
                    break;
                }
                SystemClock.sleep(250L);
            }
        } finally {
            if (activityReference.get() != null) {
                instrumentation.runOnMainSync(() -> activityReference.get().finish());
            }
        }

        assertNotNull("Web channel should keep the player activity alive", activityReference.get());
        assertTrue("Player should use native media or a trusted web video provider",
                nativeStartCount.get() > 0
                        || WebPlayerActivity.isAllowedTopLevelUrl(playerUrl.get()));
        assertTrue("Player should start native media or run web playback recovery",
                nativeStartCount.get() > 0 || recoveryCount.get() > 0);
    }

    @Test
    public void webPlayerAllowsKnownVideoProvidersButRejectsUnknownRedirects() {
        assertTrue("canlitv player should be allowed", WebPlayerActivity.isAllowedTopLevelUrl(
                "https://www.canlitv.diy/player/index.php?id=1"));
        assertTrue("YouTube player should be allowed", WebPlayerActivity.isAllowedTopLevelUrl(
                "https://www.youtube.com/embed/example"));
        assertTrue("Castr player should be allowed", WebPlayerActivity.isAllowedTopLevelUrl(
                "https://player.castr.com/live_example"));
        assertFalse("unknown advertiser should be blocked", WebPlayerActivity.isAllowedTopLevelUrl(
                "https://example.com/advertisement"));
        assertFalse("lookalike domain should be blocked", WebPlayerActivity.isAllowedTopLevelUrl(
                "https://youtube.com.example.com/fake-player"));
        assertFalse("cleartext redirect should be blocked",
                WebPlayerActivity.isAllowedTopLevelUrl("http://www.youtube.com/watch?v=x"));
    }
}
