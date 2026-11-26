package dev.ccnc.settings;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import dev.ccnc.common.Bg;
import dev.ccnc.common.Fonts;
import dev.ccnc.common.Palette;

/**
 * Every vehicle setting the unit exposes, in ccNC's style. The list isn't typed in by hand: it is
 * discovered from {@code com.hkmc.usm.USMManager} (framework, by reflection) -- each
 * {@code getGroup_Setting()} is a row, grouped by its prefix -- so it follows the firmware and covers
 * lights, doors, seats, cluster, HUD, convenience, eco/EV, digital key and the rest (about 300).
 * Values are shown as the raw code the car reports (they are multi-state and differ per setting) and a
 * row says whether the car also accepts a change. The stock Vehicle Settings screen makes the changes.
 */
public final class VehicleSettingsActivity extends Activity {

    private static final String TAG = "ccncVehicle";

    /** Friendly names for the USM group prefixes. */
    private static final Map<String, String> GROUPS = new LinkedHashMap<String, String>();
    static {
        GROUPS.put("Lights", "Lights");
        GROUPS.put("DualLight", "Dual lights");
        GROUPS.put("Door", "Doors and trunk");
        GROUPS.put("Convenience", "Convenience");
        GROUPS.put("FrontSeatHeat", "Front seat heating");
        GROUPS.put("RearSeatHeat", "Rear seat heating");
        GROUPS.put("SeatBackRecline", "Seat back recline");
        GROUPS.put("AllSeatMoving", "Seat positions");
        GROUPS.put("SmartIMS20", "Seat memory");
        GROUPS.put("Ergo", "Ergo motion");
        GROUPS.put("Cluster", "Instrument cluster");
        GROUPS.put("HUD", "Head-up display");
        GROUPS.put("CameraMonitorSystem", "Cameras");
        GROUPS.put("SWRC", "Steering wheel buttons");
        GROUPS.put("EcoVehicle", "Electric vehicle");
        GROUPS.put("DriveMode", "Drive mode");
        GROUPS.put("DigitalKey2", "Digital key");
        GROUPS.put("UserProfile", "User profile");
        GROUPS.put("Assistance", "Driver assistance (all)");
    }

    private static final class Row {
        final String name, getter;
        final boolean writable;
        Row(String name, String getter, boolean writable) { this.name = name; this.getter = getter; this.writable = writable; }
    }

    private Object usm;
    private final Map<String, List<Row>> byGroup = new TreeMap<String, List<Row>>();
    private final List<String> order = new ArrayList<String>();
    private LinearLayout rail;
    private FrameLayout pane;
    private final Handler ui = new Handler();

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        dev.ccnc.common.Themes.apply(this);
        discover();
        if (getIntent().getBooleanExtra("dump", false)) { // verification run: log every setting, then close
            new Thread(new Runnable() {
                @Override public void run() {
                    for (String g : order) for (Row r : byGroup.get(g)) {
                        int v = read(r.getter);
                        Log.i(TAG, g + "." + r.getter + "=" + (v == Integer.MIN_VALUE ? "n/a" : v) + (r.writable ? " (settable)" : ""));
                    }
                    Log.i(TAG, "dump done: " + countAll() + " settings");
                    ui.post(new Runnable() { @Override public void run() { finish(); } });
                }
            }).start();
        }

        FrameLayout root = new FrameLayout(this);
        root.addView(new Bg(this), new FrameLayout.LayoutParams(-1, -1));
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        root.addView(content, new FrameLayout.LayoutParams(-1, -1));
        content.addView(topBar());
        LinearLayout main = new LinearLayout(this);
        main.setOrientation(LinearLayout.HORIZONTAL);
        content.addView(main, new LinearLayout.LayoutParams(-1, 0, 1f));
        rail = new LinearLayout(this);
        rail.setOrientation(LinearLayout.VERTICAL);
        ScrollView rs = new ScrollView(this);
        rs.setVerticalScrollBarEnabled(false);
        rs.addView(rail);
        main.addView(rs, new LinearLayout.LayoutParams(Palette.px(this, 270), -1));
        View div = new View(this);
        div.setBackgroundColor(Palette.DIVIDER);
        main.addView(div, new LinearLayout.LayoutParams(1, -1));
        pane = new FrameLayout(this);
        main.addView(pane, new LinearLayout.LayoutParams(0, -1, 1f));
        setContentView(root);

        if (order.isEmpty()) {
            pane.addView(text("The vehicle settings service isn't available on this unit", 20f, Palette.SUBTEXT, Fonts.REGULAR),
                    new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
            return;
        }
        for (int i = 0; i < order.size(); i++) rail.addView(railRow(i));
        String want = getIntent().getStringExtra("group");
        int start = 0;
        for (int i = 0; i < order.size(); i++) if (order.get(i).equals(want)) start = i;
        show(start);
    }

    /** Finds every getX_Y() on USMManager and notes whether setX_Y(int) exists. */
    private void discover() {
        try {
            Class<?> c = Class.forName("com.hkmc.usm.USMManager");
            usm = c.getConstructor(Context.class).newInstance(this);
            java.util.Set<String> setters = new java.util.HashSet<String>();
            for (Method m : c.getMethods()) if (m.getName().startsWith("set") && m.getParameterTypes().length == 1) setters.add(m.getName().substring(3));
            Map<String, List<Row>> tmp = new TreeMap<String, List<Row>>();
            for (Method m : c.getMethods()) {
                String n = m.getName();
                if (!n.startsWith("get") || m.getParameterTypes().length != 0 || m.getReturnType() != int.class) continue;
                String core = n.substring(3);
                int us = core.indexOf('_');
                if (us <= 0) continue;
                String group = core.substring(0, us);
                List<Row> l = tmp.get(group);
                if (l == null) { l = new ArrayList<Row>(); tmp.put(group, l); }
                l.add(new Row(humanize(core.substring(us + 1)), n, setters.contains(core)));
            }
            for (String g : GROUPS.keySet()) if (tmp.containsKey(g)) { order.add(g); byGroup.put(g, sorted(tmp.get(g))); }
            for (String g : tmp.keySet()) if (!GROUPS.containsKey(g)) { GROUPS.put(g, humanize(g)); order.add(g); byGroup.put(g, sorted(tmp.get(g))); }
            Log.i(TAG, "discovered " + countAll() + " settings in " + order.size() + " groups");
        } catch (Throwable t) {
            Log.i(TAG, "USM service unavailable: " + t);
            if (!hasUsm()) demo(); // off the car: a sample of real setting names so the page can be previewed
        }
    }

    private static boolean hasUsm() {
        try { Class.forName("com.hkmc.usm.USMManager"); return true; } catch (Throwable t) { return false; }
    }

    private void demo() {
        String[][] d = {
            {"Lights", "Ambient light depends on speed", "Headlamp delay", "Welcome light", "Dual light speed", "Dashboard brightness"},
            {"Door", "Power trunk speed", "Trunk opening height warning", "Trunk opening speed warning", "Auto lock", "Auto unlock on exit"},
            {"Convenience", "Welcome mirror", "Seat easy access", "Wireless charging", "Rear occupant alert", "Smart key"},
            {"Cluster", "Icy road warning", "Speed odometer subscale", "Theme", "Digital speedometer", "Driving info"},
            {"HUD", "Speed", "Speed color", "Speed number size", "Lane safety info", "Rotation", "Height"},
            {"EcoVehicle", "I-Pedal", "Smart regeneration", "Coasting guide", "Green zone drive", "Tow weight", "V2L mode"},
        };
        for (String[] g : d) {
            List<Row> l = new ArrayList<Row>();
            for (int i = 1; i < g.length; i++) l.add(new Row(g[i], "demo" + i, i % 2 == 0));
            order.add(g[0]);
            byGroup.put(g[0], l);
        }
    }

    private int countAll() { int n = 0; for (List<Row> l : byGroup.values()) n += l.size(); return n; }

    private static List<Row> sorted(List<Row> l) {
        java.util.Collections.sort(l, new java.util.Comparator<Row>() {
            @Override public int compare(Row a, Row b) { return a.name.compareToIgnoreCase(b.name); }
        });
        return l;
    }

    /** "BlindSpotSafety_ViewNew" style names to words: "Blind spot safety view new". */
    private static String humanize(String s) {
        String w = s.replace('_', ' ').replaceAll("([a-z0-9])([A-Z])", "$1 $2").replaceAll("([A-Z]+)([A-Z][a-z])", "$1 $2").trim();
        w = w.replaceAll("\\s+", " ");
        return w.isEmpty() ? s : Ui.titleCase(Character.toUpperCase(w.charAt(0)) + w.substring(1));
    }

    private View railRow(final int i) {
        TextView t = text(GROUPS.get(order.get(i)), 17f, Palette.SUBTEXT, Fonts.REGULAR);
        t.setPadding(Palette.px(this, 24), 0, Palette.px(this, 12), 0);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setLayoutParams(new LinearLayout.LayoutParams(-1, Palette.px(this, 50)));
        t.setTag(i);
        t.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { show(i); } });
        return t;
    }

    private void show(int i) {
        for (int c = 0; c < rail.getChildCount(); c++) {
            TextView t = (TextView) rail.getChildAt(c);
            boolean on = (Integer) t.getTag() == i;
            t.setTextColor(on ? Palette.TEXT : Palette.SUBTEXT);
            t.setTypeface(Fonts.get(this, on ? Fonts.SEMIBOLD : Fonts.REGULAR));
            t.setBackgroundColor(on ? Palette.alpha(Palette.ACCENT, 0x33) : 0);
        }
        pane.removeAllViews();
        final String group = order.get(i);
        final List<Row> rows = byGroup.get(group);
        final LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        int pad = Palette.px(this, 26);
        col.setPadding(pad, Palette.px(this, 26), pad, pad);
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.addView(col);
        pane.addView(sv);
        col.addView(text(GROUPS.get(group), 24f, Palette.TEXT, Fonts.SEMIBOLD));
        col.addView(text(rows.size() + " settings · values are the car's raw codes", 13f, Palette.HINT, Fonts.REGULAR));
        final TextView[] values = new TextView[rows.size()];
        for (int r = 0; r < rows.size(); r++) {
            Row row = rows.get(r);
            LinearLayout line = new LinearLayout(this);
            line.setOrientation(LinearLayout.HORIZONTAL);
            line.setGravity(Gravity.CENTER_VERTICAL);
            line.setPadding(Palette.px(this, 18), Palette.px(this, 10), Palette.px(this, 18), Palette.px(this, 10));
            LinearLayout t = new LinearLayout(this);
            t.setOrientation(LinearLayout.VERTICAL);
            t.addView(text(row.name, 16f, Palette.TEXT, Fonts.SEMIBOLD));
            t.addView(text(row.writable ? "Can be changed in vehicle settings" : "Read only", 12f, Palette.HINT, Fonts.REGULAR));
            line.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
            values[r] = text("…", 15f, Palette.SUBTEXT, Fonts.REGULAR);
            line.addView(values[r]);
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setCornerRadius(Palette.px(this, 14));
            bg.setColor(Palette.CARD);
            line.setBackground(bg);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = Palette.px(this, 8);
            col.addView(line, lp);
            line.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { openStock(); } });
        }
        // Read the values off the UI thread: a few hundred binder calls.
        new Thread(new Runnable() {
            @Override public void run() {
                final int[] v = new int[rows.size()];
                for (int r = 0; r < v.length; r++) { v[r] = read(rows.get(r).getter); Log.i(TAG, group + "." + rows.get(r).getter + "=" + (v[r] == Integer.MIN_VALUE ? "n/a" : v[r])); }
                ui.post(new Runnable() {
                    @Override public void run() {
                        for (int r = 0; r < v.length; r++) values[r].setText(v[r] == Integer.MIN_VALUE ? "—" : "code " + v[r]);
                    }
                });
            }
        }).start();
    }

    private int read(String getter) {
        if (getter.startsWith("demo")) return Integer.parseInt(getter.substring(4)) % 3;
        try { return ((Number) usm.getClass().getMethod(getter).invoke(usm)).intValue(); }
        catch (Throwable t) { return Integer.MIN_VALUE; }
    }

    /** ccNC Setup's Vehicle category (the stock Vehicle Settings screen is no longer opened). */
    private void openStock() {
        Ui.openSetup(this, "com.android.vehiclesettings.VehicleSetting");
    }

    private View topBar() {
        LinearLayout bar = dev.ccnc.common.Chrome.topBar(this, "Vehicle Settings");
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
