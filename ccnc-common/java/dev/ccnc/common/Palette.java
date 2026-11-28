package dev.ccnc.common;

import android.content.Context;
import android.graphics.Color;

/**
 * ccNC design tokens. Single dark theme with the Hyundai/Kia brand cyan as the
 * interactive accent, matching the CCNC setup/climate screens: near-black blue
 * gradient background, translucent rounded cards, white titles, muted subtitles.
 */
public final class Palette {

    // Background gradient (top-left to bottom-right), four stops.
    public static int[] BG = {0xFF0B1117, 0xFF101922, 0xFF15212C, 0xFF0A1016};
    public static int GLOW1 = 0x44304A5E;   // soft top-left glow
    public static int GLOW2 = 0x281E3A4C;   // secondary glow, right
    public static int SHADE = 0x66040A10;   // bottom shade

    // Cards / rails.
    public static int CARD = 0x591E2A36;    // content card fill
    public static int CARD_HIGHLIGHT = 0x732A3A4A;
    public static int RAIL = 0x40101822;    // side rail fill
    public static int DIVIDER = 0x1FFFFFFF;
    public static int BORDER = 0x21FFFFFF;
    public static int PRESS = 0x26FFFFFF;

    // Text.
    /** The theme is a light one (Day): dark text, and white monochrome icons drawn dark (Art.ink). */
    public static boolean LIGHT = false;
    public static int TEXT = 0xFFFFFFFF;
    public static int SUBTEXT = 0xFFA9B6C2;
    public static int HINT = 0xFF6E7B87;

    // Brand accent (Hyundai cyan; Kia uses the same cyan family in CCNC).
    public static int ACCENT = 0xFF00AAD2;
    public static int ACCENT_DIM = 0x6600AAD2;

    // Semantic.
    public static final int ON = 0xFF2FD06F;
    public static final int OFF = 0xFF5B6873;

    /** Wallpaper name (assets/ccnc/wallpaper) for the current theme, or null for the gradient. */
    public static String WALLPAPER;
    /** Icon override folder of the current theme pack ("pack:<id>/icons"), or null for the built-in icons. */
    public static String ICONS;
    /** Bumped whenever {@link Themes#apply} changes the palette, so views know to rebuild their shaders. */
    public static int VERSION;

    private Palette() {}

    public static int alpha(int color, int a) {
        return (a << 24) | (color & 0x00FFFFFF);
    }

    public static float lum(int color) {
        return (0.299f * Color.red(color) + 0.587f * Color.green(color) + 0.114f * Color.blue(color)) / 255f;
    }

    public static int dp(Context c) {
        return Math.max(1, Math.round(c.getResources().getDisplayMetrics().density));
    }

    public static int px(Context c, float d) {
        return Math.round(d * c.getResources().getDisplayMetrics().density);
    }
}
