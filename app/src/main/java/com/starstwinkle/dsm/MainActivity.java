package com.starstwinkle.dsm;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.starstwinkle.dsm.core.Prefs;
import com.starstwinkle.dsm.core.Store;
import com.starstwinkle.dsm.core.Theme;
import com.starstwinkle.dsm.ui.ChatView;
import com.starstwinkle.dsm.ui.HomeView;
import com.starstwinkle.dsm.ui.OnboardingView;
import com.starstwinkle.dsm.ui.SettingsView;
import com.starstwinkle.dsm.ui.Ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * 单 Activity 承载全部页面，用左右位移做转场，贴近官方 App 的导航手感。
 */
public class MainActivity extends Activity {

    public static final String S_HOME = "home";
    public static final String S_CHAT = "chat";
    public static final String S_SETTINGS = "settings";
    public static final String S_ONBOARD = "onboarding";

    private Theme theme;
    private Prefs prefs;
    private Store store;
    private FrameLayout stage;

    private final Map<String, View> cache = new HashMap<>();
    private final ArrayList<String> stack = new ArrayList<>();
    private String current = null;
    private int statusBarHeight;

    /** 每个页面若实现此接口，主题切换后可自行刷新 */
    public interface Screen {
        void refresh();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = Prefs.get(this);
        store = Store.get(this);
        theme = Theme.resolve(this);

        Window w = getWindow();
        w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        w.setStatusBarColor(Color.TRANSPARENT);
        w.setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= 23) {
            int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
            w.getDecorView().setSystemUiVisibility(flags);
            if (!theme.dark) {
                w.getDecorView().setSystemUiVisibility(flags | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
            }
        }

        statusBarHeight = Ui.statusBarHeight(this);
        applyFontScale();

        stage = new FrameLayout(this);
        stage.setBackgroundColor(theme.bg);
        stage.setPadding(0, statusBarHeight, 0, 0);
        setContentView(stage);

        go(S_HOME, false);
    }

    private void applyFontScale() {
        String f = prefs.font();
        if ("large".equals(f)) Ui.fontScale = 1.08f;
        else if ("xlarge".equals(f)) Ui.fontScale = 1.16f;
        else Ui.fontScale = 1f;
    }

    public Theme theme() { return theme; }
    public Prefs prefs() { return prefs; }
    public Store store() { return store; }
    public int statusBar() { return statusBarHeight; }

    /* ---------- 路由 ---------- */
    public void go(String screen, boolean animate) {
        if (screen.equals(current)) return;

        View next = cache.get(screen);
        if (next == null) {
            next = create(screen);
            cache.put(screen, next);
            stage.addView(next, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }

        View prev = current == null ? null : cache.get(current);

        if (prev != null) {
            if (animate) {
                prev.animate().translationX(-stage.getWidth() * 0.22f).alpha(0f)
                        .setDuration(220).start();
            }
            prev.setVisibility(View.VISIBLE);
        }

        next.setVisibility(View.VISIBLE);
        next.bringToFront();
        if (animate) {
            next.setTranslationX(stage.getWidth());
            next.setAlpha(1f);
            next.animate().translationX(0f).setDuration(280).start();
        } else {
            next.setTranslationX(0f);
            next.setAlpha(1f);
        }

        if (prev != null) {
            prev.postDelayed(() -> {
                prev.setVisibility(View.GONE);
                prev.setTranslationX(0f);
                prev.setAlpha(1f);
            }, 300);
        }

        current = screen;
        if (!stack.contains(screen)) stack.add(screen);
        if (next instanceof Screen) ((Screen) next).refresh();
    }

    public void back() {
        if (stack.size() <= 1) {
            finish();
            return;
        }
        stack.remove(stack.size() - 1);
        String target = stack.get(stack.size() - 1);
        current = null; // 强制重新进入
        go(target, true);
    }

    private View create(String screen) {
        switch (screen) {
            case S_CHAT: return new ChatView(this);
            case S_SETTINGS: return new SettingsView(this);
            case S_ONBOARD: return new OnboardingView(this);
            case S_HOME:
            default: return new HomeView(this);
        }
    }

    /* ---------- 供页面调用的动作 ---------- */
    public void openChat(String convId) {
        View v = cache.get(S_CHAT);
        if (v instanceof ChatView) ((ChatView) v).openConv(convId);
        go(S_CHAT, true);
    }

    /** 首页/推荐词里直接开始一个新对话并发出去 */
    public void startChatWith(String text) {
        go(S_CHAT, true);
        View v = cache.get(S_CHAT);
        if (v instanceof ChatView) ((ChatView) v).startNew(text);
    }

    public void newChat() {
        View v = cache.get(S_CHAT);
        if (v instanceof ChatView) ((ChatView) v).openConv(null);
        go(S_CHAT, true);
    }

    public void openSettings() { go(S_SETTINGS, true); }

    public void openOnboarding() { go(S_ONBOARD, true); }

    public void openBrowser(String url) {
        Intent i = new Intent(this, BrowserActivity.class);
        i.putExtra("url", url);
        startActivity(i);
    }

    /** 没有密钥时把用户交回官方 App，这叫"原生调用"回退 */
    public void openOfficialApp() {
        Intent i = getPackageManager().getLaunchIntentForPackage("com.deepseek.chat");
        if (i != null) {
            try {
                startActivity(i);
                return;
            } catch (ActivityNotFoundException ignored) {
            }
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://chat.deepseek.com")));
        } catch (Exception e) {
            Ui.toast(this, "没有找到 DeepSeek App，也没有可用的浏览器");
        }
    }

    public void toast(String msg) { Ui.toast(this, msg); }

    /** 主题/字体变化后整体重建，避免逐个控件改色出错 */
    public void rebuildAll() {
        applyFontScale();
        theme = Theme.resolve(this);
        stage.setBackgroundColor(theme.bg);
        Window w = getWindow();
        if (Build.VERSION.SDK_INT >= 23) {
            int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
            if (!theme.dark) flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            w.getDecorView().setSystemUiVisibility(flags);
        }
        String keep = current;
        cache.clear();
        // 移除除占位外的所有页面
        stage.removeAllViews();
        stack.clear();
        current = null;
        go(keep == null || keep.equals(S_CHAT) ? S_HOME : keep, false);
    }

    @Override
    public void onBackPressed() {
        back();
    }
}
