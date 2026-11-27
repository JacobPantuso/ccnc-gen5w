package dev.ccnc.common;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.InputStream;
import java.security.MessageDigest;

/**
 * The car's start-up profile screen (SystemUI's ConsentScreen: the agreement, the three profiles and Done) as the
 * ccNC apps see it. ccNC never closes that screen itself: it can move the selection (CHANGE_FOCUS_CONSENT) and ask
 * the screen to continue (HIDE_CONSENT_SCREEN). Stock, that second broadcast confirms without the screen's own
 * checks, so ccNC only sends it while ccNC's LivePatch step "consent" is the SystemUI that is running: with that
 * step the broadcast goes through the same checks as the screen's 10 s timer (no reverse gear / early camera, power
 * and ACC state, no valet mode, no language dialog, no update view), and the screen's window sits one layer lower so
 * ccNC Profile's start-up chooser can be shown on top of it. See private/systemui/DESIGN.txt.
 */
public final class StartupScreen {
    private StartupScreen() {}

    static final String TAG = "ccncStartup";
    public static final String FOCUS = "com.mobis.intent.action.CHANGE_FOCUS_CONSENT";
    public static final String HIDE = "com.mobis.intent.action.HIDE_CONSENT_SCREEN";
    public static final String CHOOSER = "dev.ccnc.profile/dev.ccnc.profile.StartupChooser";
    /** bin/lp-ccnc's record of the patched file's md5, and the file it has to match. */
    static final String RECORD = "/data/ccnc/livepatch/consent.md5";
    static final String SYSTEMUI = "/system/priv-app/SystemUI.odex";
    /** The stock agreement (CEEV.CAN 250818, English), used when SystemUI's own text can't be read. */
    static final String AGREEMENT = "Drive safely and obey traffic rules.\nWatching this screen while the vehicle is in motion can lead to"
            + " a serious accident. Make selections only while stopped. Some map data may be incorrect. Please read the safety"
            + " instructions in the Navigation Manual.";

    public static String prop(String key) {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            return (String) sp.getMethod("get", String.class, String.class).invoke(null, key, "");
        } catch (Throwable t) { return ""; }
    }

    /** The start-up screen was created at this start and hasn't been confirmed yet. */
    public static boolean pending() {
        return "true".equals(prop("sys.boot.show_screen")) && !"true".equals(prop("sys.boot.confirm_screen"));
    }

    public static boolean enabled() { return "true".equals(prop("sys.boot.enabled_screen")); }

    public static boolean confirmed() { return "true".equals(prop("sys.boot.confirm_screen")); }

    private static Boolean active;

    /**
     * True only when the SystemUI file in use is byte for byte the one ccNC's patch tool wrote and verified (its md5
     * is in the tool's record). False on a stock unit, in LivePatch safe mode, after a firmware update, after an
     * uninstall, or when anything can't be read. Reads 0.8 MB: call it off the UI thread. The answer can't change
     * while Android is up (LivePatch mounts before it starts), so it is kept.
     */
    public static synchronized boolean patchActive() {
        if (active != null) return active;
        boolean ok = false;
        try {
            String want = null;
            BufferedReader r = new BufferedReader(new FileReader(RECORD));
            try { want = r.readLine(); } finally { r.close(); }
            if (want != null && want.trim().length() == 32) {
                MessageDigest md = MessageDigest.getInstance("MD5");
                InputStream in = new FileInputStream(new File(SYSTEMUI));
                try {
                    byte[] buf = new byte[1 << 16];
                    for (int n; (n = in.read(buf)) > 0; ) md.update(buf, 0, n);
                } finally { in.close(); }
                StringBuilder sb = new StringBuilder(32);
                for (byte x : md.digest()) sb.append(Character.forDigit((x >> 4) & 0xf, 16)).append(Character.forDigit(x & 0xf, 16));
                ok = sb.toString().equalsIgnoreCase(want.trim());
                if (!ok) Log.i(TAG, "SystemUI isn't ccNC's patched file: the car's own start-up screen stays as it is");
            }
        } catch (Throwable t) {
            ok = false;
        }
        active = ok;
        return ok;
    }

    /** Starts ccNC Profile's start-up chooser; false if this ccNC Profile doesn't have one. */
    public static boolean startChooser(Context c) {
        try {
            return c.startService(new Intent().setComponent(ComponentName.unflattenFromString(CHOOSER))) != null;
        } catch (Throwable t) {
            Log.i(TAG, "no start-up chooser: " + t);
            return false;
        }
    }

    /** Moves the stock screen's selection to {@code profile}; the screen ignores it while its profile buttons are disabled. */
    public static void select(Context c, int profile) {
        c.sendBroadcast(new Intent(FOCUS).putExtra("userType", profile).putExtra("fromWhere", 0));
    }

    /**
     * Asks the stock screen to continue with its selected profile. Only with ccNC's SystemUI step: the screen then
     * decides by its own checks and does nothing if any of them says no. Returns false (nothing sent) without it.
     */
    public static boolean askContinue(Context c) {
        if (!patchActive()) return false;
        c.sendBroadcast(new Intent(HIDE));
        return true;
    }

    /**
     * The agreement the stock screen shows, in the car's language, read from SystemUI's own resources and chosen
     * the way the stock screen chooses it (navigation or not; plus the data notice where the car adds it).
     */
    public static String agreement(Context c) {
        try {
            Context ui = c.createPackageContext("com.android.systemui", 0);
            Resources r = ui.getResources();
            PackageManager pm = c.getPackageManager();
            boolean navi = pm.hasSystemFeature("com.mobis.software.navigation") || pm.hasSystemFeature("com.hkmc.software.navi.sdcard")
                    || pm.hasSystemFeature("com.hkmc.software.baidu");
            String text = string(r, navi ? "consent_note_navi" : "consent_note");
            if (text == null || text.trim().isEmpty()) return AGREEMENT;
            String plus = dataNotice(r);
            return plus == null ? text : text + " " + plus;
        } catch (Throwable t) {
            return AGREEMENT;
        }
    }

    private static String string(Resources r, String name) {
        int id = r.getIdentifier(name, "string", "com.android.systemui");
        return id == 0 ? null : r.getString(id);
    }

    /** Stock ConsentScreen.dH(): spec step 13+, region 1 only; Genesis, Hyundai (vendor 0) or Kia (vendor 1) wording. */
    private static String dataNotice(Resources r) {
        try {
            int step = Integer.parseInt(prop("ro.product.opt.stepofspec").trim());
            Class<?> bi = Class.forName("com.hkmc.BuildInfo");
            if (step < 13 || bi.getField("REGION").getInt(null) != 1) return null;
            boolean genesis = (Boolean) Class.forName("com.hkmc.Feature").getMethod("hasFeature", String.class).invoke(null, "com.hkmc.software.genesis");
            int vendor = bi.getField("VENDOR").getInt(null);
            String s = string(r, genesis ? "consent_note_plus_genesis" : vendor == 0 ? "consent_note_plus_hyundai" : vendor == 1 ? "consent_note_plus_kia_connect" : "");
            return s == null || s.trim().isEmpty() || "N/A".equals(s.trim()) ? null : s;
        } catch (Throwable t) {
            return null;
        }
    }
}
