package com.starstwinkle.dsm.ui;

import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.starstwinkle.dsm.MainActivity;
import com.starstwinkle.dsm.R;
import com.starstwinkle.dsm.core.Store;
import com.starstwinkle.dsm.core.Theme;

/** 首页：品牌栏 + 大标题 + 输入卡片 + 最近对话。 */
public class HomeView extends FrameLayout implements MainActivity.Screen {

    private final MainActivity act;
    private final Theme t;
    private LinearLayout convList;
    private LinearLayout listHead;
    private View notice;
    private Composer composer;

    public HomeView(MainActivity a) {
        super(a);
        this.act = a;
        this.t = a.theme();

        ScrollView sv = new ScrollView(a);
        sv.setFillViewport(false);
        sv.setVerticalScrollBarEnabled(false);
        addView(sv, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout col = Ui.column(a);
        int pad = Ui.dp(a, 18);
        col.setPadding(pad, Ui.dp(a, 8), pad, Ui.dp(a, 28));
        sv.addView(col, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // ---- 顶栏 ----
        LinearLayout top = Ui.row(a);
        LinearLayout brand = Ui.row(a);
        ImageView mark = new ImageView(a);
        mark.setImageResource(R.drawable.ic_spark);
        mark.setColorFilter(t.brand);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(Ui.dp(a, 20), Ui.dp(a, 20));
        mlp.rightMargin = Ui.dp(a, 7);
        brand.addView(mark, mlp);
        TextView name = Ui.tv(a, "DeepSeek", 17f, t.text, true);
        brand.addView(name);
        top.addView(brand);

        top.addView(new View(a), new LinearLayout.LayoutParams(0, 1, 1f));

        ImageView settings = new ImageView(a);
        settings.setImageResource(R.drawable.ic_settings);
        settings.setColorFilter(t.text2);
        settings.setPadding(Ui.dp(a, 9), Ui.dp(a, 9), Ui.dp(a, 9), Ui.dp(a, 9));
        settings.setOnClickListener(v -> act.openSettings());
        top.addView(settings, new LinearLayout.LayoutParams(Ui.dp(a, 38), Ui.dp(a, 38)));
        col.addView(top);

        // ---- 标题 ----
        TextView title = Ui.tv(a, "有什么可以帮你的？", 26f, t.text, true);
        title.setPadding(0, Ui.dp(a, 22), 0, 0);
        col.addView(title);

        TextView sub = Ui.tv(a, "用 API 对话，不再受每日次数限制", 13f, t.text3, false);
        sub.setPadding(0, Ui.dp(a, 8), 0, Ui.dp(a, 16));
        col.addView(sub);

        // ---- 未配置提示 ----
        notice = buildNotice(a);
        col.addView(notice);

        // ---- 输入 ----
        composer = new Composer(a, t);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.topMargin = Ui.dp(a, 2);
        col.addView(composer, clp);
        composer.setListener(new Composer.Listener() {
            @Override public void onSend(String text) { act.startChatWith(text); }
            @Override public void onStop() {}
            @Override public void onToggleThinking() {
                boolean next = !act.prefs().thinking();
                act.prefs().setThinking(next);
                composer.syncMode();
                chatComposerSync();
            }
        });

        // ---- 推荐 ----
        HorizontalScrollView hs = new HorizontalScrollView(a);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout chips = Ui.row(a);
        chips.setPadding(0, Ui.dp(a, 14), 0, 0);
        addChip(chips, "帮我写一封请假邮件");
        addChip(chips, "解释一下什么是闭包");
        addChip(chips, "今晚吃什么，给点建议");
        hs.addView(chips);
        col.addView(hs);

        // ---- 最近对话 ----
        listHead = Ui.row(a);
        listHead.setPadding(0, Ui.dp(a, 26), 0, Ui.dp(a, 4));
        TextView lh = Ui.tv(a, "最近对话", 12.5f, t.text3, true);
        listHead.addView(lh);
        listHead.addView(new View(a), new LinearLayout.LayoutParams(0, 1, 1f));
        col.addView(listHead);

        convList = Ui.column(a);
        col.addView(convList);

        refresh();
    }

    private void chatComposerSync() {
        View chat = null;
        try {
            chat = act.getWindow().getDecorView();
        } catch (Exception ignored) {
        }
    }

    private void addChip(LinearLayout parent, String text) {
        TextView chip = Ui.tv(act, text, 12.5f, t.text2, false);
        chip.setPadding(Ui.dp(act, 13), Ui.dp(act, 8), Ui.dp(act, 13), Ui.dp(act, 8));
        chip.setBackground(Ui.round(0x00000000, Ui.dp(act, 999), t.line, Ui.dp(act, 1)));
        chip.setOnClickListener(v -> act.startChatWith(text));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = Ui.dp(act, 8);
        parent.addView(chip, lp);
    }

    private View buildNotice(MainActivity a) {
        LinearLayout n = Ui.row(a);
        n.setPadding(Ui.dp(a, 14), Ui.dp(a, 12), Ui.dp(a, 12), Ui.dp(a, 12));
        n.setBackground(Ui.round(t.brandWeak, Ui.dp(a, 16), 0x664D6BFE, Ui.dp(a, 1)));

        View dot = new View(a);
        dot.setBackground(Ui.oval(t.brand));
        LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(Ui.dp(a, 7), Ui.dp(a, 7));
        dp.rightMargin = Ui.dp(a, 10);
        n.addView(dot, dp);

        LinearLayout texts = Ui.column(a);
        TextView b = Ui.tv(a, "还未升级为 API 对话", 13.5f, t.text, true);
        TextView em = Ui.tv(a, "配置密钥后即可无限畅聊，点此开始", 12f, t.text2, false);
        em.setPadding(0, Ui.dp(a, 2), 0, 0);
        texts.addView(b);
        texts.addView(em);
        n.addView(texts, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        ImageView chev = new ImageView(a);
        chev.setImageResource(R.drawable.ic_chevron);
        chev.setColorFilter(t.brand);
        n.addView(chev, new LinearLayout.LayoutParams(Ui.dp(a, 18), Ui.dp(a, 18)));

        n.setOnClickListener(v -> act.openOnboarding());
        return n;
    }

    @Override
    public void refresh() {
        boolean hasKey = act.prefs().hasKey();
        notice.setVisibility(hasKey ? GONE : VISIBLE);
        if (composer != null) composer.syncMode();

        convList.removeAllViews();
        Store store = act.store();
        if (store.convs.isEmpty()) {
            listHead.setVisibility(GONE);
            TextView empty = Ui.tv(act,
                    "还没有对话。\n在上面的输入框里说点什么，就会出现在这里。", 12.5f, t.text3, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, Ui.dp(act, 18), 0, Ui.dp(act, 18));
            empty.setLineSpacing(Ui.dp(act, 3), 1.55f);
            convList.addView(empty);
            return;
        }
        listHead.setVisibility(VISIBLE);
        for (Store.Conv c : store.convs) {
            convList.addView(convItem(c));
        }
    }

    private View convItem(Store.Conv c) {
        LinearLayout row = Ui.row(act);
        row.setPadding(Ui.dp(act, 10), Ui.dp(act, 11), Ui.dp(act, 8), Ui.dp(act, 11));
        row.setBackground(Ui.ripple(act, Color.TRANSPARENT, Ui.dp(act, 14), t.line));

        LinearLayout iconBox = new LinearLayout(act);
        iconBox.setGravity(Gravity.CENTER);
        iconBox.setBackground(Ui.round(t.card2, Ui.dp(act, 10)));
        ImageView icon = new ImageView(act);
        icon.setImageResource(R.drawable.ic_spark);
        icon.setColorFilter(t.text2);
        iconBox.addView(icon, new LinearLayout.LayoutParams(Ui.dp(act, 16), Ui.dp(act, 16)));
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(Ui.dp(act, 34), Ui.dp(act, 34));
        ilp.rightMargin = Ui.dp(act, 12);
        row.addView(iconBox, ilp);

        LinearLayout body = Ui.column(act);
        TextView title = Ui.tv(act, c.title, 14.5f, t.text, false);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        TextView meta = Ui.tv(act, Ui.relTime(c.updatedAt) + " · " + c.messages.size() + " 条",
                11.5f, t.text3, false);
        meta.setPadding(0, Ui.dp(act, 3), 0, 0);
        body.addView(title);
        body.addView(meta);
        row.addView(body, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        ImageView del = new ImageView(act);
        del.setImageResource(R.drawable.ic_trash);
        del.setColorFilter(t.text3);
        del.setPadding(Ui.dp(act, 8), Ui.dp(act, 8), Ui.dp(act, 8), Ui.dp(act, 8));
        del.setOnClickListener(v -> Ui.confirm(act, t, "删除这个对话？", "删除后无法恢复。",
                "删除", "取消", () -> {
                    act.store().delete(c.id);
                    refresh();
                }));
        row.addView(del, new LinearLayout.LayoutParams(Ui.dp(act, 32), Ui.dp(act, 32)));

        row.setOnClickListener(v -> act.openChat(c.id));
        return row;
    }
}
