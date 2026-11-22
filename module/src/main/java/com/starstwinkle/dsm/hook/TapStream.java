package com.starstwinkle.dsm.hook;

import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 包在 TLS 解密流外面，按行记录明文。
 * 只输出疑似 SSE 的行，避免日志爆炸。
 */
final class TapStream extends FilterInputStream {

    interface Sink {
        void onLine(String line);
    }

    private final Sink sink;
    private final ByteArrayOutputStream carry = new ByteArrayOutputStream();

    TapStream(InputStream in, Sink sink) {
        super(in);
        this.sink = sink;
    }

    @Override
    public int read() throws IOException {
        int b = super.read();
        if (b >= 0) {
            carry.write(b);
            flushLines();
        }
        return b;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int n = super.read(b, off, len);
        if (n > 0) {
            carry.write(b, off, n);
            flushLines();
        }
        return n;
    }

    private void flushLines() {
        byte[] all = carry.toByteArray();
        int start = 0;
        for (int i = 0; i < all.length; i++) {
            if (all[i] == '\n') {
                String line = new String(all, start, i - start, StandardCharsets.UTF_8).trim();
                start = i + 1;
                if (line.isEmpty()) continue;
                if (line.startsWith("data:") || line.startsWith("event:")
                        || line.contains("fragments") || line.contains("response/")
                        || line.contains("chat_session")) {
                    if (line.length() > 2500) line = line.substring(0, 2500) + "…";
                    sink.onLine(line);
                }
            }
        }
        carry.reset();
        if (start < all.length) {
            carry.write(all, start, all.length - start);
            // 防止单行异常长
            if (carry.size() > 1 << 20) carry.reset();
        }
    }
}
