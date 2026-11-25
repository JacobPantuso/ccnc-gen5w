package dev.ccnc.settings.sections;

import android.content.Context;
import android.content.ContextWrapper;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Runs a stock Gen5W settings app's own code in our process (its class loader via createPackageContext), so feature
 * checks and value reads/writes are exactly the stock ones. Used by ButtonSection and ThemeSection. Never throws.
 */
final class ButtonSectionStock {
    private static final String TAG = "ccnc.StockApp";

    final String pkg;
    final Context sc;
    final ClassLoader cl;

    private ButtonSectionStock(String pkg, Context sc) { this.pkg = pkg; this.sc = sc; this.cl = sc.getClassLoader(); }

    /**
     * Opens the stock package and gives its Application singleton (static field instField of appCls) a context, as the
     * stock app's own Application would have, since its static helpers ask it for one. null if the app isn't there.
     */
    static ButtonSectionStock open(Context c, String pkg, String appCls, String instField) {
        try {
            Context sc = c.createPackageContext(pkg, Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
            ButtonSectionStock s = new ButtonSectionStock(pkg, sc);
            if (appCls != null) {
                try {
                    Class<?> app = s.cl.loadClass(appCls);
                    Field inst = app.getDeclaredField(instField);
                    inst.setAccessible(true);
                    if (inst.get(null) == null) {
                        Object a = app.newInstance();
                        Method attach = ContextWrapper.class.getDeclaredMethod("attachBaseContext", Context.class);
                        attach.setAccessible(true);
                        attach.invoke(a, sc);
                        inst.set(null, a);
                    }
                } catch (Throwable t) { Log.w(TAG, pkg + ": app context not set", t); }
            }
            return s;
        } catch (Throwable t) {
            Log.w(TAG, pkg + " not usable", t);
            return null;
        }
    }

    /** Static no-arg boolean check of the stock app (false when it throws). */
    boolean is(String cls, String method) {
        try { return (Boolean) cl.loadClass(cls).getMethod(method).invoke(null); }
        catch (Throwable t) { Log.w(TAG, cls + "." + method, t); return false; }
    }


    /** A system property (android.os.SystemProperties.get), "" when unreadable. */
    static String prop(String key) {
        try { return (String) Class.forName("android.os.SystemProperties").getMethod("get", String.class, String.class).invoke(null, key, ""); }
        catch (Throwable t) { return ""; }
    }

    /**
     * Settings.System key suffix of the active user profile, as the stock ButtonSettings/ThemeSettings Interface
     * helpers use it (Wrapper$UserProfileManager.getUserType(): 1 -> _dr1, 2 -> _dr2, otherwise the plain key).
     */
    static String userSuffix(Context c) {
        try {
            ButtonSectionStock b = open(c, "com.android.buttonsettings", null, null);
            if (b == null) return "";
            Class<?> w = b.cl.loadClass("com.android.buttonsettings.util.Wrapper$UserProfileManager");
            Object m = w.getMethod("getInstance", Context.class).invoke(null, b.sc);
            int u = (Integer) w.getMethod("getUserType").invoke(m);
            return u == 1 ? "_dr1" : u == 2 ? "_dr2" : "";
        } catch (Throwable t) { Log.w(TAG, "user type", t); return ""; }
    }

    /** Static boolean check of the stock app taking a Context (the stock package context). */
    boolean isCtx(String cls, String method) {
        try { return (Boolean) cl.loadClass(cls).getMethod(method, Context.class).invoke(null, sc); }
        catch (Throwable t) { Log.w(TAG, cls + "." + method, t); return false; }
    }

    /** A stock string resource by name (current locale), line breaks joined; null if missing. */
    String str(String name) {
        try {
            int id = sc.getResources().getIdentifier(name, "string", pkg);
            return id == 0 ? null : clean(sc.getResources().getString(id));
        } catch (Throwable t) { return null; }
    }

    /** A stock string resource by id; null if missing. */
    String str(int id) {
        try { return clean(sc.getResources().getString(id)); } catch (Throwable t) { return null; }
    }

    private static String clean(String s) { return s == null ? null : s.replace("\\n", " ").replace('\n', ' ').replaceAll(" +", " ").trim(); }

    /** Static int getter cls.method(Context) of the stock app; def when it throws. */
    int getInt(String cls, String method, int def) {
        try { return (Integer) cl.loadClass(cls).getMethod(method, Context.class).invoke(null, sc); }
        catch (Throwable t) { Log.w(TAG, cls + "." + method, t); return def; }
    }

    /** Static setter cls.method(Context, int) of the stock app. */
    void setInt(String cls, String method, int v) {
        try { cl.loadClass(cls).getMethod(method, Context.class, int.class).invoke(null, sc, v); }
        catch (Throwable t) { Log.w(TAG, cls + "." + method + "(" + v + ")", t); }
    }
}
