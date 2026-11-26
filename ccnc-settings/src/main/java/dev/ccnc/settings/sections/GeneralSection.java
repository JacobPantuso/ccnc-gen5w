package dev.ccnc.settings.sections;

import android.content.Context;
import android.provider.Settings;

import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import dev.ccnc.settings.api.Ask;
import dev.ccnc.settings.api.Car;
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
 * Feature checks are the stock app's own statics, called through its class loader ({@link GeneralStock}).
 * Everything is on ccNC pages; nothing opens the stock General screen:
 *   Software Info/Update  versions, read only. The USB / OTA update itself is the system updater (uid system,
 *                         REBOOT / recovery): BLOCKED for a normal app, said so on the page.
 *   System Info           storage figures (StatFs), read only like the stock page.
 *   Date & Time           GPS auto time, 12/24 h, DST (stock helpers, as before); Time Zone = the stock ZoneList write
 *                         AlarmManager.setTimeZone("Mobis/" + utc label) (SET_TIME_ZONE is a normal permission here);
 *                         manual date/time = the stock TimePolicyManager.setCurrentTimeMillis(SRC_USER, millis). The
 *                         service ends in SystemClock.setCurrentTimeMillis without clearing the caller identity, so
 *                         SET_TIME (signature|system) may refuse it for us: the page then says so. VERIFY ON CAR.
 *   Language              the stock LocalePicker write IActivityManager.updateConfiguration(locale, userSetLocale).
 *                         Needs android.permission.CHANGE_CONFIGURATION (signature|system|development): works after the
 *                         LivePatch step "pm grant dev.ccnc.settings android.permission.CHANGE_CONFIGURATION";
 *                         without it the page shows the language read only and names that step.
 *   Keyboard              BLOCKED: the layouts live in the keyboard app's private preferences (uid system). Note only.
 *   Units                 stock util.i get/set*Unit (USMManager + the stock app's own backup), 1-based in the stock
 *                         list order (tyre "bar" = 3 seen in UnitSetting; the others inferred). VERIFY ON CAR that
 *                         each row shows what the cluster shows before changing one.
 *   Media Options         as before.
 *   Reset                 BLOCKED: factory reset is MASTER_CLEAR / CLEAR_APP_USER_DATA plus a system-wide broadcast
 *                         every stock app acts on; not offered from a normal app. Note only.
 */
public final class GeneralSection extends Section {
    @Override public String classId() { return "com.android.GeneralSettings"; }
    @Override public int treeNode() { return 1004; }
    @Override public String icon() { return "set_ic_menu_general"; }

    private static final String PKG = "com.android.generalsettings";

    @Override public void build(Category c) {
        final Context x = c.context();
        final GeneralStock st = GeneralStock.get(x);

        // SW Info/Update (always first in the stock app): read-only version rows.
        {
            c.tab(c.word(3001, "Software Info/Update"), new Fn.Build() {
                @Override public void build(Page p) {
                    if (st.available()) p.info(st.str("sysinfo_platform", "Platform"), st.str("sysinfo_platform_g5w", ""));
                    // Model: the stock value (e.g. CE1AE.S5BMC.CA) is not SDefine.getModelName() ("ceev") nor a single
                    // property; its source is not recoverable from the deodex, so there is no Model row.
                    softwareInfo(p, x);
                }
            });
        }
        if (st.boolOr(GeneralStock.UTILS, GeneralStock.SYSINFO_CHECK, true)) {
            c.tab(c.word(3010, st.str("generalsetting_system_info_title", "System Info")), new Fn.Build() {
                @Override public void build(Page p) { systemInfo(p, x); }
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
                timeZone(p, x);
                if (!st.gpsAuto(x)) manualTime(p, x);
            }
        });

        // Language: stock adds the tab when Utils.wA().
        if (st.boolOr(GeneralStock.UTILS, GeneralStock.LANG_CHECK, true)) {
            c.tab(c.word(3021, "Language"), new Fn.Build() {
                @Override public void build(Page p) { language(p, x); }
            });
        }
        units(c, x, st);
        mediaOptions(c, st);
    }

    // ---- Software Info / System Info --------------------------------------------------------------

    private static void softwareInfo(Page p, Context x) {
        String sw = Car.prop("ro.build.product2");
        if (sw.length() == 0) sw = android.os.Build.DISPLAY;
        p.info(Car.str(x, PKG, "generalsetting_system_info_sw_version", "Software Version"), sw);
        String fw = Car.prop("ro.build.display.id");
        if (fw.length() > 0) p.info(Car.str(x, PKG, "generalsetting_system_info_firm_version", "Firmware Version"), fw);
        p.info("Android", android.os.Build.VERSION.RELEASE);
        try {
            p.info("ccNC Setup", x.getPackageManager().getPackageInfo(x.getPackageName(), 0).versionName);
        } catch (Throwable ignored) { }
        // The update itself (USB / over the air) is done by the system updater: uid system, recovery and reboot rights.
        p.note("Software updates are installed by the unit's system updater and cannot be started from ccNC Setup.");
    }

    private static void systemInfo(Page p, Context x) {
        storage(p, Car.str(x, PKG, "applicationsetting_storage_space", "Storage"), android.os.Environment.getDataDirectory());
        try {
            java.io.File ext = android.os.Environment.getExternalStorageDirectory();
            if (ext != null && ext.exists())
                storage(p, Car.str(x, PKG, "applicationsetting_storage_memory_media", "Media File Storage Space"), ext);
        } catch (Throwable ignored) { }
    }

    private static void storage(Page p, String title, java.io.File dir) {
        try {
            android.os.StatFs s = new android.os.StatFs(dir.getPath());
            long free = (long) s.getAvailableBlocks() * s.getBlockSize(), total = (long) s.getBlockCount() * s.getBlockSize();
            if (total <= 0) return;
            p.info(title, android.text.format.Formatter.formatFileSize(p.context(), free) + " Available Of "
                    + android.text.format.Formatter.formatFileSize(p.context(), total));
        } catch (Throwable ignored) { }
    }

    // ---- Time Zone (stock ZoneList) -----------------------------------------------------------------

    /** Zone names / ids as the stock ZoneList shows them for this region: arrays timezone_<r> + timezone_<r>_os. */
    private static String[][] zones(Context x) {
        String region = Car.prop("ro.product.locale.region").toUpperCase(Locale.US);
        String r = "US".equals(region) || "CA".equals(region) || "MX".equals(region) ? "na"
                : "AU".equals(region) || "NZ".equals(region) ? "au" : null;
        try {
            Context sc = Car.pkg(x, PKG);
            if (sc != null && r != null) {
                int n = sc.getResources().getIdentifier("timezone_" + r, "array", PKG);
                int o = sc.getResources().getIdentifier("timezone_" + r + "_os", "array", PKG);
                if (n != 0 && o != 0) {
                    String[] names = sc.getResources().getStringArray(n), ids = sc.getResources().getStringArray(o);
                    if (names.length == ids.length && names.length > 0) return new String[][] { names, ids };
                }
            }
            if (sc != null) {
                int o = sc.getResources().getIdentifier("timezone_etc", "array", PKG);
                if (o != 0) { String[] ids = sc.getResources().getStringArray(o); if (ids.length > 0) return new String[][] { ids, ids }; }
            }
        } catch (Throwable ignored) { }
        if ("na".equals(r)) return new String[][] {
                {"Newfoundland", "Atlantic", "Eastern", "Central", "Mountain", "Pacific", "Alaska", "Hawaii-Aleutian"},
                {"UTC-03:30", "UTC-04:00", "UTC-05:00", "UTC-06:00", "UTC-07:00", "UTC-08:00", "UTC-09:00", "UTC-10:00"}};
        return null;
    }

    private static void timeZone(Page p, final Context x) {
        final String[][] z = zones(x);
        final String title = p.word(3400, "Time Zone");
        if (z == null) {
            TimeZone tz = TimeZone.getDefault();
            p.info(title, tz.getDisplayName(tz.inDaylightTime(new Date()), TimeZone.LONG, Locale.getDefault()));
            return;
        }
        final String[] labels = new String[z[0].length];
        for (int i = 0; i < labels.length; i++) labels[i] = z[0][i].equals(z[1][i]) ? z[0][i] : z[0][i] + " (" + z[1][i] + ")";
        p.choice(title, labels,
                new Fn.Int() { @Override public int get() {
                    // persist.sys.timezone = "Mobis/UTC-05:00" on this firmware
                    String cur = Car.prop("persist.sys.timezone");
                    if (cur.length() == 0) cur = TimeZone.getDefault().getID();
                    for (int i = 0; i < z[1].length; i++) if (cur.equals("Mobis/" + z[1][i]) || cur.equals(z[1][i])) return i;
                    return -1;
                } },
                new Fn.SetInt() { @Override public void set(int i) {
                    if (i < 0 || i >= z[1].length) return;
                    if (dev.ccnc.settings.api.Demo.on()) { dev.ccnc.settings.api.Demo.setProp("persist.sys.timezone", "Mobis/" + z[1][i]); return; }
                    try {
                        ((android.app.AlarmManager) x.getSystemService(Context.ALARM_SERVICE)).setTimeZone("Mobis/" + z[1][i]);
                    } catch (Throwable t) { android.util.Log.w("ccncSetup", "setTimeZone: " + t); }
                } });
    }

    // ---- manual date / time (stock DateTimePickerPref -> util.at.d -> TimePolicyManager) -------------

    private static final java.util.Calendar PENDING = java.util.Calendar.getInstance();
    private static long pendingAt;

    private static void manualTime(Page p, final Context x) {
        p.drill("Set Date and Time", new Fn.Str() {
            @Override public String get() {
                return java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT).format(new Date());
            }
        }, new Fn.Build() {
            @Override public void build(Page q) {
                // Start from the current clock; keep the edited value while the page is being used (2 minutes).
                if (System.currentTimeMillis() - pendingAt > 120000) { PENDING.setTimeZone(TimeZone.getDefault()); PENDING.setTimeInMillis(System.currentTimeMillis()); }
                pendingAt = System.currentTimeMillis();
                field(q, "Year", java.util.Calendar.YEAR, 2020, 2049, 0);
                field(q, "Month", java.util.Calendar.MONTH, 0, 11, 1);
                field(q, "Day", java.util.Calendar.DAY_OF_MONTH, 1, PENDING.getActualMaximum(java.util.Calendar.DAY_OF_MONTH), 0);
                field(q, "Hour", java.util.Calendar.HOUR_OF_DAY, 0, 23, 0);
                field(q, "Minute", java.util.Calendar.MINUTE, 0, 59, 0);
                q.button("Apply", new Fn.Act() { @Override public void run(final Host h) {
                    PENDING.set(java.util.Calendar.SECOND, 0);
                    final long want = PENDING.getTimeInMillis();
                    Object tm = Car.manager(x, "com.hkmc.time.TimePolicyManager");
                    // SRC_USER = 4 (TimePolicyManager constants)
                    Car.call(tm, "setCurrentTimeMillis", new Class<?>[] { Integer.class, Long.class }, Integer.valueOf(4), Long.valueOf(want));
                    new android.os.Handler().postDelayed(new Runnable() { @Override public void run() {
                        boolean ok = dev.ccnc.settings.api.Demo.on() || Math.abs(System.currentTimeMillis() - want) < 90000;
                        android.widget.Toast.makeText(h.activity(), ok ? "Date and Time Set" : "The unit did not accept the time change", android.widget.Toast.LENGTH_LONG).show();
                        pendingAt = 0;
                        if (ok) h.back(); else h.refresh();
                    } }, 800);
                } });
            }
        });
    }

    private static void field(Page q, String title, final int f, final int min, final int max, final int shown) {
        final String[] labels = new String[max - min + 1];
        for (int i = 0; i < labels.length; i++) { int v = min + i + shown; labels[i] = (f == java.util.Calendar.MINUTE && v < 10 ? "0" : "") + v; }
        q.choice(title, labels,
                new Fn.Int() { @Override public int get() { return PENDING.get(f) - min; } },
                new Fn.SetInt() { @Override public void set(int i) { PENDING.set(f, min + i); pendingAt = System.currentTimeMillis(); } });
    }

    // ---- Language (stock LocalePicker) ----------------------------------------------------------------

    private static void language(final Page p, final Context x) {
        // persist.sys.language_list = "en_US,fr_CA,es_US,ko_KR": the languages this firmware offers.
        final java.util.List<Locale> list = new java.util.ArrayList<Locale>();
        for (String s : Car.prop("persist.sys.language_list").split(",")) {
            String[] lc = s.trim().split("_");
            if (lc.length == 2 && lc[0].length() == 2) list.add(new Locale(lc[0], lc[1]));
        }
        final Locale cur = Locale.getDefault();
        boolean granted = dev.ccnc.settings.api.Demo.on() // emulator demo: the choice is kept in memory, the system language is not touched
                || x.checkCallingOrSelfPermission("android.permission.CHANGE_CONFIGURATION") == android.content.pm.PackageManager.PERMISSION_GRANTED;
        if (list.isEmpty() || !granted) {
            p.info(p.word(3021, "Language"), title(cur.getDisplayName(cur)));
            if (!granted) p.note("Changing the language here arrives with the next ccNC for Gen5W update.");
            return;
        }
        final String[] labels = new String[list.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = title(list.get(i).getDisplayName(list.get(i)));
        p.radios(labels,
                new Fn.Int() { @Override public int get() {
                    if (dev.ccnc.settings.api.Demo.on()) return dev.ccnc.settings.api.Demo.getInt("gen.language", 0);
                    Locale now = Locale.getDefault();
                    for (int i = 0; i < list.size(); i++) if (list.get(i).equals(now)) return i;
                    for (int i = 0; i < list.size(); i++) if (list.get(i).getLanguage().equals(now.getLanguage())) return i;
                    return -1;
                } },
                new Fn.SetInt() { @Override public void set(final int i) {
                    if (dev.ccnc.settings.api.Demo.on()) { if (i >= 0 && i < list.size()) dev.ccnc.settings.api.Demo.put("gen.language", i); return; }
                    if (i < 0 || i >= list.size() || list.get(i).equals(Locale.getDefault())) return;
                    Ask.confirm(p.host().activity(), p.word(3021, "Language"), labels[i], "Change", new Runnable() {
                        @Override public void run() { setLocale(list.get(i)); } });
                } });
    }

    private static String title(String s) { return s == null || s.length() == 0 ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1); }

    /** Stock LocalePicker: config.locale = l; config.userSetLocale = true; IActivityManager.updateConfiguration(config). */
    private static void setLocale(Locale l) {
        try {
            Object am = Class.forName("android.app.ActivityManagerNative").getMethod("getDefault").invoke(null);
            android.content.res.Configuration cfg = (android.content.res.Configuration) am.getClass().getMethod("getConfiguration").invoke(am);
            cfg.locale = l;
            try { android.content.res.Configuration.class.getField("userSetLocale").setBoolean(cfg, true); } catch (Throwable ignored) { }
            am.getClass().getMethod("updateConfiguration", android.content.res.Configuration.class).invoke(am, cfg);
            try { android.app.backup.BackupManager.dataChanged("com.android.providers.settings"); } catch (Throwable ignored) { }
        } catch (Throwable t) { android.util.Log.w("ccncSetup", "updateConfiguration: " + t); }
    }

    // ---- Units (stock UnitSetting) ----------------------------------------------------------------------

    private static void units(Category c, final Context x, final GeneralStock st) {
        if (!st.available() || !st.boolOr(GeneralStock.UNITS, "fU", true)) return;
        final boolean ev = Car.pkg(x, "com.hkmc.daudio.app.ev3") != null;
        // {getter/setter suffix, title string, option strings...}; values are 1-based in this order.
        final String[][] rows = {
                {"DistanceSpeedUnit", "unit_distance", "Distance, Speed Unit", "km", "mile"},
                {"TemperatureUnit", "unit_temperature", "Temperature Unit", "℃", "℉"},
                ev ? new String[] {"FuelEfficiencyUnitKm", "unit_energy_economy", "EV Economy Unit", "km/kWh", "kWh/100km"}
                   : new String[] {"FuelEfficiencyUnitKm", "unit_fuel_efficiency", "Fuel Economy Unit", "km/L", "L/100km"},
                ev ? null : new String[] {"FuelEfficiencyUnitMile", "unit_fuel_efficiency", "Fuel Economy Unit", "MPG (US Gallon)", "MPG (UK Gallon)"},
                {"TireAirPressure", "unit_tyre_pressure", "Tire Pressure Unit", "psi", "kPa", "bar"},
                {"TorqueUnit", "unit_torque", "Torque", "Nm", "lb-ft"},
        };
        boolean any = false;
        for (String[] r : rows) if (r != null && st.intCall0(GeneralStock.UNITS, "get" + r[0]) >= 1) any = true;
        if (!any) return;
        c.tab(c.word(3023, st.str("unit_tab", "Units")), new Fn.Build() {
            @Override public void build(Page p) {
                int dist = st.intCall0(GeneralStock.UNITS, "getDistanceSpeedUnit");
                for (final String[] r : rows) {
                    if (r == null) continue;
                    int cur = st.intCall0(GeneralStock.UNITS, "get" + r[0]);
                    final int n = r.length - 3;
                    if (cur < 1 || cur > n) continue; // not reported by this car (or a value this page doesn't know): no row
                    // km-based and mile-based economy units: only the one matching the distance unit, as stock shows.
                    if (r[0].equals("FuelEfficiencyUnitKm") && dist == 2 || r[0].equals("FuelEfficiencyUnitMile") && dist != 2) continue;
                    final String[] labels = new String[n];
                    System.arraycopy(r, 3, labels, 0, n);
                    p.segmented(st.str(r[1], r[2]), labels,
                            new Fn.Int() { @Override public int get() { return st.intCall0(GeneralStock.UNITS, "get" + r[0]) - 1; } },
                            new Fn.SetInt() { @Override public void set(int v) { st.intSet1(GeneralStock.UNITS, "set" + r[0], v + 1); p.host().refresh(); } });
                }
            }
        });
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

    /** Value of Settings.System time_12_24 style keys (string). */
    static String sysString(Context c, String key) {
        try { return Settings.System.getString(c.getContentResolver(), key); } catch (Throwable t) { return null; }
    }
}
