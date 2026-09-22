package iq.carfinder.app;

import android.app.*;
import android.os.Bundle;
import android.content.*;
import android.graphics.Color;
import android.provider.Settings;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.util.*;

public class MainActivity extends Activity {
    private EditText make, model, yearFrom, yearTo, priceTo;
    private Spinner city;
    private SharedPreferences prefs;
    private TextView fbStatus;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("carfinder_iq", MODE_PRIVATE);

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(18), dp(16), dp(28));
        scroll.addView(root);

        TextView title = text("🚘 CarFinder IQ", 26, true);
        title.setTextColor(Color.rgb(10,115,95));
        root.addView(title);
        root.addView(text("بحث موحّد: السوق المفتوح + iQ Cars + Facebook", 14, false));
        root.addView(space(14));

        TextView note = text(
            "V1.5: تم إلغاء تسجيل دخول Facebook داخل التطبيق. الربط أصبح مع تطبيق Facebook المثبّت على الهاتف.",
            14, false);
        note.setPadding(dp(12),dp(12),dp(12),dp(12));
        note.setBackgroundColor(Color.rgb(236,250,245));
        root.addView(note);
        root.addView(space(14));

        root.addView(section("مواصفات السيارة"));
        make = input("الشركة - مثال GMC", false); root.addView(make);
        model = input("الموديل - مثال Terrain", false); root.addView(model);

        LinearLayout years = new LinearLayout(this);
        years.setOrientation(LinearLayout.HORIZONTAL);
        yearFrom = input("السنة من", true);
        yearTo = input("السنة إلى", true);
        years.addView(yearFrom, weight());
        years.addView(spaceW(8));
        years.addView(yearTo, weight());
        root.addView(years);

        priceTo = input("أعلى سعر بالدولار - اختياري", true);
        root.addView(priceTo);

        city = new Spinner(this);
        String[] cities = {"كل العراق","البصرة","بغداد","النجف","كربلاء","أربيل","السليمانية","ذي قار","ميسان","واسط"};
        city.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, cities));
        root.addView(city, full());

        root.addView(space(12));

        Button all = button("🔎 ابحث في الجميع", Color.rgb(8,128,103));
        all.setTextSize(18);
        all.setOnClickListener(v -> {
            String q = buildQuery();
            Intent i = new Intent(this, ResultsActivity.class);
            i.putExtra("query", q);
            startActivity(i);
        });
        root.addView(all, full());

        root.addView(space(18));
        root.addView(section("ربط Facebook"));

        fbStatus = text("", 14, false);
        root.addView(fbStatus);

        Button enable = button("⚙️ تفعيل ربط Facebook", Color.rgb(24,119,242));
        enable.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            } catch (Exception e) {
                Toast.makeText(this, "تعذر فتح إعدادات إمكانية الوصول", Toast.LENGTH_LONG).show();
            }
        });
        root.addView(enable, full());

        TextView fbNote = text(
            "فعّل خدمة «CarFinder IQ - Facebook Capture» مرة واحدة من إعدادات إمكانية الوصول. بعدها يستخدم التطبيق حساب Facebook المفتوح على هاتفك ويجمع فقط النصوص التي تظهر لك أثناء البحث.",
            13, false);
        fbNote.setPadding(dp(10),dp(10),dp(10),dp(10));
        fbNote.setBackgroundColor(Color.rgb(239,245,255));
        root.addView(fbNote);

        restoreSearch();
        setContentView(scroll);
    }

    @Override protected void onResume() {
        super.onResume();
        if (fbStatus != null) {
            boolean enabled = isAccessibilityEnabled();
            fbStatus.setText(enabled
                ? "✅ ربط Facebook مفعّل."
                : "⚠️ ربط Facebook غير مفعّل بعد.");
        }
    }

    private boolean isAccessibilityEnabled() {
        try {
            String enabled = Settings.Secure.getString(
                getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            );
            return enabled != null &&
                enabled.toLowerCase().contains(getPackageName().toLowerCase() + "/.facebookcaptureservice");
        } catch (Exception e) {
            return false;
        }
    }

    private String buildQuery() {
        saveSearch();
        ArrayList<String> q = new ArrayList<>();
        addIf(q, make.getText().toString());
        addIf(q, model.getText().toString());

        String yf = yearFrom.getText().toString().trim();
        String yt = yearTo.getText().toString().trim();

        if (!yf.isEmpty() && yf.equals(yt)) q.add(yf);
        else {
            addIf(q, yf);
            addIf(q, yt);
        }

        String c = String.valueOf(city.getSelectedItem());
        if (!"كل العراق".equals(c)) q.add(c);

        String p = priceTo.getText().toString().trim();
        if (!p.isEmpty()) q.add("أقل من " + p + " دولار");

        return String.join(" ", q);
    }

    private void addIf(ArrayList<String> q, String s) {
        if (s != null && !s.trim().isEmpty()) q.add(s.trim());
    }

    private EditText input(String hint, boolean numeric) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setTextSize(16);
        e.setPadding(dp(12), dp(10), dp(12), dp(10));
        if (numeric) e.setInputType(InputType.TYPE_CLASS_NUMBER);
        e.setSingleLine(true);
        e.setLayoutParams(full());
        return e;
    }

    private TextView section(String s) {
        TextView t = text(s, 19, true);
        t.setPadding(0,dp(4),0,dp(8));
        return t;
    }

    private void saveSearch() {
        prefs.edit()
            .putString("make", make.getText().toString())
            .putString("model", model.getText().toString())
            .putString("yf", yearFrom.getText().toString())
            .putString("yt", yearTo.getText().toString())
            .putString("price", priceTo.getText().toString())
            .putInt("city", city.getSelectedItemPosition())
            .apply();
    }

    private void restoreSearch() {
        make.setText(prefs.getString("make","GMC"));
        model.setText(prefs.getString("model","Terrain"));
        yearFrom.setText(prefs.getString("yf","2021"));
        yearTo.setText(prefs.getString("yt","2023"));
        priceTo.setText(prefs.getString("price",""));
        int c = prefs.getInt("city",0);
        if (c >= 0 && c < city.getCount()) city.setSelection(c);
    }

    private TextView text(String s,int size,boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        if (bold) t.setTypeface(null,1);
        return t;
    }

    private Button button(String s,int color) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextColor(Color.WHITE);
        b.setBackgroundColor(color);
        b.setAllCaps(false);
        return b;
    }

    private View space(int h) {
        Space s = new Space(this);
        s.setLayoutParams(new LinearLayout.LayoutParams(1,dp(h)));
        return s;
    }

    private View spaceW(int w) {
        Space s = new Space(this);
        s.setLayoutParams(new LinearLayout.LayoutParams(dp(w),1));
        return s;
    }

    private LinearLayout.LayoutParams full() { return new LinearLayout.LayoutParams(-1,-2); }
    private LinearLayout.LayoutParams weight() { return new LinearLayout.LayoutParams(0,-2,1f); }
    private int dp(int v) { return Math.round(v*getResources().getDisplayMetrics().density); }
}
