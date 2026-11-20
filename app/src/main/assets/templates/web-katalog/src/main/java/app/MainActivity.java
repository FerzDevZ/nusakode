package app;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/**
 * Membungkus halaman web lokal menjadi aplikasi Android. Berkas HTML/CSS/JS ada di
 * src/main/assets dan dibaca dari file:///android_asset — tidak perlu internet sama sekali.
 */
public class MainActivity extends Activity {

    private WebView web;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        web = new WebView(this);
        WebSettings setelan = web.getSettings();
        setelan.setJavaScriptEnabled(true);
        setelan.setDomStorageEnabled(true);
        setelan.setAllowFileAccess(true);
        setelan.setCacheMode(WebSettings.LOAD_NO_CACHE);
        web.setWebViewClient(new WebViewClient());
        web.loadUrl("file:///android_asset/index.html");
        setContentView(web);
    }

    /** Tombol kembali mengikuti riwayat halaman, bukan langsung menutup aplikasi. */
    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) {
            web.goBack();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
}
