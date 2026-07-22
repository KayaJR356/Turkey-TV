package tv.kaya.turksat;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Process;
import android.util.Log;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Date;

final class AppDiagnostics {
    private static final String TAG = "TurkiyeCanliTV";
    private static final String PREFS = "app_diagnostics";
    private static final String LAST_CRASH = "last_crash";
    private static final String LAST_EVENT = "last_event";
    private static final int MAX_REPORT_CHARS = 6_000;
    private static boolean installed;

    private AppDiagnostics() {
    }

    static synchronized void install(Context context) {
        if (installed) {
            return;
        }
        installed = true;
        Context appContext = context.getApplicationContext();
        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            save(appContext, LAST_CRASH, "uncaught/" + thread.getName(), error);
            save(appContext, LAST_EVENT, "uncaught/" + thread.getName(), error);
            if (previous != null) {
                previous.uncaughtException(thread, error);
            } else {
                Process.killProcess(Process.myPid());
                System.exit(10);
            }
        });
    }

    static void record(Context context, String area, Throwable error) {
        Log.e(TAG, area, error);
        save(context.getApplicationContext(), LAST_EVENT, area, error);
    }

    static void record(Context context, String area, String detail) {
        Log.e(TAG, area + ": " + detail);
        saveText(context.getApplicationContext(), LAST_EVENT,
                new Date() + "\n" + area + "\n" + detail);
    }

    static String consumeLastCrash(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String value = preferences.getString(LAST_CRASH, "");
        if (!value.isEmpty()) {
            preferences.edit().remove(LAST_CRASH).apply();
        }
        return value;
    }

    static String latestEvent(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(LAST_EVENT, "");
    }

    private static void save(Context context, String key, String area, Throwable error) {
        try {
            StringWriter stack = new StringWriter();
            error.printStackTrace(new PrintWriter(stack));
            saveText(context, key, new Date() + "\n" + area + "\n" + stack);
        } catch (Throwable ignored) {
            // Tanılama sistemi uygulamanın asıl hata işleyicisini engellememeli.
        }
    }

    private static void saveText(Context context, String key, String value) {
        try {
            String limited = value.length() > MAX_REPORT_CHARS
                    ? value.substring(0, MAX_REPORT_CHARS) : value;
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putString(key, limited).commit();
        } catch (Throwable ignored) {
            // Depolama dolu veya kullanılamaz olsa da uygulamanın hata yolu devam etmeli.
        }
    }
}
