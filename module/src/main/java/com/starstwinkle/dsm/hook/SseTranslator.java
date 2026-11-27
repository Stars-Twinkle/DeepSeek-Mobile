package com.starstwinkle.dsm.hook;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;

/**
 * 把 DeepSeek 官方 API 的 OpenAI 兼容流，翻译成官方 App 认识的 SSE 格式。
 *
 * 官方格式是 p/o/v 增量（见 docs/protocol.md）：
 *   {"p":"response/fragments","o":"APPEND","v":[{"id":2,"type":"THINK","content":""}]}
 *   {"p":"response/fragments/-1/content","o":"APPEND","v":"文字"}
 *   {"p":"response/status","v":"FINISHED"}
 */
public final class SseTranslator {

    private final String messageId;
    private final boolean thinkingEnabled;
    private int fragSeq = 2;
    private int lastFragId = 0;
    private String currentType = null;
    private long totalTokens = 0;

    public SseTranslator(boolean thinkingEnabled) {
        this.messageId = UUID.randomUUID().toString().replace("-", "");
        this.thinkingEnabled = thinkingEnabled;
    }

    public String messageId() {
        return messageId;
    }

    /** 首帧：消息骨架。必须与官方字段完全一致，否则 App 的 Kotlin 反序列化会失败。 */
    public String meta() {
        try {
            String firstType = thinkingEnabled ? "THINK" : "RESPONSE";
            JSONObject frag = new JSONObject();
            frag.put("id", 2);
            frag.put("type", firstType);
            frag.put("content", "");
            frag.put("elapsed_secs", JSONObject.NULL);
            frag.put("references", new JSONArray());
            frag.put("stage_id", 1);
            lastFragId = 2;
            currentType = firstType;

            JSONArray frags = new JSONArray();
            frags.put(frag);

            JSONObject resp = new JSONObject();
            resp.put("message_id", 2);
            resp.put("parent_id", 1);
            resp.put("model", "");
            resp.put("role", "ASSISTANT");
            resp.put("thinking_enabled", thinkingEnabled);
            resp.put("ban_edit", false);
            resp.put("ban_regenerate", false);
            resp.put("status", "WIP");
            resp.put("incomplete_message", JSONObject.NULL);
            resp.put("accumulated_token_usage", 0);
            resp.put("feedback", JSONObject.NULL);
            resp.put("inserted_at", System.currentTimeMillis() / 1000);
            resp.put("search_enabled", false);
            resp.put("fragments", frags);
            resp.put("conversation_mode", "DEFAULT");
            resp.put("has_pending_fragment", false);
            resp.put("auto_continue", false);
            resp.put("search_triggered", false);
            resp.put("extra_search_providers", new JSONArray());

            JSONObject root = new JSONObject();
            root.put("v", new JSONObject().put("response", resp));
            return line(root);
        } catch (JSONException e) {
            return "";
        }
    }

    /** 开一个新的片段（THINK / RESPONSE），后续 append 都进它 */
    public String openFragment(String type) {
        if (type.equals(currentType)) return "";
        try {
            fragSeq++;
            lastFragId = fragSeq;
            currentType = type;

            JSONObject frag = new JSONObject();
            frag.put("id", fragSeq);
            frag.put("type", type);
            frag.put("content", "");

            JSONArray arr = new JSONArray();
            arr.put(frag);

            JSONObject root = new JSONObject();
            root.put("p", "response/fragments");
            root.put("o", "APPEND");
            root.put("v", arr);
            return line(root);
        } catch (JSONException e) {
            return "";
        }
    }

    /** 往当前片段追加文字 */
    public String append(String text) {
        if (text == null || text.isEmpty()) return "";
        try {
            JSONObject root = new JSONObject();
            root.put("p", "response/fragments/-1/content");
            root.put("o", "APPEND");
            root.put("v", text);
            return line(root);
        } catch (JSONException e) {
            return "";
        }
    }

    /** 更新累计 token 用量 */
    public String usage(long tokens) {
        totalTokens = tokens;
        try {
            JSONObject root = new JSONObject();
            root.put("p", "response/accumulated_token_usage");
            root.put("v", tokens);
            return line(root);
        } catch (JSONException e) {
            return "";
        }
    }

    /** 正常结束 */
    public String finish() {
        StringBuilder sb = new StringBuilder();
        try {
            JSONObject status = new JSONObject();
            status.put("p", "response/status");
            status.put("v", "FINISHED");
            sb.append(line(status));

            if (totalTokens > 0) {
                JSONObject usage = new JSONObject();
                usage.put("p", "response/accumulated_token_usage");
                usage.put("v", totalTokens);
                sb.append(line(usage));
            }
        } catch (JSONException ignored) {
        }
        return sb.toString();
    }

    /** 出错：官方 App 会把 type=error 的 v 当作 toast 错误提示 */
    public String error(String message, String code) {
        try {
            JSONObject v = new JSONObject();
            v.put("type", "error");
            v.put("content", message == null ? "请求失败" : message);
            v.put("finish_reason", code == null ? "error" : code);

            JSONObject root = new JSONObject();
            root.put("v", v);
            return line(root);
        } catch (JSONException e) {
            return "";
        }
    }

    private String line(JSONObject obj) {
        return "data: " + obj.toString() + "\n\n";
    }
}
