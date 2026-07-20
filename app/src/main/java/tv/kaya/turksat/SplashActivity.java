package tv.kaya.turksat;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.media3.common.util.UnstableApi;

@UnstableApi
public final class SplashActivity extends Activity {
    private static final long SPLASH_DURATION_MS = 900L;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable openPlayer = () -> {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        startActivity(new Intent(this, MainActivity.class));
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER);
        content.setBackgroundColor(Color.BLACK);

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.app_icon);
        logo.setAlpha(0f);
        logo.setScaleX(0.82f);
        logo.setScaleY(0.82f);
        content.addView(logo, new LinearLayout.LayoutParams(dp(150), dp(150)));

        TextView title = label(getString(R.string.app_name), 40);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setAlpha(0f);
        title.setTranslationY(dp(14));
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        titleParams.setMargins(0, dp(22), 0, dp(8));
        content.addView(title, titleParams);

        TextView subtitle = label("Canlı yayınlar hazırlanıyor", 19);
        subtitle.setTextColor(0xffaab7c9);
        subtitle.setAlpha(0f);
        content.addView(subtitle);

        setContentView(content);
        logo.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(420).start();
        title.animate().alpha(1f).translationY(0f).setStartDelay(120).setDuration(360).start();
        subtitle.animate().alpha(1f).setStartDelay(260).setDuration(320).start();
        handler.postDelayed(openPlayer, SPLASH_DURATION_MS);
    }

    private TextView label(String value, int sizeSp) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextColor(Color.WHITE);
        view.setTextSize(sizeSp);
        view.setGravity(Gravity.CENTER);
        return view;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(openPlayer);
        super.onDestroy();
    }
}
