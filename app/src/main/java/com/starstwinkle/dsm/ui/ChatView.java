package com.starstwinkle.dsm.ui;

import android.graphics.Color;
import android.text.TextUtils;
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
import com.starstwinkle.dsm.core.Api;
import com.starstwinkle.dsm.core.NetError;
import com.starstwinkle.dsm.core.Prefs;
import com.starstwinkle.dsm.core.Store;
import com.starstwinkle.dsm.core.Theme;

import java.util.List;

/** 对话页：消息流 + 流式渲染 + 错误卡片。 */
public class ChatView extends FrameLayout implements MainActivity.Screen {

    private final MainActivity act;
    private final Theme t;

    private final TextView titleView;
    private final TextView subView;
    private final LinearLayout msgContainer;
    private final ScrollView scroll;
    private final Composer composer;

    private Store.Conv conv;
    private Api.Task task;

    // 流式中的临时节点
    private LinearLayout liveRoot;
    private TextView liveThink;
    private TextView liveBubble;
    private LinearLayout liveThinkBox;
    private StringBuilder liveText = new StringBuilder();
    private StringBuilder liveReason = new StringBuilder();

    public ChatView(MainActivity a) {
        super(a);
        this.act = a;
        this.t = a.theme();
        setBackgroundColor(t.bg);

        LinearLayout root = Ui.column(a);
        addView(root, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        // ---- 顶栏 ----
        LinearLayout top = Ui.row(a);
        top.setPadding(Ui.dp(a, 10), Ui.dp(a, 8), Ui.dp(a, 10), Ui.dp(a, 6));

        ImageView back = new ImageView(a);
        back.setImageResource(R.drawable.ic_back);
        back.setColorFilter(t.text2);
        back.setPadding(Ui.dp(a, 9), Ui.dp(a, 9), Ui.dp(a, 9), Ui.dp(a, 9));
        back.setOnClickListener(v -> act.back());
        top.addView(back, new LinearLayout.LayoutParams(Ui.dp(a, 40), Ui.dp(a, 40)));

        LinearLayout center = Ui.column(a);
        center.setGravity(Gravity.CENTER);
        titleView = Ui.tv(a, "新对话", 15f, t.text, true);
        titleView.setSingleLine(true);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        titleView.setGravity(Gravity.CENTER);
        subView = Ui.tv(a, "DeepSeek · 对话模式", 11.5f, t.text3, false);
        subView.setGravity(Gravity.CENTER);
        subView.setPadding(0, Ui.dp(a, 2), 0, 0);
        center.addView(titleView);
        center.addView(subView);
        top.addView(center, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        ImageView more = new ImageView(a);
        more.setImageResource(R.drawable.ic_retry);
        more.setColorFilter(t.text2);
        more.setPadding(Ui.dp(a, 9), Ui.dp(a, 9), Ui.dp(a, 9), Ui.dp(a, 9));
        more.setOnClickListener(v -> act.toast("长按消息可以复制内容"));
        top.addView(more, new LinearLayout.LayoutParams(Ui.dp(a, 40), Ui.dp(a, 40)));
        root.addView(top);

        // ---- 消息区 ----
        scroll = new ScrollView(a);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        scroll.setPadding(Ui.dp(a, 16), Ui.dp(a, 4), Ui.dp(a, 16), Ui.dp(a, 10));
        msgContainer = Ui.column(a);
        msgContainer.setPadding(0, 0, 0, Ui.dp(a, 8));
        scroll.addView(msgContainer, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // ---- 输入 ----
        LinearLayout footer = Ui.column(a);
        footer.setPadding(Ui.dp(a, 12), Ui.dp(a, 6), Ui.dp(a, 12), Ui.dp(a, 10));
        composer = new Composer(a, t);
        footer.addView(composer);
        root.addView(footer);

        composer.setListener(new Composer.Listener() {
            @Override public void onSend(String text) {
                if (task != null) {
                    act.toast("正在回答，先停止再说");
                    return;
                }
                send(text);
            }
            @Override public void onStop() { stopStreaming(); }
            @Override public void onToggleThinking() {
                Prefs p = act.prefs();
                p.setThinking(!p.thinking());
                composer.syncMode();
                updateHeader();
            }
        });
    }

    /* ---------- 对外 ---------- */
    public void openConv(String id) {
        stopStreaming();
        conv = id == null ? null : act.store().find(id);
        if (conv != null) {
            act.prefs().setThinking(conv.thinking);
            renderAll();
        } else {
            renderEmpty();
        }
        composer.syncMode();
        updateHeader();
    }

    public void startNew(String text) {
        stopStreaming();
        conv = null;
        renderEmpty();
        send(text);
    }

    @Override
    public void refresh() {
        if (conv != null) {
            Store.Conv fresh = act.store().find(conv.id);
            if (fresh != null) conv = fresh;
        }
    }

    /* ---------- 渲染 ---------- */
    private void updateHeader() {
        boolean thinking = act.prefs().thinking();
        titleView.setText(conv == null ? "新对话" : conv.title);
        subView.setText("DeepSeek · " + (thinking ? "思考模式" : "对话模式")
                + " · " + Api.modelName(act.prefs().model()));
    }

    private void renderEmpty() {
        msgContainer.removeAllViews();
        LinearLayout empty = Ui.column(act);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(0, Ui.dp(act, 90), 0, 0);
        ImageView mark = new ImageView(act);
        mark.setImageResource(R.drawable.ic_spark);
        mark.setColorFilter(t.brand);
        empty.addView(mark, new LinearLayout.LayoutParams(Ui.dp(act, 34), Ui.dp(act, 34)));
        TextView tv = Ui.tv(act, "开始一个新对话", 13f, t.text3, false);
        tv.setPadding(0, Ui.dp(act, 12), 0, 0);
        empty.addView(tv);
        msgContainer.addView(empty);
    }

    private void renderAll() {
        msgContainer.removeAllViews();
        if (conv == null || conv.messages.isEmpty()) {
            renderEmpty();
            return;
        }
        for (Store.Msg m : conv.messages) {
            if ("user".equals(m.role)) {
                msgContainer.addView(userBubble(m.content));
            } else if (m.error != null) {
                msgContainer.addView(errorCard(m.error, false));
            } else {
                msgContainer.addView(aiBubble(m.content, m.reasoning));
            }
        }
        scrollToBottom(true);
    }

    private View userBubble(String text) {
        LinearLayout wrap = Ui.row(act);
        wrap.setGravity(Gravity.END);
        wrap.setPadding(0, Ui.dp(act, 6), 0, Ui.dp(act, 14));

        TextView bubble = Ui.tv(act, text, 15f, t.bubbleUserText, false);
        bubble.setLineSpacing(Ui.dp(act, 2), 1.5f);
        bubble.setBackground(Ui.round(t.bubbleUser, Ui.dp(act, 18)));
        bubble.setPadding(Ui.dp(act, 14), Ui.dp(act, 10), Ui.dp(act, 14), Ui.dp(act, 10));
        bubble.setTextIsSelectable(true);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.width = (int) (measureWidth() * 0.82f);
        wrap.addView(bubble, lp);
        return wrap;
    }

    private int measureWidth() {
        int w = getResources().getDisplayMetrics().widthPixels;
        return w - Ui.dp(act, 32);
    }

    private View aiShell() {
        LinearLayout wrap = Ui.column(act);
        wrap.setPadding(0, 0, 0, Ui.dp(act, 18));

        LinearLayout who = Ui.row(act);
        ImageView mark = new ImageView(act);
        mark.setImageResource(R.drawable.ic_spark);
        mark.setColorFilter(t.brand);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(Ui.dp(act, 14), Ui.dp(act, 14));
        mlp.rightMargin = Ui.dp(act, 5);
        who.addView(mark, mlp);
        TextView name = Ui.tv(act, "DeepSeek", 11.5f, t.text3, true);
        who.addView(name);
        who.setPadding(0, 0, 0, Ui.dp(act, 6));
        wrap.addView(who);
        return wrap;
    }

    private View aiBubble(String content, String reasoning) {
        LinearLayout wrap = (LinearLayout) aiShell();
        if (reasoning != null && !reasoning.isEmpty()) {
            wrap.addView(thinkBlock(reasoning, true));
        }
        if (content != null && !content.isEmpty()) {
            wrap.addView(Markdown.render(act, t, content));
        }
        return wrap;
    }

    /** 可折叠的思考过程 */
    private View thinkBlock(String reasoning, boolean collapsed) {
        LinearLayout box = Ui.column(act);
        box.setPadding(Ui.dp(act, 12), Ui.dp(act, 8), Ui.dp(act, 12), Ui.dp(act, 8));
        box.setBackground(Ui.round(t.card2, Ui.dp(act, 12)));

        LinearLayout head = Ui.row(act);
        TextView label = Ui.tv(act, collapsed ? "已深度思考" : "思考中…", 12.5f, t.text3, false);
        head.addView(label);
        ImageView chev = new ImageView(act);
        chev.setImageResource(R.drawable.ic_chevron);
        chev.setColorFilter(t.text3);
        chev.setRotation(0);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(Ui.dp(act, 13), Ui.dp(act, 13));
        clp.leftMargin = Ui.dp(act, 4);
        head.addView(chev, clp);
        box.addView(head);

        TextView body = Ui.tv(act, reasoning, 13f, t.text3, false);
        body.setLineSpacing(Ui.dp(act, 2), 1.6f);
        body.setPadding(0, Ui.dp(act, 6), 0, Ui.dp(act, 4));
        body.setVisibility(collapsed ? GONE : VISIBLE);
        box.addView(body);

        head.setOnClickListener(v -> {
            boolean open = body.getVisibility() == VISIBLE;
            body.setVisibility(open ? GONE : VISIBLE);
            chev.setRotation(open ? 0 : 90);
        });
        if (collapsed) chev.setRotation(0);
        return box;
    }

    private View errorCard(NetError e, boolean retryable) {
        LinearLayout card = Ui.column(act);
        card.setBackground(Ui.round(0x1AFF6B6B, Ui.dp(act, 14), 0x59FF6B6B, Ui.dp(act, 1)));
        card.setPadding(Ui.dp(act, 14), Ui.dp(act, 12), Ui.dp(act, 14), Ui.dp(act, 10));

        TextView title = Ui.tv(act, e.title, 14f, t.text, true);
        card.addView(title);
        TextView desc = Ui.tv(act, e.desc, 13f, t.text2, false);
        desc.setLineSpacing(Ui.dp(act, 2), 1.55f);
        desc.setPadding(0, Ui.dp(act, 5), 0, Ui.dp(act, 8));
        card.addView(desc);

        LinearLayout actions = Ui.row(act);
        if (e.action != NetError.Action.NONE) actions.addView(actionBtn(e.action));
        if (e.action2 != NetError.Action.NONE) actions.addView(actionBtn(e.action2));
        if (actions.getChildCount() > 0) card.addView(actions);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Ui.dp(act, 16);
        card.setLayoutParams(lp);
        return card;
    }

    private View actionBtn(NetError.Action a) {
        String label;
        switch (a) {
            case OPEN_KEY: label = "去填写密钥"; break;
            case TOPUP: label = "去充值"; break;
            case NATIVE: label = "用官方 App 继续"; break;
            case RETRY:
            default: label = "重试"; break;
        }
        TextView btn = Ui.tv(act, label, 12.5f, t.brand, true);
        btn.setPadding(Ui.dp(act, 12), Ui.dp(act, 7), Ui.dp(act, 12), Ui.dp(act, 7));
        btn.setBackground(Ui.round(0x1A4D6BFE, Ui.dp(act, 999), 0x664D6BFE, Ui.dp(act, 1)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = Ui.dp(act, 8);
        btn.setLayoutParams(lp);
        btn.setOnClickListener(v -> handleAction(a));
        return btn;
    }

    private void handleAction(NetError.Action a) {
        switch (a) {
            case OPEN_KEY: act.openOnboarding(); break;
            case TOPUP: act.openBrowser("https://platform.deepseek.com/top_up"); break;
            case NATIVE: act.openOfficialApp(); break;
            case RETRY: retryLast(); break;
            default: break;
        }
    }

    /* ---------- 发送 ---------- */
    private void send(String text) {
        if (!act.prefs().hasKey()) {
            showNoKeySheet(text);
            return;
        }
        if (conv == null) {
            conv = act.store().create(act.prefs().thinking());
        }
        Store.Msg user = new Store.Msg("user", text);
        conv.messages.add(user);
        conv.thinking = act.prefs().thinking();
        act.store().touch(conv);

        if (msgContainer.getChildCount() > 0) msgContainer.removeAllViews();
        renderAll();
        updateHeader();
        composer.clear();
        composer.setSending(true);

        startStream();
    }

    private void startStream() {
        liveText.setLength(0);
        liveReason.setLength(0);

        liveRoot = (LinearLayout) aiShell();
        liveThinkBox = (LinearLayout) Ui.column(act);
        liveThinkBox.setVisibility(GONE);
        liveThinkBox.setPadding(Ui.dp(act, 12), Ui.dp(act, 8), Ui.dp(act, 12), Ui.dp(act, 8));
        liveThinkBox.setBackground(Ui.round(t.card2, Ui.dp(act, 12)));
        TextView thinkTitle = Ui.tv(act, "思考中…", 12.5f, t.text3, false);
        liveThinkBox.addView(thinkTitle);
        liveThink = Ui.tv(act, "", 13f, t.text3, false);
        liveThink.setLineSpacing(Ui.dp(act, 2), 1.6f);
        liveThink.setPadding(0, Ui.dp(act, 6), 0, Ui.dp(act, 2));
        liveThinkBox.addView(liveThink);
        liveRoot.addView(liveThinkBox);

        liveBubble = Ui.tv(act, "", 15.5f, t.text, false);
        liveBubble.setLineSpacing(Ui.dp(act, 3), 1.62f);
        liveRoot.addView(liveBubble);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        msgContainer.addView(liveRoot, lp);
        scrollToBottom(true);

        task = Api.stream(act, conv, new Api.Cb() {
            @Override public void onDelta(String content, String reasoning) {
                if (reasoning != null && !reasoning.isEmpty()) {
                    liveReason.append(reasoning);
                    if (liveThinkBox.getVisibility() != VISIBLE) liveThinkBox.setVisibility(VISIBLE);
                    liveThink.setText(liveReason.toString());
                }
                if (content != null && !content.isEmpty()) {
                    liveText.append(content);
                    liveBubble.setText(liveText.toString());
                }
                scrollToBottom(false);
            }

            @Override public void onDone() {
                finishStream(true);
            }

            @Override public void onError(NetError e) {
                finishStream(false, e);
            }
        });
    }

    private void finishStream(boolean ok) {
        finishStream(ok, null);
    }

    private void finishStream(boolean ok, NetError error) {
        task = null;
        composer.setSending(false);
        if (conv == null) return;

        if (ok) {
            Store.Msg m = new Store.Msg("assistant", liveText.toString());
            m.reasoning = liveReason.toString();
            conv.messages.add(m);
            act.store().touch(conv);
            renderAll();
            updateHeader();
        } else {
            if (error == null) {
                error = new NetError("unknown", "出了点问题", "回答没能完成，可以重试。");
            }
            if (liveText.length() > 0) {
                Store.Msg m = new Store.Msg("assistant", liveText.toString());
                m.reasoning = liveReason.toString();
                conv.messages.add(m);
            }
            Store.Msg errMsg = new Store.Msg("assistant", "");
            errMsg.error = error;
            conv.messages.add(errMsg);
            act.store().touch(conv);
            renderAll();
        }
    }

    private void stopStreaming() {
        if (task != null) {
            task.cancel();
            task = null;
            composer.setSending(false);
            if (conv != null && liveText.length() > 0) {
                Store.Msg m = new Store.Msg("assistant", liveText.toString());
                m.reasoning = liveReason.toString();
                conv.messages.add(m);
                act.store().touch(conv);
            } else if (liveText.length() == 0) {
                // 没收到内容就直接移除占位
                if (liveRoot != null) msgContainer.removeView(liveRoot);
                return;
            }
            renderAll();
        }
    }

    private void retryLast() {
        if (conv == null || conv.messages.isEmpty()) return;
        Store.Msg last = null;
        for (int i = conv.messages.size() - 1; i >= 0; i--) {
            if ("user".equals(conv.messages.get(i).role)) { last = conv.messages.get(i); break; }
        }
        if (last == null) return;
        // 去掉末尾的错误占位
        while (!conv.messages.isEmpty() && conv.messages.get(conv.messages.size() - 1).error != null) {
            conv.messages.remove(conv.messages.size() - 1);
        }
        act.store().save();
        renderAll();
        composer.setSending(true);
        startStream();
    }

    /* ---------- 无密钥时的降级 ---------- */
    private void showNoKeySheet(String pendingText) {
        Ui.sheet(act, t, "还没有配置 API 密钥", new String[]{
                "去配置密钥（推荐）",
                "用官方 App 继续（原生对话）",
                "取消"
        }, -1, idx -> {
            if (idx == 0) act.openOnboarding();
            else if (idx == 1) act.openOfficialApp();
        });
    }

    /* ---------- 滚动 ---------- */
    private void scrollToBottom(boolean force) {
        scroll.post(() -> {
            View child = scroll.getChildAt(0);
            if (child == null) return;
            int max = child.getHeight() - scroll.getHeight();
            if (max <= 0) return;
            boolean nearBottom = scroll.getScrollY() + scroll.getHeight() + Ui.dp(act, 160) >= child.getHeight();
            if (force || nearBottom) scroll.smoothScrollTo(0, max);
        });
    }
}
