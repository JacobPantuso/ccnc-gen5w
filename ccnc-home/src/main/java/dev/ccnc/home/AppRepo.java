package dev.ccnc.home;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.TextUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Loads the head unit's launchable apps and turns them into home cards. */
final class AppRepo {

    static final String STOCK_LAUNCHER = "com.android.launcher3";

    static final class Entry {
        final ComponentName component;
        final String label;
        final Icons.Kind kind;
        final Drawable appIcon;
        /** The card stacked behind this one (a stack holds two cards), or null. */
        Entry stackBack;
        /** ccNC artwork icon; drawn in preference to {@link #appIcon} and the line icon. */
        android.graphics.Bitmap art;
        String subtitle;
        /** When set, the card sends this broadcast instead of starting {@link #component}. */
        Intent broadcast;
        /** A hidden app, shown greyed with a crossed-out eye after the other apps in edit mode; a tap shows it again. */
        boolean hiddenApp;

        Entry(ComponentName component, String label, Icons.Kind kind, Drawable appIcon) {
            this.component = component;
            this.label = label;
            this.kind = kind;
            this.appIcon = appIcon;
        }

        String key() { return component.flattenToShortString(); }
    }

    private final Context ctx;
    private final SharedPreferences prefs;

    AppRepo(Context ctx) {
        this.ctx = ctx;
        this.prefs = ctx.getSharedPreferences("home", Context.MODE_PRIVATE);
    }

    /** Head-unit app list for previewing off the car ({@code am start ... --ez demo true}). */
    static List<Entry> demo(Context ctx) {
        String[][] d = {
                {"com.hkmc.phoneprojection", "Android Auto", ""},
                {"com.hkmc.bluetooth.phone", "Phone", "Fold 7"},
                {"com.hkmc.voicememo", "Voice Memo", ""},
                {"com.hkmc.telematics.weather", "Weather", "Few Clouds\n54\u00b0F"},
                {"com.android.settings", "Setup", "Updated 10-16-2026"},
                {"com.mobis.software.navigation", "Navigation", ""},
                {"com.hkmc.av.app.radiona", "Radio", ""},
                {"com.hkmc.usbmusic", "USB Music", ""},
                {"com.hkmc.telematics.service.apps.climate", "Climate", ""},
                {"com.hkmc.daudio.app.ev3", "EV", ""},
                {"com.hkmc.software.user.profile", "Profile", ""},
        };
        List<Entry> out = new ArrayList<Entry>();
        for (String[] r : d) {
            Entry e = new Entry(new ComponentName(r[0], r[0] + ".Main"), r[1], classify(r[0], "", r[1]), null);
            e.art = dev.ccnc.common.Art.icon(ctx, e.kind == Icons.Kind.PROJECTION ? "pp" : Curated.iconFor(e.kind));
            e.subtitle = r[2].replace("\\n", "\n");
            out.add(e);
        }
        return out;
    }

    /** Every launchable app, in saved order, hidden ones included when {@code withHidden}. */
    List<Entry> load(boolean withHidden) {
        PackageManager pm = ctx.getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> infos = pm.queryIntentActivities(main, 0);
        Set<String> hidden = hidden();
        Projection.State proj = Projection.read(ctx);
        List<Entry> out = new ArrayList<Entry>();
        for (ResolveInfo ri : infos) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(ctx.getPackageName()) || pkg.equals(STOCK_LAUNCHER)) continue;
            ComponentName cn = new ComponentName(pkg, ri.activityInfo.name);
            String key = cn.flattenToShortString();
            if (Curated.SKIP.contains(key)) continue;
            // Only the projection that's connected (both if the mode service can't be read).
            if (!proj.shows(pkg)) continue;
            if (!withHidden && hidden.contains(key)) continue;
            Curated.Card card = Curated.get(key);
            String label;
            Icons.Kind kind;
            String art;
            if (card != null) {
                label = card.label;
                kind = card.kind;
                art = card.icon;
            } else {
                CharSequence l = ri.loadLabel(pm);
                label = l == null || l.toString().trim().isEmpty() ? ri.activityInfo.name : l.toString().trim();
                if (label.startsWith("HKMC_")) label = label.substring(5);
                kind = classify(pkg, ri.activityInfo.name, label);
                art = Curated.iconFor(kind);
            }
            // App icons for unknown apps, and the real CarPlay / Android Auto logos from the unit.
            Drawable icon = null;
            if (kind == Icons.Kind.PROJECTION) icon = Projection.logo(ctx, key);
            if (icon == null && (kind == Icons.Kind.APP || kind == Icons.Kind.PROJECTION)) icon = ri.loadIcon(pm);
            Entry e = new Entry(cn, label, kind, icon);
            // One icon set on every card: apps without a ccNC icon get ccNC's generic one, not their own icon.
            e.art = dev.ccnc.common.Art.icon(ctx, art != null ? art : kind == Icons.Kind.PROJECTION ? null : "themelayout");
            if (kind == Icons.Kind.SETTINGS && Build.TIME > 0 && pkg.equals("com.android.settings")) {
                e.subtitle = "Updated " + new SimpleDateFormat("MM-dd-yyyy", Locale.US).format(new Date(Build.TIME));
            }
            out.add(e);
        }
        addHiddenLaunchers(pm, hidden, withHidden, out);
        addWeather(pm, hidden, withHidden, out);
        if (proj.none()) addProjectionPlaceholder(pm, hidden, withHidden, out);
        sort(out);
        return out;
    }

    /** Apps the stock launcher only shows through HMG-API's hooks: they have no LAUNCHER category. */
    static final String[][] HOOKED_APPS = {
            {"hmgapi.app", "hmgapi.app.MainActivity"},
            {"com.nemesis.manager", "com.nemesis.manager.MainActivity"},
    };

    /**
     * HMG-API, NEMESIS Manager and other mod apps: on the stock home screen HMG-API adds them to the launcher's own
     * menu, because they deliberately have no LAUNCHER category (the stock launcher only shows its built-in list).
     * A plain launcher query never sees them, so they're added here: the two known apps, the activities mods list
     * under provides.launcher in /data/hmgapi/mods/<id>/mod.json (when readable), and any app installed by the
     * owner (not part of the system image) that has a MAIN activity but no LAUNCHER one.
     */
    private void addHiddenLaunchers(PackageManager pm, Set<String> hidden, boolean withHidden, List<Entry> out) {
        Set<String> have = new java.util.HashSet<String>();
        for (Entry e : out) have.add(e.component.getPackageName());
        java.util.LinkedHashSet<ComponentName> want = new java.util.LinkedHashSet<ComponentName>();
        for (String[] a : HOOKED_APPS) want.add(new ComponentName(a[0], a[1]));
        java.io.File[] mods = new java.io.File("/data/hmgapi/mods").listFiles();
        if (mods != null) for (java.io.File m : mods) {
            try {
                java.io.File f = new java.io.File(m, "mod.json");
                if (!f.canRead() || new java.io.File(m, ".disabled").exists()) continue;
                byte[] b = new byte[(int) Math.min(f.length(), 256 * 1024)];
                java.io.DataInputStream in = new java.io.DataInputStream(new java.io.FileInputStream(f));
                try { in.readFully(b); } finally { in.close(); }
                org.json.JSONObject prov = new org.json.JSONObject(new String(b, "UTF-8")).optJSONObject("provides");
                org.json.JSONArray l = prov == null ? null : prov.optJSONArray("launcher");
                for (int i = 0; l != null && i < l.length(); i++) {
                    org.json.JSONObject o = l.optJSONObject(i);
                    String pkg = o == null ? null : o.optString("package", null), cls = o == null ? null : o.optString("class", null);
                    if (pkg == null || cls == null) continue;
                    want.add(new ComponentName(pkg, cls.startsWith(".") ? pkg + cls : cls));
                }
            } catch (Throwable t) { /* unreadable or malformed: skip that mod */ }
        }
        // Owner-installed apps with only a MAIN activity (mod apps made for HMG-API's menu).
        try {
            for (ResolveInfo ri : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN), 0)) {
                android.content.pm.ApplicationInfo ai = ri.activityInfo.applicationInfo;
                if ((ai.flags & (android.content.pm.ApplicationInfo.FLAG_SYSTEM | android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0) continue;
                if (!ri.activityInfo.exported || ri.activityInfo.packageName.startsWith("dev.ccnc.")) continue;
                want.add(new ComponentName(ri.activityInfo.packageName, ri.activityInfo.name));
            }
        } catch (Throwable ignored) { }
        for (ComponentName cn : want) {
            if (have.contains(cn.getPackageName())) continue;                // already has a card (or another entry)
            String key = cn.flattenToShortString();
            if (Curated.SKIP.contains(key) || (!withHidden && hidden.contains(key))) continue;
            android.content.pm.ActivityInfo ai;
            try { ai = pm.getActivityInfo(cn, 0); } catch (Throwable t) { continue; }   // not installed
            if (!ai.exported || !ai.enabled) continue;
            CharSequence l = ai.loadLabel(pm);
            String label = l == null || l.toString().trim().isEmpty() ? cn.getPackageName() : l.toString().trim();
            Entry e = new Entry(cn, label, Icons.Kind.APP, ai.loadIcon(pm));
            // Their own icon, as the stock launcher shows them (HMG-API's tile uses the app's icon too): drawn like the
            // CarPlay / Android Auto logos rather than replaced by ccNC's generic icon.
            e.art = null;
            out.add(e);
            have.add(cn.getPackageName());
        }
    }

    /** Nothing connected: one card for the phone projection settings, as the stock home does. */
    private void addProjectionPlaceholder(PackageManager pm, Set<String> hidden, boolean withHidden, List<Entry> out) {
        ComponentName cn = ComponentName.unflattenFromString(Curated.PROJECTION_SETTINGS);
        Intent i = new Intent(Intent.ACTION_MAIN).setComponent(cn);
        if (pm.resolveActivity(i, 0) == null) return;
        if (!withHidden && hidden.contains(Curated.PROJECTION_SETTINGS)) return;
        Entry e = new Entry(cn, "Phone Projection", Icons.Kind.PROJECTION,
                Projection.logo(ctx, Curated.PROJECTION_SETTINGS));
        e.art = dev.ccnc.common.Art.icon(ctx, "pp");

        out.add(e);
    }

    /**
     * Weather has no launchable screen of its own: WeatherActivity isn't exported and is
     * opened by the telematics app (Vrm.main) on the WEATHER_SHORTCUT broadcast, as the
     * stock home does for its weather tile.
     */
    private void addWeather(PackageManager pm, Set<String> hidden, boolean withHidden, List<Entry> out) {
        try {
            pm.getPackageInfo("dev.ccnc.weather", 0);
            return; // ccNC Weather is installed: it has its own card
        } catch (PackageManager.NameNotFoundException ignored) {
        }
        try {
            pm.getPackageInfo(Curated.WEATHER_PKG, 0);
        } catch (PackageManager.NameNotFoundException e) {
            return;
        }
        ComponentName cn = ComponentName.unflattenFromString(Curated.WEATHER_KEY);
        if (!withHidden && hidden.contains(Curated.WEATHER_KEY)) return;
        Entry e = new Entry(cn, "Weather", Icons.Kind.WEATHER, null);
        e.art = dev.ccnc.common.Art.icon(ctx, "weather");
        e.broadcast = new Intent(Curated.WEATHER_SHORTCUT);
        out.add(e);
    }

    static Icons.Kind classify(String pkg, String cls, String label) {
        String p = (pkg + " " + cls).toLowerCase(Locale.US);
        String l = label.toLowerCase(Locale.US);
        if (has(p, "phoneprojection", "projection", "connectivity.carplay", "connectivity.androidauto", ".gpm", ".dipo")
                || has(l, "android auto", "carplay", "projection")) return Icons.Kind.PROJECTION;
        if (has(p, "voicememo") || has(l, "voice memo")) return Icons.Kind.VOICE_MEMO;
        if (has(p, "weather") || has(l, "weather")) return Icons.Kind.WEATHER;
        if (has(p, "btphone", "incallui", "com.android.phone", "bluetooth.phone") || l.equals("phone") || has(l, "phone"))
            return Icons.Kind.PHONE;
        if (has(p, "daudio.app.ev", "app.ev3", "tappev", "hardware.ev") || l.equals("ev") || has(l, "charging"))
            return Icons.Kind.EV;
        if (has(p, "navi", ".map") || has(l, "navigation", "map")) return Icons.Kind.NAVIGATION;
        if (has(p, "radio", "sxm") || has(l, "radio")) return Icons.Kind.RADIO;
        if (has(p, "music", "media", "audio", "video", "naturesound") || has(l, "media", "music", "sound"))
            return Icons.Kind.MEDIA;
        if (has(p, "climate") || has(l, "climate")) return Icons.Kind.CLIMATE;
        if (has(p, "profile") || has(l, "profile")) return Icons.Kind.PROFILE;
        if (has(p, "settings", "setting", "homesetting") || has(l, "setup", "settings")) return Icons.Kind.SETTINGS;
        return Icons.Kind.APP;
    }

    private static boolean has(String s, String... needles) {
        for (String n : needles) if (s.contains(n)) return true;
        return false;
    }

    private void sort(List<Entry> list) {
        final List<String> order = order();
        Collections.sort(list, new Comparator<Entry>() {
            @Override public int compare(Entry a, Entry b) {
                int ia = order.indexOf(a.key()), ib = order.indexOf(b.key());
                if (ia >= 0 && ib >= 0) return ia - ib;
                if (ia >= 0) return -1;
                if (ib >= 0) return 1;
                Curated.Card ca = Curated.get(a.key()), cb = Curated.get(b.key());
                if (ca != null && cb != null) return ca.order - cb.order;
                if (ca != null) return -1;
                if (cb != null) return 1;
                int ka = a.kind.ordinal(), kb = b.kind.ordinal();
                if (ka != kb) return ka - kb;
                return a.label.compareToIgnoreCase(b.label);
            }
        });
    }

    List<String> order() {
        String s = prefs.getString("order", "");
        List<String> out = new ArrayList<String>();
        if (!TextUtils.isEmpty(s)) Collections.addAll(out, s.split("\\|"));
        return out;
    }

    void saveOrder(List<Entry> visible) {
        // Keep hidden entries' places so un-hiding puts them back where they were.
        List<String> keys = new ArrayList<String>();
        for (Entry e : visible) keys.add(e.key());
        for (String old : order()) if (!keys.contains(old)) keys.add(old);
        prefs.edit().putString("order", TextUtils.join("|", keys)).apply();
    }

    /** What the owner hid (or, until they hide or show something, the curated defaults). */
    private Set<String> chosen() {
        return !prefs.contains("hidden") ? new HashSet<String>(Curated.DEFAULT_HIDDEN)
                : new HashSet<String>(prefs.getStringSet("hidden", Collections.<String>emptySet()));
    }

    Set<String> hidden() {
        Set<String> h = chosen();
        // Stock apps a ccNC app replaces stay off the pages while it's installed, unless the owner showed them.
        Set<String> shown = prefs.getStringSet("shown", Collections.<String>emptySet());
        for (Map.Entry<String, String> r : Curated.REPLACED.entrySet())
            if (!shown.contains(r.getKey()) && installed(r.getValue())) h.add(r.getKey());
        return h;
    }

    private boolean installed(String pkg) {
        try { ctx.getPackageManager().getPackageInfo(pkg, 0); return true; } catch (Exception e) { return false; }
    }

    void setHidden(String key, boolean hide) {
        Set<String> h = chosen();
        Set<String> shown = new HashSet<String>(prefs.getStringSet("shown", Collections.<String>emptySet()));
        if (hide) { h.add(key); shown.remove(key); } else { h.remove(key); shown.add(key); }
        prefs.edit().putStringSet("hidden", h).putStringSet("shown", shown).apply();
    }

    void reset() {
        prefs.edit().remove("order").remove("hidden").remove("shown").remove("stacks").apply();
    }

    /** Stacks as {frontKey, backKey} pairs. A stack holds exactly two cards. */
    List<String[]> stacks() {
        List<String[]> out = new ArrayList<String[]>();
        String s = prefs.getString("stacks", "");
        if (!TextUtils.isEmpty(s)) for (String pair : s.split(";")) {
            String[] k = pair.split("\\|");
            if (k.length == 2) out.add(k);
        }
        return out;
    }

    void saveStacks(List<String[]> stacks) {
        List<String> parts = new ArrayList<String>();
        for (String[] k : stacks) parts.add(k[0] + "|" + k[1]);
        prefs.edit().putString("stacks", TextUtils.join(";", parts)).apply();
    }

    /** Puts the back card of a stack in front (and swaps their places in the saved order). */
    void flipStack(String front, String back) {
        List<String[]> st = stacks();
        for (String[] k : st) if (k[0].equals(front) && k[1].equals(back)) { k[0] = back; k[1] = front; }
        saveStacks(st);
        List<String> order = order();
        int a = order.indexOf(front), b = order.indexOf(back);
        if (a >= 0 && b >= 0) {
            order.set(a, back);
            order.set(b, front);
            prefs.edit().putString("order", TextUtils.join("|", order)).apply();
        }
    }

    /** Folds each stack's back card behind its front card; stacks with a missing card are left unstacked. */
    void applyStacks(List<Entry> list) {
        for (String[] k : stacks()) {
            Entry f = null, b = null;
            for (Entry e : list) { if (e.key().equals(k[0])) f = e; if (e.key().equals(k[1])) b = e; }
            if (f != null && b != null && f != b && f.stackBack == null && b.stackBack == null) {
                f.stackBack = b;
                list.remove(b);
            }
        }
    }
}
