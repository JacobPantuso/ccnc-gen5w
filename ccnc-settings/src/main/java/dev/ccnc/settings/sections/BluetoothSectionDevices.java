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
 * "Bluetooth" tab) categories. Reads only: bonded devices from BluetoothAdapter and, per device, the profiles
 * whose proxy reports STATE_CONNECTED. Connect / disconnect / delete are left to the stock screen: every device
 * row opens it (h.openStock()). Profile wording: "Phone" (ccNC 5009 / stock BtSettings) for hands-free,
 * "Bluetooth Audio" (ccNC 5008) for A2DP.
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

    /** Adds one row per paired device (connected ones first); tap opens the category's stock screen. */
    static void addRows(final Page p) {
        final Context x = p.context();
        lastHost = new WeakReference<Host>(p.host());
        ensureProxies(x);
        final String phone = p.word(5009, "Phone");
        final String audio = p.word(5008, "Bluetooth Audio");
        List<BluetoothDevice> list = new ArrayList<BluetoothDevice>();
        try {
            BluetoothAdapter a = BluetoothAdapter.getDefaultAdapter();
            Set<BluetoothDevice> bonded = a != null ? a.getBondedDevices() : null;
            if (bonded != null) list.addAll(bonded);
        } catch (Throwable t) {
            Log.w(TAG, "bt: bonded devices unreadable: " + t);
        }
        final Map<BluetoothDevice, String> state = new LinkedHashMap<BluetoothDevice, String>();
        for (BluetoothDevice d : list) state.put(d, profiles(x, d, phone, audio));
        Collections.sort(list, new Comparator<BluetoothDevice>() {
            @Override public int compare(BluetoothDevice a, BluetoothDevice b) {
                boolean ca = state.get(a) != null, cb = state.get(b) != null;
                if (ca != cb) return ca ? -1 : 1;
                return name(a).compareToIgnoreCase(name(b));
            }
        });
        if (list.isEmpty()) {
            // Stock PhoneProjection / BtSettings wording for an empty list.
            p.value("No devices have been paired.", null, new Fn.Act() {
                @Override public void run(Host h) { h.openStock(); }
            });
            return;
        }
        for (final BluetoothDevice d : list) {
            final String v = state.get(d);
            p.value(name(d), new Fn.Str() { @Override public String get() { return v; } }, new Fn.Act() {
                @Override public void run(Host h) { h.openStock(); }
            });
        }
    }

    static String name(BluetoothDevice d) {
        String n = null;
        try { n = d.getName(); } catch (Throwable ignored) { }
        if (n == null || n.length() == 0) try { n = d.getAddress(); } catch (Throwable ignored) { }
        return n != null ? n : "";
    }
}
