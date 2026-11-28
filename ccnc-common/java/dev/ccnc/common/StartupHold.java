package dev.ccnc.common;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Work that can wait until the car's start-up screen (and ccNC's chooser over it) has gone: weather, now playing,
 * the EV card, fresh profile photos. At start-up all of it competes with the chooser for a head unit that is already
 * dropping frames; held, it runs a moment after the chooser closes. Never held longer than {@link #MAX_MS}.
 * Main thread only.
 */
public final class StartupHold {
    private StartupHold() {}

    static final String TAG = "ccncStartup";
    static final long MAX_MS = 60000, AFTER_MS = 800;

    private static final Handler ui = new Handler(Looper.getMainLooper());
    private static final Map<String, Runnable> queue = new LinkedHashMap<String, Runnable>();
    private static boolean holding;

    private static final Runnable timeout = new Runnable() { @Override public void run() { release("still held after " + MAX_MS / 1000 + " s"); } };

    public static boolean holding() { return holding; }

    /** Starts holding (a fresh start where the start-up screen may still come). */
    public static void hold() {
        if (holding) return;
        holding = true;
        ui.postDelayed(timeout, MAX_MS);
    }

    /** Runs {@code r} now, or once the hold ends; a later call with the same key replaces a waiting one. */
    public static void run(String key, Runnable r) {
        if (!holding) { r.run(); return; }
        queue.remove(key);
        queue.put(key, r);
    }

    /** Ends the hold: the waiting work runs {@link #AFTER_MS} later, in the order it came. */
    public static void release(final String why) {
        if (!holding) return;
        ui.removeCallbacks(timeout);
        ui.postDelayed(new Runnable() { @Override public void run() {
            if (!holding) return;
            holding = false;
            Log.i(TAG, "start-up hold over (" + why + "): " + queue.size() + " waiting");
            Runnable[] rs = queue.values().toArray(new Runnable[0]);
            queue.clear();
            for (Runnable r : rs) try { r.run(); } catch (Throwable t) { Log.w(TAG, "held work", t); }
        } }, AFTER_MS);
    }
}
