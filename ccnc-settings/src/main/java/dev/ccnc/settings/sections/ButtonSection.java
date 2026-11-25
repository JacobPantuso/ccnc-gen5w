package dev.ccnc.settings.sections;

import android.content.Context;
import android.content.Intent;
import android.util.Log;


import dev.ccnc.settings.api.Category;
import dev.ccnc.settings.api.Fn;
import dev.ccnc.settings.api.Page;
import dev.ccnc.settings.api.Section;

/**
 * Button (stock classId com.android.ButtonSettings, app com.android.buttonsettings).
 *
 * Menus and their order are the stock ButtonSettings.onCreate() ones, each gated by the stock app's own static check
 * (com.android.buttonsettings.util.Utils, called through the stock class loader):
 *   usedNButton(ctx)            Left / Right N Button (Steering Wheel)   -> stock tab "N-Left" / "N-Right"
 *   isSupportTuneKnobSettings() TUNE Knob Function                      -> native (TuneKnobFunction$Interface get/set)
 *   usedCustomButton()          Custom Button ☆ (...)                    -> stock tab "CustomButton"
 *   hasFeatureSWRC_CustomKey()  Custom Button ★ (Steering Wheel)         -> stock tab "CustomButtonSWRC"
 *   usedModeButton()            MODE Button                              -> stock tab "SteeringModeButton"
 *   usedSwrcUpDnBtn()           [∧]/[∨] Buttons (Steering Wheel)         -> native (SwrcUnDnBtnActivity$SwrcUpDnBtn)
 *   checkedRRCMenu()            Lock Rear Controls                       -> stock tab "LockRear"
 *   usedOneKnobSwap()           VOL Knob                                 -> native (KeySwapActivity$KeySwap)
 *   else usedKeySwap()          VOL/TUNE Knob                            -> stock tab "KeySwap"
 *   isSupportClimateHaptic(ctx) Climate Touchscreen: Haptic Intensity    -> not mirrored (see build), stock entry
 * "stock tab" entries open the stock screen on that tab (extra target_tab_id): their option lists depend on many
 * stock checks and are written through the stock ModeManager/VCRM paths, so they stay in the stock app.
 * Native rows read/write through the stock app's own static Interface helpers (Settings.System *_dr1/_dr2/_g keys
 * per user profile). Writes: not tested on car.
 */
public final class ButtonSection extends Section {
    private static final String PKG = "com.android.buttonsettings";
    private static final String P = PKG + ".";
    private static final String U = P + "util.Utils";

    @Override public String classId() { return "com.android.ButtonSettings"; }
    @Override public int treeNode() { return 1006; }
    @Override public String icon() { return "set_ic_menu_button"; }

    @Override public void build(Category c) {
        final ButtonSectionStock s = ButtonSectionStock.open(c.context(), PKG, P + "ButtonSettingsApp", "mInstance");
        if (s == null) return; // no stock app: the card opens the stock screen (framework)
        Context x = c.context();

        if (s.isCtx(U, "usedNButton")) {
            stockTab(c, x, c.word(5072, s.str("n_left")), "N-Left");
            stockTab(c, x, c.word(5081, s.str("n_right")), "N-Right");
        }
        if (s.is(U, "isSupportTuneKnobSettings")) tuneKnob(c, s);
        boolean swrcKey = s.is(U, "hasFeatureSWRC_CustomKey");
        if (s.is(U, "usedCustomButton")) {
            String t;
            if (swrcKey) t = s.is(U, "usedMyData") ? c.word(5001, null) : c.word(5093, null);
            else t = s.str("custom_button_setting");
            customTab(c, s, t, "persist.sys.key.custom");
        }
        if (swrcKey) customTab(c, s, c.word(5031, null), "persist.sys.key.swrccustom");
        if (s.is(U, "usedModeButton")) stockTab(c, x, c.word(5049, s.str("steering_mode_button")), "SteeringModeButton");
        if (s.is(U, "usedSwrcUpDnBtn")) swrcUpDn(c, s);
        if (s.is(U, "checkedRRCMenu")) stockTab(c, x, c.word(5066, s.str("rear_lock_title")), "LockRear");
        if (s.is(U, "usedOneKnobSwap")) volKnob(c, s);
        else if (s.is(U, "usedKeySwap")) keySwap(c, s);
        // Climate Touchscreen: Haptic Intensity (stock check Utils.isSupportClimateHaptic(ctx)) is not checked here: that
        // check registers a DATC vehicle listener whose stock callbacks would then run in our process. Stays in the stock entry.
    }

    /** Left entry opening the stock ButtonSettings on one of its tabs. */
    private static void stockTab(Category c, Context x, String title, String tabId) {
        if (title == null) return;
        c.tab(title, component(PKG, ".ButtonSettings").putExtra("target_tab_id", tabId));
    }

    /** Stock CustomItem actions (CustomButtonSetting/CustomButtonSettingSWRC get*Item()) -> their stock label names. */
    private static final String[][] ACTIONS = {
        {"gohome", "custom_button_setting_home"},
        {"com.hkmc.intent.action.DISP_SHORTCUT", "custom_button_setting_displayoff"},
        {"com.hkmc.intent.action.REARQUIET_SHORTCUT", "custom_button_setting_quiet"},
        {"com.mobis.intent.action.EV_SHORTCUT", "custom_button_setting_ev"},
        {"com.mobis.intent.action.HD_DATA_SHORTCUT", "custom_button_setting_hd_data"},
        {"com.hkmc.intent.action.BLUELINK_SHORTCUT", "custom_button_setting_bluelink"},
        {"com.mobis.intent.action.BTAUDIOKEY_SHORTCUT", "custom_button_setting_bluetoothaudio"},
        {"com.mobis.intent.action.CALENDAR_SHORTCUT", "custom_button_setting_calendar"},
        {"com.mobis.intent.action.CHANGE_HANDSFREE_DEVICE_SHORTCUT", "custom_button_swrc_change_device"},
        {"com.mobis.intent.action.SXM_DATA_SHORTCUT", "custom_button_setting_data_service"},
        {"com.hkmc.intent.action.DMB_SHORTCUT", "custom_button_setting_dmb"},
        {"com.mobis.intent.action.DRIVER_ASSISTANCE_SHORTCUT", "custom_button_setting_driver_assistance"},
        {"com.hkmc.intent.action.DRVM_SHORTCUT", "custom_button_setting_drvm"},
        {"com.hkmc.intent.action.BTCALLEND_SHORTCUT", "custom_button_swrc_endcall"},
        {"com.hkmc.intent.action.HOTSPOT_SHORTCUT", "custom_button_setting_wifi"},
        {"com.hkmc.intent.action.UVO_SHORTCUT", "custom_button_setting_uvo"},
        {"com.mobis.intent.action.LOCATIONS_SHARING_SHORTCUT", "custom_button_setting_location_sharing"},
        {"com.hkmc.intent.action.MAP_SHORTCUT", "custom_button_swrc_map"},
        {"com.hkmc.intent.action.MYMENU_SHORTCUT", "custom_button_setting_mymenu"},
        {"com.mobis.intent.action.NMODE", "custom_button_setting_nmode"},
        {"com.hkmc.intent.action.MEDIA_NATURESOUND_SHORTCUT", "steering_mode_button_sound_nature"},
        {"com.mobis.intent.action.CANCEL_ROUTE_SHORTCUT", "custom_button_swrc_nav_cancel_route"},
        {"com.mobis.intent.action.REROUTE_SHORTCUT", "custom_button_swrc_nav_reroute"},
        {"com.mobis.intent.action.PERFORMANCE_GAUGE_SHORTCUT", "custom_button_setting_pgauge"},
        {"com.hkmc.intent.action.REARTALK_SHORTCUT", "custom_button_setting_talk"},
        {"com.mobis.intent.action.DRIVERWATCH_SHORTCUT", "custom_button_setting_passenger_view"},
        {"com.hkmc.intent.action.PHONE_SHORTCUT", "custom_button_setting_phone"},
        {"com.hkmc.intent.action.RADIO_SHORTCUT", "custom_button_setting_radio"},
        {"com.mobis.intent.action.CONNECTIVITY_SHORTCUT", "phone_projection"},
        {"com.mobis.intent.action.VOICE_MEMO_SHORTCUT", "custom_button_swrc_voice_memo_start_stop"},
        {"com.mobis.intent.action.WEATHER_SHORTCUT", "custom_button_setting_weather"},
        {"com.hkmc.intent.action.QIBLA_SHORTCUT", "custom_button_setting_qibla"},
        {"com.mobis.intent.action.MOOD_LAMP_SHORTCUT", "custom_button_setting_sml"},
        {"com.hkmc.intent.action.SETUP_SHORTCUT", "custom_button_setting_setup"},
        {"com.mobis.intent.action.SMARTPURE_AIR_SHORTCUT", "custom_button_setting_pure_air"},
        {"com.mobis.intent.action.SPORTS_SHORTCUT", "custom_button_setting_sports"},
        {"com.mobis.intent.action.UTILITY_MODE_SHORTCUT", "custom_button_setting_utility_mode"},
        {"com.mobis.intent.action.PRIVACY_MODE_SHORTCUT", "custom_button_swrc_privacy_mode"},
        {"com.mobis.intent.action.CUSTOM_SHORTCUT", "custom_button_setting_none"},
    };

    /**
     * Custom Button ☆ / ★: the current function (stock key property persist.sys.key.custom / .swrccustom, written by
     * the stock app through com.hkmc.mode.ModeManager), shown as the stock label; tapping opens the stock list
     * (its option set depends on dozens of stock checks, so choosing stays in the stock app).
     */
    private static void customTab(Category c, final ButtonSectionStock s, final String title, final String prop) {
        if (title == null) return;
        c.tab(title, new Fn.Build() {
            @Override public void build(Page p) {
                p.value(title, new Fn.Str() {
                    @Override public String get() {
                        String a = ButtonSectionStock.prop(prop);
                        for (String[] e : ACTIONS) if (e[0].equals(a)) { String l = s.str(e[1]); return l == null ? "" : l; }
                        return "";
                    }
                }, new Fn.Act() { @Override public void run(dev.ccnc.settings.api.Host h) { h.openStock(); } });
            }
        });
    }

    /** VOL/TUNE Knob: stock KeySwap 0 = normal, 1 = swapped ("Swap"); set() also asks ModeManager.reqKnobKeySwap. */
    private static void keySwap(Category c, final ButtonSectionStock s) {
        final String title = c.word(5068, s.str("vol_tuneknob_tab"));
        final String swap = s.str("vol_tuneknob_swap");
        if (title == null || swap == null) return;
        final String cls = P + "KeySwapActivity$KeySwap";
        c.tab(title, new Fn.Build() {
            @Override public void build(Page p) {
                p.toggle(swap, new Fn.Bool() {
                    @Override public boolean get() { return s.getInt(cls, "get", 0) == 1; }
                }, new Fn.SetBool() {
                    @Override public void set(boolean on) { s.setInt(cls, "set", on ? 1 : 0); }
                });
            }
        });
    }

    /** TUNE Knob Function: stock TuneKnobFunction$TUNE_KNOB values (AUDIO_SYSTEM, MAP) with their own label ids. */
    private static void tuneKnob(Category c, final ButtonSectionStock s) {
        try {
            final Class<?> itf = s.cl.loadClass(P + "TuneKnobFunction$Interface");
            final Class<?> en = s.cl.loadClass(P + "TuneKnobFunction$TUNE_KNOB");
            final Object[] vals = en.getEnumConstants();
            final String[] labels = new String[vals.length];
            for (int i = 0; i < vals.length; i++) {
                labels[i] = s.str(en.getField("rscID").getInt(vals[i]));
                if (labels[i] == null) return;
            }
            String title = s.str("tune_knob_setting");
            if (title == null || vals.length == 0) return;
            c.tab(title, new Fn.Build() {
                @Override public void build(Page p) {
                    p.radios(labels, new Fn.Int() {
                        @Override public int get() {
                            try {
                                Object cur = itf.getMethod("get", Context.class).invoke(null, s.sc);
                                for (int i = 0; i < vals.length; i++) if (vals[i] == cur) return i;
                            } catch (Throwable t) { Log.w("ccnc.Button", "tune get", t); }
                            return -1;
                        }
                    }, new Fn.SetInt() {
                        @Override public void set(int v) {
                            try { itf.getMethod("set", Context.class, en).invoke(null, s.sc, vals[v]); }
                            catch (Throwable t) { Log.w("ccnc.Button", "tune set", t); }
                        }
                    });
                }
            });
        } catch (Throwable t) { Log.w("ccnc.Button", "tune knob", t); }
    }

    /** [∧]/[∨] Buttons: stock SwrcUpDnBtn 0 = Change preset station, 1 = Change station / frequency. */
    private static void swrcUpDn(Category c, ButtonSectionStock s) {
        String a = s.str("swrc_updn_preset"), b = s.str("swrc_updn_frequency");
        if (a == null || b == null) return;
        radioTab(c, s, c.word(5090, s.str("swrc_updn_tab")), new String[] {a, b}, P + "SwrcUnDnBtnActivity$SwrcUpDnBtn");
    }

    /** VOL Knob (one-knob units): stock KeySwap 0 = Volume, Power; 1 = Browse Media, Map Zoom In/Out. */
    private static void volKnob(Category c, ButtonSectionStock s) {
        String a = s.str("vol_knob_volume_power");
        String b = s.is(U, "isSupportNavigationAll") ? s.str("vol_knob_browse_media_map") : s.str("vol_tuneknob_browse_media");
        if (a == null || b == null) return;
        radioTab(c, s, c.word(5069, s.str("vol_knob_tab")), new String[] {a, b}, P + "KeySwapActivity$KeySwap");
    }

    private static void radioTab(Category c, final ButtonSectionStock s, String title, final String[] labels, final String cls) {
        if (title == null) return;
        c.tab(title, new Fn.Build() {
            @Override public void build(Page p) {
                p.radios(labels, new Fn.Int() {
                    @Override public int get() { return s.getInt(cls, "get", -1); }
                }, new Fn.SetInt() {
                    @Override public void set(int v) { s.setInt(cls, "set", v); }
                });
            }
        });
    }

    @Override public Intent stockIntent(Context c) { return component(PKG, ".ButtonSettings"); }
}
