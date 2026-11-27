package dev.ccnc.home;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.util.Log;

import dev.ccnc.common.Profiles;
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
 * With ccNC's SystemUI step (LivePatch "consent", see dev.ccnc.common.StartupScreen) it hands over to ccNC Profile's
 * start-up chooser instead, which is shown on top of the stock screen and does both through the stock screen's own
 * checks. Without that step, or with a ccNC Profile that has no chooser, everything below is as before.
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

    @Override public void onReceive(Context c, Intent i) {
        if (i != null && ACC.equals(i.getAction())) again(c); else run(c);
    }

    private static long lastAgain;

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
            if (StartupScreen.startChooser(c)) Log.i(TAG, "start-up chooser started after an ACC change (soft start)");
        } }, "ccncStartupAgain").start();
    }

    static synchronized void run(Context ctx) {
        if (started) return;
        started = true;
        if (SystemClock.elapsedRealtime() > 5 * 60 * 1000L) return;   // not a fresh start (Home restarted later)
        final Context c = ctx.getApplicationContext();
        new Thread(new Runnable() { @Override public void run() {
            if (StartupScreen.patchActive()) {
                // Home can be up before SystemUI has made its start-up screen: give it a moment to exist.
                long wait = SystemClock.elapsedRealtime() + 30000;
                while (!"true".equals(prop("sys.boot.show_screen")) && SystemClock.elapsedRealtime() < wait) SystemClock.sleep(300);
                if (StartupScreen.pending() && StartupScreen.startChooser(c)) {
                    Log.i(TAG, "start-up chooser started (ccNC Profile)");
                    return;
                }
            }
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
