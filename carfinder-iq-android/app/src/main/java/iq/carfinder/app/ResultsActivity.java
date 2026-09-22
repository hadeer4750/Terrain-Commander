package iq.carfinder.app;

import android.app.*;
import android.os.Bundle;
import android.content.*;
import android.graphics.Color;
import android.net.Uri;
import android.webkit.*;
import android.widget.*;
import android.view.*;
import org.json.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import fbbridge.Fbbridge;

public class ResultsActivity extends Activity {
    private String query;
    private LinearLayout content;
    private ExecutorService exec = Executors.newSingleThreadExecutor();

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        query = getIntent().getStringExtra("query");
        if (query == null) query = "";

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(this);
        title.setText("نتائج البحث: " + query);
        title.setTextSize(17);
        title.setPadding(dp(12),dp(12),dp(12),dp(10));
        root.addView(title);

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        Button iq = tab("iQ Cars");
        Button os = tab("السوق المفتوح");
        Button fb = tab("Facebook");
        tabs.addView(iq, weight());
        tabs.addView(os, weight());
        tabs.addView(fb, weight());
        root.addView(tabs);

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        root.addView(content, new LinearLayout.LayoutParams(-1,0,1f));

        iq.setOnClickListener(v -> showWeb("iqcars.net"));
        os.setOnClickListener(v -> showWeb("iq.opensooq.com"));
        fb.setOnClickListener(v -> showFacebook());

        setContentView(root);
        showWeb("iqcars.net");
    }

    private Button tab(String t) {
        Button b = new Button(this);
        b.setText(t);
        b.setAllCaps(false);
        return b;
    }

    private void showWeb(String domain) {
        content.removeAllViews();
        WebView w = new WebView(this);
        w.getSettings().setJavaScriptEnabled(true);
        w.getSettings().setDomStorageEnabled(true);
        w.setWebViewClient(new WebViewClient());
        String q = "site:" + domain + " " + query;
        w.loadUrl("https://www.google.com/search?q=" + enc(q));
        content.addView(w, new LinearLayout.LayoutParams(-1,-1));
    }

    private void showFacebook() {
        content.removeAllViews();
        ProgressBar progress = new ProgressBar(this);
        TextView status = new TextView(this);
        status.setText("جاري جلب كروبات حسابك والبحث في منشوراتها…");
        status.setTextSize(16);
        status.setPadding(dp(12),dp(12),dp(12),dp(12));
        content.addView(progress);
        content.addView(status);

        String cookies = CookieManager.getInstance().getCookie("https://www.facebook.com/");
        if (cookies == null || !cookies.contains("c_user=") || !cookies.contains("xs=")) {
            content.removeAllViews();
            TextView t = new TextView(this);
            t.setText("لم يتم ربط Facebook. ارجع إلى الشاشة الرئيسية واضغط «ربط حساب Facebook».");
            t.setPadding(dp(16),dp(16),dp(16),dp(16));
            content.addView(t);
            return;
        }

        exec.submit(() -> {
            try {
                String json = Fbbridge.searchJoinedGroups(cookies, query);
                runOnUiThread(() -> renderFacebook(json));
            } catch (Exception e) {
                runOnUiThread(() -> {
                    content.removeAllViews();
                    TextView t = new TextView(this);
                    t.setText("تعذر البحث في Facebook الآن:\n" + e.getMessage() + "\n\nقد تحتاج إلى إعادة تسجيل الدخول إذا انتهت الجلسة.");
                    t.setPadding(dp(16),dp(16),dp(16),dp(16));
                    content.addView(t);
                });
            }
        });
    }

    private void renderFacebook(String json) {
        content.removeAllViews();
        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(10),dp(10),dp(10),dp(20));
        scroll.addView(list);

        try {
            JSONArray arr = new JSONArray(json);
            TextView count = new TextView(this);
            count.setText("تم العثور على " + arr.length() + " منشور مطابق");
            count.setTextSize(16);
            count.setPadding(dp(4),dp(4),dp(4),dp(12));
            list.addView(count);

            if (arr.length() == 0) {
                TextView none = new TextView(this);
                none.setText("لا توجد نتائج مطابقة في أول صفحة من منشورات الكروبات التي أمكن قراءتها.");
                none.setPadding(dp(10),dp(10),dp(10),dp(10));
                list.addView(none);
            }

            for (int i=0;i<arr.length();i++) {
                JSONObject o=arr.getJSONObject(i);
                LinearLayout card=new LinearLayout(this);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setPadding(dp(12),dp(12),dp(12),dp(12));
                card.setBackgroundColor(Color.rgb(247,249,252));

                TextView g=new TextView(this);
                g.setText("👥 " + o.optString("groupName"));
                g.setTextSize(16);
                g.setTypeface(null,1);
                card.addView(g);

                TextView author=new TextView(this);
                author.setText("الناشر: " + o.optString("authorName"));
                card.addView(author);

                TextView msg=new TextView(this);
                msg.setText(o.optString("message"));
                msg.setTextSize(15);
                msg.setPadding(0,dp(8),0,dp(8));
                card.addView(msg);

                Button open=new Button(this);
                open.setText("فتح المنشور في Facebook");
                String url=o.optString("url");
                open.setOnClickListener(v -> {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
                    catch(Exception ignored) {}
                });
                card.addView(open);

                list.addView(card, new LinearLayout.LayoutParams(-1,-2));
                Space sp=new Space(this);
                sp.setLayoutParams(new LinearLayout.LayoutParams(1,dp(8)));
                list.addView(sp);
            }
        } catch(Exception e) {
            TextView t=new TextView(this);
            t.setText("خطأ في قراءة النتائج: " + e.getMessage());
            list.addView(t);
        }

        content.addView(scroll, new LinearLayout.LayoutParams(-1,-1));
    }

    private String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }
    private LinearLayout.LayoutParams weight(){ return new LinearLayout.LayoutParams(0,-2,1f); }
    private int dp(int v){ return Math.round(v*getResources().getDisplayMetrics().density); }

    @Override protected void onDestroy() {
        exec.shutdownNow();
        super.onDestroy();
    }
}
