package dev.ccnc.common;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;
import android.view.KeyEvent;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The car's own routes for opening a media source and for its transport keys, for apps that are not system apps.
 * Everything is by reflection on the unit's mode manager ({@code com.hkmc.mode.ModeManager}, boot classpath) and
 * the Bluetooth app's AVRCP service; off the car every call just reports failure.
 *
 * <ul>
 * <li><b>Open a source</b>: {@code ModeManager.launchMode(mode, true)}, the call the stock home and the stock
 *     media list make. The mode service (system) then starts the source's own service with its SHORTCUT action,
 *     so it works for screens that are not exported (Bluetooth Audio's BtMusicMainActivity / MusicService).</li>
 * <li><b>Next / previous</b>: {@code ModeManager.handleKeyForService(action, keyCode, flags, deviceId, repeat,
 *     viewHandled)} with the panel's SEEK keys. The mode service hands the key to the key listener of the
 *     <i>current</i> source only (ModeKeyDispatcher.sendKeyEventToListener -> getCurrentRealMode), which is how
 *     every stock player takes its keys: none of them listens for Android media buttons.</li>
 * <li><b>Bluetooth play / pause</b>: the key pipeline has no play/pause key, so this goes to the AVRCP service
 *     the stock player itself uses ({@code IBluetoothAvrcpController.sendBTAudioCommand(MOBIS_PLAY_PAUSE, 0)} in
 *     com.android.bluetooth; exported, no permission on that call). The service ignores it while another
 *     source is playing.</li>
 * </ul>
 * {@code ModeManager.generateKeyEvent} is not used: it injects through InputManager under the caller's uid, and
 * only events from INJECT_EVENTS holders reach the car's key pipeline.
 */
public final class CarMedia {
    private CarMedia() {}

    private static final String TAG = "ccncCarMedia";

    /** ModeConstants.ModeType.BLUETOOTH. */
    public static final int MODE_BLUETOOTH = 0x36;
    public static final String BT_AUDIO_PKG = "com.daudio.av.app.btaudio.ui";

    /** Results of {@link #launchMode}. */
    public static final int LAUNCHED = 0, NOT_AVAILABLE = 1, FAILED = 2;

    /** HKMC key codes of the panel's SEEK keys: up = next track / seek up, down = previous track / seek down. */
    private static final int KEY_SEEK_UP = 0xe1, KEY_SEEK_DOWN = 0xe2;
    /** ModeConstants.HKMCInputDevice.FRONTPANEL. */
    private static final int DEVICE_FRONT_PANEL = 1;

    /** BluetoothAvrcpController.MOBIS_PLAY_PAUSE. */
    private static final int MOBIS_PLAY_PAUSE = 3;
    private static final String BLUETOOTH_PKG = "com.android.bluetooth";
    private static final String AVRCP_IFACE = "com.broadcom.bt.avrcp.IBluetoothAvrcpController";
    private static final String BROADCOM_JAR = "/system/framework/com.broadcom.bt.jar";

    /** One thread for every call into the car, so the calls stay in order and off the UI thread. */
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();

    private static Object modeManager;
    private static Class<?> modeManagerClass;

    private static synchronized Object modeManager(Context ctx) throws Exception {
        if (modeManager == null) {
            Class<?> c = Class.forName("com.hkmc.mode.ModeManager");
            Context app = ctx.getApplicationContext();
            modeManager = c.getConstructor(Context.class).newInstance(app != null ? app : ctx);
            modeManagerClass = c;
        }
        return modeManager;
    }

    /** The source that has the audio now (ModeConstants.ModeType), or -1 when the mode service can't be read. */
    public static int currentMode(Context ctx) {
        try {
            Object mm = modeManager(ctx);
            return (Integer) modeManagerClass.getMethod("getCurrentMode").invoke(mm);
        } catch (Throwable t) {
            return -1;
        }
    }

    /**
     * Switches to a source and shows its stock screen. {@link #NOT_AVAILABLE}: the car says the source can't be
     * used now (for Bluetooth: no phone connected for audio). {@link #FAILED}: no mode service, or it refused.
     */
    public static int launchMode(Context ctx, int mode) {
        try {
            Object mm = modeManager(ctx);
            modeManagerClass.getMethod("launchMode", int.class, boolean.class)
                    .invoke(mm, Integer.valueOf(mode), Boolean.TRUE);
            Log.i(TAG, "launchMode(0x" + Integer.toHexString(mode) + ") sent");
            return LAUNCHED;
        } catch (InvocationTargetException e) {
            // The mode service throws IllegalArgumentException("Not available mode ...") for a source that isn't ready.
            Throwable cause = e.getCause();
            Log.w(TAG, "launchMode(0x" + Integer.toHexString(mode) + ") refused: " + cause);
            return cause instanceof IllegalArgumentException ? NOT_AVAILABLE : FAILED;
        } catch (Throwable t) {
            Log.w(TAG, "launchMode(0x" + Integer.toHexString(mode) + ") failed", t);
            return FAILED;
        }
    }

    /**
     * Next / previous on whatever source is playing: one press and release of the panel's SEEK key, given to that
     * source's key listener. False when the mode service isn't there (the caller may fall back to a media button).
     */
    public static boolean seek(Context ctx, final boolean next) {
        final Object mm;
        final Method m;
        try {
            mm = modeManager(ctx);
            m = modeManagerClass.getMethod("handleKeyForService",
                    int.class, int.class, int.class, int.class, int.class, boolean.class);
        } catch (Throwable t) {
            Log.w(TAG, "seek: no mode manager", t);
            return false;
        }
        IO.execute(new Runnable() {
            @Override public void run() {
                int key = next ? KEY_SEEK_UP : KEY_SEEK_DOWN;
                try {
                    // (action, keyCode, flags, deviceId, repeatCount, isViewHandled)
                    m.invoke(mm, KeyEvent.ACTION_DOWN, key, 0, DEVICE_FRONT_PANEL, 0, Boolean.FALSE);
                    m.invoke(mm, KeyEvent.ACTION_UP, key, 0, DEVICE_FRONT_PANEL, 0, Boolean.FALSE);
                    Log.i(TAG, "seek " + (next ? "up" : "down") + " sent");
                } catch (Throwable t) {
                    Log.w(TAG, "seek failed", t);
                }
            }
        });
        return true;
    }

    // ---- Bluetooth play / pause (AVRCP) ----

    private static IBinder avrcp;
    private static boolean avrcpBinding;
    private static boolean avrcpPending;
    private static Context avrcpCtx;

    private static final ServiceConnection AVRCP = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder service) {
            boolean send;
            synchronized (CarMedia.class) {
                avrcp = service;
                avrcpBinding = false;
                send = avrcpPending;
                avrcpPending = false;
            }
            Log.i(TAG, "AVRCP service connected: " + name);
            if (send) sendPlayPause(service);
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            synchronized (CarMedia.class) { avrcp = null; avrcpBinding = false; }
        }
    };

    /** Connects to the Bluetooth app's AVRCP service ahead of the first press. Safe to call again. */
    public static synchronized boolean btConnect(Context ctx) {
        if (avrcp != null || avrcpBinding) return true;
        try {
            Context app = ctx.getApplicationContext() != null ? ctx.getApplicationContext() : ctx;
            Intent i = new Intent(AVRCP_IFACE).setPackage(BLUETOOTH_PKG);
            boolean ok = app.bindService(i, AVRCP, Context.BIND_AUTO_CREATE);
            Log.i(TAG, "AVRCP bind = " + ok);
            if (!ok) return false;
            avrcpCtx = app;
            avrcpBinding = true;
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "AVRCP bind failed", t);
            return false;
        }
    }

    /** Drops the AVRCP connection made by {@link #btConnect}. */
    public static synchronized void btDisconnect() {
        if (avrcpCtx != null) {
            try { avrcpCtx.unbindService(AVRCP); } catch (Throwable ignored) { }
        }
        avrcpCtx = null; avrcp = null; avrcpBinding = false; avrcpPending = false;
    }

    /**
     * Play / pause on the phone playing over Bluetooth. False when the AVRCP service can't be reached at all.
     * Call only while Bluetooth Audio is the source ({@link #currentMode} == {@link #MODE_BLUETOOTH}).
     */
    public static boolean btPlayPause(Context ctx) {
        IBinder b;
        synchronized (CarMedia.class) {
            b = avrcp;
            if (b == null) {
                if (!btConnect(ctx)) return false;
                avrcpPending = true;                    // sent from onServiceConnected
                return true;
            }
        }
        sendPlayPause(b);
        return true;
    }

    private static void sendPlayPause(final IBinder binder) {
        IO.execute(new Runnable() {
            @Override public void run() {
                try {
                    ClassLoader cl = broadcomLoader();
                    Object svc = Class.forName(AVRCP_IFACE + "$Stub", true, cl)
                            .getMethod("asInterface", IBinder.class).invoke(null, binder);
                    Object r = Class.forName(AVRCP_IFACE, true, cl)
                            .getMethod("sendBTAudioCommand", int.class, int.class)
                            .invoke(svc, MOBIS_PLAY_PAUSE, 0);
                    Log.i(TAG, "AVRCP play/pause sent, result " + r);
                } catch (Throwable t) {
                    Log.w(TAG, "AVRCP play/pause failed", t);
                }
            }
        });
    }

    private static ClassLoader broadcomLoader;

    /**
     * The loader that has the unit's Bluetooth library: the app's own when its manifest names
     * {@code <uses-library android:name="com.broadcom.bt" android:required="false"/>}, else the jar itself.
     */
    private static synchronized ClassLoader broadcomLoader() {
        if (broadcomLoader == null) {
            ClassLoader own = CarMedia.class.getClassLoader();
            try {
                Class.forName(AVRCP_IFACE, false, own);
                broadcomLoader = own;
            } catch (Throwable notInApp) {
                broadcomLoader = new dalvik.system.PathClassLoader(BROADCOM_JAR, own);
            }
        }
        return broadcomLoader;
    }
}
