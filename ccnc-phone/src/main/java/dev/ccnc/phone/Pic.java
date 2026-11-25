package dev.ccnc.phone;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/** Loads ccNC bt_phone art from assets/bt and draws .9.png files as real nine-patches. */
final class Pic {
    private Pic() {}

    private static final Map<String, Pic9> cache = new HashMap<String, Pic9>();
    private static final Paint P = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);

    static final class Pic9 {
        Bitmap bm; int x1, x2, y1, y2; boolean nine;
        int w() { return bm.getWidth(); }
        int h() { return bm.getHeight(); }

        void draw(Canvas c, int l, int t, int r, int b) {
            Rect d = new Rect();
            if (!nine) { d.set(l, t, r, b); c.drawBitmap(bm, null, d, P); return; }
            int w = bm.getWidth(), h = bm.getHeight();
            int[] sx = {0, x1, x2, w}, sy = {0, y1, y2, h};
            int rw = w - x2, bh = h - y2;
            int[] dx = {l, l + x1, r - rw, r}, dy = {t, t + y1, b - bh, b};
            Rect s = new Rect();
            for (int j = 0; j < 3; j++) for (int i = 0; i < 3; i++) {
                if (sx[i + 1] <= sx[i] || sy[j + 1] <= sy[j]) continue;
                s.set(sx[i], sy[j], sx[i + 1], sy[j + 1]);
                d.set(dx[i], dy[j], dx[i + 1], dy[j + 1]);
                c.drawBitmap(bm, s, d, P);
            }
        }
    }

    static Pic9 get(Context c, String name) {
        Pic9 p = cache.get(name);
        if (p != null) return p;
        p = load(c, name);
        cache.put(name, p);
        return p;
    }

    private static Pic9 load(Context c, String name) {
        Pic9 p = new Pic9();
        try {
            InputStream in = c.getAssets().open("bt/" + name);
            Bitmap raw = BitmapFactory.decodeStream(in);
            in.close();
            if (name.endsWith(".9.png")) {
                int w = raw.getWidth(), h = raw.getHeight();
                int a = -1, b = -1;
                for (int x = 1; x < w - 1; x++) if ((raw.getPixel(x, 0) >>> 24) > 128) { if (a < 0) a = x; b = x; }
                int e = -1, f = -1;
                for (int y = 1; y < h - 1; y++) if ((raw.getPixel(0, y) >>> 24) > 128) { if (e < 0) e = y; f = y; }
                p.bm = Bitmap.createBitmap(raw, 1, 1, w - 2, h - 2);
                p.nine = a >= 0 && e >= 0;
                p.x1 = Math.max(0, a - 1); p.x2 = Math.max(p.x1, b);
                p.y1 = Math.max(0, e - 1); p.y2 = Math.max(p.y1, f);
            } else {
                p.bm = raw;
            }
        } catch (Throwable t) {
            p.bm = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        }
        return p;
    }
}
