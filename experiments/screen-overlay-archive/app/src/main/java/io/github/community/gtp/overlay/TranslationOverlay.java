package io.github.community.gtp.overlay;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Owns the click-through translation panel shown above the captured app. */
final class TranslationOverlay implements AutoCloseable {
    private final WindowManager windowManager;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final LinearLayout panel;
    private final TextView translationView;
    private boolean attached;

    TranslationOverlay(Context context) {
        if (!Settings.canDrawOverlays(context)) {
            throw new IllegalStateException("尚未授予悬浮窗权限");
        }
        windowManager = context.getSystemService(WindowManager.class);
        panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(context, 16), dp(context, 10), dp(context, 16), dp(context, 10));

        GradientDrawable background = new GradientDrawable();
        background.setColor(0xE61F2937);
        background.setCornerRadius(dp(context, 14));
        background.setStroke(dp(context, 1), 0xAA60A5FA);
        panel.setBackground(background);
        panel.setVisibility(View.GONE);

        TextView title = new TextView(context);
        title.setText("GTP · 本地词典");
        title.setTextColor(0xFF93C5FD);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        panel.addView(title);

        translationView = new TextView(context);
        translationView.setTextColor(Color.WHITE);
        translationView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        translationView.setGravity(Gravity.CENTER);
        translationView.setLineSpacing(0, 1.12f);
        translationView.setPadding(0, dp(context, 4), 0, 0);
        translationView.setMaxWidth((int) (context.getResources().getDisplayMetrics().widthPixels * 0.88f));
        panel.addView(translationView);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_SECURE,
                android.graphics.PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        params.y = dp(context, 72);
        params.setTitle("GTP Translation Overlay");
        windowManager.addView(panel, params);
        attached = true;
    }

    void show(String translation) {
        mainHandler.post(() -> {
            if (!attached) {
                return;
            }
            translationView.setText(translation);
            panel.setContentDescription("GTP 翻译：" + translation.replace('\n', '，'));
            panel.setVisibility(View.VISIBLE);
        });
    }

    void hide() {
        mainHandler.post(() -> {
            if (attached) {
                panel.setVisibility(View.GONE);
                panel.setContentDescription("GTP 翻译：暂无词典命中");
            }
        });
    }

    @Override
    public void close() {
        mainHandler.post(() -> {
            if (!attached) {
                return;
            }
            attached = false;
            windowManager.removeView(panel);
        });
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
