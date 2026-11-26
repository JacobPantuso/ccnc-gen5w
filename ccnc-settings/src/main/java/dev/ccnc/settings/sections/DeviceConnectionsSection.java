package dev.ccnc.settings.sections;

import android.content.Context;
import android.util.Log;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import dev.ccnc.settings.api.Ask;
import dev.ccnc.settings.api.Category;
import dev.ccnc.settings.api.Fn;
import dev.ccnc.settings.api.Page;
import dev.ccnc.settings.api.Scontrol;
import dev.ccnc.settings.api.Section;
import dev.ccnc.settings.api.Sys;

/**
 * Device Connections (stock classId com.daudio.app.phoneprojection, PhoneProjection DeviceConnectionSettings).
 * Stock tabs: "Bluetooth" (paired devices), "Android" (Android Auto), "IOS" (Apple CarPlay), MirrorLink / Baidu
 * (China / CarLife builds only). Here, all on ccNC pages:
 *  - "Bluetooth Connections": paired devices with connect / disconnect / delete / add new (BluetoothSectionDevices),
 *    then ccNC's own Device Connections menus Message Notification, Privacy Mode, Bluetooth System Information
 *    (BluetoothSection.extras).
 *  - "Phone Projection" (ccNC 7049): "Android Auto Options" (7050) / "Apple CarPlay Options" (7051), each with the
 *    enable check (7053 / stock "Enable Apple CarPlay"). Shown only when the stock checks
 *    a.b.isAndroidAutoSupport() / isCarPlaySupport() hold: com.hkmc.Feature.hasFeature("com.hkmc.software.
 *    connectivity.androidauto" / ".carplay"). Value = Settings.System androidauto_information / carplay_information
 *    (+ _dr1/_dr2 for driver profile 1/2, as stock d.ak(-1)/d.ao(-1)); 1 = enabled.
 *    Change = the stock PhoneProjection smart-control keys EnableAndroidAuto 30007 / EnableAppleCarPlay 30008
 *    (type 7, PhoneProjectionSettingDB$SSQUERY_DATA; receiver.k): the stock app itself disconnects the running session
 *    and notifies the projection services (and the wireless CarPlay mod sees the same broadcasts as from the stock
 *    screen). Turning one off asks for confirmation first. Not tested on car.
 */
public final class DeviceConnectionsSection extends Section {
    @Override public String classId() { return "com.daudio.app.phoneprojection"; }
    @Override public int treeNode() { return 1008; }
    @Override public String icon() { return "set_ic_menu_deviceconnections"; }

    private static final String TAG = "ccncSetup";
    private static final String PKG = "com.daudio.app.phoneprojection";
    private static final int K_ANDROID_AUTO = 30007, K_CARPLAY = 30008;

    @Override public void build(Category c) {
        final Context x = c.context();
        c.tab("Bluetooth Connections", new Fn.Build() {
            @Override public void build(Page p) { BluetoothSectionDevices.addRows(p); }
        });
        final boolean aa = feature("com.hkmc.software.connectivity.androidauto");
        final boolean cp = feature("com.hkmc.software.connectivity.carplay");
        if (aa || cp) {
            final Scontrol sc = Scontrol.of(x, PKG, 7);
            sc.prefetch(K_ANDROID_AUTO, K_CARPLAY);
            c.tab(c.word(7049, "Phone Projection"), new Fn.Build() {
                @Override public void build(Page p) {
                    if (aa) p.drill(p.word(7050, "Android Auto Options"), null, new Fn.Build() {
                        @Override public void build(Page q) {
                            enable(q, x, sc, q.word(7053, "Enable Android Auto"), "androidauto_information", K_ANDROID_AUTO);
                        }
                    });
                    if (cp) p.drill(p.word(7051, "Apple CarPlay Options"), null, new Fn.Build() {
                        @Override public void build(Page q) {
                            enable(q, x, sc, "Enable Apple CarPlay", "carplay_information", K_CARPLAY);
                        }
                    });
                }
            });
        }
        BluetoothSection.extras(c);
    }

    private static void enable(final Page q, final Context x, final Scontrol sc, final String title, String key, final int id) {
        sc.attach(q.host());
        final Fn.Bool on = enabled(x, key);
        q.toggle(title, on, new Fn.SetBool() {
            @Override public void set(final boolean want) {
                final Runnable go = new Runnable() { @Override public void run() {
                    sc.set(id, want ? 1 : 0);
                    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(new Runnable() {
                        @Override public void run() { try { q.host().refresh(); } catch (Throwable ignored) { } }
                    }, 1200);
                } };
                if (want) go.run();
                else Ask.confirm(q.host().activity(), title, "A connected phone will be disconnected.", "Turn Off", go);
            }
        });
    }

    /** Stock read: Settings.System key (+ "_dr1"/"_dr2" for user type 1/2) == 1. */
    private static Fn.Bool enabled(final Context x, final String key) {
        return new Fn.Bool() {
            @Override public boolean get() {
                int u = userType(x);
                String k = u == 1 ? key + "_dr1" : u == 2 ? key + "_dr2" : key;
                return Sys.getInt(x, k, 0) == 1;
            }
        };
    }

    /** com.hkmc.Feature.hasFeature(name), as PhoneProjection a.b does. */
    static boolean feature(String name) {
        if (dev.ccnc.settings.api.Demo.on()) return dev.ccnc.settings.api.Demo.feature(name);
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
        if (dev.ccnc.settings.api.Demo.on()) return 0;
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
