package com.starstwinkle.dsm.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.text.InputType;
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
import com.starstwinkle.dsm.core.Theme;

/** 引导页：让完全没用过 API 的人也能走完充值 -> 建密钥 -> 回填 -> 查余额。 */
public class OnboardingView extends FrameLayout implements MainActivity.Screen {

    private static final String URL_TOPUP = "https://platform.deepseek.com/top_up";
    private static final String URL_KEYS = "https://platform.deepseek.com/api_keys";

    private final MainActivity act;
    private final Theme t;

    private android.widget.EditText keyInput;
    private TextView errorView;
    private TextView okView;
    private TextView saveBtn;
    private LinearLayout balanceCard;
    private TextView balanceValue;
    private TextView balanceSub;
    private TextView topupBtn;

    public OnboardingView(MainActivity a) {
        super(a);
        this.act = a;
        this.t = a.theme();
        setBackgroundColor(t.bg);

        LinearLayout root = Ui.column(a);
        addView(root, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout top = Ui.row(a);
        top.setPadding(Ui.dp(a, 10), Ui.dp(a, 8), Ui.dp(a, 14), Ui.dp(a, 6));
        ImageView back = new ImageView(a);
        back.setImageResource(R.drawable.ic_back);
        back.setColorFilter(t.text2);
        back.setPadding(Ui.dp(a, 9), Ui.dp(a, 9), Ui.dp(a, 9), Ui.dp(a, 9));
        back.setOnClickListener(v -> act.back());
        top.addView(back, new LinearLayout.LayoutParams(Ui.dp(a, 40), Ui.dp(a, 40)));
        TextView title = Ui.tv(a, "升级为 API 对话", 16f, t.text, true);
        title.setPadding(Ui.dp(a, 4), 0, 0, 0);
        top.addView(title);
        root.addView(top);

        ScrollView sv = new ScrollView(a);
        sv.setVerticalScrollBarEnabled(false);
        root.addView(sv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout col = Ui.column(a);
        col.setPadding(Ui.dp(a, 16), Ui.dp(a, 6), Ui.dp(a, 16), Ui.dp(a, 30));
        sv.addView(col, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // 标题
        TextView badge = Ui.tv(a, "3 分钟搞定", 11f, t.brand, true);
        badge.setBackground(Ui.round(t.brandWeak, Ui.dp(a, 999)));
        badge.setPadding(Ui.dp(a, 9), Ui.dp(a, 4), Ui.dp(a, 9), Ui.dp(a, 4));
        LinearLayout badgeRow = Ui.row(a);
        badgeRow.addView(badge);
        col.addView(badgeRow);

        TextView h2 = Ui.tv(a, "为什么需要 API 密钥？", 21f, t.text, true);
        h2.setPadding(0, Ui.dp(a, 12), 0, 0);
        col.addView(h2);

        TextView desc = Ui.tv(a,
                "DeepSeek App 的免费额度每天有限。用自己的 API 密钥后，按量付费、速度独立，不再受 App 次数限制。",
                13.5f, t.text2, false);
        desc.setLineSpacing(Ui.dp(a, 3), 1.6f);
        desc.setPadding(0, Ui.dp(a, 8), 0, 0);
        col.addView(desc);

        // 步骤
        col.addView(step(1, "充值 10 元先用着", "10 元大约能聊几十万字，日常够用很久。",
                "打开充值页", v -> act.openBrowser(URL_TOPUP)));
        col.addView(step(2, "创建 API 密钥", "在开放平台里点「创建 API key」，复制那串 sk- 开头的字符。",
                "打开密钥页", v -> act.openBrowser(URL_KEYS)));
        col.addView(step(3, "粘贴到下面", "只保存在你手机本地，不会上传到任何第三方。", null, null));

        // 输入卡
        LinearLayout card = Ui.column(a);
        card.setPadding(Ui.dp(a, 16), Ui.dp(a, 15), Ui.dp(a, 16), Ui.dp(a, 14));
        card.setBackground(Ui.round(t.card, Ui.dp(a, 18), t.line, Ui.dp(a, 1)));
        TextView label = Ui.tv(a, "API 密钥", 12f, t.text3, true);
        card.addView(label);

        LinearLayout inputRow = Ui.row(a);
        inputRow.setPadding(0, Ui.dp(a, 8), 0, 0);
        keyInput = new android.widget.EditText(a);
        keyInput.setHint("sk-…");
        keyInput.setHintTextColor(t.text3);
        keyInput.setTextColor(t.text);
        keyInput.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, Ui.sp(a, 13.5f));
        keyInput.setSingleLine(true);
        keyInput.setTypeface(android.graphics.Typeface.MONOSPACE);
        keyInput.setBackground(Ui.round(t.card2, Ui.dp(a, 12), t.line, Ui.dp(a, 1)));
        keyInput.setPadding(Ui.dp(a, 12), 0, Ui.dp(a, 12), 0);
        keyInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        keyInput.setMinHeight(Ui.dp(a, 44));
        inputRow.addView(keyInput, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView paste = Ui.tv(a, "粘贴", 13f, t.text2, false);
        paste.setPadding(Ui.dp(a, 14), 0, Ui.dp(a, 14), 0);
        paste.setBackground(Ui.round(t.card2, Ui.dp(a, 12), t.line, Ui.dp(a, 1)));
        paste.setGravity(Gravity.CENTER);
        paste.setMinHeight(Ui.dp(a, 44));
        paste.setOnClickListener(v -> pasteFromClipboard());
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(a, 44));
        plp.leftMargin = Ui.dp(a, 8);
        inputRow.addView(paste, plp);
        card.addView(inputRow);

        errorView = Ui.tv(a, "", 12.5f, t.text, false);
        errorView.setLineSpacing(Ui.dp(a, 2), 1.55f);
        errorView.setPadding(Ui.dp(a, 12), Ui.dp(a, 10), Ui.dp(a, 12), Ui.dp(a, 10));
        errorView.setBackground(Ui.round(0x1AFF6B6B, Ui.dp(a, 12), 0x59FF6B6B, Ui.dp(a, 1)));
        errorView.setVisibility(GONE);
        LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        elp.topMargin = Ui.dp(a, 10);
        card.addView(errorView, elp);

        okView = Ui.tv(a, "", 12.5f, t.text, false);
        okView.setLineSpacing(Ui.dp(a, 2), 1.55f);
        okView.setPadding(Ui.dp(a, 12), Ui.dp(a, 10), Ui.dp(a, 12), Ui.dp(a, 10));
        okView.setBackground(Ui.round(0x1A46D39A, Ui.dp(a, 12), 0x5946D39A, Ui.dp(a, 1)));
        okView.setVisibility(GONE);
        LinearLayout.LayoutParams okp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        okp.topMargin = Ui.dp(a, 10);
        card.addView(okView, okp);

        saveBtn = Ui.tv(a, "保存并验证", 14.5f, 0xFFFFFFFF, true);
        saveBtn.setGravity(Gravity.CENTER);
        saveBtn.setBackground(Ui.round(t.brand, Ui.dp(a, 14)));
        saveBtn.setMinHeight(Ui.dp(a, 46));
        saveBtn.setOnClickListener(v -> saveAndVerify());
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(a, 46));
        slp.topMargin = Ui.dp(a, 12);
        card.addView(saveBtn, slp);

        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.topMargin = Ui.dp(a, 18);
        col.addView(card, clp);

        // 余额卡
        balanceCard = Ui.column(a);
        balanceCard.setGravity(Gravity.CENTER);
        balanceCard.setPadding(Ui.dp(a, 16), Ui.dp(a, 16), Ui.dp(a, 16), Ui.dp(a, 16));
        balanceCard.setBackground(Ui.round(t.card, Ui.dp(a, 18), t.line, Ui.dp(a, 1)));
        TextView bl = Ui.tv(a, "当前余额", 12f, t.text3, false);
        bl.setGravity(Gravity.CENTER);
        balanceCard.addView(bl);
        balanceValue = Ui.tv(a, "--", 30f, t.text, true);
        balanceValue.setGravity(Gravity.CENTER);
        balanceValue.setPadding(0, Ui.dp(a, 6), 0, Ui.dp(a, 2));
        balanceCard.addView(balanceValue);
        balanceSub = Ui.tv(a, "", 12f, t.text3, false);
        balanceSub.setGravity(Gravity.CENTER);
        balanceSub.setLineSpacing(Ui.dp(a, 2), 1.5f);
        balanceCard.addView(balanceSub);
        topupBtn = Ui.tv(a, "去充值", 12.5f, t.text, true);
        topupBtn.setGravity(Gravity.CENTER);
        topupBtn.setBackground(Ui.round(t.card2, Ui.dp(a, 10), t.line, Ui.dp(a, 1)));
        topupBtn.setPadding(Ui.dp(a, 14), Ui.dp(a, 8), Ui.dp(a, 14), Ui.dp(a, 8));
        topupBtn.setOnClickListener(v -> act.openBrowser(URL_TOPUP));
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = Ui.dp(a, 12);
        tlp.gravity = Gravity.CENTER_HORIZONTAL;
        balanceCard.addView(topupBtn, tlp);
        balanceCard.setVisibility(GONE);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.topMargin = Ui.dp(a, 16);
        col.addView(balanceCard, blp);

        // FAQ
        TextView faqTitle = Ui.tv(a, "常见问题", 12f, t.text3, true);
        faqTitle.setPadding(Ui.dp(a, 4), Ui.dp(a, 26), 0, Ui.dp(a, 8));
        col.addView(faqTitle);
        col.addView(faq("密钥会不会泄露？",
                "密钥只存在你手机的本地存储中，请求直接从手机发往 api.deepseek.com，本应用没有服务器。"));
        col.addView(faq("一定要先充值吗？",
                "新账号通常有赠送额度，可以先直接创建密钥试用；余额为 0 时接口会返回「余额不足」，届时再充值即可。"));
        col.addView(faq("不用 API 行不行？",
                "可以。不配置密钥时，会话页会提供「用官方 App 继续」，对话仍由 DeepSeek App 原生完成。"));

        refresh();
    }

    /* ---------- 子组件 ---------- */
    private View step(int no, String title, String desc, String btn, View.OnClickListener click) {
        LinearLayout row = Ui.row(act);
        row.setGravity(Gravity.TOP);
        row.setPadding(0, Ui.dp(act, 14), 0, Ui.dp(act, 14));

        TextView n = Ui.tv(act, String.valueOf(no), 12f, t.brand, true);
        n.setGravity(Gravity.CENTER);
        n.setBackground(Ui.oval(t.brandWeak));
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(Ui.dp(act, 24), Ui.dp(act, 24));
        nlp.rightMargin = Ui.dp(act, 12);
        row.addView(n, nlp);

        LinearLayout body = Ui.column(act);
        body.addView(Ui.tv(act, title, 14.5f, t.text, true));
        TextView d = Ui.tv(act, desc, 12.5f, t.text3, false);
        d.setLineSpacing(Ui.dp(act, 2), 1.55f);
        d.setPadding(0, Ui.dp(act, 4), 0, 0);
        body.addView(d);
        if (btn != null && click != null) {
            TextView b = Ui.tv(act, btn, 12.5f, t.brand, true);
            b.setPadding(Ui.dp(act, 12), Ui.dp(act, 7), Ui.dp(act, 12), Ui.dp(act, 7));
            b.setBackground(Ui.round(t.card2, Ui.dp(act, 10), t.line, Ui.dp(act, 1)));
            b.setOnClickListener(click);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = Ui.dp(act, 10);
            body.addView(b, lp);
        }
        row.addView(body, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout wrap = Ui.column(act);
        wrap.addView(row);
        View line = new View(act);
        line.setBackgroundColor(t.line);
        wrap.addView(line, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(act, 1)));
        return wrap;
    }

    private View faq(String q, String a) {
        LinearLayout box = Ui.column(act);
        box.setBackground(Ui.round(t.card, Ui.dp(act, 14), t.line, Ui.dp(act, 1)));
        box.setPadding(Ui.dp(act, 14), Ui.dp(act, 12), Ui.dp(act, 14), Ui.dp(act, 12));

        LinearLayout head = Ui.row(act);
        TextView qt = Ui.tv(act, q, 13f, t.text, false);
        head.addView(qt);
        head.addView(new View(act), new LinearLayout.LayoutParams(0, 1, 1f));
        TextView plus = Ui.tv(act, "+", 15f, t.text3, false);
        head.addView(plus);
        box.addView(head);

        TextView at = Ui.tv(act, a, 12.5f, t.text2, false);
        at.setLineSpacing(Ui.dp(act, 2), 1.62f);
        at.setPadding(0, Ui.dp(act, 8), 0, 0);
        at.setVisibility(GONE);
        box.addView(at);

        head.setOnClickListener(v -> {
            boolean open = at.getVisibility() == View.VISIBLE;
            at.setVisibility(open ? GONE : View.VISIBLE);
            plus.setText(open ? "+" : "−");
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Ui.dp(act, 8);
        box.setLayoutParams(lp);
        return box;
    }

    /* ---------- 逻辑 ---------- */
    private void pasteFromClipboard() {
        try {
            ClipboardManager cm = (ClipboardManager) act.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null && cm.hasPrimaryClip()) {
                ClipData clip = cm.getPrimaryClip();
                if (clip != null && clip.getItemCount() > 0) {
                    CharSequence text = clip.getItemAt(0).coerceToText(act);
                    if (text != null) {
                        keyInput.setText(text.toString().trim());
                        return;
                    }
                }
            }
            Ui.toast(act, "剪贴板里没有内容");
        } catch (Exception e) {
            Ui.toast(act, "读取剪贴板失败，请手动粘贴");
        }
    }

    private void saveAndVerify() {
        String key = keyInput.getText().toString().trim();
        errorView.setVisibility(GONE);
        okView.setVisibility(GONE);

        if (TextUtils.isEmpty(key)) {
            showError("请先填入 API 密钥。", null);
            return;
        }
        if (!key.startsWith("sk-")) {
            showError("这个密钥看起来不对：DeepSeek 的密钥以 sk- 开头，请检查是否复制完整。", null);
            return;
        }
        if (key.length() < 20) {
            showError("密钥太短了，可能是复制时被截断。请回密钥页重新复制整串。", null);
            return;
        }

        act.prefs().setApiKey(key);
        saveBtn.setText("正在验证…");
        saveBtn.setAlpha(0.7f);

        Api.fetchBalance(act, (ok, text, err) -> {
            saveBtn.setText("保存并验证");
            saveBtn.setAlpha(1f);
            if (ok) {
                act.prefs().setBalance(text, System.currentTimeMillis());
                okView.setVisibility(VISIBLE);
                okView.setText("验证通过，已开始使用 API 对话。");
                balanceCard.setVisibility(VISIBLE);
                balanceValue.setText(text);
                balanceSub.setText("余额充足，可以开始聊天了。");
                topupBtn.setVisibility(View.GONE);
                Ui.toast(act, "已保存");
            } else {
                if (err != null) showError(err.desc, err);
            }
        });
    }

    private void showError(String msg, NetError err) {
        errorView.setVisibility(VISIBLE);
        errorView.setText(msg);
        if (err != null) {
            String code = err.code;
            if ("no_balance".equals(code)) {
                balanceCard.setVisibility(VISIBLE);
                balanceValue.setText("¥0");
                balanceSub.setText("余额不足，充值时顺便确认一下是否有赠送额度已过期。");
                topupBtn.setVisibility(View.VISIBLE);
            } else if ("unauthorized".equals(code)) {
                errorView.setText(err.title + "：" + err.desc + "\n\n请确认密钥没有多余空格，也没在别处被删除。");
            }
        }
    }

    @Override
    public void refresh() {
        Prefs p = act.prefs();
        if (p.hasKey() && TextUtils.isEmpty(keyInput.getText())) {
            keyInput.setText(p.apiKey());
        }
        if (p.hasKey() && !p.balanceText().isEmpty()) {
            balanceCard.setVisibility(VISIBLE);
            balanceValue.setText(p.balanceText());
            balanceSub.setText("上次查询：" + Ui.relTime(p.balanceAt()));
            topupBtn.setVisibility(View.VISIBLE);
        }
    }
}
