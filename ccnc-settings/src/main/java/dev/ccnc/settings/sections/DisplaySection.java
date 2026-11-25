package dev.ccnc.settings.sections;

import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.util.Log;

import dev.ccnc.settings.api.Category;
import dev.ccnc.settings.api.Fn;
import dev.ccnc.settings.api.Page;
import dev.ccnc.settings.api.Section;
import dev.ccnc.settings.api.Sys;

/**
 * Display (stock classId com.android.DisplaySettingsModeCheck, app com.android.displaysettings).
 *
 * Mirrors what the car's stock DisplaySetting shows (Illumination with the new light sensor, Blue Light Filter,
 * Camera Settings). Only stable, non-obfuscated APIs are used (the stock app's obfuscated helpers differ per
 * firmware): com.hkmc.setup.VideoSetupManager, com.hkmc.bluelight.BluelightManager,
 * com.hkmc.userprofile.UserProfileManager (automotive.jar), the Settings.System backup keys the stock app writes
 * (setting_option_information[_drN], user_bright[_drN], manual_bright[_drN]) and stock resource names.
 */
public final class DisplaySection extends Section {
    private static final String TAG = "ccnc.DisplaySection";
    private static final String PKG = "com.android.displaysettings";

    @Override public String classId() { return "com.android.DisplaySettingsModeCheck"; }
    @Override public int treeNode() { return 1003; }
    @Override public String icon() { return "set_ic_menu_display"; }

    private Context sc;   // stock package context (resources, class loader)
    private static Object sVideo;  // one VideoSetupManager for the process (its listener must stay registered)

    // ---- reflection helpers ----
    private static Object make(Context x, String cls) {
        try {
            return Class.forName(cls).getConstructor(Context.class).newInstance(x.getApplicationContext());
        } catch (Throwable t) {
            Log.w(TAG, cls + " unavailable", t);
            return null;
        }
    }

    private static Object call(Object o, String m) throws Exception { return o.getClass().getMethod(m).invoke(o); }

    private static int callInt(Object o, String m, int def) {
        try { return ((Number) call(o, m)).intValue(); } catch (Throwable t) { Log.w(TAG, m + " failed: " + t); return def; }
    }

    private static boolean callBool(Object o, String m, boolean def) {
        try { return Boolean.TRUE.equals(call(o, m)); } catch (Throwable t) { Log.w(TAG, m + " failed: " + t); return def; }
    }

    private static String callStr(Object o, String m) {
        try { Object r = call(o, m); return r == null ? null : r.toString(); } catch (Throwable t) { return null; }
    }

    private static void callSet(Object o, String m, int v) {
        try { o.getClass().getMethod(m, int.class).invoke(o, v); Log.i(TAG, m + "(" + v + ")"); }
        catch (Throwable t) { Log.w(TAG, m + " failed", t); }
    }

    /** Stock string by resource name (car firmware), else fallback. */
    private String str(String name, String fallback) {
        try {
            int id = sc.getResources().getIdentifier(name, "string", PKG);
            if (id != 0) return sc.getResources().getString(id);
        } catch (Throwable ignored) { }
        return fallback;
    }

    /** Per-user suffix of the stock backup keys: user type 1 -> _dr1, 2 -> _dr2, otherwise (guest) none. */
    private static String userKey(Context x, String base) {
        Object up = make(x, "com.hkmc.userprofile.UserProfileManager");
        int u = up == null ? -1 : callInt(up, "getUserType", -1);
        return u == 1 ? base + "_dr1" : u == 2 ? base + "_dr2" : base;
    }

    private static int sysGet(Context x, String key, int def) {
        try { return Settings.System.getInt(x.getContentResolver(), key); } catch (Throwable t) { return def; }
    }

    // ---- build ----
    @Override public void build(Category c) {
        final Context x = c.context();
        try {
            sc = x.createPackageContext(PKG, Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
        } catch (Throwable t) {
            Log.w(TAG, "stock DisplaySettings missing: " + t);
            sc = null;
        }
        if (sVideo == null && sc != null) sVideo = make(x, "com.hkmc.setup.VideoSetupManager");
        final Object video = sVideo;
        if (video != null && callBool(video, "isNewLightSensorOn", false)) {
            illumination(c, x, video);
        } else {
            // No stock new-light-sensor illumination here: plain Android brightness (Settings.System).
            Log.i(TAG, "VideoSetupManager " + (video == null ? "missing" : "old light sensor") + ": generic brightness");
            c.tab(c.word(2001, "Brightness"), new Fn.Build() {
                @Override public void build(Page p) {
                    p.toggle(p.word(2002, "Automatically Adjust Brightness"),
                            new Fn.Bool() { @Override public boolean get() { return Sys.getInt(x, "screen_brightness_mode", 0) == 1; } },
                            new Fn.SetBool() { @Override public void set(boolean on) { Sys.putInt(x, "screen_brightness_mode", on ? 1 : 0); } });
                    if (Sys.getInt(x, "screen_brightness", -1) >= 0)
                        p.slider(p.word(2007, "Brightness"), 0, 255,
                                new Fn.Int() { @Override public int get() { return Sys.getInt(x, "screen_brightness", 0); } },
                                new Fn.SetInt() { @Override public void set(int v) { Sys.putInt(x, "screen_brightness", v); } });
                }
            });
        }
        if (sc == null) return;
        if (supportsBluelight()) {
            Object bl = make(x, "com.hkmc.bluelight.BluelightManager");
            if (bl != null) bluelight(c, bl);
        }
        camera(c, x);
    }

    /**
     * Stock DisplayIlluminationNew with the new light sensor: "Brightness Auto-Adjusts" check box
     * (VideoSetupManager get/setNewBrightnessSettingOption, 1 = auto, 2 = manual; backup setting_option_information)
     * and one brightness bar min..100 (auto: setUserBrightness(v*10) + backup user_bright, as logged by the stock
     * app: "setUserBrightness_DS val: 4" -> service "unsigned_brightness = [40]"; manual: setManualBrightness(v*10)
     * + backup manual_bright, read getLastManualBrightLevel()/10).
     */
    private void illumination(Category c, final Context x, final Object video) {
        c.tab(c.word(2001, "Brightness"), new Fn.Build() {
            @Override public void build(Page p) {
                p.toggle(p.word(2002, "Automatically Adjust Brightness"),
                        new Fn.Bool() { @Override public boolean get() { return callInt(video, "getNewBrightnessSettingOption", 1) == 1; } },
                        new Fn.SetBool() { @Override public void set(boolean on) {
                            int mode = on ? 1 : 2;
                            callSet(video, "setNewBrightnessSettingOption", mode);
                            Sys.putInt(x, userKey(x, "setting_option_information"), mode);
                        } });
                final int min = Math.max(1, callInt(video, "getMinManualBrightLevel", 10) / 10);  // stock: getMinBright = raw / 10
                requestCurrent(x, video, p.host());
                p.slider(p.word(2007, "Brightness"), min, 100,
                        new Fn.Int() { @Override public int get() { return brightness(x, video, min); } },
                        new Fn.SetInt() { @Override public void set(int v) {
                            if (callInt(video, "getNewBrightnessSettingOption", 1) == 1) {
                                callSet(video, "setUserBrightness", v * 10);
                                Sys.putInt(x, userKey(x, "user_bright"), v);
                            } else {
                                callSet(video, "setManualBrightness", v * 10);
                                Sys.putInt(x, userKey(x, "manual_bright"), v);
                            }
                        } });
            }
        });
    }

    private static int brightness(Context x, Object video, int min) {
        int v;
        if (callInt(video, "getNewBrightnessSettingOption", 1) == 1) {
            // Stock bar shows the service's current brightness (onCurrentBrightness, 10 per step), else user_bright.
            int cur = DisplaySectionDex.last();
            v = cur > 0 ? cur / 10 : sysGet(x, userKey(x, "user_bright"), -1);
            if (v < min || v > 100) v = callInt(video, "getLastManualBrightLevel", -1) / 10;
        } else {
            v = callInt(video, "getLastManualBrightLevel", -1) / 10;
        }
        Log.i(TAG, "brightness read " + v + " (current " + DisplaySectionDex.last() + ")");
        return v < min ? min : v > 100 ? 100 : v;
    }

    private static Object listener;

    /** Registers the onCurrentBrightness listener once and asks the service for the current brightness. */
    private static void requestCurrent(Context x, Object video, final dev.ccnc.settings.api.Host host) {
        try {
            Class<?> k = DisplaySectionDex.listener(x);
            if (k == null) return;
            if (listener == null) {
                listener = k.newInstance();
                video.getClass().getMethod("addListener", k.getSuperclass()).invoke(video, listener);
            }
            final int before = DisplaySectionDex.last();
            final android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
            DisplaySectionDex.callback(new Runnable() {
                @Override public void run() {
                    final int now = DisplaySectionDex.last();
                    Log.i(TAG, "onCurrentBrightness " + now);
                    DisplaySectionDex.callback(null);
                    if (now != before) h.post(new Runnable() { @Override public void run() { try { host.refresh(); } catch (Throwable ignored) { } } });
                }
            });
            call(video, "requestCurrentBrightness");
        } catch (Throwable t) {
            Log.w(TAG, "requestCurrentBrightness failed", t);
        }
    }

    /** Stock check: Utils.isSupportMinimumStep(6) (public, inherited by the stock Utils class a.d). */
    private boolean supportsBluelight() {
        try {
            Class<?> d = sc.getClassLoader().loadClass(PKG + ".a.d");
            boolean r = Boolean.TRUE.equals(d.getMethod("isSupportMinimumStep", int.class).invoke(null, 6));
            Log.i(TAG, "isSupportMinimumStep(6) = " + r);
            return r;
        } catch (Throwable t) {
            Log.w(TAG, "isSupportMinimumStep not callable: " + t);
            return true;  // BluelightManager present decides
        }
    }

    private static int levelOf(String name, int def) {
        try {
            Class<?> e = Class.forName("com.hkmc.bluelight.BluelightConstants$BluelightLevel");
            Object v = e.getField(name).get(null);
            return ((Number) e.getMethod("getLevel").invoke(v)).intValue();
        } catch (Throwable t) { return def; }
    }

    /** Stock DisplayBluelight / DisplayBluelightTime through com.hkmc.bluelight.BluelightManager. */
    private void bluelight(Category c, final Object bl) {
        final int lo = levelOf("LEVEL1", 1), hi = levelOf("LEVEL9", 9);
        c.tab(c.word(2019, "Blue Light Filter"), new Fn.Build() {
            @Override public void build(Page p) {
                final boolean on = callInt(bl, "isEnable", 0) == 1;
                p.toggle(p.word(2020, "Use Blue Light Filter"),
                        new Fn.Bool() { @Override public boolean get() { return callInt(bl, "isEnable", 0) == 1; } },
                        new Fn.SetBool() { @Override public void set(boolean v) { callSet(bl, "setEnable", v ? 1 : 0); p.host().refresh(); } });
                p.slider(p.word(2022, "Warmer"), lo, hi,
                        new Fn.Int() { @Override public int get() { return callInt(bl, "getBluelightLevel", lo); } },
                        new Fn.SetInt() { @Override public void set(int v) { callSet(bl, "setBluelightLevel", v); } })
                        .enabled(on);
                final String[] modes = { p.word(2023, "Automatic"), p.word(2024, "User-Set Time") };
                p.drill(str("display_setting_bluelight_time_desc", "Blue Light Filter Settings"), new Fn.Str() {
                    @Override public String get() { int m = callInt(bl, "getMode", -1); return m == 0 || m == 1 ? modes[m] : null; }
                }, new Fn.Build() {
                    @Override public void build(Page q) {
                        // BluelightConstants.BluelightMode: AUTO 0, SCHEDULED_BY_USER 1.
                        q.radios(modes,
                                new Fn.Int() { @Override public int get() { return callInt(bl, "getMode", -1); } },
                                new Fn.SetInt() { @Override public void set(int v) { callSet(bl, "setMode", v); } });
                        if (callInt(bl, "getMode", -1) != 1) return;
                        time(q, bl, str("displaysetting_bluelight_start", "Start Time"), 1, "Start");
                        time(q, bl, str("displaysetting_bluelight_end", "End Time"), 0, "End");
                    }
                }).enabled(on);
            }
        });
    }

    /**
     * One schedule time (BluelightConstants.BluelightTime START 1 / END 0), stored by the service as "7hh00mmPM";
     * written with setOperationTime(which, "<h>hh<mm>mm<AM|PM>") + saveBluelightAlram().
     */
    private void time(Page q, final Object bl, final String title, final int which, final String part) {
        q.drill(title, new Fn.Str() {
            @Override public String get() {
                String h = callStr(bl, "get" + part + "Hour"), m = callStr(bl, "get" + part + "Min"), ap = callStr(bl, "get" + part + "AmPm");
                return h == null || m == null ? null : h + ":" + m + (ap == null ? "" : " " + ap);
            }
        }, new Fn.Build() {
            @Override public void build(Page r) {
                final String[] hours = new String[12];
                for (int i = 0; i < 12; i++) hours[i] = String.valueOf(i + 1);
                final String[] mins = new String[60];
                for (int i = 0; i < 60; i++) mins[i] = (i < 10 ? "0" : "") + i;
                final String[] ampm = { "AM", "PM" };
                r.choice(str("HR", "HR"), hours,
                        new Fn.Int() { @Override public int get() { return parse(callStr(bl, "get" + part + "Hour")) - 1; } },
                        new Fn.SetInt() { @Override public void set(int v) { write(bl, which, part, v + 1, -1, null); } });
                r.choice(str("MIN", "MIN"), mins,
                        new Fn.Int() { @Override public int get() { return parse(callStr(bl, "get" + part + "Min")); } },
                        new Fn.SetInt() { @Override public void set(int v) { write(bl, which, part, -1, v, null); } });
                r.segmented("", ampm,
                        new Fn.Int() { @Override public int get() { return "PM".equalsIgnoreCase(callStr(bl, "get" + part + "AmPm")) ? 1 : 0; } },
                        new Fn.SetInt() { @Override public void set(int v) { write(bl, which, part, -1, -1, ampm[v]); } });
            }
        });
    }

    private static int parse(String s) { try { return Integer.parseInt(s.trim()); } catch (Throwable t) { return -1; } }

    private static void write(Object bl, int which, String part, int hour, int min, String ap) {
        int h = hour > 0 ? hour : parse(callStr(bl, "get" + part + "Hour"));
        int m = min >= 0 ? min : parse(callStr(bl, "get" + part + "Min"));
        String a = ap != null ? ap : callStr(bl, "get" + part + "AmPm");
        if (h < 1 || h > 12 || m < 0 || m > 59 || a == null) return;
        String v = h + "hh" + (m < 10 ? "0" : "") + m + "mm" + a.toUpperCase();
        try {
            bl.getClass().getMethod("setOperationTime", int.class, String.class).invoke(bl, which, v);
            call(bl, "saveBluelightAlram");
            Log.i(TAG, "setOperationTime(" + which + "," + v + ")");
        } catch (Throwable t) { Log.w(TAG, "setOperationTime failed", t); }
    }

    /** Stock DisplayCameraSetting: "Display Contents" opens com.android.vehiclesettings RearCameraSecondDepth. */
    private void camera(Category c, Context x) {
        final Intent i = component("com.android.vehiclesettings", "com.android.vehiclesettings.rearcamera.RearCameraSecondDepth");
        if (x.getPackageManager().resolveActivity(i, 0) == null) return;
        final String row = str("camera_settings_display_contents", "Display Contents");
        c.tab(c.word(2031, "Camera Settings"), new Fn.Build() {
            @Override public void build(Page p) { p.activity(row, i); }
        });
    }

    // Left in the stock entry: Illumination "Reset" (factory reset of the display settings) and the top-bar
    // "Display Off" button; complete() stays false for them.
    @Override public Intent stockIntent(Context c) { return component("com.android.displaysettings", ".DisplaySetting"); }
}
