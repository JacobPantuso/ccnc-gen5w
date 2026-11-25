package dev.ccnc.common;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * ccNC artwork bundled from ccnc-common/assets/ccnc (see build-assets.sh):
 * 90x90 app icons per brand, and the 1920x720 home wallpapers.
 */
public final class Art {
    private Art() {}

    private static final Map<String, Bitmap> cache = new HashMap<String, Bitmap>();
    private static final Bitmap MISSING = Bitmap.createBitmap(1, 1, Bitmap.Config.ALPHA_8);

    /** App icon by short name ("phone", "set_display"...), in the brand's colours; null if absent. */
    public static Bitmap icon(Context c, String name) {
        if (name == null) return null;
        if (Palette.ICONS != null) { // theme pack override, if the pack has this one
            Bitmap b = load(c, Palette.ICONS + "/" + name + ".png");
            if (b != null) return b;
        }
        return load(c, "ccnc/" + Fonts.brand(c) + "/icons/" + name + ".png");
    }

    /** Climate screen artwork by short name ("front", "driver_seat", "air_top_on"...); null if absent. */
    public static Bitmap climate(Context c, String name) {
        return load(c, "ccnc/climate/" + name + ".png");
    }

    /** Weather artwork: "clear_day", "few_clouds_night", "info_hum"...; null if absent. */
    public static Bitmap weather(Context c, String name) {
        return load(c, "ccnc/weather/" + name + ".png");
    }

    /** Profile avatar: "driver" or "guest"; null if absent. */
    public static Bitmap profile(Context c, String name) {
        return load(c, "ccnc/profile/" + name + ".png");
    }

    /** Driver-assistance graphic by file name without extension ("usm_da_015_on"); only bundled in ccnc-settings. */
    public static Bitmap da(Context c, String name) {
        return load(c, "ccnc/da/" + name + ".png");
    }

    /** Screenshot of an app's UI page ("climate", "radio"...); only bundled in ccnc-home. */
    public static Bitmap preview(Context c, String name) {
        return load(c, "ccnc/preview/" + name + ".jpg");
    }

    /** System-bar button glyph: "back" or "home". */
    public static Bitmap chrome(Context c, String name) {
        return load(c, "ccnc/chrome/" + name + ".png");
    }

    /** Wallpaper by name ("nebula", "starfield"); null if absent. */
    public static Bitmap wallpaper(Context c, String name) {
        if (name == null) return null;
        if (name.startsWith(ThemePacks.SCHEME)) return load(c, name); // a theme pack's own wallpaper
        return load(c, "ccnc/wallpaper/" + name + ".jpg");
    }

    private static synchronized Bitmap load(Context c, String path) {
        Bitmap b = cache.get(path);
        if (b != null) return b == MISSING ? null : b;
        InputStream in = null;
        try {
            in = path.startsWith(ThemePacks.SCHEME) ? ThemePacks.open(c, path) : c.getAssets().open(path);
            BitmapFactory.Options o = new BitmapFactory.Options();
            // ARGB_8888 even for the opaque wallpapers: RGB_565 bands their dark gradients.
            o.inPreferredConfig = Bitmap.Config.ARGB_8888;
            o.inScaled = false;
            b = BitmapFactory.decodeStream(in, null, o);
        } catch (Throwable e) {
            b = null;
        } finally {
            if (in != null) try { in.close(); } catch (IOException ignored) {}
        }
        cache.put(path, b == null ? MISSING : b);
        return b;
    }
}
