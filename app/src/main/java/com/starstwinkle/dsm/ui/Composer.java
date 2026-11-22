package com.starstwinkle.dsm.ui;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.starstwinkle.dsm.R;
import com.starstwinkle.dsm.core.Prefs;
import com.starstwinkle.dsm.core.Theme;

/** 底部输入组件：首页与对话页共用，样式对齐官方 App 的圆角输入卡片。 */
public class Composer extends LinearLayout {

    public interface Listener {
        void onSend(String text);
        void onStop();
        void onToggleThinking();
    }

    private final Activity activity;
    private final Theme t;
    private final EditText input;
    private final ImageView sendBtn;
    private final TextView modeLabel;
    private final LinearLayout modeChip;
    private Listener listener;
    private boolean sending;

    public Composer(Activity a, Theme theme) {
        super(a);
        this.activity = a;
        this.t = theme;
        setOrientation(VERTICAL);
        setBackground(Ui.round(t.bgSoft == 0 ? t.card : t.card, Ui.dp(a, 24), t.line, Ui.dp(a, 1)));
        int p = Ui.dp(a, 12);
        setPadding(p, Ui.dp(a, 10), p, p);

        input = new EditText(a);
        input.setBackground(null);
        input.setHint("给 DeepSeek 发送消息");
        input.setHintTextColor(t.text3);
        input.setTextColor(t.text);
        input.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, Ui.sp(a, 15.5f));
        input.setMaxLines(6);
        input.setMinLines(1);
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setPadding(Ui.dp(a, 4), Ui.dp(a, 4), Ui.dp(a, 4), Ui.dp(a, 6));
        input.setImeOptions(EditorInfo.IME_ACTION_NONE);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setLineSpacing(Ui.dp(a, 2), 1.45f);
        addView(input, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout bar = Ui.row(a);
        bar.setGravity(Gravity.CENTER_VERTICAL);

        modeChip = Ui.row(a);
        modeChip.setGravity(Gravity.CENTER_VERTICAL);
        modeChip.setPadding(Ui.dp(a, 10), Ui.dp(a, 6), Ui.dp(a, 12), Ui.dp(a, 6));
        modeChip.setBackground(Ui.round(t.card2, Ui.dp(a, 999), t.line, Ui.dp(a, 1)));

        ImageView thinkIcon = new ImageView(a);
        thinkIcon.setImageResource(R.drawable.ic_think);
        thinkIcon.setColorFilter(t.text2);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(Ui.dp(a, 14), Ui.dp(a, 14));
        ilp.rightMargin = Ui.dp(a, 5);
        modeChip.addView(thinkIcon, ilp);

        modeLabel = Ui.tv(a, "对话", 12.5f, t.text2, true);
        modeChip.addView(modeLabel);

        modeChip.setOnClickListener(v -> {
            if (listener != null) listener.onToggleThinking();
        });
        bar.addView(modeChip);

        View spacer = new View(a);
        bar.addView(spacer, new LayoutParams(0, 1, 1f));

        sendBtn = new ImageView(a);
        sendBtn.setScaleType(ImageView.ScaleType.CENTER);
        sendBtn.setImageResource(R.drawable.ic_send);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(Ui.dp(a, 36), Ui.dp(a, 36));
        sendBtn.setBackground(Ui.oval(t.brand));
        sendBtn.setColorFilter(Color.WHITE);
        sendBtn.setPadding(Ui.dp(a, 8), Ui.dp(a, 8), Ui.dp(a, 8), Ui.dp(a, 8));
        sendBtn.setOnClickListener(v -> {
            if (sending) {
                if (listener != null) listener.onStop();
            } else {
                String text = input.getText().toString().trim();
                if (text.isEmpty()) {
                    Ui.toast(activity, "先输入内容再发送");
                    return;
                }
                if (listener != null) listener.onSend(text);
            }
        });
        bar.addView(sendBtn, slp);

        LayoutParams blp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.topMargin = Ui.dp(a, 2);
        addView(bar, blp);

        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a2) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { syncSendButton(); }
        });
        syncSendButton();
        syncMode();
    }

    public void setListener(Listener l) {
        this.listener = l;
    }

    public void setDemo(String text) {
        input.setText(text);
        input.setSelection(input.getText().length());
    }

    public String getText() {
        return input.getText().toString().trim();
    }

    public void clear() {
        input.setText("");
    }

    public void focus() {
        input.requestFocus();
    }

    public void setSending(boolean s) {
        this.sending = s;
        syncSendButton();
    }

    private void syncSendButton() {
        if (sending) {
            sendBtn.setImageResource(R.drawable.ic_stop);
            sendBtn.setColorFilter(Color.WHITE);
            sendBtn.setBackground(Ui.oval(t.card2));
            sendBtn.setPadding(Ui.dp(activity, 10), Ui.dp(activity, 10), Ui.dp(activity, 10), Ui.dp(activity, 10));
            return;
        }
        boolean has = getText().length() > 0;
        sendBtn.setImageResource(R.drawable.ic_send);
        sendBtn.setPadding(Ui.dp(activity, 8), Ui.dp(activity, 8), Ui.dp(activity, 8), Ui.dp(activity, 8));
        if (has) {
            sendBtn.setBackground(Ui.oval(t.brand));
            sendBtn.setColorFilter(Color.WHITE);
        } else {
            sendBtn.setBackground(Ui.oval(t.card2));
            sendBtn.setColorFilter(t.text3);
        }
    }

    /** 思考模式开关的视觉状态 */
    public void syncMode() {
        boolean thinking = Prefs.get(activity).thinking();
        modeLabel.setText(thinking ? "思考" : "对话");
        modeLabel.setTextColor(thinking ? t.brand : t.text2);
        modeChip.setBackground(Ui.round(
                thinking ? t.brandWeak : t.card2, Ui.dp(activity, 999),
                thinking ? 0x664D6BFE : t.line, Ui.dp(activity, 1)));
        View icon = modeChip.getChildAt(0);
        if (icon instanceof ImageView) {
            ((ImageView) icon).setColorFilter(thinking ? t.brand : t.text2);
        }
    }
}
