package dev.ccnc.settings.sections;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import dev.ccnc.settings.api.Category;
import dev.ccnc.settings.api.Fn;
import dev.ccnc.settings.api.Host;
import dev.ccnc.settings.api.Page;
import dev.ccnc.settings.api.Section;

/**
 * User Profile (stock classId com.hkmc.system.app.userprofile.UserProfileMainActivity). The active profile is read
 * from the framework UserProfileManager (getUserType(), getUserName(id)), as the stock Profile app does. Changing
 * user goes through the stock screen (it handles profile passwords, account links and confirmation popups), so the
 * row opens it; everything else (names, images, passwords, accounts, digital key, sync) stays in the stock entry.
 */
public final class UserProfileSection extends Section {
    @Override public String classId() { return "com.hkmc.system.app.userprofile.UserProfileMainActivity"; }
    @Override public int treeNode() { return 1007; }
    @Override public String icon() { return "set_ic_menu_userprofile"; }

    private static final String PKG = "com.hkmc.system.app.userprofile";

    @Override public void build(Category c) {
        final Context x = c.context();
        if (activeName(x) == null) return;  // no profile service: the card opens the stock screen
        c.tab(c.word(1248, "User Profile"), new Fn.Build() {
            @Override public void build(Page p) {
                p.value(p.word(6010, "Change User"),
                        new Fn.Str() { @Override public String get() { return activeName(x); } },
                        new Fn.Act() { @Override public void run(Host h) { h.openStock(); } });
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

    @Override public Intent stockIntent(Context c) { return component("dev.ccnc.profile", ".MainActivity"); }
}
