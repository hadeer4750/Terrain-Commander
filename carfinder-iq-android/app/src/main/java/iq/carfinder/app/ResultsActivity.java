package iq.carfinder.app;

import android.app.*;
import android.os.Bundle;
import android.content.*;
import android.graphics.Color;
import android.net.Uri;
import android.webkit.*;
import android.widget.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class ResultsActivity extends Activity {
    private WebView web;
    private String query;
    private SharedPreferences prefs;
    private boolean facebookTab = false;

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

        os.setOnClickListener(v -> { facebookTab=false; loadOpenSooq(); });
        iq.setOnClickListener(v -> { facebookTab=false; loadIqCars(); });
        fb.setOnClickListener(v -> { facebookTab=true; loadFacebookResults(); });

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

        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                if ("carfinder".equalsIgnoreCase(u.getScheme())) {
                    String host = u.getHost();
                    if ("start-facebook".equals(host)) {
                        startFacebookSearch();
                        return true;
                    }
                    if ("refresh-facebook".equals(host)) {
                        loadFacebookResults();
                        return true;
                    }
                }
                return false;
            }

            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url != null && url.startsWith("carfinder://start-facebook")) {
                    startFacebookSearch();
                    return true;
                }
                if (url != null && url.startsWith("carfinder://refresh-facebook")) {
                    loadFacebookResults();
                    return true;
                }
                return false;
            }
        });

        root.addView(web, new LinearLayout.LayoutParams(-1,0,1f));
        setContentView(root);

        loadOpenSooq();
    }

    @Override protected void onResume() {
        super.onResume();
        if (facebookTab && web != null) {
            loadFacebookResults();
        }
    }

    private void loadOpenSooq() {
        String url = "https://iq.opensooq.com/ar/%D8%B3%D9%8A%D8%A7%D8%B1%D8%A7%D8%AA-%D9%88%D9%85%D8%B1%D9%83%D8%A8%D8%A7%D8%AA?term=" + enc(query);
        web.loadUrl(url);
    }

    private void loadIqCars() {
        String q = "site:iqcars.net " + query;
        web.loadUrl("https://www.google.com/search?q=" + enc(q));
    }

    private void loadFacebookResults() {
        Set<String> stored = prefs.getStringSet("fb_results", new LinkedHashSet<>());
        ArrayList<String> rows = new ArrayList<>(stored);

        StringBuilder html = new StringBuilder();
        html.append("<html dir='rtl'><head><meta name='viewport' content='width=device-width,initial-scale=1'>");
        html.append("<style>");
        html.append("body{font-family:sans-serif;padding:16px;background:#f6f8fb;color:#17202a}");
        html.append(".card{background:white;border-radius:14px;padding:14px;margin:10px 0;box-shadow:0 2px 8px #0001;line-height:1.7}");
        html.append(".btn{display:block;background:#1877f2;color:white;text-decoration:none;padding:13px;border-radius:10px;text-align:center;margin:10px 0;font-weight:bold}");
        html.append(".secondary{background:#4f6578}.muted{color:#657786;font-size:13px}");
        html.append("</style></head><body>");
        html.append("<h2>نتائج Facebook</h2>");
        html.append("<div class='muted'>البحث: ").append(esc(query)).append("</div>");
        html.append("<a class='btn' href='carfinder://start-facebook'>ابدأ بحث Facebook بالحساب المفتوح</a>");
        html.append("<a class='btn secondary' href='carfinder://refresh-facebook'>تحديث النتائج المجمعة</a>");

        if (rows.isEmpty()) {
            html.append("<div class='card'>");
            html.append("<b>لا توجد نتائج مجمعة بعد.</b><br>");
            html.append("فعّل خدمة CarFinder من إعدادات إمكانية الوصول، ثم اضغط «ابدأ بحث Facebook». ");
            html.append("سيُفتح تطبيق Facebook، وسيجمع CarFinder النصوص المطابقة التي تظهر لك أثناء البحث.");
            html.append("</div>");
        } else {
            html.append("<p>تم جمع ").append(rows.size()).append(" نتيجة/مقطع ظاهر.</p>");
            Collections.reverse(rows);
            int count=0;
            for (String row : rows) {
                if (count++ >= 100) break;
                html.append("<div class='card'>").append(esc(row)).append("</div>");
            }
        }

        html.append("<div class='muted'>ملاحظة: النتائج تأتي من المحتوى الذي يعرضه Facebook لحسابك على الهاتف، وقد تختلف التغطية حسب واجهة Facebook ونتائج البحث المتاحة لحسابك.</div>");
        html.append("</body></html>");

        web.loadDataWithBaseURL("https://local.carfinder/", html.toString(), "text/html", "UTF-8", null);
    }

    private void startFacebookSearch() {
        prefs.edit()
            .putString("fb_query", query)
            .putStringSet("fb_results", new LinkedHashSet<>())
            .putBoolean("fb_capture_active", true)
            .putLong("fb_capture_started_at", System.currentTimeMillis())
            .apply();

        String url = "https://www.facebook.com/search/posts/?q=" + enc(query);
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));

        try {
            i.setPackage("com.facebook.katana");
            startActivity(i);
        } catch (Exception e) {
            try {
                i.setPackage(null);
                startActivity(i);
            } catch (Exception ex) {
                Toast.makeText(this, "تعذر فتح Facebook", Toast.LENGTH_LONG).show();
            }
        }
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

    private LinearLayout.LayoutParams weight() {
        return new LinearLayout.LayoutParams(0,-2,1f);
    }

    private int dp(int v) {
        return Math.round(v*getResources().getDisplayMetrics().density);
    }

    @Override public void onBackPressed() {
        if (web != null && web.canGoBack() && !facebookTab) web.goBack();
        else super.onBackPressed();
    }
}
