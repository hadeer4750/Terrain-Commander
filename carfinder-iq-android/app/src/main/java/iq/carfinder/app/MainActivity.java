package iq.carfinder.app;

import android.app.*;
import android.os.Bundle;
import android.content.*;
import android.graphics.Color;
import android.net.Uri;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;

public class MainActivity extends Activity {
    private EditText make, model, yearFrom, yearTo, priceTo;
    private Spinner city;
    private LinearLayout groupsBox;
    private SharedPreferences prefs;
    private final ArrayList<String> groups = new ArrayList<>();

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("carfinder_iq", MODE_PRIVATE);
        loadGroups();

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = box(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(18), dp(16), dp(28));
        scroll.addView(root);

        TextView title = text("🚘 CarFinder IQ", 25, true);
        title.setTextColor(Color.rgb(12,122,101));
        root.addView(title);
        root.addView(text("بحث السيارات في العراق + مجموعات فيسبوك", 14, false));
        root.addView(space(12));

        TextView note = text("✅ تم إلغاء عدّاد المسافة نهائيًا. التطبيق لا يخزن كلمة مرور فيسبوك، ويستخدم حسابك المسجّل على الهاتف لفتح البحث داخل الكروبات.", 14, false);
        note.setPadding(dp(12),dp(12),dp(12),dp(12));
        note.setBackgroundColor(Color.rgb(236,250,245));
        root.addView(note);
        root.addView(space(14));

        root.addView(section("مواصفات السيارة"));
        make = input("الشركة - مثال GMC", false); root.addView(make);
        model = input("الموديل - مثال Terrain", false); root.addView(model);

        LinearLayout years = box(LinearLayout.HORIZONTAL);
        yearFrom = input("السنة من", true); yearTo = input("السنة إلى", true);
        years.addView(yearFrom, weight()); years.addView(spaceW(8)); years.addView(yearTo, weight());
        root.addView(years);

        priceTo = input("أعلى سعر بالدولار - اختياري", true); root.addView(priceTo);
        city = new Spinner(this);
        String[] cities = {"كل العراق","البصرة","بغداد","النجف","كربلاء","أربيل","السليمانية","ذي قار","ميسان","واسط"};
        city.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, cities));
        root.addView(city, full());

        Button iq = button("🔎 ابحث في iQ Cars", Color.rgb(12,122,101));
        iq.setOnClickListener(v -> searchSite("iqcars.net"));
        root.addView(iq, full());

        Button os = button("🔎 ابحث في السوق المفتوح", Color.rgb(36,107,253));
        os.setOnClickListener(v -> searchSite("iq.opensooq.com"));
        root.addView(os, full());

        root.addView(space(18));
        root.addView(section("👥 كروبات فيسبوك"));

        TextView fbHelp = text("أضف رابط كل كروب أنت مشترك به مرة واحدة. سيظهر زر بحث مباشر لكل كروب باستخدام مواصفات السيارة أعلاه.", 14, false);
        root.addView(fbHelp);

        Button add = button("➕ إضافة كروب فيسبوك", Color.rgb(36,107,253));
        add.setOnClickListener(v -> addGroupDialog());
        root.addView(add, full());

        Button copy = button("📋 نسخ كلمات البحث", Color.rgb(90,105,125));
        copy.setOnClickListener(v -> copyQuery());
        root.addView(copy, full());

        groupsBox = box(LinearLayout.VERTICAL);
        root.addView(groupsBox, full());
        renderGroups();

        root.addView(space(14));
        TextView warning = text("مهم: فيسبوك لا يتيح للتطبيق قراءة قائمة كل الكروبات الخاصة بحسابك تلقائيًا. أضف روابط الكروبات مرة واحدة، ثم البحث يعمل داخل ما تستطيع أنت رؤيته.", 13, false);
        warning.setPadding(dp(12),dp(12),dp(12),dp(12));
        warning.setBackgroundColor(Color.rgb(255,248,223));
        root.addView(warning);

        restoreSearch();
        setContentView(scroll);
    }

    private TextView section(String s) {
        TextView t = text(s, 19, true);
        t.setPadding(0, dp(4), 0, dp(8));
        return t;
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

    private void searchSite(String domain) {
        String q = "site:" + domain + " " + query();
        open("https://www.google.com/search?q=" + enc(q));
    }

    private void copyQuery() {
        String q = query();
        ClipboardManager cm = (ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("CarFinder IQ", q));
        Toast.makeText(this, "تم نسخ: " + q, Toast.LENGTH_SHORT).show();
    }

    private void addGroupDialog() {
        LinearLayout body = box(LinearLayout.VERTICAL);
        body.setPadding(dp(18),0,dp(18),0);
        EditText name = input("اسم الكروب - اختياري", false);
        EditText url = input("رابط الكروب", false);
        body.addView(name); body.addView(url);

        new AlertDialog.Builder(this)
            .setTitle("إضافة كروب فيسبوك")
            .setView(body)
            .setPositiveButton("حفظ", (d,w) -> {
                String u = normalizeGroup(url.getText().toString());
                if (u == null) {
                    Toast.makeText(this, "رابط الكروب غير صحيح", Toast.LENGTH_LONG).show();
                    return;
                }
                String n = name.getText().toString().trim();
                if (n.isEmpty()) n = "كروب فيسبوك";
                String item = n + "|||" + u;
                if (!groups.contains(item)) groups.add(item);
                saveGroups(); renderGroups();
            })
            .setNegativeButton("إلغاء", null)
            .show();
    }

    private String normalizeGroup(String raw) {
        if (raw == null) return null;
        String u = raw.trim();
        if (!u.startsWith("http")) u = "https://" + u;
        Matcher m = Pattern.compile("facebook\\.com/groups/([^/?#]+)", Pattern.CASE_INSENSITIVE).matcher(u);
        if (!m.find()) return null;
        return "https://www.facebook.com/groups/" + m.group(1);
    }

    private void renderGroups() {
        groupsBox.removeAllViews();
        if (groups.isEmpty()) {
            TextView empty = text("لم تضف أي كروب بعد.", 14, false);
            empty.setPadding(0,dp(12),0,dp(12));
            groupsBox.addView(empty);
            return;
        }
        for (int i=0;i<groups.size();i++) {
            final int idx=i;
            String[] p=groups.get(i).split("\\|\\|\\|",2);
            String n=p[0], u=p.length>1?p[1]:"";

            LinearLayout card=box(LinearLayout.VERTICAL);
            card.setPadding(dp(10),dp(10),dp(10),dp(10));
            card.setBackgroundColor(Color.rgb(247,250,253));
            TextView name=text(n,16,true); card.addView(name);
            TextView link=text(u,11,false); card.addView(link);

            LinearLayout row=box(LinearLayout.HORIZONTAL);
            Button search=button("🔎 بحث داخل الكروب", Color.rgb(36,107,253));
            search.setOnClickListener(v -> open(u + "/search/?q=" + enc(query())));
            Button del=button("حذف", Color.rgb(185,55,55));
            del.setOnClickListener(v -> { groups.remove(idx); saveGroups(); renderGroups(); });
            row.addView(search, weight()); row.addView(spaceW(8)); row.addView(del);
            card.addView(row);
            groupsBox.addView(card, full());
            groupsBox.addView(space(8));
        }
    }

    private void open(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Toast.makeText(this, "تعذر فتح الرابط", Toast.LENGTH_SHORT).show();
        }
    }

    private String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private void loadGroups() {
        groups.clear();
        groups.addAll(prefs.getStringSet("groups", new LinkedHashSet<>()));
    }

    private void saveGroups() {
        prefs.edit().putStringSet("groups", new LinkedHashSet<>(groups)).apply();
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

    private LinearLayout box(int o) { LinearLayout l=new LinearLayout(this); l.setOrientation(o); return l; }
    private TextView text(String s,int size,boolean bold){ TextView t=new TextView(this); t.setText(s); t.setTextSize(size); if(bold)t.setTypeface(null,1); return t; }
    private Button button(String s,int color){ Button b=new Button(this); b.setText(s); b.setTextColor(Color.WHITE); b.setBackgroundColor(color); b.setAllCaps(false); return b; }
    private View space(int h){ Space s=new Space(this); s.setLayoutParams(new LinearLayout.LayoutParams(1,dp(h))); return s; }
    private View spaceW(int w){ Space s=new Space(this); s.setLayoutParams(new LinearLayout.LayoutParams(dp(w),1)); return s; }
    private LinearLayout.LayoutParams full(){ return new LinearLayout.LayoutParams(-1,-2); }
    private LinearLayout.LayoutParams weight(){ return new LinearLayout.LayoutParams(0,-2,1f); }
    private int dp(int v){ return Math.round(v*getResources().getDisplayMetrics().density); }
}
