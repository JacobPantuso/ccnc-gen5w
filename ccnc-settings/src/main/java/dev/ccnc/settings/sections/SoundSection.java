package dev.ccnc.settings.sections;

import android.content.Context;

import java.lang.reflect.Method;

import dev.ccnc.settings.api.Ask;
import dev.ccnc.settings.api.Car;
import dev.ccnc.settings.api.Category;
import dev.ccnc.settings.api.Fn;
import dev.ccnc.settings.api.Host;
import dev.ccnc.settings.api.Page;
import dev.ccnc.settings.api.Scontrol;
import dev.ccnc.settings.api.Section;
import dev.ccnc.settings.api.Sys;

/**
 * Sound (stock classId com.android.SoundSetting, app com.android.soundsettings).
 *
 * Stock SoundSetting.onCreate on this unit (ro.product.opt.stepofspec=105, so Utils.isSupportKR20y6th() is true)
 * adds, in order: Premium Sound (SoundAdvancedManager.isNeedShowPremiumSound), Position (Utils.isSupportSoundPosition:
 * not a bus and stepofspec >= 15), Tone, Guidance, Active Sound Design, Radio Noise, Driver Assistance, Connected Devices.
 * The stock FrameworkManager wrappers delegate 1:1 to com.hkmc.media.MicomAudioManager (automotive.jar); we call the
 * same methods by reflection:
 *   Tone      getSoundSetting(BASS 8 / MIDDLE 9 / TREBLE 10), 0..20 (stock shows -10..+10), setBassMidTreb(b, m, t)
 *   Position  getSoundSetting(FADE 5 / BALANCE 6), 0..20 centre 10, setFadeBalance(fade, balance)
 *   Guidance  (only when Feature com.mobis.software.navigation or com.hkmc.software.baidu, as SoundSettingApp sets
 *             Utils.isSupportNavigation): Navigation Guidance During Calls get/setNaviDuringCall(bool);
 *             Navigation Volume Priority getSoundPriority()==NAVI_FIRST(1), set 1 (on) / SAME_MIX_AV_NAVI 3 (off),
 *             the values DrivingGuidance.onCheckItemClick writes.
 * Everything else goes through the stock app's own smart-control bridge ({@link Scontrol}, type 10, handled by
 * SoundSettingService -> search.CcVRcontrol / CfgPremiumSound / CfgASD): the stock app reads and writes the value
 * itself (FrameworkManager + its SettingsProvider + up-sync), so the car gets exactly the stock call. Key ids are
 * com.hkmc.ssearch.SSearchConstants$SID$Sound. Rows appear only for settings the stock app reports on this car.
 *   Premium Sound   Live Dynamic, QLS, Clari-Fi, Bass Boost, Centerpoint, Arkamys, Meridian, SDVC (as fitted)
 *   Guidance        Guidance Volumes (levels 1202..1210 with the stock min/max), Voice Type, Charging Voice Prompts,
 *                   Weather Information Briefing, Welcome Sound
 *   Active Sound Design, Radio Noise, Driver Assistance (Warning Volume, Driving / Parking Safety Priority)
 *   Reset           Tone and Position back to centre (the stock "Reset" also clears the amplifier options; here
 *                   only the two things this page can show are reset, after a confirmation)
 * Not ported: Connected Devices (per-projection volumes; the stock ConnDeviceVolumes screen has no smart-control
 * keys) - shown as a note. Nothing here opens a stock screen.
 */
public final class SoundSection extends Section {
    @Override public String classId() { return "com.android.SoundSetting"; }
    @Override public int treeNode() { return 1002; }
    @Override public String icon() { return "set_ic_menu_sound"; }

    private static final int FADE = 5, BALANCE = 6, BASS = 8, MIDDLE = 9, TREBLE = 10;
    private static final int NAVI_FIRST = 1, SAME_MIX_AV_NAVI = 3, BOSE_DSC = 0x2f;

    private static final String PKG = "com.android.soundsettings";

    // ---- smart-control key ids (SSearchConstants$SID$Sound) ----
    private static final int K_LIVE_DYNAMIC = 0x27d8, K_QLS = 0x27e2, K_ARKAMYS = 0x280a, K_HORIZON = 0x2814,
            K_INTELLIQ = 0x281e, K_SDVC4 = 0x2828, K_CLARIFI = 0x760c, K_BASS_BOOST = 0x7616, K_BOSE_CP = 0x7620,
            K_ARKAMYS_AVC = 0x762f, K_SDVC2 = 0x7652, K_LD_LITE = 0x7670, K_QLS_LITE = 0x767a;
    private static final int[] PREMIUM = {K_LIVE_DYNAMIC, K_QLS, K_ARKAMYS, K_HORIZON, K_INTELLIQ, K_SDVC4, K_CLARIFI,
            K_BASS_BOOST, K_BOSE_CP, K_ARKAMYS_AVC, K_SDVC2, K_LD_LITE, K_QLS_LITE};
    private static final int K_VOICE_TYPE = 0x2968, K_CVP = 0x297c, K_WEATHER = 0x77d8, K_WELCOME = 0x77e2;
    /** Guidance volume levels (CcVRcontrol.setVolumeCCVR switch 0x4b2..0x4ba; 0x4b6 is unused). */
    private static final int V_BEEP = 0x4b2, V_RINGTONE = 0x4b3, V_VR = 0x4b4, V_PROMPTS = 0x4b5, V_NAVI = 0x4b7,
            V_ALERTS = 0x4b8, V_NAVI_FX = 0x4b9, V_CLUSTER = 0x4ba;
    private static final int[] VOLUMES = {V_NAVI, V_NAVI_FX, V_VR, V_PROMPTS, V_RINGTONE, V_ALERTS, V_CLUSTER, V_BEEP};
    private static final int K_ASD = 0x2a30, K_ESE = 0x2a3a, K_EV_ASD = 0x2a58, K_APS = 0x2a76, K_VESS = 0x2a80, K_ANC = 0x78aa;
    private static final int[] ACTIVE = {K_ASD, K_ESE, K_EV_ASD, K_APS, K_VESS, K_ANC};
    private static final int K_RNC = 0x2af8;
    private static final int K_DA_WARN = 0x2b5c, K_DA_DRIVE_PRIO = 0x7986, K_DA_PARK_PRIO = 0x7990;
    private static final int[] DA = {K_DA_WARN, K_DA_DRIVE_PRIO, K_DA_PARK_PRIO};

    private static String st(Context x, String name, String fallback) { return Car.str(x, PKG, name, fallback); }

    private static boolean any(Scontrol s, int[] keys) { for (int k : keys) if (s.has(k)) return true; return false; }

    @Override public void build(Category c) {
        final Context x = c.context();
        final Scontrol sc = Scontrol.of(x, PKG, 10);
        sc.prefetch(PREMIUM); sc.prefetch(VOLUMES); sc.prefetch(ACTIVE); sc.prefetch(DA);
        sc.prefetch(K_VOICE_TYPE, K_CVP, K_WEATHER, K_WELCOME, K_RNC);
        if (!sc.anyAnswer()) sc.await(1500);
        final Audio a = Audio.get(x);
        if (a == null) { smartTabs(c, x, sc, null, false, false); return; } // no MicomAudioManager (emulator)

        // Premium Sound (PremiumSound.getList): Dynamic Speed Compensation when SoundAdvancedManager has BOSE_DSC
        // (getAmpType Bose + getSoundAvailability(BOSE_DSCMODE 0x2f) == 1; value getSoundSetting == 1, write setSound(0x2f, 1/0));
        // Start-up Volume Limit on KR20y6th builds (stepofspec >= 25): get/setAutoAdjustingVolumeSetting(boolean).
        final boolean dsc = a.integer("getSoundAvailability", BOSE_DSC) == 1;
        final boolean startup = step(25);
        if (dsc || startup || any(sc, PREMIUM)) {
            c.tab(c.word(1023, "Premium Sound"), new Fn.Build() {
                @Override public void build(Page p) {
                    premium(p, x, sc);
                    if (dsc) p.toggle(p.word(1026, "Dynamic Speed Compensation"), a.flag(BOSE_DSC), a.setFlag(BOSE_DSC));
                    if (startup) p.toggle(p.word(1025, "Start-up Volume Limit"),
                            new Fn.Bool() { @Override public boolean get() { return a.bool("getAutoAdjustingVolumeSetting"); } },
                            new Fn.SetBool() { @Override public void set(boolean on) { a.call("setAutoAdjustingVolumeSetting", new Class[]{boolean.class}, on); } });
                }
            });
        }

        // Position: fader / balance (0..20, 10 = centre). Stock shows F=n when fade < 10 and L=n when balance < 10,
        // so the bars run Rear -> Front and Left -> Right: fader shows 10 - fade (+ = front), balance shows balance - 10.
        if (supportSoundPosition() && a.setting(FADE) >= 0 && a.setting(BALANCE) >= 0) {
            c.tab(c.word(1032, "Position"), new Fn.Build() {
                @Override public void build(Page p) {
                    p.slider("Fader", -10, 10,
                            new Fn.Int() { @Override public int get() { return 10 - a.setting(FADE); } },
                            new Fn.SetInt() { @Override public void set(int v) { a.fadeBalance(10 - v, a.setting(BALANCE)); } });
                    p.slider("Balance", -10, 10,
                            new Fn.Int() { @Override public int get() { return a.setting(BALANCE) - 10; } },
                            new Fn.SetInt() { @Override public void set(int v) { a.fadeBalance(a.setting(FADE), v + 10); } });
                }
            });
        }

        // Tone (stock AudioTune seek bars, top to bottom: Treble, Midrange, Bass; -10..+10).
        if (a.setting(BASS) >= 0 && a.setting(MIDDLE) >= 0 && a.setting(TREBLE) >= 0) {
            c.tab(c.word(1033, "Tone"), new Fn.Build() {
                @Override public void build(Page p) {
                    p.slider(p.word(1034, "Treble"), -10, 10,
                            new Fn.Int() { @Override public int get() { return a.setting(TREBLE) - 10; } },
                            new Fn.SetInt() { @Override public void set(int v) { a.tone(a.setting(BASS), a.setting(MIDDLE), v + 10); } });
                    p.slider(p.word(1036, "Midrange"), -10, 10,
                            new Fn.Int() { @Override public int get() { return a.setting(MIDDLE) - 10; } },
                            new Fn.SetInt() { @Override public void set(int v) { a.tone(a.setting(BASS), v + 10, a.setting(TREBLE)); } });
                    p.slider(p.word(1035, "Bass"), -10, 10,
                            new Fn.Int() { @Override public int get() { return a.setting(BASS) - 10; } },
                            new Fn.SetInt() { @Override public void set(int v) { a.tone(v + 10, a.setting(MIDDLE), a.setting(TREBLE)); } });
                }
            });
        }

        // Guidance (DrivingGuidance.getList on this build, in order). Guidance Volumes (volume groups with preview audio),
        // Charging Voice Prompts (EV) and Navigation Alerts (navigation screen) open the stock screen.
        final boolean navi = supportNavigation();
        c.tab(c.word(1038, "Guidance"), new Fn.Build() {
            @Override public void build(Page p) {
                guidance(p, x, sc);
                if (navi && !isBus()) {
                    p.toggle(p.word(1051, "Navigation Guidance During Calls"),
                            new Fn.Bool() { @Override public boolean get() { return a.bool("getNaviDuringCall"); } },
                            new Fn.SetBool() { @Override public void set(boolean on) { a.call("setNaviDuringCall", new Class[]{boolean.class}, on); } });
                    p.toggle(p.word(1052, "Navigation Volume Priority"),
                            new Fn.Bool() { @Override public boolean get() { return a.integer("getSoundPriority") == NAVI_FIRST; } },
                            new Fn.SetBool() { @Override public void set(boolean on) { a.call("setSoundPriority", new Class[]{int.class}, on ? NAVI_FIRST : SAME_MIX_AV_NAVI); } });
                    // SettingsProvider column sound_mute_nav_guide (+ _dr1/_dr2 for UserProfileManager.getUserType() 1/2).
                    p.toggle(p.word(1053, "Mute Navigation Guidance"),
                            new Fn.Bool() { @Override public boolean get() { return Sys.getInt(x, muteKey(x), 0) == 1; } },
                            new Fn.SetBool() { @Override public void set(boolean on) { Sys.putInt(x, muteKey(x), on ? 1 : 0); } });
                }
            }
        });
        smartTabs(c, x, sc, a, true, true);
    }

    /** Premium Sound rows the stock app reports (amplifier dependent; none on a base audio system). */
    private static void premium(Page p, Context x, Scontrol sc) {
        sc.attach(p.host());
        sc.group(p, K_LIVE_DYNAMIC, st(x, "sound_advanced_item_title_live_dynamic", "Live Dynamic"),
                new int[] {0x4ee9, 0x4eea, 0x4eeb},
                new String[] {st(x, "sound_advanced_live_dynamic_value_natural", "Natural"),
                        st(x, "sound_advanced_live_dynamic_value_concert", "Concert"), st(x, "sound_advanced_reference", "Reference")});
        sc.check(p, K_LD_LITE, st(x, "sound_advanced_item_title_live_dynamic", "Live Dynamic"));
        sc.group(p, K_QLS, st(x, "sound_advanced_item_title_quantum_logic_surround", "Quantum Logic Surround"),
                new int[] {0x4ef3, 0x4ef4, 0x4ef5},
                new String[] {st(x, "sound_advanced_qls_value_audience_mode", "Audience"),
                        st(x, "sound_advanced_qls_value_on_stage_mode", "On Stage"), st(x, "sound_advanced_reference", "Reference")});
        sc.check(p, K_QLS_LITE, st(x, "sound_advanced_item_title_quantum_logic_surround", "Quantum Logic Surround"));
        sc.check(p, K_CLARIFI, st(x, "sound_advanced_item_title_clari_fi", "Clari-Fi"));
        sc.check(p, K_BASS_BOOST, st(x, "sound_advanced_bass_boost", "Bass Boost"));
        sc.check(p, K_BOSE_CP, st(x, "sound_advanced_bose_cp", "Centerpoint Surround Technology"));
        sc.group(p, K_ARKAMYS, st(x, "soundsetting_sound_mode", "Arkamys Sound Mood"),
                new int[] {0x4f1c, 0x4f1b, 0x4f1e, 0x4f1d},
                new String[] {st(x, "soundsetting_sound_mode_natural", "Natural"), st(x, "soundsetting_sound_mode_club", "Club"),
                        st(x, "soundsetting_sound_mode_longue", "Lounge"), st(x, "soundsetting_sound_mode_live", "Live")});
        sc.check(p, K_ARKAMYS_AVC, st(x, "soundsetting_sound_mode_automatic_volume_control", "Automatic Volume Control"));
        // Meridian Horizon "Custom" (imaging / immersion bars) has no smart-control levels: Authentic / Surround only.
        sc.group(p, K_HORIZON, st(x, "sound_advanced_meridian_horizon_title", "Meridian Horizon"),
                new int[] {0x4f25, 0x4f26},
                new String[] {st(x, "sound_advanced_meridian_horizon_authentic", "Authentic"),
                        st(x, "sound_advanced_meridian_horizon_surround", "Surround")});
        String[] four = {st(x, "common_strong", "Strong"), st(x, "common_normal", "Normal"), st(x, "common_soft", "Soft"), st(x, "common_off", "Off")};
        sc.group(p, K_INTELLIQ, st(x, "sound_advanced_meridian_intelliq_title", "Meridian Intelli-Q"),
                new int[] {0x4f2f, 0x4f30, 0x4f31, 0x4f32}, four);
        sc.group(p, K_SDVC4, st(x, "sys_vol_speed_dependent_volume_control", "Speed Dependent Volume Control"),
                new int[] {0x4f39, 0x4f3a, 0x4f3b, 0x4f3c}, four);
        sc.check(p, K_SDVC2, st(x, "sys_vol_speed_dependent_volume_control", "Speed Dependent Volume Control"));
    }

    /** Guidance rows through smart control: the volume levels, voice type, charging prompts, briefing, welcome sound. */
    private static void guidance(Page p, final Context x, final Scontrol sc) {
        sc.attach(p.host());
        boolean vols = false;
        for (int k : VOLUMES) if (sc.has(k)) vols = true;
        if (vols) p.drill(p.word(1039, "Guidance Volumes"), null, new Fn.Build() {
            @Override public void build(Page q) {
                sc.attach(q.host());
                // Levels are set silently (the stock screen also plays a sample sound; that needs the stock audio focus path).
                sc.level(q, V_NAVI, st(x, "sound_setting_volume_ratio_item_navigation_guidance", "Navigation Guidance"), 0, 45);
                sc.level(q, V_NAVI_FX, st(x, "sound_setting_volume_ratio_item_navigation_sound_effect", "Navigation Alerts"), 0, 45);
                sc.level(q, V_VR, st(x, "sound_setting_volume_ratio_item_vr_prompt", "Voice Recognition Prompts"), 0, 45);
                sc.level(q, V_PROMPTS, st(x, "sound_setting_volume_ratio_general_system_prompt", "Voice Prompts"), 0, 45);
                sc.level(q, V_RINGTONE, st(x, "sound_setting_volume_ratio_item_ringtone", "Ringtone"), 0, 45);
                sc.level(q, V_ALERTS, st(x, "sound_setting_volume_ratio_item_alerts", "Alerts"), 0, 45);
                // Cluster Voice Prompts: the stock app refuses the change with the ignition off (result 7).
                sc.level(q, V_CLUSTER, st(x, "sound_setting_volume_ratio_item_cluster_voice", "Cluster Voice Prompts"), 0, 45);
                sc.level(q, V_BEEP, st(x, "common_beep", "Beep"), 0, 45);
            }
        });
        sc.group(p, K_VOICE_TYPE, st(x, "voice_type_setting", "Voice Type"), new int[] {0x5079, 0x507a},
                new String[] {st(x, "voice_type_setting_two", "Voice Type 2"), st(x, "voice_type_setting_four", "Voice Type 4")});
        sc.group(p, K_CVP, "Charging Voice Prompts", new int[] {0x508d, 0x508e, 0x508f, 0x5090},
                new String[] {st(x, "common_high", "High"), st(x, "common_mid", "Medium"), st(x, "common_low", "Low"), st(x, "common_off", "Off")});
        sc.check(p, K_WEATHER, st(x, "drv_guide_weather_info_brief", "Weather Information Briefing"));
        sc.check(p, K_WELCOME, st(x, "drv_guide_cluster_welcome_sound", "Welcome Sound"));
    }

    /** The tabs that are smart-control only, then Connected Devices (note) and Reset. */
    private static void smartTabs(Category c, final Context x, final Scontrol sc, final Audio a, boolean tone, boolean position) {
        if (a == null && (any(sc, PREMIUM))) c.tab(c.word(1023, "Premium Sound"), new Fn.Build() {
            @Override public void build(Page p) { premium(p, x, sc); }
        });
        if (a == null && (any(sc, VOLUMES) || sc.has(K_VOICE_TYPE) || sc.has(K_CVP))) c.tab(c.word(1038, "Guidance"), new Fn.Build() {
            @Override public void build(Page p) { guidance(p, x, sc); }
        });
        if (any(sc, ACTIVE)) c.tab(c.word(1056, st(x, "soundsetting_active_sound", "Active Sound Design")), new Fn.Build() {
            @Override public void build(Page p) {
                sc.attach(p.host());
                String[] three = {st(x, "common_largest", "Largest Change"), st(x, "common_moderate", "Moderate Change"), st(x, "common_smallest", "Smallest Change")};
                sc.group(p, K_ASD, st(x, "active_sound_design_title", "Active Sound Design"), new int[] {0x5141, 0x5142, 0x5143, 0x5144},
                        new String[] {three[0], three[1], three[2], st(x, "common_off", "Off")});
                sc.group(p, K_VESS, st(x, "virtual_engine_sound_title", "Virtual Engine Sound System"), new int[] {0x5191, 0x5192, 0x5193}, three);
                sc.group(p, K_EV_ASD, st(x, "active_sound_design_title", "Active Sound Design"), new int[] {0x5169, 0x516a, 0x516b, 0x516c},
                        new String[] {st(x, "ev_asd_sound_mode_futuristic", "Futuristic"), st(x, "ev_asd_sound_mode_s_engine", "S-Engine"),
                                st(x, "ev_asd_sound_mode_e_motor", "E-Motor"), st(x, "ev_asd_sound_mode_custom", "Custom Sound")});
                sc.group(p, K_APS, st(x, "ev_asd_aps_title", "Acceleration Pedal Response"), new int[] {0x5187, 0x5188, 0x5189},
                        new String[] {st(x, "ev_asd_aps_slow", "Slow"), st(x, "ev_asd_aps_normal", "Normal"), st(x, "ev_asd_aps_fast", "Fast")});
                sc.group(p, K_ESE, st(x, "ese_title", "Advanced Settings"), new int[] {0x514b, 0x514c, 0x514d, 0x514e, 0x514f},
                        new String[] {"Personal", "Sporty", "High Performance", "TCR", "Rally"});
                sc.check(p, K_ANC, st(x, "active_noise_control_title", "Active Noise Control"));
            }
        });
        if (sc.has(K_RNC)) c.tab(c.word(1087, st(x, "soundsetting_radio_nosise_ctrl", "Radio Noise")), new Fn.Build() {
            @Override public void build(Page p) {
                sc.attach(p.host());
                sc.radios(p, K_RNC, new int[] {0x5209, 0x520a, 0x520b},
                        new String[] {st(x, "sound_rnc_original_sound", "Original Sound"),
                                st(x, "sound_rnc_min_noise_reduction", "Minimum Noise Reduction"),
                                st(x, "sound_rnc_max_noise_reduction", "Max. Noise Reduction")});
            }
        });
        // Driver Assistance warning sounds: safety related. Same stock write (CcVRcontrol.setDrvAsisstance), only on a tap.
        if (any(sc, DA)) c.tab(c.word(1139, st(x, "soundsetting_drv_assist_warning", "Driver Assistance")), new Fn.Build() {
            @Override public void build(Page p) {
                sc.attach(p.host());
                p.guide("set_bg_sound_driver_assistance_warning");
                sc.group(p, K_DA_WARN, st(x, "drv_assist_warning_notifications", "Warning Volume"), new int[] {0x526d, 0x526e, 0x526f, 0x5270},
                        new String[] {st(x, "common_high", "High"), st(x, "common_mid", "Medium"), st(x, "common_low", "Low"), st(x, "common_off", "Off")});
                sc.check(p, K_DA_DRIVE_PRIO, st(x, "drv_guide_driving_safety_priority", "Driving Safety Priority"));
                sc.check(p, K_DA_PARK_PRIO, st(x, "drv_guide_parking_safety_priority", "Parking Safety Priority"));
            }
        });
        if (a != null) c.tab(st(x, "common_reset", "Reset"), new Fn.Build() {
            @Override public void build(Page p) {
                p.button("Reset Tone", new Fn.Act() { @Override public void run(final Host h) {
                    Ask.confirm(h.activity(), "Reset Tone", "Bass, midrange and treble return to 0.", "Reset", new Runnable() {
                        @Override public void run() { a.tone(10, 10, 10); h.refresh(); } });
                } });
                p.button("Reset Position", new Fn.Act() { @Override public void run(final Host h) {
                    Ask.confirm(h.activity(), "Reset Position", "Fader and balance return to the centre.", "Reset", new Runnable() {
                        @Override public void run() { a.fadeBalance(10, 10); h.refresh(); } });
                } });
            }
        });
    }

    private static String muteKey(Context x) {
        int t = 0;
        try {
            Class<?> k = Class.forName("com.hkmc.userprofile.UserProfileManager");
            Object u = k.getConstructor(Context.class).newInstance(x.getApplicationContext());
            t = (Integer) k.getMethod("getUserType").invoke(u);
        } catch (Throwable ignored) { }
        return "sound_mute_nav_guide" + (t == 1 ? "_dr1" : t == 2 ? "_dr2" : "");
    }

    // ---- stock feature checks (com.android.soundsettings.util.Utils), replicated with the same system calls ----

    private static int prop(String key, int def) {
        if (dev.ccnc.settings.api.Demo.on()) return Car.propInt(key, def);
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            return (Integer) sp.getMethod("getInt", String.class, int.class).invoke(null, key, def);
        } catch (Throwable t) { return def; }
    }

    private static String propStr(String key) {
        if (dev.ccnc.settings.api.Demo.on()) return Car.prop(key);
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            Object v = sp.getMethod("get", String.class).invoke(null, key);
            return v == null ? "" : (String) v;
        } catch (Throwable t) { return ""; }
    }

    /** Utils.isSupportMinimumStep(step): ro.product.opt.stepofspec >= step. */
    private static boolean step(int s) { return s <= prop("ro.product.opt.stepofspec", 0); }

    /** Utils.isBus(): model "gz" (CS/CY bus variants are commercial-only builds). */
    private static boolean isBus() { return propStr("ro.product.model").toLowerCase().contains("gz"); }

    /** Utils.isSupportSoundPosition(): not a bus; changed-sound (step 10) builds need KR20y1st (step 15). */
    private static boolean supportSoundPosition() {
        if (isBus()) return false;
        if (!step(10)) return true;
        return step(15);
    }

    /** SoundSettingApp: Feature.hasFeature("com.mobis.software.navigation") || hasFeature("com.hkmc.software.baidu"). */
    private static boolean supportNavigation() {
        return hasFeature("com.mobis.software.navigation") || hasFeature("com.hkmc.software.baidu");
    }

    private static boolean hasFeature(String f) {
        if (dev.ccnc.settings.api.Demo.on()) return Car.feature(f);
        try {
            Class<?> k = Class.forName("com.hkmc.Feature");
            return Boolean.TRUE.equals(k.getMethod("hasFeature", String.class).invoke(null, f));
        } catch (Throwable t) { return false; }
    }

    /** com.hkmc.media.MicomAudioManager by reflection (bootclasspath automotive.jar). */
    private static final class Audio {
        private static Audio sInstance;
        private final Object m;

        private Audio(Object m) { this.m = m; }

        static synchronized Audio get(Context c) {
            if (sInstance != null) return sInstance;
            if (dev.ccnc.settings.api.Demo.on()) { sInstance = new Audio(dev.ccnc.settings.api.Demo.manager("com.hkmc.media.MicomAudioManager")); return sInstance; }
            try {
                Class<?> k = Class.forName("com.hkmc.media.MicomAudioManager");
                Object o = k.getConstructor(Context.class).newInstance(c.getApplicationContext());
                sInstance = new Audio(o);
            } catch (Throwable t) { return null; }
            return sInstance;
        }

        Object call(String name, Class<?>[] types, Object... args) {
            if (m instanceof dev.ccnc.settings.api.Demo.Obj) return ((dev.ccnc.settings.api.Demo.Obj) m).call(name, args);
            try {
                Method mt = m.getClass().getMethod(name, types);
                return mt.invoke(m, args);
            } catch (Throwable t) { return null; }
        }

        int setting(int type) {
            Object v = call("getSoundSetting", new Class[]{int.class}, type);
            return v instanceof Integer ? (Integer) v : -1;
        }

        int integer(String getter) {
            Object v = call(getter, new Class[0]);
            return v instanceof Integer ? (Integer) v : -1;
        }

        int integer(String getter, int arg) {
            Object v = call(getter, new Class[]{int.class}, arg);
            return v instanceof Integer ? (Integer) v : -1;
        }

        Fn.Bool flag(final int type) {
            return new Fn.Bool() { @Override public boolean get() { return setting(type) == 1; } };
        }

        Fn.SetBool setFlag(final int type) {
            return new Fn.SetBool() { @Override public void set(boolean on) { call("setSound", new Class[]{int.class, int.class}, type, on ? 1 : 0); } };
        }

        boolean bool(String getter) { return Boolean.TRUE.equals(call(getter, new Class[0])); }

        void tone(int bass, int mid, int treble) {
            if (bass < 0 || mid < 0 || treble < 0) return;
            stepTo(new int[]{BASS, MIDDLE, TREBLE}, new int[]{clamp(bass), clamp(mid), clamp(treble)});
        }

        void fadeBalance(int fade, int balance) {
            if (fade < 0 || balance < 0) return;
            stepTo(new int[]{FADE, BALANCE}, new int[]{clamp(fade), clamp(balance)});
        }

        /**
         * The car's audio controller takes tone and position changes one level at a time: the stock sound screen's
         * slider sends every level as it is dragged, and a jump sent in one go (+3 to +10 on release) was not kept.
         * So the change goes out one level per call, 60 ms apart, on a worker thread; a newer target replaces an
         * older one that hasn't finished.
         */
        private final Object stepLock = new Object();
        private int[] stepTypes, stepTarget;
        private Thread stepper;

        private void stepTo(int[] types, int[] target) {
            synchronized (stepLock) {
                stepTypes = types;
                stepTarget = target;
                if (stepper != null) return;   // the running worker picks up the new target
                stepper = new Thread(new Runnable() { @Override public void run() { stepLoop(); } }, "ccncSoundStep");
                stepper.start();
            }
        }

        private void stepLoop() {
            for (int guard = 0; guard < 80; guard++) {
                int[] types, target;
                synchronized (stepLock) { types = stepTypes; target = stepTarget; }
                int[] now = new int[types.length];
                boolean done = true;
                for (int i = 0; i < types.length; i++) {
                    int cur = setting(types[i]);
                    if (cur < 0) { synchronized (stepLock) { stepper = null; } return; }
                    now[i] = cur == target[i] ? cur : cur + (target[i] > cur ? 1 : -1);
                    if (now[i] != cur) done = false;
                }
                if (done) {
                    synchronized (stepLock) { if (stepTarget == target) { stepper = null; return; } }
                    continue;   // a newer target came in meanwhile
                }
                if (types.length == 3) call("setBassMidTreb", new Class[]{int.class, int.class, int.class}, now[0], now[1], now[2]);
                else call("setFadeBalance", new Class[]{int.class, int.class}, now[0], now[1]);
                try { Thread.sleep(60); } catch (InterruptedException e) { break; }
            }
            synchronized (stepLock) { stepper = null; }
        }

        private static int clamp(int v) { return Math.max(0, Math.min(20, v)); }
    }
}
