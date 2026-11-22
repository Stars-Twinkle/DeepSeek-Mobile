package com.starstwinkle.dsm.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.starstwinkle.dsm.core.Theme;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 极简 Markdown 渲染：把回答切成段落 / 代码块 / 列表 / 引用。
 * 流式过程中用纯文本，结束后再走这里做排版。
 */
public final class Markdown {

    private static final Pattern BOLD = Pattern.compile("\\*\\*([^*]+)\\*\\*");
    private static final Pattern CODE = Pattern.compile("`([^`]+)`");
    private static final Pattern LINK = Pattern.compile("\\[([^\\]]+)]\\((https?://[^)]+)\\)");

    public static View render(Context c, Theme t, String src) {
        LinearLayout box = Ui.column(c);
        if (src == null) src = "";
        String[] lines = src.replace("\r\n", "\n").split("\n", -1);

        int i = 0;
        while (i < lines.length) {
            String line = lines[i];

            // 代码块
            if (line.trim().startsWith("```")) {
                String lang = line.trim().substring(3).trim();
                StringBuilder code = new StringBuilder();
                i++;
                while (i < lines.length && !lines[i].trim().startsWith("```")) {
                    code.append(lines[i]).append('\n');
                    i++;
                }
                i++; // 跳过结束围栏
                box.addView(codeBlock(c, t, code.toString(), lang));
                continue;
            }

            if (line.trim().isEmpty()) { i++; continue; }

            // 标题
            if (line.matches("^#{1,4}\\s+.*")) {
                int level = 0;
                while (level < line.length() && line.charAt(level) == '#') level++;
                String text = line.substring(level).trim();
                TextView tv = Ui.tv(c, "", level <= 2 ? 17f : 15.5f, t.text, true);
                tv.setText(inline(c, t, text, false));
                tv.setPadding(0, Ui.dp(c, 6), 0, Ui.dp(c, 3));
                box.addView(tv);
                i++;
                continue;
            }

            // 引用
            if (line.trim().startsWith(">")) {
                StringBuilder quote = new StringBuilder();
                while (i < lines.length && lines[i].trim().startsWith(">")) {
                    quote.append(lines[i].trim().substring(1).trim()).append('\n');
                    i++;
                }
                LinearLayout q = Ui.column(c);
                q.setBackground(Ui.round(0x00000000, 0, t.lineStrong, Ui.dp(c, 2)));
                q.setPadding(Ui.dp(c, 12), Ui.dp(c, 2), 0, Ui.dp(c, 2));
                TextView tv = Ui.tv(c, "", 14.5f, t.text2, false);
                tv.setText(inline(c, t, quote.toString().trim(), false));
                tv.setLineSpacing(0, 1.55f);
                q.addView(tv);
                box.addView(q, margin(c, 0, 6, 0, 10));
                continue;
            }

            // 无序列表
            if (line.matches("^\\s*[-*+•]\\s+.*")) {
                String text = line.replaceFirst("^\\s*[-*+•]\\s+", "");
                box.addView(bullet(c, t, "•", text));
                i++;
                continue;
            }

            // 有序列表
            if (line.matches("^\\s*\\d+[.)]\\s+.*")) {
                Matcher m = Pattern.compile("^\\s*(\\d+)[.)]\\s+(.*)$").matcher(line);
                if (m.matches()) box.addView(bullet(c, t, m.group(1) + ".", m.group(2)));
                i++;
                continue;
            }

            // 普通段落：把连续行拼起来
            StringBuilder para = new StringBuilder();
            while (i < lines.length && !lines[i].trim().isEmpty()
                    && !lines[i].trim().startsWith("```")
                    && !lines[i].matches("^#{1,4}\\s+.*")
                    && !lines[i].trim().startsWith(">")
                    && !lines[i].matches("^\\s*[-*+•]\\s+.*")
                    && !lines[i].matches("^\\s*\\d+[.)]\\s+.*")) {
                if (para.length() > 0) para.append('\n');
                para.append(lines[i]);
                i++;
            }
            TextView tv = Ui.tv(c, "", 15.5f, t.text, false);
            tv.setLineSpacing(Ui.dp(c, 3), 1.62f);
            tv.setText(inline(c, t, para.toString(), false));
            tv.setPadding(0, 0, 0, Ui.dp(c, 9));
            box.addView(tv);
        }
        return box;
    }

    private static View bullet(Context c, Theme t, String mark, String text) {
        LinearLayout row = Ui.row(c);
        row.setGravity(Gravity.TOP);
        TextView dot = Ui.tv(c, mark, 15.5f, t.text2, false);
        dot.setMinWidth(Ui.dp(c, 22));
        row.addView(dot);
        TextView body = Ui.tv(c, "", 15.5f, t.text, false);
        body.setLineSpacing(Ui.dp(c, 2), 1.6f);
        body.setText(inline(c, t, text, false));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        row.addView(body, lp);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = Ui.dp(c, 3);
        row.setLayoutParams(rlp);
        row.setPadding(0, 0, 0, Ui.dp(c, 2));
        return row;
    }

    private static View codeBlock(Context c, Theme t, String code, String lang) {
        LinearLayout wrap = Ui.column(c);
        wrap.setBackground(Ui.round(t.codeBg, Ui.dp(c, 14), t.line, Ui.dp(c, 1)));
        wrap.setPadding(Ui.dp(c, 13), Ui.dp(c, 11), Ui.dp(c, 13), Ui.dp(c, 11));

        if (lang != null && !lang.isEmpty()) {
            TextView label = Ui.tv(c, lang, 11f, t.text3, true);
            label.setPadding(0, 0, 0, Ui.dp(c, 6));
            wrap.addView(label);
        }

        TextView tv = new TextView(c);
        tv.setText(code);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(c, 13));
        tv.setTextColor(t.text);
        tv.setTypeface(Typeface.MONOSPACE);
        tv.setLineSpacing(Ui.dp(c, 2), 1.45f);
        tv.setTextIsSelectable(true);

        HorizontalScrollView hs = new HorizontalScrollView(c);
        hs.setHorizontalScrollBarEnabled(false);
        hs.addView(tv);
        wrap.addView(hs);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(c, 4);
        lp.bottomMargin = Ui.dp(c, 10);
        wrap.setLayoutParams(lp);
        return wrap;
    }

    /** 行内样式：**粗体**、`代码`、[链接](url) */
    public static CharSequence inline(Context c, Theme t, String text, boolean boldAll) {
        SpannableStringBuilder sb = new SpannableStringBuilder(text);

        Matcher m = BOLD.matcher(sb);
        while (m.find()) {
            sb.setSpan(new StyleSpan(Typeface.BOLD), m.start(), m.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        // 去掉 ** 标记
        strip(sb, "**");

        m = CODE.matcher(sb);
        while (m.find()) {
            int s = m.start(), e = m.end();
            if (s + 1 >= e - 1) continue;
            sb.setSpan(new TypefaceSpan("monospace"), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            sb.setSpan(new BackgroundColorSpan(t.codeBg), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            sb.setSpan(new ForegroundColorSpan(t.brand), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        m = LINK.matcher(sb);
        while (m.find()) {
            sb.setSpan(new ForegroundColorSpan(t.brand), m.start(), m.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        if (boldAll) sb.setSpan(new StyleSpan(Typeface.BOLD), 0, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return sb;
    }

    private static void strip(SpannableStringBuilder sb, String token) {
        int idx;
        while ((idx = sb.toString().indexOf(token)) >= 0) {
            sb.delete(idx, idx + token.length());
        }
    }

    private static LinearLayout.LayoutParams margin(Context c, int l, int t, int r, int b) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(Ui.dp(c, l), Ui.dp(c, t), Ui.dp(c, r), Ui.dp(c, b));
        return lp;
    }
}
