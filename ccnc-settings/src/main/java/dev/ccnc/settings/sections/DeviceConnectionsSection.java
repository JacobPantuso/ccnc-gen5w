package dev.ccnc.settings.sections;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import dev.ccnc.settings.api.Category;
import dev.ccnc.settings.api.Fn;
import dev.ccnc.settings.api.Page;
import dev.ccnc.settings.api.Section;
import dev.ccnc.settings.api.Sys;

/**
 * Device Connections (stock classId com.daudio.app.phoneprojection, PhoneProjection DeviceConnectionSettings).
 * Stock tabs: "Bluetooth" (paired devices), "Android" (Android Auto), "IOS" (Apple CarPlay), MirrorLink / Baidu
 * (China / CarLife builds only).
 *  - "Bluetooth Connections" (stock PhoneProjection wording): paired devices + connected profiles, read only (BluetoothSectionDevices);
 *    connect/disconnect/delete through the stock screen.
 *  - "Phone Projection" (ccNC 7049): "Android Auto Options" (7050) / "Apple CarPlay Options" (7051), each with the
 *    enable check (7053 / stock "Enable Apple CarPlay"). Shown only when the stock checks
 *    a.b.isAndroidAutoSupport() / isCarPlaySupport() hold: com.hkmc.Feature.hasFeature("com.hkmc.software.
 *    connectivity.androidauto" / ".carplay"). Value = Settings.System androidauto_information / carplay_information
 *    (+ _dr1/_dr2 for driver profile 1/2, as stock d.ak(-1)/d.ao(-1)); 1 = enabled.
 *    Writes are NOT done here: enabling/disabling goes through stock confirmation popups, disconnects the running
 *    session and notifies the projection services (and the wireless CarPlay mod), so a tap opens the stock
 *    screen on that tab (extra "currentTab"). Not tested on car.
 */
public final class DeviceConnectionsSection extends Section {
    @Override public String classId() { return "com.daudio.app.phoneprojection"; }
    @Override public int treeNode() { return 1008; }
    @Override public String icon() { return "set_ic_menu_deviceconnections"; }

    private static final String TAG = "ccncSetup";
    private static final String PKG = "com.daudio.app.phoneprojection";

    @Override public void build(Category c) {
        final Context x = c.context();
        c.tab("Bluetooth Connections", new Fn.Build() {
            @Override public void build(Page p) { BluetoothSectionDevices.addRows(p); }
        });
        final boolean aa = feature("com.hkmc.software.connectivity.androidauto");
        final boolean cp = feature("com.hkmc.software.connectivity.carplay");
        if (!aa && !cp) return;
        c.tab(c.word(7049, "Phone Projection"), new Fn.Build() {
            @Override public void build(Page p) {
                if (aa) p.drill(p.word(7050, "Android Auto Options"), null, new Fn.Build() {
                    @Override public void build(Page q) {
                        q.toggle(q.word(7053, "Enable Android Auto"),
                                enabled(x, "androidauto_information"), openTab(x, "Android"));
                    }
                });
                if (cp) p.drill(p.word(7051, "Apple CarPlay Options"), null, new Fn.Build() {
                    @Override public void build(Page q) {
                        q.toggle("Enable Apple CarPlay", enabled(x, "carplay_information"), openTab(x, "IOS"));
                    }
                });
            }
        });
    }

    @Override public Intent stockIntent(Context c) { return component(PKG, ".settings.ui.DeviceConnectionSettings"); }

    /** Stock read: Settings.System key (+ "_dr1"/"_dr2" for user type 1/2) == 1. */
    private static Fn.Bool enabled(final Context x, final String key) {
        return new Fn.Bool() {
            @Override public boolean get() {
                int u = userType(x);
                String k = u == 1 ? key + "_dr1" : u == 2 ? key + "_dr2" : key;
                int v = Sys.getInt(x, k, 0);
                Log.i(TAG, "devconn: user " + u + " " + k + "=" + v);
                return v == 1;
            }
        };
    }

    /** "Write" = open the stock Device Connections screen on the given tab; the user confirms there. */
    private static Fn.SetBool openTab(final Context x, final String tab) {
        return new Fn.SetBool() {
            @Override public void set(boolean v) {
                try {
                    Intent i = component(PKG, ".settings.ui.DeviceConnectionSettings");
                    i.putExtra("currentTab", tab);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    x.startActivity(i);
                } catch (Throwable t) {
                    Log.w(TAG, "devconn: stock open failed: " + t);
                }
            }
        };
    }

    /** com.hkmc.Feature.hasFeature(name), as PhoneProjection a.b does. */
    static boolean feature(String name) {
        try {
            Class<?> f = Class.forName("com.hkmc.Feature");
            Object r = f.getMethod("hasFeature", String.class).invoke(null, name);
            return Boolean.TRUE.equals(r);
        } catch (Throwable t) {
            Log.w(TAG, "devconn: Feature." + name + " unreadable: " + t);
            return false;
        }
    }

    /** Current driver profile type (stock d.ea(): UserProfileManager.getUserType()); 0 when unknown. */
    static int userType(Context x) {
        try {
            Class<?> k = Class.forName("com.hkmc.userprofile.UserProfileManager");
            Object m = null;
            for (Method me : k.getMethods()) {
                if (!Modifier.isStatic(me.getModifiers()) || !me.getName().equals("getInstance")) continue;
                Class<?>[] pt = me.getParameterTypes();
                if (pt.length == 0) m = me.invoke(null);
                else if (pt.length == 1 && pt[0] == Context.class) m = me.invoke(null, x);
                if (m != null) break;
            }
            if (m != null) return ((Number) k.getMethod("getUserType").invoke(m)).intValue();
        } catch (Throwable ignored) { }
        try {
            Context sc = x.createPackageContext("com.android.settings",
                    Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
            Class<?> w = sc.getClassLoader().loadClass("com.android.settings.util.Wrapper$UserProfileManager");
            Object m = w.getMethod("getInstance", Context.class).invoke(null, x);
            return ((Number) w.getMethod("getUserType").invoke(m)).intValue();
        } catch (Throwable t) {
            Log.w(TAG, "devconn: user type unreadable: " + t);
            return 0;
        }
    }

}
