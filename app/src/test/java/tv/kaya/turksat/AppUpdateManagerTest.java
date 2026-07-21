package tv.kaya.turksat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class AppUpdateManagerTest {
    @Test
    public void comparesCommonReleaseVersions() {
        assertTrue(AppUpdateManager.compareVersions("v3.4.0", "3.3.0") > 0);
        assertTrue(AppUpdateManager.compareVersions("3.3.1", "v3.3.0") > 0);
        assertEquals(0, AppUpdateManager.compareVersions("v3.3.0", "3.3.0"));
    }

    @Test
    public void stableReleaseSortsAfterPrerelease() {
        assertTrue(AppUpdateManager.compareVersions("3.4.0", "3.4.0-beta.1") > 0);
        assertTrue(AppUpdateManager.compareVersions("3.4.0-beta.2", "3.4.0-beta.1") > 0);
    }
}
