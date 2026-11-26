package dev.ccnc.home;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.util.Log;

import java.util.List;

/**
 * The car's NAV key opens ccNC Navigation and the MEDIA key's source list opens ccNC Media, without patching anything.
 *
 * The unit's mode service sends both keys as ordered broadcasts with no permission (NAVI_SHORTCUT, MEDIAGATE_SHORTCUT),
 * and every stock receiver sits at priority 0. This one is at 1000, so it runs first and stops the stock app opening.
 * It lives in Home because Home is never a "stopped" package (the broadcasts don't include stopped packages).
 * Removing ccNC, or setting ccnc_hardkeys = 0 (adb: settings put global ccnc_hardkeys 0), gives the stock keys back.
 * The MAP key stays stock: ccNC Navigation's Map tile opens the same stock map.
 */
public final class HardKeyReceiver extends BroadcastReceiver {
    static final String TAG = "ccncKeys";
    static final String NAVI = "com.hkmc.intent.action.NAVI_SHORTCUT";
    static final String MEDIAGATE = "com.mobis.intent.action.MEDIAGATE_SHORTCUT";
    static final String X_REASON = "com.hkmc.mode.EXTRA_SHORTCUT_REASON";
    static final String HARD_KEY = "dev.ccnc.action.HARD_KEY";
    /** ModeConstants.ShortcutReason: hardware key, long press, custom key, steering-wheel key. */
    static final int HWKEY = 3, HWLONGKEY = 0xe, HWKEY_CUSTOM = 0x12, HWKEY_SWRC = 0x13;
    static final String NAVI_PKG = "dev.ccnc.navi", MEDIA_PKG = "dev.ccnc.media";
    /** Receivers that only watch the NAV key (they open nothing); they still get a copy after we stop the broadcast. */
    static final String[] NAVI_WATCHERS = {"com.nemesis.manager", "com.hkmc.telematics.service", "com.mnsoft.navipopupservice"};

    @Override public void onReceive(Context c, Intent i) {
        if (!isOrderedBroadcast()) return;                        // a copy sent directly: not the key
        int reason = i.getIntExtra(X_REASON, -1);
        if (reason != HWKEY && reason != HWLONGKEY && reason != HWKEY_CUSTOM && reason != HWKEY_SWRC) return;
        if (!enabled(c)) return;
        String a = i.getAction();
        if (NAVI.equals(a)) {
            if (NAVI_PKG.equals(top(c))) return;                    // second press on ccNC Navigation: stock navigation
            if (!open(c, NAVI_PKG, i)) return;
            abortBroadcast();
            for (String p : NAVI_WATCHERS) {
                try { c.sendBroadcast(new Intent(i).setPackage(p)); } catch (Throwable ignored) { }
            }
            Log.i(TAG, "NAV key -> ccNC Navigation");
        } else if (MEDIAGATE.equals(a)) {
            if (MEDIA_PKG.equals(top(c))) {                         // the list is showing: close it, like stock
                abortBroadcast();
                try {
                    c.startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (Throwable ignored) { }
                Log.i(TAG, "MEDIA key on ccNC Media -> Home");
                return;
            }
            if (!open(c, MEDIA_PKG, i)) return;
            abortBroadcast();
            Log.i(TAG, "MEDIA key (source list) -> ccNC Media");
        }
    }

    static boolean enabled(Context c) {
        try { return !"0".equals(Settings.Global.getString(c.getContentResolver(), "ccnc_hardkeys")); }
        catch (Throwable t) { return true; }
    }

    /** Opens the ccNC app; false (stock path) if it isn't installed or won't start. */
    static boolean open(Context c, String pkg, Intent from) {
        try {
            c.startActivity(new Intent(HARD_KEY).setComponent(new ComponentName(pkg, pkg + ".MainActivity"))
                    .putExtra("key", from.getAction()).putExtra(X_REASON, from.getIntExtra(X_REASON, -1))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "can't open " + pkg + ": leaving the key to the stock app", t);
            return false;
        }
    }

    /** The package on screen (Home holds GET_TASKS). */
    static String top(Context c) {
        try {
            List<ActivityManager.RunningTaskInfo> t =
                    ((ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE)).getRunningTasks(1);
            return t.isEmpty() ? "" : t.get(0).topActivity.getPackageName();
        } catch (Throwable e) { return ""; }
    }
}
