package dev.ccnc.common;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;

/**
 * This app's own copy of ccNC Profile's photos and start-up settings (default profile, auto-advance). Asking ccNC
 * Profile for them starts its process, which at start-up costs about a second while the head unit is at its
 * busiest; the copy is read instead then, and brought up to date once start-up is over and whenever ccNC Profile
 * reports a change. Without a copy yet, ccNC Profile is asked as before.
 */
public final class ProfileCache {
    private ProfileCache() {}

    static final String TAG = "ccncProfiles";
    private static final String PREFS = "profile_cache";

    private static File dir(Context c) { return new File(c.getFilesDir(), "profile_cache"); }
    private static File file(Context c, int id) { return new File(dir(c), "photo_" + id + ".png"); }

    /** Profile {@code id}'s photo: the copy unless {@code fresh} (or there's no copy yet). Off the UI thread. */
    public static Bitmap photo(Context c, int id, boolean fresh) {
        if (!fresh) {
            File f = file(c, id);
            if (f.isFile()) {
                Bitmap b = BitmapFactory.decodeFile(f.getPath());
                if (b != null) return b;
            }
        }
        Bitmap b = Profiles.photo(c, id);
        if (b != null) save(c, id, b);
        return b;
    }

    /** ccNC Profile's setting {@code key}: the copy unless {@code fresh} (or there's no copy yet). Off the UI thread. */
    public static int setting(Context c, String key, int def, boolean fresh) {
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!fresh && p.contains(key)) return p.getInt(key, def);
        int v = Profiles.setting(c, key, Integer.MIN_VALUE);
        if (v == Integer.MIN_VALUE) return p.getInt(key, def);   // ccNC Profile not there: the copy, if any
        p.edit().putInt(key, v).apply();
        return v;
    }

    /** Brings the whole copy up to date, in the background. */
    public static void refresh(final Context ctx) {
        final Context c = ctx.getApplicationContext();
        new Thread(new Runnable() { @Override public void run() {
            for (int i = 0; i < Profiles.COUNT; i++) photo(c, i, true);
            for (String k : new String[]{"default_profile", "auto_advance", "auto_advance_custom"}) setting(c, k, 0, true);
        } }, "ccncProfileCache").start();
    }

    private static synchronized void save(Context c, int id, Bitmap b) {
        File d = dir(c);
        if (!d.isDirectory() && !d.mkdirs()) return;
        File tmp = new File(d, "photo_" + id + ".tmp"), f = file(c, id);
        try {
            FileOutputStream o = new FileOutputStream(tmp);
            try { b.compress(Bitmap.CompressFormat.PNG, 100, o); } finally { o.close(); }
            if (!tmp.renameTo(f)) tmp.delete();
        } catch (Throwable t) {
            tmp.delete();
            Log.w(TAG, "photo copy", t);
        }
    }
}
