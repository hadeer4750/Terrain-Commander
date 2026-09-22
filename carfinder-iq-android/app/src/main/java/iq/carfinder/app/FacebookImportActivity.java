package iq.carfinder.app;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.webkit.*;
import android.widget.*;
import org.json.*;
import java.util.*;
import java.util.regex.*;

public class FacebookImportActivity extends Activity {
    private WebView web;
    private SharedPreferences prefs;
    private TextView status;
    private Button importBtn;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int scanStep = 0;
    private int lastTotal = 0;
    private boolean autoImportRunning = false;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("carfinder_iq", MODE_PRIVATE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        status = new TextView(this);
        status.setText("سجّل الدخول إلى Facebook إن طُلب، ثم اضغط «استيراد تلقائي». التطبيق سيمرّر صفحة الكروبات ويجمع الروابط تلقائيًا.");
        status.setTextSize(14);
        status.setPadding(dp(12),dp(10),dp(12),dp(10));
        status.setBackgroundColor(Color.rgb(239,245,255));
        root.addView(status);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);

        Button myGroups = button("فتح صفحة الكروبات");
        myGroups.setOnClickListener(v -> {
            autoImportRunning = false;
            web.loadUrl("https://www.facebook.com/groups/feed/");
        });
        bar.addView(myGroups, weight());

        importBtn = button("استيراد تلقائي");
        importBtn.setOnClickListener(v -> startAutoImport());
        bar.addView(importBtn, weight());

        root.addView(bar);

        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setLoadsImagesAutomatically(true);
        s.setDatabaseEnabled(true);
        s.setUseWideViewPort(false);
        s.setLoadWithOverviewMode(false);
        s.setUserAgentString(
            "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/140.0.0.0 Mobile Safari/537.36"
        );

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web,true);

        web.addJavascriptInterface(new GroupBridge(), "CarFinderBridge");
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (autoImportRunning) {
                    handler.postDelayed(() -> runScanCycle(), 900);
                }
            }
        });

        root.addView(web, new LinearLayout.LayoutParams(-1,0,1f));
        setContentView(root);

        web.loadUrl("https://www.facebook.com/groups/feed/");
    }

    private void startAutoImport() {
        scanStep = 0;
        lastTotal = prefs.getStringSet("fb_groups", new LinkedHashSet<>()).size();
        autoImportRunning = true;
        importBtn.setEnabled(false);
        status.setText("جاري فتح صفحة الكروبات وتجهيز الاستيراد...");
        web.loadUrl("https://www.facebook.com/groups/feed/");
        handler.postDelayed(() -> runScanCycle(), 1800);
    }

    private void runScanCycle() {
        if (!autoImportRunning || isFinishing()) return;

        if (scanStep >= 14) {
            finishAutoImport();
            return;
        }

        scanStep++;
        status.setText("جاري الاستيراد... خطوة " + scanStep + " من 14 — المحفوظ حتى الآن: " +
                prefs.getStringSet("fb_groups", new LinkedHashSet<>()).size());

        scanVisibleGroups();

        web.evaluateJavascript(
            "(function(){window.scrollBy(0,Math.max(window.innerHeight*2,1800));return document.body.scrollHeight;})();",
            null
        );

        handler.postDelayed(() -> runScanCycle(), 850);
    }

    private void scanVisibleGroups() {
        String js =
            "(function(){"+
            "const out=[];const seen={};"+
            "const bad=new Set(['feed','discover','create','notifications','joins','your_groups','for_you','search']);"+
            "function add(raw,name){"+
            " try{"+
            "  const u=new URL(raw,location.href);"+
            "  const m=u.pathname.match(/\\/groups\\/([^\\/?#]+)/i);"+
            "  if(!m)return;"+
            "  const id=(m[1]||'').trim();"+
            "  if(!id||bad.has(id.toLowerCase()))return;"+
            "  const key=id.toLowerCase(); if(seen[key])return; seen[key]=1;"+
            "  let n=(name||id).replace(/\\s+/g,' ').trim(); if(!n)n=id;"+
            "  if(n.length>160)n=n.slice(0,160);"+
            "  out.push({name:n,url:'https://www.facebook.com/groups/'+id});"+
            " }catch(e){}"+
            "}"+
            "document.querySelectorAll('a[href]').forEach(a=>{"+
            " const h=a.getAttribute('href')||a.href||'';"+
            " const n=(a.innerText||a.textContent||a.getAttribute('aria-label')||'').trim();"+
            " if(h.indexOf('/groups/')!==-1)add(h,n);"+
            "});"+
            "const html=document.documentElement?document.documentElement.innerHTML:'';"+
            "const re=/(?:https?:\\\\/\\\\/(?:www\\\\.|m\\\\.)?facebook\\\\.com)?\\\\/groups\\\\/([A-Za-z0-9._-]{3,})/gi;"+
            "let m;while((m=re.exec(html))!==null){add('/groups/'+m[1],m[1]);if(out.length>500)break;}"+
            "CarFinderBridge.saveGroups(JSON.stringify(out));"+
            "return out.length;"+
            "})();";

        web.evaluateJavascript(js, value -> {
            // النتيجة العددية من JavaScript ليست ضرورية، والحفظ يتم عبر GroupBridge.
        });
    }

    private void finishAutoImport() {
        autoImportRunning = false;
        importBtn.setEnabled(true);

        int total = prefs.getStringSet("fb_groups", new LinkedHashSet<>()).size();
        int added = Math.max(0, total - lastTotal);

        if (total == 0) {
            status.setText(
                "لم يتم العثور على كروبات في الصفحة الحالية. تأكد أنك داخل حساب Facebook، ثم افتح صفحة الكروبات التي أنت عضو فيها واضغط «استيراد تلقائي» مرة ثانية."
            );
            Toast.makeText(this, "لم يتم العثور على كروبات", Toast.LENGTH_LONG).show();
        } else {
            status.setText("اكتمل الاستيراد: " + total + " كروب محفوظ، الجديد في هذه المحاولة: " + added + ".");
            Toast.makeText(this, "تم استيراد " + total + " كروب", Toast.LENGTH_LONG).show();
        }
    }

    public class GroupBridge {
        @JavascriptInterface public void saveGroups(String json) {
            runOnUiThread(() -> {
                try {
                    JSONArray arr = new JSONArray(json);
                    LinkedHashMap<String,String> byUrl = new LinkedHashMap<>();

                    Set<String> old = prefs.getStringSet("fb_groups", new LinkedHashSet<>());
                    for (String item : old) {
                        String[] p = item.split("\\|\\|\\|",2);
                        if (p.length == 2) byUrl.put(p[1], item);
                    }

                    for (int i=0;i<arr.length();i++) {
                        JSONObject o = arr.getJSONObject(i);
                        String name = o.optString("name","كروب").trim();
                        String url = normalize(o.optString("url",""));

                        if (!url.isEmpty()) {
                            if (name.isEmpty()) name = groupId(url);
                            String existing = byUrl.get(url);

                            if (existing == null || existing.startsWith(groupId(url) + "|||")) {
                                byUrl.put(url, name + "|||" + url);
                            }
                        }
                    }

                    LinkedHashSet<String> merged = new LinkedHashSet<>(byUrl.values());
                    prefs.edit().putStringSet("fb_groups", merged).apply();

                    if (autoImportRunning) {
                        status.setText("جاري الاستيراد... خطوة " + scanStep + " من 14 — تم العثور على " + merged.size() + " كروب");
                    }
                } catch (Exception e) {
                    status.setText("حدث خطأ أثناء قراءة دفعة من الكروبات، وسيستمر التطبيق بالمحاولة.");
                }
            });
        }
    }

    private String normalize(String u) {
        if (u == null) return "";
        Matcher m = Pattern.compile("facebook\\.com/groups/([^/?#]+)", Pattern.CASE_INSENSITIVE).matcher(u);
        if (m.find()) {
            String id = m.group(1);
            if (id == null || id.isEmpty()) return "";
            return "https://www.facebook.com/groups/" + id;
        }
        return "";
    }

    private String groupId(String u) {
        Matcher m = Pattern.compile("facebook\\.com/groups/([^/?#]+)", Pattern.CASE_INSENSITIVE).matcher(u == null ? "" : u);
        return m.find() ? m.group(1) : "كروب";
    }

    private Button button(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setBackgroundColor(Color.rgb(24,119,242));
        return b;
    }

    private LinearLayout.LayoutParams weight() {
        return new LinearLayout.LayoutParams(0,-2,1f);
    }

    private int dp(int v) {
        return Math.round(v*getResources().getDisplayMetrics().density);
    }

    @Override protected void onDestroy() {
        autoImportRunning = false;
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }
}
