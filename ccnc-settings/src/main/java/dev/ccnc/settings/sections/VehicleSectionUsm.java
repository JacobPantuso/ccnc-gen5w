package dev.ccnc.settings.sections;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import dev.ccnc.settings.api.Host;

/**
 * Stock Vehicle Settings (com.android.vehiclesettings) data for VehicleSection:
 *  - the item tree from the stock app's own VehicleSettingsProvider (the table its search uses; rows carry the
 *    stock wording per locale, keyid/parentid and spc; keyid ranges: <10000 group, 1xxxx radio group,
 *    2xxxx radio option, 3xxxx check box, 4xxxx level). Only spc == 0 rows are shown (as the stock search does).
 *  - values and changes through the stock app's exported VehicleSettingsSearchReceiver ("smart control"):
 *    SCONTROL_GET_REQ(type,keyID) -> SCONTROL_GET_RES(keyID,statusValue); SCONTROL_SET_REQ(type,keyID,statusValue).
 *    Check box: 0/1; radio group: keyID of the selected option; -1 unknown.
 *  - the stock left-menu feature checks (ui.uiUtils.checkFirstViewType) called through the stock class loader.
 */
final class VehicleSectionUsm {
    static final String PKG = "com.android.vehiclesettings";
    private static final Uri URI = Uri.parse("content://com.android.vehiclesettings.provider.VehicleSettingsProvider/VehicleSettingsDB.db/VehicleSettings");
    private static final String GET_REQ = "com.mobis.intent.action.SCONTROL_GET_REQ";
    private static final String GET_RES = "com.mobis.intent.action.SCONTROL_GET_RES";
    private static final String SET_REQ = "com.mobis.intent.action.SCONTROL_SET_REQ";
    private static final String SET_RES = "com.mobis.intent.action.SCONTROL_SET_RES";
    static final int ROOT_MAJOR = 3313;
    static final String TAG = "ccncVeh";

    static final class Node {
        int key, parent, type, major, minor, spc;
        long id;
        String text, name;
        final List<Node> kids = new ArrayList<Node>();
    }

    private static VehicleSectionUsm one, ev;
    static synchronized VehicleSectionUsm get(Context c) {
        dev.ccnc.settings.api.Demo.init(c);
        if (one == null) one = new VehicleSectionUsm(c.getApplicationContext(), PKG, URI, ROOT_MAJOR);
        return one;
    }

    /**
     * The stock EV app (HKMC_TAppEV3) uses the same search table helper (com.hkmc.ssearch, type 16) and answers the
     * same smart-control broadcasts (ssearch.b: SCONTROL_SET_REQ type 16 -> its own HEVManager/USMManager writes).
     */
    static final String EV_PKG = "com.hkmc.daudio.app.ev3";
    private static final Uri EV_URI = Uri.parse("content://com.hkmc.daudio.app.ev3.ssearch.EvSettingsProvider/settingDB.db");
    static synchronized VehicleSectionUsm ev(Context c) {
        dev.ccnc.settings.api.Demo.init(c);
        if (ev == null) ev = new VehicleSectionUsm(c.getApplicationContext(), EV_PKG, EV_URI, -1);
        return ev;
    }

    final String pkg;
    private final Uri uri;
    private final int rootMajor;

    private final Context app;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Map<Integer, Integer> vals = new java.util.concurrent.ConcurrentHashMap<Integer, Integer>();
    private volatile long lastAnswer;
    private final Map<Integer, Long> asked = new java.util.concurrent.ConcurrentHashMap<Integer, Long>();
    private final Map<Integer, Integer> types = new java.util.concurrent.ConcurrentHashMap<Integer, Integer>();
    /** keyIDs of this app's table: answers for other keys (the other stock app's) are ignored. */
    private final java.util.Set<Integer> known = java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<Integer, Boolean>());
    private volatile Host host;
    private final Runnable refresh = new Runnable() {
        @Override public void run() { Host h = host; if (h != null) try { h.refresh(); } catch (Throwable ignored) { } }
    };

    private VehicleSectionUsm(Context app, String pkg, Uri uri, int rootMajor) {
        this.app = app;
        this.pkg = pkg;
        this.uri = uri;
        this.rootMajor = rootMajor;
        try {
            IntentFilter f = new IntentFilter(GET_RES);
            f.addAction(SET_RES);
            app.registerReceiver(new BroadcastReceiver() {
                @Override public void onReceive(Context c, Intent i) { onResult(i); }
            }, f, null, bg());
        } catch (Throwable ignored) { }
    }

    private void onResult(Intent i) {
        try {
            int key = i.getIntExtra("keyID", -1);
            if (key < 0 || !known.contains(key)) return; // not a key of this app's table
            // Vehicle Settings and EV3 both answer on these actions and their keyIDs can overlap: the "type" tells them apart.
            int t = i.getIntExtra("type", -1);
            Integer mine = types.get(key);
            if (t >= 0 && mine != null && mine != t) return;
            if (SET_RES.equals(i.getAction())) { asked.remove(key); ask(key); return; }
            if (!i.hasExtra("statusValue")) return;
            int v = i.getIntExtra("statusValue", -1);
            Log.w(TAG, "res " + key + "=" + v);
            lastAnswer = System.currentTimeMillis();
            Integer old = vals.put(key, v);
            if (old == null || old != v) { ui.removeCallbacks(refresh); ui.postDelayed(refresh, 150); }
        } catch (Throwable ignored) { }
    }

    void attach(Host h) { host = h; }

    private static Handler bg() {
        android.os.HandlerThread t = new android.os.HandlerThread("ccncVeh");
        t.start();
        return new Handler(t.getLooper());
    }

    /** First open: wait (max 2.5 s) until the stock app has answered the prefetch and gone quiet for 300 ms. */
    void awaitFirstAnswers() {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < 2500) {
            long la = lastAnswer;
            if (la > 0 && System.currentTimeMillis() - la > 300) break;
            try { Thread.sleep(50); } catch (InterruptedException e) { break; }
        }
        Log.w(TAG, "answers: " + vals.size() + " after " + (System.currentTimeMillis() - start) + " ms");
    }

    boolean anyAnswer() { return !vals.isEmpty(); }

    /** Last value the stock app reported for keyID, -1 if none yet; asks for a fresh one. */
    int value(int key) { ask(key); Integer v = vals.get(key); return v == null ? -1 : v; }

    private void ask(int key) {
        if (dev.ccnc.settings.api.Demo.on()) return; // emulator demo: values are seeded with the demo tree
        Long t = asked.get(key);
        long now = System.currentTimeMillis();
        if (t != null && now - t < 10000) return;
        asked.put(key, now);
        try {
            Integer type = types.get(key);
            app.sendBroadcast(new Intent(GET_REQ).setPackage(pkg).putExtra("type", type == null ? 0 : type).putExtra("keyID", key));
        } catch (Throwable ignored) { }
    }

    /** Not tested on car. */
    void set(int key, int status) {
        if (dev.ccnc.settings.api.Demo.on()) { vals.put(key, status); return; }
        try {
            Integer type = types.get(key);
            app.sendBroadcast(new Intent(SET_REQ).setPackage(pkg).putExtra("type", type == null ? 0 : type)
                    .putExtra("keyID", key).putExtra("statusValue", status));
            vals.put(key, status);
        } catch (Throwable ignored) { }
    }

    // ---- item tree (stock provider) ----

    private List<Node> menus;
    private long menusAt;

    /** Top-level stock menus (major 3313) with their trees; empty when the provider isn't there. */
    synchronized List<Node> menus() {
        if (dev.ccnc.settings.api.Demo.on()) { // emulator demo: the invented tree instead of the stock provider's table
            if (menus == null) {
                Map<Integer, Integer> seed = new HashMap<Integer, Integer>();
                menus = DemoData.menus(rootMajor < 0, seed);
                vals.putAll(seed);
            }
            return menus;
        }
        if (menus != null && System.currentTimeMillis() - menusAt < 10000) return menus;
        List<Node> out = new ArrayList<Node>();
        Map<Integer, Node> byKey = new HashMap<Integer, Node>();
        List<Node> all = new ArrayList<Node>();
        Cursor c = null;
        try {
            c = app.getContentResolver().query(uri, null, null, null, "_id");
            if (c != null) {
                Locale l = Locale.getDefault();
                int txt = c.getColumnIndex(l.getLanguage() + "_" + l.getCountry());
                if (txt < 0 || "ko".equals(l.getLanguage()) && !"KR".equals(l.getCountry())) txt = c.getColumnIndex("en_US");
                int en = c.getColumnIndex("en_US");
                int ck = c.getColumnIndex("keyid"), cp = c.getColumnIndex("parentid"), ct = c.getColumnIndex("type"),
                    cma = c.getColumnIndex("major"), cmi = c.getColumnIndex("minor"), cs = c.getColumnIndex("spc"), cl = c.getColumnIndex("lid");
                while (c.moveToNext()) {
                    Node n = new Node();
                    n.id = c.getPosition(); n.key = c.getInt(ck); n.parent = c.getInt(cp); n.type = c.getInt(ct);
                    n.major = c.getInt(cma); n.minor = c.getInt(cmi); n.spc = cs < 0 ? 0 : c.getInt(cs);
                    n.text = txt >= 0 ? c.getString(txt) : null;
                    if ((n.text == null || n.text.length() == 0) && en >= 0) n.text = c.getString(en);
                    n.name = cl >= 0 ? c.getString(cl) : null;
                    if (n.text == null || n.text.trim().length() == 0) continue;
                    n.text = n.text.trim();
                    all.add(n);
                }
            }
        } catch (Throwable t) { Log.w(TAG, "item table: " + t);
        } finally { if (c != null) try { c.close(); } catch (Throwable ignored) { } }
        // menus: major 3313 rows that are real menus (key 1 = root, key 0 = Reset/title rows, 2xxxx = options)
        Map<Integer, Node> menuByKey = new HashMap<Integer, Node>();
        if (rootMajor > 0) {
            for (Node n : all)
                if (n.major == rootMajor && n.key > 1 && n.key < 20000) { out.add(n); if (!menuByKey.containsKey(n.key)) menuByKey.put(n.key, n); }
        } else {
            // No fixed root (EV): top-level groups are the menus; loose top-level settings go in a first, untitled one.
            Map<Integer, Node> first = new HashMap<Integer, Node>();
            for (Node n : all) if (n.key > 1 && !first.containsKey(n.key)) first.put(n.key, n);
            Node loose = new Node();
            loose.key = 0; loose.id = -1;
            for (Node n : all) {
                if (first.get(n.key) != n) continue;
                boolean top = n.parent <= 1 || n.parent == n.key || !first.containsKey(n.parent);
                if (!top) continue;
                if (n.key < 10000) { out.add(n); menuByKey.put(n.key, n); }
                else if (n.key < 20000 || n.key >= 30000) loose.kids.add(n);
            }
            if (!loose.kids.isEmpty()) { out.add(0, loose); }
        }
        // items: first row per key wins (later rows with the same key are the same setting under another trim's label)
        for (Node n : all) {
            if (out.contains(n) || n.key <= 1) continue;
            if (byKey.containsKey(n.key) || menuByKey.containsKey(n.key) && n.key < 10000) continue;
            byKey.put(n.key, n);
            types.put(n.key, n.type);
        }
        for (Node n : byKey.values()) {
            Node p = n.parent == n.key ? null : byKey.get(n.parent);
            if (p == null) p = menuByKey.get(n.parent);
            if (p != null && p != n) p.kids.add(n);
        }
        for (Node m : out) types.put(m.key, m.type);
        if (rootMajor < 0) for (Node n : all) if (!types.containsKey(n.key)) types.put(n.key, n.type);
        known.clear();
        known.addAll(types.keySet());
        sortKids(out);
        Log.w(TAG, "items " + byKey.size() + " menus " + out.size());
        menus = out;
        menusAt = System.currentTimeMillis();
        return out;
    }

    /** Value the stock app reported (no new request); -2 = no answer yet. */
    int known(int key) { Integer v = vals.get(key); return v == null ? -2 : v; }

    /** Leaf (check/radio group/level) the stock getter answers with a real value on this car. */
    boolean leafOk(Node n) { return known(n.key) >= 0; }

    /** Keep table order (_id) for children: byKey iteration is unordered. */
    private void sortKids(List<Node> ms) {
        for (Node m : ms) sortRec(m);
    }
    private void sortRec(Node n) {
        java.util.Collections.sort(n.kids, new java.util.Comparator<Node>() {
            @Override public int compare(Node a, Node b) { return a.id < b.id ? -1 : a.id > b.id ? 1 : 0; }
        });
        for (Node k : n.kids) sortRec(k);
    }

    /** Children this car has: leaves the stock getter answers (-1 = not supported), groups holding one. */
    List<Node> visible(Node n) {
        List<Node> l = new ArrayList<Node>();
        for (Node k : n.kids) if (has(k)) l.add(k);
        return l;
    }

    private boolean has(Node k) {
        if (k.key >= 20000 && k.key < 30000) return true; // radio option: shown with its group
        if (k.key >= 10000) return leafOk(k);
        for (Node c : k.kids) if (has(c)) return true;
        return k.kids.isEmpty() && false;
    }

    /** Ask the stock app for every leaf value (once per 10 s); pages redraw as answers arrive. */
    void prefetch(List<Node> ms) {
        for (Node m : ms) prefetch(m);
    }
    private void prefetch(Node n) {
        if ((n.key >= 10000 && n.key < 20000) || (n.key >= 30000 && n.key < 50000)) ask(n.key);
        for (Node c : n.kids) prefetch(c);
    }

    // ---- stock feature checks ----

    private ClassLoader cl;
    private Context stockCtx;
    private final Map<String, Boolean> checks = new HashMap<String, Boolean>();
    private long checksAt;

    private ClassLoader loader() {
        if (cl != null) return cl;
        try {
            stockCtx = app.createPackageContext(PKG, Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
            cl = stockCtx.getClassLoader();
            Class<?> u = cl.loadClass(PKG + ".util.Utils");
            Field f = u.getDeclaredField("mContext");
            f.setAccessible(true);
            if (f.get(null) == null) f.set(null, stockCtx);
        } catch (Throwable t) { cl = null; }
        return cl;
    }

    /** Static boolean check of the stock app (cls under com.android.vehiclesettings.util); null if it can't run. */
    private Boolean call(String cls, String m, boolean withCtx) {
        String id = cls + "." + m;
        if (System.currentTimeMillis() - checksAt > 10000) { checks.clear(); checksAt = System.currentTimeMillis(); }
        if (checks.containsKey(id)) return checks.get(id);
        Boolean r = null;
        try {
            ClassLoader l = loader();
            if (l != null) {
                Class<?> c = l.loadClass(PKG + ".util." + cls);
                Object v;
                if (withCtx) { Method mm = c.getMethod(m, Context.class, boolean.class); v = mm.invoke(null, stockCtx, false); }
                else v = c.getMethod(m).invoke(null);
                r = (Boolean) v;
            }
        } catch (Throwable t) { r = null; Log.w(TAG, "stock check " + id + " failed: " + t); }
        Log.w(TAG, "check " + id + " = " + r);
        checks.put(id, r);
        return r;
    }

    private static Boolean or(Boolean a, Boolean b) {
        if (Boolean.TRUE.equals(a) || Boolean.TRUE.equals(b)) return Boolean.TRUE;
        return a == null || b == null ? null : Boolean.FALSE;
    }

    /** Utils.Pf(): drive-mode menu (vs ECO). */
    Boolean driveMode() { return call("Utils", "OY", false); }

    /** The stock left-menu check for a menu (by its minor id); null when it can't be evaluated. */
    Boolean menuSupported(int minor) {
        if (dev.ccnc.settings.api.Demo.on()) return null; // demo: a menu shows when it has items
        switch (minor) {
            // names from the car's build (KVT49L, ui/aw "checkFirstViewType"); a missing one logs and returns null
            case 3315: return call("Utils", "isSupportDriverAssistance", false);
            case 3316: return call("Utils", "OY", false);
            case 3318: return call("EcoUtils", "c", true);
            case 3326: return call("Utils", "Py", false);
            case 3314: return call("HeadUpUtils", "qc", false);
            case 3321: return call("ClusterUtils", "tl", false);
            case 3317: return climate();
            case 3323: return call("Utils", "Pk", false);
            case 3319: return call("Utils", "tj", false);
            case 3320: return call("DoorTrunkUtils", "Cd", false);
            case 3324: return or(call("Utils", "Qb", false), call("Utils", "Qc", false));
            case 3322: return call("Utils", "tm", false);
            default: return null;
        }
    }

    /** util.o.A(ctx).vf() */
    private Boolean climate() {
        try {
            ClassLoader l = loader();
            if (l == null) return null;
            Class<?> c = l.loadClass(PKG + ".util.o");
            Object o = c.getMethod("A", Context.class).invoke(null, stockCtx);
            return (Boolean) c.getMethod("vf").invoke(o);
        } catch (Throwable t) { Log.w(TAG, "stock check util.o.A/vf missing: " + t); return null; }
    }
}
