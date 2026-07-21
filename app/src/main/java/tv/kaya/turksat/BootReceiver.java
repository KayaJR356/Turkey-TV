package tv.kaya.turksat;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.media3.common.util.UnstableApi;

@UnstableApi
public final class BootReceiver extends BroadcastReceiver {
    private static final String PREFS = "tv_settings";
    private static final String AUTO_LAUNCH_KEY = "auto_launch_on_boot";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())
                || !context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(AUTO_LAUNCH_KEY, false)) {
            return;
        }

        Intent launch = new Intent(context, SplashActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        try {
            context.startActivity(launch);
        } catch (RuntimeException ignored) {
            // Some TV firmware blocks background activity launches after boot.
        }
    }
}
