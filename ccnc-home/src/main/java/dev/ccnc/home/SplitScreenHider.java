package dev.ccnc.home;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

/**
 * Hides the Gen5W right-screen (split screen) tab. ccNC has no split screen.
 *
 * The tab lives in com.mobis.splitscreen.right (SplitScreenHome), which also draws the volume and
 * media OSDs and the call banners, so the package stays enabled. The service's own API
 * (SplitScreenManager, bootclasspath automotive.jar) has a per-activity "button off" list: when a
 * listed activity is resumed the tab is hidden. We put every activity of every installed app on it,
 * turn the panel itself off the way the stock General Settings does, and hide the button now.
 * The list is in memory in the service, so this runs on every Home start and again later.
 */
final class SplitScreenHider {

    static final String TAG = "ccncSplit";

    private final Context ctx;
    private static android.os.HandlerThread thread;
    private static Handler worker;
    private static boolean listed;   // the per-activity list is in the service's memory: once per Home process
    private Object mgr;

    SplitScreenHider(Context ctx) { this.ctx = ctx.getApplicationContext(); }

    /**
     * Off the main thread. The cheap part (panel off, tab hidden) at once, every time. The whole list (every
     * activity of every app from the package manager, then ~1000 calls into the service, each logged by it)
     * once per process and again 30 s later in case the service was still starting; at start-up it waits for
     * the start-up screen and ccNC's chooser to go (StartupHold), as it was one of the biggest loads then.
     * Main thread only.
     */
    void apply() {
        if (worker == null) {
            thread = new android.os.HandlerThread("ccncSplit", android.os.Process.THREAD_PRIORITY_BACKGROUND);
            thread.start();
            worker = new Handler(thread.getLooper());
        }
        worker.post(new Runnable() { @Override public void run() { run1(false); } });
        if (!listed) {
            listed = true;
            dev.ccnc.common.StartupHold.run("splitList", new Runnable() { @Override public void run() {
                worker.post(new Runnable() { @Override public void run() { run1(true); } });
                worker.postDelayed(new Runnable() { @Override public void run() { run1(true); } }, 30000);
            } });
        }
    }

    private void run1(boolean full) {
        try {
            if (mgr == null) {
                Class<?> c = Class.forName("com.hkmc.split.manager.SplitScreenManager");
                mgr = c.getMethod("getInstance", Context.class).invoke(null, ctx);
            }
            call("requestSplitScreenOn", false, true);
            call("requestSplitButton", false);
            if (!full) return;
            int n = 0;
            for (String cls : activityClasses()) if (call("addBtnOffScreen", cls)) n++;
            Log.i(TAG, "split screen: panel off, tab hidden, " + n + " activity classes on the button-off list");
        } catch (Throwable t) {
            Log.w(TAG, "split screen service not reachable", t);
        }
    }

    private List<String> activityClasses() {
        List<String> out = new ArrayList<String>();
        PackageManager pm = ctx.getPackageManager();
        for (PackageInfo p : pm.getInstalledPackages(PackageManager.GET_ACTIVITIES)) {
            if (p.activities == null) continue;
            for (ActivityInfo a : p.activities) out.add(a.name);
        }
        return out;
    }


    /** Calls the manager method with this name and argument count; false if it isn't there or fails. */
    private final java.util.HashMap<String, Method> methods = new java.util.HashMap<String, Method>();

    private boolean call(String name, Object... args) {
        try {
            String key = name + "/" + args.length;
            Method found = methods.get(key);
            if (found == null) {
                for (Method m : mgr.getClass().getMethods()) {
                    if (m.getName().equals(name) && m.getParameterTypes().length == args.length) { found = m; break; }
                }
                if (found != null) methods.put(key, found);
            }
            if (found != null) {
                found.invoke(mgr, args);
                return true;
            }
            Log.w(TAG, "no method " + key);
        } catch (Throwable t) {
            Log.w(TAG, name + " failed", t);
        }
        return false;
    }
}
