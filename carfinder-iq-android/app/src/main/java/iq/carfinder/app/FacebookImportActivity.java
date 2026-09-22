package iq.carfinder.app;

import android.app.*;
import android.os.Bundle;
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

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("carfinder_iq", MODE_PRIVATE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        status = new TextView(this);
        status.setText("1) سجّل الدخول إلى Facebook إن طُلب.\n2) افتح قائمة الكروبات التي انضممت إليها.\n3) بعد ظهور الكروبات اضغط «استيراد الكروبات الظاهرة».");
        status.setTextSize(14);
        status.setPadding(dp(12),dp(10),dp(12),dp(10));
        status.setBackgroundColor(Color.rgb(239,245,255));
        root.addView(status);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);

        Button myGroups = button("فتح كروباتي");
        myGroups.setOnClickListener(v -> web.loadUrl("https://www.facebook.com/groups/joins/"));
        bar.addView(myGroups, weight());

        Button importBtn = button("استيراد الكروبات الظاهرة");
        importBtn.setOnClickListener(v -> importVisibleGroups());
        bar.addView(importBtn, weight());

        root.addView(bar);

        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setLoadsImagesAutomatically(true);
        s.setUserAgentString(s.getUserAgentString().replace("; wv",""));
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web,true);
        web.addJavascriptInterface(new GroupBridge(), "CarFinderBridge");
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient());

        root.addView(web, new LinearLayout.LayoutParams(-1,0,1f));
        setContentView(root);

        web.loadUrl("https://www.facebook.com/groups/");
    }

    private void importVisibleGroups() {
        String js =
            "(function(){"+
            "const out=[]; const seen={};"+
            "document.querySelectorAll('a[href*=\"/groups/\"]').forEach(a=>{"+
            " try{ const u=new URL(a.href, location.href); const m=u.pathname.match(/^\\/groups\\/([^\\/?#]+)/);"+
            " if(!m)return; const id=m[1]; const bad=['feed','discover','create','notifications','joins']; if(bad.includes(id))return;"+
            " const key=id.toLowerCase(); if(seen[key])return; seen[key]=1;"+
            " let name=(a.innerText||a.getAttribute('aria-label')||id).trim(); if(!name)name=id;"+
            " out.push({name:name,url:'https://www.facebook.com/groups/'+id}); }catch(e){}"+
            "});"+
            "CarFinderBridge.saveGroups(JSON.stringify(out));"+
            "})();";
        web.evaluateJavascript(js, null);
    }

    public class GroupBridge {
        @JavascriptInterface public void saveGroups(String json) {
            runOnUiThread(() -> {
                try {
                    JSONArray arr = new JSONArray(json);
                    LinkedHashSet<String> merged = new LinkedHashSet<>(prefs.getStringSet("fb_groups", new LinkedHashSet<>()));
                    int before = merged.size();
                    for (int i=0;i<arr.length();i++) {
                        JSONObject o = arr.getJSONObject(i);
                        String name = o.optString("name","كروب").trim();
                        String url = normalize(o.optString("url",""));
                        if (!url.isEmpty()) merged.add(name + "|||" + url);
                    }
                    prefs.edit().putStringSet("fb_groups", merged).apply();
                    int added = merged.size() - before;
                    status.setText("تم الاستيراد. المجموع: " + merged.size() + " كروب، الجديد: " + added + ". إذا كانت بعض الكروبات غير ظاهرة، مرّر الصفحة للأسفل ثم اضغط الاستيراد مرة ثانية.");
                    Toast.makeText(FacebookImportActivity.this, "تم حفظ " + merged.size() + " كروب", Toast.LENGTH_LONG).show();
                } catch (Exception e) {
                    status.setText("تعذر قراءة الكروبات من الصفحة الحالية. افتح «كروباتي» وانتظر ظهور القائمة ثم جرّب مرة ثانية.");
                }
            });
        }
    }

    private String normalize(String u) {
        if (u == null) return "";
        Matcher m = Pattern.compile("facebook\\.com/groups/([^/?#]+)", Pattern.CASE_INSENSITIVE).matcher(u);
        if (m.find()) return "https://www.facebook.com/groups/" + m.group(1);
        return "";
    }

    private Button button(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setBackgroundColor(Color.rgb(24,119,242));
        return b;
    }

    private LinearLayout.LayoutParams weight() { return new LinearLayout.LayoutParams(0,-2,1f); }
    private int dp(int v) { return Math.round(v*getResources().getDisplayMetrics().density); }

    @Override public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }
}
