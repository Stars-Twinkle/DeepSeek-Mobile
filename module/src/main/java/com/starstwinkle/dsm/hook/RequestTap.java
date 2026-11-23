package com.starstwinkle.dsm.hook;

import java.io.ByteArrayOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * 包在 TLS 输出流外面，直接从字节流里定位会话请求的 JSON 体。
 *
 * 注意：官方 App 走 HTTP/2，请求头是 HPACK 压缩的二进制，不能按 HTTP/1.1 报文解析；
 * 但 DATA 帧里是原始 JSON 字节，用 ISO-8859-1 保持字节一一对应，再定位 "prompt" 字段即可。
 */
final class RequestTap extends FilterOutputStream {

    interface Sink {
        void onChatRequest(String sessionId, String prompt, boolean thinking, String raw);
    }

    private final Sink sink;
    private final ByteArrayOutputStream buf = new ByteArrayOutputStream();
    private volatile boolean done;

    RequestTap(OutputStream out, Sink sink) {
        super(out);
        this.sink = sink;
    }

    @Override
    public void write(int b) throws IOException {
        out.write(b);
        if (!done) {
            if (buf.size() < 262144) buf.write(b);
            tryParse();
        }
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        out.write(b, off, len);
        if (!done) {
            if (buf.size() < 262144) buf.write(b, off, len);
            tryParse();
        }
    }

    private void tryParse() {
        String s = new String(buf.toByteArray(), StandardCharsets.ISO_8859_1);
        int p = s.indexOf("\"prompt\"");
        if (p < 0) {
            // 不是会话请求，缓冲到一定程度就放弃
            if (buf.size() > 200000) done = true;
            return;
        }
        int start = s.lastIndexOf('{', p);
        if (start < 0) return;

        int depth = 0;
        int end = -1;
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) { end = i + 1; break; }
            }
        }
        if (end < 0) return; // JSON 还没收完

        done = true;
        String raw = new String(
                s.substring(start, end).getBytes(StandardCharsets.ISO_8859_1),
                StandardCharsets.UTF_8);

        String sid = jsonString(raw, "chat_session_id");
        String prompt = jsonString(raw, "prompt");
        boolean thinking = raw.contains("\"thinking_enabled\":true");
        sink.onChatRequest(sid, prompt, thinking, raw.length() > 2000 ? raw.substring(0, 2000) : raw);
    }

    private String jsonString(String json, String key) {
        String k = "\"" + key + "\"";
        int i = json.indexOf(k);
        if (i < 0) return "";
        int c = json.indexOf(':', i + k.length());
        if (c < 0) return "";
        int q = json.indexOf('"', c);
        if (q < 0) return "";
        StringBuilder sb = new StringBuilder();
        for (int j = q + 1; j < json.length(); j++) {
            char ch = json.charAt(j);
            if (ch == '\\' && j + 1 < json.length()) {
                char n = json.charAt(++j);
                switch (n) {
                    case 'n': sb.append('\n'); break;
                    case 't': sb.append('\t'); break;
                    case 'r': sb.append('\r'); break;
                    case '"': sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    case 'u':
                        try {
                            sb.append((char) Integer.parseInt(json.substring(j + 1, j + 5), 16));
                            j += 4;
                        } catch (Exception ignored) {}
                        break;
                    default: sb.append(n);
                }
            } else if (ch == '"') {
                break;
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }
}
