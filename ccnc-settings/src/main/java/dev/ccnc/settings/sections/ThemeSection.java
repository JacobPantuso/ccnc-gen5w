package dev.ccnc.settings.sections;

import android.content.Context;
import android.content.Intent;

import dev.ccnc.settings.api.Category;
import dev.ccnc.settings.api.Fn;
import dev.ccnc.settings.api.Host;
import dev.ccnc.settings.api.Page;
import dev.ccnc.settings.api.Sys;
import dev.ccnc.settings.api.Section;

/**
 * Theme / Screen Layout (stock classId com.android.ThemeSettings, app com.android.themesettings).
 *
 * Menus and order are the stock MainActivity.createTab() ones, each gated by the stock app's own static check
 * (obfuscated com.android.themesettings.util.i / ClusterUtils, called through the stock class loader):
  *   i.da()                          Digital Picture Frame     tab "DigitalFrame"
 *   i.dm()                          Graphic Themes            current value (day_theme_<user>) + stock tab "DayTheme"
 *   ClusterUtils.N(ctx) && i.dc()   Cluster Theme Selection   tab "ClusterTheme"
 *   (always)                        Screen Saver              tab "Screensaver"
 *   i.dz()                          Split Screen              tab "SplitScreenSetting"
 * (names from the car's own build, /tmp/ugset; they change between firmwares.) Each entry opens the stock screen on that tab (extra SAVED_CURRENT_TAB): the stock value encodings (analog_clock_type,
 * day_theme, split-screen order, cluster theme via the vehicle) could not be recovered reliably from the odex, so the
 * values are read/written only by the stock screens.
 * Home screen icons/widgets (com.hkmc.system.app.homesetting) are opened from the stock Display category, not here.
 */
public final class ThemeSection extends Section {
    private static final String PKG = "com.android.themesettings";
    private static final String I = PKG + ".util.i";

    @Override public String classId() { return "com.android.ThemeSettings"; }
    @Override public int treeNode() { return 1005; }
    @Override public String icon() { return "set_ic_menu_themelayout"; }

    private static final android.net.Uri HOME_THEME = android.net.Uri.parse("content://dev.ccnc.home.theme/theme");

    /** ccNC Home's own themes and accent colour (shared with every ccNC app through Home's ThemeProvider). */
    private static void ccncTabs(final Category c) {
        final Context ctx = c.context();
        // Built-in themes, then installed theme packs made for this car's brand ("Name \u00b7 by Author").
        java.util.List<String> idl = new java.util.ArrayList<String>(), nl = new java.util.ArrayList<String>();
        idl.add("auto"); nl.add("Auto (Day 7:00\u201319:00, ccNC Nebula at night)");
        for (dev.ccnc.common.Themes.T t : dev.ccnc.common.Themes.ALL) {
            if (t == dev.ccnc.common.Themes.HYUNDAI && dev.ccnc.common.Fonts.kia()) continue; // brand theme
            idl.add(t.id); nl.add(t.name);
        }
        for (dev.ccnc.common.ThemePacks.Pack p : dev.ccnc.common.ThemePacks.all(ctx)) {
            if (!p.fits(ctx)) continue;
            idl.add(p.theme.id);
            nl.add(p.name + (p.author.length() > 0 ? " \u00b7 by " + p.author : ""));
        }
        final String[] ids = idl.toArray(new String[0]), names = nl.toArray(new String[0]);
        c.tab("ccNC Theme", new dev.ccnc.settings.api.Fn.Build() {
            @Override public void build(dev.ccnc.settings.api.Page p) {
                p.radios(names, new dev.ccnc.settings.api.Fn.Int() {
                    @Override public int get() {
                        String cur = homeString(ctx, "theme", "nebula");
                        for (int i = 0; i < ids.length; i++) if (ids[i].equals(cur)) return i;
                        return 1;
                    }
                }, new dev.ccnc.settings.api.Fn.SetInt() {
                    @Override public void set(int v) { homePut(ctx, "theme", ids[v], 0, false); }
                });
            }
        });
        c.tab("Theme Store", new dev.ccnc.settings.api.Fn.Build() {
            @Override public void build(dev.ccnc.settings.api.Page p) { ThemeStorePage.build(ctx, p); }
        });
        c.tab("Accent Colour", new dev.ccnc.settings.api.Fn.Build() {
            @Override public void build(dev.ccnc.settings.api.Page p) {
                p.radios(dev.ccnc.common.Themes.ACCENT_NAMES, new dev.ccnc.settings.api.Fn.Int() {
                    @Override public int get() {
                        int cur = homeInt(ctx, "accent");
                        for (int i = 0; i < dev.ccnc.common.Themes.ACCENTS.length; i++) if (dev.ccnc.common.Themes.ACCENTS[i] == cur) return i;
                        return 0;
                    }
                }, new dev.ccnc.settings.api.Fn.SetInt() {
                    @Override public void set(int v) { homePut(ctx, "accent", null, dev.ccnc.common.Themes.ACCENTS[v], true); }
                });
            }
        });
    }

    private static String homeString(Context c, String col, String def) {
        android.database.Cursor cur = null;
        try {
            cur = c.getContentResolver().query(HOME_THEME, null, null, null, null);
            if (cur != null && cur.moveToFirst()) { String v = cur.getString(cur.getColumnIndex(col)); if (v != null) return v; }
        } catch (Throwable ignored) {
        } finally { if (cur != null) cur.close(); }
        return def;
    }

    private static int homeInt(Context c, String col) {
        try { return Integer.parseInt(homeString(c, col, "0")); } catch (Throwable t) { return 0; }
    }

    private static void homePut(Context c, String col, String s, int i, boolean isInt) {
        try {
            android.content.ContentValues v = new android.content.ContentValues();
            if (isInt) v.put(col, i); else v.put(col, s);
            c.getContentResolver().update(HOME_THEME, v, null, null);
        } catch (Throwable ignored) { }
        // Show the new theme here at once; other ccNC apps pick it up when they come back to the front.
        if (dev.ccnc.common.Themes.apply(c) && c instanceof dev.ccnc.settings.MainActivity)
            ((dev.ccnc.settings.MainActivity) c).themeChanged();
    }

    @Override public void build(Category c) {
        ccncTabs(c);
        // Stock gating members as named in THIS car's ThemeSettings build (obfuscated; logged when missing).
        final ButtonSectionStock s = ButtonSectionStock.open(c.context(), PKG, null, null);
        if (s == null) return; // no stock app: the card opens the stock screen (framework)
        final Context x = c.context();
        if (s.is(I, "da")) tab(c, c.word(4001, s.str("digital_frame")), "DigitalFrame");
        if (s.is(I, "dm")) {
            final String title = c.word(4052, s.str("day_theme"));
            // stock DayThemeActivity$MODE order: THEME_WHITE, THEME_BLACK, SCHEDULED_BY_USER, THEME_SUNRISE_SUNSET
            final String[] labels = {s.str("white_theme"), s.str("black_theme"), s.str("schedule_theme"), s.str("sunrise_sunset_theme")};
            if (title != null) c.tab(title, new Fn.Build() {
                @Override public void build(Page p) {
                    // Applying a theme runs inside the stock ThemeSettingService: value shown here, chosen in the stock screen.
                    p.value(title, new Fn.Str() {
                        @Override public String get() {
                            int v = Sys.getInt(x, "day_theme" + ButtonSectionStock.userSuffix(x), -1);
                            return v >= 0 && v < labels.length && labels[v] != null ? labels[v] : "";
                        }
                    }, new Fn.Act() {
                        @Override public void run(Host h) { h.start(component(PKG, ".MainActivity").putExtra("SAVED_CURRENT_TAB", "DayTheme")); }
                    });
                }
            });
        }
        if (s.isCtx(PKG + ".util.ClusterUtils", "N") && s.is(I, "dc"))
            tab(c, c.word(4034, s.str("cluster_theme")), "ClusterTheme");
        tab(c, c.word(4004, s.str("screen_saver")), "Screensaver");
        // No Split Screen entry: ccNC has no split screen, and ccNC Home keeps the Gen5W right screen off.
    }

    private static void tab(Category c, String title, String tabId) {
        if (title == null) return;
        c.tab(title, component(PKG, ".MainActivity").putExtra("SAVED_CURRENT_TAB", tabId));
    }

    @Override public Intent stockIntent(Context c) { return component(PKG, ".MainActivity"); }
}
