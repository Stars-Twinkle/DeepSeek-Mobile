package com.starstwinkle.dsm.hook;

import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 会话响应劫持流。
 *
 * 工作方式：
 *   1. 先正常读，累积字节，等官方 SSE 的首帧（含 "v":{"response"）出现
 *   2. 一旦确认是会话流，丢弃官方数据，改为返回我们自己构造的 SSE
 *      （内容 = 调用 api.deepseek.com 后按官方格式转码）
 *   3. 官方 UI 全程无感知
 */
final class InjectStream extends FilterInputStream {

    interface Builder {
        /** 同步生成官方格式的完整 SSE 字节；失败返回 null 走原样 */
        byte[] build();
    }

    private final Builder builder;
    private final ByteArrayOutputStream carry = new ByteArrayOutputStream();
    private byte[] injected;
    private int pos;
    private boolean hijacked;
    private boolean gaveUp;

    InjectStream(InputStream in, Builder builder) {
        super(in);
        this.builder = builder;
    }

    @Override
    public int read() throws IOException {
        byte[] one = new byte[1];
        int n = read(one, 0, 1);
        return n <= 0 ? -1 : (one[0] & 0xff);
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (hijacked) {
            if (injected == null) {
                injected = builder.build();
                if (injected == null || injected.length == 0) {
                    hijacked = false;
                    gaveUp = true;
                }
            }
            if (hijacked) {
                if (pos >= injected.length) return -1;
                int n = Math.min(len, injected.length - pos);
                System.arraycopy(injected, pos, b, off, n);
                pos += n;
                return n;
            }
        }

        int n = super.read(b, off, len);
        if (n <= 0) return n;

        if (!gaveUp && !hijacked) {
            carry.write(b, off, n);
            String s = new String(carry.toByteArray(), StandardCharsets.UTF_8);
            if (s.contains("\"v\":{\"response\"") || s.contains("\"response\":{\"message_id\"")) {
                hijacked = true;
                carry.reset();
                return read(b, off, len);
            }
            // 只保留尾部，避免无限增长
            byte[] all = carry.toByteArray();
            if (all.length > 4096) {
                carry.reset();
                carry.write(all, all.length - 2048, 2048);
            }
        }
        return n;
    }
}
