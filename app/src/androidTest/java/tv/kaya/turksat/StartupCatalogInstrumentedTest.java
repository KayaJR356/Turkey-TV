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
    public void webPlayerAllowsKnownVideoProvidersButRejectsUnknownRedirects() {
        assertTrue(WebPlayerActivity.isAllowedTopLevelUrl(
                "https://www.canlitv.diy/player/index.php?id=1"));
        assertTrue(WebPlayerActivity.isAllowedTopLevelUrl(
                "https://www.youtube.com/embed/example"));
        assertTrue(WebPlayerActivity.isAllowedTopLevelUrl(
                "https://player.castr.com/live_example"));
        assertFalse(WebPlayerActivity.isAllowedTopLevelUrl(
                "https://example.com/advertisement"));
        assertFalse(WebPlayerActivity.isAllowedTopLevelUrl(
                "https://youtube.com.example.com/fake-player"));
        assertFalse(WebPlayerActivity.isAllowedTopLevelUrl("http://www.youtube.com/watch?v=x"));
    }
}
