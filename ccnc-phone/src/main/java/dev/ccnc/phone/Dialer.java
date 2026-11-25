package dev.ccnc.phone;

import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import java.lang.reflect.Method;

/**
 * Places calls the way the stock phone app does: through the Broadcom hands-free profile
 * ({@code com.broadcom.bt.hfdevice.BluetoothHfDevice}, framework jar, by reflection), which
 * routes the call over the paired phone. Without it (emulator) it falls back to a plain
 * tel: intent so the stock phone handles it.
 */
final class Dialer {
    static final String TAG = "ccncPhone";
    private static final String HF = "com.broadcom.bt.hfdevice.BluetoothHfDevice";

    private final Context ctx;
    private Object proxy;

    Dialer(Context ctx) { this.ctx = ctx; }

    void start() {
        try {
            Class<?> c = Class.forName(HF);
            Method get = c.getMethod("getProxy", Context.class, BluetoothProfile.ServiceListener.class);
            get.invoke(null, ctx, new BluetoothProfile.ServiceListener() {
                @Override public void onServiceConnected(int profile, BluetoothProfile p) { proxy = p; }
                @Override public void onServiceDisconnected(int profile) { proxy = null; }
            });
        } catch (Throwable t) {
            Log.i(TAG, "no Broadcom hands-free profile (" + t + ")");
        }
    }

    void stop() {
        if (proxy != null) {
            try { proxy.getClass().getMethod("closeProxy").invoke(proxy); } catch (Throwable ignored) {}
            proxy = null;
        }
    }

    /** Name of the phone connected over hands-free, or "" when none/unreadable. */
    String deviceName() {
        if (proxy == null) return "";
        try {
            java.util.List<?> l = (java.util.List<?>) proxy.getClass().getMethod("getConnectedDevices").invoke(proxy);
            Log.i(TAG, "hf connected devices: " + (l == null ? -1 : l.size()));
            if (l != null && !l.isEmpty()) {
                String n = ((android.bluetooth.BluetoothDevice) l.get(0)).getName();
                return n == null ? "" : n;
            }
        } catch (Throwable t) { Log.w(TAG, "device name unavailable", t); }
        return "";
    }

    boolean ready() { return proxy != null; }

    /** Returns null on success, or a short reason. */
    String dial(String number) {
        number = number.trim();
        if (number.isEmpty()) return "Enter a number";
        if (proxy != null) {
            try {
                Object ok = proxy.getClass().getMethod("dial", String.class).invoke(proxy, number);
                if (Boolean.TRUE.equals(ok)) return null;
                return "The phone didn't accept the call";
            } catch (Throwable t) {
                Log.w(TAG, "dial via hands-free failed", t);
            }
        }
        try {
            ctx.startActivity(new Intent(Intent.ACTION_CALL, Uri.fromParts("tel", number, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return null;
        } catch (Throwable t) {
            return "No phone connected";
        }
    }
}
