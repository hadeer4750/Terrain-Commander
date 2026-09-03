package com.terrain.commander;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

public class ContactAwareMainActivity extends MainActivity {
    private static final int CONTACT_REQ = 177;
    private static final int CALL_REQ = 178;
    private WebView contactWeb;
    private String pendingContactName;
    private String pendingCallNumber;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        contactWeb = findWebView(getWindow().getDecorView());
        if (contactWeb != null) {
            contactWeb.addJavascriptInterface(new ContactBridge(), "AndroidContacts");
            scheduleBridgeInstall();
        }
    }

    @Override protected void onResume() {
        super.onResume();
        scheduleBridgeInstall();
    }

    private WebView findWebView(View view) {
        if (view instanceof WebView) return (WebView) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                WebView found = findWebView(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    private void scheduleBridgeInstall() {
        if (contactWeb == null) return;
        contactWeb.postDelayed(this::installBridge, 500);
        contactWeb.postDelayed(this::installBridge, 1500);
        contactWeb.postDelayed(this::installBridge, 3000);
    }

    private void installBridge() {
        if (contactWeb == null) return;
        String script = "(function(){" +
            "if(window.__terrainContactsInstalled||typeof window.execute!=='function')return;" +
            "window.__terrainContactsInstalled=true;" +
            "var original=window.execute;" +
            "window.execute=async function(raw){" +
              "var text=(typeof normalize==='function'?normalize(raw||''):String(raw||'').trim());" +
              "var isCall=/^(?:اتصل|دق على)\\s+/.test(text);" +
              "var hasNumber=/(?:\\+?964|0)?7\\d{9}/.test(text);" +
              "if(isCall&&!hasNumber&&window.AndroidContacts){" +
                "var name=text.replace(/^(?:اتصل|دق على)\\s+/,'').trim();" +
                "if(name){" +
                  "if(typeof say==='function')say('أبحث عن '+name);" +
                  "AndroidContacts.callContact(name);" +
                  "if(typeof log==='function')log(raw,'بحث عن جهة الاتصال: '+name);" +
                  "return 'بحث عن جهة اتصال';" +
                "}" +
              "}" +
              "return original(raw);" +
            "};" +
          "})();";
        contactWeb.evaluateJavascript(script, null);
    }

    private String normalizeName(String value) {
        if (value == null) return "";
        return value.replaceAll("[\\u064B-\\u065F\\u0670\\u0640]", "")
                .replace('أ','ا').replace('إ','ا').replace('آ','ا')
                .replaceAll("\\s+", " ").trim().toLowerCase();
    }

    private void callContact(String requestedName) {
        String name = requestedName == null ? "" : requestedName.trim();
        if (name.isEmpty()) {
            announce("قل اسم الشخص بعد كلمة اتصل");
            return;
        }
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            pendingContactName = name;
            requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, CONTACT_REQ);
            return;
        }
        lookupAndCall(name);
    }

    private void lookupAndCall(String requestedName) {
        final String needle = normalizeName(requestedName);
        ArrayList<String[]> exact = new ArrayList<>();
        ArrayList<String[]> partial = new ArrayList<>();
        String[] projection = {
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
        };
        try (Cursor cursor = getContentResolver().query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection, null, null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC")) {
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String display = cursor.getString(0);
                    String number = cursor.getString(1);
                    if (number == null || number.trim().isEmpty()) continue;
                    String normalized = normalizeName(display);
                    if (normalized.equals(needle)) exact.add(new String[]{display, number});
                    else if (normalized.contains(needle)) partial.add(new String[]{display, number});
                }
            }
        } catch (Exception error) {
            announce("تعذر قراءة جهات الاتصال");
            return;
        }

        ArrayList<String[]> selected = exact.isEmpty() ? partial : exact;
        if (selected.isEmpty()) {
            announce("لم أجد " + requestedName + " في جهات الاتصال");
            return;
        }

        LinkedHashMap<String,String> unique = new LinkedHashMap<>();
        for (String[] hit : selected) {
            String clean = hit[1].replaceAll("[^+0-9]", "");
            if (!clean.isEmpty()) unique.put(clean, hit[0] == null ? requestedName : hit[0]);
        }
        if (unique.isEmpty()) {
            announce("لم أجد رقم هاتف صالحًا لهذا الاسم");
            return;
        }

        if (unique.size() == 1) {
            Map.Entry<String,String> one = unique.entrySet().iterator().next();
            announce("أتصل بـ " + one.getValue());
            callNumber(one.getKey());
            return;
        }

        ArrayList<Map.Entry<String,String>> choices = new ArrayList<>(unique.entrySet());
        String[] labels = new String[choices.size()];
        for (int i = 0; i < choices.size(); i++) {
            Map.Entry<String,String> item = choices.get(i);
            labels[i] = item.getValue() + " — " + item.getKey();
        }
        new AlertDialog.Builder(this)
                .setTitle("أي رقم تريد؟")
                .setItems(labels, (dialog, which) -> {
                    Map.Entry<String,String> item = choices.get(which);
                    announce("أتصل بـ " + item.getValue());
                    callNumber(item.getKey());
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    private void callNumber(String number) {
        String clean = number == null ? "" : number.replaceAll("[^+0-9]", "");
        if (clean.isEmpty()) {
            announce("رقم الهاتف غير صالح");
            return;
        }
        if (checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            pendingCallNumber = clean;
            requestPermissions(new String[]{Manifest.permission.CALL_PHONE}, CALL_REQ);
            return;
        }
        try {
            startActivity(new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + clean)));
        } catch (Exception error) {
            try {
                startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + clean)));
            } catch (Exception ignored) {
                announce("تعذر بدء الاتصال");
            }
        }
    }

    private void announce(String message) {
        if (contactWeb == null) return;
        String quoted = JSONObject.quote(message == null ? "" : message);
        contactWeb.evaluateJavascript("if(typeof say==='function'){say(" + quoted + ");}else if(window.NativeVoice&&NativeVoice.onNotice){NativeVoice.onNotice(" + quoted + ");}", null);
    }

    public class ContactBridge {
        @JavascriptInterface public void callContact(String name) {
            runOnUiThread(() -> ContactAwareMainActivity.this.callContact(name));
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(requestCode, permissions, grants);
        if (requestCode == CONTACT_REQ) {
            String name = pendingContactName;
            pendingContactName = null;
            if (grants.length > 0 && grants[0] == PackageManager.PERMISSION_GRANTED && name != null) {
                lookupAndCall(name);
            } else {
                announce("صلاحية جهات الاتصال مطلوبة للاتصال بالاسم");
            }
        } else if (requestCode == CALL_REQ) {
            String number = pendingCallNumber;
            pendingCallNumber = null;
            if (grants.length > 0 && grants[0] == PackageManager.PERMISSION_GRANTED && number != null) {
                callNumber(number);
            } else if (number != null) {
                announce("لم تُمنح صلاحية الاتصال المباشر؛ سأفتح شاشة الاتصال");
                try { startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + number))); } catch (Exception ignored) {}
            }
        }
    }

    @Override protected void onDestroy() {
        if (contactWeb != null) contactWeb.removeJavascriptInterface("AndroidContacts");
        super.onDestroy();
    }
}
