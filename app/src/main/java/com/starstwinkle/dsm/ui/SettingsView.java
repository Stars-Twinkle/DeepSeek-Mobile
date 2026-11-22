package com.starstwinkle.dsm.ui;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.starstwinkle.dsm.MainActivity;
import com.starstwinkle.dsm.R;
import com.starstwinkle.dsm.core.Prefs;
import com.starstwinkle.dsm.core.Theme;

/** 设置页：密钥状态、模型、思考模式、外观、通用。 */
public class SettingsView extends FrameLayout implements MainActivity.Screen {

    private final MainActivity act;
    private final Theme t;

    private LinearLayout apiCard;
    private TextView apiState;
    private TextView apiBalance;
    private TextView apiHint;
    private TextView apiAction;
    private TextView modelValue;
    private TextView modelSub;
    private View thinkSwitch;
    private TextView themeValue;
    private TextView fontValue;
    private TextView effortValue;

    public SettingsView(MainActivity a) {
        super(a);
        this.act = a;
        this.t = a.theme();
        setBackgroundColor(t.bg);

        LinearLayout root = Ui.column(a);
        addView(root, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        // 顶栏
        LinearLayout top = Ui.row(a);
        top.setPadding(Ui.dp(a, 10), Ui.dp(a, 8), Ui.dp(a, 14), Ui.dp(a, 6));
        ImageView back = new ImageView(a);
        back.setImageResource(R.drawable.ic_back);
        back.setColorFilter(t.text2);
        back.setPadding(Ui.dp(a, 9), Ui.dp(a, 9), Ui.dp(a, 9), Ui.dp(a, 9));
        back.setOnClickListener(v -> act.back());
        top.addView(back, new LinearLayout.LayoutParams(Ui.dp(a, 40), Ui.dp(a, 40)));
        TextView title = Ui.tv(a, "设置", 16f, t.text, true);
        title.setPadding(Ui.dp(a, 4), 0, 0, 0);
        top.addView(title);
        root.addView(top);

        ScrollView sv = new ScrollView(a);
        sv.setVerticalScrollBarEnabled(false);
        root.addView(sv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        LinearLayout col = Ui.column(a);
        col.setPadding(Ui.dp(a, 16), Ui.dp(a, 4), Ui.dp(a, 16), Ui.dp(a, 30));
        sv.addView(col, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // ---- API 状态卡 ----
        apiCard = Ui.column(a);
        apiCard.setPadding(Ui.dp(a, 16), Ui.dp(a, 15), Ui.dp(a, 16), Ui.dp(a, 15));
        apiCard.setBackground(Ui.round(t.card, Ui.dp(a, 18), t.line, Ui.dp(a, 1)));

        LinearLayout apiTop = Ui.row(a);
        apiState = Ui.tv(a, "未配置", 13f, t.text3, true);
        apiTop.addView(apiState);
        apiTop.addView(new View(a), new LinearLayout.LayoutParams(0, 1, 1f));
        apiAction = Ui.tv(a, "去配置", 12.5f, t.brand, true);
        apiAction.setOnClickListener(v -> act.openOnboarding());
        apiTop.addView(apiAction);
        apiCard.addView(apiTop);

        apiBalance = Ui.tv(a, "余额：--", 22f, t.text, true);
        apiBalance.setPadding(0, Ui.dp(a, 10), 0, 0);
        apiCard.addView(apiBalance);

        apiHint = Ui.tv(a, "配置 API 密钥后，对话不再受移动端每日次数限制。", 11.5f, t.text3, false);
        apiHint.setLineSpacing(Ui.dp(a, 2), 1.55f);
        apiHint.setPadding(0, Ui.dp(a, 6), 0, 0);
        apiCard.addView(apiHint);

        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.topMargin = Ui.dp(a, 14);
        col.addView(apiCard, clp);

        // ---- 模型 ----
        LinearLayout g1 = group(col, "模型");
        modelValue = Ui.tv(a, "Flash", 13.5f, t.text3, false);
        g1.addView(rowWith("默认模型", "响应更快，日常够用", modelValue, true, v -> pickModel()));
        thinkSwitch = makeSwitch(act.prefs().thinking(), v -> {
            act.prefs().setThinking(((View) v).isSelected());
            refresh();
        });
        g1.addView(rowWith("思考模式", "开启后先推理再回答，适合难题", thinkSwitch, false, null));

        // ---- 外观 ----
        LinearLayout g2 = group(col, "外观");
        themeValue = Ui.tv(a, labelTheme(act.prefs().theme()), 13.5f, t.text3, false);
        g2.addView(rowWith("主题", null, themeValue, true, v -> pickTheme()));
        fontValue = Ui.tv(a, labelFont(act.prefs().font()), 13.5f, t.text3, false);
        g2.addView(rowWith("字体大小", null, fontValue, true, v -> pickFont()));

        // ---- 通用 ----
        LinearLayout g3 = group(col, "通用");
        effortValue = Ui.tv(a, labelEffort(act.prefs().effort()), 13.5f, t.text3, false);
        g3.addView(rowWith("思考强度", "仅在思考模式下生效", effortValue, true, v -> pickEffort()));
        g3.addView(rowWith("用官方 App 继续", "没有密钥时交给官方 App 原生对话", null, true,
                v -> act.openOfficialApp()));
        g3.addView(rowWith("清空所有对话", null, null, true, v ->
                Ui.confirm(act, t, "清空所有对话？", "所有本地记录都会被删除，无法恢复。",
                        "清空", "取消", () -> {
                            act.store().clear();
                            act.toast("已清空");
                        })));

        // ---- 关于 ----
        LinearLayout g4 = group(col, "关于");
        TextView ver = Ui.tv(a, "1.0.0", 13.5f, t.text3, false);
        g4.addView(rowWith("版本", null, ver, false, null));
        g4.addView(rowWith("查看对话提示词", "决定回答风格的隐藏设定", null, true, v ->
                Ui.infoDialog(act, t, "对话提示词（每次请求都会附带）",
                        com.starstwinkle.dsm.core.Api.systemPrompt(false))));

        TextView foot = Ui.tv(a, "本应用与 DeepSeek 官方无关，密钥仅保存在本机。", 11f, t.text3, false);
        foot.setGravity(Gravity.CENTER);
        foot.setPadding(0, Ui.dp(a, 22), 0, 0);
        col.addView(foot);

        refresh();
    }

    /* ---------- 行构建 ---------- */
    private LinearLayout group(LinearLayout parent, String title) {
        TextView tv = Ui.tv(act, title, 12f, t.text3, true);
        tv.setPadding(Ui.dp(act, 6), Ui.dp(act, 20), 0, Ui.dp(act, 8));
        parent.addView(tv);

        LinearLayout g = Ui.column(act);
        g.setBackground(Ui.round(t.card, Ui.dp(act, 16), t.line, Ui.dp(act, 1)));
        parent.addView(g);
        return g;
    }

    private View rowWith(String label, String sub, View right, boolean chevron,
                         View.OnClickListener click) {
        LinearLayout r = Ui.row(act);
        r.setPadding(Ui.dp(act, 15), Ui.dp(act, 13), Ui.dp(act, 13), Ui.dp(act, 13));
        LinearLayout main = Ui.column(act);
        main.addView(Ui.tv(act, label, 14.5f, t.text, false));
        if (sub != null) {
            TextView s = Ui.tv(act, sub, 11.5f, t.text3, false);
            s.setPadding(0, Ui.dp(act, 3), 0, 0);
            s.setLineSpacing(Ui.dp(act, 1), 1.45f);
            main.addView(s);
        }
        r.addView(main, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (right != null) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.leftMargin = Ui.dp(act, 8);
            r.addView(right, lp);
        }
        if (chevron) {
            ImageView c = new ImageView(act);
            c.setImageResource(R.drawable.ic_chevron);
            c.setColorFilter(t.text3);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(act, 16), Ui.dp(act, 16));
            lp.leftMargin = Ui.dp(act, 4);
            r.addView(c, lp);
        }
        if (click != null) {
            r.setBackground(Ui.ripple(act, Color.TRANSPARENT, 0, t.line));
            r.setOnClickListener(click);
        }
        return r;
    }

    private View makeSwitch(boolean on, View.OnClickListener click) {
        FrameLayout sw = new FrameLayout(act);
        sw.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(act, 46), Ui.dp(act, 27)));
        sw.setBackground(Ui.round(on ? t.brand : t.card2, Ui.dp(act, 999),
                on ? 0 : t.lineStrong, Ui.dp(act, 1)));
        View knob = new View(act);
        knob.setBackground(Ui.oval(Color.WHITE));
        FrameLayout.LayoutParams klp = new FrameLayout.LayoutParams(Ui.dp(act, 23), Ui.dp(act, 23));
        klp.gravity = Gravity.START | Gravity.CENTER_VERTICAL;
        klp.leftMargin = Ui.dp(act, on ? 21 : 2);
        sw.addView(knob, klp);
        sw.setSelected(on);
        sw.setOnClickListener(v -> {
            v.setSelected(!v.isSelected());
            click.onClick(v);
        });
        return sw;
    }

    /* ---------- 选择器 ---------- */
    private void pickModel() {
        boolean pro = "pro".equals(act.prefs().model());
        Ui.sheet(act, t, "默认模型", new String[]{
                "Flash · 响应更快，日常够用",
                "Pro · 更强，适合复杂问题（更贵）"
        }, pro ? 1 : 0, idx -> {
            act.prefs().setModel(idx == 1 ? "pro" : "flash");
            modelValue.setText(idx == 1 ? "Pro" : "Flash");
            refresh();
        });
    }

    private void pickTheme() {
        String cur = act.prefs().theme();
        int chk = "light".equals(cur) ? 1 : ("system".equals(cur) ? 2 : 0);
        Ui.sheet(act, t, "主题", new String[]{"深色", "浅色", "跟随系统"}, chk, idx -> {
            act.prefs().setTheme(idx == 0 ? "dark" : (idx == 1 ? "light" : "system"));
            act.rebuildAll();
        });
    }

    private void pickFont() {
        String cur = act.prefs().font();
        int chk = "large".equals(cur) ? 1 : ("xlarge".equals(cur) ? 2 : 0);
        Ui.sheet(act, t, "字体大小", new String[]{"标准", "大", "特大"}, chk, idx -> {
            act.prefs().setFont(idx == 0 ? "normal" : (idx == 1 ? "large" : "xlarge"));
            act.rebuildAll();
        });
    }

    private void pickEffort() {
        String cur = act.prefs().effort();
        int chk = "low".equals(cur) ? 0 : ("max".equals(cur) ? 2 : 1);
        Ui.sheet(act, t, "思考强度", new String[]{"快速（low）", "标准（high）", "最强（max）"},
                chk, idx -> {
                    act.prefs().setEffort(idx == 0 ? "low" : (idx == 1 ? "high" : "max"));
                    effortValue.setText(labelEffort(act.prefs().effort()));
                });
    }

    private static String labelTheme(String v) {
        if ("light".equals(v)) return "浅色";
        if ("system".equals(v)) return "跟随系统";
        return "深色";
    }

    private static String labelFont(String v) {
        if ("large".equals(v)) return "大";
        if ("xlarge".equals(v)) return "特大";
        return "标准";
    }

    private static String labelEffort(String v) {
        if ("low".equals(v)) return "快速";
        if ("max".equals(v)) return "最强";
        return "标准";
    }

    /* ---------- 状态刷新 ---------- */
    @Override
    public void refresh() {
        Prefs p = act.prefs();
        boolean has = p.hasKey();
        if (has) {
            apiState.setText("已配置 · " + Prefs.mask(p.apiKey()));
            apiState.setTextColor(t.ok);
            apiAction.setText("重新填写");
            apiBalance.setText("余额：" + (p.balanceText().isEmpty() ? "--" : p.balanceText()));
            apiHint.setText("对话已升级为 API 调用，不再受 App 每日次数限制。");
            long age = System.currentTimeMillis() - p.balanceAt();
            if (p.balanceAt() > 0 && age > 5 * 60 * 1000L) {
                refreshBalance();
            }
        } else {
            apiState.setText("未配置");
            apiState.setTextColor(t.text3);
            apiAction.setText("去配置");
            apiBalance.setText("余额：--");
            apiHint.setText("配置 API 密钥后，对话不再受移动端每日次数限制。");
        }
        if (thinkSwitch != null) {
            boolean on = p.thinking();
            thinkSwitch.setSelected(on);
            thinkSwitch.setBackground(Ui.round(on ? t.brand : t.card2, Ui.dp(act, 999),
                    t.line, Ui.dp(act, 1)));
            View knob = ((ViewGroup) thinkSwitch).getChildAt(0);
            if (knob != null) {
                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) knob.getLayoutParams();
                lp.leftMargin = Ui.dp(act, on ? 21 : 2);
                knob.setLayoutParams(lp);
            }
        }
        if (modelValue != null) {
            modelValue.setText(com.starstwinkle.dsm.core.Api.modelName(p.model()));
        }
    }

    private void refreshBalance() {
        com.starstwinkle.dsm.core.Api.fetchBalance(act, (ok, text, err) -> {
            if (ok) {
                act.prefs().setBalance(text, System.currentTimeMillis());
                apiBalance.setText("余额：" + text);
            } else if (err != null && "unauthorized".equals(err.code)) {
                apiState.setText("密钥无效");
                apiState.setTextColor(t.danger);
                apiHint.setText(err.desc);
            }
        });
    }
}
