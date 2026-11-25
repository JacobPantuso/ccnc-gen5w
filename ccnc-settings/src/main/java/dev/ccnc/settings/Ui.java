package dev.ccnc.settings;

import android.app.Activity;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import dev.ccnc.common.Bg;
import dev.ccnc.common.Fonts;
import dev.ccnc.common.Palette;

/** Small builders shared by the native settings pages (page frame, section titles, rows). */
final class Ui {
    private Ui() {}

    static TextView text(Activity a, String t, float size, int color, int face) {
        TextView v = new TextView(a);
        v.setText(t);
        v.setTextSize(size);
        v.setTextColor(color);
        v.setTypeface(Fonts.get(a, face));
        return v;
    }

    /** Sets up the themed page with a title bar and returns the scrolling column to add rows to. */
    static LinearLayout page(final Activity a, String title) {
        dev.ccnc.common.Themes.apply(a);
        FrameLayout root = new FrameLayout(a);
        root.addView(new Bg(a), new FrameLayout.LayoutParams(-1, -1));
        LinearLayout content = new LinearLayout(a);
        content.setOrientation(LinearLayout.VERTICAL);
        root.addView(content, new FrameLayout.LayoutParams(-1, -1));

        content.addView(dev.ccnc.common.Chrome.topBar(a, titleCase(title)));

        ScrollView sv = new ScrollView(a);
        sv.setVerticalScrollBarEnabled(false);
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        int p = Palette.px(a, 28);
        col.setPadding(p, Palette.px(a, 20), p, p);
        sv.addView(col);
        content.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        a.setContentView(root);
        return col;
    }

    /**
     * Static descriptions under settings. Ours were written by us, not taken from ccNC, and ccNC's
     * wording isn't in the dump (only identifiers), so they stay hidden until the real text is supplied.
     */
    static final boolean DESCRIPTIONS = false;

    private static final java.util.Set<String> SMALL = new java.util.HashSet<String>(java.util.Arrays.asList(
            "a", "an", "and", "as", "at", "by", "for", "in", "of", "on", "or", "the", "to", "vs"));

    /** "Forward safety" -> "Forward Safety": ccNC capitalises every word of a title except small joining words. */
    static String titleCase(String s) {
        if (s == null || s.isEmpty()) return s;
        String[] w = s.split(" ", -1);
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < w.length; i++) {
            String x = w[i];
            if (i > 0) b.append(' ');
            if (x.isEmpty()) continue;
            boolean small = i > 0 && SMALL.contains(x.toLowerCase(java.util.Locale.US));
            int at = 0;
            while (at < x.length() && !Character.isLetter(x.charAt(at))) at++; // "(Beta)", "1st"
            if (small || at >= x.length() || Character.isDigit(x.charAt(0))) b.append(x);
            else b.append(x, 0, at).append(Character.toUpperCase(x.charAt(at))).append(x.substring(at + 1));
            // hyphenated: "Blind-spot" -> "Blind-Spot"
            int h = b.lastIndexOf("-");
            if (h > b.length() - x.length() - 1 && h >= 0 && h + 1 < b.length() && Character.isLowerCase(b.charAt(h + 1)))
                b.setCharAt(h + 1, Character.toUpperCase(b.charAt(h + 1)));
        }
        return b.toString();
    }

    static void section(Activity a, LinearLayout col, String title) {
        TextView t = text(a, titleCase(title), 20f, Palette.TEXT, Fonts.SEMIBOLD);
        t.setPadding(0, Palette.px(a, 16), 0, Palette.px(a, 4));
        col.addView(t);
    }

    static void note(Activity a, LinearLayout col, String s) {
        col.addView(text(a, s, 13f, Palette.HINT, Fonts.REGULAR));
    }

    /** A card row: title, optional subtitle, optional right-hand value; clickable when {@code click} is set. */
    static LinearLayout row(Activity a, String title, String sub, String value, View.OnClickListener click) {
        LinearLayout r = new LinearLayout(a);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(Palette.px(a, 18), Palette.px(a, 12), Palette.px(a, 18), Palette.px(a, 12));
        LinearLayout t = new LinearLayout(a);
        t.setOrientation(LinearLayout.VERTICAL);
        t.addView(text(a, titleCase(title), 17f, Palette.TEXT, Fonts.SEMIBOLD));
        if (sub != null && !sub.isEmpty()) t.addView(text(a, sub, 13f, Palette.HINT, Fonts.REGULAR));
        r.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        if (value != null) r.addView(text(a, value, 15f, Palette.SUBTEXT, Fonts.REGULAR));
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(Palette.px(a, 14));
        g.setColor(Palette.CARD);
        r.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Palette.px(a, 8);
        r.setLayoutParams(lp);
        if (click != null) r.setOnClickListener(click);
        return r;
    }

    static void openStock(Activity a, String pkg, String cls, String what) {
        try {
            a.startActivity(new android.content.Intent(android.content.Intent.ACTION_MAIN)
                    .setComponent(new android.content.ComponentName(pkg, cls)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Throwable t) {
            android.widget.Toast.makeText(a, what + " isn't available", android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    /** Opens the car's original home screen (Launcher_WP3), without a chooser. */
    static void openStockLauncher(Activity a) {
        android.content.Intent i = new android.content.Intent(android.content.Intent.ACTION_MAIN)
                .addCategory(android.content.Intent.CATEGORY_HOME).setPackage("com.android.launcher3")
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            if (a.getPackageManager().resolveActivity(i, 0) == null) {
                i = a.getPackageManager().getLaunchIntentForPackage("com.android.launcher3");
                if (i == null) { android.widget.Toast.makeText(a, "The stock launcher isn't available", android.widget.Toast.LENGTH_SHORT).show(); return; }
            }
            a.startActivity(i);
        } catch (Throwable t) {
            android.widget.Toast.makeText(a, "The stock launcher isn't available", android.widget.Toast.LENGTH_SHORT).show();
        }
    }
}
