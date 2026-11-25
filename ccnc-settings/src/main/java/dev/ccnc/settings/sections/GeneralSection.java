package dev.ccnc.settings.sections;

import android.content.Context;
import android.content.Intent;
import android.provider.Settings;

import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import dev.ccnc.settings.api.Category;
import dev.ccnc.settings.api.Fn;
import dev.ccnc.settings.api.Host;
import dev.ccnc.settings.api.Page;
import dev.ccnc.settings.api.Section;
import dev.ccnc.settings.api.Sys;

/**
 * General (stock classId com.android.GeneralSettings, app com.android.generalsettings).
 *
 * Stock tab order (GeneralSettings.zn()): SW Info/Update, System Info [Utils.wD], Bluetooth Remote Lock [India],
 * Date & Time, Language [Utils.wB], Keyboard, Units [util.i], Media Options [Utils.wi], Reset [Utils.wg].
 * Here: Date & Time and Language are native; everything else stays in the stock entry the framework appends.
 * Feature checks are the stock app's own statics, called through its class loader ({@link GeneralStock}).
 */
public final class GeneralSection extends Section {
    @Override public String classId() { return "com.android.GeneralSettings"; }
    @Override public int treeNode() { return 1004; }
    @Override public String icon() { return "set_ic_menu_general"; }

    /** Opens the stock General screen (time zone / language are changed there). */
    private static final Fn.Act STOCK = new Fn.Act() { @Override public void run(Host h) { h.openStock(); } };

    @Override public void build(Category c) {
        final Context x = c.context();
        final GeneralStock st = GeneralStock.get(x);

        // SW Info/Update (always first in the stock app): read-only version rows; USB/OTA update stays in the stock entry.
        if (st.available()) {
            c.tab(c.word(3001, "Software Info/Update"), new Fn.Build() {
                @Override public void build(Page p) {
                    p.value(st.str("sysinfo_platform", "Platform"), new Fn.Str() {
                        @Override public String get() { return st.str("sysinfo_platform_g5w", ""); }
                    }, null);
                    // Model: the stock value (e.g. CE1AE.S5BMC.CA) is not SDefine.getModelName() ("ceev") nor a
                    // single property; source not recoverable from the lossy deodex, so the row is left to the stock screen.
                    final String sw = GeneralStock.prop("ro.build.product2");
                    if (sw != null) p.value(st.str("generalsetting_system_info_sw_version", "Software Version"), new Fn.Str() {
                        @Override public String get() { return sw; }
                    }, null);
                }
            });
        }

        // Date & Time: stock ClockSettingPref (date_time_prefs_common.xml + 24-hour value), always added by the stock app.
        c.tab(c.word(3013, "Date & Time"), new Fn.Build() {
            @Override public void build(Page p) {
                // "gps_auto_time" pref; stock shows it when ag.yd() (GPS time supported on this unit).
                if (st.bool(GeneralStock.AG, GeneralStock.GPS_CHECK)) {
                    p.toggle(p.word(3014, "Set Time Automatically"),
                            new Fn.Bool() { @Override public boolean get() { return st.gpsAuto(x); } },
                            new Fn.SetBool() { @Override public void set(boolean on) { st.setGpsAuto(x, on); } });
                }
                p.segmented(p.word(3015, "Time Format"),
                        new String[] { p.word(3016, "24 Hr"), p.word(3017, "12 Hr") },
                        new Fn.Int() { @Override public int get() { return st.is24(x) ? 0 : 1; } },
                        new Fn.SetInt() { @Override public void set(int v) { st.set24(x, v == 0); } });
                // "summer_time" pref; stock keeps it only for regions where ag.ye() is true.
                if (st.bool(GeneralStock.AG, GeneralStock.DST_CHECK)) {
                    p.toggle(p.word(3018, "Daylight Saving Time"),
                            new Fn.Bool() { @Override public boolean get() { return st.dst(x); } },
                            new Fn.SetBool() { @Override public void set(boolean on) { st.setDst(x, on); } });
                }
                p.value(p.word(3400, "Time Zone"), new Fn.Str() {
                    @Override public String get() {
                        TimeZone tz = TimeZone.getDefault();
                        return tz.getDisplayName(tz.inDaylightTime(new Date()), TimeZone.LONG, Locale.getDefault());
                    }
                }, STOCK);
            }
        });

        // Language: stock adds the tab when Utils.wA(); the locale is changed by the stock LocalePicker
        // (system-only configuration change), so the row shows the current language and opens the stock screen.
        if (st.boolOr(GeneralStock.UTILS, GeneralStock.LANG_CHECK, true)) {
            c.tab(c.word(3021, "Language"), new Fn.Build() {
                @Override public void build(Page p) {
                    p.value(p.word(3021, "Language"), new Fn.Str() {
                        @Override public String get() { Locale l = Locale.getDefault(); return l.getDisplayName(l); }
                    }, STOCK);
                }
            });
        }
        mediaOptions(c, st);
    }

    private static void mediaOptions(Category c, final GeneralStock st) {
        // Media Options (stock LastModeSetting, tab when Utils.wh()): "Display Media Change Notifications" =
        // Settings.System media_notification via the stock profile-aware helper dv.H()/dv.i().
        if (!st.bool(GeneralStock.UTILS, GeneralStock.MEDIA_CHECK) || st.intCall(GeneralStock.MEDIA_NOTI, "H") < 0) return;
        c.tab(c.word(3051, "Media Options"), new Fn.Build() {
            @Override public void build(Page p) {
                p.toggle(st.str("media_notification_item_title", "Display Media Change Notifications"),
                        new Fn.Bool() { @Override public boolean get() { return st.intCall(GeneralStock.MEDIA_NOTI, "H") == 1; } },
                        new Fn.SetBool() { @Override public void set(boolean on) { st.intSet(GeneralStock.MEDIA_NOTI, "i", on ? 1 : 0); } });
            }
        });
    }

    @Override public Intent stockIntent(Context c) { return component("com.android.generalsettings", ".GeneralSettings"); }

    /** Value of Settings.System time_12_24 style keys (string). */
    static String sysString(Context c, String key) {
        try { return Settings.System.getString(c.getContentResolver(), key); } catch (Throwable t) { return null; }
    }
}
