package com.starstwinkle.dsm.hook;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import de.robv.android.xposed.XSharedPreferences;

/** 模块配置。优先读模块设置页写入的 prefs，读不到就用默认值。 */
final class Config {

    private static XSharedPreferences sp;
    private static long lastLoad;

    private static synchronized void load() {
        long now = System.currentTimeMillis();
        if (sp != null && now - lastLoad < 3000) return;
        lastLoad = now;
        try {
            if (sp == null) {
                sp = new XSharedPreferences("com.starstwinkle.dsm.hook", "dsm_hook");
                sp.makeWorldReadable();
            }
            sp.reload();
        } catch (Throwable t) {
            HookEntry.log("config load failed: " + t);
        }
    }

    /** 调试兜底：/data/local/tmp/dsm_config.json（adb 可直接写，方便联调） */
    private static String fromFile(String key) {
        try {
            java.io.File f = new java.io.File("/data/local/tmp/dsm_config.json");
            if (!f.exists()) return null;
            byte[] data = new byte[(int) f.length()];
            java.io.FileInputStream in = new java.io.FileInputStream(f);
            int off = 0;
            while (off < data.length) {
                int n = in.read(data, off, data.length - off);
                if (n <= 0) break;
                off += n;
            }
            in.close();
            org.json.JSONObject o = new org.json.JSONObject(
                    new String(data, java.nio.charset.StandardCharsets.UTF_8));
            String v = o.optString(key, "");
            return v.isEmpty() ? null : v;
        } catch (Throwable t) {
            return null;
        }
    }

    private static String get(String key, String def) {
        String f = fromFile(key);
        if (f != null) return f;
        load();
        try {
            if (sp != null) {
                String v = sp.getString(key, def);
                if (v != null && !v.isEmpty()) return v;
            }
        } catch (Throwable ignored) {
        }
        return def;
    }

    static String baseUrl() {
        return get("baseUrl", "http://127.0.0.1:8765");
    }

    static String apiKey() {
        return get("apiKey", "sk-mock-1234567890");
    }

    static String model() {
        String m = get("model", "deepseek-flash");
        return m.contains("pro") ? "deepseek-v4-pro" : "deepseek-flash";
    }

    private static final String PROMPT =
            "你是 DeepSeek，正在用手机和用户聊天。\n" +
            "\n" +
            "说话方式：\n" +
            "1. 直接回答问题，不要寒暄、不要复述用户的话、不要说“作为一个AI”这类套话。\n" +
            "2. 默认简短：能两句说清就别写五段。用户明确要求详细时才加长。\n" +
            "3. 口语化、自然，但涉及事实和步骤时保持准确。\n" +
            "4. 需要列举时用短列表；给代码时用代码块并标注语言。\n" +
            "5. 不确定的事情就说不确定，不要编造。\n" +
            "6. 你看不到图片和文件，也没法联网，除非用户把内容粘贴进来。\n" +
            "7. 用户用别的语言提问，就用那种语言回答；默认简体中文。";

    static String systemPrompt(boolean thinking) {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA);
        String p = PROMPT + "\n\n当前时间：" + f.format(new Date());
        if (thinking) p += "\n这一轮先在内部想清楚，只把结论写给用户。";
        return p;
    }
}
