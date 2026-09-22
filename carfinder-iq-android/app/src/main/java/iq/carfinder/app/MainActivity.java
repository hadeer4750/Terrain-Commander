package iq.carfinder.app;

import android.app.*;
import android.os.Bundle;
import android.content.*;
import android.graphics.Color;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.util.*;

public class MainActivity extends Activity {
    private EditText make, model, yearFrom, yearTo, priceTo;
    private Spinner city;
    private SharedPreferences prefs;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("carfinder_iq", MODE_PRIVATE);

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = box(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(18), dp(16), dp(28));
        scroll.addView(root);

        TextView title = text("🚘 CarFinder IQ", 26, true);
        title.setTextColor(Color.rgb(12,122,101));
        root.addView(title);
        root.addView(text("بحث واحد في iQ Cars + السوق المفتوح + كروبات فيسبوك", 14, false));
        root.addView(space(14));

        root.addView(section("مواصفات السيارة"));
        make = input("الشركة - مثال GMC", false); root.addView(make);
        model = input("الموديل - مثال Terrain", false); root.addView(model);

        LinearLayout years = box(LinearLayout.HORIZONTAL);
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

        root.addView(space(14));
        Button login = button("🔐 ربط حساب Facebook", Color.rgb(24,119,242));
        login.setOnClickListener(v -> startActivity(new Intent(this, FacebookLoginActivity.class)));
        root.addView(login, full());

        TextView loginHelp = text("سجّل الدخول داخل التطبيق مرة واحدة. التطبيق لا يطلب كلمة السر منك ولا يرفع جلسة Facebook إلى خادم خارجي.", 13, false);
        loginHelp.setPadding(dp(6),dp(8),dp(6),dp(12));
        root.addView(loginHelp);

        Button all = button("🔎 ابحث في الجميع", Color.rgb(12,122,101));
        all.setTextSize(18);
        all.setOnClickListener(v -> {
            String q = query();
            if (q.trim().isEmpty()) {
                Toast.makeText(this, "اكتب الشركة أو الموديل أولاً", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent i = new Intent(this, ResultsActivity.class);
            i.putExtra("query", q);
            startActivity(i);
        });
        root.addView(all, full());

        TextView note = text("في تبويب Facebook سيقرأ التطبيق تلقائيًا قائمة الكروبات التي أنت عضو فيها ثم يبحث في المنشورات التي يحق لحسابك رؤيتها. الفحص قد يستغرق وقتًا إذا كان حسابك مشتركًا في كروبات كثيرة.", 13, false);
        note.setPadding(dp(12),dp(14),dp(12),dp(14));
        note.setBackgroundColor(Color.rgb(236,250,245));
        root.addView(space(12));
        root.addView(note);

        restoreSearch();
        setContentView(scroll);
    }

    private String query() {
        saveSearch();
        ArrayList<String> q = new ArrayList<>();
        addIf(q, make.getText().toString());
        addIf(q, model.getText().toString());
        String yf = yearFrom.getText().toString().trim();
        String yt = yearTo.getText().toString().trim();
        if (!yf.isEmpty() && yf.equals(yt)) q.add(yf);
        else { addIf(q, yf); addIf(q, yt); }
        String c = String.valueOf(city.getSelectedItem());
        if (!"كل العراق".equals(c)) q.add(c);
        String p = priceTo.getText().toString().trim();
        if (!p.isEmpty()) q.add(p + " دولار");
        return String.join(" ", q);
    }

    private void addIf(ArrayList<String> q, String s) {
        s = s == null ? "" : s.trim();
        if (!s.isEmpty()) q.add(s);
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
        int c=prefs.getInt("city",0);
        if(c>=0 && c<city.getCount()) city.setSelection(c);
    }

    private TextView section(String s) { TextView t=text(s,19,true); t.setPadding(0,dp(4),0,dp(8)); return t; }
    private EditText input(String hint, boolean numeric) { EditText e=new EditText(this); e.setHint(hint); e.setTextSize(16); e.setPadding(dp(12),dp(10),dp(12),dp(10)); if(numeric)e.setInputType(InputType.TYPE_CLASS_NUMBER); e.setSingleLine(true); e.setLayoutParams(full()); return e; }
    private LinearLayout box(int o) { LinearLayout l=new LinearLayout(this); l.setOrientation(o); return l; }
    private TextView text(String s,int size,boolean bold){ TextView t=new TextView(this); t.setText(s); t.setTextSize(size); if(bold)t.setTypeface(null,1); return t; }
    private Button button(String s,int color){ Button b=new Button(this); b.setText(s); b.setTextColor(Color.WHITE); b.setBackgroundColor(color); b.setAllCaps(false); return b; }
    private View space(int h){ Space s=new Space(this); s.setLayoutParams(new LinearLayout.LayoutParams(1,dp(h))); return s; }
    private View spaceW(int w){ Space s=new Space(this); s.setLayoutParams(new LinearLayout.LayoutParams(dp(w),1)); return s; }
    private LinearLayout.LayoutParams full(){ return new LinearLayout.LayoutParams(-1,-2); }
    private LinearLayout.LayoutParams weight(){ return new LinearLayout.LayoutParams(0,-2,1f); }
    private int dp(int v){ return Math.round(v*getResources().getDisplayMetrics().density); }
}
