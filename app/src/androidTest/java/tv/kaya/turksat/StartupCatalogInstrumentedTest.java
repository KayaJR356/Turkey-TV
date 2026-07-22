package tv.kaya.turksat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.os.SystemClock;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

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
        AtomicInteger channelCount = new AtomicInteger();
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            for (int attempt = 0; attempt < 24; attempt++) {
                scenario.onActivity(activity ->
                        channelCount.set(activity.loadedChannelCountForTest()));
                if (channelCount.get() >= 250) {
                    break;
                }
                SystemClock.sleep(250L);
            }
        }

        assertTrue("MainActivity should load the bundled channel catalog",
                channelCount.get() >= 250);
    }
}
