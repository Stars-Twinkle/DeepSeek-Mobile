package com.starstwinkle.dsm.core;

/** 统一的错误对象：所有错误都翻译成中文标题 + 说明 + 可执行动作。 */
public final class NetError {

    /** 错误后可以做什么，界面据此显示按钮 */
    public enum Action {
        NONE,          // 无
        OPEN_KEY,      // 去填写密钥
        TOPUP,         // 去充值
        RETRY,         // 重试
        NATIVE         // 用官方 App 继续
    }

    public final String code;
    public final String title;
    public final String desc;
    public final Action action;
    public final Action action2;

    public NetError(String code, String title, String desc) {
        this(code, title, desc, Action.RETRY, Action.NONE);
    }

    public NetError(String code, String title, String desc, Action a, Action b) {
        this.code = code;
        this.title = title;
        this.desc = desc;
        this.action = a;
        this.action2 = b;
    }

    // ---------- HTTP 状态码 -> 中文 ----------
    public static NetError fromHttp(int status, String detail) {
        String suffix = (detail == null || detail.isEmpty()) ? "" : "（服务端说明：" + trim(detail) + "）";
        switch (status) {
            case 400:
                return new NetError("bad_request", "请求格式有误",
                        "这次的消息没能正确发给服务器" + suffix + "。换一种说法，或新建对话重发。",
                        Action.RETRY, Action.NONE);
            case 401:
                return new NetError("unauthorized", "API 密钥无效",
                        "服务器不认这个密钥：可能复制不完整、已被删除，或填错了。" + suffix,
                        Action.OPEN_KEY, Action.NONE);
            case 402:
                return new NetError("no_balance", "余额不足",
                        "账户余额已经用完了，充值后即可继续。" + suffix,
                        Action.TOPUP, Action.OPEN_KEY);
            case 403:
                return new NetError("forbidden", "没有访问权限",
                        "这个密钥或账号被禁止访问该模型。" + suffix, Action.OPEN_KEY, Action.NONE);
            case 404:
                return new NetError("not_found", "接口地址不对",
                        "当前 API 地址找不到这个接口，请检查设置里的 API 地址，或恢复默认。" + suffix,
                        Action.OPEN_KEY, Action.NONE);
            case 408:
                return new NetError("timeout", "请求超时",
                        "服务器等太久没收到完整请求。换个网络或稍后再试。" + suffix,
                        Action.RETRY, Action.NONE);
            case 413:
                return new NetError("too_large", "内容太长了",
                        "这次发送的内容超出接口限制，删减一些后再试，或新建对话。" + suffix,
                        Action.RETRY, Action.NONE);
            case 422:
                return new NetError("bad_params", "参数不被接受",
                        "请求里的某个参数不合法（多为模型名或参数组合问题）" + suffix + "。",
                        Action.OPEN_KEY, Action.RETRY);
            case 429:
                return new NetError("rate_limit", "请求太频繁",
                        "短时间内发得太快，触发了限速。等几秒再发就好。" + suffix,
                        Action.RETRY, Action.NONE);
            case 500:
                return new NetError("server", "服务器出错了",
                        "这是 DeepSeek 服务端的问题，不是你操作错了。稍等一会儿再试。" + suffix,
                        Action.RETRY, Action.NONE);
            case 502:
            case 503:
                return new NetError("overloaded", "服务器繁忙",
                        "当前请求量太大，服务端暂时接不过来。稍后重试通常就好了。" + suffix,
                        Action.RETRY, Action.NONE);
            case 504:
                return new NetError("gateway_timeout", "服务端响应超时",
                        "网关等待超时，稍后重试；如果一直这样，可能是服务波动。" + suffix,
                        Action.RETRY, Action.NONE);
            default:
                return new NetError("http_" + status, "请求失败（" + status + "）",
                        (detail != null && !detail.isEmpty() ? "服务端说明：" + trim(detail) : "服务器返回了未预期的状态码。")
                                + " 可以重试一次，或到设置里检查密钥与地址。",
                        Action.RETRY, Action.OPEN_KEY);
        }
    }

    // ---------- 网络异常 -> 中文 ----------
    public static NetError fromException(Exception e) {
        if (e == null) return new NetError("unknown", "出了点问题", "请求没能完成，可以重试一次。");
        String name = e.getClass().getSimpleName();
        String msg = e.getMessage() == null ? "" : e.getMessage();
        if (name.contains("Timeout") || msg.contains("timed out") || msg.contains("Timeout")) {
            return new NetError("timeout", "连接超时",
                    "等了很久没有收到服务器响应。检查网络，或者稍后重试。", Action.RETRY, Action.NONE);
        }
        if (e instanceof java.net.UnknownHostException) {
            return new NetError("dns", "找不到服务器",
                    "域名解析失败。检查网络是否正常，或设置里的 API 地址是否写错。",
                    Action.RETRY, Action.OPEN_KEY);
        }
        if (e instanceof java.net.ConnectException || e instanceof java.net.SocketException
                || e instanceof javax.net.ssl.SSLException || msg.contains("Connection")) {
            return new NetError("network", "网络连接失败",
                    "没能连上服务器。请检查：手机是否联网、代理或 VPN 是否不稳定、系统时间是否准确。",
                    Action.RETRY, Action.NONE);
        }
        if (msg.contains("Cleartext") || msg.contains("cleartext")) {
            return new NetError("cleartext", "这个地址不能用明文 HTTP",
                    "系统不允许以 http:// 连接。把 API 地址改成 https://，或换用支持 HTTPS 的代理地址。",
                    Action.OPEN_KEY, Action.NONE);
        }
        return new NetError("unknown", "出了点问题",
                "请求没能完成" + (msg.isEmpty() ? "" : "：" + trim(msg)) + "。可以重试一次。",
                Action.RETRY, Action.NONE);
    }

    private static String trim(String s) {
        if (s == null) return "";
        s = s.replace('\n', ' ').trim();
        return s.length() > 160 ? s.substring(0, 160) + "…" : s;
    }
}
