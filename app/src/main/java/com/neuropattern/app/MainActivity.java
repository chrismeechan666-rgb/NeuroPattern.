package com.neuropattern.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import org.json.JSONObject;

public class MainActivity extends Activity {
    private static final String APP_URL = "file:///android_asset/index.html";
    private static final int SAVE_DOCUMENT = 40, OPEN_DOCUMENT = 41;
    private WebView webView;
    private ExportDocument pendingExport;
    private ValueCallback<Uri[]> importCallback;
    private boolean destroyed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.SplashTheme);
        super.onCreate(savedInstanceState);
        Object retained = getLastNonConfigurationInstance();
        if (retained instanceof ExportDocument) pendingExport = (ExportDocument) retained;
        webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        webView.addJavascriptInterface(new ExportBridge(), "NeuroPatternAndroid");
        // Expose the bridge only to the packaged application, never an external page.
        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return !isAppUrl(url);
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return !isAppUrl(request.getUrl().toString());
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                                       FileChooserParams params) {
                if (importCallback != null) importCallback.onReceiveValue(null);
                importCallback = callback;
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("application/json");
                try { startActivityForResult(intent, OPEN_DOCUMENT); }
                catch (RuntimeException error) {
                    importCallback.onReceiveValue(null); importCallback = null;
                    android.widget.Toast.makeText(MainActivity.this,
                            "Unable to open the backup picker.", android.widget.Toast.LENGTH_LONG).show();
                }
                return true;
            }
        });
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            if (destroyed) return;
            setTheme(R.style.AppTheme);
            setContentView(webView);
            webView.loadUrl(APP_URL);
        }, 250);
    }

    private static boolean isAppUrl(String url) {
        return url != null && (APP_URL.equals(url) || url.startsWith(APP_URL + "#"));
    }

    public final class ExportBridge {
        @JavascriptInterface
        public void exportFile(String id, String name, String mime, String text) {
            runOnUiThread(() -> {
                if (destroyed || !isAppUrl(webView.getUrl())) return;
                if (pendingExport != null) {
                    exportResult(id, "error", "Another export is already open."); return;
                }
                try {
                    pendingExport = new ExportDocument(id, name, mime, text);
                    Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType(pendingExport.mime);
                    intent.putExtra(Intent.EXTRA_TITLE, pendingExport.name);
                    startActivityForResult(intent, SAVE_DOCUMENT);
                } catch (RuntimeException error) {
                    pendingExport = null;
                    exportResult(id, "error", "Unable to open the save picker: " + error.getMessage());
                }
            });
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == OPEN_DOCUMENT) {
            ValueCallback<Uri[]> callback = importCallback; importCallback = null;
            if (callback != null) callback.onReceiveValue(resultCode == RESULT_OK && data != null
                    && data.getData() != null ? new Uri[]{data.getData()} : null);
            return;
        }
        if (requestCode != SAVE_DOCUMENT || pendingExport == null) return;
        final ExportDocument document = pendingExport;
        if (resultCode != RESULT_OK) {
            pendingExport = null; exportResult(document.id, "cancelled", ""); return;
        }
        final Uri uri = data == null ? null : data.getData();
        if (uri == null || !"content".equals(uri.getScheme())) {
            pendingExport = null;
            exportResult(document.id, "error", "No writable document was selected."); return;
        }
        // Providers can be slow: write away from the UI thread.
        new Thread(() -> {
            String status = "saved", message = "";
            try { document.write(getContentResolver().openOutputStream(uri, "wt")); }
            catch (Exception error) {
                status = "error"; message = "Unable to write the file. Check the location and try again.";
                // Never delete a document: it may have existed before this export.
            }
            final String result = status, explanation = message;
            runOnUiThread(() -> {
                pendingExport = null;
                exportResult(document.id, result, explanation);
            });
        }, "NeuroPattern-export").start();
    }

    private void exportResult(String id, String status, String message) {
        if (destroyed) return;
        webView.evaluateJavascript("if(window.neuropatternExportResult)window.neuropatternExportResult("
                + JSONObject.quote(id) + "," + JSONObject.quote(status) + ","
                + JSONObject.quote(message) + ");", null);
    }

    @Override public Object onRetainNonConfigurationInstance() { return pendingExport; }
    @Override protected void onDestroy() {
        destroyed = true;
        if (importCallback != null) { importCallback.onReceiveValue(null); importCallback = null; }
        webView.removeJavascriptInterface("NeuroPatternAndroid"); webView.destroy();
        super.onDestroy();
    }
    @Override public void onBackPressed() {
        if (webView.canGoBack()) webView.goBack(); else super.onBackPressed();
    }
}
