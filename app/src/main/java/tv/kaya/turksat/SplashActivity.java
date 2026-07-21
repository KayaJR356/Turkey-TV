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

public final class SplashActivity extends Activity {
    private static final long SPLASH_DURATION_MS = 1100L;
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
        content.setBackgroundResource(R.drawable.splash_background);
        content.setPadding(dp(40), dp(40), dp(40), dp(40));

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.app_icon);
        logo.setAlpha(0f);
        logo.setScaleX(0.82f);
        logo.setScaleY(0.82f);
        content.addView(logo, new LinearLayout.LayoutParams(dp(132), dp(132)));

        TextView title = label(getString(R.string.app_name), 44);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setAlpha(0f);
        title.setTranslationY(dp(14));
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        titleParams.setMargins(0, dp(22), 0, dp(8));
        content.addView(title, titleParams);

        TextView subtitle = label("Canlı yayın. Sade deneyim.", 20);
        subtitle.setTextColor(0xffc8d2df);
        subtitle.setAlpha(0f);
        content.addView(subtitle);

        TextView platform = label("ANDROID TV  •  GOOGLE TV", 13);
        platform.setTextColor(0xffff6973);
        platform.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        platform.setLetterSpacing(0.08f);
        platform.setAlpha(0f);
        LinearLayout.LayoutParams platformParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        platformParams.setMargins(0, dp(18), 0, 0);
        content.addView(platform, platformParams);

        setContentView(content);
        logo.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(420).start();
        title.animate().alpha(1f).translationY(0f).setStartDelay(120).setDuration(360).start();
        subtitle.animate().alpha(1f).setStartDelay(260).setDuration(320).start();
        platform.animate().alpha(1f).setStartDelay(360).setDuration(320).start();
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
