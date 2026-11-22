package com.starstwinkle.dsm.core;

import android.content.Context;
import android.content.res.Configuration;

/**
 * 主题色板。官方 App 的配色取自实测截图与资源表：
 * 品牌蓝 #4D6BFE，深色底 #131417 / 卡片 #202127，浅色底 #F4F5F8。
 */
public final class Theme {

    public final boolean dark;

    public int bg;
    public int bgSoft;
    public int card;
    public int card2;
    public int line;
    public int lineStrong;
    public int text;
    public int text2;
    public int text3;
    public int brand;
    public int brandWeak;
    public int danger;
    public int ok;
    public int warn;
    public int bubbleUser;
    public int bubbleUserText;
    public int codeBg;
    public int scrim;

    private Theme(boolean dark) {
        this.dark = dark;
        if (dark) {
            bg = 0xFF131417;
            bgSoft = 0xFF191A1F;
            card = 0xFF202127;
            card2 = 0xFF26272E;
            line = 0x14FFFFFF;
            lineStrong = 0x22FFFFFF;
            text = 0xFFECECF1;
            text2 = 0xFFA5A7B4;
            text3 = 0xFF71737F;
            brand = 0xFF4D6BFE;
            brandWeak = 0x294D6BFE;
            danger = 0xFFFF6B6B;
            ok = 0xFF46D39A;
            warn = 0xFFF5B23D;
            bubbleUser = 0xFF3B4FC4;
            bubbleUserText = 0xFFF4F6FF;
            codeBg = 0xFF17181D;
            scrim = 0x99000000;
        } else {
            bg = 0xFFF4F5F8;
            bgSoft = 0xFFFFFFFF;
            card = 0xFFFFFFFF;
            card2 = 0xFFF6F7FA;
            line = 0x12101426;
            lineStrong = 0x1F101426;
            text = 0xFF14161F;
            text2 = 0xFF5F6473;
            text3 = 0xFF8D93A3;
            brand = 0xFF4D6BFE;
            brandWeak = 0x1A4D6BFE;
            danger = 0xFFD93B3B;
            ok = 0xFF0F9D63;
            warn = 0xFFB57A10;
            bubbleUser = 0xFF4D6BFE;
            bubbleUserText = 0xFFFFFFFF;
            codeBg = 0xFFF2F3F7;
            scrim = 0x66000000;
        }
    }

    public static Theme dark() { return new Theme(true); }
    public static Theme light() { return new Theme(false); }

    /** 按用户设置构造主题：dark / light / system */
    public static Theme resolve(Context ctx) {
        String pref = Prefs.get(ctx).theme();
        if ("light".equals(pref)) return light();
        if ("dark".equals(pref)) return dark();
        boolean night = (ctx.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        return night ? dark() : light();
    }
}
