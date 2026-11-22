package com.starstwinkle.dsm;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebView;

/** 内置浏览器：引导用户完成充值 / 创建密钥，不跳出应用。 */
public class BrowserActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WebView web = new WebView(this);
        String url = getIntent().getStringExtra("url");
        web.loadUrl(url == null ? "https://platform.deepseek.com/" : url);
        setContentView(web);
    }
}
