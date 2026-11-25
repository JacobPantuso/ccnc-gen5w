package dev.ccnc.common;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.NinePatchDrawable;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads raw (uncompiled) ccNC ".9.png" files from assets/ccnc/ui and builds NinePatchDrawables by parsing
 * the 1px border (stretch regions top/left, padding bottom/right). Plain PNGs from the same folder:
 * {@link #bitmap}. File names are given without extension, e.g. "co_bg_list_h_n.9".
 */
public final class NinePatchArt {
    private NinePatchArt() {}

    private static final Map<String, Bitmap> bmp = new HashMap<String, Bitmap>();

    public static synchronized Bitmap bitmap(Context c, String name) {
        Bitmap b = bmp.get(name);
        if (b != null) return b;
        InputStream in = null;
        try {
            in = c.getAssets().open("ccnc/ui/" + name + ".png");
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inScaled = false;
            b = BitmapFactory.decodeStream(in, null, o);
            if (b != null) bmp.put(name, b);
            return b;
        } catch (Throwable t) {
            return null;
        } finally {
            try { if (in != null) in.close(); } catch (Throwable ignored) { }
        }
    }

    /** A fresh drawable for "<name>.9" (a new instance each call: drawables hold state). Null if absent. */
    public static Drawable patch(Context c, String name) {
        try {
            Bitmap raw = bitmap(c, name);
            if (raw == null) return null;
            int w = raw.getWidth() - 2, h = raw.getHeight() - 2;
            if (w <= 0 || h <= 0) return null;
            int[] top = new int[w], left = new int[h], bot = new int[w], right = new int[h];
            raw.getPixels(top, 0, w, 1, 0, w, 1);
            raw.getPixels(bot, 0, w, 1, raw.getHeight() - 1, w, 1);
            raw.getPixels(left, 0, 1, 0, 1, 1, h);
            raw.getPixels(right, 0, 1, raw.getWidth() - 1, 1, 1, h);
            int[] xd = runs(top), yd = runs(left);
            int[] pad = new int[4];
            int[] pr = runs(bot), pt = runs(right);
            pad[0] = pr.length >= 2 ? pr[0] : 0;
            pad[1] = pr.length >= 2 ? w - pr[pr.length - 1] : 0;
            pad[2] = pt.length >= 2 ? pt[0] : 0;
            pad[3] = pt.length >= 2 ? h - pt[pt.length - 1] : 0;
            Bitmap inner = Bitmap.createBitmap(raw, 1, 1, w, h);
            ByteBuffer bb = ByteBuffer.allocate(32 + 4 * (xd.length + yd.length + 9 + 0)).order(ByteOrder.nativeOrder());
            int colors = 9;
            bb = ByteBuffer.allocate(32 + 4 * (xd.length + yd.length + colors)).order(ByteOrder.nativeOrder());
            bb.put((byte) 1).put((byte) xd.length).put((byte) yd.length).put((byte) colors);
            bb.putInt(0).putInt(0);
            bb.putInt(pad[0]).putInt(pad[1]).putInt(pad[2]).putInt(pad[3]);
            bb.putInt(0);
            for (int v : xd) bb.putInt(v);
            for (int v : yd) bb.putInt(v);
            for (int i = 0; i < colors; i++) bb.putInt(1); // NO_COLOR
            return new NinePatchDrawable(c.getResources(), inner, bb.array(), new Rect(pad[0], pad[2], pad[1], pad[3]), name);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Start/end pairs of non-transparent-black runs along a border strip. Whole strip empty -> whole-span stretch not assumed. */
    private static int[] runs(int[] px) {
        int[] tmp = new int[px.length + 2];
        int n = 0;
        boolean in = false;
        for (int i = 0; i < px.length; i++) {
            boolean on = (px[i] >>> 24) > 0x80 && (px[i] & 0xFFFFFF) == 0;
            if (on && !in) { tmp[n++] = i; in = true; }
            else if (!on && in) { tmp[n++] = i; in = false; }
        }
        if (in) tmp[n++] = px.length;
        int[] r = new int[n];
        System.arraycopy(tmp, 0, r, 0, n);
        return r;
    }
}
