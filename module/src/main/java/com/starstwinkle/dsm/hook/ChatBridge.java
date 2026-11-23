package com.starstwinkle.dsm.hook;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 会话桥：把「官方请求」翻译成「API 请求」，再把「API 流」翻译成「官方 SSE」。
 */
final class ChatBridge {

    static final class Req {
        String sessionId = "";
        String prompt = "";
        boolean thinking;
        long at = System.currentTimeMillis();
    }

    /** socket -> 该连接上发出去的会话请求 */
    private static final Map<Object, Req> PENDING =
            Collections.synchronizedMap(new WeakHashMap<Object, Req>());

    static void onChatRequest(Object socket, String sid, String prompt, boolean thinking) {
        Req r = new Req();
        r.sessionId = sid == null ? "" : sid;
        r.prompt = prompt == null ? "" : prompt;
        r.thinking = thinking;
        PENDING.put(socket, r);
        HookEntry.log("BRIDGE captured prompt=" + r.prompt + " thinking=" + r.thinking);
    }

    /** 为某个连接构造完整的官方 SSE 响应；失败返回 null（走原样） */
    static byte[] build(Object socket) {
        Req req = PENDING.remove(socket);
        if (req == null) {
            HookEntry.log("BRIDGE no request bound to this socket");
            return null;
        }
        String base = Config.baseUrl();
        String key = Config.apiKey();
        HookEntry.log("BRIDGE calling " + base + " key=" + (key.isEmpty() ? "<empty>" : key.substring(0, Math.min(6, key.length())) + "…"));

        HttpURLConnection conn = null;
        try {
            SseTranslator tr = new SseTranslator(req.thinking);
            StringBuilder out = new StringBuilder(4096);

            String url = base.replaceAll("/+$", "") + "/chat/completions";
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(120000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setRequestProperty("Authorization", "Bearer " + key);
            conn.setRequestProperty("Accept", "text/event-stream");

            JSONArray msgs = new JSONArray();
            JSONObject sys = new JSONObject();
            sys.put("role", "system");
            sys.put("content", Config.systemPrompt(req.thinking));
            msgs.put(sys);
            JSONObject user = new JSONObject();
            user.put("role", "user");
            user.put("content", req.prompt);
            msgs.put(user);

            JSONObject body = new JSONObject();
            body.put("model", Config.model());
            body.put("messages", msgs);
            body.put("stream", true);
            body.put("max_tokens", 8192);
            JSONObject thinking = new JSONObject();
            thinking.put("type", req.thinking ? "enabled" : "disabled");
            body.put("thinking", thinking);
            if (req.thinking) body.put("reasoning_effort", "high");
            else body.put("temperature", 1.3);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.toString().getBytes(StandardCharsets.UTF_8));
                os.flush();
            }

            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                String detail = readAll(conn.getErrorStream());
                HookEntry.log("BRIDGE http " + code + " " + trim(detail));
                out.append(tr.meta());
                out.append(tr.error("API 返回 " + code + "：" + trim(detail), "http_" + code));
                return out.toString().getBytes(StandardCharsets.UTF_8);
            }

            out.append(tr.meta());
            out.append(tr.openFragment(req.thinking ? "THINK" : "RESPONSE"));

            InputStream is = conn.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8), 4096);
            String line;
            boolean inResponse = false;
            long tokens = 0;

            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.startsWith("data:")) continue;
                String data = line.substring(5).trim();
                if ("[DONE]".equals(data)) break;
                JSONObject json;
                try {
                    json = new JSONObject(data);
                } catch (Exception e) {
                    continue;
                }
                if (json.has("usage")) {
                    JSONObject u = json.optJSONObject("usage");
                    if (u != null) tokens = u.optLong("total_tokens", 0);
                }
                JSONArray choices = json.optJSONArray("choices");
                if (choices == null || choices.length() == 0) continue;
                JSONObject delta = choices.getJSONObject(0).optJSONObject("delta");
                if (delta == null) continue;

                String think = delta.optString("reasoning_content", "");
                String content = delta.optString("content", "");
                if (!think.isEmpty()) out.append(tr.append(think));
                if (!content.isEmpty()) {
                    if (!inResponse) {
                        inResponse = true;
                        out.append(tr.openFragment("RESPONSE"));
                    }
                    out.append(tr.append(content));
                }
            }

            if (tokens > 0) out.append(tr.usage(tokens));
            out.append(tr.finish());
            HookEntry.log("BRIDGE done, bytes=" + out.length());
            return out.toString().getBytes(StandardCharsets.UTF_8);

        } catch (Exception e) {
            HookEntry.log("BRIDGE error " + e);
            try {
                SseTranslator tr = new SseTranslator(req.thinking);
                return (tr.meta() + tr.error("调用 API 失败：" + e, "bridge_error"))
                        .getBytes(StandardCharsets.UTF_8);
            } catch (Exception ignored) {
                return null;
            }
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static String readAll(InputStream is) {
        if (is == null) return "";
        try (BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String l;
            while ((l = r.readLine()) != null) {
                sb.append(l);
                if (sb.length() > 600) break;
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private static String trim(String s) {
        if (s == null) return "";
        return s.length() > 200 ? s.substring(0, 200) + "…" : s;
    }
}
