package dev.ccnc.common;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * The car's driver profiles for every ccNC app: Guest (0), Driver 1 (1), Driver 2 (2).
 *
 * Reads go through the unit's UserProfileManager (framework, by reflection; no permission needed). The active
 * profile, names and stock avatars are also kept in Settings.System (user_profile_driver, user_profile_driver1_name,
 * user_profile_diriver2_name [stock spelling], user_profile_diriver1_img ...), so a ContentObserver on those tells
 * every app when the profile changes, wherever it was changed (ccNC Profile, the stock screen, the boot screen).
 * Photos and ccNC's own profile settings come from ccNC Profile's provider (content://dev.ccnc.profile).
 *
 * Off the car (no UserProfileManager) it runs a demo: three profiles, switching changes a local value.
 */
public final class Profiles {
    private Profiles() {}

    static final String TAG = "ccncProfiles";
    public static final int GUEST = 0, DRIVER1 = 1, DRIVER2 = 2, COUNT = 3;
    public static final String AUTHORITY = "dev.ccnc.profile";
    public static final Uri SETTINGS_URI = Uri.parse("content://" + AUTHORITY + "/settings");
    public static final String PROFILE_ACTIVITY = "dev.ccnc.profile/.MainActivity";
    private static final String[] SYS_KEYS = {"user_profile_driver", "user_profile_driver1_name", "user_profile_diriver2_name",
            "user_profile_guest_name", "user_profile_diriver1_img", "user_profile_diriver2_img"};
    private static final String[] DEFAULT_NAMES = {"Guest", "Driver 1", "Driver 2"};

    public interface Listener { void onProfilesChanged(); }

    public interface Done { void onDone(boolean ok, String message); }

    private static Object manager;
    private static Class<?> managerClass;
    private static int demoActive = DRIVER1;
    private static final Handler ui = new Handler(Looper.getMainLooper());

    private static synchronized Object manager(Context c) {
        if (manager == null) {
            try {
                managerClass = Class.forName("com.hkmc.userprofile.UserProfileManager");
                manager = managerClass.getConstructor(Context.class).newInstance(c.getApplicationContext());
            } catch (Throwable t) { manager = null; }
        }
        return manager;
    }

    private static Object call(Context c, String name, Class<?>[] types, Object... args) throws Exception {
        Object m = manager(c);
        if (m == null) throw new IllegalStateException("no UserProfileManager");
        Method x = managerClass.getMethod(name, types);
        return x.invoke(m, args);
    }

    /** True when the unit's profile service is there (false on an emulator). */
    public static boolean onCar(Context c) { return manager(c) != null; }

    /** The active profile (0 guest, 1, 2), or -1 if it can't be read. */
    public static int active(Context c) {
        if (!onCar(c)) return setting(c, "demo_active", demoActive);   // shared through ccNC Profile, when it's there
        try { return ((Number) call(c, "getUserType", new Class<?>[0])).intValue(); } catch (Throwable t) { return -1; }
    }

    /**
     * The profile to start on: the one the car is on now (UserProfileManager.getUserType()), exactly what the stock
     * start-up screen highlights first.
     *
     * Key / digital-key recognition isn't a feature the head unit checks for. On cars that support it, the body module
     * sends the key's profile over the micom (UserProfileService.onHandlePreBootUserTypeChanged: byte 1/2/3 = profile
     * 0/1/2, anything else = -1 "no match") and the service switches to that profile, so getUserType() already
     * follows the key. On cars without it that message never arrives and the last-used profile stays, as on the stock
     * screen. getPreBootUserType() is not used: it can't tell "no key support" from Guest (it reads 0 either way).
     */
    public static int keyProfile(Context c) {
        return active(c);
    }

    /**
     * Read-only diagnostics for the log (tag ccncProfiles): what an app can read about profiles, passwords and the
     * gear/reverse state (com.hkmc.pgs.PgsManager, used by the stock start-up screen). For the car checks before
     * ccNC's own start-up chooser; nothing is changed.
     */
    public static void logDiagnostics(final Context c) {
        new Thread(new Runnable() { @Override public void run() {
            StringBuilder b = new StringBuilder("diag:");
            b.append(" onCar=").append(onCar(c)).append(" active=").append(active(c)).append(" key=").append(keyProfile(c));
            b.append(" preBoot=");
            try { b.append(call(c, "getPreBootUserType", new Class<?>[0])); } catch (Throwable t) { b.append('?'); }
            for (int i = 1; i < COUNT; i++) {
                b.append(" pwd").append(i).append('=');
                try { b.append(call(c, "isNeededFrsOrPassword", new Class<?>[]{int.class}, i)); }
                catch (Throwable t) { b.append('?').append(t.getClass().getSimpleName()); }
            }
            try {
                Class<?> pk = Class.forName("com.hkmc.pgs.PgsManager");
                Object pm = pk.getConstructor(Context.class).newInstance(c.getApplicationContext());
                for (String m : new String[]{"requestReverseStatus", "getGearPStatus", "isGearNState"}) {
                    try { b.append(' ').append(m).append('=').append(pk.getMethod(m).invoke(pm)); }
                    catch (Throwable t) { b.append(' ').append(m).append("=?").append(t.getCause() != null ? t.getCause() : t); }
                }
            } catch (Throwable t) { b.append(" PgsManager=").append(t); }
            Log.i(TAG, b.toString());
        } }, "ccncProfileDiag").start();
    }

    public static String name(Context c, int id) {
        if (id < 0 || id >= COUNT) return "";
        if (onCar(c)) {
            try {
                String n = (String) call(c, "getUserName", new Class<?>[]{int.class}, id);
                if (n != null && !n.trim().isEmpty()) return n.trim();
            } catch (Throwable ignored) { }
        }
        return DEFAULT_NAMES[id];
    }

    /** The stock avatar number (UserProfileManager.getUserImg: 1-3, 5-12 built-in, 4 = Bluelink photo), or 0. */
    public static int stockImage(Context c, int id) {
        if (!onCar(c) || id == GUEST) return id == GUEST ? 0 : id;
        try { return ((Number) call(c, "getUserImg", new Class<?>[]{int.class}, id)).intValue(); } catch (Throwable t) { return id; }
    }

    /** The profile needs its password (or face) before the car switches to it. */
    public static boolean needsPassword(Context c, int id) {
        if (!onCar(c) || id == GUEST) return false;
        try { return ((Number) call(c, "isNeededFrsOrPassword", new Class<?>[]{int.class}, id)).intValue() == 2; } catch (Throwable t) { return false; }
    }

    /**
     * Switches to profile {@code id} in the background. {@code done} runs on the UI thread once the call returned
     * (the car then takes a few seconds to re-apply that profile's settings; watch() reports when it's active).
     * A profile with a password hands over to the car's own password screen.
     */
    public static void switchTo(final Context c, final int id, final Done done) {
        new Thread(new Runnable() { @Override public void run() {
            boolean ok;
            String msg = null;
            if (!onCar(c)) {
                demoActive = id;
                setSetting(c, "demo_active", id);   // every ccNC app follows (the provider announces the change)
                ok = true;
                ui.postDelayed(new Runnable() { @Override public void run() { notifyDemo(); } }, 1200);
            } else {
                try {
                    call(c, "switchUser", new Class<?>[]{int.class, boolean.class, boolean.class}, id, false, false);
                    ok = true;
                    Log.i(TAG, "switchUser(" + id + ")");
                } catch (Throwable t) {
                    ok = false;
                    msg = String.valueOf(t.getCause() != null ? t.getCause() : t);
                    Log.w(TAG, "switchUser(" + id + ") failed", t);
                }
            }
            final boolean fOk = ok;
            final String fMsg = msg;
            if (done != null) ui.post(new Runnable() { @Override public void run() { done.onDone(fOk, fMsg); } });
        } }, "ccncProfileSwitch").start();
    }

    // ---- change notifications ----

    private static final List<Listener> demoListeners = new ArrayList<Listener>();

    private static void notifyDemo() {
        for (Listener l : new ArrayList<Listener>(demoListeners)) l.onProfilesChanged();
    }

    /** Calls {@code l} on the UI thread whenever the profile, a name, an avatar or a ccNC photo changes. Returns a handle for unwatch(). */
    public static Object watch(Context c, final Listener l) {
        ContentObserver o = new ContentObserver(ui) {
            @Override public void onChange(boolean self) { l.onProfilesChanged(); }
        };
        ContentResolver r = c.getContentResolver();
        for (String k : SYS_KEYS) {
            try { r.registerContentObserver(Settings.System.getUriFor(k), false, o); } catch (Throwable ignored) { }
        }
        try { r.registerContentObserver(Uri.parse("content://" + AUTHORITY), true, o); } catch (Throwable ignored) { }
        synchronized (demoListeners) { demoListeners.add(l); }
        return new Object[]{o, l};
    }

    public static void unwatch(Context c, Object handle) {
        if (!(handle instanceof Object[])) return;
        Object[] h = (Object[]) handle;
        try { c.getContentResolver().unregisterContentObserver((ContentObserver) h[0]); } catch (Throwable ignored) { }
        synchronized (demoListeners) { demoListeners.remove(h[1]); }
    }

    // ---- ccNC Profile's photos and settings ----

    /** The profile's photo as ccNC Profile shows it (its own photo, a ccNC avatar or the stock one), round; null if ccNC Profile isn't there. */
    public static Bitmap photo(Context c, int id) {
        InputStream in = null;
        try {
            in = c.getContentResolver().openInputStream(Uri.parse("content://" + AUTHORITY + "/photo/" + id));
            return in == null ? null : BitmapFactory.decodeStream(in);
        } catch (Throwable t) {
            return null;
        } finally {
            if (in != null) try { in.close(); } catch (Throwable ignored) { }
        }
    }

    /** Saves ccNC Profile's setting {@code key} (default_profile, auto_advance); false if ccNC Profile isn't there. */
    public static boolean setSetting(Context c, String key, int value) {
        try {
            android.os.Bundle b = new android.os.Bundle();
            b.putInt("value", value);
            return c.getContentResolver().call(SETTINGS_URI, "set", key, b) != null;
        } catch (Throwable t) { return false; }
    }

    /** Opens ccNC Profile's photo picker for profile {@code id}. */
    public static void pickPhoto(Context c, int id) {
        try {
            c.startActivity(new android.content.Intent().setComponent(android.content.ComponentName.unflattenFromString(PROFILE_ACTIVITY))
                    .putExtra("photo", id).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Throwable ignored) { }
    }

    /** ccNC Profile's setting {@code key} (default_profile, auto_advance, auto_advance_custom), or {@code def}. */
    public static int setting(Context c, String key, int def) {
        Cursor cur = null;
        try {
            cur = c.getContentResolver().query(SETTINGS_URI, null, null, null, null);
            if (cur == null) return def;
            int col = cur.getColumnIndex(key);
            if (col < 0 || !cur.moveToFirst()) return def;
            return cur.getInt(col);
        } catch (Throwable t) {
            return def;
        } finally {
            if (cur != null) cur.close();
        }
    }
}
