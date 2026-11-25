package dev.ccnc.common;

import android.content.Context;
import android.graphics.Typeface;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Loads the CCNC signature typefaces from assets. Brand bundles live under
 * assets/fonts/{hyundai,kia}/ as subsetted TTFs produced by ccnc-common/build-fonts.sh.
 * Falls back to the closest system face when a bundle file is missing.
 */
public final class Fonts {

    public static final int REGULAR = 0;
    public static final int SEMIBOLD = 1;
    public static final int BOLD = 2;
    public static final int NUMERIC = 3;       // tabular-ish numerals for clocks/values
    public static final int NUMERIC_SEMIBOLD = 4;

    private static Typeface[] faces = new Typeface[5];
    private static String loadedBrand;

    private Fonts() {}

    /** Firmware product prefixes (ro.build.product, as HMG-API's car maps list them) -> brand. */
    private static final String[][] PRODUCTS = {
            {"CVEV", "kia"},                                   // Kia EV6
            {"CEEV", "hyundai"}, {"NEEV", "hyundai"},           // Hyundai Ioniq 6 (CE) / Ioniq 5 (NE, incl. NEEV23)
    };

    /**
     * Brand bundle name: "kia" or "hyundai", from (in order) the persist.sys.ccnc.brand override, the firmware product
     * prefix (ro.build.product), the vendor code in ro.product.brand ("...kmc" Kia, "...hmc" Hyundai), else Hyundai.
     * Resolved once, then cached.
     */
    public static synchronized String brand(Context c) { return brand(); }

    /** Same, for code without a Context (the brand comes from system properties only). */
    public static synchronized String brand() {
        if (loadedBrand == null) loadedBrand = detectBrand();
        return loadedBrand;
    }

    public static boolean kia() { return "kia".equals(brand()); }

    private static String prop(String key) {
        try {
            // android.os.SystemProperties is @hide, so go through reflection.
            Class<?> sp = Class.forName("android.os.SystemProperties");
            String v = (String) sp.getMethod("get", String.class, String.class).invoke(null, key, "");
            return v == null ? "" : v.trim();
        } catch (Throwable t) {
            return "";
        }
    }

    static String detectBrand() {
        String o = prop("persist.sys.ccnc.brand").toLowerCase(java.util.Locale.US);
        if (o.equals("kia") || o.equals("hyundai")) return o;
        String product = prop("ro.build.product").toUpperCase(java.util.Locale.US);
        for (String[] p : PRODUCTS) if (product.startsWith(p[0])) return p[1];
        String b = prop("ro.product.brand").toLowerCase(java.util.Locale.US);
        if (b.contains("kia") || b.endsWith("kmc")) return "kia";
        if (b.contains("hyundai") || b.endsWith("hmc")) return "hyundai";
        android.util.Log.w("ccnc", "brand unknown (product=" + product + ", brand=" + b + "): using hyundai");
        return "hyundai";
    }

    public static synchronized Typeface get(Context c, int which) {
        if (faces[which] != null) return faces[which];
        String[] names = {"sans_regular.ttf", "sans_semibold.ttf", "sans_bold.ttf",
                "numeric_regular.ttf", "numeric_semibold.ttf"};
        Typeface t = fromAsset(c, "fonts/" + brand(c) + "/" + names[which]);
        if (t == null) {
            if (which == BOLD) t = Typeface.create("sans-serif", Typeface.BOLD);
            else if (which == SEMIBOLD) t = Typeface.create("sans-serif", Typeface.BOLD);
            else t = Typeface.create("sans-serif", Typeface.NORMAL);
        }
        faces[which] = t;
        return t;
    }

    /**
     * KitKat's Typeface.createFromAsset leaves the asset stream open on some
     * builds and fails on compressed assets, so extract to cache first.
     */
    private static Typeface fromAsset(Context c, String path) {
        InputStream in = null;
        try {
            in = c.getAssets().open(path);
            File out = new File(c.getCacheDir(), path.replace('/', '_'));
            FileOutputStream fos = new FileOutputStream(out);
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) fos.write(buf, 0, n);
            fos.close();
            return Typeface.createFromFile(out);
        } catch (IOException e) {
            return null;
        } finally {
            if (in != null) try { in.close(); } catch (IOException ignored) {}
        }
    }
}
