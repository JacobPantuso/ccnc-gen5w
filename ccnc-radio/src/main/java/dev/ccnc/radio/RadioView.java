package dev.ccnc.radio;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import dev.ccnc.common.Fonts;
import dev.ccnc.common.Palette;

/**
 * ccNC NA radio scene (naradiohmi QML) on a 1920 x 620 grid: 512 tab menu | 896 tuner (dial bar,
 * 96px frequency, 40px unit, favorite star, tune -/+) | 512 channel info. Favorites list screen.
 */
final class RadioView extends View {

    interface Actions {
        void seek(boolean up);
        void band(int band);
        void preset(Radio.Preset p);
        void tune(int raw);
    }

    private static final float DW = 1920f, DH = 620f;
    private static final int TEXT = 0xFFDFEDFE, SUB = 0x80DFEDFE, TICK = 0xFF555454, LABEL = 0xFF999999;
    private static final float CX = 960f; // centre of the 896 tuner column (512..1408)

    private final Actions actions;
    private Radio.State s = new Radio.State();
    private boolean favorites;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bmp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Map<String, Bitmap> cache = new HashMap<String, Bitmap>();
    private float k;

    private static final class Hit {
        final RectF box; final Runnable run;
        Hit(RectF b, Runnable r) { box = b; run = r; }
    }
    private final List<Hit> hits = new ArrayList<Hit>();
    private Hit down;
    private final RectF dial = new RectF();
    private boolean dialDown, dragged;
    private float dialX0, dialDx;

    RadioView(Context c, Actions a) {
        super(c);
        actions = a;
        setClickable(true);
    }

    void setState(Radio.State st) { s = st; invalidate(); }

    private Bitmap img(String name) {
        Bitmap b = cache.get(name);
        if (b == null) {
            try {
                InputStream in = getContext().getAssets().open("ccnc/radio/" + name + ".png");
                b = BitmapFactory.decodeStream(in);
                in.close();
            } catch (Throwable t) { b = null; }
            if (b != null) cache.put(name, b);
        }
        return b;
    }

    private void draw(Canvas c, String name, float l, float t, float w, float h) {
        Bitmap b = img(name);
        if (b == null) return;
        c.drawBitmap(b, null, new RectF(X(l), X(t), X(l + w), X(t + h)), bmp);
    }

    private float X(float d) { return d * k; }

    private boolean isAm() { return s.band == Radio.AM1; }

    /** Frequency in ccNC dial units: FM 0.01 MHz (8750..10790), AM kHz (530..1710). */
    private int units(int raw) {
        if (raw <= 0) return 0;
        if (isAm()) return raw;
        return raw >= 70000 ? raw / 10 : raw >= 7000 ? raw : raw >= 700 ? raw * 10 : raw * 100;
    }

    private int minU() { return isAm() ? 530 : 8750; }
    private int maxU() { return isAm() ? 1710 : 10790; }
    private int stepU() { return isAm() ? 10 : 20; }

    private int rawFromUnits(int u) {
        if (isAm()) return u;
        return s.frequency >= 7000 ? u : u / 10; // car reports FM in 0.1 MHz (875..1080)
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        float x = e.getX() / k, y = e.getY() / k;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                down = null;
                dialDown = false;
                for (int i = hits.size() - 1; i >= 0; i--) if (hits.get(i).box.contains(e.getX(), e.getY())) { down = hits.get(i); break; }
                if (down == null && !favorites && s.live && dial.contains(e.getX(), e.getY())) { dialDown = true; dragged = false; dialX0 = x; dialDx = 0; }
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (dialDown) { dialDx = x - dialX0; if (Math.abs(dialDx) > 10) dragged = true; invalidate(); }
                return true;
            case MotionEvent.ACTION_UP:
                Hit h = down;
                down = null;
                if (dialDown) {
                    dialDown = false;
                    int cur = units(s.frequency);
                    float du = dragged ? -dialDx / 1.5f : (x - CX) / 1.5f;
                    int u = Math.round(cur + du);
                    u = Math.max(minU(), Math.min(maxU(), u));
                    int rem = (u - minU()) % stepU();
                    u -= rem > stepU() / 2 ? rem - stepU() : rem;
                    u = Math.max(minU(), Math.min(maxU(), u));
                    actions.tune(rawFromUnits(u));
                }
                invalidate();
                if (h != null && h.box.contains(e.getX(), e.getY())) h.run.run();
                return true;
            case MotionEvent.ACTION_CANCEL:
                down = null; dialDown = false;
                invalidate();
                return true;
            default:
                return true;
        }
    }

    private void txt(Canvas c, String t, float x, float y, float size, int color, int face, Paint.Align a, float maxW) {
        text.setTypeface(Fonts.get(getContext(), face));
        text.setTextSize(X(size));
        text.setColor(color);
        text.setTextAlign(a);
        if (maxW > 0 && text.measureText(t) > X(maxW)) t = TextUtils.ellipsize(t, new TextPaint(text), X(maxW), TextUtils.TruncateAt.END).toString();
        c.drawText(t, X(x), X(y), text);
    }

    private Hit hit(float l, float t, float w, float h, Runnable r) {
        Hit x = new Hit(new RectF(X(l), X(t), X(l + w), X(t + h)), r);
        hits.add(x);
        return x;
    }

    private boolean pressed(Hit h) { return down == h; }

    private void tuneStep(boolean up) {
        int u = units(s.frequency);
        if (u <= 0) return;
        u += up ? stepU() : -stepU();
        if (u > maxU()) u = minU();
        if (u < minU()) u = maxU();
        actions.tune(rawFromUnits(u));
    }

    private boolean isPreset() {
        for (Radio.Preset p : s.presets) if (p != null && p.band == s.band && p.frequency == s.frequency) return true;
        return false;
    }

    private String fmt(int band, int raw) {
        if (raw <= 0) return "--";
        return Radio.freq(band, raw);
    }

    @Override protected void onDraw(Canvas c) {
        hits.clear();
        k = Math.min(getWidth() / DW, getHeight() / DH);

        // Left tab menu (512 wide): FM, AM, Favorites.
        String[] tabs = {"FM", "AM", "Favorites"};
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            boolean on = i == 2 ? favorites : (!favorites && (i == 1) == isAm());
            float t = 40f + i * 110f;
            Hit h = hit(24f, t, 464f, 96f, new Runnable() { @Override public void run() {
                if (idx == 2) favorites = true; else { favorites = false; if ((idx == 1) != isAm()) actions.band(idx == 1 ? Radio.AM1 : Radio.FM1); }
                invalidate(); } });
            fill.setColor(on ? Palette.ACCENT_DIM : pressed(h) ? Palette.PRESS : 0x00000000);
            c.drawRoundRect(h.box, X(16f), X(16f), fill);
            txt(c, tabs[i], 60f, t + 62f, 36f, on ? TEXT : SUB, on ? Fonts.SEMIBOLD : Fonts.REGULAR, Paint.Align.LEFT, 400f);
        }

        if (s.error != null) {
            txt(c, s.error, 1000f, 300f, 30f, SUB, Fonts.REGULAR, Paint.Align.CENTER, 1300f);
            return;
        }
        if (favorites) { drawFavorites(c); return; }
        drawTuner(c);
        drawInfo(c);
    }

    private void drawTuner(Canvas c) {
        int cur = units(s.frequency);
        float shift = dialDown && dragged ? dialDx : 0f;
        float dl = 512f, dt = 34f;
        dial.set(X(dl), X(dt), X(dl + 896f), X(dt + 132f));
        draw(c, "ra_bg_base_wide", dl, dt, 896f, 132f);
        int ref = cur > 0 ? cur : minU();
        int shown = dialDown && dragged ? Math.max(minU(), Math.min(maxU(), Math.round(ref - dialDx / 1.5f))) : ref;
        c.save();
        c.clipRect(dial);
        fill.setColor(TICK);
        int lo = Math.max(minU(), (int) Math.floor((ref - 310 - shift / 1.5f) / 10f) * 10);
        int hi = Math.min(maxU() + 10, ref + 310 - (int) (shift / 1.5f));
        for (int u = lo; u <= hi; u += 10) {
            float x = CX + (u - ref) * 1.5f + shift;
            boolean lng = u % 50 == 0;
            c.drawRect(X(x - 1f), X(dt), X(x + 1f), X(dt + (lng ? 30f : 15f)), fill);
            if (u % 100 == 0) txt(c, isAm() ? String.valueOf(u) : String.format(Locale.US, "%.1f", u / 100f), x, dt + 41f + 26f, 32f, LABEL, Fonts.NUMERIC, Paint.Align.CENTER, 0);
        }
        c.restore();
        draw(c, "ra_ic_dialpin", CX - 8f, dt + 132f - 24f, 16f, 24f);

        // Tune row: y = dial bottom + 135, 100 high.
        float ry = dt + 132f + 135f;
        String f = s.live ? (isAm() ? String.valueOf(shown) : String.format(Locale.US, "%.1f", shown / 100f)) : "--";
        if (s.live && cur <= 0) f = "--";
        txt(c, f, CX, ry + 76f, 96f, TEXT, Fonts.NUMERIC, Paint.Align.CENTER, 420f);
        text.setTextSize(X(96f));
        float fw = text.measureText(f) / k;
        txt(c, isAm() ? "kHz" : "MHz", CX + fw / 2f + 20f, ry + 96f, 40f, SUB, Fonts.REGULAR, Paint.Align.LEFT, 0);
        draw(c, isPreset() ? "ra_ic_favorite_on" : "ra_ic_favorite_off", CX - fw / 2f - 50f - 60f, ry + 20f, 60f, 60f);

        Hit m = hit(530f, ry, 100f, 100f, new Runnable() { @Override public void run() { tuneStep(false); } });
        Hit p = hit(1290f, ry, 100f, 100f, new Runnable() { @Override public void run() { tuneStep(true); } });
        draw(c, pressed(m) ? "ra_ic_tune_prev_01_p" : "ra_ic_tune_prev_01_n", 550f, ry + 20f, 60f, 60f);
        draw(c, pressed(p) ? "ra_ic_tune_next_01_p" : "ra_ic_tune_next_01_n", 1310f, ry + 20f, 60f, 60f);
    }

    private void drawInfo(Canvas c) {
        float x = 1448f;
        boolean none = s.live && s.signal <= 0;
        draw(c, none ? "ra_ic_nosignal" : "ra_ic_signal", x, 40f, 60f, 60f);
        if (none) txt(c, "No Signal", x + 76f, 84f, 32f, SUB, Fonts.REGULAR, Paint.Align.LEFT, 380f);
        txt(c, s.station, x, 190f, 44f, TEXT, Fonts.SEMIBOLD, Paint.Align.LEFT, 440f);
        txt(c, s.text, x, 240f, 28f, SUB, Fonts.REGULAR, Paint.Align.LEFT, 440f);
        Hit a = hit(x, 330f, 100f, 100f, new Runnable() { @Override public void run() { actions.seek(false); } });
        Hit b = hit(x + 220f, 330f, 100f, 100f, new Runnable() { @Override public void run() { actions.seek(true); } });
        draw(c, pressed(a) ? "ra_ic_nowplaying_prev_01_p" : "ra_ic_nowplaying_prev_01_n", x + 10f, 340f, 80f, 80f);
        draw(c, pressed(b) ? "ra_ic_nowplaying_next_01_p" : "ra_ic_nowplaying_next_01_n", x + 230f, 340f, 80f, 80f);
    }

    private void drawFavorites(Canvas c) {
        int n = 0;
        for (int i = 0; i < s.presets.length; i++) {
            final Radio.Preset p = s.presets[i];
            if (p == null) continue;
            float t = 30f + n * 84f;
            n++;
            boolean cur = s.preset == p.number && s.band == p.band;
            Hit h = hit(540f, t, 1300f, 80f, new Runnable() { @Override public void run() { actions.preset(p); } });
            fill.setColor(cur ? Palette.ACCENT_DIM : pressed(h) ? Palette.PRESS : 0x00000000);
            c.drawRoundRect(h.box, X(12f), X(12f), fill);
            txt(c, fmt(p.band, p.frequency) + (p.band == Radio.AM1 ? " kHz" : " MHz"), 580f, t + 54f, 36f, TEXT, Fonts.NUMERIC, Paint.Align.LEFT, 400f);
            txt(c, p.name, 900f, t + 52f, 32f, SUB, Fonts.REGULAR, Paint.Align.LEFT, 700f);
            draw(c, "ra_ic_favorite_on", 1760f, t + 10f, 60f, 60f);
        }
        if (n == 0) txt(c, "No Favorites Saved.", 1176f, 300f, 36f, SUB, Fonts.REGULAR, Paint.Align.CENTER, 1000f);
    }
}
