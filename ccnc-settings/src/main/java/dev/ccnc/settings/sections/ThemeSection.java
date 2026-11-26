package dev.ccnc.settings.sections;

import android.content.Context;

import dev.ccnc.settings.api.Category;
import dev.ccnc.settings.api.Fn;
import dev.ccnc.settings.api.Page;
import dev.ccnc.settings.api.Sys;
import dev.ccnc.settings.api.Section;

/**
 * Theme / Screen Layout (stock classId com.android.ThemeSettings, app com.android.themesettings).
 *
 * ccNC's own tabs first (ccNC Theme, Theme Store, Accent Colour), then the stock menus in the stock
 * MainActivity.createTab() order, each gated by the stock app's own static check (obfuscated
 * com.android.themesettings.util.i / ClusterUtils, called through the stock class loader):
 *   i.da()                          Digital Picture Frame     NOT PORTED (note): pictures come from the Bluelink phone app
 *                                                             through the stock CCSImageDownloadService
 *   i.dm()                          Graphic Themes            White / Black
 *   ClusterUtils.N(ctx) && i.dc()   Cluster Theme Selection   Theme A..., Link To Drive Mode
 *   (always)                        Screen Saver              None / Digital Clock / Analog Clock / Digital Picture Frame
 *   i.dz()                          Split Screen              no entry: ccNC has no split screen
 * Values are read and changed through the stock ThemeSettingService's smart-control bridge ({@link Scontrol}, type 15;
 * key ids = the stock app's own integer resources theme_tab_* / day_theme_mode_* / disp_scr_saver_* / cluster_*), so
 * applying a theme runs the stock code (ThemeSettingService, VideoSetupManager.setScreenSaver,
 * USMManager.setCluster_ThemeStatus...). The scheduled / sunrise-sunset graphic theme modes are not smart-control
 * options: when one is active it is shown, and White / Black can still be chosen. Not tested on car.
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

    private static final int K_DAY = 12050, K_SAVER = 12052, K_CLUSTER = 12056, K_CLUSTER_LINK = 35010;
    private static final int[] CLUSTER = {25011, 25012, 25013, 25014, 25015, 25016, 25017, 25018, 25019, 25020, 25021};
    private static final String[] CLUSTER_NAMES = {"cluster_theme_a", "cluster_theme_b", "cluster_theme_c", "cluster_theme_d",
            "cluster_theme_e", "cluster_theme_f", "cluster_classic_a", "cluster_classic_b", "cluster_classic_c", "cluster_cube", "cluster_dynamic"};
    private static final String[] CLUSTER_FALLBACK = {"Theme a", "Theme B", "Theme C", "Theme D", "Theme E", "Theme F",
            "Classic a", "Classic B", "Classic C", "Cube", "Dynamic"};

    @Override public void build(Category c) {
        ccncTabs(c);
        final Context x = c.context();
        final dev.ccnc.settings.api.Scontrol sc = dev.ccnc.settings.api.Scontrol.of(x, PKG, 15);
        sc.prefetch(K_DAY, K_SAVER, K_CLUSTER, K_CLUSTER_LINK);
        // Stock gating members as named in THIS car's ThemeSettings build (obfuscated; logged when missing).
        final ButtonSectionStock s = ButtonSectionStock.open(x, PKG, null, null);
        if (s == null) return; // no stock app (emulator): only the ccNC tabs
        if (!sc.anyAnswer()) sc.await(1200);
        if (s.is(I, "dm")) {
            final String title = c.word(4052, s.str("day_theme"));
            if (title != null) c.tab(title, new Fn.Build() {
                @Override public void build(Page p) {
                    sc.attach(p.host());
                    // stock DayThemeActivity$MODE order: THEME_WHITE 0, THEME_BLACK 1, SCHEDULED_BY_USER 2, THEME_SUNRISE_SUNSET 3
                    int v = Sys.getInt(x, "day_theme" + ButtonSectionStock.userSuffix(x), -1);
                    if (v == 2) p.info(title, or(s.str("schedule_theme"), "Scheduled"));
                    else if (v == 3) p.info(title, or(s.str("sunrise_sunset_theme"), "Sunrise / Sunset"));
                    if (!sc.radios(p, K_DAY, new int[] {25101, 25102},
                            new String[] {or(s.str("white_theme"), "White"), or(s.str("black_theme"), "Black")}))
                        p.note("Not available on this unit.");
                }
            });
        }
        if (s.isCtx(PKG + ".util.ClusterUtils", "N") && s.is(I, "dc")) {
            String t = c.word(4034, s.str("cluster_theme"));
            if (t != null) c.tab(t, new Fn.Build() {
                @Override public void build(Page p) {
                    sc.attach(p.host());
                    // USMManager.getCluster_ThemeCount(): how many of Theme A.. this cluster has (0 / unreadable: the first three).
                    int n = dev.ccnc.settings.api.Car.getInt(dev.ccnc.settings.api.Car.manager(x, "com.hkmc.usm.USMManager"), "getCluster_ThemeCount", 0);
                    int cur = sc.value(K_CLUSTER);
                    java.util.List<Integer> keys = new java.util.ArrayList<Integer>();
                    java.util.List<String> names = new java.util.ArrayList<String>();
                    for (int i = 0; i < CLUSTER.length; i++) {
                        boolean inCount = i < (n >= 1 && n <= 6 ? n : 3);
                        if (!inCount && CLUSTER[i] != cur) continue; // other themes only when the car reports one as current
                        keys.add(CLUSTER[i]); names.add(or(s.str(CLUSTER_NAMES[i]), CLUSTER_FALLBACK[i]));
                    }
                    int[] k = new int[keys.size()];
                    for (int i = 0; i < k.length; i++) k[i] = keys.get(i);
                    boolean any = sc.check(p, K_CLUSTER_LINK, or(s.str("cluster_theme_link_to_drivemode"), "Link to Drive Mode"));
                    any |= sc.radios(p, K_CLUSTER, k, names.toArray(new String[0]));
                    if (!any) p.note("Not available on this unit.");
                }
            });
        }
        String ss = c.word(4004, s.str("screen_saver"));
        if (ss != null) c.tab(ss, new Fn.Build() {
            @Override public void build(Page p) {
                sc.attach(p.host());
                if (!sc.radios(p, K_SAVER, new int[] {21061, 21062, 21063, 21060},
                        new String[] {or(s.str("screen_saver_setting_none"), "None"), or(s.str("screen_saver_setting_digital"), "Digital Clock"),
                                or(s.str("screen_saver_setting_analog"), "Analog Clock"), or(s.str("screen_saver_setting_image"), "Digital Picture Frame")}))
                    p.note("Not available on this unit.");
            }
        });
        // No Split Screen entry: ccNC has no split screen, and ccNC Home keeps the Gen5W right screen off.
    }

    private static String or(String a, String b) { return a != null && a.length() > 0 ? a : b; }
}
