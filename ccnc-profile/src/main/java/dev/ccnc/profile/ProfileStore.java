package dev.ccnc.profile;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

import dev.ccnc.common.Profiles;

/**
 * ccNC's own profile settings and photos (the car's profile service only knows its 11 built-in avatars):
 *   default_profile  -1 follow the key (stock), else 0 / 1 / 2: chosen on the start-up profile screen
 *   auto_advance     seconds the profile chooser counts down before it continues with the selected profile
 *                    (default 3; -1 = off, wait for a tap). auto_advance_custom: 1 once the owner picked a value
 *                    (until then ccNC Home leaves the stock start-up screen's own timing alone)
 *   photo_<id>       "" (the car's own avatar), "avatar:<1-11>" (a ccNC avatar) or "custom" (files/photo_<id>.png)
 * Other ccNC apps read these through ProfileProvider (content://dev.ccnc.profile).
 */
final class ProfileStore {
    private ProfileStore() {}

    static final String PREFS = "profiles";
    static final int AVATARS = 11;
    static final int PHOTO_SIZE = 220;
    static final int DEFAULT_AUTO = 3;
    static final Uri ROOT = Uri.parse("content://" + Profiles.AUTHORITY);

    static SharedPreferences prefs(Context c) { return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }

    static int defaultProfile(Context c) { return prefs(c).getInt("default_profile", -1); }
    static int autoAdvance(Context c) { return prefs(c).getInt("auto_advance", DEFAULT_AUTO); }
    static boolean autoAdvanceCustom(Context c) { return prefs(c).contains("auto_advance"); }
    static String photoChoice(Context c, int id) { return prefs(c).getString("photo_" + id, ""); }

    static void setDefaultProfile(Context c, int id) { prefs(c).edit().putInt("default_profile", id).commit(); changed(c); }
    static void setAutoAdvance(Context c, int secs) { prefs(c).edit().putInt("auto_advance", secs).commit(); changed(c); }

    /** Writes from ccNC Setup (ProfileProvider.call "set"): only these keys and values. */
    static boolean set(Context c, String key, int v) {
        if ("default_profile".equals(key) && v >= -1 && v <= 2) { setDefaultProfile(c, v); return true; }
        if ("auto_advance".equals(key) && v >= -1 && v <= 30) { setAutoAdvance(c, v); return true; }
        return false;
    }

    /** "" (the car's own avatar), "avatar:<0-11>" (0 = the guest picture) or "custom". */
    static void setChoice(Context c, int id, String choice) {
        prefs(c).edit().putString("photo_" + id, choice).commit();
        changed(c);
    }

    /** Saves {@code src} (any size) as profile {@code id}'s photo: centre square, PHOTO_SIZE px. */
    static boolean setCustomPhoto(Context c, int id, Bitmap src) {
        if (src == null) return false;
        int s = Math.min(src.getWidth(), src.getHeight());
        Bitmap out = Bitmap.createBitmap(PHOTO_SIZE, PHOTO_SIZE, Bitmap.Config.ARGB_8888);
        new Canvas(out).drawBitmap(src, new Rect((src.getWidth() - s) / 2, (src.getHeight() - s) / 2, (src.getWidth() + s) / 2, (src.getHeight() + s) / 2),
                new Rect(0, 0, PHOTO_SIZE, PHOTO_SIZE), new Paint(Paint.FILTER_BITMAP_FLAG));
        File f = new File(c.getFilesDir(), "photo_" + id + ".png");
        try {
            FileOutputStream o = new FileOutputStream(f);
            try { out.compress(Bitmap.CompressFormat.PNG, 100, o); } finally { o.close(); }
        } catch (Exception e) { return false; }
        prefs(c).edit().putString("photo_" + id, "custom").commit();
        changed(c);
        return true;
    }

    static void changed(Context c) {
        try { c.getContentResolver().notifyChange(ROOT, null); } catch (Throwable ignored) { }
    }

    /** ccNC avatar n (1-11), or the guest picture for n = 0. */
    static Bitmap avatar(Context c, int n) {
        String name = n <= 0 ? "frs_ic_thumbnail_guest" : String.format(java.util.Locale.US, "frs_ic_detailinfo_01_l_%02d", n);
        try {
            InputStream in = c.getAssets().open("ccnc/frs/" + name + ".png");
            try { return BitmapFactory.decodeStream(in); } finally { in.close(); }
        } catch (Exception e) { return null; }
    }

    /**
     * The picture profile {@code id} shows: the owner's photo, the ccNC avatar they picked, or the car's own avatar
     * (stock numbers 1-3 and 5-12 are its 11 built-in images, in ccNC's order; 4 is a Bluelink photo we can't read).
     */
    static Bitmap photo(Context c, int id) {
        String p = photoChoice(c, id);
        if (p.equals("custom")) {
            Bitmap b = BitmapFactory.decodeFile(new File(c.getFilesDir(), "photo_" + id + ".png").getPath());
            if (b != null) return b;
        }
        if (p.startsWith("avatar:")) {
            try { Bitmap b = avatar(c, Integer.parseInt(p.substring(7))); if (b != null) return b; } catch (NumberFormatException ignored) { }
        }
        if (id == Profiles.GUEST) return avatar(c, 0);
        int st = Profiles.stockImage(c, id);
        int n = st >= 1 && st <= 3 ? st : st >= 5 && st <= 12 ? st - 1 : id;
        return avatar(c, n);
    }
}
