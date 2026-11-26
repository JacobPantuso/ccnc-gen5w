package dev.ccnc.settings.sections;

import android.content.Context;
import android.util.Log;


import dev.ccnc.settings.api.Category;
import dev.ccnc.settings.api.Fn;
import dev.ccnc.settings.api.Page;
import dev.ccnc.settings.api.Section;

/**
 * Button (stock classId com.android.ButtonSettings, app com.android.buttonsettings).
 *
 * Menus and their order are the stock ButtonSettings.onCreate() ones, each gated by the stock app's own static check
 * (com.android.buttonsettings.util.Utils, called through the stock class loader). All pages are ccNC pages:
 *   usedNButton(ctx)            Left / Right N Button (Steering Wheel)   -> NOT PORTED (note): the N-button function sets
 *                               (CustomButtonSettingN$NBtnCustom) are written through a private VCRM path, not decoded
 *   isSupportTuneKnobSettings() TUNE Knob Function                      -> native (TuneKnobFunction$Interface get/set)
 *   usedCustomButton()          Custom Button ☆                          -> native: ModeManager.setCustomKey(action), the
 *                               call the stock list makes (the framework service stores persist.sys.key.custom)
 *   hasFeatureSWRC_CustomKey()  Custom Button ★ (Steering Wheel)         -> native: ModeManager.setKey(5, action)
 *   usedModeButton()            MODE Button                              -> native: SteeringModeButtonSetting$MODE
 *                               .makeSupportedList() + $Interface.get/set(ctx, list) (ModeManager.setModeKeySetting)
 *   usedSwrcUpDnBtn()           [∧]/[∨] Buttons (Steering Wheel)         -> native (SwrcUnDnBtnActivity$SwrcUpDnBtn)
 *   checkedRRCMenu()            Lock Rear Controls                       -> native: ModeManager.rseLock(1 / 0) (RearLock)
 *   usedOneKnobSwap()           VOL Knob                                 -> native (KeySwapActivity$KeySwap)
 *   else usedKeySwap()          VOL/TUNE Knob                            -> native
 *   isSupportClimateHaptic(ctx) Climate Touchscreen: Haptic Intensity    -> NOT PORTED (the stock check registers a DATC
 *                               listener whose callbacks would run in our process); no tab
 * The Custom Button function lists are the stock functions whose label exists in this firmware, minus the ones that
 * need hardware this page cannot check (DMB, HD/SXM data, N mode, Qibla ...): choosing one only tells the framework
 * which shortcut broadcast the button sends. The stock app also logs the change to VCRM and up-syncs it to the user
 * profile; that part is not done here. Writes: not tested on car.
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
        if (s == null) return; // no stock app (emulator): the framework shows "Not available on this unit."
        final Context x = c.context();

        if (s.isCtx(U, "usedNButton")) {
            noteTab(c, c.word(5072, s.str("n_left")), "N button functions are not available in ccNC Setup yet.");
            noteTab(c, c.word(5081, s.str("n_right")), "N button functions are not available in ccNC Setup yet.");
        }
        if (s.is(U, "isSupportTuneKnobSettings")) tuneKnob(c, s);
        boolean swrcKey = s.is(U, "hasFeatureSWRC_CustomKey");
        if (s.is(U, "usedCustomButton")) {
            String t;
            if (swrcKey) t = s.is(U, "usedMyData") ? c.word(5001, null) : c.word(5093, null);
            else t = s.str("custom_button_setting");
            customTab(c, s, t, "persist.sys.key.custom", false);
        }
        if (swrcKey) customTab(c, s, c.word(5031, null), "persist.sys.key.swrccustom", true);
        if (s.is(U, "usedModeButton")) modeButton(c, s);
        if (s.is(U, "usedSwrcUpDnBtn")) swrcUpDn(c, s);
        if (s.is(U, "checkedRRCMenu")) rearLock(c, x, s);
        if (s.is(U, "usedOneKnobSwap")) volKnob(c, s);
        else if (s.is(U, "usedKeySwap")) keySwap(c, s);
        // Climate Touchscreen: Haptic Intensity (stock check Utils.isSupportClimateHaptic(ctx)) is not checked here: that
        // check registers a DATC vehicle listener whose stock callbacks would then run in our process. Not ported.
    }

    private static void noteTab(Category c, String title, final String note) {
        if (title == null) return;
        c.tab(title, new Fn.Build() { @Override public void build(Page p) { p.note(note); } });
    }

    private static Object modeManager(Context x) { return dev.ccnc.settings.api.Car.manager(x, "com.hkmc.mode.ModeManager"); }

    /** MODE Button: which media sources the steering-wheel MODE button steps through (at least one stays on). */
    private static void modeButton(Category c, final ButtonSectionStock s) {
        final String title = c.word(5049, s.str("steering_mode_button"));
        if (title == null) return;
        if (dev.ccnc.settings.api.Demo.on()) { // emulator demo: the stock MODE enum is not there; same page from the in-memory car
            final String[] modes = {"Radio", "Bluetooth Audio", "USB Music", "Phone Projection", "Sounds of Nature"};
            c.tab(title, new Fn.Build() {
                @Override public void build(Page p) {
                    for (final String m : modes) {
                        final String key = "btn.mode." + m;
                        p.toggle(m, new Fn.Bool() {
                            @Override public boolean get() { return dev.ccnc.settings.api.Demo.getBool(key, !m.startsWith("Sounds")); }
                        }, new Fn.SetBool() {
                            @Override public void set(boolean on) {
                                if (!on) { // one source must stay selected
                                    int n = 0;
                                    for (String o : modes) if (dev.ccnc.settings.api.Demo.getBool("btn.mode." + o, !o.startsWith("Sounds"))) n++;
                                    if (n <= 1) return;
                                }
                                dev.ccnc.settings.api.Demo.put(key, on);
                            }
                        });
                    }
                }
            });
            return;
        }
        try {
            final Class<?> mode = s.cl.loadClass(P + "SteeringModeButtonSetting$MODE");
            final Class<?> itf = s.cl.loadClass(P + "SteeringModeButtonSetting$Interface");
            final java.util.List<?> supported = (java.util.List<?>) mode.getMethod("makeSupportedList").invoke(null);
            if (supported == null || supported.isEmpty()) return;
            c.tab(title, new Fn.Build() {
                @Override public void build(final Page p) {
                    for (final Object m : supported) {
                        String label;
                        try { label = s.str(mode.getField("rscID").getInt(m)); } catch (Throwable t) { label = null; }
                        if (label == null) continue;
                        p.toggle(label, new Fn.Bool() {
                            @Override public boolean get() { return selected(itf, s).contains(m); }
                        }, new Fn.SetBool() {
                            @Override public void set(boolean on) {
                                java.util.List<Object> now = new java.util.ArrayList<Object>(selected(itf, s));
                                if (on) { if (!now.contains(m)) now.add(m); } else now.remove(m);
                                if (now.isEmpty()) return; // stock checkValidation: one source must stay selected
                                // keep the stock order (the order of the supported list)
                                java.util.List<Object> ordered = new java.util.ArrayList<Object>();
                                for (Object o : supported) if (now.contains(o)) ordered.add(o);
                                try { itf.getMethod("set", Context.class, java.util.List.class).invoke(null, s.sc, ordered); }
                                catch (Throwable t) { Log.w("ccnc.Button", "mode set", t); }
                            }
                        });
                    }
                }
            });
        } catch (Throwable t) { Log.w("ccnc.Button", "mode button", t); }
    }

    private static java.util.List<?> selected(Class<?> itf, ButtonSectionStock s) {
        try {
            java.util.List<?> l = (java.util.List<?>) itf.getMethod("get", Context.class).invoke(null, s.sc);
            if (l != null) return l;
        } catch (Throwable t) { Log.w("ccnc.Button", "mode get", t); }
        return java.util.Collections.emptyList();
    }

    /**
     * Lock Rear Controls: stock RearLock.setRSELock() -> ModeManager.rseLock(1 = lock, 0 = unlock). The current state
     * only arrives through a ModeKeyListener callback (reqRearLockStatus), so the page offers the two actions.
     */
    private static void rearLock(Category c, final Context x, ButtonSectionStock s) {
        String title = c.word(5066, s.str("rear_lock_title"));
        if (title == null) return;
        c.tab(title, new Fn.Build() {
            @Override public void build(Page p) {
                p.button("Lock Rear Controls", new Fn.Act() { @Override public void run(dev.ccnc.settings.api.Host h) {
                    dev.ccnc.settings.api.Car.setInt(modeManager(x), "rseLock", 1); } });
                p.button("Unlock Rear Controls", new Fn.Act() { @Override public void run(dev.ccnc.settings.api.Host h) {
                    dev.ccnc.settings.api.Car.setInt(modeManager(x), "rseLock", 0); } });
            }
        });
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

    /** Functions left out of the choice: they need hardware / region features this page cannot check. */
    private static final java.util.Set<String> SKIP = new java.util.HashSet<String>(java.util.Arrays.asList(
            "custom_button_setting_hd_data", "custom_button_setting_data_service", "custom_button_setting_dmb",
            "custom_button_setting_drvm", "custom_button_setting_uvo", "custom_button_setting_nmode",
            "custom_button_setting_pgauge", "custom_button_setting_talk", "custom_button_setting_quiet",
            "custom_button_setting_passenger_view", "custom_button_setting_qibla", "custom_button_setting_sml",
            "custom_button_setting_pure_air", "custom_button_setting_sports", "custom_button_setting_utility_mode",
            "custom_button_setting_mymenu", "custom_button_setting_calendar", "custom_button_setting_location_sharing",
            "steering_mode_button_sound_nature"));
    /** Steering-wheel (★) only functions; the ☆ list leaves them out, as the stock lists do. */
    private static final java.util.Set<String> SWRC_ONLY = new java.util.HashSet<String>(java.util.Arrays.asList(
            "custom_button_swrc_change_device", "custom_button_swrc_endcall", "custom_button_swrc_map",
            "custom_button_swrc_nav_cancel_route", "custom_button_swrc_nav_reroute",
            "custom_button_swrc_voice_memo_start_stop", "custom_button_swrc_privacy_mode"));

    /**
     * Custom Button ☆ / ★: the current function (stock key property persist.sys.key.custom / .swrccustom) as a choice.
     * Picking one calls com.hkmc.mode.ModeManager.setCustomKey(action) (☆) or setKey(5, action) (★): the calls the stock
     * CustomButtonSetting / CustomButtonSettingSWRC make through Wrapper$ModeManager.
     */
    private static void customTab(Category c, final ButtonSectionStock s, final String title, final String prop, final boolean swrc) {
        if (title == null) return;
        final Context x = c.context();
        final java.util.List<String> actions = new java.util.ArrayList<String>(), labels = new java.util.ArrayList<String>();
        boolean ev = dev.ccnc.settings.api.Car.pkg(x, "com.hkmc.daudio.app.ev3") != null;
        for (String[] e : ACTIONS) {
            if (SKIP.contains(e[1]) || !swrc && SWRC_ONLY.contains(e[1])) continue;
            if (e[1].equals("custom_button_setting_ev") && !ev) continue;
            if (e[1].equals("custom_button_setting_wifi") && !x.getPackageManager().hasSystemFeature("com.hkmc.software.wifihotspot")) continue;
            String l = s.str(e[1]);
            if (l == null || labels.contains(l)) continue;
            actions.add(e[0]); labels.add(l);
        }
        // The function set now, if it is one this list leaves out: shown so the page never misreports the button.
        String cur = ButtonSectionStock.prop(prop);
        if (cur.length() > 0 && !actions.contains(cur)) {
            for (String[] e : ACTIONS) if (e[0].equals(cur)) { String l = s.str(e[1]); if (l != null) { actions.add(0, cur); labels.add(0, l); } }
        }
        if (actions.isEmpty()) return;
        final String[] ls = labels.toArray(new String[0]);
        c.tab(title, new Fn.Build() {
            @Override public void build(final Page p) {
                p.radios(ls, new Fn.Int() {
                    @Override public int get() { return actions.indexOf(ButtonSectionStock.prop(prop)); }
                }, new Fn.SetInt() {
                    @Override public void set(int v) {
                        if (v < 0 || v >= actions.size()) return;
                        Object mm = modeManager(x);
                        if (swrc) dev.ccnc.settings.api.Car.call(mm, "setKey", new Class<?>[] { int.class, String.class }, 5, actions.get(v));
                        else dev.ccnc.settings.api.Car.call(mm, "setCustomKey", new Class<?>[] { String.class }, actions.get(v));
                        // the service writes the property asynchronously: read it back shortly after
                        new android.os.Handler().postDelayed(new Runnable() { @Override public void run() { p.host().refresh(); } }, 500);
                    }
                });
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
        if (dev.ccnc.settings.api.Demo.on()) { // emulator demo: the stock TUNE_KNOB enum is not there
            radioTab(c, s, s.str("tune_knob_setting"), new String[] {"Audio System", "Map Zoom In/Out"}, P + "TuneKnobFunction$Interface");
            return;
        }
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

}
