package dev.ccnc.ev;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.SystemClock;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;

import dev.ccnc.common.Fonts;

/** ccNC EV scene (evhmi): Home.Main with the car battery graph, plus the tab bar (Home, Energy Info, Charging, Setting). */
final class EvView extends View {

    interface Actions { void openStock(); }

    private static final int HIGHLIGHT = 0xFF27A6D0, NORMAL = 0xFFDFEDFE, DIM = 0x80DFEDFE;
    private static final String[] TABS = {"ev_home", "energy_info", "charging", "setting"};

    private final Actions actions;
    private EvData.Snapshot s = new EvData.Snapshot();
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final HashMap<String, Bitmap> cache = new HashMap<String, Bitmap>();
    private final RectF r = new RectF();
    private final RectF[] tabBox = new RectF[4];
    private float k;
    private int tab = 0, pressed = -1;

    EvView(Context c, Actions a) {
        super(c);
        actions = a;
        for (int i = 0; i < 4; i++) tabBox[i] = new RectF();
        setClickable(true);
    }

    void setSnapshot(EvData.Snapshot snap) { s = snap; invalidate(); }

    @Override public boolean onTouchEvent(MotionEvent e) {
        int hit = -1;
        for (int i = 0; i < 4; i++) if (tabBox[i].contains(e.getX(), e.getY())) hit = i;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: pressed = hit; invalidate(); return true;
            case MotionEvent.ACTION_UP:
                int f = pressed;
                pressed = -1;
                if (f >= 0 && f == hit) {
                    if (f == 3) actions.openStock(); else tab = f;
                }
                invalidate();
                return true;
            case MotionEvent.ACTION_CANCEL: pressed = -1; invalidate(); return true;
            default: return true;
        }
    }

    private Bitmap bmp(String name, boolean ninePatch) {
        String key = name + (ninePatch ? "#9" : "");
        Bitmap b = cache.get(key);
        if (b != null) return b;
        try {
            InputStream in = getContext().getAssets().open("ccnc/ev/" + name + ".png");
            b = BitmapFactory.decodeStream(in);
            in.close();
            if (b != null && ninePatch) b = Bitmap.createBitmap(b, 1, 1, b.getWidth() - 2, b.getHeight() - 2);
        } catch (Throwable t) { b = null; }
        if (b != null) cache.put(key, b);
        return b;
    }

    private float X(float d) { return d * k; }

    private void img(Canvas c, String name, float x, float y, float w, float h, boolean nine) {
        Bitmap b = bmp(name, nine);
        if (b == null) return;
        r.set(X(x), X(y), X(x + w), X(y + h));
        c.drawBitmap(b, null, r, p);
    }

    /** ccNC card (ev_bg_main.9) drawn as a real 9-patch, so its rounded corners keep their shape at any size. */
    private void card(Canvas c, float x, float y, float w, float h) {
        android.graphics.drawable.Drawable d = dev.ccnc.common.NinePatchArt.patch(getContext(), "ev_bg_main.9");
        if (d == null) { img(c, "ev_bg_main.9", x, y, w, h, true); return; }
        d.setBounds(Math.round(X(x)), Math.round(X(y)), Math.round(X(x + w)), Math.round(X(y + h)));
        d.draw(c);
    }

    private void txt(Canvas c, String t, float x, float y, float size, int color, int face, Paint.Align a) {
        p.setTypeface(Fonts.get(getContext(), face));
        p.setTextSize(X(size));
        p.setColor(color);
        p.setTextAlign(a);
        c.drawText(t, X(x), X(y), p);
    }

    private float width(String t, float size, int face) {
        p.setTypeface(Fonts.get(getContext(), face));
        p.setTextSize(X(size));
        return p.measureText(t) / k;
    }

    @Override protected void onDraw(Canvas c) {
        k = Math.min(getWidth() / 1920f, getHeight() / 626f);
        float ox = (getWidth() / k - 1920f) / 2f;
        c.save();
        c.translate(X(ox), 0);
        int soc = s.soc; // already in % (EvData.half)
        if (tab == 0) home(c, soc);
        else if (tab == 1) energy(c);
        else charging(c);
        tabs(c);
        c.restore();
    }

    private void home(Canvas c, int soc) {
        boolean chg = s.charging();
        // CarBatteryGraph (524 x 230) at left 107, top 136; EVHomeBattery 254 x 48 at (144, 128) inside it.
        float gx = 107f, gy = 136f;
        img(c, "ev_etc_electricityuse_sedan", gx, gy, 524f, 230f, false);
        img(c, "ev_etc_battery_bg", gx + 144f, gy + 128f, 254f, 48f, false);
        if (soc >= 0) {
            int idx = Math.max(0, Math.min(20, (100 - soc) / 5));
            String set = chg ? "charger" : soc <= 10 ? "r_charger" : soc <= 20 ? "o_charger" : "charger";
            img(c, "ev_etc_battery_" + set + "_" + (idx < 10 ? "0" : "") + idx, gx + 144f, gy + 128f, 254f, 48f, false);
        }
        if (chg && soc >= 0) sweep(c, gx + 144f, gy + 128f, soc);
        img(c, "ev_etc_battery_line", gx + 144f, gy + 128f, 254f, 48f, false);
        // Battery rate: 78 px SemiBold number + 56 px "%", centred above the battery.
        String rate = soc < 0 ? "--" : String.valueOf(soc);
        float w = width(rate, 78f, Fonts.SEMIBOLD) + 8f + width("%", 56f, Fonts.REGULAR) + (chg ? 54f : 0f);
        float x = gx + 262f - w / 2f, base = gy + 50f + 65f;
        if (chg) { img(c, "ev_ic_main_charging", x, gy + 50f, 32f, 62f, false); x += 54f; postInvalidateDelayed(40); }
        txt(c, rate, x, base, 78f, NORMAL, Fonts.SEMIBOLD, Paint.Align.LEFT);
        txt(c, "%", x + width(rate, 78f, Fonts.SEMIBOLD) + 8f, base, 56f, NORMAL, Fonts.REGULAR, Paint.Align.LEFT);
        // Range (DTE) on the right, in the unit the car reports.
        String d = s.dte < 0 ? "--" : String.valueOf(s.dte);
        String u = s.km() ? "km" : "mi";
        float dw = width(d, 78f, Fonts.SEMIBOLD) + 10f + width(u, 40f, Fonts.REGULAR);
        float dx = 1920f - 178f - dw;
        txt(c, d, dx, 38f + 110f, 78f, NORMAL, Fonts.SEMIBOLD, Paint.Align.LEFT);
        txt(c, u, dx + width(d, 78f, Fonts.SEMIBOLD) + 10f, 38f + 110f, 40f, DIM, Fonts.REGULAR, Paint.Align.LEFT);
        if (chg) {
            // q037 realTimeChargingInfo: 40 px icon + "AC Charging" 32 px SemiBold dim, below the battery graph.
            String t = s.fast > 0 ? "DC Charging" : "AC Charging";
            float tw = width(t, 32f, Fonts.SEMIBOLD) + 49f, tx = gx + 262f - tw / 2f, ty = gy + 230f + 20f;
            img(c, "ev_ic_main_charging_s", tx, ty, 40f, 40f, false);
            txt(c, t, tx + 49f, ty + 31f, 32f, DIM, Fonts.SEMIBOLD, Paint.Align.LEFT);
        }
        String nd = nextDeparture();
        String[][] rows = chg && s.remMin > 0
                ? new String[][]{{"Charging Time", (s.remMin / 60) + " h " + (s.remMin % 60) + " m"}, {"Max. % Charge", limits()}, {"Next Departure", s.depart == null ? "--" : nd == null ? "None" : nd}}
                : new String[][]{{"Max. % Charge", limits()}, {"Next Departure", s.depart == null ? "--" : nd == null ? "None" : nd}, {"Off-peak Hours", offPeakText()}};
        infoRows(c, 1060f, 196f, 1742f - 1060f, rows);
    }

    /** Home info list under the range (ccNC Home.Main widgets: charge limit, schedule): label left, value right. */
    private void infoRows(Canvas c, float x, float y, float w, String[][] rows) {
        for (int i = 0; i < rows.length; i++) {
            float ry = y + i * 92f;
            txt(c, rows[i][0], x, ry + 52f, 30f, DIM, Fonts.REGULAR, Paint.Align.LEFT);
            txt(c, rows[i][1], x + w, ry + 52f, 32f, NORMAL, Fonts.SEMIBOLD, Paint.Align.RIGHT);
            if (i < rows.length - 1) img(c, "co_bg_list_line.9", x, ry + 91f, w, 2f, true);
        }
    }

    private String time(int h, int m) {
        if (android.text.format.DateFormat.is24HourFormat(getContext())) return String.format(Locale.US, "%02d:%02d", h, m);
        return String.format(Locale.US, "%d:%02d %s", h % 12 == 0 ? 12 : h % 12, m, h < 12 ? "AM" : "PM");
    }

    private static final String[] DAY = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};

    private String days(int bits) {
        if (bits == 0x7F) return "Every day";
        if (bits == 0x3E) return "Weekdays";
        if (bits == 0x41) return "Weekends";
        if (bits == 0) return "Once";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 7; i++) if ((bits & 1 << i) != 0) b.append(b.length() > 0 ? " " : "").append(DAY[i]);
        return b.toString();
    }

    /** Next of the (up to two) set departures from now, as "Mon 7:30 AM", or null. */
    private String nextDeparture() {
        java.util.Calendar now = java.util.Calendar.getInstance(), best = null;
        for (int i = 0; i < 2; i++) {
            if (!s.departSet(i)) continue;
            int bits = s.departDays(i);
            for (int d = 0; d < 8; d++) {
                java.util.Calendar t = (java.util.Calendar) now.clone();
                t.add(java.util.Calendar.DAY_OF_YEAR, d);
                t.set(java.util.Calendar.HOUR_OF_DAY, s.departHour(i));
                t.set(java.util.Calendar.MINUTE, s.departMin(i));
                t.set(java.util.Calendar.SECOND, 0);
                int dow = t.get(java.util.Calendar.DAY_OF_WEEK) - 1;
                if (t.before(now) || bits != 0 && (bits & 1 << dow) == 0) continue;
                if (best == null || t.before(best)) best = t;
                break;
            }
        }
        if (best == null) return null;
        return DAY[best.get(java.util.Calendar.DAY_OF_WEEK) - 1] + " " + time(best.get(java.util.Calendar.HOUR_OF_DAY), best.get(java.util.Calendar.MINUTE));
    }

    private String offPeakText() {
        if (s.offPeak == null) return "--";
        if (!s.offPeakKnown() || s.offMode() == 0) return "Not set";
        return time(s.offStartH(), s.offStartM()) + " \u2013 " + time(s.offEndH(), s.offEndM());
    }

    private String limits() {
        String a = s.targetSlow < 0 ? "--" : s.targetSlow + "%", d = s.targetQuick < 0 ? "--" : s.targetQuick + "%";
        return "AC " + a + "  \u00b7  DC " + d;
    }

    private static final int[] DOT = {0xFF48AEC3, 0xFFC6784D, 0xFFE1BB31, 0xFF2BBB46};
    private static final String[] TITLE = {"Drivetrain", "Electronics", "Climate", "Battery Care"};

    /** EV_Energy_Info / Electricity Use (q040 + q051): ratio bar and four 293 x 240 cards. Trip energy sums from the HEV service. */
    private void energy(Canvas c) {
        int[] wh = {s.whDrive, s.whElec, s.whClimate, s.whBatt};
        long total = 0;
        for (int w : wh) if (w > 0) total += w;
        // HRatioGraph: 40 px bar at (62, 40); no-data = empty track.
        r.set(X(62f), X(40f), X(1857f), X(80f));
        p.setColor(0xFF19242E);
        c.drawRoundRect(r, X(20f), X(20f), p);
        if (total > 0) {
            float x = 62f, wAll = 1795f;
            c.save();
            c.clipRect(r);
            Path clip = new Path();
            clip.addRoundRect(r, X(20f), X(20f), Path.Direction.CW);
            c.clipPath(clip);
            for (int i = 0; i < 4; i++) {
                if (wh[i] <= 0) continue;
                float w = wAll * wh[i] / total;
                p.setColor(DOT[i]);
                c.drawRect(X(x), X(40f), X(x + w), X(80f), p);
                x += w;
            }
            c.restore();
        }
        for (int i = 0; i < 4; i++) {
            float x = 62f + i * 306f, y = 140f;
            card(c, x, y, 293f, 240f);
            img(c, "ev_ic_widget_dot_0" + (i + 1), x + 20f, y + 20f, 16f, 16f, false);
            txt(c, TITLE[i], x + 44f, y + 38f, 28f, DOT[i], Fonts.SEMIBOLD, Paint.Align.LEFT);
            String amt = wh[i] < 0 ? "--" : String.format(Locale.US, "%.1f", wh[i] / 1000f);
            txt(c, amt, x + 20f, y + 130f, 48f, NORMAL, Fonts.SEMIBOLD, Paint.Align.LEFT);
            txt(c, "kWh", x + 30f + width(amt, 48f, Fonts.SEMIBOLD), y + 130f, 30f, NORMAL, Fonts.REGULAR, Paint.Align.LEFT);
            img(c, "co_bg_list_line.9", x + 20f, y + 164f, 253f, 2f, true);
            String pc = total > 0 && wh[i] >= 0 ? String.valueOf(Math.round(100f * wh[i] / total)) : "--";
            txt(c, pc, x + 20f, y + 218f, 48f, NORMAL, Fonts.SEMIBOLD, Paint.Align.LEFT);
            txt(c, "%", x + 30f + width(pc, 48f, Fonts.SEMIBOLD), y + 218f, 30f, NORMAL, Fonts.REGULAR, Paint.Align.LEFT);
        }
    }

    /** q056 slider rows: 1080 x 40 bar (track #19242e, target #364350, current = highlight), title, current % and "(About N km)". */
    private void charging(Canvas c) {
        int soc = s.soc; // already in % (EvData.half)
        String[] title = {"AC Charger", "DC Charger"};
        int[] target = {s.targetSlow, s.targetQuick};
        int[] dte = {s.dteSlow, s.dteQuick};
        for (int i = 0; i < 2; i++) {
            float x = 90f, y = 40f + i * 250f;
            txt(c, title[i], x, y + 32f, 32f, NORMAL, Fonts.REGULAR, Paint.Align.LEFT);
            float by = y + 70f;
            r.set(X(x), X(by), X(x + 860f), X(by + 40f));
            p.setColor(0xFF19242E);
            c.drawRoundRect(r, X(20f), X(20f), p);
            if (target[i] >= 0) {
                r.set(X(x), X(by), X(x + 860f * target[i] / 100f), X(by + 40f));
                p.setColor(0xFF364350);
                c.drawRoundRect(r, X(20f), X(20f), p);
            }
            if (soc >= 0) {
                float cw = 860f * soc / 100f;
                r.set(X(x), X(by), X(x + cw), X(by + 40f));
                p.setColor(HIGHLIGHT);
                c.drawRoundRect(r, X(20f), X(20f), p);
                String t = soc + "%";
                txt(c, t, x + Math.max(10f, cw - width(t, 28f, Fonts.REGULAR) - 20f), by + 30f, 28f, NORMAL, Fonts.REGULAR, Paint.Align.LEFT);
            } else {
                txt(c, "--%", x + 10f, by + 30f, 28f, NORMAL, Fonts.REGULAR, Paint.Align.LEFT);
            }
            // Handle at the target: circle with the target value.
            if (target[i] >= 0) {
                float hx = x + 860f * target[i] / 100f - 47f;
                p.setColor(0xFFDFEDFE);
                c.drawCircle(X(hx + 47f), X(by + 20f), X(47f), p);
                txt(c, target[i] + "%", hx + 47f, by + 31f, 28f, 0xFF0B1117, Fonts.SEMIBOLD, Paint.Align.CENTER);
            }
            if (dte[i] > 0) txt(c, "(About " + dte[i] + " " + (s.km() ? "km" : "mi") + ")", x, by + 90f, 28f, DIM, Fonts.REGULAR, Paint.Align.LEFT);
        }
        schedule(c, 1080f, 30f, 1830f - 1080f);
    }

    /** Scheduled Charging and Climate (stock EV3 Charge Management), read-only: both departures and off-peak hours. */
    private void schedule(Canvas c, float x, float y, float w) {
        card(c, x - 30f, y, w + 60f, 370f);
        txt(c, "Scheduled Charging and Climate", x, y + 54f, 30f, HIGHLIGHT, Fonts.SEMIBOLD, Paint.Align.LEFT);
        String[][] rows = new String[3][];
        for (int i = 0; i < 2; i++) {
            String v;
            if (s.depart == null) v = "--";
            else if (!s.departSet(i)) v = "Off";
            else {
                v = time(s.departHour(i), s.departMin(i)) + "  " + days(s.departDays(i));
                if (s.climateOn(i)) v += "  \u00b7  " + (s.climateF(i) ? String.valueOf((int) s.climateTemp(i)) + "\u00b0F"
                        : String.format(Locale.US, "%.1f\u00b0C", s.climateTemp(i))) + (s.climateDefrost(i) ? " + Defog" : "");
            }
            rows[i] = new String[]{"Departure " + (i + 1), v};
        }
        String op = offPeakText();
        if (s.offPeakKnown() && s.offMode() == 2) op += " (only)";
        rows[2] = new String[]{"Off-peak Hours", s.offPeak == null ? "--" : op};
        infoRows(c, x, y + 80f, w, rows);
    }

    /** EVEllipseEffect stand-in: a soft light ellipse sweeping across the filled part of the battery art while charging. */
    private void sweep(Canvas c, float bx, float by, int soc) {
        Bitmap mask = bmp("ev_etc_battery_charger_" + (Math.max(0, Math.min(20, (100 - soc) / 5)) < 10 ? "0" : "") + Math.max(0, Math.min(20, (100 - soc) / 5)), false);
        if (mask == null) return;
        r.set(X(bx), X(by), X(bx + 254f), X(by + 48f));
        int sv = c.saveLayer(r, null, Canvas.ALL_SAVE_FLAG);
        float t = (SystemClock.uptimeMillis() % 1600) / 1600f;
        float cx = bx + 254f * t;
        Paint g = new Paint(Paint.ANTI_ALIAS_FLAG);
        g.setShader(new RadialGradient(X(cx), X(by + 24f), X(70f), 0xCC27A6D0 | 0x00000000, 0x0027A6D0, Shader.TileMode.CLAMP));
        c.drawRect(r, g);
        Paint m = new Paint(Paint.ANTI_ALIAS_FLAG);
        m.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        c.drawBitmap(mask, null, r, m);
        c.restoreToCount(sv);
    }

    private void tabs(Canvas c) {
        // HButton 126 x 66, spacing 74, row centred in the 96 px bottom bar.
        float rowW = 4 * 126f + 3 * 74f, x0 = (1920f - rowW) / 2f, y = 626f / 1f - 96f + 15f + 530f - 530f;
        y = getHeight() / k - 96f + 15f;
        for (int i = 0; i < 4; i++) {
            float x = x0 + i * 200f;
            boolean sel = tab == i;
            String st = pressed == i ? "p" : sel ? "s" : "n";
            // No pill: selected = bright icon + short accent underline (follows the ccNC theme), others dimmed.
            p.setAlpha(sel || pressed == i ? 255 : 150);
            img(c, "ev_ic_tabbar_" + TABS[i] + "_" + (sel ? "s" : pressed == i ? "p" : "n"), x + 3f, y, 120f, 66f, false); // art is 120 x 66
            p.setAlpha(255);
            if (sel) {
                r.set(X(x + 63f - 24f), X(y + 66f + 4f), X(x + 63f + 24f), X(y + 66f + 9f));
                p.setColor(dev.ccnc.common.Palette.ACCENT);
                c.drawRoundRect(r, X(2.5f), X(2.5f), p);
            }
            tabBox[i].set(X(x0 + i * 200f - 37f + 0f) + X(0), X(y - 15f), X(x + 163f), X(y + 81f));
            tabBox[i].offset(X(ox()), 0);
        }
    }

    private float ox() { return (getWidth() / k - 1920f) / 2f; }
}
