package com.starstwinkle.dsm.hook;

import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * 会话响应劫持。
 *
 * 两条路径：
 *   A. HTTP/1.1（官方会话实际走这条）：放行响应头，body 换成我们构造的 chunked 流
 *   B. HTTP/2：放行 HEADERS 帧，在第一个 DATA 帧处替换为构造的 DATA 帧
 *
 * 官方 UI 无感知。
 */
final class InjectStream extends FilterInputStream {

    interface Builder {
        byte[] build();
    }

    private static final int MAX_FRAME = 16000;
    private static final byte[] CRLFCRLF = {'\r', '\n', '\r', '\n'};

    private final Builder builder;

    private final ByteArrayOutputStream raw = new ByteArrayOutputStream();
    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private int outPos;

    private int mode;           // 0 未知, 1 h1, 2 h2, 3 加密透传
    private boolean h1HeaderDone;
    private boolean hijacked;
    private boolean injectedAll;
    private int streamId = -1;
    private boolean logged;

    InjectStream(InputStream in, Object socket, Builder builder) {
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
        while (true) {
            if (outPos < out.size()) return drain(b, off, len);
            if (injectedAll) return -1;

            if (mode == 3) {                      // 加密流，原样透传
                return super.read(b, off, len);
            }

            if (mode == 1 && h1HeaderDone) {      // h1：头已放行，开始注入
                if (!hijacked) {
                    hijacked = true;
                    byte[] payload = builder.build();
                    if (payload == null || payload.length == 0) {
                        mode = 3;                 // 放弃
                    } else {
                        HookEntry.log("H1 inject bytes=" + payload.length);
                        writeChunked(payload);
                    }
                }
                if (mode == 1) continue;
                return super.read(b, off, len);
            }

            if (mode == 2 && hijacked) {
                if (!injectedAll) {
                    // 等待或补充
                }
            }

            if (processBuffered()) continue;

            int n = super.read(b, off, len);
            if (n <= 0) {
                if (mode == 1 && h1HeaderDone && hijacked && !injectedAll) {
                    out.write("0\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
                    injectedAll = true;
                    continue;
                }
                if (mode == 2 && hijacked && !injectedAll) {
                    out.write(frame(new byte[0], 0x00, 0x01));
                    injectedAll = true;
                    continue;
                }
                return n;
            }

            if (mode == 0) detect(b, off, n);
            if (mode == 3) return n;
            if (mode == 1) {
                raw.write(b, off, n);
                if (!h1HeaderDone) {
                    byte[] r = raw.toByteArray();
                    int idx = indexOf(r, CRLFCRLF);
                    if (idx < 0) return n;       // 头还没收完，先给调用方
                    h1HeaderDone = true;
                    int headEnd = idx + 4;
                    byte[] head = Arrays.copyOfRange(r, 0, headEnd);
                    out.write(head);
                    raw.reset();
                    if (!logged) {
                        logged = true;
                        HookEntry.log("H1 header len=" + headEnd);
                    }
                    continue;
                }
            } else {
                raw.write(b, off, n);
                if (!logged) {
                    logged = true;
                    StringBuilder hx = new StringBuilder();
                    for (int k = 0; k < n && k < 16; k++) hx.append(String.format("%02x ", b[off + k]));
                    HookEntry.log("H2 stream first n=" + n + " hex=" + hx);
                }
            }
        }
    }

    private void detect(byte[] b, int off, int n) {
        if (n >= 5 && b[off] == 'H' && b[off + 1] == 'T' && b[off + 2] == 'T'
                && b[off + 3] == 'P' && b[off + 4] == '/') {
            mode = 1;
            HookEntry.log("stream is HTTP/1.1");
        } else if (n >= 3 && (b[off] & 0xff) == 0x16 && (b[off + 1] & 0xff) == 0x03) {
            mode = 3;
            HookEntry.log("stream encrypted, passthrough");
        } else {
            mode = 2;
            HookEntry.log("stream is HTTP/2");
        }
    }

    /** 处理 raw 中的内容：h2 帧解析 */
    private boolean processBuffered() throws IOException {
        if (mode != 2) return false;
        byte[] r = raw.toByteArray();
        int consumed = 0;
        while (r.length - consumed >= 9) {
            int len = ((r[consumed] & 0xff) << 16) | ((r[consumed + 1] & 0xff) << 8) | (r[consumed + 2] & 0xff);
            int type = r[consumed + 3] & 0xff;
            int sid = ((r[consumed + 5] & 0x7f) << 24) | ((r[consumed + 6] & 0xff) << 16)
                    | ((r[consumed + 7] & 0xff) << 8) | (r[consumed + 8] & 0xff);
            if (r.length - consumed < 9 + len) break;
            byte[] full = Arrays.copyOfRange(r, consumed, consumed + 9 + len);
            consumed += 9 + len;

            if (hijacked) {
                if (type == 0x06) out.write(full);
                continue;
            }
            if (type == 0x01) {
                streamId = sid;
                HookEntry.log("H2 HEADERS sid=" + sid + " len=" + len + " flags=" + r[consumed - 9 - len + 4]);
                out.write(full);
            } else if (type == 0x00) {
                // 只劫持 SSE 流（含 "data:" 的帧）；官方 JSON 接口的响应必须原样放行，
                // 否则会破坏登录/PoW/会话创建，表现为 INVALID_POW。
                if (!looksSse(full)) {
                    out.write(full);
                    continue;
                }
                HookEntry.log("H2 SSE detected sid=" + sid + " len=" + len);
                byte[] payload = builder.build();
                if (payload == null || payload.length == 0) {
                    out.write(full);
                } else {
                    hijacked = true;
                    HookEntry.log("H2 inject sid=" + sid + " bytes=" + payload.length);
                    writeFrames(payload);
                }
            } else {
                out.write(full);
            }
        }
        if (consumed > 0) {
            raw.reset();
            raw.write(r, consumed, r.length - consumed);
            return true;
        }
        return false;
    }

    private void writeChunked(byte[] payload) throws IOException {
        int i = 0;
        while (i < payload.length) {
            int n = Math.min(8000, payload.length - i);
            out.write((Integer.toHexString(n) + "\r\n").getBytes(StandardCharsets.US_ASCII));
            out.write(payload, i, n);
            out.write("\r\n".getBytes(StandardCharsets.US_ASCII));
            i += n;
        }
        out.write("0\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
        injectedAll = true;
    }

    private void writeFrames(byte[] payload) throws IOException {
        int i = 0;
        while (i < payload.length) {
            int n = Math.min(MAX_FRAME, payload.length - i);
            boolean last = (i + n >= payload.length);
            out.write(frame(Arrays.copyOfRange(payload, i, i + n), 0x00, last ? 0x01 : 0x00));
            i += n;
        }
        if (payload.length == 0) out.write(frame(new byte[0], 0x00, 0x01));
        injectedAll = true;
    }

    private byte[] frame(byte[] payload, int type, int flags) {
        int len = payload.length;
        byte[] f = new byte[9 + len];
        f[0] = (byte) ((len >> 16) & 0xff);
        f[1] = (byte) ((len >> 8) & 0xff);
        f[2] = (byte) (len & 0xff);
        f[3] = (byte) type;
        f[4] = (byte) flags;
        f[5] = (byte) ((streamId >> 24) & 0x7f);
        f[6] = (byte) ((streamId >> 16) & 0xff);
        f[7] = (byte) ((streamId >> 8) & 0xff);
        f[8] = (byte) (streamId & 0xff);
        System.arraycopy(payload, 0, f, 9, len);
        return f;
    }

    /** 帧 payload 是否是 SSE 文本 */
    private boolean looksSse(byte[] frame) {
        for (int k = 9; k + 5 <= frame.length; k++) {
            if (frame[k] == 'd' && frame[k + 1] == 'a' && frame[k + 2] == 't'
                    && frame[k + 3] == 'a' && frame[k + 4] == ':') return true;
        }
        // 兼容不带 data: 前缀的 stream
        String s = new String(frame, 9, Math.max(0, Math.min(frame.length - 9, 200)), StandardCharsets.UTF_8);
        return s.contains("\"v\":{\"response\"") || s.contains("\"response\":{\"message_id\"");
    }

    private int drain(byte[] b, int off, int len) {
        byte[] o = out.toByteArray();
        int n = Math.min(len, o.length - outPos);
        System.arraycopy(o, outPos, b, off, n);
        outPos += n;
        if (outPos >= o.length) {
            out.reset();
            outPos = 0;
        }
        return n;
    }

    private static int indexOf(byte[] data, byte[] pat) {
        outer:
        for (int i = 0; i <= data.length - pat.length; i++) {
            for (int j = 0; j < pat.length; j++) {
                if (data[i + j] != pat[j]) continue outer;
            }
            return i;
        }
        return -1;
    }
}
