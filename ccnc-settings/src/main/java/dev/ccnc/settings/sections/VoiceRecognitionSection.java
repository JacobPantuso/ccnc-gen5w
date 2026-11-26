package dev.ccnc.settings.sections;

import android.content.Context;
import android.content.ContextWrapper;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import dev.ccnc.settings.api.Category;
import dev.ccnc.settings.api.Fn;
import dev.ccnc.settings.api.Page;
import dev.ccnc.settings.api.Section;

/**
 * Voice Recognition (stock classId com.android.settings.voice). Mirrors the stock Settings_W VoiceMainSettings:
 * its tabs come from the stock app's own static checks (VoiceOptionsSetting.isShowingVoiceOptions(), Utils.isIndia(),
 * Utils.checkCarModel()), called through the stock class loader, and every read/write goes through the stock
 * VoiceXxxSetting$Interface.get/set(user, ctx[, value]) for the current profile (Wrapper$UserProfileManager.getUserType()),
 * which also sends the stock change broadcasts. Link Vehicle Features / Rear Seat follow the stock
 * Utils.checkMoodLampVROptions() / checkRearVROptions(). Use Network (Singapore), Kakao Friends (Korea) and the India
 * prompt list are not ported (other regions' builds). No stock screen is opened.
 */
public final class VoiceRecognitionSection extends Section {
    @Override public String classId() { return "com.android.settings.voice"; }
    @Override public int treeNode() { return 1010; }
    @Override public String icon() { return "set_ic_menu_voicerecognition"; }

    private static final String TAG = "ccncSetup";
    private static final String STOCK = "com.android.settings";
    private static final String V = "com.android.settings.voice.";
    private static final String U = "com.android.settings.util.Utils";

    @Override public void build(Category c) {
        final Context x = c.context();
        final Context sc = stock(x);
        if (sc == null) return;  // stock Setup app not readable (emulator): "Not available on this unit."

        // Audible Prompts (VoiceGuidanceSetting): Beginner = 0, Normal = 1, Expert = 2 (stock list order and values).
        // India (other than ky1) builds a different list in the stock screen: left to the stock entry there.
        boolean india = bool(sc, U, "isIndia");
        boolean ky1 = Boolean.TRUE.equals(call(sc, "com.android.settings.util.Utils", "checkCarModel",
                new Class<?>[]{String.class}, "ky1"));
        if (!india || ky1) {
            final String[] labels = {str(sc, "voicesetting_info_title_more"), str(sc, "voicesetting_info_title_simple"),
                    str(sc, "voicesetting_info_title_noting")};
            if (labels[0] != null && labels[1] != null && labels[2] != null)
                c.tab(c.word(9002, str(sc, "voice_tab_guidance")), new Fn.Build() {
                    @Override public void build(Page p) {
                        p.radios(labels,
                                new Fn.Int() { @Override public int get() { return read(sc, x, "VoiceGuidanceSetting$Interface", "get"); } },
                                new Fn.SetInt() { @Override public void set(int v) { write(sc, x, "VoiceGuidanceSetting$Interface", "set", v); } });
                    }
                });
        }

        // Voice Options (VoiceOptionsSetting): shown by the stock screen only when isShowingVoiceOptions().
        if (bool(sc, V + "VoiceOptionsSetting", "isShowingVoiceOptions")) {
            c.tab(str(sc, "voice_tab_vr_options"), new Fn.Build() {
                @Override public void build(Page p) {
                    p.toggle(p.word(9014, str(sc, "voice_vr_voice_wake_up")),
                            new Fn.Bool() { @Override public boolean get() { return read(sc, x, "VoiceOptionsSetting$Interface", "getWakeUp") == 1; } },
                            new Fn.SetBool() { @Override public void set(boolean on) { write(sc, x, "VoiceOptionsSetting$Interface", "setWakeUp", on ? 1 : 0); } });
                    p.toggle(str(sc, "voice_vr_voice_interruption"),
                            new Fn.Bool() { @Override public boolean get() { return read(sc, x, "VoiceOptionsSetting$Interface", "getBargeIn") == 1; } },
                            new Fn.SetBool() { @Override public void set(boolean on) { write(sc, x, "VoiceOptionsSetting$Interface", "setBargeIn", on ? 1 : 0); } });
                }
            });
        }

        // Link Vehicle Features (VoiceVehicleControl): shown when the stock Utils.checkMoodLampVROptions() is true
        // (USM getLights_AmbientAIVoice() is 1 = off or 2 = on). Mood Lighting check box: USM 2 = on, 1 = off.
        if (bool(sc, U, "checkMoodLampVROptions")) {
            c.tab(str(sc, "voice_tab_link_vehicle_control_vr"), new Fn.Build() {
                @Override public void build(Page p) {
                    p.toggle(str(sc, "voicesetting_info_title_vehicle_control"),
                            new Fn.Bool() { @Override public boolean get() { return usm(x, "getLights_AmbientAIVoice", -1) == 2; } },
                            new Fn.SetBool() { @Override public void set(boolean on) { usm(x, "setLights_AmbientAIVoice", on ? 2 : 1); } });
                }
            });
        }

        // Rear Seat (VoiceRearVRSetting): shown when the stock Utils.checkRearVROptions() is true (rear mic available).
        if (bool(sc, U, "checkRearVROptions")) {
            c.tab(c.word(9017, str(sc, "voice_tab_rear_vr")), new Fn.Build() {
                @Override public void build(Page p) {
                    p.toggle(p.word(9018, str(sc, "voicesetting_info_title_lock_rear")),
                            new Fn.Bool() { @Override public boolean get() { return read(sc, x, "VoiceRearVRSetting$Interface", "get") == 1; } },
                            new Fn.SetBool() { @Override public void set(boolean on) { write(sc, x, "VoiceRearVRSetting$Interface", "set", on ? 1 : 0); } });
                }
            });
        }

        // Use Network (Utils.isSGP()) and Kakao Friends (Utils.checkServerVROptions(), Korea) are not built here:
        // only then is something left for the stock screen.
        // (Singapore / Korea / India builds only; none of them applies to this car. They are not ported.)
        leftover = bool(sc, U, "isSGP") || bool(sc, U, "checkServerVROptions") || (india && !ky1);
        Log.i(TAG, "voice: india=" + india + " ky1=" + ky1 + " options=" + bool(sc, V + "VoiceOptionsSetting", "isShowingVoiceOptions") + " moodLamp=" + bool(sc, U, "checkMoodLampVROptions") + " rear=" + bool(sc, U, "checkRearVROptions") + " leftover=" + leftover);
    }

    private static boolean leftover = true;


    /** USMManager (automotive.jar) getter (arg < 0) or setter, as VoiceVehicleControl calls it. */
    static int usm(Context x, String m, int arg) {
        if (dev.ccnc.settings.api.Demo.on()) { // emulator demo
            if (arg < 0) return dev.ccnc.settings.api.Demo.getInt("USMManager#" + m.substring(3), -1);
            dev.ccnc.settings.api.Demo.put("USMManager#" + m.substring(3), arg);
            return arg;
        }
        try {
            Class<?> c = Class.forName("com.hkmc.usm.USMManager");
            Object u = c.getConstructor(Context.class).newInstance(x);
            if (arg < 0) return ((Number) c.getMethod(m).invoke(u)).intValue();
            c.getMethod(m, int.class).invoke(u, arg);
            return arg;
        } catch (Throwable t) {
            Log.w(TAG, "voice: USMManager." + m + " failed: " + t);
            return -1;
        }
    }

    // ---- stock Settings_W access (private; same approach as StockMenu) ----

    /** The stock Setup app's package context with its SettingsApp singleton given a context, or null. */
    static Context stock(Context c) {
        dev.ccnc.settings.api.Demo.init(c);
        if (dev.ccnc.settings.api.Demo.on()) return c; // emulator demo: call() / str() answer from Demo
        try {
            Context sc = c.createPackageContext(STOCK, Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
            Class<?> app = sc.getClassLoader().loadClass("com.android.settings.SettingsApp");
            Field inst = app.getDeclaredField("mInstance");
            inst.setAccessible(true);
            if (inst.get(null) == null) {
                Object a = app.newInstance();
                Method attach = ContextWrapper.class.getDeclaredMethod("attachBaseContext", Context.class);
                attach.setAccessible(true);
                attach.invoke(a, sc);
                inst.set(null, a);
            }
            return sc;
        } catch (Throwable t) {
            Log.w(TAG, "voice: stock Setup not readable", t);
            return null;
        }
    }

    static Object call(Context sc, String cls, String m, Class<?>[] types, Object... args) {
        if (dev.ccnc.settings.api.Demo.on()) { // emulator demo: static checks by method name; Interface get*/set* from the in-memory car
            if (!cls.endsWith("$Interface")) return Boolean.valueOf(dev.ccnc.settings.api.Demo.check(m));
            String key = "voice." + cls.substring(cls.lastIndexOf('.') + 1) + "." + m.substring(3);
            if (m.startsWith("set")) { dev.ccnc.settings.api.Demo.put(key, args[args.length - 1]); return null; }
            return Integer.valueOf(dev.ccnc.settings.api.Demo.getInt(key, 0));
        }
        try {
            Method mm = sc.getClassLoader().loadClass(cls).getMethod(m, types);
            return mm.invoke(null, args);
        } catch (Throwable t) {
            Log.w(TAG, "voice: " + cls + "." + m + " failed: " + t);
            return null;
        }
    }

    static boolean bool(Context sc, String cls, String m) {
        return Boolean.TRUE.equals(call(sc, cls, m, new Class<?>[0]));
    }

    static String str(Context sc, String name) {
        if (dev.ccnc.settings.api.Demo.on()) return dev.ccnc.settings.api.Demo.str(name);
        try {
            int id = sc.getResources().getIdentifier(name, "string", STOCK);
            return id == 0 ? null : sc.getResources().getString(id);
        } catch (Throwable t) { return null; }
    }

    /** Current profile as the stock screens read it: Wrapper$UserProfileManager.getInstance(ctx).getUserType(). */
    static int user(Context sc, Context x) {
        if (dev.ccnc.settings.api.Demo.on()) return 0;
        try {
            Class<?> w = sc.getClassLoader().loadClass("com.android.settings.util.Wrapper$UserProfileManager");
            Object m = w.getMethod("getInstance", Context.class).invoke(null, x);
            return ((Number) w.getMethod("getUserType").invoke(m)).intValue();
        } catch (Throwable t) {
            Log.w(TAG, "voice: user type unreadable: " + t);
            return 0;
        }
    }

    static int read(Context sc, Context x, String iface, String m) {
        Object r = call(sc, V + iface, m, new Class<?>[]{int.class, Context.class}, user(sc, x), x);
        return r instanceof Number ? ((Number) r).intValue() : -1;
    }

    static void write(Context sc, Context x, String iface, String m, int v) {
        call(sc, V + iface, m, new Class<?>[]{int.class, Context.class, int.class}, user(sc, x), x, v);
    }
}
