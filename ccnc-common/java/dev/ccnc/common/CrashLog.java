package dev.ccnc.common;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * When a ccNC app dies, shows the exception on screen for a few seconds (so it can be photographed in
 * the car) and saves the stack trace: in the app's files, on /sdcard, and on any USB stick under
 * "ccNC reports/crash-&lt;package&gt;.txt". Then the normal crash handling carries on.
 */
public final class CrashLog {
    private CrashLog() {}

    private static boolean installed;

    public static synchronized void install(Context c) {
        if (installed || c == null) return;
        installed = true;
        final Context app = c.getApplicationContext() != null ? c.getApplicationContext() : c;
        final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override public void uncaughtException(Thread t, Throwable e) {
                try {
                    StringWriter sw = new StringWriter();
                    e.printStackTrace(new PrintWriter(sw));
                    String trace = app.getPackageName() + " crashed in thread " + t.getName() + "\n" + sw;
                    save(app, trace);
                    show(app, e);
                } catch (Throwable ignored) {
                }
                if (prev != null) prev.uncaughtException(t, e);
            }
        });
    }

    private static void save(Context app, String trace) {
        String name = "crash-" + app.getPackageName() + ".txt";
        write(new File(app.getFilesDir(), name), trace);
        write(new File("/sdcard", "ccnc-" + name), trace);
        for (String u : new String[]{"/storage/usb0", "/storage/usb1", "/storage/usb2"}) {
            File d = new File(u);
            if (!d.isDirectory()) continue;
            File dir = new File(d, "ccNC reports");
            dir.mkdirs();
            write(new File(dir, name), trace);
        }
    }

    private static void write(File f, String s) {
        try {
            FileOutputStream o = new FileOutputStream(f, true);
            o.write((s + "\n").getBytes("UTF-8"));
            o.close();
        } catch (Throwable ignored) {
        }
    }

    /** A toast from a throwaway thread with its own looper: the crashing one can't show it. */
    private static void show(final Context app, Throwable e) throws InterruptedException {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) root = root.getCause();
        StringBuilder b = new StringBuilder("ccNC crash: ").append(root);
        StackTraceElement[] st = root.getStackTrace();
        for (int i = 0; i < Math.min(3, st.length); i++) b.append("\n  at ").append(st[i]);
        final String msg = b.length() > 380 ? b.substring(0, 380) : b.toString();
        Thread th = new Thread() {
            @Override public void run() {
                Looper.prepare();
                final Looper l = Looper.myLooper();
                Toast.makeText(app, msg, Toast.LENGTH_LONG).show();
                new Handler(l).postDelayed(new Runnable() { @Override public void run() { l.quit(); } }, 6000);
                Looper.loop();
            }
        };
        th.start();
        th.join(7000);
    }
}
