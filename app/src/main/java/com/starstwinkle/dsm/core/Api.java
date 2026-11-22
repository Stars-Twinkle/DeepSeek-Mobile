package com.starstwinkle.dsm.core;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * DeepSeek API 接入层。
 *
 * 两个要点：
 * 1. 每次请求都带上一段固定的对话提示词（官方 App 的说话方式），
 *    这是解决"裸调 API 对话不像人"的关键。
 * 2. 所有网络/接口错误都翻译成中文，并给出可执行动作。
 */
public final class Api {

    public static final String MODEL_FLASH = "deepseek-flash";
    public static final String MODEL_PRO = "deepseek-v4-pro";

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    /* ---------- 提示词 ---------- */
    private static final String PROMPT_BASE =
            "你是 DeepSeek，正在用手机和用户聊天。\n" +
            "\n" +
            "说话方式：\n" +
            "1. 直接回答问题，不要寒暄、不要复述用户的话、不要说“作为一个AI”“希望这对你有帮助”这类套话。\n" +
            "2. 默认简短：能两句说清就别写五段。用户明确要求“详细”“展开”“写长一点”时才加长。\n" +
            "3. 口语化、自然，像朋友在微信上回消息，但涉及事实和步骤时保持准确。\n" +
            "4. 需要列举时用短列表；给代码时用代码块并标注语言；不要滥用加粗和标题。\n" +
            "5. 不确定的事情就说不确定，不要编造数据、链接、引用或作者。\n" +
            "6. 你看不到图片和文件，也没法联网，除非用户把内容直接粘贴进对话。\n" +
            "7. 涉及实时信息（今天、现在、最新）时，用下面给出的当前时间作参考。\n" +
            "8. 用户用别的语言提问，就用那种语言回答；默认简体中文。";

    private static final String PROMPT_THINKING =
            "\n这一轮请先在内部把问题想清楚再开口，但只把结论写给用户，不要把思考过程复述一遍。";

    public static String systemPrompt(boolean thinking) {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm（EEEE）", Locale.CHINA);
        String p = PROMPT_BASE + "\n\n当前时间：" + f.format(new Date());
        if (thinking) p += PROMPT_THINKING;
        return p;
    }

    public static String modelId(String pref) {
        return "pro".equals(pref) ? MODEL_PRO : MODEL_FLASH;
    }

    public static String modelName(String pref) {
        return "pro".equals(pref) ? "Pro" : "Flash";
    }

    /* ---------- 请求体 ---------- */
    static JSONObject buildBody(Store.Conv conv, Prefs prefs, boolean stream) throws JSONException {
        JSONArray messages = new JSONArray();

        JSONObject sys = new JSONObject();
        sys.put("role", "system");
        sys.put("content", systemPrompt(prefs.thinking()));
        messages.put(sys);

        List<Store.Msg> all = conv.messages;
        // 只取正常对话内容，错误占位不算上下文
        JSONArray history = new JSONArray();
        for (Store.Msg m : all) {
            if (m.error != null) continue;
            if (!"user".equals(m.role) && !"assistant".equals(m.role)) continue;
            if (m.content == null || m.content.isEmpty()) continue;
            JSONObject o = new JSONObject();
            o.put("role", m.role);
            o.put("content", m.content);
            history.put(o);
        }
        // 从尾部往回取，控制在 24 条 / 24000 字符以内
        int begin = Math.max(0, history.length() - 24);
        int used = 0;
        int start = history.length();
        for (int i = history.length() - 1; i >= begin; i--) {
            JSONObject o = history.getJSONObject(i);
            used += o.optString("content", "").length();
            if (used > 24000 && start - i >= 2) break;
            start = i;
        }
        for (int i = start; i < history.length(); i++) {
            messages.put(history.getJSONObject(i));
        }

        JSONObject body = new JSONObject();
        body.put("model", modelId(prefs.model()));
        body.put("messages", messages);
        body.put("stream", stream);
        body.put("max_tokens", 8192);

        if (prefs.thinking()) {
            JSONObject thinking = new JSONObject();
            thinking.put("type", "enabled");
            body.put("thinking", thinking);
            body.put("reasoning_effort", prefs.effort());
            // 思考模式不支持 temperature，不发
        } else {
            JSONObject thinking = new JSONObject();
            thinking.put("type", "disabled");
            body.put("thinking", thinking);
            body.put("temperature", 1.3);
        }
        return body;
    }

    /* ---------- 流式对话 ---------- */
    public interface Cb {
        void onDelta(String content, String reasoning);
        void onDone();
        void onError(NetError e);
    }

    public static final class Task {
        volatile boolean cancelled;
        HttpURLConnection conn;

        public void cancel() {
            cancelled = true;
            HttpURLConnection c = conn;
            if (c != null) {
                try { c.disconnect(); } catch (Exception ignored) {}
            }
        }
    }

    public static Task stream(final Context ctx, final Store.Conv conv, final Cb cb) {
        final Task task = new Task();
        final Prefs prefs = Prefs.get(ctx);

        Thread t = new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                String url = prefs.baseUrl().replaceAll("/+$", "") + "/chat/completions";
                conn = (HttpURLConnection) new URL(url).openConnection();
                task.conn = conn;
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(20000);
                conn.setReadTimeout(120000);
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                conn.setRequestProperty("Authorization", "Bearer " + prefs.apiKey().trim());
                conn.setRequestProperty("Accept", "text/event-stream");

                byte[] payload = buildBody(conv, prefs, true).toString().getBytes("UTF-8");
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(payload);
                    os.flush();
                }

                int code = conn.getResponseCode();
                if (code < 200 || code >= 300) {
                    String detail = readAll(conn.getErrorStream());
                    detail = extractMessage(detail);
                    fail(task, cb, NetError.fromHttp(code, detail));
                    return;
                }

                InputStream is = conn.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"), 4096);
                String line;
                boolean gotAny = false;
                while ((line = reader.readLine()) != null) {
                    if (task.cancelled) return;
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith(":")) continue;
                    if (!line.startsWith("data:")) continue;
                    String data = line.substring(5).trim();
                    if ("[DONE]".equals(data)) break;

                    JSONObject json;
                    try {
                        json = new JSONObject(data);
                    } catch (JSONException e) {
                        continue;
                    }
                    if (json.has("error")) {
                        JSONObject err = json.optJSONObject("error");
                        String msg = err != null ? err.optString("message") : json.optString("error");
                        fail(task, cb, new NetError("stream_error", "回答中断",
                                "服务器在生成过程中返回了错误：" + msg + "。", NetError.Action.RETRY,
                                NetError.Action.NONE));
                        return;
                    }
                    JSONArray choices = json.optJSONArray("choices");
                    if (choices == null || choices.length() == 0) continue;
                    JSONObject choice = choices.getJSONObject(0);
                    JSONObject delta = choice.optJSONObject("delta");
                    if (delta == null) continue;
                    String reasoning = delta.optString("reasoning_content", "");
                    String content = delta.optString("content", "");
                    if (!reasoning.isEmpty() || !content.isEmpty()) {
                        gotAny = true;
                        final String c1 = content, r1 = reasoning;
                        MAIN.post(() -> cb.onDelta(c1, r1));
                    }
                }

                if (!task.cancelled) {
                    final boolean empty = !gotAny;
                    MAIN.post(() -> {
                        if (empty) {
                            cb.onError(new NetError("empty", "没有收到内容",
                                    "服务器返回了空回答，可能是网络中断。重试一次通常就好。",
                                    NetError.Action.RETRY, NetError.Action.NONE));
                        } else {
                            cb.onDone();
                        }
                    });
                }
            } catch (Exception e) {
                if (!task.cancelled) {
                    fail(task, cb, NetError.fromException(e));
                }
            } finally {
                if (conn != null) conn.disconnect();
            }
        }, "dsm-stream");
        t.setDaemon(true);
        t.start();
        return task;
    }

    private static void fail(Task task, Cb cb, NetError e) {
        if (task.cancelled) return;
        MAIN.post(() -> cb.onError(e));
    }

    private static String readAll(InputStream is) {
        if (is == null) return "";
        try (BufferedReader r = new BufferedReader(new InputStreamReader(is, "UTF-8"))) {
            StringBuilder sb = new StringBuilder();
            String l;
            while ((l = r.readLine()) != null) {
                sb.append(l);
                if (sb.length() > 800) break;
            }
            return sb.toString();
        } catch (IOException e) {
            return "";
        }
    }

    private static String extractMessage(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        try {
            JSONObject o = new JSONObject(raw);
            JSONObject err = o.optJSONObject("error");
            if (err != null) return err.optString("message", raw);
            return o.optString("message", raw);
        } catch (JSONException e) {
            return raw;
        }
    }

    /* ---------- 余额 ---------- */
    public interface BalanceCb {
        void onResult(boolean ok, String text, NetError error);
    }

    public static void fetchBalance(final Context ctx, final BalanceCb cb) {
        final Prefs prefs = Prefs.get(ctx);
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                String url = prefs.baseUrl().replaceAll("/+$", "") + "/user/balance";
                conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(20000);
                conn.setRequestProperty("Authorization", "Bearer " + prefs.apiKey().trim());
                conn.setRequestProperty("Accept", "application/json");

                int code = conn.getResponseCode();
                if (code < 200 || code >= 300) {
                    String detail = extractMessage(readAll(conn.getErrorStream()));
                    NetError e = NetError.fromHttp(code, detail);
                    MAIN.post(() -> cb.onResult(false, null, e));
                    return;
                }

                final String body = readAll(conn.getInputStream());
                JSONObject o = new JSONObject(body);
                boolean available = o.optBoolean("is_available", false);
                JSONArray infos = o.optJSONArray("balance_infos");
                String text = null;
                if (infos != null && infos.length() > 0) {
                    JSONObject info = infos.optJSONObject(0);
                    for (int i = 0; i < infos.length(); i++) {
                        JSONObject tmp = infos.optJSONObject(i);
                        if (tmp != null && "CNY".equals(tmp.optString("currency"))) { info = tmp; break; }
                    }
                    if (info != null) {
                        String cur = info.optString("currency", "CNY");
                        String symbol = "CNY".equals(cur) ? "¥" : (info.optString("currency") + " ");
                        text = symbol + info.optString("total_balance", "0");
                    }
                }
                final String result = text;
                final boolean av = available;
                MAIN.post(() -> {
                    if (!av) {
                        cb.onResult(false, result, new NetError("no_balance", "余额不足",
                                "账户余额已用完，充值后即可继续使用。", NetError.Action.TOPUP, NetError.Action.OPEN_KEY));
                    } else {
                        cb.onResult(true, result == null ? "未知" : result, null);
                    }
                });
            } catch (Exception e) {
                final NetError ne = NetError.fromException(e);
                MAIN.post(() -> cb.onResult(false, null, ne));
            } finally {
                if (conn != null) conn.disconnect();
            }
        }, "dsm-balance").start();
    }
}
