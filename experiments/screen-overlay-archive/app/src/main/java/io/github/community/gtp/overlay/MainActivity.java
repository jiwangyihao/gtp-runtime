package io.github.community.gtp.overlay;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public final class MainActivity extends Activity {
    private static final int REQUEST_PROJECTION = 1001;
    private TextView statusView;
    private Button startButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildContent());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_PROJECTION) {
            return;
        }
        if (resultCode != RESULT_OK || data == null) {
            setStatus("屏幕捕获授权已取消。", 0xFFB91C1C);
            return;
        }
        Intent serviceIntent = ProjectionService.startIntent(this, resultCode, data);
        try {
            startForegroundService(serviceIntent);
            setStatus("正在启动本地 OCR；请切换到游戏。", 0xFF166534);
            launchTargetGame();
        } catch (RuntimeException error) {
            setStatus("启动失败：" + error.getMessage(), 0xFFB91C1C);
        }
    }

    private View buildContent() {
        int padding = dp(24);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(padding, padding, padding, padding);
        root.setBackgroundColor(0xFFF8FAFC);

        TextView title = text("GTP 屏幕翻译", 30, 0xFF0F172A);
        title.setGravity(Gravity.CENTER);
        root.addView(title, matchWrap(0));

        TextView subtitle = text(
                "独立于游戏进程运行：系统授权截图、本地日文 OCR、只读悬浮译文。不会修改游戏或许可证。",
                16,
                0xFF475569);
        subtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subtitleParams = matchWrap(dp(12));
        subtitleParams.bottomMargin = dp(20);
        root.addView(subtitle, subtitleParams);

        statusView = text("", 16, 0xFF475569);
        statusView.setGravity(Gravity.CENTER);
        statusView.setPadding(dp(16), dp(14), dp(16), dp(14));
        statusView.setBackgroundColor(0xFFE2E8F0);
        LinearLayout.LayoutParams statusParams = matchWrap(0);
        statusParams.bottomMargin = dp(16);
        root.addView(statusView, statusParams);

        Button overlayButton = new Button(this);
        overlayButton.setText("1. 授予悬浮窗权限");
        overlayButton.setOnClickListener(view -> openOverlaySettings());
        root.addView(overlayButton, matchWrap(dp(8)));

        startButton = new Button(this);
        startButton.setText("2. 开始翻译并打开游戏");
        startButton.setOnClickListener(view -> requestProjection());
        root.addView(startButton, matchWrap(dp(8)));

        Button previewButton = new Button(this);
        previewButton.setText("预览菜单翻译（无需捕获）");
        previewButton.setOnClickListener(view -> startActivity(new Intent(this, PreviewActivity.class)));
        root.addView(previewButton, matchWrap(dp(8)));

        Button stopButton = new Button(this);
        stopButton.setText("停止翻译");
        stopButton.setOnClickListener(view -> {
            startService(ProjectionService.stopIntent(this));
            setStatus("已请求停止翻译。", 0xFF475569);
        });
        root.addView(stopButton, matchWrap(dp(8)));

        TextView note = text(
                "每次开始都必须在 Android 系统页重新确认捕获。若游戏仍显示 Google Play/PAIRIP 错误，请先完成官方 Play 安装；本工具不会绕过该检查。",
                13,
                0xFF64748B);
        note.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams noteParams = matchWrap(dp(18));
        root.addView(note, noteParams);
        return root;
    }

    private void refreshStatus() {
        boolean overlayGranted = Settings.canDrawOverlays(this);
        startButton.setEnabled(overlayGranted);
        if (ProjectionService.isRunning()) {
            setStatus("翻译服务运行中。", 0xFF166534);
        } else if (overlayGranted) {
            setStatus("悬浮窗权限已授予，可以开始翻译。", 0xFF166534);
        } else {
            setStatus("需要先授予悬浮窗权限。", 0xFFB45309);
        }
    }

    private void openOverlaySettings() {
        Intent intent = new Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()));
        startActivity(intent);
    }

    private void requestProjection() {
        if (!Settings.canDrawOverlays(this)) {
            openOverlaySettings();
            return;
        }
        try {
            startActivityForResult(ProjectionService.captureIntent(this), REQUEST_PROJECTION);
        } catch (RuntimeException error) {
            Toast.makeText(this, "无法打开系统捕获授权页：" + error.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void launchTargetGame() {
        Intent launch = getPackageManager().getLaunchIntentForPackage("jp.gree_ent.mushoku");
        if (launch == null) {
            setStatus("翻译已启动，但未找到原版游戏 jp.gree_ent.mushoku。", 0xFFB45309);
            return;
        }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(launch);
    }

    private void setStatus(String text, int color) {
        statusView.setText(text);
        statusView.setTextColor(color);
    }

    private TextView text(String value, int sizeSp, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        view.setTextColor(color);
        view.setLineSpacing(0, 1.15f);
        return view;
    }

    private LinearLayout.LayoutParams matchWrap(int topMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = topMargin;
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
