package dev.ccnc.settings.api;

import android.content.Context;
import android.provider.Settings;

/** Settings.System / Settings.Global access that never throws (returns def / false on failure). */
public final class Sys {
    private Sys() {}
    public static int getInt(Context c, String key, int def) {
        try { return Settings.System.getInt(c.getContentResolver(), key); } catch (Throwable t) { return def; }
    }
    public static String getString(Context c, String key) {
        try { return Settings.System.getString(c.getContentResolver(), key); } catch (Throwable t) { return null; }
    }
    public static boolean putInt(Context c, String key, int v) {
        try { return Settings.System.putInt(c.getContentResolver(), key, v); } catch (Throwable t) { return false; }
    }
    public static int getGlobal(Context c, String key, int def) {
        try { return Settings.Global.getInt(c.getContentResolver(), key); } catch (Throwable t) { return def; }
    }
    public static boolean putGlobal(Context c, String key, int v) {
        try { return Settings.Global.putInt(c.getContentResolver(), key, v); } catch (Throwable t) { return false; }
    }
}
