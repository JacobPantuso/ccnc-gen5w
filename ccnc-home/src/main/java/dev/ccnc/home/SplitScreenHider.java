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
     * Off the main thread (the full list is ~1000 calls into the service): the whole list once per
     * process (again 30 s later in case the service was still starting), and on later resumes only the
     * cheap "button off" request.
     */
    void apply() {
        if (worker == null) {
            thread = new android.os.HandlerThread("ccncSplit");
            thread.start();
            worker = new Handler(thread.getLooper());
        }
        if (!listed) {
            listed = true;
            worker.post(new Runnable() { @Override public void run() { run1(true); } });
            worker.postDelayed(new Runnable() { @Override public void run() { run1(true); } }, 30000);
        } else {
            worker.post(new Runnable() { @Override public void run() { run1(false); } });
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
    private boolean call(String name, Object... args) {
        try {
            for (Method m : mgr.getClass().getMethods()) {
                if (!m.getName().equals(name) || m.getParameterTypes().length != args.length) continue;
                m.invoke(mgr, args);
                return true;
            }
            Log.w(TAG, "no method " + name + "/" + args.length);
        } catch (Throwable t) {
            Log.w(TAG, name + " failed", t);
        }
        return false;
    }
}
