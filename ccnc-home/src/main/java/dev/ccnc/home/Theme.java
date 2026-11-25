package dev.ccnc.home;

import android.content.Context;

import java.util.Calendar;

/** Colour sets for the home screen. Views read {@link #current} when they draw. */
final class Theme {

    final String id, name;
    final boolean light;
    /** Background: four-stop diagonal gradient, two soft glows, bottom shade. */
    final int[] bg;
    final int glow1, glow2, shade;
    /** Cards. */
    final int cardTop, cardBottom, band, border, press;
    final int title, subtitle, accent;
    /** Top bar and page dashes. */
    final int text, handle, dashOn, dashOff;
    /** ccNC wallpaper (assets/ccnc/wallpaper/<name>.jpg) drawn instead of the gradient; null for none. */
    String wallpaper;

    private Theme(String id, String name, boolean light, int[] bg, int glow1, int glow2, int shade,
                  int cardTop, int cardBottom, int band, int border, int press,
                  int title, int subtitle, int accent, int text, int handle, int dashOn, int dashOff) {
        this.id = id; this.name = name; this.light = light; this.bg = bg;
        this.glow1 = glow1; this.glow2 = glow2; this.shade = shade;
        this.cardTop = cardTop; this.cardBottom = cardBottom; this.band = band; this.border = border; this.press = press;
        this.title = title; this.subtitle = subtitle; this.accent = accent;
        this.text = text; this.handle = handle; this.dashOn = dashOn; this.dashOff = dashOff;
    }

    /** ccNC's own home wallpaper (the default theme on the newer cars). */
    static final Theme NEBULA = new Theme("nebula", "ccNC Nebula", false,
            new int[]{0xFF05070A, 0xFF0B1118, 0xFF101922, 0xFF06090C}, 0, 0, 0x99000000,
            0x5C56636E, 0x5A2A343D, 0x14FFFFFF, 0x26FFFFFF, 0x26FFFFFF,
            0xFFFFFFFF, 0xFFB4BEC6, 0xFF4FB3E8, 0xFFFFFFFF, 0xA6FFFFFF, 0xFFFFFFFF, 0x66FFFFFF).with("nebula");

    /** The starfield from ccNC's first-run setup. */
    static final Theme STARFIELD = new Theme("starfield", "ccNC Starfield", false,
            new int[]{0xFF060A14, 0xFF0B1426, 0xFF0E1A30, 0xFF060A14}, 0, 0, 0x80000000,
            0x5C566A80, 0x5A27354A, 0x14FFFFFF, 0x26FFFFFF, 0x26FFFFFF,
            0xFFFFFFFF, 0xFFB4C2D2, 0xFF6FA8FF, 0xFFFFFFFF, 0xA6FFFFFF, 0xFFFFFFFF, 0x66FFFFFF).with("starfield");

    private Theme with(String wallpaper) { this.wallpaper = wallpaper; return this; }

    static final Theme HYUNDAI = new Theme("hyundai", "Hyundai Blue", false,
            new int[]{0xFF0A1220, 0xFF0F1D33, 0xFF142846, 0xFF0A1220}, 0x40285AA0, 0x2018408A, 0x80040A14,
            0x70213A5C, 0x70162844, 0x16FFFFFF, 0x2AFFFFFF, 0x26FFFFFF,
            0xFFFFFFFF, 0xFFA8BBD6, 0xFF00AAD2, 0xFFFFFFFF, 0xA6FFFFFF, 0xFFFFFFFF, 0x66FFFFFF);

    static final Theme EMBER = new Theme("ember", "Ember", false,
            new int[]{0xFF140A08, 0xFF2A130E, 0xFF3A1A12, 0xFF160B08}, 0x60C8501E, 0x40803214, 0x80080302,
            0x70573427, 0x70301A14, 0x16FFFFFF, 0x2AFFFFFF, 0x26FFFFFF,
            0xFFFFFFFF, 0xFFD9B8A8, 0xFFFF6B35, 0xFFFFFFFF, 0xA6FFFFFF, 0xFFFFFFFF, 0x66FFFFFF);

    static final Theme MONO = new Theme("mono", "Mono", false,
            new int[]{0xFF000000, 0xFF0A0A0A, 0xFF121212, 0xFF000000}, 0, 0, 0x99000000,
            0x66303030, 0x66181818, 0x10FFFFFF, 0x33FFFFFF, 0x26FFFFFF,
            0xFFFFFFFF, 0xFFC8C8C8, 0xFFFFFFFF, 0xFFFFFFFF, 0xA6FFFFFF, 0xFFFFFFFF, 0x66FFFFFF);

    static final Theme MIDNIGHT = new Theme("midnight", "Midnight", false,
            new int[]{0xFF0A131A, 0xFF16242F, 0xFF1F3240, 0xFF0E1820}, 0x553C5E78, 0x302C4A60, 0x80050A0E,
            0x6E4E5E6A, 0x6A2E3B45, 0x16FFFFFF, 0x24FFFFFF, 0x26FFFFFF,
            0xFFFFFFFF, 0xFFB4BEC6, 0xFFE39A5B, 0xFFFFFFFF, 0xA6FFFFFF, 0xFFFFFFFF, 0x66FFFFFF);

    static final Theme GRAPHITE = new Theme("graphite", "Graphite", false,
            new int[]{0xFF080808, 0xFF151617, 0xFF1C1D1F, 0xFF0B0B0C}, 0x40505356, 0x20404244, 0x90000000,
            0x70505254, 0x702A2B2D, 0x12FFFFFF, 0x20FFFFFF, 0x26FFFFFF,
            0xFFFFFFFF, 0xFFA9ACB0, 0xFFD9D9D9, 0xFFFFFFFF, 0x99FFFFFF, 0xFFFFFFFF, 0x55FFFFFF);

    static final Theme OCEAN = new Theme("ocean", "Ocean", false,
            new int[]{0xFF03161C, 0xFF07303A, 0xFF0B4350, 0xFF041D24}, 0x6036A3B5, 0x40207E8E, 0x80021014,
            0x70357A86, 0x70154550, 0x16FFFFFF, 0x2AFFFFFF, 0x26FFFFFF,
            0xFFFFFFFF, 0xFFA9D3DA, 0xFF5FE0D0, 0xFFFFFFFF, 0xA6FFFFFF, 0xFFFFFFFF, 0x66FFFFFF);

    static final Theme SUNSET = new Theme("sunset", "Sunset", false,
            new int[]{0xFF1A0E1F, 0xFF34172E, 0xFF4A1F2C, 0xFF1C0D16}, 0x60D2694A, 0x408E3F7A, 0x800C050A,
            0x70745064, 0x703C2434, 0x16FFFFFF, 0x2AFFFFFF, 0x26FFFFFF,
            0xFFFFFFFF, 0xFFD8B9C4, 0xFFFF9C6B, 0xFFFFFFFF, 0xA6FFFFFF, 0xFFFFFFFF, 0x66FFFFFF);

    static final Theme FOREST = new Theme("forest", "Forest", false,
            new int[]{0xFF07120C, 0xFF102419, 0xFF183222, 0xFF09150E}, 0x504E8A5E, 0x30305E40, 0x80030805,
            0x704E6A58, 0x70263A2E, 0x16FFFFFF, 0x24FFFFFF, 0x26FFFFFF,
            0xFFFFFFFF, 0xFFB5C8BB, 0xFFB7D96A, 0xFFFFFFFF, 0xA6FFFFFF, 0xFFFFFFFF, 0x66FFFFFF);

    static final Theme DAY = new Theme("day", "Day (light)", true,
            new int[]{0xFFDDE4EA, 0xFFEEF2F5, 0xFFE3EAF0, 0xFFCFD8E0}, 0x80FFFFFF, 0x60FFFFFF, 0x30708090,
            0xD0FFFFFF, 0xB8F2F5F8, 0x0A10202A, 0x26203040, 0x1A203040,
            0xFF16212A, 0xFF5B6873, 0xFFE07A3C, 0xFF16212A, 0x8016212A, 0xFF16212A, 0x4016212A);

    /** Pseudo-theme: Day from 07:00 to 19:00, ccNC Nebula otherwise. */
    static final String AUTO = "auto";

    static final Theme[] ALL = {NEBULA, STARFIELD, HYUNDAI, MIDNIGHT, GRAPHITE, OCEAN, SUNSET, FOREST, EMBER, MONO, DAY};

    static Theme current = NEBULA;

    /** Owner-picked accent colour over any theme (0 = the theme's own); shared with the other ccNC apps. */
    static int accentOverride;

    /** The accent in use: the override if set, else this theme's. */
    int accent() { return accentOverride != 0 ? accentOverride : accent; }

    static void loadAccent(Context c) {
        accentOverride = c.getSharedPreferences("home", Context.MODE_PRIVATE).getInt("accent", 0);
    }

    static void saveAccent(Context c, int color) {
        accentOverride = color;
        c.getSharedPreferences("home", Context.MODE_PRIVATE).edit().putInt("accent", color).apply();
    }

    static String savedId(Context c) {
        return c.getSharedPreferences("home", Context.MODE_PRIVATE).getString("theme", NEBULA.id);
    }

    static void save(Context c, String id) {
        c.getSharedPreferences("home", Context.MODE_PRIVATE).edit().putString("theme", id).apply();
    }

    private static final java.util.Map<dev.ccnc.common.Themes.T, Theme> packThemes = new java.util.HashMap<dev.ccnc.common.Themes.T, Theme>();

    /** Home's look for an installed theme pack: the light/dark base with the pack's colours and wallpaper. */
    static synchronized Theme of(dev.ccnc.common.Themes.T t) {
        Theme h = packThemes.get(t);
        if (h != null) return h;
        Theme b = t.light ? DAY : NEBULA;
        int[] bg = new int[4];
        for (int i = 0; i < 4; i++) bg[i] = t.bgColor(i);
        int card = t.cardColor();
        h = new Theme(t.id, t.name, t.light, bg, t.glow1Color(), t.glow2Color(), t.shadeColor(),
                lighter(card), card, b.band, b.border, b.press,
                b.title, t.subtextColor(), t.accentColor(), b.text, b.handle, b.dashOn, b.dashOff);
        h.wallpaper = t.wallpaper;
        packThemes.put(t, h);
        return h;
    }

    private static int lighter(int c) {
        int a = c >>> 24, r = Math.min(255, ((c >> 16) & 0xFF) + 40), g = Math.min(255, ((c >> 8) & 0xFF) + 40), bl = Math.min(255, (c & 0xFF) + 40);
        return (a << 24) | (r << 16) | (g << 8) | bl;
    }

    /** Built-in themes, then the installed theme packs (for the theme picker). */
    static java.util.List<Theme> all(Context c) {
        java.util.List<Theme> l = new java.util.ArrayList<Theme>(java.util.Arrays.asList(ALL));
        for (dev.ccnc.common.ThemePacks.Pack p : dev.ccnc.common.ThemePacks.all(c)) l.add(of(p.theme));
        return l;
    }

    /** Resolves the saved choice, including theme packs ("pack.<id>"); Nebula if the pack is gone. */
    static Theme resolve(Context c, String id) {
        if (id != null && id.startsWith("pack.")) {
            dev.ccnc.common.ThemePacks.Pack p = dev.ccnc.common.ThemePacks.find(c, id.substring(5));
            return p != null ? of(p.theme) : NEBULA;
        }
        return resolve(id);
    }

    /** Resolves the saved choice (including Auto) to a theme. */
    static Theme resolve(String id) {
        if (AUTO.equals(id)) {
            int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
            return h >= 7 && h < 19 ? DAY : NEBULA;
        }
        for (Theme t : ALL) if (t.id.equals(id)) return t;
        return NEBULA;
    }

    /** Icon colour: the normal pastel on dark themes, a deeper shade on light ones. */
    int icon(Icons.Kind k) {
        int c = Icons.color(k);
        if (!light) return c;
        if (k == Icons.Kind.PROJECTION || k == Icons.Kind.APP) return 0xFF16212A;
        float[] hsv = new float[3];
        android.graphics.Color.colorToHSV(c, hsv);
        hsv[1] = Math.min(1f, hsv[1] + 0.35f);
        hsv[2] = Math.max(0f, hsv[2] - 0.35f);
        if (hsv[1] < 0.2f) hsv[2] = 0.35f; // greys (settings, profile)
        return android.graphics.Color.HSVToColor(hsv);
    }
}
