package dev.ccnc.settings.api;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.view.View;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import dev.ccnc.common.Palette;

/**
 * Colour swatches for the ambient-light options, keyed by the stock option names (HKMC_VehicleSetting
 * strings: hmc_presetcolor_*, kmc_presetcolor_*, single_color_*, dual_color_*, custom colours).
 * The stock app sends CAN signal values, not RGB, so the swatch colours are approximations of each named colour.
 */
public final class AmbientColors {
    private AmbientColors() {}

    private static final int[] RAINBOW = {};
    private static final Map<String, int[]> MAP = new HashMap<String, int[]>();
    private static void put(String n, int... c) { MAP.put(n.toLowerCase(Locale.US), c); }
    static {
        // Hyundai presets
        put("Polar White", 0xFFF2F6FF); put("Moon White", 0xFFFFEBCB); put("Ice Blue", 0xFF9BD6FF);
        put("Ocean Blue", 0xFF2266FF); put("Jade Green", 0xFF28C98C); put("Orchid Green", 0xFF8ED64A);
        put("Freesia Yellow", 0xFFFFD43B); put("Sunrise Red", 0xFFFF4636); put("Aurora Purple", 0xFF9A5CFF);
        put("Lightning Violet", 0xFFC64CFF); put("Disney 100", 0xFF2E4BFF, 0xFFB98CFF);
        // Kia presets / single colours
        put("Blue Flight", 0xFF2C7BFF); put("Peaceful Forest", 0xFF3CC864); put("Dreamy Purple", 0xFF8E5CFF);
        put("Dream Purple", 0xFF8E5CFF); put("Violet Cloud", 0xFFB48CFF); put("Aurora Violet", 0xFFA070FF);
        put("Aurora White", 0xFFF2F6FF); put("Orange Delight", 0xFFFF8A2A); put("Golden Insight", 0xFFFFC23A);
        put("Refreshing Sea", 0xFF2FD4D0); put("Refreshing Ocean", 0xFF2FD4D0); put("Lively Red", 0xFFFF3B3B);
        put("Urban Sky", 0xFF6FB8FF);
        // Dual colours (upper, lower)
        put("Concentration", 0xFF2C7BFF, 0xFFF2F6FF); put("Healing Forest", 0xFF3CC864, 0xFF2FD4D0);
        put("Wonderful Day", 0xFFFF8A2A, 0xFFFFD43B); put("Mind Care", 0xFF2FD4D0, 0xFF8E5CFF);
        put("Meditation", 0xFF8E5CFF, 0xFF2C7BFF); put("Creative Moment", 0xFFFF4A8A, 0xFF8E5CFF);
        // Plain colour names some cars use
        put("White", 0xFFF2F6FF); put("Red", 0xFFFF3B3B); put("Orange", 0xFFFF8A2A); put("Yellow", 0xFFFFD43B);
        put("Green", 0xFF3CC864); put("Blue", 0xFF2C7BFF); put("Sky Blue", 0xFF6FB8FF); put("Purple", 0xFF8E5CFF);
        put("Violet", 0xFFB48CFF); put("Pink", 0xFFFF6FB0); put("Cyan", 0xFF2FD4D0);
        put("Custom Color", RAINBOW); put("Custom Dual Color", RAINBOW); put("Set Custom Color", RAINBOW);
    }

    /** Swatch colours for an option label (1 = single, 2 = upper/lower, empty = custom), or null. */
    public static int[] of(String label) {
        return label == null ? null : MAP.get(label.trim().toLowerCase(Locale.US));
    }

    /** True when most labels of a radio group are colour names: then every row gets a swatch slot. */
    public static boolean isColourList(String[] labels) {
        int n = 0;
        for (String l : labels) if (of(l) != null) n++;
        return n >= 2 && n * 2 >= labels.length;
    }

    /** Round 40 px swatch (ring for unknown names). */
    public static View view(Context c, final int[] cols) {
        return new View(c) {
            final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            final RectF r = new RectF();
            @Override protected void onDraw(Canvas cv) {
                float w = getWidth(), h = getHeight(), rad = Math.min(w, h) / 2f - 2f, cx = w / 2f, cy = h / 2f;
                r.set(cx - rad, cy - rad, cx + rad, cy + rad);
                p.setStyle(Paint.Style.FILL);
                p.setShader(null);
                if (cols == null) {
                    p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2f); p.setColor(Palette.LIGHT ? Palette.alpha(Palette.TEXT, 0x66) : 0x66FFFFFF);
                    cv.drawCircle(cx, cy, rad, p);
                    return;
                }
                if (cols.length == 0) {
                    p.setShader(new SweepGradient(cx, cy, new int[]{0xFFFF3B3B, 0xFFFFD43B, 0xFF3CC864, 0xFF2FD4D0,
                            0xFF2C7BFF, 0xFF8E5CFF, 0xFFFF3B3B}, null));
                    cv.drawCircle(cx, cy, rad, p);
                } else if (cols.length == 1) {
                    p.setColor(cols[0]);
                    cv.drawCircle(cx, cy, rad, p);
                } else {
                    p.setColor(cols[0]); cv.drawArc(r, 180f, 180f, true, p);
                    p.setColor(cols[1]); cv.drawArc(r, 0f, 180f, true, p);
                }
                p.setShader(null);
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2f); p.setColor(Palette.LIGHT ? Palette.alpha(Palette.TEXT, 0x40) : 0x40FFFFFF);
                cv.drawCircle(cx, cy, rad, p);
            }
        };
    }
}
