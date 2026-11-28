package dev.ccnc.climate;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import dev.ccnc.common.Art;
import dev.ccnc.common.Fonts;
import dev.ccnc.common.Palette;

/**
 * The ccNC climate screen, laid out from ccNC's own climate app (climatehmi, QML recovered from the
 * binary) on its 1920 x 720 grid: the fan pill top centre (x738 y57, 446 x 89), a temperature
 * column each side of the cabin (x544 / x1258: up arrow, value, down arrow), the seat picture with the
 * air-flow overlays in the middle (x756 / x948, y233), seat heat and vent toggles at the far left and
 * right (x192.5 / x1607.5), dividers at x505 and x1414, and the bottom bar below the divider at y620
 * (back, AUTO, OFF, the air-flow preview that opens the mode popup, A/C, SYNC).
 *
 * <p>Not on this screen in ccNC, so not here: recirculation, rear defrost and power (physical buttons),
 * and "driver only" (no state for it yet). All touch targets are rebuilt on every draw.
 *
 * <p>Controls the car doesn't have are left out and the rest re-flowed ({@link Equipment}): the seat rows at the
 * sides close up and stay centred, a side with nothing loses its divider, a single-zone car has one temperature and
 * no SYNC, a manual climate has no AUTO, and the bottom bar spreads what is left evenly. The seat popup does the same.
 */
final class ClimateView extends View {

    interface Callback { void onChanged(); void onBack(); }

    private static final float DW = 1920f, DH = 720f;
    private static final float[] TEMP_RANGE = {15.5f, 29f};
    /** Popup choices, ccNC mode numbers: 1 vent, 2 vent+floor, 3 floor, 4 defog+floor, 10 defog. */
    private static final int[] POPUP_MODES = {1, 2, 3, 4, 10};

    private final Callback cb;
    private Climate.State s = new Climate.State();
    /** What this car has; controls it doesn't have aren't drawn. Everything until the car has been read. */
    private Equipment eq = Equipment.detect(s);
    private boolean popup, seatPopup;

    /** Opens the ccNC seat heat/vent popup (also what the car's seat buttons open). */
    void showSeats() { seatPopup = true; popup = false; invalidate(); }

    void hideSeats() { seatPopup = false; popup = false; invalidate(); }

    /** The popup was dismissed (tap outside or the close button). In overlay mode the window goes too. */
    private void closeSeats() { seatPopup = false; if (overlay) cb.onBack(); }

    private final Paint bmp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();

    private static final class Hit {
        final RectF box; final Runnable run; final boolean change;
        Hit(RectF box, Runnable run, boolean change) { this.box = box; this.run = run; this.change = change; }
    }
    private final List<Hit> hits = new ArrayList<Hit>();
    private Hit down;
    private float k, ox, oy; // design px -> view px, and the letterbox offset

    /**
     * Overlay mode: only the seat popup, drawn over a transparent window that sits above whatever app is on
     * screen (see {@link SeatOverlay}). Tapping outside the popup calls {@link Callback#onBack()}.
     */
    private final boolean overlay;

    ClimateView(Context c, Callback cb) { this(c, cb, false); }

    ClimateView(Context c, Callback cb, boolean overlay) {
        super(c);
        this.cb = cb;
        this.overlay = overlay;
        if (overlay) seatPopup = true;
        line.setStyle(Paint.Style.STROKE);
        setClickable(true);
    }

    void setState(Climate.State s, boolean readOnly) {
        this.s = s;
        eq = Equipment.of(getContext(), s);
        invalidate();
    }

    // ---- touch ----

    @Override public boolean onTouchEvent(MotionEvent e) {
        float x = e.getX(), y = e.getY();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                down = null;
                for (int i = hits.size() - 1; i >= 0; i--) if (hits.get(i).box.contains(x, y)) { down = hits.get(i); break; }
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
                Hit h = down;
                down = null;
                invalidate();
                if (h != null && h.box.contains(x, y)) { h.run.run(); if (h.change) cb.onChanged(); else invalidate(); }
                return true;
            case MotionEvent.ACTION_CANCEL:
                down = null;
                invalidate();
                return true;
            default:
                return true;
        }
    }

    // ---- helpers (design units in, view px out) ----

    private RectF box(float l, float t, float w, float h) { return new RectF(ox + l * k, oy + t * k, ox + (l + w) * k, oy + (t + h) * k); }

    private RectF grow(RectF b, float d) { return new RectF(b.left - d * k, b.top - d * k, b.right + d * k, b.bottom + d * k); }

    /** Draws at the bitmap's own size (the QML images have no explicit size), or scaled to {@code w}. */
    private void img(Canvas c, String name, float l, float t, float w, int alpha) {
        Bitmap b = Art.climate(getContext(), name);
        if (b == null) return;
        float width = w > 0 ? w : b.getWidth();
        r.set(ox + l * k, oy + t * k, ox + (l + width) * k, oy + (t + width * b.getHeight() / b.getWidth()) * k);
        bmp.setAlpha(alpha);
        c.drawBitmap(b, null, r, bmp);
        bmp.setAlpha(255);
    }

    private void imgChrome(Canvas c, String name, float l, float t, float size) {
        Bitmap b = Art.chrome(getContext(), name);
        if (b == null) return;
        r.set(ox + l * k, oy + t * k, ox + (l + size) * k, oy + (t + size) * k);
        c.drawBitmap(b, null, r, bmp);
    }

    private void label(Canvas c, String t, float cx, float cy, float size, int color, int face) {
        text.setTypeface(Fonts.get(getContext(), face));
        text.setTextSize(size * k);
        text.setColor(color);
        text.setTextAlign(Paint.Align.CENTER);
        Paint.FontMetrics fm = text.getFontMetrics();
        c.drawText(t, ox + cx * k, oy + cy * k - (fm.ascent + fm.descent) / 2f, text);
    }

    private void hit(RectF b, boolean change, Runnable run) { hits.add(new Hit(b, run, change)); }

    private boolean pressed(RectF b) { return down != null && down.box.equals(b); }

    // ---- ccNC air-flow mode numbers <-> our mode ----

    private int ccMode() {
        if (s.ccMode > 0) return s.ccMode;
        switch (s.mode) {
            case Climate.State.MODE_FACE: return 1;
            case Climate.State.MODE_FACE_FEET: return 2;
            case Climate.State.MODE_FEET: return 3;
            case Climate.State.MODE_FEET_DEFOG: return 4;
            default: return 10;
        }
    }

    private static boolean top(int m) { return m == 4 || m == 5 || m == 7 || (m >= 9 && m <= 14); }
    private static boolean mid(int m) { return m == 1 || m == 2 || m == 7 || m == 9 || m == 11 || m == 13; }
    private static boolean bottom(int m) { return (m >= 2 && m <= 4) || m == 9 || m == 12 || m == 13; }

    private void setCcMode(int m) {
        s.power = true;
        s.ccMode = m;
        switch (m) {
            case 1: s.mode = Climate.State.MODE_FACE; break;
            case 2: s.mode = Climate.State.MODE_FACE_FEET; break;
            case 3: s.mode = Climate.State.MODE_FEET; break;
            case 4: s.mode = Climate.State.MODE_FEET_DEFOG; break;
            default: s.mode = Climate.State.MODE_DEFOG; break;
        }
    }

    // ---- the screen ----

    @Override protected void onDraw(Canvas c) {
        hits.clear();
        k = Math.min(getWidth() / DW, getHeight() / DH);
        ox = (getWidth() - DW * k) / 2f;
        oy = (getHeight() - DH * k) / 2f;

        if (overlay) { // the seat popup alone, over whatever is on screen
            if (seatPopup) seatPopup(c);
            return;
        }

        boolean flowing = s.power && (s.blower > 0 || s.auto) && s.modeKnown;
        int mode = ccMode();

        // Cabin: the seat with the air-flow overlays on it.
        img(c, "cli_bg_seat", 948f, 233f, 0, 255);
        if (flowing) {
            if (top(mode)) img(c, "clm_bg_wind_top", 756f, 233f, 0, 255);
            if (mid(mode)) img(c, "clm_bg_wind_mid", 756f, 233f, 0, 255);
            if (bottom(mode)) img(c, "clm_bg_wind_bot", 756f, 233f, 0, 255);
        }
        if (s.modeKnown) hit(box(756f, 233f, 472f, 310f), false, new Runnable() { @Override public void run() { popup = true; } });

        // A side with no seat controls has no divider either: the cabin area simply runs to the edge.
        boolean drvSide = eq.wheel || eq.driverSeat(), astSide = eq.passengerSeat();
        if (rhd ? astSide : drvSide) img(c, "clm_ic_divider_h", 505f, 106f, 1f, 255);
        if (rhd ? drvSide : astSide) img(c, "clm_ic_divider_h", 1414f, 106f, 1f, 255);

        fan(c);
        temp(c, true);
        if (eq.dual) temp(c, false);
        seatButtons(c);
        bottomBar(c, mode);

        if (popup) popup(c, mode);
        if (seatPopup) seatPopup(c);
    }

    /** The fan pill: down, level (AUTO above it when automatic), up. */
    private void fan(Canvas c) {
        final float l = 738f, t = 57f;
        img(c, "clm_bg_pop_fan", l, t, 446f, 255);
        boolean off = !s.power || (s.blower == 0 && !s.auto);
        img(c, off ? "clm_ic_pop_fan_02_off" : "clm_ic_pop_fan_02_down", l, t, 89f, 255);
        img(c, "clm_ic_pop_fan_02_up", l + 357f, t, 89f, 255);
        String level = off ? "OFF" : String.valueOf(Math.max(1, s.blower));
        if (s.auto && !off) {
            label(c, "AUTO", l + 89f + 134f, t + 17f, 20f, Palette.TEXT, Fonts.REGULAR);
            label(c, level, l + 89f + 134f, t + 30f + 29f, 48f, Palette.TEXT, Fonts.REGULAR);
        } else {
            label(c, level, l + 89f + 134f, t + 44f, off ? 40f : 60f, Palette.TEXT, Fonts.REGULAR);
        }
        hit(grow(box(l, t, 89f, 89f), 22f), true, new Runnable() { @Override public void run() {
            s.auto = false; s.power = true; s.blower = Math.max(0, s.blower - 1); } });
        hit(grow(box(l + 357f, t, 89f, 89f), 22f), true, new Runnable() { @Override public void run() {
            s.auto = false; s.power = true; s.blower = Math.min(eq.fanMax, s.blower + 1); } });
    }

    /** One temperature column: up arrow, value, down arrow. */
    /** Right-hand drive: the driver's controls on the right (Climate.rhd()). */
    private final boolean rhd = Climate.rhd(getContext());

    private void temp(Canvas c, final boolean driver) {
        final float l = (driver != rhd) ? 544f : 1258f, t = 153f;   // the driver's side of the screen
        boolean enabled = driver || s.dual;
        int a = enabled ? 255 : 110;
        float v = driver ? s.driverTemp : (s.dual ? s.passengerTemp : s.driverTemp);
        img(c, "clm_ic_up_red", l + 28f, t, 64f, a);
        img(c, "clm_ic_down_blue", l + 28f, t + 356f, 64f, a);
        String txt;
        if (v <= TEMP_RANGE[0]) txt = "LO";
        else if (v >= TEMP_RANGE[1]) txt = "HI";
        else if (s.celsius) txt = String.format(Locale.US, "%.1f", v);
        else txt = String.format(Locale.US, "%d", Math.round(v * 9f / 5f + 32f));
        label(c, txt, l + 60f, t + 171f + 39f, 48f, enabled ? Palette.TEXT : Palette.HINT, Fonts.REGULAR);
        hit(grow(box(l + 28f, t, 64f, 64f), 16f), true, new Runnable() { @Override public void run() { step(driver, 0.5f); } });
        hit(grow(box(l + 28f, t + 356f, 64f, 64f), 17f), true, new Runnable() { @Override public void run() { step(driver, -0.5f); } });
    }

    private void step(boolean driver, float d) {
        if (!driver && !s.dual) s.dual = true; // touching the passenger side unlinks it
        float v = driver ? s.driverTemp : s.passengerTemp;
        v = Math.max(TEMP_RANGE[0], Math.min(TEMP_RANGE[1], v + d));
        if (driver) { s.driverTemp = v; if (!s.dual) s.passengerTemp = v; }
        else s.passengerTemp = v;
    }

    /**
     * The seat controls at the far left and right, each 120 x 85 with its level bars on top: wheel heat (driver's
     * side only), seat heat (toggle 02) and seat vent (toggle 03). Both sides share the rows, so heat sits opposite
     * heat. Only the rows this car has are there, stacked 115 apart and centred on the ccNC block (all three:
     * y244 / 359 / 474, ccNC's own positions).
     */
    private void seatButtons(Canvas c) {
        boolean rowWheel = eq.wheel, rowHeat = eq.drvHeat || eq.astHeat, rowVent = eq.drvVent || eq.astVent;
        int n = (rowWheel ? 1 : 0) + (rowHeat ? 1 : 0) + (rowVent ? 1 : 0);
        if (n == 0) return;
        float y = 401.5f - (n * 85f + (n - 1) * 30f) / 2f;
        final float dx = rhd ? 1607.5f : 192.5f, px = rhd ? 192.5f : 1607.5f;   // driver's and passenger's side
        if (rowWheel) { wheel(c, dx, y); y += 115f; }
        if (rowHeat) {
            if (eq.drvHeat) seat(c, dx, y, s.driverSeatHeat, true, true);
            if (eq.astHeat) seat(c, px, y, s.passengerSeatHeat, true, false);
            y += 115f;
        }
        if (rowVent) {
            if (eq.drvVent) seat(c, dx, y, s.driverSeatVent, false, true);
            if (eq.astVent) seat(c, px, y, s.passengerSeatVent, false, false);
        }
    }

    /** Level bars to light for a level 0..3: with 2 levels (low / high, levels 1 and 3) that is 0, 1 or 2 bars. */
    private static int lit(int level, int steps) { return steps == 2 ? (level >= 3 ? 2 : level >= 1 ? 1 : 0) : level; }

    /** Steering-wheel warmer (ccNC clm_ic_toggle_01): one indicator bar, press toggles it. */
    private void wheel(Canvas c, float x, float y) {
        RectF b = box(x, y, 120f, 85f);
        if (pressed(b)) { fill.setColor(Palette.PRESS); c.drawRoundRect(b, 16f * k, 16f * k, fill); }
        img(c, s.wheelHeat ? "clm_ic_toggle_01_s" : "clm_ic_toggle_01", x + 25f, y + 15f, 70f, 255);
        indicator(c, x + 16f, y + 10f, 88f, s.wheelHeat, true);
        hit(b, true, new Runnable() { @Override public void run() { s.wheelHeat = !s.wheelHeat; } });
    }

    private int level(int seat, boolean heat) {
        switch (seat) {
            case 0: return heat ? s.driverSeatHeat : s.driverSeatVent;
            case 1: return heat ? s.passengerSeatHeat : s.passengerSeatVent;
            case 2: return heat ? s.rearLeftHeat : s.rearLeftVent;
            default: return heat ? s.rearRightHeat : s.rearRightVent;
        }
    }

    private void setLevel(int seat, int heat, int vent) {
        switch (seat) {
            case 0: s.driverSeatHeat = heat; s.driverSeatVent = vent; break;
            case 1: s.passengerSeatHeat = heat; s.passengerSeatVent = vent; break;
            case 2: s.rearLeftHeat = heat; s.rearLeftVent = vent; break;
            default: s.rearRightHeat = heat; s.rearRightVent = vent; break;
        }
    }

    private void seat(Canvas c, float x, float y, final int level, final boolean heat, final boolean driver) {
        RectF b = box(x, y, 120f, 85f);
        String icon = "clm_ic_toggle_0" + (heat ? "2" : "3") + (level > 0 ? "_s" : "");
        img(c, icon, x + 25f, y + 15f, 70f, 255);
        final int steps = driver ? eq.drvSteps : eq.astSteps;   // 3 bars of 24, or 2 of 40, across the same 88
        // ccNC's bars: orange for heating (toggle01), blue for ventilation (toggle02)
        if (steps == 2) for (int i = 0; i < 2; i++) indicator(c, x + 16f + i * 48f, y + 10f, 40f, i < lit(level, 2), true, !heat);
        else for (int i = 0; i < 3; i++) indicator(c, x + 16f + i * 32f, y + 10f, 24f, i < level, true, !heat);
        hit(b, true, new Runnable() { @Override public void run() {
            // Stock cycle (SeatHeatVentPopup): heat 8,7,6,2 / vent 5,4,3,2 on the SEAT_STATES values (8,6,2 / 5,3,2 with 2 levels).
            int cur = driver ? (s.driverSeatHeat > 0 ? Usm.stateOf(true, s.driverSeatHeat) : Usm.stateOf(false, s.driverSeatVent))
                    : (s.passengerSeatHeat > 0 ? Usm.stateOf(true, s.passengerSeatHeat) : Usm.stateOf(false, s.passengerSeatVent));
            int nx = Usm.nextState(cur, heat, steps);
            if (driver) { s.driverSeatHeat = Usm.heatLevel(nx); s.driverSeatVent = Usm.ventLevel(nx); }
            else { s.passengerSeatHeat = Usm.heatLevel(nx); s.passengerSeatVent = Usm.ventLevel(nx); }
        } });
    }

    private static final int HEAT = 0xFFFF6B2C, VENT = 0xFF4C9BFF, GAUGE_OFF = 0x33FFFFFF;

    /**
     * ccNC seat popup (seathmi art): a wide panel with the driver and passenger seats drawn with their heat (orange)
     * or vent (blue) glow for the current level, ccNC seat buttons with level gauges beside each seat, and the rear
     * seats and steering-wheel warmer in the middle. Seats the car doesn't have are left out; tap outside to close.
     */
    private void seatPopup(Canvas c) {
        hits.clear();
        fill.setColor(0xD9000000);
        c.drawRect(0, 0, getWidth(), getHeight(), fill);
        hit(new RectF(0, 0, getWidth(), getHeight()), false, new Runnable() { @Override public void run() { closeSeats(); } });

        RectF panel = box(70f, 66f, 1780f, 548f);
        fill.setColor(0xF5121A23);
        c.drawRoundRect(panel, 32f * k, 32f * k, fill);
        line.setColor(Palette.BORDER);
        line.setStrokeWidth(Math.max(1f, 2f * k));
        c.drawRoundRect(panel, 32f * k, 32f * k, line);
        hit(panel, false, new Runnable() { @Override public void run() { } }); // taps on the panel stay in it

        // Close (top right).
        RectF close = box(1762f, 82f, 72f, 72f);
        if (pressed(close)) { fill.setColor(Palette.PRESS); c.drawOval(close, fill); }
        img(c, "clm_ic_close", 1778f, 98f, 40f, 255);
        hit(close, false, new Runnable() { @Override public void run() { closeSeats(); } });

        // Only what the car has (Equipment): a front seat without heating or ventilation, the rear seats and the
        // wheel warmer are left out, and the rest closes up.
        boolean rl = eq.rearLeft(), rr = eq.rearRight();
        boolean middle = rl || rr || eq.wheel;
        // Right-hand drive: the passenger on the left, the driver on the right.
        boolean leftSeat = rhd ? eq.passengerSeat() : eq.driverSeat(), rightSeat = rhd ? eq.driverSeat() : eq.passengerSeat();

        if (!leftSeat && !rightSeat && !middle) {
            label(c, "This car has no seat heating or ventilation", 960f, 340f, 34f, Palette.SUBTEXT, Fonts.REGULAR);
            return;
        }

        // Left seat: seat art, then its buttons; the right seat mirrored.
        if (leftSeat) frontSeat(c, rhd ? 1 : 0, 110f, 430f, true, !rhd);
        if (rightSeat) frontSeat(c, rhd ? 0 : 1, middle ? 1240f : 1010f, 1510f, false, rhd);

        if (middle) {
            if (leftSeat) img(c, "clm_ic_divider_h", 700f, 120f, 1f, 160);
            if (rightSeat) img(c, "clm_ic_divider_h", 1200f, 120f, 1f, 160);
            float cx = 950f;
            // Rear seats: a heat row and / or a vent row. With both rows and the wheel below, everything is a size smaller.
            boolean rowHeat = eq.rlHeat || eq.rrHeat, rowVent = eq.rlVent || eq.rrVent;
            boolean tight = rowHeat && rowVent && eq.wheel;
            float tw = tight ? 180f : 220f, th = tw * 146f / 280f;
            if (rl || rr) {
                label(c, "Rear Seats", cx, 140f, 32f, Palette.TEXT, Fonts.SEMIBOLD);
                float lx = rr ? 955f - tw : 960f - tw / 2f, rx = rl ? 965f : 960f - tw / 2f;
                float y = tight ? 180f : 200f;
                if (rowHeat) {
                    if (eq.rlHeat) seatTile(c, lx, y, tw, 150f, 2, true, null);
                    if (eq.rrHeat) seatTile(c, rx, y, tw, 150f, 3, true, null);
                    y += th + 10f;
                }
                if (rowVent) {
                    if (eq.rlVent) seatTile(c, lx, y, tw, 150f, 2, false, null);
                    if (eq.rrVent) seatTile(c, rx, y, tw, 150f, 3, false, null);
                    y += th + 10f;
                }
                if (rl) label(c, "Left", lx + tw / 2f, y + 16f, 26f, Palette.SUBTEXT, Fonts.REGULAR);
                if (rr) label(c, "Right", rx + tw / 2f, y + 16f, 26f, Palette.SUBTEXT, Fonts.REGULAR);
            }
            if (eq.wheel) {
                float wy = !(rl || rr) ? 200f : tight ? 486f : 440f;
                label(c, "Steering Wheel", cx, wy - 42f, 32f, Palette.TEXT, Fonts.SEMIBOLD);
                wheelTile(c, cx - tw / 2f, wy, tw, 150f);
            }
        }
    }

    /** One front seat: caption, the seat art with its glow, and heat/vent buttons beside it. */
    /** {@code left}: on the left half of the popup (art, then buttons); {@code driver}: the driver's seat. */
    private void frontSeat(Canvas c, final int seat, float artX, float btnX, boolean left, boolean driver) {
        int h = level(seat, true), v = level(seat, false);
        boolean heat = eq.heat(seat), vent = eq.vent(seat);
        String who = left ? "driver" : "passenger";   // seat art drawn for that side of the screen
        String art = h > 0 ? "set_bg_" + who + "_heat_0" + Math.min(3, h) : v > 0 ? "set_bg_" + who + "_vent_0" + Math.min(3, v) : "set_bg_" + who + "_seat";
        float ax = left ? artX : btnX;   // right half: art right of its buttons
        float bx = left ? btnX : artX;
        img(c, art, ax, 190f, 300f, 255);
        label(c, driver ? "Driver" : "Passenger", ax + 150f, 140f, 32f, Palette.TEXT, Fonts.SEMIBOLD);
        if (heat && vent) {
            seatTile(c, bx, 200f, 240f, 150f, seat, true, null);
            seatTile(c, bx, 360f, 240f, 150f, seat, false, null);
        } else {
            seatTile(c, bx, 280f, 240f, 150f, seat, heat, null);   // heating only (or ventilation only): one button, centred
        }
    }

    /** ccNC seat button (set_btn_bg_seat): icon, three level gauges (orange heat / blue vent), optional caption. */
    private void seatTile(Canvas c, float x, float y, float w, float h, final int seat, final boolean heat, String caption) {
        int lv = level(seat, heat);
        RectF b = box(x, y, w, h);
        img(c, pressed(b) ? "set_btn_bg_seat_p" : lv > 0 ? "set_btn_bg_seat_s" : "set_btn_bg_seat_n", x, y, w, 255);
        if (lv > 0) { // lit edge in the level colour
            line.setColor((heat ? HEAT : VENT) & 0x99FFFFFF);
            line.setStrokeWidth(Math.max(1f, 2f * k));
            RectF e = box(x + 2f, y + 2f, w - 4f, w * 146f / 280f - 4f);
            c.drawRoundRect(e, 22f * k, 22f * k, line);
        }
        float bh = w * 146f / 280f;
        String icon = seat >= 2 ? "clm_ic_toggle_0" + (heat ? "2" : "3") + "_r" + (lv > 0 ? "_s" : "")
                : "clm_ic_seat_" + (heat ? "heat" : "vent") + (seat == 0 ? "_l" : "_r");
        float is = (seat >= 2 ? 64f : 70f) * Math.min(1f, w / 220f);   // smaller on the tight rear tiles
        img(c, icon, x + (w - is) / 2f, y + bh * 0.16f, is, lv > 0 || seat < 2 ? 255 : 200);
        final int steps = eq.steps(seat);
        gauges(c, x + w / 2f, y + bh * 0.78f, lit(lv, steps), heat ? HEAT : VENT, steps);
        if (caption != null) label(c, caption, x + w / 2f, y + bh + 26f, 26f, Palette.SUBTEXT, Fonts.REGULAR);
        hit(b, true, new Runnable() { @Override public void run() {
            int hh = level(seat, true), vv = level(seat, false);
            int nx = Usm.nextState(hh > 0 ? Usm.stateOf(true, hh) : Usm.stateOf(false, vv), heat, steps);
            setLevel(seat, Usm.heatLevel(nx), Usm.ventLevel(nx));
        } });
    }

    /** Steering-wheel warmer tile: on/off, one gauge. */
    private void wheelTile(Canvas c, float x, float y, float w, float h) {
        RectF b = box(x, y, w, w * 146f / 280f);
        boolean on = s.wheelHeat;
        img(c, pressed(b) ? "set_btn_bg_seat_p" : on ? "set_btn_bg_seat_s" : "set_btn_bg_seat_n", x, y, w, 255);
        float bh = w * 146f / 280f;
        if (on) {
            line.setColor(HEAT & 0x99FFFFFF);
            line.setStrokeWidth(Math.max(1f, 2f * k));
            c.drawRoundRect(box(x + 2f, y + 2f, w - 4f, bh - 4f), 22f * k, 22f * k, line);
        }
        float is = 64f * Math.min(1f, w / 220f);
        img(c, on ? "clm_ic_toggle_01_s" : "clm_ic_toggle_01", x + (w - is) / 2f, y + bh * 0.16f, is, 255);
        r.set(ox + (x + w / 2f - 30f) * k, oy + (y + bh * 0.78f) * k, ox + (x + w / 2f + 30f) * k, oy + (y + bh * 0.78f + 6f) * k);
        fill.setColor(on ? HEAT : GAUGE_OFF);
        c.drawRoundRect(r, 3f * k, 3f * k, fill);
        hit(b, true, new Runnable() { @Override public void run() { s.wheelHeat = !s.wheelHeat; } });
    }

    /** {@code n} gauges (3, or 2 for a 2-level seat), the first {@code level} of them lit. */
    private void gauges(Canvas c, float cx, float y, int level, int color, int n) {
        float bw = 34f, gap = 8f, l = cx - (n * bw + (n - 1) * gap) / 2f;
        for (int i = 0; i < n; i++) {
            r.set(ox + (l + i * (bw + gap)) * k, oy + y * k, ox + (l + i * (bw + gap) + bw) * k, oy + (y + 6f) * k);
            fill.setColor(i < level ? color : GAUGE_OFF);
            c.drawRoundRect(r, 3f * k, 3f * k, fill);
        }
    }

    /**
     * Below the divider at y620: back, AUTO, OFF, air-flow preview, A/C, SYNC. Right-hand drive: the row is mirrored
     * (mx), so back sits at the right, next to the driver.
     */
    private float mx(float x, float w) { return rhd ? 1920f - x - w : x; }

    /**
     * Left edge of bottom-bar item {@code i} of {@code n} (after the back button), {@code w} wide. With all six
     * (AUTO, OFF, seats, air flow, A/C, SYNC) each sits at its ccNC position, centre {@code ccncCx}; when the car
     * lacks some, what is left is spread evenly over the same stretch (x172 to x1878). Mirrored for right-hand drive.
     */
    private float slot(int i, int n, float ccncCx, float w) {
        float cx = n == 6 ? ccncCx : 172f + 1706f * (i + 0.5f) / n;
        return mx(cx - w / 2f, w);
    }

    /** Centre of the air-flow preview in the bottom bar: the mode popup points at it. */
    private float flowCx = 1247f;

    private void bottomBar(Canvas c, int mode) {
        img(c, "clm_ic_divider_w", 0f, 620f, 1920f, 255);

        float bx = mx(20f, 122f);
        RectF back = box(bx, 638f, 122f, 74f);
        if (pressed(back)) { fill.setColor(Palette.PRESS); c.drawRoundRect(back, 20f * k, 20f * k, fill); }
        imgChrome(c, "back", bx + 21f, 638f - 3f, 80f);
        hit(grow(back, 12f), false, new Runnable() { @Override public void run() { cb.onBack(); } });

        // OFF, air flow and A/C are on every car; AUTO, the seat popup and SYNC only where the car has them.
        boolean seatPop = eq.anySeatControl();
        int n = 3 + (eq.auto ? 1 : 0) + (seatPop ? 1 : 0) + (eq.dual ? 1 : 0), i = 0;

        if (eq.auto) toggle(c, slot(i++, n, 282f, 220f), 637f, "AUTO", s.auto && s.power, new Runnable() { @Override public void run() {
            s.auto = !s.auto; s.power = true; if (s.auto) s.blower = Math.max(1, s.blower); } });

        boolean off = !s.power || (s.blower == 0 && !s.auto);
        float ofx = slot(i++, n, 554f, 220f);
        RectF offBox = box(ofx, 637f, 220f, 86f);
        label(c, "OFF", ofx + 110f, 620f + 35f + 23.5f, 30f, Palette.TEXT, Fonts.REGULAR);
        hit(offBox, true, new Runnable() { @Override public void run() { s.auto = false; s.blower = 0; } });

        // Seats: opens the seat popup (rear seats and wheel included).
        if (seatPop) {
            float sx = slot(i++, n, 870f, 220f);
            RectF seats = box(sx, 637f, 220f, 78f);
            if (pressed(seats)) { fill.setColor(Palette.PRESS); c.drawRoundRect(seats, 20f * k, 20f * k, fill); }
            boolean anySeat = s.rearLeftHeat + s.rearLeftVent + s.rearRightHeat + s.rearRightVent > 0;
            img(c, anySeat ? "clm_ic_toggle_02_r_s" : "clm_ic_toggle_02_r", sx + 75f, 641f, 70f, 255);
            hit(seats, false, new Runnable() { @Override public void run() { seatPopup = true; } });
        }

        // The air-flow preview doubles as the mode popup button.
        float px = slot(i++, n, 1247f, 186f);
        flowCx = px + 93f;
        RectF pv = box(px, 628f, 186f, 92f);
        if (pressed(pv)) { fill.setColor(Palette.PRESS); c.drawRoundRect(pv, 20f * k, 20f * k, fill); }
        img(c, "clm_ic_seat_off", px, 628f, 186f, 255);
        if (s.modeKnown && s.power) {
            if (top(mode)) img(c, "clm_ic_defog_off", px, 628f, 186f, 255);
            if (mid(mode)) img(c, "clm_ic_air_mid_off", px, 628f, 186f, 255);
            if (bottom(mode)) img(c, "clm_ic_air_bot_off", px, 628f, 186f, 255);
        }
        if (s.modeKnown) hit(pv, false, new Runnable() { @Override public void run() { popup = !popup; } });

        toggle(c, slot(i++, n, 1502f, 220f), 637f, "A/C", s.ac && s.power, new Runnable() { @Override public void run() { s.ac = !s.ac; } });
        if (eq.dual) toggle(c, slot(i++, n, 1768f, 220f), 637f, "SYNC", !s.dual, new Runnable() { @Override public void run() {
            s.dual = !s.dual; if (!s.dual) s.passengerTemp = s.driverTemp; } });
    }

    private final java.util.HashMap<String, Bitmap> ind = new java.util.HashMap<String, Bitmap>();

    /** ccNC's toggle indicator art (9-patch, 1px guide border cropped), drawn w x 5. */
    private void indicator(Canvas c, float l, float t, float w, boolean on, boolean seat) { indicator(c, l, t, w, on, seat, false); }

    private void indicator(Canvas c, float l, float t, float w, boolean on, boolean seat, boolean cool) {
        String n = (seat ? (cool ? "clm_btn_toggle02_" : "clm_btn_toggle01_") : "clm_btn_toggle_") + (on ? "on" : "off");
        Bitmap b = ind.get(n);
        if (b == null) {
            try {
                java.io.InputStream in = getContext().getAssets().open("ccnc/climate/" + n + ".9.png");
                Bitmap raw = android.graphics.BitmapFactory.decodeStream(in);
                in.close();
                b = Bitmap.createBitmap(raw, 1, 1, raw.getWidth() - 2, raw.getHeight() - 2);
            } catch (Exception e) { return; }
            ind.put(n, b);
        }
        r.set(ox + l * k, oy + t * k, ox + (l + w) * k, oy + (t + 5f) * k);
        c.drawBitmap(b, null, r, bmp);
    }

    /** ccNC ClimateToggleButton: 220 x 71, one 38 x 5 indicator at (91,12), caption 32 px at y18 h47; no background. */
    private void toggle(Canvas c, float l, float t, String caption, boolean on, Runnable run) {
        RectF b = box(l, t, 220f, 71f);
        indicator(c, l + 91f, t + 12f, 38f, on, false);
        label(c, caption, l + 110f, t + 18f + 23.5f, 32f, Palette.TEXT, Fonts.REGULAR);
        hit(b, true, run);
    }

    /** The air-flow mode popup over a dimmed screen, pointing at the preview. */
    private void popup(Canvas c, int mode) {
        hits.clear(); // the dim layer takes every touch
        fill.setColor(0xCC000000);
        c.drawRect(0, 0, getWidth(), getHeight(), fill);
        hit(new RectF(0, 0, getWidth(), getHeight()), false, new Runnable() { @Override public void run() { popup = false; } });

        float w = POPUP_MODES.length * 186f, l = 495f, t = 528f;
        RectF panel = box(l, t, w, 92f);
        fill.setColor(0xFF161F29);
        c.drawRoundRect(panel, 24f * k, 24f * k, fill);
        line.setColor(Palette.BORDER);
        line.setStrokeWidth(Math.max(1f, 2f * k));
        c.drawRoundRect(panel, 24f * k, 24f * k, line);
        img(c, "clm_bg_set_polygon", flowCx - 26f, 618f, 19f, 255);   // x1221 in ccNC's own layout
        for (int i = 0; i < POPUP_MODES.length; i++) {
            final int m = POPUP_MODES[i];
            boolean on = m == mode;
            float x = l + i * 186f;
            img(c, on ? "clm_ic_seat_on" : "clm_ic_seat_off", x, t, 186f, 255);
            if (top(m)) img(c, on ? "clm_ic_defog_on" : "clm_ic_defog_off", x, t, 186f, 255);
            if (mid(m)) img(c, on ? "clm_ic_air_mid_on" : "clm_ic_air_mid_off", x, t, 186f, 255);
            if (bottom(m)) img(c, on ? "clm_ic_air_bot_on" : "clm_ic_air_bot_off", x, t, 186f, 255);
            hit(box(x, t, 186f, 92f), true, new Runnable() { @Override public void run() { setCcMode(m); popup = false; } });
        }
    }
}
