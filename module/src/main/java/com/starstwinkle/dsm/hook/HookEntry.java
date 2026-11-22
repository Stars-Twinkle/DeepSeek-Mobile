package com.starstwinkle.dsm.hook;

import android.content.Context;
import android.os.Environment;

import java.io.File;
import java.io.FileWriter;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * 模块入口。
 *
 * 目标：在不改官方 APK 的前提下，把官方 App 的会话请求接到 api.deepseek.com。
 * 阶段一（本版本）：只旁路记录会话请求，用来还原官方协议，不改变行为。
 */
public class HookEntry implements IXposedHookLoadPackage {

    private static final String TAG = "DSM-HOOK";
    private static final String TARGET = "com.deepseek.chat";
    private static final String PKG = "com.starstwinkle.dsm.hook";

    private static XSharedPreferences sp;
    private static File logFile;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) {
        if (!TARGET.equals(lp.packageName)) return;
        log("module loaded into " + lp.packageName);

        try {
            sp = new XSharedPreferences(PKG, "dsm_hook");
            sp.makeWorldReadable();
        } catch (Throwable t) {
            log("prefs unavailable: " + t);
        }

        hookOkHttp(lp.classLoader);
        hookClassLoader(lp.classLoader);
    }

    /** 探测：记录被加载的网络相关类，用来还原混淆后的真实类名 */
    private void hookClassLoader(final ClassLoader appCl) {
        try {
            XposedHelpers.findAndHookMethod(ClassLoader.class, "loadClass", String.class, boolean.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            String name = (String) param.args[0];
                            if (name == null) return;
                            String low = name.toLowerCase(Locale.US);
                            if (low.contains("okhttp") || low.contains("ktor")
                                    || low.contains("kotlinx.coroutines.io")
                                    || low.contains("deepseek")) {
                                log("LOAD " + name);
                            }
                        }
                    });
            log("classloader probe installed");
        } catch (Throwable t) {
            log("probe failed: " + t);
        }

        // 常见候选直接探测
        String[] probes = {
                "io.ktor.client.engine.okhttp.OkHttpEngine",
                "io.ktor.client.engine.okhttp.OkHttpEngineContainer",
                "io.ktor.client.HttpClient",
                "io.ktor.client.engine.HttpClientEngine",
                "io.ktor.client.request.HttpRequestBuilder",
                "okhttp3.OkHttpClient",
                "okhttp3.Request"
        };
        for (String p : probes) {
            Class<?> c = XposedHelpers.findClassIfExists(p, appCl);
            log("PROBE " + p + " -> " + (c == null ? "null" : c.getName()));
        }
    }

    /* ---------- 抓包：OkHttp 拦截链 ---------- */
    private void hookOkHttp(ClassLoader cl) {
        final Class<?> requestCls = XposedHelpers.findClassIfExists("okhttp3.Request", cl);
        if (requestCls == null) {
            log("okhttp3.Request not found");
            return;
        }
        final Class<?> chainCls = XposedHelpers.findClassIfExists(
                "okhttp3.internal.http.RealInterceptorChain", cl);
        if (chainCls == null) {
            log("RealInterceptorChain not found");
            return;
        }

        XC_MethodHook proceedHook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                try {
                    Object req = param.args[0];
                    if (req == null) return;
                    String url = String.valueOf(XposedHelpers.callMethod(req, "url"));
                    String method = String.valueOf(XposedHelpers.callMethod(req, "method"));
                    Object headers = XposedHelpers.callMethod(req, "headers");
                    String body = readBody(req);

                    if (!url.contains("deepseek")) return;
                    log("REQ " + method + " " + url);
                    log("HEADERS " + headers);
                    if (!body.isEmpty()) log("BODY " + body);
                } catch (Throwable t) {
                    log("before err: " + t);
                }
            }

            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                try {
                    Object resp = param.getResult();
                    if (resp == null) return;
                    Object code = XposedHelpers.callMethod(resp, "code");
                    Object req = XposedHelpers.callMethod(resp, "request");
                    String url = String.valueOf(XposedHelpers.callMethod(req, "url"));
                    if (!url.contains("deepseek")) return;
                    log("RESP " + code + " " + url);
                    Object headers = XposedHelpers.callMethod(resp, "headers");
                    log("RESP_HEADERS " + headers);
                } catch (Throwable t) {
                    // 忽略
                }
            }
        };

        try {
            XposedHelpers.findAndHookMethod(chainCls, "proceed", requestCls, proceedHook);
            log("hooked proceed(Request)");
        } catch (Throwable t) {
            log("hook proceed failed: " + t);
        }
    }

    /** 反射读请求体（okio.Buffer 拷贝一份，不影响原请求） */
    private String readBody(Object req) {
        try {
            Object body = XposedHelpers.callMethod(req, "body");
            if (body == null) return "";
            Class<?> bufferCls = XposedHelpers.findClassIfExists("okio.Buffer", req.getClass().getClassLoader());
            if (bufferCls == null) return "";
            Object buffer = bufferCls.getConstructor().newInstance();
            Method writeTo = null;
            for (Method m : body.getClass().getMethods()) {
                if ("writeTo".equals(m.getName()) && m.getParameterTypes().length == 1
                        && m.getParameterTypes()[0].isAssignableFrom(bufferCls)) {
                    writeTo = m;
                    break;
                }
            }
            if (writeTo == null) return "";
            writeTo.invoke(body, buffer);
            Method readUtf8 = bufferCls.getMethod("readUtf8");
            String s = String.valueOf(readUtf8.invoke(buffer));
            return s.length() > 4000 ? s.substring(0, 4000) + "…" : s;
        } catch (Throwable t) {
            return "";
        }
    }

    /* ---------- 日志 ---------- */
    static void log(String msg) {
        String line = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(new Date())
                + "  " + msg;
        XposedBridge.log(TAG + ": " + line);
    }

    static void logToFile(String msg) {
        try {
            if (logFile == null) {
                File dir = Environment.getExternalStorageDirectory();
                logFile = new File(dir, "dsm_hook.log");
            }
            FileWriter fw = new FileWriter(logFile, true);
            fw.write(msg);
            fw.write('\n');
            fw.close();
        } catch (Throwable ignored) {
        }
    }
}
