package dev.ccnc.settings.sections;

import android.content.Context;
import android.util.Log;

import dev.ccnc.settings.api.Ask;
import dev.ccnc.settings.api.Car;
import dev.ccnc.settings.api.Category;
import dev.ccnc.settings.api.Fn;
import dev.ccnc.settings.api.Host;
import dev.ccnc.settings.api.Page;
import dev.ccnc.settings.api.Section;
import dev.ccnc.common.Profiles;

/**
 * User Profile (stock classId com.hkmc.system.app.userprofile.UserProfileMainActivity).
 *   Change User         ccNC Profile's chooser (instant feedback; the car's password screen when a profile has one)
 *   Default Profile     ccNC's choice on the start-up chooser: Last Used (the car's own choice, which follows the key on cars with key-profile linking) or a profile
 *   Auto-Advance        how long the start-up chooser counts down before it continues (3 s default; Off = wait)
 *   <name> photo        ccNC Profile's photo picker (ccNC avatars, the car's photo, a picture from a USB stick)
 *   Names               UserProfileManager.setUserName(type, name), the call the stock profile app makes (b.q);
 *                       typed in a ccNC panel. Guest keeps the car's name.
 *   Passwords           NOT PORTED on purpose (note): UserProfileManager.setUserPassword exists, but how the stock app
 *                       encodes the password and couples it to the profile lock / Bluelink account is not decoded, and
 *                       a wrong write could lock a profile. Linked accounts and Digital Key are telematics flows.
 * Default profile, auto-advance and photos are stored by ccNC Profile (content://dev.ccnc.profile).
 * Nothing opens the stock profile screen.
 */
public final class UserProfileSection extends Section {
    @Override public String classId() { return "com.hkmc.system.app.userprofile.UserProfileMainActivity"; }
    @Override public int treeNode() { return 1007; }
    @Override public String icon() { return "set_ic_menu_userprofile"; }

    private static final String PKG = "com.hkmc.system.app.userprofile";
    private static final int[] AUTO = {3, 5, 10, -1};
    private static final String[] AUTO_LABELS = {"3 s", "5 s", "10 s", "Off (Wait for a Tap)"};

    @Override public void build(Category c) {
        final Context x = c.context();
        c.tab(c.word(1248, "User Profile"), new Fn.Build() {
            @Override public void build(Page p) {
                p.value(p.word(6010, "Change User"),
                        new Fn.Str() { @Override public String get() { String n = activeName(x); return n != null ? n : Profiles.name(x, Profiles.active(x)); } },
                        new Fn.Act() { @Override public void run(Host h) { h.start(component("dev.ccnc.profile", ".MainActivity")); } });
                final String[] defLabels = {"Last Used", Profiles.name(x, 0), Profiles.name(x, 1), Profiles.name(x, 2)};
                final Fn.Int defGet = new Fn.Int() { @Override public int get() { return Profiles.setting(x, "default_profile", -1) + 1; } };
                final Fn.SetInt defSet = new Fn.SetInt() { @Override public void set(int v) { Profiles.setSetting(x, "default_profile", v - 1); } };
                p.drill("Default Profile", new Fn.Str() { @Override public String get() {
                    int i = defGet.get();
                    return i >= 0 && i < defLabels.length ? defLabels[i] : defLabels[0];
                } }, new Fn.Build() { @Override public void build(Page q) {
                    // Key recognition isn't something the unit reports; cars that have it switch profile by themselves.
                    q.radios(defLabels, new String[]{"On vehicles that support key-profile linking, this follows the profile linked to the key or digital key."},
                            defGet, defSet);
                } });
                p.choice("Auto-Advance", AUTO_LABELS,
                        new Fn.Int() { @Override public int get() {
                            int s = Profiles.setting(x, "auto_advance", 3);
                            for (int i = 0; i < AUTO.length; i++) if (AUTO[i] == s) return i;
                            return 0;
                        } },
                        new Fn.SetInt() { @Override public void set(int v) { if (v >= 0 && v < AUTO.length) Profiles.setSetting(x, "auto_advance", AUTO[v]); } });
                p.heading("Photos");
                for (int i = 0; i < Profiles.COUNT; i++) {
                    final int id = i;
                    p.value(Profiles.name(x, id), new Fn.Str() { @Override public String get() { return "Change"; } },
                            new Fn.Act() { @Override public void run(Host h) { Profiles.pickPhoto(x, id); } });
                }
                p.heading("Names");
                for (int i = 1; i < Profiles.COUNT; i++) {
                    final int id = i;
                    p.value(Profiles.name(x, id), new Fn.Str() { @Override public String get() { return "Rename"; } },
                            new Fn.Act() { @Override public void run(final Host h) {
                                Ask.text(h.activity(), "Profile Name", Profiles.name(x, id), false, 20, new Ask.Text() {
                                    @Override public void onText(String v) {
                                        if (v.length() == 0) return;
                                        Car.call(Car.manager(x, "com.hkmc.userprofile.UserProfileManager"), "setUserName",
                                                new Class<?>[] { int.class, String.class }, id, v);
                                        new android.os.Handler().postDelayed(new Runnable() { @Override public void run() { h.refresh(); } }, 600);
                                    }
                                });
                            } });
                }
                p.heading(p.word(6002, "Security"));
                p.note("Profile passwords, linked accounts and Digital Key are not available in ccNC Setup.");
            }
        });
    }

    /** Name of the active profile: the user's name, or else the stock app's own default name (Guest / Driver 1 / Driver 2). */
    static String activeName(Context x) {
        try {
            Class<?> c = Class.forName("com.hkmc.userprofile.UserProfileManager");
            Object m = c.getConstructor(Context.class).newInstance(x);
            int type = ((Number) c.getMethod("getUserType").invoke(m)).intValue();
            String n = (String) c.getMethod("getUserName", int.class).invoke(m, type);
            if (n != null && n.trim().length() > 0) return n.trim();
            String res = type == 0 ? "guest" : type == 1 ? "driver_1" : type == 2 ? "driver_2" : null;
            if (res == null) return null;
            Context pc = x.createPackageContext(PKG, 0);
            int id = pc.getResources().getIdentifier(res, "string", PKG);
            return id != 0 ? pc.getResources().getString(id) : null;
        } catch (Throwable t) {
            Log.i("ccncSetup", "profile: " + t);
            return null;
        }
    }

}
