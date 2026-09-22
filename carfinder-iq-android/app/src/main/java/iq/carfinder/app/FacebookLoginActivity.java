package iq.carfinder.app;

import android.app.*;
import android.os.Bundle;
import android.graphics.Color;
import android.webkit.*;
import android.widget.*;
import android.view.*;

public class FacebookLoginActivity extends Activity {
    private WebView web;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        Button done = new Button(this);
        done.setText("✅ تم تسجيل الدخول");
        done.setTextColor(Color.WHITE);
        done.setBackgroundColor(Color.rgb(24,119,242));
        done.setOnClickListener(v -> {
            String cookies = CookieManager.getInstance().getCookie("https://www.facebook.com/");
            if (cookies != null && cookies.contains("c_user=") && cookies.contains("xs=")) {
                Toast.makeText(this, "تم ربط جلسة Facebook على هذا الهاتف", Toast.LENGTH_LONG).show();
                finish();
            } else {
                Toast.makeText(this, "لم يتم اكتشاف تسجيل الدخول بعد", Toast.LENGTH_LONG).show();
            }
        });
        root.addView(done, new LinearLayout.LayoutParams(-1,-2));

        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(web, true);
        web.setWebViewClient(new WebViewClient());
        web.loadUrl("https://www.facebook.com/groups/feed/");
        root.addView(web, new LinearLayout.LayoutParams(-1,0,1f));
        setContentView(root);
    }

    @Override public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack(); else super.onBackPressed();
    }
}
