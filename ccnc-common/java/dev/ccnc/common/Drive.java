package dev.ccnc.common;

/**
 * Which side the driver sits on, for every ccNC app. Right-hand drive puts the driver's controls on the right: the
 * back/home pill of the bottom bar, the driver's climate zone and seat. Read the way the stock climate app does
 * (persist.sys.climate.rhd); left-hand drive when it isn't set. Overrides, for a car whose firmware doesn't set it
 * (then restart the ccNC apps): the setting ccnc_rhd = 1 / 0 (adb: settings put global ccnc_rhd 1; no root needed),
 * or the property persist.sys.ccnc.rhd = 1 / 0.
 */
public final class Drive {
    private Drive() {}

    private static Boolean rhd;

    public static boolean rhd(android.content.Context c) {
        if (rhd == null) {
            String g = null;
            try { g = android.provider.Settings.Global.getString(c.getContentResolver(), "ccnc_rhd"); } catch (Throwable ignored) { }
            if (g != null && (g.equals("1") || g.equals("0"))) { rhd = g.equals("1"); return rhd; }
            String o = prop("persist.sys.ccnc.rhd");
            if (o.length() > 0) rhd = o.equals("1") || o.equalsIgnoreCase("true");
            else {
                String v = prop("persist.sys.climate.rhd");
                rhd = v.equals("1") || v.equalsIgnoreCase("true");
            }
        }
        return rhd;
    }

    static String prop(String key) {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            String v = (String) sp.getMethod("get", String.class, String.class).invoke(null, key, "");
            return v == null ? "" : v.trim();
        } catch (Throwable t) { return ""; }
    }
}
