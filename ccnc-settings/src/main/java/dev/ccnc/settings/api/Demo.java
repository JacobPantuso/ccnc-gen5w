package dev.ccnc.settings.api;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Emulator-only demo backend for ccNC Setup: an in-memory "car" (an EV, left-hand drive, Canadian build, a high
 * step of spec) so every category renders and reacts as it would on a unit. Nothing here talks to a real service.
 *
 * It is on ONLY when both hold (checked once, {@link #init}):
 *   - the app is debuggable (ApplicationInfo.FLAG_DEBUGGABLE), and
 *   - the Gen5W automotive framework is not there (com.hkmc.mode.ModeManager cannot be loaded).
 * A car build is not debuggable, so {@link #on()} is always false there and every hook (each one is a single
 * "if (Demo.on())" / "instanceof Demo.Obj" test at a low-level entry point) is skipped: behaviour is unchanged.
 * All values are invented; device names are obviously fake.
 */
public final class Demo {
    private Demo() {}
    private static final String TAG = "ccncDemo";
    private static boolean checked;
    private static volatile boolean on;

    private static final Map<String, Object> STORE = new HashMap<String, Object>();
    private static final Map<String, Object> DEFAULTS = new HashMap<String, Object>();
    private static final Map<String, String> PROPS = new HashMap<String, String>();
    private static final Map<String, String> STRINGS = new HashMap<String, String>();
    private static final Map<String, Obj> MANAGERS = new HashMap<String, Obj>();
    private static final List<String> BT = new ArrayList<String>();

    /** Decides once whether demo mode is on. Safe to call often. */
    public static synchronized void init(Context c) {
        if (checked || c == null) return;
        checked = true;
        boolean debuggable = false;
        try { debuggable = (c.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0; } catch (Throwable ignored) { }
        if (!debuggable) return;
        try { Class.forName("com.hkmc.mode.ModeManager"); return; } catch (Throwable notACar) { }
        seed();
        on = true;
        Log.i(TAG, "demo mode (emulator)");
    }

    public static boolean on() { return on; }

    // ---- store ------------------------------------------------------------------------------------------

    public static synchronized Object get(String key) { Object v = STORE.get(key); return v != null ? v : DEFAULTS.get(key); }
    public static synchronized void put(String key, Object v) { STORE.put(key, v); }
    public static int getInt(String key, int def) { Object v = get(key); return v instanceof Number ? ((Number) v).intValue() : def; }
    public static boolean getBool(String key, boolean def) { Object v = get(key); return v instanceof Boolean ? (Boolean) v : def; }
    public static String getStr(String key, String def) { Object v = get(key); return v != null ? v.toString() : def; }

    private static void seed() {
        // system properties
        PROPS.put("ro.product.opt.stepofspec", "105");
        PROPS.put("ro.product.locale.region", "CA");
        PROPS.put("ro.product.model", "demo");
        PROPS.put("persist.sys.language_list", "en_US,fr_CA,es_US,ko_KR");
        PROPS.put("persist.sys.timezone", "Mobis/UTC-05:00");
        PROPS.put("ro.build.product2", "DEMO.CAN.S5W_M.V000.000.000000");
        PROPS.put("ro.build.display.id", "DEMO dev-keys");
        PROPS.put("persist.sys.key.custom", "gohome");
        PROPS.put("persist.sys.key.swrccustom", "com.hkmc.intent.action.MAP_SHORTCUT");

        // framework managers ("SimpleName#Property"; getX / isX / setX share the property name X)
        DEFAULTS.put("MicomAudioManager#AutoAdjustingVolumeSetting", Boolean.TRUE);
        DEFAULTS.put("MicomAudioManager#NaviDuringCall", Boolean.TRUE);
        DEFAULTS.put("MicomAudioManager#SoundPriority", 1);
        DEFAULTS.put("VideoSetupManager#NewLightSensorOn", Boolean.TRUE);
        DEFAULTS.put("VideoSetupManager#NewBrightnessSettingOption", 1);
        DEFAULTS.put("VideoSetupManager#MinManualBrightLevel", 10);
        DEFAULTS.put("VideoSetupManager#LastManualBrightLevel", 700);
        DEFAULTS.put("BluelightManager#Enable", 1);
        DEFAULTS.put("BluelightManager#BluelightLevel", 5);
        DEFAULTS.put("BluelightManager#Mode", 0);
        DEFAULTS.put("BluelightManager#StartHour", "7");
        DEFAULTS.put("BluelightManager#StartMin", "00");
        DEFAULTS.put("BluelightManager#StartAmPm", "PM");
        DEFAULTS.put("BluelightManager#EndHour", "6");
        DEFAULTS.put("BluelightManager#EndMin", "00");
        DEFAULTS.put("BluelightManager#EndAmPm", "AM");
        DEFAULTS.put("USMManager#Cluster_ThemeCount", 3);
        DEFAULTS.put("USMManager#Lights_AmbientAIVoice", 2);
        DEFAULTS.put("UserProfileManager#UserType", 0);

        // Settings.System keys the sections read
        DEFAULTS.put("sys.androidauto_information", 1);
        DEFAULTS.put("sys.carplay_information", 1);
        DEFAULTS.put("sys.day_theme", 1);
        DEFAULTS.put("sys.sound_mute_nav_guide", 0);

        // smart control ("sc.<type>.<keyID>"): Sound 10
        sc(10, 0x7620, 1);                                   // Centerpoint Surround Technology
        for (int k = 0x4b2; k <= 0x4ba; k++) if (k != 0x4b6) sc(10, k, 20); // guidance volumes, 0..45
        sc(10, 0x2968, 0x5079);                              // Voice Type
        sc(10, 0x297c, 0x508e);                              // Charging Voice Prompts: Medium
        sc(10, 0x77d8, 0);                                   // Weather Information Briefing
        sc(10, 0x77e2, 1);                                   // Welcome Sound
        sc(10, 0x2a30, 0x5142);                              // Active Sound Design: Moderate
        sc(10, 0x2af8, 0x5209);                              // Radio Noise: Original
        sc(10, 0x2b5c, 0x526e);                              // Driver Assistance Warning Volume: Medium
        sc(10, 0x7986, 1);
        sc(10, 0x7990, 1);
        // Theme 15
        sc(15, 12050, 25102);                                // Graphic Themes: Black
        sc(15, 12052, 21062);                                // Screen Saver: Digital Clock
        sc(15, 12056, 25011);                                // Cluster Theme A
        sc(15, 35010, 0);                                    // Link To Drive Mode
        // Bluetooth 2, Phone Projection 7, Data/Network 14
        sc(2, 30004, 1);
        sc(7, 30007, 1);
        sc(7, 30008, 1);
        sc(14, 30004, 0);

        // General (GeneralStock)
        DEFAULTS.put("gen.gps", Boolean.TRUE);
        DEFAULTS.put("gen.24h", Boolean.FALSE);
        DEFAULTS.put("gen.dst", Boolean.FALSE);
        DEFAULTS.put("gen.media_noti", 1);
        DEFAULTS.put("unit.DistanceSpeedUnit", 1);
        DEFAULTS.put("unit.TemperatureUnit", 1);
        DEFAULTS.put("unit.FuelEfficiencyUnitKm", 1);
        DEFAULTS.put("unit.TireAirPressure", 1);

        // Voice Recognition (stock Interface values)
        DEFAULTS.put("voice.VoiceGuidanceSetting$Interface.", 1);
        DEFAULTS.put("voice.VoiceOptionsSetting$Interface.WakeUp", 1);
        DEFAULTS.put("voice.VoiceOptionsSetting$Interface.BargeIn", 0);

        // Network
        DEFAULTS.put("wifi.on", Boolean.TRUE);
        DEFAULTS.put("wifi.connected", "Demo Home Wi-Fi");
        DEFAULTS.put("bt.name", "Demo Vehicle");

        // Bluetooth: two fake phones
        BT.add("Demo iPhone");
        BT.add("Demo Pixel");
        DEFAULTS.put("bt.conn.Demo iPhone", Boolean.TRUE);

        // stock strings the Button / Voice pages need (the stock apps are not on the emulator)
        String[][] s = {
                {"custom_button_setting", "Custom Button ☆"},
                {"custom_button_setting_home", "Home"}, {"custom_button_setting_displayoff", "Display Off"},
                {"custom_button_setting_ev", "EV"}, {"custom_button_setting_bluelink", "Bluelink"},
                {"custom_button_setting_bluetoothaudio", "Bluetooth Audio"}, {"custom_button_setting_weather", "Weather"},
                {"custom_button_setting_driver_assistance", "Driver Assistance"}, {"custom_button_setting_phone", "Phone"},
                {"custom_button_setting_radio", "Radio"}, {"custom_button_setting_setup", "Setup"},
                {"custom_button_setting_none", "None"}, {"phone_projection", "Phone Projection"},
                {"custom_button_swrc_change_device", "Change Bluetooth Device"}, {"custom_button_swrc_endcall", "End Call"},
                {"custom_button_swrc_map", "Map"}, {"custom_button_swrc_nav_cancel_route", "Cancel Route"},
                {"custom_button_swrc_nav_reroute", "Reroute"}, {"custom_button_swrc_voice_memo_start_stop", "Voice Memo"},
                {"custom_button_swrc_privacy_mode", "Privacy Mode"},
                {"steering_mode_button", "MODE Button"}, {"tune_knob_setting", "TUNE Knob Function"},
                {"swrc_updn_tab", "[∧]/[∨] Buttons (Steering Wheel)"},
                {"swrc_updn_preset", "Change Preset Station"}, {"swrc_updn_frequency", "Change Station/Frequency"},
                {"vol_tuneknob_tab", "VOL/TUNE Knobs"}, {"vol_tuneknob_swap", "Swap"},
                {"voice_tab_guidance", "Audible Prompts"}, {"voicesetting_info_title_more", "Beginner"},
                {"voicesetting_info_title_simple", "Normal"}, {"voicesetting_info_title_noting", "Expert"},
                {"voice_tab_vr_options", "Voice Options"}, {"voice_vr_voice_wake_up", "Voice Wake-Up"},
                {"voice_vr_voice_interruption", "Voice Interruption"},
                {"voice_tab_link_vehicle_control_vr", "Link Vehicle Features"},
                {"voicesetting_info_title_vehicle_control", "Mood Lighting"},
                {"voice_tab_rear_vr", "Rear Seat"}, {"voicesetting_info_title_lock_rear", "Lock Rear Seat Voice Recognition"},
        };
        for (String[] e : s) STRINGS.put(e[0], e[1]);
    }

    private static void sc(int type, int key, int v) { DEFAULTS.put("sc." + type + "." + key, v); }

    // ---- Car hooks ----------------------------------------------------------------------------------------

    /** A stand-in for new cls(Context). */
    public static synchronized Obj manager(String cls) {
        Obj o = MANAGERS.get(cls);
        if (o == null) { o = new Obj(cls); MANAGERS.put(cls, o); }
        return o;
    }

    /** com.hkmc.Feature.hasFeature: everything except the China-only ones. */
    public static boolean feature(String name) {
        return name == null || !(name.contains("baidu") || name.contains("carlife") || name.contains("mirrorlink"));
    }

    /** A system property of the demo car, or null (then the real property is read). */
    public static synchronized String prop(String key) { return PROPS.get(key); }
    public static synchronized void setProp(String key, String v) { PROPS.put(key, v); }

    /** A stock string by resource name, or null when the demo has no wording for it. */
    public static String str(String name) { return STRINGS.get(name); }

    // ---- Scontrol hooks -------------------------------------------------------------------------------------

    /** Smart-control value; -4 ("not supported", as the stock apps answer) for keys the demo car does not have. */
    public static int sc(int type, int key) { return getInt("sc." + type + "." + key, -4); }

    public static void scSet(int type, int key, int v) {
        put("sc." + type + "." + key, v);
        // the stock PhoneProjection app also writes the Settings.System flag the page reads
        if (type == 7 && key == 30007) put("sys.androidauto_information", v);
        if (type == 7 && key == 30008) put("sys.carplay_information", v);
        if (type == 14 && key == 30004) put("hotspot.on", v == 1);
        if (type == 15 && key == 12050) put("sys.day_theme", v == 25101 ? 0 : 1);
    }

    /** Guidance volume levels (Sound 0x4b2..0x4ba) answer with minValue 0 / maxValue 45. */
    public static boolean scIsLevel(int type, int key) { return type == 10 && key >= 0x4b2 && key <= 0x4ba; }

    // ---- Settings.System (Sys) hooks ------------------------------------------------------------------------

    public static int sysInt(String key, int def) { return getInt("sys." + key, def); }
    public static void sysPut(String key, int v) { put("sys." + key, v); }

    // ---- stock static checks (ButtonSectionStock / GeneralStock / Voice) ------------------------------------

    /** A stock app's static boolean feature check, by method name. */
    public static boolean check(String method) {
        return !("usedNButton".equals(method) || "usedOneKnobSwap".equals(method) || "checkedRRCMenu".equals(method)
                || "isIndia".equals(method) || "isSGP".equals(method) || "checkServerVROptions".equals(method)
                || "checkRearVROptions".equals(method) || "checkCarModel".equals(method));
    }

    // ---- Bluetooth ------------------------------------------------------------------------------------------

    public static synchronized List<String> btDevices() { return new ArrayList<String>(BT); }
    public static synchronized void btDelete(String name) { BT.remove(name); }
    public static boolean btConnected(String name) { return getBool("bt.conn." + name, false); }
    public static void btConnect(String name, boolean c) { put("bt.conn." + name, c); }

    // ---- a framework manager ----------------------------------------------------------------------------------

    /** Demo stand-in for a com.hkmc.* manager: getX / isX read property X, setX(v) writes it. */
    public static final class Obj {
        public final String cls;
        private final String simple;
        Obj(String cls) { this.cls = cls; this.simple = cls.substring(cls.lastIndexOf('.') + 1); }

        private String k(String prop) { return simple + "#" + prop; }

        public Object call(String m, Object... a) {
            try {
                if (a == null) a = new Object[0];
                if ("MicomAudioManager".equals(simple)) {
                    if (m.equals("getSoundSetting")) {
                        int t = ((Number) a[0]).intValue();
                        return Integer.valueOf(Demo.getInt(k("snd" + t), t == 0x2f ? 1 : 10));
                    }
                    if (m.equals("getSoundAvailability")) return Integer.valueOf(1);
                    if (m.equals("setSound")) { put(k("snd" + ((Number) a[0]).intValue()), a[1]); return null; }
                    if (m.equals("setBassMidTreb")) { put(k("snd8"), a[0]); put(k("snd9"), a[1]); put(k("snd10"), a[2]); return null; }
                    if (m.equals("setFadeBalance")) { put(k("snd5"), a[0]); put(k("snd6"), a[1]); return null; }
                }
                if ("VideoSetupManager".equals(simple) && (m.equals("setUserBrightness") || m.equals("setManualBrightness"))) {
                    put(k("LastManualBrightLevel"), a[0]);
                    return null;
                }
                if ("BluelightManager".equals(simple) && m.equals("setOperationTime")) {
                    // (which: START 1 / END 0, "<h>hh<mm>mm<AM|PM>")
                    String part = ((Number) a[0]).intValue() == 1 ? "Start" : "End", v = String.valueOf(a[1]);
                    int hh = v.indexOf("hh"), mm = v.indexOf("mm");
                    if (hh > 0 && mm > hh) {
                        put(k(part + "Hour"), v.substring(0, hh));
                        put(k(part + "Min"), v.substring(hh + 2, mm));
                        put(k(part + "AmPm"), v.substring(mm + 2));
                    }
                    return null;
                }
                if ("ModeManager".equals(simple)) {
                    if (m.equals("setCustomKey")) { setProp("persist.sys.key.custom", String.valueOf(a[0])); return Boolean.TRUE; }
                    if (m.equals("setKey")) { setProp("persist.sys.key.swrccustom", String.valueOf(a[1])); return Boolean.TRUE; }
                }
                if ("TimePolicyManager".equals(simple) && m.equals("setCurrentTimeMillis")) return Boolean.TRUE;
                if (m.startsWith("set") && a.length >= 1) { put(k(m.substring(3)), a[a.length - 1]); return null; }
                if (m.startsWith("get")) return Demo.get(k(m.substring(3)));
                if (m.startsWith("is")) return Demo.get(k(m.substring(2)));
            } catch (Throwable t) { Log.w(TAG, simple + "." + m + ": " + t); }
            return null;
        }
    }
}
