package com.neuropattern.app;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity {

    private WebView webView;

    @Override
protected void onCreate(Bundle savedInstanceState) {
    setTheme(R.style.SplashTheme);
    super.onCreate(savedInstanceState);

    webView = new WebView(this);

    WebSettings settings = webView.getSettings();
    settings.setJavaScriptEnabled(true);
    settings.setDomStorageEnabled(true);

    webView.setWebViewClient(new WebViewClient());

    

    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
    setTheme(R.style.AppTheme);
    setContentView(webView);
    webView.loadUrl("file:///android_asset/index.html");
}, 1500);
}

@Override
public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
