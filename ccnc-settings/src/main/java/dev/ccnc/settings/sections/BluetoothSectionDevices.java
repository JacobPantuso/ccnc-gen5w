package dev.ccnc.settings.sections;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.util.Log;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.ccnc.settings.api.Fn;
import dev.ccnc.settings.api.Host;
import dev.ccnc.settings.api.Page;

/**
 * Paired-device list shared by the Bluetooth (com.android.btsettings) and Device Connections (PhoneProjection
 * "Bluetooth" tab) categories: bonded devices from BluetoothAdapter and, per device, the connected profiles.
 * Each device drills into its own ccNC page with the stock actions, made with the calls the stock BtSettings makes on
 * the Broadcom connection manager (HfDeviceBTCMManager, com.broadcom.bt.jar):
 *   Connect      IbtcmConnect(address)       (refused while IbtcmIsBusy(), as stock does)
 *   Disconnect   IbtcmDisconnect(address)
 *   Delete       IbtcmRemoveDevice(device), after a confirmation; BluetoothDevice.removeBond() when the manager isn't there
 *   Add New      IbtcmSetDiscoverableMode(true, true): the unit becomes visible; the phone starts the pairing and the
 *                unit's own pairing popup (system Bluetooth, not a settings screen) shows the passkey
 * Needs BLUETOOTH / BLUETOOTH_ADMIN only (both granted at install). Not tested on car.
 * Profile wording: "Phone" (ccNC 5009 / stock BtSettings) for hands-free, "Bluetooth Audio" (ccNC 5008) for A2DP.
 */
final class BluetoothSectionDevices {
    private static final String TAG = "ccncSetup";
    /** profile id -> proxy, filled asynchronously by getProfileProxy. */
    private static final Map<Integer, BluetoothProfile> PROXIES = new LinkedHashMap<Integer, BluetoothProfile>();
    /** profile id -> true for hands-free ("Phone"), false for audio ("Bluetooth Audio"). */
    private static final Map<Integer, Boolean> KIND = new LinkedHashMap<Integer, Boolean>();
    private static boolean requested;
    private static WeakReference<Host> lastHost;

    private BluetoothSectionDevices() { }

    /** Ask once for the HFP/A2DP proxies this framework exposes (standard and, if present, the hidden sink/client ones). */
    private static void ensureProxies(Context x) {
        if (requested) return;
        requested = true;
        KIND.put(BluetoothProfile.HEADSET, Boolean.TRUE);
        KIND.put(BluetoothProfile.A2DP, Boolean.FALSE);
        for (String f : new String[]{"HEADSET_CLIENT", "HANDSFREE_CLIENT", "HFP_CLIENT", "A2DP_SINK"}) {
            try {
                Field fl = BluetoothProfile.class.getField(f);
                KIND.put(fl.getInt(null), f.startsWith("A2DP") ? Boolean.FALSE : Boolean.TRUE);
            } catch (Throwable ignored) { }
        }
        BluetoothAdapter a = BluetoothAdapter.getDefaultAdapter();
        if (a == null) return;
        Context app = x.getApplicationContext();
        for (final Integer id : KIND.keySet()) {
            try {
                a.getProfileProxy(app, new BluetoothProfile.ServiceListener() {
                    @Override public void onServiceConnected(int profile, BluetoothProfile proxy) {
                        synchronized (PROXIES) { PROXIES.put(profile, proxy); }
                        refresh();
                    }
                    @Override public void onServiceDisconnected(int profile) {
                        synchronized (PROXIES) { PROXIES.remove(profile); }
                        refresh();
                    }
                }, id);
            } catch (Throwable t) {
                Log.w(TAG, "bt: proxy " + id + " unavailable: " + t);
            }
        }
    }

    private static void refresh() {
        Host h = lastHost != null ? lastHost.get() : null;
        if (h != null) try { h.refresh(); } catch (Throwable ignored) { }
    }

    private static Object btcm;
    private static boolean btcmRetry;

    /**
     * Stock path (HKMC_BtSettings BTUtils): Broadcom HfDeviceBTCMManager.getLocalBTCMManager(ctx)
     * .IbtcmGetConnectedProfilesAll(device) -> boolean[]: [0] = hands-free (Phone), [1] = A2DP (Bluetooth Audio).
     * com.broadcom.bt.jar is a shared library not on the boot class path; loaded with a DexClassLoader because the
     * settings manifest has no uses-library for it. The manager binds its service asynchronously: null on first use.
     */
    /** The Broadcom connection manager (null until its service is bound, or when the jar isn't on this unit). */
    static Object manager(Context x) {
        try {
            if (btcm == null) {
                Class<?> k;
                try {
                    k = Class.forName("com.broadcom.bt.hfdevice.hfbtcm.HfDeviceBTCMManager");
                } catch (Throwable notInPath) {
                    ClassLoader cl = new dalvik.system.DexClassLoader("/system/framework/com.broadcom.bt.jar",
                            x.getDir("bcbt", Context.MODE_PRIVATE).getPath(), null, Context.class.getClassLoader());
                    k = cl.loadClass("com.broadcom.bt.hfdevice.hfbtcm.HfDeviceBTCMManager");
                }
                btcm = k.getMethod("getLocalBTCMManager", Context.class).invoke(null, x.getApplicationContext());
            }
        } catch (Throwable t) { Log.w(TAG, "bt: BTCM manager unavailable: " + t); }
        return btcm;
    }

    private static Object btcmCall(Context x, String m, Class<?>[] types, Object... args) {
        Object b = manager(x);
        if (b == null) return null;
        try { return b.getClass().getMethod(m, types).invoke(b, args); }
        catch (Throwable t) { Log.w(TAG, "bt: " + m + " failed: " + t); return null; }
    }

    private static boolean[] btcmProfiles(Context x, BluetoothDevice d) {
        try {
            if (btcm == null) {
                Class<?> k;
                try {
                    k = Class.forName("com.broadcom.bt.hfdevice.hfbtcm.HfDeviceBTCMManager");
                } catch (Throwable notInPath) {
                    ClassLoader cl = new dalvik.system.DexClassLoader("/system/framework/com.broadcom.bt.jar",
                            x.getDir("bcbt", Context.MODE_PRIVATE).getPath(), null, Context.class.getClassLoader());
                    k = cl.loadClass("com.broadcom.bt.hfdevice.hfbtcm.HfDeviceBTCMManager");
                }
                btcm = k.getMethod("getLocalBTCMManager", Context.class).invoke(null, x.getApplicationContext());
            }
            if (btcm == null) return null;
            return (boolean[]) btcm.getClass().getMethod("IbtcmGetConnectedProfilesAll", BluetoothDevice.class).invoke(btcm, d);
        } catch (Throwable t) {
            Log.w(TAG, "bt: BTCM profiles unreadable: " + t);
            return null;
        }
    }

    /** Connected-profile wording for one device, or null when nothing is connected / unreadable. */
    private static String profiles(Context x, BluetoothDevice d, String phone, String audio) {
        boolean hf = false, au = false;
        boolean[] b = btcmProfiles(x, d);
        if (b != null) {
            hf = b.length > 0 && b[0];
            au = b.length > 1 && b[1];
        } else if (!btcmRetry) {
            // service still binding: re-read once shortly after
            btcmRetry = true;
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override public void run() { refresh(); }
            }, 1500);
        }
        synchronized (PROXIES) {
            for (Map.Entry<Integer, BluetoothProfile> e : PROXIES.entrySet()) {
                try {
                    if (e.getValue().getConnectionState(d) != BluetoothProfile.STATE_CONNECTED) continue;
                    if (Boolean.TRUE.equals(KIND.get(e.getKey()))) hf = true; else au = true;
                } catch (Throwable ignored) { }
            }
        }
        if (hf && au) return phone + ", " + audio;
        if (hf) return phone;
        if (au) return audio;
        return null;
    }

    /** Adds "Add New" and one row per paired device (connected ones first); a device row drills into its page. */
    static void addRows(final Page p) {
        if (dev.ccnc.settings.api.Demo.on()) { demoRows(p); return; }
        final Context x = p.context();
        lastHost = new WeakReference<Host>(p.host());
        ensureProxies(x);
        final String phone = p.word(5009, "Phone");
        final String audio = p.word(5008, "Bluetooth Audio");
        List<BluetoothDevice> list = new ArrayList<BluetoothDevice>();
        BluetoothAdapter ad = null;
        try {
            ad = BluetoothAdapter.getDefaultAdapter();
            Set<BluetoothDevice> bonded = ad != null ? ad.getBondedDevices() : null;
            if (bonded != null) list.addAll(bonded);
        } catch (Throwable t) {
            Log.w(TAG, "bt: bonded devices unreadable: " + t);
        }
        if (ad == null) { p.note("Not available on this unit."); return; }
        final Map<BluetoothDevice, String> state = new LinkedHashMap<BluetoothDevice, String>();
        for (BluetoothDevice d : list) state.put(d, profiles(x, d, phone, audio));
        Collections.sort(list, new Comparator<BluetoothDevice>() {
            @Override public int compare(BluetoothDevice a, BluetoothDevice b) {
                boolean ca = state.get(a) != null, cb = state.get(b) != null;
                if (ca != cb) return ca ? -1 : 1;
                return name(a).compareToIgnoreCase(name(b));
            }
        });
        final BluetoothAdapter adapter = ad;
        p.drill("Add New", null, new Fn.Build() {
            @Override public void build(Page q) {
                String unit = null;
                try { unit = adapter.getName(); } catch (Throwable ignored) { }
                if (unit != null) q.info("Vehicle Name", unit);
                q.note("Search for the vehicle name on your phone's Bluetooth menu and confirm the passkey shown.");
                q.button("Make Vehicle Visible", new Fn.Act() {
                    @Override public void run(Host h) {
                        Object r = btcmCall(x, "IbtcmSetDiscoverableMode", new Class<?>[] { boolean.class, boolean.class }, true, true);
                        android.widget.Toast.makeText(h.activity(), Boolean.TRUE.equals(r) ? "Waiting for the phone" : "--", android.widget.Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
        if (list.isEmpty()) {
            // Stock PhoneProjection / BtSettings wording for an empty list.
            p.note("No devices have been paired.");
            return;
        }
        for (final BluetoothDevice d : list) {
            final String v = state.get(d);
            p.drill(name(d), new Fn.Str() { @Override public String get() { return v; } }, new Fn.Build() {
                @Override public void build(Page q) { devicePage(q, x, d, phone, audio); }
            });
        }
    }

    /** Emulator demo (dev.ccnc.settings.api.Demo): the same pages over two invented phones kept in memory. */
    private static void demoRows(Page p) {
        final String both = p.word(5009, "Phone") + ", " + p.word(5008, "Bluetooth Audio");
        p.drill("Add New", null, new Fn.Build() {
            @Override public void build(Page q) {
                q.info("Vehicle Name", dev.ccnc.settings.api.Demo.getStr("bt.name", "Demo Vehicle"));
                q.note("Search for the vehicle name on your phone's Bluetooth menu and confirm the passkey shown.");
                q.button("Make Vehicle Visible", new Fn.Act() {
                    @Override public void run(Host h) {
                        android.widget.Toast.makeText(h.activity(), "Waiting for the phone", android.widget.Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
        List<String> names = dev.ccnc.settings.api.Demo.btDevices();
        if (names.isEmpty()) { p.note("No devices have been paired."); return; }
        for (final String n : names) {
            p.drill(n, new Fn.Str() { @Override public String get() { return dev.ccnc.settings.api.Demo.btConnected(n) ? both : null; } }, new Fn.Build() {
                @Override public void build(Page q) {
                    final boolean on = dev.ccnc.settings.api.Demo.btConnected(n);
                    q.info(n, on ? both : "Not Connected");
                    q.button(on ? "Disconnect" : "Connect", new Fn.Act() {
                        @Override public void run(Host h) {
                            // one phone at a time, like the unit's hands-free connection
                            if (!on) for (String o : dev.ccnc.settings.api.Demo.btDevices()) dev.ccnc.settings.api.Demo.btConnect(o, false);
                            dev.ccnc.settings.api.Demo.btConnect(n, !on);
                            h.refresh();
                        }
                    });
                    q.button("Delete", new Fn.Act() {
                        @Override public void run(final Host h) {
                            dev.ccnc.settings.api.Ask.confirm(h.activity(), n,
                                    "Deleting a device also deletes its call history and contacts.", "Delete", new Runnable() {
                                @Override public void run() { dev.ccnc.settings.api.Demo.btDelete(n); h.back(); }
                            });
                        }
                    });
                }
            });
        }
    }

    private static void later(final Host h, int ms) {
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override public void run() { try { h.refresh(); } catch (Throwable ignored) { } }
        }, ms);
    }

    /** One paired device: state, Connect / Disconnect, Delete. */
    private static void devicePage(Page q, final Context x, final BluetoothDevice d, String phone, String audio) {
        lastHost = new WeakReference<Host>(q.host());
        final String st = profiles(x, d, phone, audio);
        q.info(name(d), st != null ? st : "Not Connected");
        if (st == null) q.button("Connect", new Fn.Act() {
            @Override public void run(Host h) {
                if (Boolean.TRUE.equals(btcmCall(x, "IbtcmIsBusy", new Class<?>[0]))) {
                    android.widget.Toast.makeText(h.activity(), "Bluetooth Is Busy", android.widget.Toast.LENGTH_SHORT).show();
                    return;
                }
                btcmCall(x, "IbtcmConnect", new Class<?>[] { String.class }, d.getAddress());
                later(h, 2500); later(h, 6000);
            }
        });
        else q.button("Disconnect", new Fn.Act() {
            @Override public void run(final Host h) {
                dev.ccnc.settings.api.Ask.confirm(h.activity(), name(d), "Disconnect the Bluetooth Device?", "Disconnect", new Runnable() {
                    @Override public void run() {
                        btcmCall(x, "IbtcmDisconnect", new Class<?>[] { String.class }, d.getAddress());
                        later(h, 2000); later(h, 5000);
                    }
                });
            }
        });
        q.button("Delete", new Fn.Act() {
            @Override public void run(final Host h) {
                dev.ccnc.settings.api.Ask.confirm(h.activity(), name(d),
                        "Deleting a device also deletes its call history and contacts.", "Delete", new Runnable() {
                    @Override public void run() {
                        Object r = btcmCall(x, "IbtcmRemoveDevice", new Class<?>[] { BluetoothDevice.class }, d);
                        if (r == null) {
                            try { BluetoothDevice.class.getMethod("removeBond").invoke(d); }
                            catch (Throwable t) { Log.w(TAG, "bt: removeBond failed: " + t); }
                        }
                        h.back();
                        later(h, 1500);
                    }
                });
            }
        });
    }

    static String name(BluetoothDevice d) {
        String n = null;
        try { n = d.getName(); } catch (Throwable ignored) { }
        if (n == null || n.length() == 0) try { n = d.getAddress(); } catch (Throwable ignored) { }
        return n != null ? n : "";
    }
}
