package io.github.community.gtp.overlay;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/** Deterministic screen used by device automation to verify seeded menu translations. */
public final class PreviewActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildPreview());
    }

    private LinearLayout buildPreview() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(28), dp(28), dp(28), dp(28));
        root.setBackgroundColor(0xFF111827);

        TextView title = label("菜单词典跑通预览", 26, 0xFF93C5FD);
        root.addView(title);

        List<String> sampleLines = List.of(
                "ホーム", "クエスト", "編成", "強化", "ショップ", "ミッション", "プレゼント", "設定");
        List<DictionaryTranslator.Match> matches =
                DictionaryTranslator.seedMenuDictionary().translateLines(sampleLines);

        for (String source : sampleLines) {
            TextView row = label(source, 22, Color.WHITE);
            row.setContentDescription("日文菜单样本：" + source);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            params.topMargin = dp(10);
            root.addView(row, params);
        }

        TextView boundary = label(
                "本页提供纯日文 OCR 输入；译文应只出现在顶部悬浮层。词典自检命中 "
                        + matches.size() + " 项。",
                14,
                0xFFCBD5E1);
        LinearLayout.LayoutParams boundaryParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        boundaryParams.topMargin = dp(22);
        root.addView(boundary, boundaryParams);
        return root;
    }

    private TextView label(String text, int sizeSp, int color) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(color);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(16), dp(10), dp(16), dp(10));
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xCC1F2937);
        background.setCornerRadius(dp(12));
        view.setBackground(background);
        return view;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
