package com.starstwinkle.dsm.hook;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** 模块设置页：只负责收集 API Key 与模型偏好，供 hook 读取。 */
public class SettingsActivity extends Activity {

    private static final int BG = 0xFF121317;
    private static final int CARD = 0xFF1E1F25;
    private static final int TEXT = 0xFFEDEDF2;
    private static final int TEXT2 = 0xFF9EA0AC;
    private static final int BRAND = 0xFF4D6BFE;

    private EditText keyInput;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().getDecorView().setSystemUiVisibility(
                android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE);

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(BG);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(18);
        col.setPadding(pad, dp(40), pad, dp(30));
        sv.addView(col, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(sv);

        TextView title = tv("DeepSeek API 增强", 22, TEXT, true);
        col.addView(title);

        TextView sub = tv("让官方 App 的会话走你自己的 API 密钥。界面完全保持官方原样，只是把对话请求换成 api.deepseek.com。", 13, TEXT2, false);
        sub.setLineSpacing(dp(4), 1.6f);
        sub.setPadding(0, dp(10), 0, dp(18));
        col.addView(sub);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(round(CARD, dp(18)));
        card.setPadding(dp(16), dp(16), dp(16), dp(16));

        card.addView(tv("API 密钥", 12, TEXT2, true));

        keyInput = new EditText(this);
        keyInput.setHint("sk-…");
        keyInput.setHintTextColor(TEXT2);
        keyInput.setTextColor(TEXT);
        keyInput.setTextSize(13);
        keyInput.setSingleLine(true);
        keyInput.setTypeface(android.graphics.Typeface.MONOSPACE);
        keyInput.setBackground(round(0xFF26272E, dp(12)));
        keyInput.setPadding(dp(12), dp(10), dp(12), dp(10));
        keyInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        LinearLayout.LayoutParams klp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        klp.topMargin = dp(8);
        card.addView(keyInput, klp);

        TextView save = tv("保存", 14, Color.WHITE, true);
        save.setGravity(Gravity.CENTER);
        save.setBackground(round(BRAND, dp(12)));
        save.setPadding(0, dp(12), 0, dp(12));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = dp(12);
        save.setOnClickListener(v -> {
            getSharedPreferences("dsm_hook", MODE_PRIVATE)
                    .edit().putString("apiKey", keyInput.getText().toString().trim()).apply();
            Toast.makeText(this, "已保存，重启 DeepSeek 生效", Toast.LENGTH_SHORT).show();
        });
        card.addView(save, slp);
        col.addView(card);

        TextView tip = tv("用法\n1. 在 LSPosed 里启用本模块，勾选 DeepSeek\n2. 用 LSPatch 打包时也可以直接嵌入\n3. 密钥只存在本机，请求由本模块直连 api.deepseek.com", 12.5f, TEXT2, false);
        tip.setLineSpacing(dp(4), 1.6f);
        tip.setPadding(0, dp(20), 0, 0);
        col.addView(tip);

        String saved = getSharedPreferences("dsm_hook", MODE_PRIVATE).getString("apiKey", "");
        if (saved != null && !saved.isEmpty()) keyInput.setText(saved);
    }

    private TextView tv(String text, float size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        if (bold) t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        return t;
    }

    private android.graphics.drawable.GradientDrawable round(int color, int radius) {
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        return d;
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
