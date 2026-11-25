package dev.ccnc.home;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Name of the phone that's connected, for the CarPlay / Android Auto card and the welcome page.
 *
 * <p>CarPlay's DiPO service learns the iPhone's Bluetooth address and name in
 * {@code onSessionDetails} but keeps them private, so this asks Bluetooth instead, in order:
 * <ol>
 *   <li>the Broadcom hands-free profile ({@code com.broadcom.bt.hfdevice.BluetoothHfDevice},
 *       framework jar, by reflection) as the stock home uses it: its connected devices;</li>
 *   <li>devices seen connecting (ACL broadcasts) while ccNC Home runs;</li>
 *   <li>bonded devices whose hidden {@code isConnected()} says yes (where the build has it).</li>
 * </ol>
 * Null when none of these knows; the card then says "Connected".
 */
final class PhoneInfo {

    interface Listener { void onPhoneChanged(); }

    private static final String HF_CLASS = "com.broadcom.bt.hfdevice.BluetoothHfDevice";
    private static final String HF_CONN = "com.broadcom.bt.hfdevice.profile.action.CONNECTION_STATE_CHANGED";

    private final Context ctx;
    private final Listener listener;
    private final Handler handler = new Handler();
    private final Set<String> aclConnected = new HashSet<String>();
    private Object hfProxy;
    private boolean started;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            BluetoothDevice d = i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            String a = i.getAction();
            if (d != null && BluetoothDevice.ACTION_ACL_CONNECTED.equals(a)) aclConnected.add(d.getAddress());
            if (d != null && BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(a)) aclConnected.remove(d.getAddress());
            // The name can arrive a moment after the connection: ask again shortly.
            changed();
            handler.postDelayed(new Runnable() { @Override public void run() { changed(); } }, 3000);
        }
    };

    PhoneInfo(Context ctx, Listener listener) {
        this.ctx = ctx.getApplicationContext();
        this.listener = listener;
    }

    void start() {
        if (started) return;
        started = true;
        IntentFilter f = new IntentFilter(BluetoothDevice.ACTION_ACL_CONNECTED);
        f.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        f.addAction(BluetoothDevice.ACTION_NAME_CHANGED);
        f.addAction(HF_CONN);
        ctx.registerReceiver(receiver, f);
        bindHandsFree();
    }

    void stop() {
        if (!started) return;
        started = false;
        try { ctx.unregisterReceiver(receiver); } catch (IllegalArgumentException ignored) {}
        handler.removeCallbacksAndMessages(null);
        if (hfProxy != null) {
            try { hfProxy.getClass().getMethod("closeProxy").invoke(hfProxy); } catch (Throwable ignored) {}
            hfProxy = null;
        }
    }

    private void changed() { if (started) listener.onPhoneChanged(); }

    private void bindHandsFree() {
        try {
            Class<?> c = Class.forName(HF_CLASS);
            Method get = c.getMethod("getProxy", Context.class, BluetoothProfile.ServiceListener.class);
            get.invoke(null, ctx, new BluetoothProfile.ServiceListener() {
                @Override public void onServiceConnected(int profile, BluetoothProfile proxy) {
                    hfProxy = proxy;
                    changed();
                }
                @Override public void onServiceDisconnected(int profile) { hfProxy = null; }
            });
        } catch (Throwable t) {
            Log.i(WeatherClient.TAG, "phone: no Broadcom hands-free profile (" + t + ")");
        }
    }

    /**
     * Name of the connected phone; {@code iphone} prefers an iPhone when several are
     * connected (CarPlay), false prefers the rest (Android Auto). Null if unknown.
     */
    String name(boolean iphone) {
        BluetoothAdapter a;
        try {
            a = BluetoothAdapter.getDefaultAdapter();
        } catch (Throwable t) {
            return null;
        }
        if (a == null || !a.isEnabled()) return null;
        String best = null;
        try {
            if (hfProxy != null) {
                @SuppressWarnings("unchecked")
                List<BluetoothDevice> l = (List<BluetoothDevice>) hfProxy.getClass().getMethod("getConnectedDevices").invoke(hfProxy);
                best = pick(l, iphone, null);
            }
        } catch (Throwable t) {
            Log.w(WeatherClient.TAG, "phone: hands-free proxy failed", t);
        }
        if (best != null) return best;
        try {
            Set<BluetoothDevice> bonded = a.getBondedDevices();
            if (bonded == null) return null;
            Method isConnected = null;
            try { isConnected = BluetoothDevice.class.getMethod("isConnected"); } catch (NoSuchMethodException ignored) {}
            for (BluetoothDevice d : bonded) {
                boolean on = aclConnected.contains(d.getAddress());
                if (!on && isConnected != null) {
                    try { on = (Boolean) isConnected.invoke(d); } catch (Throwable ignored) {}
                }
                if (!on) continue;
                best = pick(java.util.Collections.singletonList(d), iphone, best);
                if (best != null && isIphone(best) == iphone) break;
            }
        } catch (Throwable t) {
            Log.w(WeatherClient.TAG, "phone: bonded devices failed", t);
        }
        return best;
    }

    /** The first named device, preferring the wanted kind; keeps {@code current} if nothing better. */
    private static String pick(List<BluetoothDevice> devices, boolean iphone, String current) {
        if (devices == null) return current;
        String any = current;
        for (BluetoothDevice d : devices) {
            String n = d.getName();
            if (n == null || n.trim().isEmpty()) continue;
            n = n.trim();
            if (isIphone(n) == iphone) return n;
            if (any == null) any = n;
        }
        return any;
    }

    private static boolean isIphone(String name) {
        return name.toLowerCase(java.util.Locale.US).contains("iphone");
    }
}
