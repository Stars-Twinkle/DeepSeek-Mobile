package com.starstwinkle.dsm.core;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** 会话与消息的内存模型 + 本地持久化（JSON 存在 SharedPreferences）。 */
public final class Store {

    private static final String FILE = "dsm_convs";
    private static final String KEY = "convs";

    private static Store instance;
    private final SharedPreferences sp;
    public final List<Conv> convs = new ArrayList<>();

    // ---------- 模型 ----------
    public static final class Msg {
        public String role;        // user | assistant
        public String content = "";
        public String reasoning = "";
        public long ts = System.currentTimeMillis();
        public NetError error;     // 非空表示这条 AI 消息是错误提示

        public Msg() {}
        public Msg(String role, String content) { this.role = role; this.content = content; }
    }

    public static final class Conv {
        public String id;
        public String title = "新对话";
        public long createdAt = System.currentTimeMillis();
        public long updatedAt = createdAt;
        public boolean thinking;
        public final List<Msg> messages = new ArrayList<>();
    }

    private Store(Context ctx) {
        sp = ctx.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
        load();
    }

    public static synchronized Store get(Context ctx) {
        if (instance == null) instance = new Store(ctx);
        return instance;
    }

    // ---------- 持久化 ----------
    private void load() {
        convs.clear();
        String raw = sp.getString(KEY, null);
        if (raw == null) return;
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Conv c = new Conv();
                c.id = o.optString("id");
                c.title = o.optString("title", "新对话");
                c.createdAt = o.optLong("createdAt", System.currentTimeMillis());
                c.updatedAt = o.optLong("updatedAt", c.createdAt);
                c.thinking = o.optBoolean("thinking", false);
                JSONArray ms = o.optJSONArray("messages");
                if (ms != null) {
                    for (int j = 0; j < ms.length(); j++) {
                        JSONObject m = ms.getJSONObject(j);
                        Msg msg = new Msg();
                        msg.role = m.optString("role");
                        msg.content = m.optString("content", "");
                        msg.reasoning = m.optString("reasoning", "");
                        msg.ts = m.optLong("ts", 0);
                        if (!m.isNull("errCode")) {
                            msg.error = new NetError(
                                    m.optString("errCode"),
                                    m.optString("errTitle"),
                                    m.optString("errDesc"));
                        }
                        c.messages.add(msg);
                    }
                }
                if (c.id != null && !c.id.isEmpty()) convs.add(c);
            }
        } catch (JSONException ignored) {
        }
    }

    public void save() {
        try {
            JSONArray arr = new JSONArray();
            int limit = Math.min(convs.size(), 80);
            for (int i = 0; i < limit; i++) {
                Conv c = convs.get(i);
                JSONObject o = new JSONObject();
                o.put("id", c.id);
                o.put("title", c.title);
                o.put("createdAt", c.createdAt);
                o.put("updatedAt", c.updatedAt);
                o.put("thinking", c.thinking);
                JSONArray ms = new JSONArray();
                for (Msg m : c.messages) {
                    JSONObject mo = new JSONObject();
                    mo.put("role", m.role);
                    mo.put("content", m.content);
                    if (m.reasoning != null && !m.reasoning.isEmpty()) mo.put("reasoning", m.reasoning);
                    mo.put("ts", m.ts);
                    if (m.error != null) {
                        mo.put("errCode", m.error.code);
                        mo.put("errTitle", m.error.title);
                        mo.put("errDesc", m.error.desc);
                    }
                    ms.put(mo);
                }
                o.put("messages", ms);
                arr.put(o);
            }
            sp.edit().putString(KEY, arr.toString()).apply();
        } catch (JSONException ignored) {
        }
    }

    // ---------- 操作 ----------
    public Conv create(boolean thinking) {
        Conv c = new Conv();
        c.id = "c" + Long.toString(System.currentTimeMillis(), 36)
                + Integer.toString((int) (Math.random() * 1000), 36);
        c.thinking = thinking;
        convs.add(0, c);
        save();
        return c;
    }

    public Conv find(String id) {
        if (id == null) return null;
        for (Conv c : convs) if (id.equals(c.id)) return c;
        return null;
    }

    public void touch(Conv c) {
        if (c == null) return;
        c.updatedAt = System.currentTimeMillis();
        if ("新对话".equals(c.title)) {
            for (Msg m : c.messages) {
                if ("user".equals(m.role) && m.content != null && !m.content.isEmpty()) {
                    String t = m.content.replaceAll("\\s+", " ").trim();
                    c.title = t.length() > 20 ? t.substring(0, 20) + "…" : t;
                    break;
                }
            }
        }
        convs.remove(c);
        convs.add(0, c);
        save();
    }

    public void delete(String id) {
        for (int i = convs.size() - 1; i >= 0; i--) {
            if (convs.get(i).id.equals(id)) convs.remove(i);
        }
        save();
    }

    public void clear() {
        convs.clear();
        save();
    }
}
