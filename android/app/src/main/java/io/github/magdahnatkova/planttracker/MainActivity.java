package io.github.magdahnatkova.planttracker;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.webkit.WebViewAssetLoader;

/**
 * Shows the web app's index.html, bundled in the APK, in a WebView.
 *
 * The page is served under the website's own address, so it talks to Firebase
 * exactly as the website does and reads and writes the same database.
 */
public class MainActivity extends Activity {

    private static final String HOST = "magdahnatkova.github.io";
    private static final String PATH = "/plant-tracker/";
    private static final String START_URL = "https://" + HOST + PATH + "index.html";

    private static final String PREFS = "plant-tracker";
    private static final String PREF_USER = "user";

    private WebView webView;
    private SharedPreferences prefs;
    // The profile shown before the latest navigation; null on the profile picker.
    private String previousUser;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);

        WebViewAssetLoader assetLoader = new WebViewAssetLoader.Builder()
                .setDomain(HOST)
                .addPathHandler(PATH, new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        // Offline, fall back to cached copies of the Firebase scripts.
        settings.setCacheMode(isOnline() ? WebSettings.LOAD_DEFAULT : WebSettings.LOAD_CACHE_ELSE_NETWORK);

        webView.addJavascriptInterface(new AndroidApp(), "AndroidApp");
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return assetLoader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri url = request.getUrl();
                if (HOST.equals(url.getHost()) && url.getPath() != null && url.getPath().startsWith(PATH)) {
                    return false;
                }
                openInBrowser(url);
                return true;
            }

            @Override
            public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
                rememberUser(Uri.parse(url).getQueryParameter("user"));
            }
        });

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState);
        } else {
            String user = prefs.getString(PREF_USER, null);
            webView.loadUrl(user == null ? START_URL : userUrl(user));
        }
    }

    /**
     * Opens the app on the profile last picked on the profile picker. Following
     * "View →" to someone else's page doesn't change it.
     */
    private void rememberUser(String user) {
        if (user == null) {
            prefs.edit().remove(PREF_USER).apply();
        } else if (previousUser == null) {
            prefs.edit().putString(PREF_USER, user).apply();
        }
        previousUser = user;
    }

    private static String userUrl(String user) {
        return START_URL + "?user=" + Uri.encode(user);
    }

    private boolean isOnline() {
        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        NetworkCapabilities caps = cm == null ? null : cm.getNetworkCapabilities(cm.getActiveNetwork());
        return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    private void openInBrowser(Uri url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, url));
        } catch (ActivityNotFoundException ignored) {
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else if (previousUser != null) {
            // Back from your own page goes to the profile picker.
            webView.loadUrl(START_URL);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        webView.saveState(outState);
    }

    @Override
    protected void onDestroy() {
        webView.destroy();
        super.onDestroy();
    }

    /** Called from index.html as window.AndroidApp. */
    private class AndroidApp {
        /** Exporting a week opens the share menu, since a WebView can't download files. */
        @JavascriptInterface
        public void shareText(String fileName, String text) {
            Intent send = new Intent(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_SUBJECT, fileName)
                    .putExtra(Intent.EXTRA_TEXT, text);
            runOnUiThread(() -> startActivity(Intent.createChooser(send, getString(R.string.share_export))));
        }
    }
}
