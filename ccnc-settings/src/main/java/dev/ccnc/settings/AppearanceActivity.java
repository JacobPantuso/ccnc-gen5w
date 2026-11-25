package dev.ccnc.settings;

import android.app.Activity;
import android.content.ContentValues;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import dev.ccnc.common.Bg;
import dev.ccnc.common.Fonts;
import dev.ccnc.common.Palette;
import dev.ccnc.common.Themes;

/**
 * ccNC themes and accent colour. The choice is stored by ccNC Home (its theme provider accepts updates)
 * and every ccNC app follows it the next time it opens.
 */
public final class AppearanceActivity extends Activity {

    private static final Uri URI = Uri.parse("content://dev.ccnc.home.theme/theme");

    private LinearLayout themeBox, accentBox;
    private String themeId = "nebula";
    private int accent;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        Themes.apply(this);
        read();

        FrameLayout root = new FrameLayout(this);
        root.addView(new Bg(this), new FrameLayout.LayoutParams(-1, -1));
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        root.addView(content, new FrameLayout.LayoutParams(-1, -1));
        content.addView(topBar());
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        int pad = Palette.px(this, 28);
        col.setPadding(pad, Palette.px(this, 20), pad, pad);
        sv.addView(col);
        content.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));

        col.addView(text("Theme", 22f, Palette.TEXT, Fonts.SEMIBOLD));
        themeBox = new LinearLayout(this);
        themeBox.setOrientation(LinearLayout.VERTICAL);
        col.addView(themeBox);
        TextView at = text("Accent colour", 22f, Palette.TEXT, Fonts.SEMIBOLD);
        at.setPadding(0, Palette.px(this, 22), 0, Palette.px(this, 8));
        col.addView(at);
        accentBox = new LinearLayout(this);
        accentBox.setOrientation(LinearLayout.VERTICAL);
        col.addView(accentBox);
        col.addView(text("Apps that are already open change when you open them again.", 13f, Palette.HINT, Fonts.REGULAR));
        setContentView(root);
        build();
    }

    private void read() {
        android.database.Cursor c = null;
        try {
            c = getContentResolver().query(URI, null, null, null, null);
            if (c != null && c.moveToFirst()) { themeId = c.getString(0); accent = c.getInt(1); }
        } catch (Throwable ignored) {
        } finally {
            if (c != null) c.close();
        }
    }

    private void save() {
        try {
            ContentValues v = new ContentValues();
            v.put("theme", themeId);
            v.put("accent", accent);
            getContentResolver().update(URI, v, null, null);
        } catch (Throwable ignored) {
        }
        Themes.apply(this);
        recreate();
    }

    private void build() {
        themeBox.removeAllViews();
        accentBox.removeAllViews();
        // Themes, four to a row.
        java.util.List<Themes.T> all = new java.util.ArrayList<Themes.T>(java.util.Arrays.asList(Themes.ALL));
        LinearLayout row = null;
        for (int i = -1; i < all.size(); i++) { // -1 = Auto
            if (row == null || row.getChildCount() == 4) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                themeBox.addView(row, new LinearLayout.LayoutParams(-1, -2));
            }
            final String id = i < 0 ? Themes.AUTO : all.get(i).id;
            String name = i < 0 ? "Auto" : all.get(i).name;
            row.addView(swatch(name, i < 0 ? Themes.NEBULA : all.get(i), id.equals(themeId)) , tile());
            row.getChildAt(row.getChildCount() - 1).setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { themeId = id; save(); }
            });
        }
        row = null;
        for (int i = 0; i < Themes.ACCENTS.length; i++) {
            if (row == null || row.getChildCount() == 5) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                accentBox.addView(row, new LinearLayout.LayoutParams(-1, -2));
            }
            final int color = Themes.ACCENTS[i];
            View d = dot(color == 0 ? Themes.find(themeId).accentColor() : color, Themes.ACCENT_NAMES[i], color == accent);
            row.addView(d, tile());
            d.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { accent = color; save(); } });
        }
    }

    private LinearLayout.LayoutParams tile() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Palette.px(this, 78), 1f);
        lp.setMargins(Palette.px(this, 5), Palette.px(this, 5), Palette.px(this, 5), Palette.px(this, 5));
        return lp;
    }

    private View swatch(String name, final Themes.T t, boolean on) {
        TextView v = text(name, 15f, t.light ? 0xFF16212A : 0xFFFFFFFF, on ? Fonts.SEMIBOLD : Fonts.REGULAR);
        v.setGravity(Gravity.CENTER);
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR, new int[]{t.bgColor(0), t.bgColor(2)});
        g.setCornerRadius(Palette.px(this, 14));
        g.setStroke(Palette.px(this, on ? 3 : 1), on ? Palette.ACCENT : Palette.BORDER);
        v.setBackground(g);
        return v;
    }

    private View dot(int color, String name, boolean on) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setGravity(Gravity.CENTER);
        View c = new View(this);
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        g.setColor(color);
        g.setStroke(Palette.px(this, on ? 3 : 1), on ? Palette.TEXT : Palette.BORDER);
        c.setBackground(g);
        l.addView(c, new LinearLayout.LayoutParams(Palette.px(this, 34), Palette.px(this, 34)));
        TextView t = text(name, 12f, on ? Palette.TEXT : Palette.SUBTEXT, Fonts.REGULAR);
        t.setGravity(Gravity.CENTER);
        l.addView(t);
        return l;
    }

    private View topBar() {
        LinearLayout bar = dev.ccnc.common.Chrome.topBar(this, "Appearance");
        bar.setLayoutParams(new LinearLayout.LayoutParams(-1, Palette.px(this, 56)));
        return bar;
    }

    private TextView text(String t, float size, int color, int face) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextSize(size);
        v.setTextColor(color);
        v.setTypeface(Fonts.get(this, face));
        return v;
    }
}
