package com.starstwinkle.dsm.ui;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.Drawable;
import android.content.res.ColorStateList;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.starstwinkle.dsm.core.Theme;

/** 视图构建的小工具：尺寸换算、圆角背景、点击反馈、对话框、Toast。 */
public final class Ui {

    public static float fontScale = 1f;

    public static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    public static int sp(Context c, float v) {
        float scaled = v * fontScale;
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, scaled,
                c.getResources().getDisplayMetrics()));
    }

    public static int statusBarHeight(Context c) {
        int id = c.getResources().getIdentifier("status_bar_height", "dimen", "android");
        return id > 0 ? c.getResources().getDimensionPixelSize(id) : dp(c, 24);
    }

    public static GradientDrawable round(int color, float radiusPx) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radiusPx);
        return d;
    }

    public static GradientDrawable round(int color, float radiusPx, int strokeColor, int strokeWidthPx) {
        GradientDrawable d = round(color, radiusPx);
        if (strokeWidthPx > 0) d.setStroke(strokeWidthPx, strokeColor);
        return d;
    }

    public static GradientDrawable oval(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        return d;
    }

    /** 带水波纹的可点击背景 */
    public static Drawable ripple(Context c, int color, float radiusPx, int rippleColor) {
        GradientDrawable content = round(color, radiusPx);
        return new RippleDrawable(ColorStateList.valueOf(rippleColor), content, null);
    }

    public static LinearLayout column(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    public static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    public static TextView tv(Context c, String text, float sizeSp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, sp(c, sizeSp));
        t.setTextColor(color);
        if (bold) t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        t.setIncludeFontPadding(false);
        return t;
    }

    public static View space(Context c, int w, int h) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(w, h));
        return v;
    }

    public static void padding(View v, int l, int t, int r, int b) {
        v.setPadding(l, t, r, b);
    }

    public static void toast(Activity a, String msg) {
        if (a == null || a.isFinishing()) return;
        Toast.makeText(a, msg, Toast.LENGTH_SHORT).show();
    }

    public static String relTime(long ts) {
        long d = System.currentTimeMillis() - ts;
        if (d < 0) d = 0;
        if (d < 60000L) return "刚刚";
        if (d < 3600000L) return (d / 60000L) + " 分钟前";
        if (d < 86400000L) return (d / 3600000L) + " 小时前";
        if (d < 7 * 86400000L) return (d / 86400000L) + " 天前";
        java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("M月d日", java.util.Locale.CHINA);
        return f.format(new java.util.Date(ts));
    }

    /* ---------- 底部选择器 ---------- */
    public interface OnPick {
        void onPick(int index);
    }

    public static Dialog sheet(Activity a, Theme t, String title, String[] items, int checkedIndex, OnPick pick) {
        Dialog dialog = new Dialog(a, android.R.style.Theme_Translucent_NoTitleBar);
        Window w = dialog.getWindow();
        if (w != null) {
            w.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
        }

        View root = new View(a);
        LinearLayout container = new LinearLayout(a);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setGravity(Gravity.BOTTOM);

        LinearLayout panel = column(a);
        panel.setBackground(round(t.card, dp(a, 22)));
        int p = dp(a, 8);
        panel.setPadding(p, p, p, dp(a, 10));

        if (title != null && !title.isEmpty()) {
            TextView tt = tv(a, title, 12f, t.text3, true);
            tt.setPadding(dp(a, 12), dp(a, 8), dp(a, 12), dp(a, 6));
            panel.addView(tt);
        }

        for (int i = 0; i < items.length; i++) {
            final int idx = i;
            TextView item = tv(a, items[i], 15f, i == checkedIndex ? t.brand : t.text, i == checkedIndex);
            item.setPadding(dp(a, 14), dp(a, 14), dp(a, 14), dp(a, 14));
            item.setBackground(ripple(a, 0x00000000, dp(a, 14), t.line));
            item.setOnClickListener(v -> {
                dialog.dismiss();
                if (pick != null) pick.onPick(idx);
            });
            panel.addView(item, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        TextView cancel = tv(a, "取消", 15f, t.text2, false);
        cancel.setGravity(Gravity.CENTER);
        cancel.setPadding(0, dp(a, 14), 0, dp(a, 14));
        cancel.setBackground(ripple(a, t.card2, dp(a, 14), t.line));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.topMargin = dp(a, 8);
        panel.addView(cancel, clp);
        cancel.setOnClickListener(v -> dialog.dismiss());

        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        plp.setMargins(dp(a, 8), 0, dp(a, 8), dp(a, 8));
        container.addView(panel, plp);
        container.setBackgroundColor(t.scrim);
        container.setOnClickListener(v -> dialog.dismiss());

        dialog.setContentView(container);
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();
        return dialog;
    }

    /* ---------- 确认框 ---------- */
    public interface OnConfirm { void onConfirm(); }    public static Dialog confirm(Activity a, Theme t, String title, String message,
                                 String okText, String cancelText, OnConfirm ok) {
        Dialog dialog = new Dialog(a);
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawableResource(android.R.color.transparent);
            w.setLayout(dp(a, 300), ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        LinearLayout panel = column(a);
        panel.setBackground(round(t.card, dp(a, 20)));
        panel.setPadding(dp(a, 20), dp(a, 20), dp(a, 20), dp(a, 8));

        TextView tt = tv(a, title, 16f, t.text, true);
        panel.addView(tt);
        if (message != null && !message.isEmpty()) {
            TextView msg = tv(a, message, 13f, t.text2, false);
            msg.setLineSpacing(0, 1.4f);
            msg.setPadding(0, dp(a, 10), 0, dp(a, 4));
            panel.addView(msg);
        }

        LinearLayout buttons = row(a);
        buttons.setGravity(Gravity.END);
        buttons.setPadding(0, dp(a, 12), 0, 0);

        TextView cancel = tv(a, cancelText == null ? "取消" : cancelText, 14f, t.text2, false);
        cancel.setPadding(dp(a, 14), dp(a, 10), dp(a, 14), dp(a, 10));
        cancel.setOnClickListener(v -> dialog.dismiss());
        buttons.addView(cancel);

        TextView okBtn = tv(a, okText == null ? "确定" : okText, 14f, t.brand, true);
        okBtn.setPadding(dp(a, 14), dp(a, 10), dp(a, 14), dp(a, 10));
        okBtn.setOnClickListener(v -> {
            dialog.dismiss();
            if (ok != null) ok.onConfirm();
        });
        buttons.addView(okBtn);

        panel.addView(buttons);
        dialog.setContentView(panel);
        dialog.show();
        return dialog;
    }

    /* ---------- 输入框 ---------- */
    public interface OnInput { void onInput(String value); }

    public static Dialog inputDialog(Activity a, Theme t, String title, String initial,
                                     String hint, OnInput cb) {
        Dialog dialog = new Dialog(a);
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawableResource(android.R.color.transparent);
            w.setLayout(dp(a, 320), ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        LinearLayout panel = column(a);
        panel.setBackground(round(t.card, dp(a, 20)));
        panel.setPadding(dp(a, 18), dp(a, 18), dp(a, 18), dp(a, 10));
        panel.addView(tv(a, title, 15f, t.text, true));

        final android.widget.EditText input = new android.widget.EditText(a);
        input.setText(initial == null ? "" : initial);
        input.setSingleLine(true);
        input.setTextColor(t.text);
        input.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, sp(a, 13f));
        input.setBackground(round(t.card2, dp(a, 12), t.lineStrong, dp(a, 1)));
        input.setPadding(dp(a, 12), dp(a, 10), dp(a, 12), dp(a, 10));
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ilp.topMargin = dp(a, 12);
        panel.addView(input, ilp);

        if (hint != null) {
            TextView h = tv(a, hint, 11f, t.text3, false);
            h.setLineSpacing(dp(a, 2), 1.5f);
            h.setPadding(0, dp(a, 8), 0, 0);
            panel.addView(h);
        }

        LinearLayout buttons = row(a);
        buttons.setGravity(Gravity.END);
        buttons.setPadding(0, dp(a, 12), 0, 0);
        TextView cancel = tv(a, "取消", 14f, t.text2, false);
        cancel.setPadding(dp(a, 14), dp(a, 10), dp(a, 14), dp(a, 10));
        cancel.setOnClickListener(v -> dialog.dismiss());
        buttons.addView(cancel);
        TextView ok = tv(a, "保存", 14f, t.brand, true);
        ok.setPadding(dp(a, 14), dp(a, 10), dp(a, 14), dp(a, 10));
        ok.setOnClickListener(v -> {
            dialog.dismiss();
            cb.onInput(input.getText().toString());
        });
        buttons.addView(ok);
        panel.addView(buttons);

        dialog.setContentView(panel);
        dialog.show();
        return dialog;
    }

    /* ---------- 长文本查看 ---------- */
    public static Dialog infoDialog(Activity a, Theme t, String title, String body) {
        Dialog dialog = new Dialog(a);
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawableResource(android.R.color.transparent);
            w.setLayout(dp(a, 320), ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        LinearLayout panel = column(a);
        panel.setBackground(round(t.card, dp(a, 20)));
        panel.setPadding(dp(a, 18), dp(a, 18), dp(a, 18), dp(a, 10));

        TextView tt = tv(a, title, 15f, t.text, true);
        panel.addView(tt);

        android.widget.ScrollView sv = new android.widget.ScrollView(a);
        TextView content = new TextView(a);
        content.setText(body);
        content.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, sp(a, 11.5f));
        content.setTextColor(t.text2);
        content.setTypeface(android.graphics.Typeface.MONOSPACE);
        content.setLineSpacing(dp(a, 3), 1.6f);
        content.setPadding(dp(a, 12), dp(a, 12), dp(a, 12), dp(a, 12));
        content.setBackground(round(t.codeBg, dp(a, 12), t.line, dp(a, 1)));
        content.setTextIsSelectable(true);
        sv.addView(content);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(a, 380));
        slp.topMargin = dp(a, 12);
        panel.addView(sv, slp);

        TextView ok = tv(a, "知道了", 14f, t.brand, true);
        ok.setGravity(Gravity.CENTER);
        ok.setPadding(0, dp(a, 12), 0, dp(a, 12));
        ok.setOnClickListener(v -> dialog.dismiss());
        panel.addView(ok);

        dialog.setContentView(panel);
        dialog.show();
        return dialog;
    }
}
