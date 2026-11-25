package dev.ccnc.common;

import java.util.ArrayList;
import java.util.List;

import android.content.Context;

import java.util.Calendar;

/**
 * The ccNC colour themes, shared by every ccNC app. The launcher serves the owner's choice through a content provider; each app calls {@link #apply} when it starts
 * and the {@link Palette} follows. Apps that can't read it fall back to the default theme.
 */
public final class Themes {
    private Themes() {}

    public static final class T {
        public final String id, name;
        public final boolean light;
        final int[] bg;
        /** Icon override folder of a theme pack ("pack:<id>/icons"), or null. */
        public final String icons;
        final int glow1, glow2, shade, card, border, press, divider, text, subtext, hint, accent;
        public final String wallpaper;

        /** Theme accent / a background stop, for swatches. */
        public int accentColor() { return accent; }
        public int bgColor(int i) { return bg[i]; }
        public int cardColor() { return card; }
        public int glow1Color() { return glow1; }
        public int glow2Color() { return glow2; }
        public int shadeColor() { return shade; }
        public int subtextColor() { return subtext; }

        T(String id, String name, boolean light, int[] bg, int glow1, int glow2, int shade, int card, int subtext, int accent, String wallpaper) {
            this(id, name, light, bg, glow1, glow2, shade, card, subtext, accent, wallpaper, null);
        }

        T(String id, String name, boolean light, int[] bg, int glow1, int glow2, int shade, int card, int subtext, int accent, String wallpaper, String icons) {
            this.icons = icons;
            this.id = id; this.name = name; this.light = light; this.bg = bg;
            this.glow1 = glow1; this.glow2 = glow2; this.shade = shade; this.card = card;
            this.border = light ? 0x26203040 : 0x24FFFFFF;
            this.press = light ? 0x1A203040 : 0x26FFFFFF;
            this.divider = light ? 0x20203040 : 0x1FFFFFFF;
            this.text = light ? 0xFF16212A : 0xFFFFFFFF;
            this.subtext = subtext;
            this.hint = light ? 0xFF7A8691 : 0xFF6E7B87;
            this.accent = accent;
            this.wallpaper = wallpaper;
        }
    }

    public static final String AUTO = "auto";
    
    public static final T NEBULA = new T("nebula", "ccNC Nebula", false, new int[]{0xFF05070A, 0xFF0B1118, 0xFF101922, 0xFF06090C},
            0, 0, 0x99000000, 0x5C2A343D, 0xFFB4BEC6, 0xFF4FB3E8, "nebula");
    public static final T STARFIELD = new T("starfield", "ccNC Starfield", false, new int[]{0xFF060A14, 0xFF0B1426, 0xFF0E1A30, 0xFF060A14},
            0, 0, 0x80000000, 0x5C27354A, 0xFFB4C2D2, 0xFF6FA8FF, "starfield");
    public static final T HYUNDAI = new T("hyundai", "Hyundai Blue", false, new int[]{0xFF0A1220, 0xFF0F1D33, 0xFF142846, 0xFF0A1220},
            0x40285AA0, 0x2018408A, 0x80040A14, 0x6A1C2E4A, 0xFFA8BBD6, 0xFF00AAD2, null);
    public static final T MIDNIGHT = new T("midnight", "Midnight", false, new int[]{0xFF0A131A, 0xFF16242F, 0xFF1F3240, 0xFF0E1820},
            0x553C5E78, 0x302C4A60, 0x80050A0E, 0x6A2E3B45, 0xFFB4BEC6, 0xFFE39A5B, null);
    public static final T GRAPHITE = new T("graphite", "Graphite", false, new int[]{0xFF080808, 0xFF151617, 0xFF1C1D1F, 0xFF0B0B0C},
            0x40505356, 0x20404244, 0x90000000, 0x702A2B2D, 0xFFA9ACB0, 0xFFD9D9D9, null);
    public static final T OCEAN = new T("ocean", "Ocean", false, new int[]{0xFF03161C, 0xFF07303A, 0xFF0B4350, 0xFF041D24},
            0x6036A3B5, 0x40207E8E, 0x80021014, 0x70154550, 0xFFA9D3DA, 0xFF5FE0D0, null);
    public static final T SUNSET = new T("sunset", "Sunset", false, new int[]{0xFF1A0E1F, 0xFF34172E, 0xFF4A1F2C, 0xFF1C0D16},
            0x60D2694A, 0x408E3F7A, 0x800C050A, 0x703C2434, 0xFFD8B9C4, 0xFFFF9C6B, null);
    public static final T FOREST = new T("forest", "Forest", false, new int[]{0xFF07120C, 0xFF102419, 0xFF183222, 0xFF09150E},
            0x504E8A5E, 0x30305E40, 0x80030805, 0x70263A2E, 0xFFB5C8BB, 0xFFB7D96A, null);
    public static final T EMBER = new T("ember", "Ember", false, new int[]{0xFF140A08, 0xFF2A130E, 0xFF3A1A12, 0xFF160B08},
            0x60C8501E, 0x40803214, 0x80080302, 0x703A2018, 0xFFD9B8A8, 0xFFFF6B35, null);
    public static final T MONO = new T("mono", "Mono", false, new int[]{0xFF000000, 0xFF0A0A0A, 0xFF121212, 0xFF000000},
            0, 0, 0x99000000, 0x66222222, 0xFFC8C8C8, 0xFFFFFFFF, null);
    public static final T DAY = new T("day", "Day (light)", true, new int[]{0xFFDDE4EA, 0xFFEEF2F5, 0xFFE3EAF0, 0xFFCFD8E0},
            0x80FFFFFF, 0x60FFFFFF, 0x30708090, 0xD0FFFFFF, 0xFF5B6873, 0xFFE07A3C, null);

    public static final T[] ALL = {NEBULA, STARFIELD, HYUNDAI, MIDNIGHT, GRAPHITE, OCEAN, SUNSET, FOREST, EMBER, MONO, DAY};

    /** Accent colours the owner can pick over any theme; 0 keeps the theme's own. */
    public static final int[] ACCENTS = {0, 0xFF00AAD2, 0xFF4FB3E8, 0xFF4CD08A, 0xFFF2C94C, 0xFFFF9C6B, 0xFFE5484D, 0xFFF472B6, 0xFFA78BFA};
    public static final String[] ACCENT_NAMES = {"Theme default", "Cyan", "Sky", "Green", "Yellow", "Orange", "Red", "Pink", "Violet"};

    /** Built-in themes, then the installed theme packs. */
    public static List<T> all(Context c) {
        List<T> l = new ArrayList<T>(java.util.Arrays.asList(ALL));
        for (ThemePacks.Pack p : ThemePacks.all(c)) l.add(p.theme);
        return l;
    }

    /** A built-in theme or an installed pack's; ccNC Nebula when it's gone (e.g. the pack was uninstalled). */
    public static T find(Context c, String id) {
        if (id != null && id.startsWith("pack.")) {
            ThemePacks.Pack p = ThemePacks.find(c, id.substring(5));
            return p != null ? p.theme : NEBULA;
        }
        return find(id);
    }

    public static T find(String id) {
        for (T t : ALL) if (t.id.equals(id)) return t;
        return NEBULA;
    }

    /** Auto: Day from 07:00 to 19:00, ccNC Nebula otherwise. */
    public static T resolve(String id) {
        if (AUTO.equals(id)) {
            int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
            return h >= 7 && h < 19 ? DAY : NEBULA;
        }
        return find(id);
    }

    public static int accentOf(T t, int override) { return override != 0 ? override : t.accent; }

    /** Reads the owner's choice from ccNC Home's theme provider and points the Palette at it. Returns true if the palette changed. */
    public static boolean apply(Context c) {
        CrashLog.install(c); // every ccNC app calls this first: from here a crash is shown on screen and saved
        String id = null;
        int accent = 0;
        android.database.Cursor cur = null;
        try {
            cur = c.getContentResolver().query(android.net.Uri.parse("content://dev.ccnc.home.theme/theme"), null, null, null, null);
            if (cur != null && cur.moveToFirst()) {
                id = cur.getString(0);
                accent = cur.getInt(1);
            }
        } catch (Throwable ignored) { // ccNC Home not installed: default theme
        } finally {
            if (cur != null) cur.close();
        }
        return set(AUTO.equals(id) || id == null ? resolve(id) : find(c, id), accent);
    }

    /** Points the Palette at a theme (with an accent override). */
    public static boolean set(T t, int accentOverride) {
        int acc = accentOf(t, accentOverride);
        boolean same = Palette.WALLPAPER == null ? t.wallpaper == null : Palette.WALLPAPER.equals(t.wallpaper);
        same &= Palette.ACCENT == acc && Palette.BG == t.bg && Palette.TEXT == t.text
                && (Palette.ICONS == null ? t.icons == null : Palette.ICONS.equals(t.icons));
        if (same) return false;
        Palette.BG = t.bg;
        Palette.GLOW1 = t.glow1; Palette.GLOW2 = t.glow2; Palette.SHADE = t.shade;
        Palette.CARD = t.card; Palette.CARD_HIGHLIGHT = t.card; Palette.RAIL = t.card & 0x80FFFFFF;
        Palette.DIVIDER = t.divider; Palette.BORDER = t.border; Palette.PRESS = t.press;
        Palette.TEXT = t.text; Palette.SUBTEXT = t.subtext; Palette.HINT = t.hint;
        Palette.ACCENT = acc; Palette.ACCENT_DIM = (acc & 0x00FFFFFF) | 0x66000000;
        Palette.WALLPAPER = t.wallpaper;
        Palette.ICONS = t.icons;
        Palette.VERSION++;
        return true;
    }

}
