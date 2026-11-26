package dev.ccnc.settings.api;

import android.content.Context;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * Reflection into the Gen5W automotive framework (automotive.jar on the boot class path: com.hkmc.* managers) and
 * read access to stock apps' string resources. Never throws. The com.hkmc micom services do not enforce a caller
 * permission on these calls (automotive-service checked: only DUMP / ACCESS_RADIO / ACCESS_AUX are enforced), so a
 * normal app reaches them the same way the stock settings apps do.
 */
public final class Car {
    private Car() {}
    private static final String TAG = "ccncSetup";
    private static final Map<String, Object> MANAGERS = new HashMap<String, Object>();
    private static final Map<String, Context> PKGS = new HashMap<String, Context>();

    /** new cls(Context), cached per class; null when the class isn't on this unit (emulator). */
    public static synchronized Object manager(Context c, String cls) {
        Demo.init(c);
        if (Demo.on()) return Demo.manager(cls); // emulator demo only (never on a car build)
        if (MANAGERS.containsKey(cls)) return MANAGERS.get(cls);
        Object o = null;
        try { o = Class.forName(cls).getConstructor(Context.class).newInstance(c.getApplicationContext()); }
        catch (Throwable t) { Log.i(TAG, cls + " unavailable: " + t); }
        MANAGERS.put(cls, o);
        return o;
    }

    /** o.name(args) by exact parameter types; null on any failure (also when o is null). */
    public static Object call(Object o, String name, Class<?>[] types, Object... args) {
        if (o == null) return null;
        if (o instanceof Demo.Obj) return ((Demo.Obj) o).call(name, args);
        try {
            Method m = o.getClass().getMethod(name, types);
            return m.invoke(o, args);
        } catch (Throwable t) {
            Log.w(TAG, o.getClass().getSimpleName() + "." + name + " failed: " + t);
            return null;
        }
    }

    public static int getInt(Object o, String getter, int def) {
        Object v = call(o, getter, new Class<?>[0]);
        return v instanceof Number ? ((Number) v).intValue() : def;
    }

    public static int getInt(Object o, String getter, int arg, int def) {
        Object v = call(o, getter, new Class<?>[] { int.class }, arg);
        return v instanceof Number ? ((Number) v).intValue() : def;
    }

    public static boolean getBool(Object o, String getter, boolean def) {
        Object v = call(o, getter, new Class<?>[0]);
        return v instanceof Boolean ? (Boolean) v : def;
    }

    public static String getStr(Object o, String getter) {
        Object v = call(o, getter, new Class<?>[0]);
        return v == null ? null : v.toString();
    }

    public static void setInt(Object o, String setter, int v) { call(o, setter, new Class<?>[] { int.class }, v); }

    public static void setBool(Object o, String setter, boolean v) { call(o, setter, new Class<?>[] { boolean.class }, v); }

    /** True when the object has a public method of that name (any signature). */
    public static boolean hasMethod(Object o, String name) {
        if (o == null) return false;
        if (o instanceof Demo.Obj) return true;
        try { for (Method m : o.getClass().getMethods()) if (m.getName().equals(name)) return true; } catch (Throwable ignored) { }
        return false;
    }

    /** com.hkmc.userprofile.UserProfileManager.getUserType(): 0 guest, 1 driver 1, 2 driver 2; 0 when unreadable. */
    public static int userType(Context c) {
        int t = getInt(manager(c, "com.hkmc.userprofile.UserProfileManager"), "getUserType", 0);
        return t == 1 || t == 2 ? t : 0;
    }

    /** Per-profile Settings.System key as the stock apps name it: key, key_dr1, key_dr2. */
    public static String userKey(Context c, String key) {
        int t = userType(c);
        return t == 1 ? key + "_dr1" : t == 2 ? key + "_dr2" : key;
    }

    /** com.hkmc.Feature.hasFeature(name). */
    public static boolean feature(String name) {
        if (Demo.on()) return Demo.feature(name);
        try { return Boolean.TRUE.equals(Class.forName("com.hkmc.Feature").getMethod("hasFeature", String.class).invoke(null, name)); }
        catch (Throwable t) { return false; }
    }

    /** android.os.SystemProperties.get(key), "" when unset/unreadable. */
    public static String prop(String key) {
        if (Demo.on()) { String d = Demo.prop(key); if (d != null) return d; }
        try {
            Object v = Class.forName("android.os.SystemProperties").getMethod("get", String.class, String.class).invoke(null, key, "");
            return v == null ? "" : (String) v;
        } catch (Throwable t) { return ""; }
    }

    public static int propInt(String key, int def) {
        try { return Integer.parseInt(prop(key).trim()); } catch (Throwable t) { return def; }
    }

    /** ro.product.opt.stepofspec >= step (the stock apps' isSupportMinimumStep). */
    public static boolean step(int step) { return propInt("ro.product.opt.stepofspec", 0) >= step; }

    /** A stock app's resources (no code), cached; null when the package isn't installed. */
    public static synchronized Context pkg(Context c, String pkg) {
        Demo.init(c);
        // demo: every stock package "exists"; its strings resolve to nothing, so the ccNC fallback wording shows
        if (Demo.on()) return c.getApplicationContext();
        if (PKGS.containsKey(pkg)) return PKGS.get(pkg);
        Context p = null;
        try { p = c.createPackageContext(pkg, 0); } catch (Throwable ignored) { }
        PKGS.put(pkg, p);
        return p;
    }

    /**
     * A stock string by resource name in the current language (so labels follow the unit's language like the rest
     * of ccNC Setup), line breaks joined; fallback (ccNC wording, Title Case) when the app or name is missing.
     */
    public static String str(Context c, String pkg, String name, String fallback) {
        try {
            Context p = pkg(c, pkg);
            if (p != null) {
                int id = p.getResources().getIdentifier(name, "string", pkg);
                if (id != 0) {
                    String s = p.getResources().getString(id);
                    if (s != null) {
                        s = s.replace("\\n", " ").replace('\n', ' ').replaceAll(" +", " ").trim();
                        if (s.length() > 0) return s;
                    }
                }
            }
        } catch (Throwable ignored) { }
        return fallback;
    }
}
