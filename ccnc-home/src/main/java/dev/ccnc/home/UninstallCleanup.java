package dev.ccnc.home;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;

/**
 * Finishes uninstalling ccNC for Gen5W after the restart. The package's uninstall keeps ccNC Climate and ccNC Home
 * until then, because LivePatch's patched files stay mounted until the restart and the car's climate/seat buttons
 * would otherwise open nothing. It leaves /data/ccnc/uninstall-pending; at the next boot (or when Home starts), once
 * none of ccNC's patched files is mounted any more, this removes Climate, /data/ccnc and Home itself through NEMESIS root.
 * Installing ccNC again before the restart deletes the mark (scripts/pre.sh).
 */
public final class UninstallCleanup extends BroadcastReceiver {
    static final String TAG = "ccncCleanup";
    static final String MARK = "/data/ccnc/uninstall-pending";
    private static final String[] TARGETS = {
            "/system/framework/automotive-service.odex", "/system/app/RightScreenHome.odex",
            "/system/app/HKMC_Climate.odex", "/system/app/WelcomeGoodbye.apk"};
    private static boolean started;

    @Override public void onReceive(Context c, Intent i) { run(c); }

    static synchronized void run(Context c) {
        if (started || !new File(MARK).exists()) return;
        String mounts = read("/proc/mounts");
        if (mounts == null) return;
        for (String t : TARGETS) if (mounts.contains(" " + t + " ")) { Log.i(TAG, t + " still mounted: not restarted yet"); return; }
        started = true;
        new Thread(new Runnable() { @Override public void run() {
            Log.i(TAG, "removing ccNC Climate and Home after the uninstall");
            String r = dev.ccnc.common.DevMenu.root(
                    "pm uninstall dev.ccnc.climate; rm -rf /data/ccnc; pm uninstall dev.ccnc.home", 60000);
            Log.i(TAG, r);   // only reached if Home is still here: the cleanup didn't finish
        } }, "ccncCleanup").start();
    }

    private static String read(String path) {
        try {
            FileInputStream in = new FileInputStream(path);
            java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
            byte[] b = new byte[8192];
            int n;
            while ((n = in.read(b)) > 0) o.write(b, 0, n);
            in.close();
            return o.toString();
        } catch (Throwable t) { return null; }
    }
}
