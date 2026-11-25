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
 *   Run checks     the on-car check (/data/ccnc/verify.sh, report to the USB stick) through NEMESIS root;
 *                  without root, the checks an app can do itself
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
            {"volume", "Volume strip off", "/system/framework/automotive-service.odex", "f426a78f829b935005995b4f998863ca"},
            {"sideosd", "Side popups off", "/system/app/RightScreenHome.odex", "0b04470678194ab7e5bf626de3738001"},
            {"climate", "Climate buttons", "/system/app/HKMC_Climate.odex", "364fe54acec3998d06ad53c44e7056d7"},
            {"welcome", "Startup video", "/system/app/WelcomeGoodbye.apk", ""},
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
        final Runnable cpuTick = new Runnable() { @Override public void run() { if (tab == 1) { cpu(); ui.postDelayed(this, 2000); } } };

        Menu(Activity a) {
            super(a, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen);
            this.a = a;
            dp = a.getResources().getDisplayMetrics().density;
            requestWindowFeature(Window.FEATURE_NO_TITLE);
            getWindow().setBackgroundDrawable(new ColorDrawable(0xD9000000));

            FrameLayout root = new FrameLayout(a);
            root.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { dismiss(); } });

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
            close.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { dismiss(); } });
            head.addView(close);
            panel.addView(head);

            LinearLayout body = new LinearLayout(a);
            body.setPadding(0, px(14), 0, 0);
            // Left: menu.
            LinearLayout menu = new LinearLayout(a);
            menu.setOrientation(LinearLayout.VERTICAL);
            String[] names = {"Status", "CPU usage", "Compatibility", "Run checks", "Reboot"};
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

        void button(String s, View.OnClickListener l) {
            TextView b = text(s, 18, Palette.TEXT, Fonts.SEMIBOLD);
            b.setGravity(Gravity.CENTER);
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(18 * dp);
            g.setColor(Palette.alpha(Palette.ACCENT, 0x55));
            b.setBackgroundDrawable(g);
            b.setPadding(px(20), px(12), px(20), px(12));
            b.setOnClickListener(l);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.topMargin = px(10);
            content.addView(b, lp);
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
            heading("System patches");
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
            heading("Busiest processes (last 2 s)");
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
            heading("Head unit");
            row("Firmware", fw, 0);
            row("Brand", cap(Fonts.brand(a)) + "  (" + brandSource() + ")", 0);
            android.util.DisplayMetrics dm = a.getResources().getDisplayMetrics();
            boolean screen = dm.widthPixels == 1920 && dm.heightPixels >= 700;
            row("Screen", dm.widthPixels + " x " + dm.heightPixels + " @ " + dm.densityDpi + " dpi", screen ? GOOD : WARN);
            heading("System patches on this firmware");
            note("LivePatch fits each patch to this firmware when it installs. A patch that installs successfully is supported.");
            final TextView[] pv = new TextView[PATCHES.length];
            for (int i = 0; i < PATCHES.length; i++) pv[i] = row(PATCHES[i][1], "checking…", 0);
            patchStates(2, pv);
            heading("Car services");
            row("Climate (DATC)", has(service("DATC_SERVICE", "com.hkmc.micom.datc")), 0);
            row("Seats / wheel (USM)", has(service("USM_SERVICE", "com.hkmc.micom.usm")), 0);
            row("EV (HEVManager)", has(cls("com.hkmc.hev.HEVManager")), 0);
            row("Audio (MicomAudioManager)", has(cls("com.hkmc.media.MicomAudioManager")), 0);
            heading("Stock apps ccNC uses");
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

        // ---- Run checks ----

        void checks() {
            heading("On-car check");
            note("Opens every ccNC app for a few seconds (about a minute; the screen changes by itself), then saves "
                    + "REPORT.txt, ccnc.log and screenshots to \"ccNC reports/run-N/\" on the USB stick. Park first.");
            button("Run checks", new View.OnClickListener() { @Override public void onClick(View v) { runChecks(); } });
            heading("Without root");
            button("Quick self-check", new View.OnClickListener() { @Override public void onClick(View v) { selfCheck(); } });
        }

        void runChecks() {
            content.removeAllViews();
            heading("Running the check…");
            final TextView out = note("Starting through NEMESIS root. The screen will switch between apps.");
            final String[] r = new String[1];
            bg(3, new Runnable() { @Override public void run() {
                if (!new File(DIR + "/verify.sh").exists()) { r[0] = "The check script isn't installed (" + DIR + "/verify.sh). Reinstall ccNC Apps or Home."; return; }
                r[0] = root("sh " + DIR + "/verify.sh", 240000);
            } }, new Runnable() { @Override public void run() {
                content.removeAllViews();
                heading("Check finished");
                note(r[0]);
                if (r[0] != null && r[0].startsWith("No NEMESIS root")) selfCheck();
            } });
        }

        /** What an app can check on its own: ccNC apps installed and opening, theme provider, patch files. */
        void selfCheck() {
            heading("Quick self-check");
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
            root.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { d.dismiss(); } });
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
                    heading("Reboot didn't happen");
                    note(r);
                } }, 5000);
            } }, "ccncReboot").start();
        }
    }

    // ---- root through the NEMESIS CLI ----

    /**
     * Runs cmd as root through the NEMESIS CLI: the daemon copy first (full root), then the SUID binary.
     * Returns a short report: which one ran, its exit code and the end of its output.
     */
    static String root(String cmd, long timeoutMs) {
        StringBuilder why = new StringBuilder();
        for (String bin : NEMESIS) {
            if (!new File(bin).exists()) { why.append(bin).append(": missing\n"); continue; }
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
        if (mounts != null) for (String l : mounts.split("\n")) {
            String[] f = l.split(" ");
            if (f.length > 1 && f[1].equals(path)) return new Object[]{"Active", GOOD};
        }
        String lp = read(LP_DIR + "/" + PATCHES[i][0]);
        lp = lp == null ? "" : lp.trim();
        if (lp.equals("applied")) return new Object[]{"Installed, restart to apply", WARN};
        if (lp.equals("unsupported")) return new Object[]{"Not supported on this firmware", 0};
        if (PATCHES[i][3].length() > 0 && PATCHES[i][3].equals(md5(path))) return new Object[]{"Active (1.x patch)", GOOD};
        return new Object[]{"Off", 0};
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
