package iq.carfinder.app;

import android.accessibilityservice.AccessibilityService;
import android.os.*;
import android.content.*;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.*;

public class FacebookCaptureService extends AccessibilityService {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private long sessionStarted = 0L;
    private int scrolls = 0;
    private boolean scrollScheduled = false;

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;

        CharSequence pkg = event.getPackageName();
        if (pkg == null || !"com.facebook.katana".contentEquals(pkg)) return;

        SharedPreferences prefs = getSharedPreferences("carfinder_iq", MODE_PRIVATE);
        if (!prefs.getBoolean("fb_capture_active", false)) return;

        long started = prefs.getLong("fb_capture_started_at", 0L);
        if (started <= 0L) return;

        long age = System.currentTimeMillis() - started;
        if (age > 45000L) {
            prefs.edit().putBoolean("fb_capture_active", false).apply();
            return;
        }

        if (sessionStarted != started) {
            sessionStarted = started;
            scrolls = 0;
            scrollScheduled = false;
        }

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;

        String query = prefs.getString("fb_query", "");
        collectMatches(root, query, prefs);

        if (!scrollScheduled && scrolls < 18) {
            scrollScheduled = true;
            handler.postDelayed(() -> {
                scrollScheduled = false;
                AccessibilityNodeInfo r = getRootInActiveWindow();
                if (r != null && scrollOne(r)) {
                    scrolls++;
                } else {
                    scrolls++;
                }
            }, 1200L);
        }
    }

    private void collectMatches(AccessibilityNodeInfo root, String query, SharedPreferences prefs) {
        ArrayList<String> texts = new ArrayList<>();
        collectText(root, texts);

        LinkedHashSet<String> current = new LinkedHashSet<>(
            prefs.getStringSet("fb_results", new LinkedHashSet<>())
        );

        ArrayList<String> terms = importantTerms(query);

        for (int i=0;i<texts.size();i++) {
            StringBuilder b = new StringBuilder();
            for (int j=i;j<Math.min(texts.size(), i+5);j++) {
                String t = texts.get(j);
                if (t == null || t.isEmpty()) continue;
                if (b.length() > 0) b.append(" • ");
                b.append(t);
            }

            String block = clean(b.toString());
            if (block.length() < 25 || block.length() > 900) continue;
            if (!matches(block, terms)) continue;

            current.add(block);
            while (current.size() > 120) {
                Iterator<String> it = current.iterator();
                if (it.hasNext()) {
                    it.next();
                    it.remove();
                } else break;
            }
        }

        prefs.edit().putStringSet("fb_results", current).apply();
    }

    private void collectText(AccessibilityNodeInfo node, ArrayList<String> out) {
        if (node == null || out.size() > 400) return;

        CharSequence txt = node.getText();
        if (txt != null) {
            String s = clean(txt.toString());
            if (s.length() >= 2 && s.length() <= 350) out.add(s);
        }

        CharSequence desc = node.getContentDescription();
        if (desc != null) {
            String s = clean(desc.toString());
            if (s.length() >= 2 && s.length() <= 350 && !out.contains(s)) out.add(s);
        }

        for (int i=0;i<node.getChildCount();i++) {
            collectText(node.getChild(i), out);
        }
    }

    private ArrayList<String> importantTerms(String q) {
        ArrayList<String> out = new ArrayList<>();
        if (q == null) return out;

        String normalized = q.toLowerCase(Locale.ROOT)
            .replace("أقل من"," ")
            .replace("دولار"," ")
            .replace("العراق"," ");

        for (String p : normalized.split("\\s+")) {
            String s = p.trim();
            if (s.length() >= 3) out.add(s);
        }

        return out;
    }

    private boolean matches(String block, ArrayList<String> terms) {
        if (terms.isEmpty()) return true;
        String s = block.toLowerCase(Locale.ROOT);
        int hit = 0;
        for (String t : terms) {
            if (s.contains(t)) hit++;
        }
        return hit >= Math.min(2, terms.size());
    }

    private String clean(String s) {
        if (s == null) return "";
        return s.replaceAll("\\s+"," ").trim();
    }

    private boolean scrollOne(AccessibilityNodeInfo node) {
        if (node == null) return false;

        if (node.isScrollable()) {
            try {
                if (node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) return true;
            } catch (Exception ignored) {}
        }

        for (int i=0;i<node.getChildCount();i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null && scrollOne(child)) return true;
        }

        return false;
    }

    @Override public void onInterrupt() {}

    @Override public void onServiceConnected() {
        super.onServiceConnected();
    }
}
