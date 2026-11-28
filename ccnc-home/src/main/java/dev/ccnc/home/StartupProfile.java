package dev.ccnc.home;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.util.Log;

import dev.ccnc.common.ProfileCache;
import dev.ccnc.common.Profiles;
import dev.ccnc.common.StartupChooser;
import dev.ccnc.common.StartupHold;
import dev.ccnc.common.StartupScreen;

/**
 * ccNC Profile's "Default Profile" and "Auto-Advance" on the stock start-up profile screen (SystemUI's ConsentScreen:
 * the agreement, the three profiles and Confirm), without changing SystemUI. Once per start, when that screen is
 * ready (sys.boot.enabled_screen=true), it:
 *   1. selects the default profile with the screen's own broadcast CHANGE_FOCUS_CONSENT (userType, fromWhere=0),
 *      unless it's "Last Used" or the profile has a password (the car asks for it after Confirm anyway);
 *   2. after the auto-advance time, confirms with HIDE_CONSENT_SCREEN, exactly what Confirm does.
 * Auto-advance only acts here once the owner chose a value in Setup (the stock screen keeps its own 10 s timer
 * otherwise), and only for values under 10 s; Off (-1) leaves it alone too. Runs from Home's start and from BOOT_COMPLETED,
 * whichever comes first, and only within the first minutes after the unit starts.
 *
 * With ccNC's SystemUI step (LivePatch "consent", see dev.ccnc.common.StartupScreen) it opens ccNC's start-up chooser
 * instead (dev.ccnc.common.StartupChooser), shown on top of the stock screen; it does both through the stock screen's
 * own checks. The chooser runs here in Home's process, which is already up, with Home's copy of ccNC Profile's photos
 * and settings (ProfileCache), so no other app has to start first; ccNC Profile's service is only the fallback.
 * While the start-up screen may still come, Home holds its background work (StartupHold) so the chooser gets the
 * head unit to itself. Without the SystemUI step everything below is as before.
 */
public final class StartupProfile extends BroadcastReceiver {
    static final String TAG = "ccncStartup";
    static final String FOCUS = "com.mobis.intent.action.CHANGE_FOCUS_CONSENT";
    static final String HIDE = "com.mobis.intent.action.HIDE_CONSENT_SCREEN";
    static final int STOCK_AUTO = 10;
    /**
     * Early confirm stays off until ccNC can check reverse gear / the rear camera itself: HIDE_CONSENT_SCREEN calls
     * ConsentScreen.confirm() directly, skipping the checks the stock 10 s timer makes (early camera, reverse, ACC,
     * valet, locale dialog). Until then only the default profile is selected; the stock timer or Confirm continues.
     */
    static final boolean EARLY_CONFIRM = false;
    private static boolean started;

    static final String ACC = "com.hkmc.intent.action.ACTION_ACC_STATUS_CHANGED";

    /** Emulator preview, debuggable builds only: am broadcast -n dev.ccnc.home/.StartupProfile -a dev.ccnc.home.STARTUP_PREVIEW */
    static final String PREVIEW = "dev.ccnc.home.STARTUP_PREVIEW";

    @Override public void onReceive(Context c, Intent i) {
        String a = i == null ? null : i.getAction();
        if (ACC.equals(a)) again(c);
        else if (PREVIEW.equals(a)) preview(c);
        else run(c);
    }

    private static void preview(final Context c) {
        StartupHold.hold();
        if (!StartupChooser.open(c, true, DATA, new Runnable() { @Override public void run() { StartupHold.release("preview closed"); } }))
            StartupHold.release("preview not opened");
    }

    private static long lastAgain;
    private static final android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());

    /** Home's copy of ccNC Profile's photos and settings (ccNC Profile isn't woken at start-up). */
    static final StartupChooser.Data DATA = new StartupChooser.Data() {
        @Override public android.graphics.Bitmap photo(Context c, int id) { return ProfileCache.photo(c, id, false); }
        @Override public int defaultProfile(Context c) { return ProfileCache.setting(c, "default_profile", -1, false); }
        @Override public int autoAdvance(Context c) { return ProfileCache.setting(c, "auto_advance", 3, false); }
    };

    /** Opens the chooser in Home's process (from any thread); ccNC Profile's service if that can't be done. */
    static void openChooser(final Context c, final String how) {
        main.post(new Runnable() { @Override public void run() {
            if (StartupChooser.running()) { Log.i(TAG, "start-up chooser already open (" + how + ")"); return; }
            StartupHold.hold();
            boolean ok;
            try {
                ok = StartupChooser.open(c, false, DATA, new Runnable() { @Override public void run() {
                    StartupHold.release("start-up chooser closed");
                    main.postDelayed(new Runnable() { @Override public void run() { ProfileCache.refresh(c); } }, 5000);
                } });
            } catch (Throwable t) {
                Log.w(TAG, "start-up chooser in Home", t);
                ok = false;
            }
            if (ok) { Log.i(TAG, "start-up chooser opened in Home (" + how + ")"); return; }
            StartupHold.release("start-up chooser not opened here");
            if (StartupScreen.startChooser(c)) Log.i(TAG, "start-up chooser started in ccNC Profile (" + how + ")");
        } });
    }

    /**
     * A soft start (the car switched off and on while the head unit only slept): nothing restarts, but SystemUI makes
     * its start-up screen again on the same ACC change (ConsentScreen sets sys.boot.confirm_screen=false). Wait a
     * moment for that screen, then open ccNC's chooser over it, as at a full start. Only with ccNC's SystemUI step.
     */
    static synchronized void again(Context ctx) {
        long now = SystemClock.elapsedRealtime();
        if (now - lastAgain < 15000) return;                          // one ACC change can arrive more than once
        lastAgain = now;
        final Context c = ctx.getApplicationContext();
        new Thread(new Runnable() { @Override public void run() {
            if (!StartupScreen.patchActive()) return;
            long wait = SystemClock.elapsedRealtime() + 10000;
            while (!StartupScreen.pending() && SystemClock.elapsedRealtime() < wait) SystemClock.sleep(250);
            if (!StartupScreen.pending()) return;                      // ACC change without a new start-up screen
            openChooser(c, "after an ACC change: soft start");
        } }, "ccncStartupAgain").start();
    }

    static synchronized void run(Context ctx) {
        if (started) return;
        started = true;
        final Context c = ctx.getApplicationContext();
        if (SystemClock.elapsedRealtime() > 5 * 60 * 1000L) {         // not a fresh start (Home restarted later)
            ProfileCache.refresh(c);
            return;
        }
        StartupHold.hold();   // (main thread) until it's known whether the start-up screen comes, then until it's gone
        new Thread(new Runnable() { @Override public void run() {
            if (StartupScreen.patchActive()) {
                // Home can be up before SystemUI has made its start-up screen: give it a moment to exist.
                long wait = SystemClock.elapsedRealtime() + 30000;
                while (!"true".equals(prop("sys.boot.show_screen")) && SystemClock.elapsedRealtime() < wait) SystemClock.sleep(300);
                if (StartupScreen.pending()) {
                    openChooser(c, "start-up");
                    return;
                }
            }
            main.post(new Runnable() { @Override public void run() { StartupHold.release("no ccNC start-up chooser"); } });
            int def = Profiles.setting(c, "default_profile", -1);
            int auto = Profiles.setting(c, "auto_advance", 3);
            boolean early = EARLY_CONFIRM && Profiles.setting(c, "auto_advance_custom", 0) == 1 && auto >= 0 && auto < STOCK_AUTO;
            if (def < 0 && !early) return;                              // nothing to do: all stock
            long until = SystemClock.elapsedRealtime() + 120000;
            while (!"true".equals(prop("sys.boot.enabled_screen"))) {
                if (SystemClock.elapsedRealtime() > until) { Log.i(TAG, "start-up screen never became ready"); return; }
                SystemClock.sleep(400);
            }
            SystemClock.sleep(600);                                    // let the screen finish laying out its buttons
            if ("true".equals(prop("sys.boot.confirm_screen"))) { Log.i(TAG, "start-up screen already confirmed"); return; }
            if (def >= 0 && def < Profiles.COUNT) {
                if (Profiles.needsPassword(c, def)) Log.i(TAG, "default profile " + def + " has a password: left on the key's profile");
                else {
                    c.sendBroadcast(new Intent(FOCUS).putExtra("userType", def).putExtra("fromWhere", 0));
                    Log.i(TAG, "selected default profile " + def);
                }
            }
            if (early) {
                SystemClock.sleep(Math.max(0, auto) * 1000L + 300);
                c.sendBroadcast(new Intent(HIDE));
                Log.i(TAG, "confirmed the start-up screen after " + auto + " s");
            }
        } }, "ccncStartup").start();
    }

    static String prop(String key) {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            return (String) sp.getMethod("get", String.class, String.class).invoke(null, key, "");
        } catch (Throwable t) { return ""; }
    }
}
