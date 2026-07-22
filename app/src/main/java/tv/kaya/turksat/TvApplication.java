package tv.kaya.turksat;

import android.app.Application;

public final class TvApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        AppDiagnostics.install(this);
    }
}
