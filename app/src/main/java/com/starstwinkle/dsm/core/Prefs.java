package com.starstwinkle.dsm.core;

import android.content.Context;
import android.content.SharedPreferences;

/** 设置存储。全部本地保存，不含任何服务端。 */
public final class Prefs {

    private static final String FILE = "dsm_settings";
    private static Prefs instance;

    private final SharedPreferences sp;

    private Prefs(Context ctx) {
        sp = ctx.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static synchronized Prefs get(Context ctx) {
        if (instance == null) instance = new Prefs(ctx);
        return instance;
    }

    public static void resetCache() { instance = null; }

    // ---------- 字段 ----------
    public String apiKey() { return sp.getString("apiKey", ""); }
    public void setApiKey(String v) { sp.edit().putString("apiKey", v == null ? "" : v.trim()).apply(); }

    /** flash | pro */
    public String model() { return sp.getString("model", "flash"); }
    public void setModel(String v) { sp.edit().putString("model", v).apply(); }

    /** 思考模式开关 */
    public boolean thinking() { return sp.getBoolean("thinking", false); }
    public void setThinking(boolean v) { sp.edit().putBoolean("thinking", v).apply(); }

    /** low | high | max */
    public String effort() { return sp.getString("effort", "high"); }
    public void setEffort(String v) { sp.edit().putString("effort", v).apply(); }

    /** dark | light | system */
    public String theme() { return sp.getString("theme", "system"); }
    public void setTheme(String v) { sp.edit().putString("theme", v).apply(); }

    /** normal | large | xlarge */
    public String font() { return sp.getString("font", "normal"); }
    public void setFont(String v) { sp.edit().putString("font", v).apply(); }

    public String baseUrl() { return sp.getString("baseUrl", "https://api.deepseek.com"); }
    public void setBaseUrl(String v) { sp.edit().putString("baseUrl", v).apply(); }

    public boolean fallbackNative() { return sp.getBoolean("fallbackNative", true); }
    public void setFallbackNative(boolean v) { sp.edit().putBoolean("fallbackNative", v).apply(); }

    // 余额缓存
    public String balanceText() { return sp.getString("balanceText", ""); }
    public long balanceAt() { return sp.getLong("balanceAt", 0L); }
    public void setBalance(String text, long at) {
        sp.edit().putString("balanceText", text).putLong("balanceAt", at).apply();
    }

    public boolean hasKey() {
        String k = apiKey();
        return k != null && k.trim().length() >= 8;
    }

    public static String mask(String key) {
        if (key == null) return "";
        String k = key.trim();
        if (k.length() <= 14) return k.length() <= 4 ? k : k.substring(0, 4) + "…";
        return k.substring(0, 8) + "……" + k.substring(k.length() - 4);
    }
}
