package dev.ccnc.settings.sections;

import android.content.Context;
import android.content.Intent;

import java.lang.reflect.Method;

import dev.ccnc.settings.api.Category;
import dev.ccnc.settings.api.Fn;
import dev.ccnc.settings.api.Page;
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
 * Premium Sound, the position diagram, Guidance Volumes and the remaining tabs stay in the stock entry.
 */
public final class SoundSection extends Section {
    @Override public String classId() { return "com.android.SoundSetting"; }
    @Override public int treeNode() { return 1002; }
    @Override public String icon() { return "set_ic_menu_sound"; }

    private static final int FADE = 5, BALANCE = 6, BASS = 8, MIDDLE = 9, TREBLE = 10;
    private static final int NAVI_FIRST = 1, SAME_MIX_AV_NAVI = 3, BOSE_DSC = 0x2f;

    @Override public void build(Category c) {
        final Context x = c.context();
        final Audio a = Audio.get(x);
        if (a == null) return; // no MicomAudioManager on this unit: stock screen opens directly

        // Premium Sound (PremiumSound.getList): Dynamic Speed Compensation when SoundAdvancedManager has BOSE_DSC
        // (getAmpType Bose + getSoundAvailability(BOSE_DSCMODE 0x2f) == 1; value getSoundSetting == 1, write setSound(0x2f, 1/0));
        // Start-up Volume Limit on KR20y6th builds (stepofspec >= 25): get/setAutoAdjustingVolumeSetting(boolean).
        final boolean dsc = a.integer("getSoundAvailability", BOSE_DSC) == 1;
        final boolean startup = step(25);
        if (dsc || startup) {
            c.tab(c.word(1023, "Premium Sound"), new Fn.Build() {
                @Override public void build(Page p) {
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
                p.stock(p.word(1039, "Guidance Volumes"));
                p.stock("Charging Voice Prompts");
                if (navi && step(0x38)) p.stock("Navigation Alerts");
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

    @Override public Intent stockIntent(Context c) { return component("com.android.soundsettings", ".SoundSetting"); }

    // ---- stock feature checks (com.android.soundsettings.util.Utils), replicated with the same system calls ----

    private static int prop(String key, int def) {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            return (Integer) sp.getMethod("getInt", String.class, int.class).invoke(null, key, def);
        } catch (Throwable t) { return def; }
    }

    private static String propStr(String key) {
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
            try {
                Class<?> k = Class.forName("com.hkmc.media.MicomAudioManager");
                Object o = k.getConstructor(Context.class).newInstance(c.getApplicationContext());
                sInstance = new Audio(o);
            } catch (Throwable t) { return null; }
            return sInstance;
        }

        Object call(String name, Class<?>[] types, Object... args) {
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
            call("setBassMidTreb", new Class[]{int.class, int.class, int.class}, clamp(bass), clamp(mid), clamp(treble));
        }

        void fadeBalance(int fade, int balance) {
            if (fade < 0 || balance < 0) return;
            call("setFadeBalance", new Class[]{int.class, int.class}, clamp(fade), clamp(balance));
        }

        private static int clamp(int v) { return Math.max(0, Math.min(20, v)); }
    }
}
