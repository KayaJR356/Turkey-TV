package tv.kaya.turksat;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.SystemClock;

import androidx.media3.common.util.UnstableApi;

@UnstableApi
public final class BootReceiver extends BroadcastReceiver {
    private static final String PREFS = "tv_settings";
    private static final String AUTO_LAUNCH_KEY = "auto_launch_on_boot";
    private static final String ACTION_DELAYED_BOOT =
            "tv.kaya.turksat.action.DELAYED_BOOT_LAUNCH";
    private static final int DELAYED_BOOT_REQUEST = 3401;
    private static final long DELAYED_BOOT_MS = 12_000L;

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent == null ? null : intent.getAction();
        if (!(Intent.ACTION_BOOT_COMPLETED.equals(action) || ACTION_DELAYED_BOOT.equals(action))
                || !context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(AUTO_LAUNCH_KEY, false)) {
            return;
        }

        if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            scheduleDelayedLaunch(context);
        }
        launch(context);
    }

    static void updateEnabled(Context context, boolean enabled) {
        if (!enabled) {
            AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (alarms != null) {
                alarms.cancel(delayedBootIntent(context));
            }
        }
    }

    private static void scheduleDelayedLaunch(Context context) {
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarms == null) {
            return;
        }
        long triggerAt = SystemClock.elapsedRealtime() + DELAYED_BOOT_MS;
        PendingIntent pendingIntent = delayedBootIntent(context);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAt, pendingIntent);
        } else {
            alarms.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent);
        }
    }

    private static PendingIntent delayedBootIntent(Context context) {
        Intent retry = new Intent(context, BootReceiver.class).setAction(ACTION_DELAYED_BOOT);
        return PendingIntent.getBroadcast(context, DELAYED_BOOT_REQUEST, retry,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static void launch(Context context) {
        Intent launch = new Intent(context, SplashActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        try {
            context.startActivity(launch);
        } catch (RuntimeException ignored) {
            // Some TV firmware blocks background activity launches after boot.
        }
    }
}
