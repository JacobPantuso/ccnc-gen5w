package dev.ccnc.settings.sections;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.provider.Settings;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Reaches the stock Gen5W General settings app (com.android.generalsettings) through its own class loader, so the
 * General category uses the stock feature checks and the stock Date &amp; Time write path:
 *  - ClockSettingPref.m/n/o(Context): GPS auto time / daylight saving / 24-hour reads (Settings.System
 *    gps_auto_time, summer_time, time_12_24 = the active values, index 3 in class q).
 *  - ClockSettingPref.u(boolean): the stock setGPSAutoState (profile key + active key).
 *  - q.a(Context,String) + q.a(3,Context,String): 12/24 h (profile key + active key), then the stock
 *    com.android.settings.Action24HourOn/Off broadcast (Clock24hFormat).
 *  - q.b(Context,int) + q.b(3,Context,int) + TimePolicyManager.setDst(): daylight saving (ClockSettingPref save).
 * Every call is guarded; without the stock app (emulator) the stock-dependent rows hide and 12/24 h falls back to
 * the plain Settings.System time_12_24 key.
 */
final class GeneralStock {
    private static final String TAG = "ccncSetup";
    private static final String PKG = "com.android.generalsettings";
    private static GeneralStock inst;

    // Obfuscated stock names, resolved from the CAR's build (/tmp/ugset/d_GeneralSettings, GeneralSettings.zm()).
    static final String AG = PKG + ".util.ag", UTILS = PKG + ".util.Utils", UNITS = PKG + ".util.i";
    static final String GPS_CHECK = "yd";      // ClockSettingPref.m(): GPS time supported
    static final String DST_CHECK = "ye";      // ClockSettingPref.er(): keep summer_time (not CN/KR/IN/...)
    static final String LANG_CHECK = "wA";     // Language tab
    static final String SYSINFO_CHECK = "wC";  // System Info tab
    static final String MEDIA_CHECK = "wh";    // Media Options tab
    static final String RESET_CHECK = "wf";    // Reset tab
    static final String MEDIA_NOTI = PKG + ".dv";  // Settings.System media_notification (H = get, i = set)

    private final Context sc;      // stock package context (null: stock app not readable)
    private final ClassLoader cl;

    private GeneralStock(Context sc) { this.sc = sc; this.cl = sc != null ? sc.getClassLoader() : null; }

    static synchronized GeneralStock get(Context c) {
        if (inst != null) return inst;
        dev.ccnc.settings.api.Demo.init(c);
        if (dev.ccnc.settings.api.Demo.on()) { inst = new GeneralStock(null); return inst; } // emulator demo: answers come from Demo
        Context sc = null;
        try {
            sc = c.createPackageContext(PKG, Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
            // The stock code asks GeneralSettingsApp.bb() for its context: give it one, as its Application would.
            Class<?> app = sc.getClassLoader().loadClass(PKG + ".GeneralSettingsApp");
            Field f = app.getDeclaredField("dx");
            f.setAccessible(true);
            if (f.get(null) == null) {
                Object a = app.newInstance();
                Method attach = ContextWrapper.class.getDeclaredMethod("attachBaseContext", Context.class);
                attach.setAccessible(true);
                // bb() returns getApplicationContext(): a foreign package context has none, so answer with sc itself.
                final Context base = sc;
                attach.invoke(a, new ContextWrapper(base) {
                    @Override public Context getApplicationContext() { return base; }
                });
                f.set(null, a);
            }
        } catch (Throwable t) {
            Log.w(TAG, "stock General settings not readable", t);
        }
        inst = new GeneralStock(sc);
        if (inst.cl != null) {
            StringBuilder b = new StringBuilder("General stock checks:");
            String[][] ck = { {AG, GPS_CHECK}, {AG, DST_CHECK}, {UTILS, LANG_CHECK}, {UTILS, SYSINFO_CHECK},
                    {UTILS, MEDIA_CHECK}, {UTILS, RESET_CHECK}, {UNITS, "fU"}, {UNITS, "fz"}, {UNITS, "fy"} };
            for (String[] k : ck) b.append(' ').append(k[1]).append('=').append(inst.boolOr(k[0], k[1], false));
            b.append(" model=").append(modelName()).append(" mediaNoti=").append(inst.intCall(MEDIA_NOTI, "H"));
            Log.i(TAG, b.toString());
        }
        return inst;
    }

    boolean available() { return cl != null || dev.ccnc.settings.api.Demo.on(); }

    private Object call(String cls, String name, Class<?>[] types, Object... args) throws Throwable {
        Method m = cl.loadClass(cls).getDeclaredMethod(name, types);
        m.setAccessible(true);
        return m.invoke(null, args);
    }

    /** Stock static boolean check; false when it can't be read. */
    boolean bool(String cls, String name) { return boolOr(cls, name, false); }

    boolean boolOr(String cls, String name, boolean def) {
        if (dev.ccnc.settings.api.Demo.on()) return dev.ccnc.settings.api.Demo.check(name);
        if (cl == null) return def;
        try { return (Boolean) call(cls, name, new Class<?>[0]); }
        catch (Throwable t) { Log.w(TAG, "stock check " + cls + "." + name, t); return def; }
    }

    private boolean clockRead(String name) throws Throwable {
        return (Boolean) call(PKG + ".ClockSettingPref", name, new Class<?>[] { Context.class }, sc);
    }

    // ---- Set the Time Automatically (gps_auto_time) ----
    boolean gpsAuto(Context c) {
        if (dev.ccnc.settings.api.Demo.on()) return dev.ccnc.settings.api.Demo.getBool("gen.gps", true);
        try { if (cl != null) return clockRead("m"); } catch (Throwable t) { Log.w(TAG, "gps auto read", t); }
        try { return Settings.System.getInt(c.getContentResolver(), "gps_auto_time") == 1; } catch (Throwable t) { return false; }
    }

    void setGpsAuto(Context c, boolean on) {
        if (dev.ccnc.settings.api.Demo.on()) { dev.ccnc.settings.api.Demo.put("gen.gps", on); return; }
        if (cl == null) return;
        try { call(PKG + ".ClockSettingPref", "u", new Class<?>[] { boolean.class }, on); }
        catch (Throwable t) { Log.w(TAG, "gps auto write", t); }
        if (on) {
            try {
                Object tm = timePolicy(c);
                tm.getClass().getMethod("requestRealTime").invoke(tm);
            } catch (Throwable t) { Log.w(TAG, "requestRealTime", t); }
        }
    }

    // ---- Time Format (time_12_24) ----
    boolean is24(Context c) {
        if (dev.ccnc.settings.api.Demo.on()) return dev.ccnc.settings.api.Demo.getBool("gen.24h", false);
        try { if (cl != null) return clockRead("o"); } catch (Throwable t) { Log.w(TAG, "24h read", t); }
        return "24".equals(GeneralSection.sysString(c, "time_12_24"));
    }

    void set24(Context c, boolean h24) {
        if (dev.ccnc.settings.api.Demo.on()) { dev.ccnc.settings.api.Demo.put("gen.24h", h24); return; }
        String v = h24 ? "24" : "12";
        boolean done = false;
        if (cl != null) {
            try {
                Class<?> q = cl.loadClass(PKG + ".q");
                Method byProfile = q.getDeclaredMethod("a", Context.class, String.class);
                Method byIndex = q.getDeclaredMethod("a", int.class, Context.class, String.class);
                byProfile.setAccessible(true); byIndex.setAccessible(true);
                byProfile.invoke(null, sc, v);
                byIndex.invoke(null, 3, sc, v);
                done = true;
            } catch (Throwable t) { Log.w(TAG, "24h write (stock)", t); }
        }
        if (!done) {
            try { Settings.System.putString(c.getContentResolver(), "time_12_24", v); } catch (Throwable t) { Log.w(TAG, "24h write", t); }
        }
        try { c.sendBroadcast(new Intent(h24 ? "com.android.settings.Action24HourOn" : "com.android.settings.Action24HourOff")); }
        catch (Throwable t) { Log.w(TAG, "24h broadcast", t); }
    }

    // ---- Daylight Saving Time (summer_time) ----
    boolean dst(Context c) {
        if (dev.ccnc.settings.api.Demo.on()) return dev.ccnc.settings.api.Demo.getBool("gen.dst", false);
        try { if (cl != null) return clockRead("n"); } catch (Throwable t) { Log.w(TAG, "dst read", t); }
        try { return Settings.System.getInt(c.getContentResolver(), "summer_time") > 0; } catch (Throwable t) { return false; }
    }

    void setDst(Context c, boolean on) {
        if (dev.ccnc.settings.api.Demo.on()) { dev.ccnc.settings.api.Demo.put("gen.dst", on); return; }
        if (cl == null) return;
        int v = on ? 1 : 0;
        try {
            Class<?> q = cl.loadClass(PKG + ".q");
            Method byProfile = q.getDeclaredMethod("b", Context.class, int.class);
            Method byIndex = q.getDeclaredMethod("b", int.class, Context.class, int.class);
            byProfile.setAccessible(true); byIndex.setAccessible(true);
            byProfile.invoke(null, sc, v);
            byIndex.invoke(null, 3, sc, v);
        } catch (Throwable t) { Log.w(TAG, "dst write (stock)", t); }
        try {
            Object tm = timePolicy(c);
            tm.getClass().getMethod("setDst", boolean.class).invoke(tm, on);
        } catch (Throwable t) { Log.w(TAG, "TimePolicyManager.setDst", t); }
    }

    /** Stock string by resource name (stable across firmware), in the current locale; fallback when unreadable. */
    String str(String name, String fallback) {
        if (dev.ccnc.settings.api.Demo.on()) return fallback;
        try {
            int id = sc.getResources().getIdentifier(name, "string", PKG);
            if (id != 0) return sc.getResources().getString(id);
        } catch (Throwable t) { Log.w(TAG, "stock string " + name, t); }
        return fallback;
    }

    /** Static int getter of a stock helper (profile-aware Settings read), -1 when unreadable. */
    int intCall(String cls, String name) {
        if (dev.ccnc.settings.api.Demo.on()) return dev.ccnc.settings.api.Demo.getInt("gen.media_noti", 1);
        if (cl == null) return -1;
        try { return (Integer) call(cls, name, new Class<?>[] { Context.class }, sc); }
        catch (Throwable t) { Log.w(TAG, "stock read " + cls + "." + name, t); return -1; }
    }

    /** Static (Context,int) setter of a stock helper (profile key + active key). */
    void intSet(String cls, String name, int v) {
        if (dev.ccnc.settings.api.Demo.on()) { dev.ccnc.settings.api.Demo.put("gen.media_noti", v); return; }
        if (cl == null) return;
        try { call(cls, name, new Class<?>[] { Context.class, int.class }, sc, v); }
        catch (Throwable t) { Log.w(TAG, "stock write " + cls + "." + name, t); }
    }

    /** Static no-arg int getter of a stock helper (util.i.get*Unit()), -1 when unreadable. */
    int intCall0(String cls, String name) {
        if (dev.ccnc.settings.api.Demo.on()) return dev.ccnc.settings.api.Demo.getInt("unit." + name.substring(3), -1);
        if (cl == null) return -1;
        try { return (Integer) call(cls, name, new Class<?>[0]); }
        catch (Throwable t) { Log.w(TAG, "stock read " + cls + "." + name + ": " + t); return -1; }
    }

    /** Static (int) setter of a stock helper (util.i.set*Unit(int): USMManager write + the stock backup). */
    void intSet1(String cls, String name, int v) {
        if (dev.ccnc.settings.api.Demo.on()) { dev.ccnc.settings.api.Demo.put("unit." + name.substring(3), v); return; }
        if (cl == null) return;
        try { call(cls, name, new Class<?>[] { int.class }, v); Log.i(TAG, cls + "." + name + "(" + v + ")"); }
        catch (Throwable t) { Log.w(TAG, "stock write " + cls + "." + name, t); }
    }

    /** Model name as the stock SW Info shows it (com.hkmc.ssearch.SDefine.getModelName(), framework API). */
    static String modelName() {
        try { return (String) Class.forName("com.hkmc.ssearch.SDefine").getMethod("getModelName").invoke(null); }
        catch (Throwable t) { Log.w(TAG, "SDefine.getModelName", t); return null; }
    }

    static String prop(String key) {
        try {
            String v = (String) Class.forName("android.os.SystemProperties").getMethod("get", String.class).invoke(null, key);
            return v == null || v.length() == 0 ? null : v;
        } catch (Throwable t) { return null; }
    }

    /** com.hkmc.time.TimePolicyManager (automotive framework jar on the boot class path). */
    private Object timePolicy(Context c) throws Throwable {
        Class<?> k = Class.forName("com.hkmc.time.TimePolicyManager");
        return k.getConstructor(Context.class).newInstance(c);
    }
}
