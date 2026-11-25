package dev.ccnc.home;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import java.lang.reflect.Method;

/**
 * Starts the phone's now-playing feed (title, artist, artwork, play state) on a CarPlay session.
 *
 * The stock CarPlay app subscribes with Iap2SvcNowPlayingStart() only for USB sessions: for a
 * Wireless transport it skips that block (DiPO e/b.e(I)), so over wireless CarPlay the unit never
 * hears what is playing. That leaves the cluster, the home widget and our Media app blank. The
 * subscription itself is global on the iAP2 server and DiPO's own listener already handles the
 * notifications that follow, so asking for it from here is enough.
 *
 * The iAP2 interface is a bootclasspath class (com.tcc.iap2.jar), used by reflection.
 */
final class Iap2NowPlaying {

    static final String TAG = "ccncNowPlaying";
    private static final ComponentName SERVER = new ComponentName("com.tcc.iap2server", "com.tcc.iap2server.Iap2Service");

    private final Context ctx;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean running;
    private boolean active;

    Iap2NowPlaying(Context ctx) { this.ctx = ctx.getApplicationContext(); }

    /** Call whenever the CarPlay state is known; (re)starts the subscription each time CarPlay comes up. */
    void carPlay(boolean on) {
        if (on == active) return;
        active = on;
        handler.removeCallbacksAndMessages(null);
        if (!on) return;
        // The session's iAP2 side comes up a little after the screen does: ask a few times (it is idempotent).
        int[] delays = {1500, 5000, 12000};
        for (int d : delays) handler.postDelayed(new Runnable() { @Override public void run() { if (active) request(); } }, d);
    }

    private void request() {
        if (running) return;
        running = true;
        final ServiceConnection[] holder = new ServiceConnection[1];
        ServiceConnection c = new ServiceConnection() {
            @Override public void onServiceConnected(ComponentName n, IBinder b) {
                try {
                    Class<?> stub = Class.forName("com.tcc.iap2.IIap2Service$Stub");
                    Object svc = stub.getMethod("asInterface", IBinder.class).invoke(null, b);
                    Method m = svc.getClass().getMethod("Iap2SvcNowPlayingStart");
                    Object r = m.invoke(svc);
                    Log.i(TAG, "Iap2SvcNowPlayingStart -> " + r);
                } catch (Throwable t) {
                    Log.w(TAG, "could not start now-playing", t);
                } finally {
                    running = false;
                    try { ctx.unbindService(holder[0]); } catch (Throwable ignored) {}
                }
            }
            @Override public void onServiceDisconnected(ComponentName n) { running = false; }
        };
        holder[0] = c;
        try {
            if (!ctx.bindService(new Intent().setComponent(SERVER), c, Context.BIND_AUTO_CREATE)) {
                Log.w(TAG, "iAP2 server can't be bound");
                running = false;
            }
        } catch (Throwable t) {
            Log.w(TAG, "iAP2 bind failed", t);
            running = false;
        }
    }
}
