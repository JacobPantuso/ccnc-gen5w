package dev.ccnc.common;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * ccNC developer menu: tap the clock in any ccNC top bar 7 times. A ccNC-styled popup with
 *   Status         ccNC packages and versions, brand, firmware, theme, the ccNC system patches (LivePatch mount +
 *                  the step's own result in /data/ccnc/livepatch; 1.x byte patches by md5), tools, uptime, memory, storage
 *   CPU usage      total and per-process CPU (from /proc), load, memory; refreshed every 2 s
 *   Compatibility  head unit, which system patches fit this firmware (a LivePatch step that installs is supported),
 *                  detected brand, car services and stock apps
 *   Export Logs    a summary of this car and ccNC plus the system logs, to "ccNC logs/<time>/" on the USB stick
 *                  (NEMESIS root reads and writes, ccNC removes personal information first, LogScrub); without root, a self-check
 *   Reboot         asks first (a ccNC confirm popup), then setprop sys.powerctl reboot through NEMESIS root
 * Root goes through the NEMESIS CLI: /data/ccnc/nemesis-d (non-SUID copy: the NEMESIS daemon, full root), else
 * /system/xbin/nemesis (SUID: a limited root when an app runs it). Both are tried; the popup says which worked.
 */
public final class DevMenu {
    private DevMenu() {}

    static final String TAG = "ccncDev";
    private static final String DIR = "/data/ccnc";
    private static final String[] NEMESIS = {DIR + "/nemesis-d", "/system/xbin/nemesis"};
    /**
     * The ccNC system patches: LivePatch step, name, target file, md5 of the 1.x byte-patched file ("" = none).
     * LivePatch (ccnc.gen5w 2.x) adapts each step to the firmware at install, so a step that installed is
     * supported; its tool records the result in /data/ccnc/livepatch/<step> (applied | unsupported).
     */
    private static final String[][] PATCHES = {
            {"volume", "Volume Strip Off", "/system/framework/automotive-service.odex", "f426a78f829b935005995b4f998863ca"},
            {"sideosd", "Side Popups Off", "/system/app/RightScreenHome.odex", "0b04470678194ab7e5bf626de3738001"},
            {"climate", "Climate Buttons", "/system/app/HKMC_Climate.odex", "364fe54acec3998d06ad53c44e7056d7"},
            {"welcome", "Startup Video", "/system/app/WelcomeGoodbye.apk", ""},
    };
    private static final String LP_DIR = DIR + "/livepatch";

    private static final int GOOD = 0xFF4CD08A, BAD = 0xFFE5484D, WARN = 0xFFF2C94C;

    // ---- 7 taps on the clock ----

    private static int taps;
    private static long firstTap;

    /** Call on each tap of the clock; the 7th within 3 s opens the menu. */
    public static void tap(Activity a) {
        long now = SystemClock.uptimeMillis();
        if (now - firstTap > 3000) { taps = 0; firstTap = now; }
        if (++taps >= 7) { taps = 0; show(a); }
    }

    // ---- the popup ----

    public static void show(final Activity a) {
        if (a == null || a.isFinishing()) return;
        init(a);
        new Menu(a).show();
    }

    private static final class Menu extends Dialog {
        final Activity a;
        final float dp;
        final Handler ui = new Handler(Looper.getMainLooper());
        LinearLayout content;
        TextView[] tabs;
        int tab = -1;
        final long openedAt = SystemClock.uptimeMillis();
        final Runnable cpuTick = new Runnable() { @Override public void run() { if (tab == 1) { cpu(); ui.postDelayed(this, 2000); } } };

        Menu(Activity a) {
            super(a, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen);
            this.a = a;
            dp = a.getResources().getDisplayMetrics().density;
            requestWindowFeature(Window.FEATURE_NO_TITLE);
            getWindow().setBackgroundDrawable(new ColorDrawable(0xD9000000));

            FrameLayout root = new FrameLayout(a);
            root.setSoundEffectsEnabled(false);
            // A tap outside closes it only once it has been open 3 s: fast taps on the clock or the menu land here too.
            root.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { if (outsideOk(openedAt)) dismiss(); } });

            LinearLayout panel = new LinearLayout(a);
            panel.setOrientation(LinearLayout.VERTICAL);
            panel.setClickable(true); // taps inside don't close it
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(0xF5121A23);
            bg.setCornerRadius(24 * dp);
            bg.setStroke(Math.max(1, (int) dp), Palette.BORDER);
            panel.setBackgroundDrawable(bg);
            panel.setPadding(px(24), px(18), px(24), px(18));

            // Header: title over the car line (brand, firmware), close on the right.
            LinearLayout head = new LinearLayout(a);
            head.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout titles = new LinearLayout(a);
            titles.setOrientation(LinearLayout.VERTICAL);
            titles.addView(text("Developer", 24, Palette.TEXT, Fonts.SEMIBOLD));
            String fw = prop("ro.build.product2");
            titles.addView(text(cap(Fonts.brand(a)) + "  ·  " + (fw == null ? "unknown firmware" : fw), 14, Palette.SUBTEXT, Fonts.REGULAR));
            head.addView(titles, new LinearLayout.LayoutParams(0, -2, 1f));
            TextView close = text("✕", 24, Palette.TEXT, Fonts.REGULAR);
            close.setPadding(px(12), 0, px(4), 0);
            close.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { Chrome.beep(Menu.this.a); dismiss(); } });
            head.addView(close);
            panel.addView(head);

            LinearLayout body = new LinearLayout(a);
            body.setPadding(0, px(14), 0, 0);
            // Left: menu.
            LinearLayout menu = new LinearLayout(a);
            menu.setOrientation(LinearLayout.VERTICAL);
            String[] names = {"Status", "CPU Usage", "Compatibility", "Export Logs", "Reboot"};
            tabs = new TextView[names.length];
            for (int i = 0; i < names.length; i++) {
                final int idx = i;
                TextView t = text(names[i], 20, Palette.TEXT, Fonts.REGULAR);
                t.setPadding(px(16), px(12), px(16), px(12));
                t.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { Chrome.beep(Menu.this.a); select(idx); } });
                tabs[i] = t;
                menu.addView(t, new LinearLayout.LayoutParams(px(200), -2));
            }
            // Footer at the bottom of the menu column: version and credit.
            menu.addView(new View(a), new LinearLayout.LayoutParams(1, 0, 1f));
            String hv = version("dev.ccnc.home");
            TextView credit = text("ccNC" + (hv == null ? "" : " " + hv) + "\nBuilt by Jacob Pantuso", 13, Palette.SUBTEXT, Fonts.REGULAR);
            credit.setPadding(px(16), 0, px(8), px(2));
            menu.addView(credit);
            LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(-2, -1);
            ml.rightMargin = px(16); // keep the selected item's background off the divider
            body.addView(menu, ml);
            View line = new View(a);
            line.setBackgroundColor(Palette.DIVIDER);
            body.addView(line, new LinearLayout.LayoutParams(Math.max(1, (int) dp), -1));
            // Right: the selected page.
            ScrollView sc = new ScrollView(a);
            content = new LinearLayout(a);
            content.setOrientation(LinearLayout.VERTICAL);
            content.setPadding(px(20), 0, px(8), 0);
            sc.addView(content);
            body.addView(sc, new LinearLayout.LayoutParams(0, -1, 1f));
            panel.addView(body, new LinearLayout.LayoutParams(-1, 0, 1f));

            FrameLayout.LayoutParams pl = new FrameLayout.LayoutParams(px(1040), px(400), Gravity.CENTER);
            root.addView(panel, pl);
            setContentView(root);
            select(0);
        }

        @Override public void dismiss() { ui.removeCallbacks(cpuTick); super.dismiss(); }

        int px(float v) { return Math.round(v * dp); }

        TextView text(String s, float size, int color, int face) {
            TextView t = new TextView(a);
            t.setText(s);
            t.setTextSize(size);
            t.setTextColor(color);
            t.setTypeface(Fonts.get(a, face));
            t.setIncludeFontPadding(true);
            t.setSoundEffectsEnabled(false);   // the click sounds through Chrome.beep only (else a double beep)
            return t;
        }

        void select(int i) {
            if (i == 4) { confirmReboot(); return; }
            tab = i;
            for (int k = 0; k < tabs.length; k++) {
                GradientDrawable g = new GradientDrawable();
                g.setCornerRadius(14 * dp);
                g.setColor(k == i ? Palette.alpha(Palette.ACCENT, 0x40) : 0);
                tabs[k].setBackgroundDrawable(g);
            }
            ui.removeCallbacks(cpuTick);
            content.removeAllViews();
            switch (i) {
                case 0: status(); break;
                case 1: cpu(); ui.postDelayed(cpuTick, 2000); break;
                case 2: compat(); break;
                case 3: checks(); break;
            }
        }

        // ---- rows ----

        void heading(String s) {
            TextView t = text(s, 15, Palette.ACCENT, Fonts.SEMIBOLD);
            t.setPadding(0, px(content.getChildCount() == 0 ? 0 : 14), 0, px(4));
            content.addView(t);
        }

        /** label ........ value, with an optional status dot (0 = none). */
        TextView row(String label, String value, int dot) {
            LinearLayout r = new LinearLayout(a);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setPadding(0, px(5), 0, px(5));
            if (dot != 0) {
                View d = new Dot(a, dot);
                LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(px(10), px(10));
                dl.rightMargin = px(10);
                r.addView(d, dl);
            }
            r.addView(text(label, 17, Palette.SUBTEXT, Fonts.REGULAR), new LinearLayout.LayoutParams(0, -2, 1f));
            TextView v = text(value == null ? "--" : value, 17, Palette.TEXT, Fonts.SEMIBOLD);
            v.setGravity(Gravity.RIGHT);
            r.addView(v, new LinearLayout.LayoutParams(0, -2, 1.3f));
            content.addView(r);
            return v;
        }

        TextView note(String s) {
            TextView t = text(s, 15, Palette.SUBTEXT, Fonts.REGULAR);
            t.setPadding(0, px(6), 0, px(6));
            content.addView(t);
            return t;
        }

        TextView button(String s, final View.OnClickListener l) {
            TextView b = text(s, 18, Palette.TEXT, Fonts.SEMIBOLD);
            b.setGravity(Gravity.CENTER);
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(18 * dp);
            g.setColor(Palette.alpha(Palette.ACCENT, 0x55));
            b.setBackgroundDrawable(g);
            b.setPadding(px(20), px(12), px(20), px(12));
            b.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { Chrome.beep(a); l.onClick(v); } });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.topMargin = px(10);
            content.addView(b, lp);
            return b;
        }

        /** Work off the UI thread, then show its result if the same page is still open. */
        void bg(final int page, final Runnable work, final Runnable done) {
            new Thread(new Runnable() { @Override public void run() {
                try { work.run(); } catch (Throwable t) { Log.w(TAG, "dev menu", t); }
                ui.post(new Runnable() { @Override public void run() { if (tab == page && isShowing()) done.run(); } });
            } }, "ccncDev").start();
        }

        // ---- Status ----

        void status() {
            heading("ccNC");
            PackageManager pm = a.getPackageManager();
            List<PackageInfo> ps = new ArrayList<PackageInfo>();
            for (PackageInfo p : pm.getInstalledPackages(0)) if (p.packageName.startsWith("dev.ccnc.")) ps.add(p);
            Collections.sort(ps, new java.util.Comparator<PackageInfo>() {
                @Override public int compare(PackageInfo x, PackageInfo y) { return x.packageName.compareTo(y.packageName); }
            });
            for (PackageInfo p : ps) row(p.packageName.substring(9), p.versionName, 0);
            row("Theme", themeId(), 0);
            row("Brand", cap(Fonts.brand(a)), 0);
            heading("System Patches");
            final TextView[] pv = new TextView[PATCHES.length];
            for (int i = 0; i < PATCHES.length; i++) pv[i] = row(PATCHES[i][1], "checking…", 0);
            heading("Tools");
            row("NEMESIS CLI", new File(NEMESIS[1]).exists() ? "present" : "missing", new File(NEMESIS[1]).exists() ? GOOD : BAD);
            boolean tools = new File(DIR + "/verify.sh").exists() && new File(NEMESIS[0]).exists();
            row("Dev tools (" + DIR + ")", tools ? "installed" : "missing", tools ? GOOD : WARN);
            boolean hmg = new File("/system/xbin/hmgapid").exists() || new File("/system/bin/hmgapid").exists() || new File("/data/hmgapi").exists();
            row("HMG-API", hmg ? "present" : "not found", 0);
            heading("Device");
            row("Firmware", prop("ro.build.product2"), 0);
            row("Android", android.os.Build.VERSION.RELEASE + " (API " + android.os.Build.VERSION.SDK_INT + ")", 0);
            row("Uptime", dur(SystemClock.elapsedRealtime()), 0);
            long[] m = meminfo();
            row("Memory", m == null ? null : (m[1] / 1024) + " MB free of " + (m[0] / 1024) + " MB", 0);
            try {
                android.os.StatFs s = new android.os.StatFs("/data");
                long free = (long) s.getAvailableBlocks() * s.getBlockSize(), tot = (long) s.getBlockCount() * s.getBlockSize();
                row("Storage (/data)", (free >> 20) + " MB free of " + (tot >> 20) + " MB", free < (200L << 20) ? WARN : 0);
            } catch (Throwable ignored) { }
            heading("Stock Home");
            note("Opens the Gen5W's own home screen, e.g. to reach something only it has. Press Home to come back.");
            button("Open Stock Home", new View.OnClickListener() { @Override public void onClick(View v) { openStockHome(a); dismiss(); } });
            patchStates(0, pv);
        }

        /** Fills the rows with each patch's state, worked out off the UI thread. */
        void patchStates(int page, final TextView[] pv) {
            final String[] res = new String[PATCHES.length];
            final int[] col = new int[PATCHES.length];
            bg(page, new Runnable() { @Override public void run() {
                String mounts = readAll("/proc/mounts");
                for (int i = 0; i < PATCHES.length; i++) { Object[] r = patchState(i, mounts); res[i] = (String) r[0]; col[i] = (Integer) r[1]; }
            } }, new Runnable() { @Override public void run() {
                for (int i = 0; i < PATCHES.length; i++) { pv[i].setText(res[i]); if (col[i] != 0) pv[i].setTextColor(col[i]); }
            } });
        }

        // ---- CPU usage ----

        long[] lastCpu;
        Map<Integer, Long> lastProc = new HashMap<Integer, Long>();

        void cpu() {
            long[] now = cpuTimes();
            Map<Integer, Long> procNow = new HashMap<Integer, Long>();
            Map<Integer, String> names = new HashMap<Integer, String>();
            File[] dirs = new File("/proc").listFiles();
            if (dirs != null) for (File d : dirs) {
                int pid;
                try { pid = Integer.parseInt(d.getName()); } catch (NumberFormatException e) { continue; }
                String st = read("/proc/" + pid + "/stat");
                if (st == null) continue;
                int close = st.lastIndexOf(')');
                if (close < 0) continue;
                String[] f = st.substring(close + 2).split(" ");
                if (f.length < 13) continue;
                try { procNow.put(pid, Long.parseLong(f[11]) + Long.parseLong(f[12])); } catch (NumberFormatException e) { continue; }
                String cmd = read("/proc/" + pid + "/cmdline");
                if (cmd != null) { int z = cmd.indexOf('\0'); if (z >= 0) cmd = cmd.substring(0, z); }
                names.put(pid, cmd == null || cmd.length() == 0 ? st.substring(st.indexOf('(') + 1, close) : cmd);
            }
            content.removeAllViews();
            heading("Processor");
            if (now == null) {
                row("Total", "not readable on this device", WARN);
            } else if (lastCpu == null) {
                row("Total", "measuring…", 0);
            } else {
                long busy = (now[0] - now[1]) - (lastCpu[0] - lastCpu[1]), total = now[0] - lastCpu[0];
                int pct = total > 0 ? (int) (100 * busy / total) : 0;
                row("Total", pct + " %", pct > 85 ? BAD : pct > 60 ? WARN : GOOD);
            }
            row("Cores", String.valueOf(Runtime.getRuntime().availableProcessors()), 0);
            String load = read("/proc/loadavg");
            row("Load (1 / 5 / 15 min)", load == null ? null : load.trim().replaceAll("^(\\S+) (\\S+) (\\S+).*", "$1 / $2 / $3"), 0);
            long[] m = meminfo();
            row("Memory free", m == null ? null : (m[1] / 1024) + " MB of " + (m[0] / 1024) + " MB", m != null && m[1] < 60 * 1024 ? WARN : 0);
            heading("Busiest Processes (Last 2 s)");
            if (lastCpu != null && now != null && !lastProc.isEmpty()) {
                long total = Math.max(1, now[0] - lastCpu[0]);
                List<long[]> top = new ArrayList<long[]>();
                for (Map.Entry<Integer, Long> e : procNow.entrySet()) {
                    Long before = lastProc.get(e.getKey());
                    if (before != null && e.getValue() > before) top.add(new long[]{e.getKey(), e.getValue() - before});
                }
                Collections.sort(top, new java.util.Comparator<long[]>() {
                    @Override public int compare(long[] x, long[] y) { return x[1] < y[1] ? 1 : x[1] > y[1] ? -1 : 0; }
                });
                int n = 0;
                for (long[] t : top) {
                    if (n++ >= 8) break;
                    // /proc/stat counts every core; per-process ticks against the whole machine, like top.
                    int pct = (int) (100 * t[1] / total * Runtime.getRuntime().availableProcessors());
                    String name = names.get((int) t[0]);
                    row(name + "  (" + t[0] + ")", pct + " %", name != null && name.startsWith("dev.ccnc") ? Palette.ACCENT : 0);
                }
                if (top.isEmpty()) note("Nothing used the CPU.");
            } else {
                note(now == null ? "This Android doesn't let apps read /proc." : "Measuring…");
            }
            lastCpu = now;
            lastProc = procNow;
        }

        // ---- Compatibility ----

        void compat() {
            String fw = prop("ro.build.product2");
            heading("Head Unit");
            row("Firmware", fw, 0);
            row("Brand", cap(Fonts.brand(a)) + "  (" + brandSource() + ")", 0);
            android.util.DisplayMetrics dm = a.getResources().getDisplayMetrics();
            boolean screen = dm.widthPixels == 1920 && dm.heightPixels >= 700;
            row("Screen", dm.widthPixels + " x " + dm.heightPixels + " @ " + dm.densityDpi + " dpi", screen ? GOOD : WARN);
            heading("System Patches on This Firmware");
            note("LivePatch fits each patch to this firmware when it installs. A patch that installs successfully is supported.");
            final TextView[] pv = new TextView[PATCHES.length];
            for (int i = 0; i < PATCHES.length; i++) pv[i] = row(PATCHES[i][1], "checking…", 0);
            patchStates(2, pv);
            heading("Car Services");
            row("Climate (DATC)", has(service("DATC_SERVICE", "com.hkmc.micom.datc")), 0);
            row("Seats / wheel (USM)", has(service("USM_SERVICE", "com.hkmc.micom.usm")), 0);
            row("EV (HEVManager)", has(cls("com.hkmc.hev.HEVManager")), 0);
            row("Audio (MicomAudioManager)", has(cls("com.hkmc.media.MicomAudioManager")), 0);
            heading("Stock Apps ccNC Uses");
            String[][] apps = {
                    {"com.android.settings", "Setup"},
                    {"com.android.vehiclesettings", "Vehicle Settings"},
                    {"com.hkmc.daudio.app.ev3", "EV"},
                    {"com.hkmc.telematics.service.apps.climate", "Climate"},
                    {"com.daudio.app.phoneprojection", "Phone projection"},
                    {"com.tcc.iap2server", "iAP2 (CarPlay)"},
            };
            for (String[] p : apps) {
                String v = version(p[0]);
                row(p[1], v == null ? "not installed" : v, v == null ? WARN : GOOD);
            }
        }

        String has(boolean b) { return b ? "available" : "not found"; }

        // ---- Export Logs ----

        void checks() {
            heading("Export Logs");
            note("Saves a summary of this car and ccNC, and the head unit's recent logs, to \"ccNC logs/\" on the USB "
                    + "stick. Send that folder with a bug report. Phone names, Bluetooth addresses, phone numbers, locations, "
                    + "the VIN and similar details are removed first (SANITIZED.txt lists what). Look the files over before "
                    + "sharing them.");
            final String usb = usbStick();
            if (usb == null) {
                note("Insert a USB stick to export logs.");
                TextView b = button("Export Logs", new View.OnClickListener() { @Override public void onClick(View v) { select(3); } });
                b.setAlpha(0.4f);
            } else {
                note("USB stick: " + usb);
                button("Export Logs", new View.OnClickListener() { @Override public void onClick(View v) {
                    if (usbStick() == null) { select(3); return; }   // pulled out since
                    exportLogs();
                } });
            }
            heading("Without Root");
            button("Quick Self-Check", new View.OnClickListener() { @Override public void onClick(View v) { selfCheck(); } });
        }

        void exportLogs() {
            content.removeAllViews();
            heading("Exporting Logs…");
            note("Collecting the logs, removing personal information, then writing them to the USB stick. This can take "
                    + "up to a minute.");
            final String[] r = new String[2];
            bg(3, new Runnable() { @Override public void run() {
                r[1] = summary(a);
                r[0] = writeLogs(a, r[1]);
            } }, new Runnable() { @Override public void run() {
                content.removeAllViews();
                boolean ok = r[0] != null && r[0].startsWith("Ran through");
                heading(ok ? "Logs Exported" : "Logs Not Exported");
                if (ok) {
                    String dir = null;
                    for (String l : r[0].split("\n")) if (l.startsWith("DIR ")) dir = l.substring(4);
                    note("Saved to " + (dir == null ? "the USB stick" : dir) + ". Wait a moment before removing the stick.");
                } else {
                    note("Writing to the USB stick needs NEMESIS root:\n" + r[0]);
                    heading("Summary");
                    note(r[1]);
                }
            } });
        }

        /** What an app can check on its own: ccNC apps installed and opening, theme provider, patch files. */
        void selfCheck() {
            heading("Quick Self-Check");
            PackageManager pm = a.getPackageManager();
            String[] apps = {"home", "settings", "climate", "media", "weather", "phone", "radio", "voicememo", "profile", "ev", "navi"};
            for (String s : apps) {
                String p = "dev.ccnc." + s;
                String v = version(p);
                boolean launch = v != null && pm.getLaunchIntentForPackage(p) != null;
                row(s, v == null ? "not installed" : launch ? v : v + " (no launcher)", v == null ? WARN : launch ? GOOD : BAD);
            }
            String th = themeId();
            row("Theme provider", th == null ? "no answer" : th, th == null ? BAD : GOOD);
        }

        // ---- Reboot ----

        /** A ccNC confirm popup over the menu: Cancel or Reboot. */
        void confirmReboot() {
            final Dialog d = new Dialog(a, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen);
            d.requestWindowFeature(Window.FEATURE_NO_TITLE);
            d.getWindow().setBackgroundDrawable(new ColorDrawable(0x99000000));
            FrameLayout root = new FrameLayout(a);
            root.setSoundEffectsEnabled(false);
            final long shownAt = SystemClock.uptimeMillis();
            root.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { if (outsideOk(shownAt)) d.dismiss(); } });
            LinearLayout box = new LinearLayout(a);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setClickable(true);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(0xFA17212C);
            bg.setCornerRadius(24 * dp);
            bg.setStroke(Math.max(1, (int) dp), Palette.BORDER);
            box.setBackgroundDrawable(bg);
            box.setPadding(px(32), px(26), px(32), px(24));
            box.addView(text("Reboot Headunit?", 24, Palette.TEXT, Fonts.SEMIBOLD));
            TextView body = text("The headunit will be unavailable for up to a minute. Confirm below.", 17, Palette.SUBTEXT, Fonts.REGULAR);
            body.setPadding(0, px(10), 0, px(22));
            box.addView(body);
            LinearLayout btns = new LinearLayout(a);
            btns.setGravity(Gravity.RIGHT);
            TextView cancel = pill("Cancel", 0x26FFFFFF);
            cancel.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { Chrome.beep(a); d.dismiss(); } });
            TextView ok = pill("Reboot", BAD);
            ok.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { Chrome.beep(a); d.dismiss(); reboot(); } });
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(px(160), px(56));
            btns.addView(cancel, bl);
            LinearLayout.LayoutParams ol = new LinearLayout.LayoutParams(px(160), px(56));
            ol.leftMargin = px(14);
            btns.addView(ok, ol);
            box.addView(btns);
            root.addView(box, new FrameLayout.LayoutParams(px(620), -2, Gravity.CENTER));
            d.setContentView(root);
            d.show();
        }

        TextView pill(String s, int color) {
            TextView b = text(s, 18, Palette.TEXT, Fonts.SEMIBOLD);
            b.setGravity(Gravity.CENTER);
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(28 * dp);
            g.setColor(color);
            b.setBackgroundDrawable(g);
            return b;
        }

        void reboot() {
            tabs[4].setText("Rebooting…");
            new Thread(new Runnable() { @Override public void run() {
                final String r = root("setprop sys.powerctl reboot", 15000);
                ui.postDelayed(new Runnable() { @Override public void run() {
                    // Still here: it didn't work.
                    tabs[4].setText("Reboot");
                    tabs[4].setTextColor(Palette.TEXT);
                    tab = -1;
                    content.removeAllViews();
                    heading("Reboot Didn't Happen");
                    note(r);
                } }, 5000);
            } }, "ccncReboot").start();
        }
    }

    // ---- Export Logs ----

    /** What the app itself can tell about this car and ccNC, as text (summary.txt). */
    static String summary(Context c) {
        StringBuilder b = new StringBuilder();
        java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US);
        b.append("ccNC log export ").append(f.format(new java.util.Date())).append('\n');
        b.append("\n[Head unit]\n");
        for (String k : new String[]{"ro.build.product2", "ro.build.display.id", "ro.build.version.release", "ro.product.model",
                "ro.product.device", "persist.sys.climate.rhd", "persist.sys.ccnc.rhd", "persist.sys.language", "persist.sys.country"}) {
            b.append(k).append(" = ").append(prop(k)).append('\n');
        }
        b.append("brand = ").append(Fonts.brand(c)).append(" (").append(brandSource()).append(")\n");
        b.append("right-hand drive = ").append(Drive.rhd(c)).append('\n');
        android.util.DisplayMetrics dm = c.getResources().getDisplayMetrics();
        b.append("screen = ").append(dm.widthPixels).append('x').append(dm.heightPixels).append(" @ ").append(dm.densityDpi).append(" dpi\n");
        b.append("uptime = ").append(dur(SystemClock.elapsedRealtime())).append('\n');
        long[] m = meminfo();
        if (m != null) b.append("memory = ").append(m[1] / 1024).append(" MB free of ").append(m[0] / 1024).append(" MB\n");
        b.append("theme = ").append(themeId()).append('\n');
        b.append("\n[ccNC and stock packages]\n");
        PackageManager pm = c.getPackageManager();
        List<String> lines = new ArrayList<String>();
        for (PackageInfo p : pm.getInstalledPackages(0)) {
            String n = p.packageName;
            if (n.startsWith("dev.ccnc.") || n.startsWith("com.nemesis") || n.contains("hmgapi") || n.equals("com.daudio.app.dipo")
                    || n.equals("com.google.android.projection.sink") || n.equals("com.android.launcher3")) {
                lines.add(n + " " + p.versionName + " (" + p.versionCode + ")");
            }
        }
        Collections.sort(lines);
        for (String l : lines) b.append(l).append('\n');
        b.append("\n[System patches]\n");
        String mounts = readAll("/proc/mounts");
        for (int i = 0; i < PATCHES.length; i++) b.append(PATCHES[i][1]).append(" = ").append(patchState(i, mounts)[0]).append('\n');
        b.append("\n[Car services]\n");
        b.append("DATC = ").append(service("DATC_SERVICE", "com.hkmc.micom.datc")).append('\n');
        b.append("USM = ").append(service("USM_SERVICE", "com.hkmc.micom.usm")).append('\n');
        b.append("HEVManager = ").append(cls("com.hkmc.hev.HEVManager")).append('\n');
        b.append("\n[Profiles]\n");
        try {
            b.append("active = ").append(Profiles.active(c)).append(", key = ").append(Profiles.keyProfile(c)).append('\n');
            b.append("default = ").append(Profiles.setting(c, "default_profile", -1)).append(", auto-advance = ")
                    .append(Profiles.setting(c, "auto_advance", 3)).append('\n');
        } catch (Throwable t) { b.append(t).append('\n'); }
        b.append("\n[Tools]\n");
        b.append("NEMESIS CLI = ").append(new File(NEMESIS[1]).exists()).append(", dev tools = ").append(new File(DIR + "/verify.sh").exists()).append('\n');
        return b.toString();
    }

    /**
     * Exports summary + the system logs to "<stick>/ccNC logs/<time>/", with personal information removed (LogScrub).
     * On Android 4.4 an app can neither read the system logs nor write to a USB stick, so NEMESIS root does both ends:
     *   1. root collects the raw logs into this app's private cache (ccnc-raw), readable by the app;
     *   2. the app cleans each file into ccnc-clean and writes SANITIZED.txt (what was removed);
     *   3. root copies only the cleaned files to the stick and deletes both private copies.
     * The raw logs never reach the stick. Returns root()'s report; on success its output has a line "DIR <folder>".
     */
    static String writeLogs(Context c, String summary) {
        String usb = usbStick();
        if (usb == null) return "No USB stick.";
        String stamp = new java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(new java.util.Date());
        File cache = c.getCacheDir();
        File raw = new File(cache, "ccnc-raw"), clean = new File(cache, "ccnc-clean");
        File collect = new File(cache, "ccnc-collect.sh"), copy = new File(cache, "ccnc-copy.sh");
        try {
            write(collect, "R=\"$1\"\n"
                    + "rm -rf \"$R\"; mkdir -p \"$R\" || exit 1\n"
                    + "logcat -d -v threadtime -b main -b system > \"$R/logcat.txt\" 2>&1\n"
                    + "logcat -d -v threadtime -b events > \"$R/events.txt\" 2>&1\n"
                    + "dmesg > \"$R/dmesg.txt\" 2>&1\n"
                    + "getprop > \"$R/getprop.txt\" 2>&1\n"
                    + "ps > \"$R/ps.txt\" 2>&1\n"
                    + "cat /proc/mounts > \"$R/mounts.txt\" 2>&1\n"
                    + "df > \"$R/df.txt\" 2>&1\n"
                    + "dumpsys activity activities > \"$R/activities.txt\" 2>&1\n"
                    + "dumpsys meminfo > \"$R/meminfo.txt\" 2>&1\n"
                    + "pm list packages -f -i > \"$R/packages.txt\" 2>&1\n"
                    + "nemesis livepatch status > \"$R/livepatch.txt\" 2>&1\n"
                    + "for f in " + LP_DIR + "/*; do [ -f \"$f\" ] && echo \"$f: $(cat \"$f\")\"; done >> \"$R/livepatch.txt\" 2>&1\n"
                    + "ls -lR " + DIR + " > \"$R/ccnc-files.txt\" 2>&1\n"
                    + "[ -f /data/anr/traces.txt ] && cp /data/anr/traces.txt \"$R/anr-traces.txt\"\n"
                    + "ls -l /data/tombstones > \"$R/tombstones.txt\" 2>&1\n"
                    + "chmod 777 \"$R\"; chmod 666 \"$R\"/*\n"
                    + "echo collected\n"
                    + "exit 0\n");
            write(copy, "D=\"$1/ccNC logs/$2\"\n"
                    + "mkdir -p \"$D\" || exit 1\n"
                    + "cp \"$3\"/* \"$D/\" || exit 1\n"
                    + "rm -rf \"$3\" \"$4\"\n"
                    + "sync\n"
                    + "echo \"DIR $D\"\n"
                    + "ls \"$D\" | wc -l\n"
                    + "exit 0\n");
        } catch (Throwable t) {
            return "Couldn't prepare the export: " + t;
        }
        try {
            String r = root("sh " + collect.getAbsolutePath() + " " + raw.getAbsolutePath(), 90000);
            if (!r.startsWith("Ran through") || !r.contains("collected")) return r;
            // Clean every file the root step collected, plus ccNC's own summary.
            deleteTree(clean);
            if (!clean.mkdirs()) return "Couldn't make " + clean;
            LogScrub scrub = new LogScrub(c);
            write(new File(raw, "summary.txt"), summary);
            File[] files = raw.listFiles();
            if (files == null || files.length == 0) return "The collected logs couldn't be read (" + raw + ").";
            for (File f : files) if (f.isFile()) scrub.harvest(f);     // names first, so every file loses them
            for (File f : files) {
                if (f.isFile()) scrub.file(f, new File(clean, f.getName()));
            }
            write(new File(clean, "SANITIZED.txt"), scrub.report());
            for (File f : clean.listFiles()) f.setReadable(true, false);
            r = root("sh " + copy.getAbsolutePath() + " " + usb + " " + stamp + " " + clean.getAbsolutePath() + " " + raw.getAbsolutePath(), 90000);
            Log.i(TAG, "export logs: " + r);
            return r;
        } catch (Throwable t) {
            Log.w(TAG, "export logs", t);
            return "Export failed: " + t;
        } finally {
            // Whatever happened, no raw or cleaned copy stays behind in the app's storage.
            root("rm -rf " + raw.getAbsolutePath() + " " + clean.getAbsolutePath(), 15000);
            deleteTree(raw);
            deleteTree(clean);
        }
    }

    static void deleteTree(File f) {
        File[] k = f.listFiles();
        if (k != null) for (File x : k) deleteTree(x);
        f.delete();
    }

    static void write(File f, String s) throws java.io.IOException {
        java.io.FileOutputStream o = new java.io.FileOutputStream(f);
        try { o.write(s.getBytes("UTF-8")); } finally { o.close(); }
        f.setReadable(true, false);
    }

    // ---- root through the NEMESIS CLI ----

    /**
     * Runs cmd as root through the NEMESIS CLI: the daemon copy first (full root), then the SUID binary.
     * Returns a short report: which one ran, its exit code and the end of its output.
     */
    public static String root(String cmd, long timeoutMs) {
        StringBuilder why = new StringBuilder();
        for (String bin : NEMESIS) {
            if (!new File(bin).exists()) { why.append(bin).append(": missing\n"); continue; }
            // The daemon copy is made at install; after a NEMESIS update it's an old CLI, so it's skipped until the
            // next ccNC install copies it again.
            if (bin.equals(NEMESIS[0]) && new File(bin).length() != new File(NEMESIS[1]).length()) {
                why.append(bin).append(": older than the installed NEMESIS, skipped\n");
                continue;
            }
            try {
                final Process p = new ProcessBuilder(bin, "root", "-c", cmd).redirectErrorStream(true).start();
                final StringBuilder out = new StringBuilder();
                Thread r = new Thread(new Runnable() { @Override public void run() {
                    try {
                        BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream()));
                        String l;
                        while ((l = in.readLine()) != null) synchronized (out) { out.append(l).append('\n'); }
                    } catch (Throwable ignored) { }
                } });
                r.start();
                r.join(timeoutMs);
                int code;
                try { code = p.exitValue(); } catch (IllegalThreadStateException e) { p.destroy(); code = -1; }
                String o;
                synchronized (out) { o = out.toString(); }
                if (o.length() > 1500) o = "…" + o.substring(o.length() - 1500);
                Log.i(TAG, bin + " root -c '" + cmd + "' -> " + code + "\n" + o);
                boolean refused = o.contains("Permission denied") || o.contains("not allowed") || o.contains("daemon not running");
                if (code == 0 && !refused) return "Ran through " + bin + " (exit 0)\n" + o.trim();
                why.append(bin).append(": exit ").append(code).append(' ').append(o.trim()).append('\n');
            } catch (Throwable t) {
                why.append(bin).append(": ").append(t.getMessage()).append('\n');
            }
        }
        return "No NEMESIS root for ccNC:\n" + why.toString().trim();
    }

    // ---- helpers ----

    static String prop(String key) {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            String v = (String) sp.getMethod("get", String.class, String.class).invoke(null, key, "");
            return v == null || v.length() == 0 ? null : v;
        } catch (Throwable t) { return null; }
    }

    /**
     * {text, colour} for PATCHES[i]: LivePatch's patched copy mounted over the file = active; the step's own record
     * (applied but not mounted = restart pending or safe mode; unsupported = this firmware doesn't fit); else the
     * 1.x byte patch by md5; else off.
     */
    static Object[] patchState(int i, String mounts) {
        String path = PATCHES[i][2];
        boolean mounted = false;
        if (mounts != null) for (String l : mounts.split("\n")) {
            String[] f = l.split(" ");
            if (f.length > 1 && f[1].equals(path)) { mounted = true; break; }
        }
        // ccNC's own record first (written by the step's tool, filled in by the install script when NEMESIS reused
        // a cached result): a mount alone may be another package's patch of the same file.
        String lp = read(LP_DIR + "/" + PATCHES[i][0]);
        lp = lp == null ? "" : lp.trim();
        if (lp.equals("applied")) return mounted ? new Object[]{"Active", GOOD} : new Object[]{"Installed, restart to apply", WARN};
        if (lp.equals("unsupported")) return new Object[]{mounted ? "Not supported here (another package patches this file)" : "Not supported on this firmware", 0};
        if (lp.equals("error")) return new Object[]{"Patch tool error (see NEMESIS's log)", BAD};
        if (mounted) return new Object[]{"Patched by LivePatch (no ccNC record)", WARN};
        if (PATCHES[i][3].length() > 0 && PATCHES[i][3].equals(md5(path))) return new Object[]{"Active (1.x patch)", GOOD};
        return new Object[]{"Off", 0};
    }

    static final String STOCK_LAUNCHER = "com.android.launcher3";

    static void openStockHome(Context c) {
        try {
            android.content.Intent i = new android.content.Intent(android.content.Intent.ACTION_MAIN)
                    .addCategory(android.content.Intent.CATEGORY_HOME).setPackage(STOCK_LAUNCHER);
            if (c.getPackageManager().resolveActivity(i, 0) == null) i = c.getPackageManager().getLaunchIntentForPackage(STOCK_LAUNCHER);
            if (i == null) { android.widget.Toast.makeText(c, "Stock home not found", android.widget.Toast.LENGTH_SHORT).show(); return; }
            c.startActivity(i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Throwable t) { Log.w(TAG, "stock home", t); }
    }

    /** Outside taps close a popup only after it has been open this long. */
    static boolean outsideOk(long since) { return SystemClock.uptimeMillis() - since >= 3000; }

    /** The mounted USB stick (/storage/usbN), or null. */
    static String usbStick() {
        String m = readAll("/proc/mounts");
        if (m == null) return null;
        for (String l : m.split("\n")) {
            String[] f = l.split(" ");
            if (f.length > 1 && f[1].startsWith("/storage/usb")) return f[1];
        }
        return null;
    }

    static String cap(String s) { return s == null || s.isEmpty() ? "" : Character.toUpperCase(s.charAt(0)) + s.substring(1); }

    /** The whole file (read() stops at 4 KB). */
    static String readAll(String path) {
        try {
            InputStream in = new FileInputStream(path);
            java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
            byte[] b = new byte[8192];
            int n;
            while ((n = in.read(b)) > 0) o.write(b, 0, n);
            in.close();
            return o.toString();
        } catch (Throwable t) { return null; }
    }

    static String brandSource() {
        String o = prop("persist.sys.ccnc.brand");
        if (o != null) return "override persist.sys.ccnc.brand";
        String p = prop("ro.build.product");
        if (p != null && (p.startsWith("CVEV") || p.startsWith("CEEV") || p.startsWith("NEEV"))) return "firmware " + p.substring(0, 4);
        String b = prop("ro.product.brand");
        return b != null ? "ro.product.brand " + b : "default";
    }

    static String themeId() { return themeIdStatic; }
    private static String themeIdStatic;

    /** Reads the theme choice from ccNC Home's provider (also proves the provider answers). */
    static String readTheme(Context c) {
        android.database.Cursor cur = null;
        try {
            cur = c.getContentResolver().query(android.net.Uri.parse("content://dev.ccnc.home.theme/theme"), null, null, null, null);
            if (cur != null && cur.moveToFirst()) return cur.getString(0);
        } catch (Throwable ignored) {
        } finally { if (cur != null) cur.close(); }
        return null;
    }

    static boolean service(String field, String fallback) {
        String name = fallback;
        try {
            Object v = Class.forName("com.hkmc.Service").getField(field).get(null);
            if (v instanceof String) name = (String) v;
        } catch (Throwable ignored) { }
        try {
            Object b = Class.forName("android.os.ServiceManager").getMethod("getService", String.class).invoke(null, name);
            return b != null;
        } catch (Throwable t) { return false; }
    }

    static boolean cls(String name) {
        try { Class.forName(name); return true; } catch (Throwable t) { return false; }
    }

    static String read(String path) {
        try {
            FileInputStream in = new FileInputStream(path);
            byte[] b = new byte[4096];
            int n = in.read(b);
            in.close();
            return n <= 0 ? null : new String(b, 0, n);
        } catch (Throwable t) { return null; }
    }

    /** /proc/stat first line: {total jiffies, idle (+iowait)}. */
    static long[] cpuTimes() {
        String s = read("/proc/stat");
        if (s == null) return null;
        String[] f = s.substring(0, s.indexOf('\n')).trim().split("\\s+");
        long total = 0;
        for (int i = 1; i < f.length; i++) try { total += Long.parseLong(f[i]); } catch (NumberFormatException ignored) { }
        long idle = 0;
        try { idle = Long.parseLong(f[4]) + (f.length > 5 ? Long.parseLong(f[5]) : 0); } catch (Throwable ignored) { }
        return new long[]{total, idle};
    }

    /** {MemTotal, MemFree + Buffers + Cached} in kB. */
    static long[] meminfo() {
        try {
            BufferedReader r = new BufferedReader(new FileReader("/proc/meminfo"));
            long total = -1, free = 0;
            String l;
            while ((l = r.readLine()) != null) {
                String[] f = l.split("\\s+");
                if (f.length < 2) continue;
                long v = Long.parseLong(f[1]);
                if (l.startsWith("MemTotal:")) total = v;
                else if (l.startsWith("MemFree:") || l.startsWith("Buffers:") || l.startsWith("Cached:")) free += v;
            }
            r.close();
            return total < 0 ? null : new long[]{total, free};
        } catch (Throwable t) { return null; }
    }

    static String md5(String path) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            InputStream in = new FileInputStream(path);
            byte[] b = new byte[65536];
            int n;
            while ((n = in.read(b)) > 0) md.update(b, 0, n);
            in.close();
            StringBuilder s = new StringBuilder();
            for (byte x : md.digest()) s.append(String.format(Locale.US, "%02x", x & 0xff));
            return s.toString();
        } catch (Throwable t) { return null; }
    }

    static String dur(long ms) {
        long m = ms / 60000, h = m / 60, d = h / 24;
        return d > 0 ? d + " d " + (h % 24) + " h" : h > 0 ? h + " h " + (m % 60) + " min" : m + " min";
    }

    private static Context appCtx;

    static String version(String pkg) {
        try { return appCtx.getPackageManager().getPackageInfo(pkg, 0).versionName; } catch (Throwable t) { return null; }
    }

    /** Status dot. */
    private static final class Dot extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Dot(Context c, int color) { super(c); p.setColor(color); }
        @Override protected void onDraw(Canvas c) { c.drawCircle(getWidth() / 2f, getHeight() / 2f, Math.min(getWidth(), getHeight()) / 2f, p); }
    }

    static void init(Context c) {
        appCtx = c.getApplicationContext();
        themeIdStatic = readTheme(c);
    }
}
