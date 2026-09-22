package iq.carfinder.app;

import android.app.*;
import android.os.Bundle;
import android.content.*;
import android.graphics.Color;
import android.webkit.*;
import android.widget.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class ResultsActivity extends Activity {
    private WebView web;
    private String query;
    private SharedPreferences prefs;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        query = getIntent().getStringExtra("query");
        if (query == null) query = "";
        prefs = getSharedPreferences("carfinder_iq", MODE_PRIVATE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(this);
        title.setText("نتائج البحث: " + query);
        title.setTextSize(16);
        title.setTypeface(null,1);
        title.setPadding(dp(12),dp(12),dp(12),dp(8));
        root.addView(title);

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);

        Button os = tab("السوق المفتوح");
        Button iq = tab("iQ Cars");
        Button fb = tab("Facebook");

        os.setOnClickListener(v -> loadOpenSooq());
        iq.setOnClickListener(v -> loadIqCars());
        fb.setOnClickListener(v -> loadFacebookIndex());

        tabs.addView(os, weight());
        tabs.addView(iq, weight());
        tabs.addView(fb, weight());
        root.addView(tabs);

        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setLoadsImagesAutomatically(true);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);

        web.setWebViewClient(new WebViewClient());
        web.setWebChromeClient(new WebChromeClient());

        root.addView(web, new LinearLayout.LayoutParams(-1,0,1f));
        setContentView(root);

        loadOpenSooq();
    }

    private void loadOpenSooq() {
        String url = "https://iq.opensooq.com/ar/%D8%B3%D9%8A%D8%A7%D8%B1%D8%A7%D8%AA-%D9%88%D9%85%D8%B1%D9%83%D8%A8%D8%A7%D8%AA?term=" + enc(query);
        web.loadUrl(url);
    }

    private void loadIqCars() {
        String q = "site:iqcars.net " + query;
        web.loadUrl("https://www.google.com/search?q=" + enc(q));
    }

    private void loadFacebookIndex() {
        Set<String> stored = prefs.getStringSet("fb_groups", new LinkedHashSet<>());
        ArrayList<String> groups = new ArrayList<>(stored);

        StringBuilder html = new StringBuilder();
        html.append("<html dir='rtl'><head><meta name='viewport' content='width=device-width,initial-scale=1'>");
        html.append("<style>");
        html.append("body{font-family:sans-serif;padding:16px;background:#f6f8fb;color:#17202a}");
        html.append(".card{background:white;border-radius:14px;padding:14px;margin:10px 0;box-shadow:0 2px 8px #0001}");
        html.append("a{display:block;background:#1877f2;color:white;text-decoration:none;padding:12px;border-radius:10px;text-align:center;margin-top:8px}");
        html.append(".muted{color:#657786;font-size:13px}");
        html.append("</style></head><body>");
        html.append("<h2>نتائج Facebook</h2>");
        html.append("<div class='muted'>البحث: ").append(esc(query)).append("</div>");

        if (groups.isEmpty()) {
            html.append("<div class='card'><b>لم يتم استيراد كروبات بعد.</b>");
            html.append("<p>ارجع للشاشة الرئيسية واضغط «تسجيل الدخول واستيراد كروباتي».</p></div>");
        } else {
            html.append("<p>الكروبات المستوردة: ").append(groups.size()).append("</p>");
            for (String item : groups) {
                String[] p = item.split("\\|\\|\\|",2);
                String name = p.length > 0 ? p[0] : "كروب";
                String url = p.length > 1 ? p[1] : "";

                String searchUrl;
                if (!url.isEmpty()) {
                    if (url.endsWith("/")) url = url.substring(0,url.length()-1);
                    searchUrl = url + "/search/?q=" + enc(query);
                } else {
                    searchUrl = "https://www.facebook.com/search/groups/?q=" + enc(name + " " + query);
                }

                html.append("<div class='card'><b>").append(esc(name)).append("</b>");
                html.append("<a href='").append(escAttr(searchUrl)).append("'>عرض نتائج هذا الكروب</a></div>");
            }
        }

        html.append("</body></html>");
        web.loadDataWithBaseURL("https://www.facebook.com/", html.toString(), "text/html", "UTF-8", null);
    }

    private Button tab(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setBackgroundColor(Color.rgb(32,73,107));
        return b;
    }

    private String enc(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }

    private String esc(String s) {
        if (s == null) return "";
        return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");
    }

    private String escAttr(String s) {
        return esc(s).replace("'","&#39;").replace("\"","&quot;");
    }

    private LinearLayout.LayoutParams weight() {
        return new LinearLayout.LayoutParams(0,-2,1f);
    }

    private int dp(int v) {
        return Math.round(v*getResources().getDisplayMetrics().density);
    }

    @Override public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }
}
