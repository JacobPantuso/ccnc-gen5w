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

    /**
     * Light themes: {@code b} with its white and light-grey pixels (the ccNC icon artwork is white line art for a
     * dark background) redrawn in {@code ink}, keeping each pixel's alpha and its shade (a 60% grey stays lighter
     * than a white). Coloured pixels (accent, red record, orange heat) are kept as they are. Dark themes, or null:
     * {@code b} itself. Cached per bitmap and ink.
     */
    public static Bitmap ink(Bitmap b, int ink) {
        if (b == null || !Palette.LIGHT) return b;
        String key = System.identityHashCode(b) + "/" + Integer.toHexString(ink);
        synchronized (inked) {
            Bitmap done = inked.get(key);
            if (done != null) return done;
        }
        int w = b.getWidth(), h = b.getHeight();
        int[] px = new int[w * h];
        b.getPixels(px, 0, w, 0, 0, w, h);
        int ir = (ink >> 16) & 0xFF, ig = (ink >> 8) & 0xFF, ib = ink & 0xFF;
        for (int i = 0; i < px.length; i++) {
            int p = px[i], a = p >>> 24;
            if (a == 0) continue;
            int r = (p >> 16) & 0xFF, g = (p >> 8) & 0xFF, bl = p & 0xFF;
            int max = Math.max(r, Math.max(g, bl)), min = Math.min(r, Math.min(g, bl));
            if (max - min > 40 || max < 110) continue;               // coloured or already dark: keep
            float k = max / 255f;                                      // white -> full ink, light grey -> lighter ink
            int na = Math.round(a * (0.35f + 0.65f * k));
            px[i] = (na << 24) | (ir << 16) | (ig << 8) | ib;
        }
        Bitmap out = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888);
        out.setDensity(b.getDensity());
        synchronized (inked) { inked.put(key, out); }
        return out;
    }

    /** {@link #ink(Bitmap, int)} in the theme's text colour. */
    public static Bitmap ink(Bitmap b) { return ink(b, Palette.TEXT); }

    private static final java.util.HashMap<String, Bitmap> inked = new java.util.HashMap<String, Bitmap>();

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
