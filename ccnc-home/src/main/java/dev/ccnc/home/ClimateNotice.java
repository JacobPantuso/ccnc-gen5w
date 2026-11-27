package dev.ccnc.home;

import android.app.Notification;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.RemoteViews;

import dev.ccnc.common.Drive;
import dev.ccnc.common.Fonts;

import java.util.Locale;

/**
 * ccNC's bottom popup for the climate system's own notices (washer fluid in use / poor air quality / tunnel ->
 * air recirculation), in place of the Gen5W bar at the top of the screen.
 *
 * The stock bar is a normal Android notification: DATCService (system process, automotive-service) posts it with
 * NotificationManager.notify("DATCService", 0, ..) and SystemUI's AvnStatusBar draws it as a heads-up. Every climate
 * notice uses that one tag and id, so the notice is told apart by its title, compared with the system's own strings
 * (by resource name, in the unit's language). {@link MediaNotificationBlocker} removes the stock one and calls
 * {@link #show}: one event is both the block and the trigger. A notice that isn't in {@link #KINDS} stays stock.
 *
 * Same overlay window as the volume bar (VolumeOsdService): osd_bg_vol.9, 97 high, bottom centre, closes after 3 s;
 * it moves above the volume bar while that one is up.
 * Off: settings put global ccnc_climate_notice 0 (the stock bar comes back).
 *
 * Two more stock bars are shown the same way (see "system notices" below): Android's "USB debugging connected" and
 * the seat memory notices of the vehicle settings app ("Settings 1 applied." after a profile switch, and the like).
 * Off: settings put global ccnc_system_notice 0.
 */
final class ClimateNotice {
    static final String TAG = "ccncClm";
    /** DATCService's notification tag and id (package "android"). */
    static final String DATC_TAG = "DATCService";
    static final int DATC_ID = 0;

    static final int H = VolumeOsdService.H, WIN_W = VolumeOsdService.WIN_W, TIMEOUT_MS = 3000;
    static final int MIN_W = 900, MAX_W = 1700, PAD = 40, ICON = 64, GAP = 20, LIFT = H + 8;

    static final int RECIRC = 0, WASHER = 1, AIR = 2, INFO = 3, SEAT = 4, WARN = 5;

    /** A stock notice ccNC shows itself: icon and message. */
    static final class Match {
        final int icon; final String msg;
        Match(int icon, String msg) { this.icon = icon; this.msg = msg; }
    }

    /**
     * {android string name, icon, ccNC wording in English or null for the stock text}. Other languages always get
     * the stock text. The names are the ones DATCService reads (showTxtPopupNotification, showTunnelAirOffOsd,
     * showAirCleanNotification, showNewAirCleanNotification). Left stock on purpose: auto_defog_clean,
     * rear_climate_auto_off, air_clean_text_on/off.
     */
    private static final Object[][] KINDS = {
        {"washer_intake_on", WASHER, "Blocking outside air to prevent the odor of washer fluid from entering the vehicle."},
        {"active_rec_mode_plus_nosftwindow", RECIRC, "Blocking outside air for comfort."},
        {"active_rec_mode_plus", RECIRC, "Closing windows and blocking outside air for comfort."},
        {"tennel_air_off", RECIRC, "Entering a tunnel. Air recirculation is on."},
        {"tennel_air_off_window", RECIRC, "Entering a tunnel. Air recirculation is on and the windows are closing."},
        {"auto_air_clean_text_on", AIR, "Air cleaning mode is on because the air quality is poor."},
        {"sensor_on_window_close", AIR, "Air purification mode is on to block outside air."},
        {"high_dust_aircleaning_on", AIR, "Air purification mode is on because indoor fine dust has increased."},
        {"high_dust_aircleaning_off", AIR, null},
        {"opening_warning", AIR, null},
        {"aircleaning_sys_on", AIR, "Air purification mode is on."},
        {"aircleaning_sys_off", AIR, "Air purification mode is off."},
    };

    private ClimateNotice() {}

    static boolean enabled(Context c) { return on(c, "ccnc_climate_notice"); }

    private static boolean on(Context c, String key) {
        try { return !"0".equals(android.provider.Settings.Global.getString(c.getContentResolver(), key)); }
        catch (Throwable t) { return true; }
    }

    /** The ccNC notice for a DATCService notification with this title, or null when it isn't one of ours. */
    static Match match(Context c, CharSequence title) {
        if (title == null || !enabled(c)) return null;
        String t = title.toString().trim();
        if (t.length() == 0) return null;
        Resources sys = Resources.getSystem();
        boolean english = "en".equals(Locale.getDefault().getLanguage());
        for (Object[] k : KINDS) {
            try {
                int id = sys.getIdentifier((String) k[0], "string", "android");
                if (id == 0 || !t.equals(sys.getString(id).trim())) continue;
                String own = (String) k[2];
                return new Match((Integer) k[1], english && own != null ? own : t);
            } catch (Throwable e) { /* not on this firmware */ }
        }
        return null;
    }

    // ---- system notices: USB debugging and the seat memory (MediaNotificationBlocker removes the stock bar) ----
    private static final String SYS_KEY = "ccnc_system_notice";

    /**
     * Android's "USB debugging connected" (UsbDeviceManager, package "android", no tag): its notification id is the
     * resource id of its title string (0x010404b0 on firmware 250818). 0 when this system doesn't have the string.
     * On this unit it is a plain auto-cancel notification (flags 0x10), posted once per adb connection.
     */
    static int adbId() {
        try { return Resources.getSystem().getIdentifier("adb_active_notification_title", "string", "android"); }
        catch (Throwable t) { return 0; }
    }

    /** The ccNC notice for the USB debugging notification (the system's own title), or null to leave it stock. */
    static Match matchAdb(Context c) {
        if (!on(c, SYS_KEY)) return null;
        try {
            int id = adbId();
            if (id == 0) return null;
            String s = Resources.getSystem().getString(id).trim();
            return s.length() == 0 ? null : new Match(INFO, s);
        } catch (Throwable t) { return null; }
    }

    /**
     * The seat memory (IMS) notices: SmartIMSService in the vehicle settings app posts them with
     * NotificationManager.notify("SmartIMSService", id, ..). The id is made from the clock when that app starts
     * (currentTimeMillis % 10000), so only the package and tag identify it.
     */
    static final String IMS_PKG = "com.android.vehiclesettings", IMS_TAG = "SmartIMSService";

    /**
     * {vehicle settings string name, icon, English text for the emulator test}. The notice is told apart by its text,
     * compared with that app's own strings in the unit's language, and shown with the same text. Left stock on
     * purpose: the rear seat relax and "reset the seat system" warnings (long, and not seat memory).
     */
    private static final Object[][] SEAT_KINDS = {
        {"drv_ims_ply_back_p1_req", SEAT, "Settings 1 applied."},
        {"drv_ims_ply_back_p2_req", SEAT, "Settings 2 applied."},
        {"drv_ims_ply_back_p3_req", SEAT, "Settings 3 applied."},
        {"drv_ims_memory_p1_req", SEAT, "Settings 1 saved."},
        {"drv_ims_memory_p2_req", SEAT, "Settings 2 saved."},
        {"drv_ims_memory_p3_req", SEAT, "Settings 3 saved."},
        {"drv_ims_ply_memory_step_ena_state", SEAT, "Press button to save settings."},
        {"drv_ims_ply_back_step_req", SEAT, "Driver settings paused."},
        {"seat_position_vr_osd_title", SEAT, "Adjusting the Seat Position."},
        {"drv_ims_con_warn_dis", WARN, "Not available while driving."},
    };

    /** The ccNC notice for a SmartIMSService notification, or null when its text isn't one of {@link #SEAT_KINDS}. */
    static Match matchSeat(Context c, Notification n) {
        if (n == null || !on(c, SYS_KEY)) return null;
        try {
            // Most are a custom layout (vehicle_settings_osd_layout) with the text set on it; one uses a normal title.
            CharSequence cs = n.extras == null ? null : n.extras.getCharSequence(Notification.EXTRA_TITLE);
            if (cs == null || cs.toString().trim().length() == 0) cs = remoteText(n.contentView);
            if (cs == null) return null;
            String t = cs.toString().trim();
            if (t.length() == 0) return null;
            Resources r = c.getPackageManager().getResourcesForApplication(IMS_PKG);
            for (Object[] k : SEAT_KINDS) {
                int id = r.getIdentifier((String) k[0], "string", IMS_PKG);
                if (id != 0 && t.equals(r.getString(id).trim())) return new Match((Integer) k[1], t);
            }
        } catch (Throwable e) { /* no such app, or the layout isn't what we know: stays stock */ }
        return null;
    }

    /**
     * The one text a RemoteViews sets with setTextViewText (Android 4.4 keeps it as a ReflectionAction "setText" in
     * mActions), or null when there is none or more than one.
     */
    private static CharSequence remoteText(RemoteViews rv) {
        if (rv == null) return null;
        try {
            java.lang.reflect.Field fa = RemoteViews.class.getDeclaredField("mActions");
            fa.setAccessible(true);
            java.util.List<?> actions = (java.util.List<?>) fa.get(rv);
            if (actions == null) return null;
            CharSequence found = null;
            for (Object a : actions) {
                if (a == null || !a.getClass().getName().endsWith("$ReflectionAction")) continue;
                java.lang.reflect.Field fm = a.getClass().getDeclaredField("methodName");
                fm.setAccessible(true);
                if (!"setText".equals(fm.get(a))) continue;
                java.lang.reflect.Field fv = a.getClass().getDeclaredField("value");
                fv.setAccessible(true);
                Object v = fv.get(a);
                if (!(v instanceof CharSequence)) continue;
                if (found != null) return null;
                found = (CharSequence) v;
            }
            return found;
        } catch (Throwable t) { return null; }
    }

    /**
     * Emulator test (debuggable builds only; a car build ignores it): shows the popup for a stock string name, e.g.
     *   adb shell am broadcast -n dev.ccnc.home/.ClimateNotice\$Test --es kind washer_intake_on
     *   adb shell am broadcast -n dev.ccnc.home/.ClimateNotice\$Test --es kind adb
     *   adb shell am broadcast -n dev.ccnc.home/.ClimateNotice\$Test --es kind drv_ims_ply_back_p1_req
     */
    public static final class Test extends android.content.BroadcastReceiver {
        @Override public void onReceive(Context c, android.content.Intent i) {
            if ((c.getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) == 0) return;
            String kind = i.getStringExtra("kind");
            for (Object[] k : KINDS) {
                if (!k[0].equals(kind)) continue;
                show(c, new Match((Integer) k[1], k[2] != null ? (String) k[2] : kind));
                return;
            }
            if ("adb".equals(kind)) {
                Match m = matchAdb(c);
                show(c, m != null ? m : new Match(INFO, "USB debugging connected"));
                return;
            }
            for (Object[] k : SEAT_KINDS) {
                if (!k[0].equals(kind)) continue;
                show(c, new Match((Integer) k[1], (String) k[2]));
                return;
            }
        }
    }

    // ---- window (main thread only) ----
    private static final Handler h = new Handler(Looper.getMainLooper());
    private static WindowManager wm;
    private static NoticeView view;
    private static FrameLayout box;
    private static WindowManager.LayoutParams lp;
    private static boolean attached, volumeUp;

    private static final Runnable closer = new Runnable() { @Override public void run() { hide(); } };

    /** Any thread (a notification listener is called on a binder thread on Android 4.4). */
    static void show(Context ctx, final Match m) {
        final Context c = ctx.getApplicationContext();
        h.post(new Runnable() { @Override public void run() {
            try { showNow(c, m); } catch (Throwable t) { Log.w(TAG, "show", t); }
        } });
    }

    private static void showNow(Context c, Match m) {
        if (wm == null) {
            wm = (WindowManager) c.getSystemService(Context.WINDOW_SERVICE);
            view = new NoticeView(c);
            box = new FrameLayout(c);   // a window doesn't apply its root view's translation/alpha (see VolumeOsdService)
            box.addView(view, new FrameLayout.LayoutParams(-1, -1));
        }
        view.set(m);
        if (!attached) {
            // The car (Android 4.4) uses the volume bar's system type; Android 8+ (emulator) only allows the overlay type.
            int type = android.os.Build.VERSION.SDK_INT >= 26 ? 2038 : VolumeOsdService.VOL_TYPE;
            lp = new WindowManager.LayoutParams(WIN_W, H, type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                    | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
            // Anchored like the volume bar: the driver's side (right on a right-hand-drive car), popup centred in it.
            lp.gravity = Gravity.BOTTOM | (Drive.rhd(c) ? Gravity.RIGHT : Gravity.LEFT);
            lp.y = volumeUp ? LIFT : 0;
            lp.windowAnimations = R.style.OsdNoAnim;
            view.active = false;
            try { wm.addView(box, lp); attached = true; } catch (Throwable t) { Log.w(TAG, "addView", t); return; }
        } else place();
        view.animate().setListener(null).cancel();   // a hide that is still running must not switch the content off
        boolean wasActive = view.active;
        view.active = true; view.invalidate();
        if (!wasActive || view.getTranslationY() > 0) {
            if (!wasActive) { view.setTranslationY(H); view.setAlpha(0f); }
            view.animate().translationY(0).alpha(1f).setDuration(260)
                .setInterpolator(new android.view.animation.DecelerateInterpolator()).setListener(null).start();
        }
        h.removeCallbacks(closer);
        h.postDelayed(closer, TIMEOUT_MS);
        Log.i(TAG, "notice: " + m.msg);
    }

    /** The window stays added, like the volume bar's; only its content is hidden. */
    private static void hide() {
        if (!attached || view == null) return;
        view.animate().cancel();
        view.animate().translationY(H).alpha(0f).setDuration(240).setInterpolator(new android.view.animation.AccelerateInterpolator())
            .setListener(new android.animation.AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(android.animation.Animator a) {
                    view.active = false; view.invalidate(); view.setTranslationY(0); view.setAlpha(1f);
                }
            }).start();
    }

    /** VolumeOsdService (main thread): its bar is up / gone, so the notice sits above it / at the bottom. */
    static void volumeBar(boolean up) {
        if (volumeUp == up) return;
        volumeUp = up;
        if (view != null && view.active) place();
    }

    private static void place() {
        int y = volumeUp ? LIFT : 0;
        if (!attached || lp == null || lp.y == y) return;
        lp.y = y;
        try { wm.updateViewLayout(box, lp); } catch (Throwable t) { Log.w(TAG, "move", t); }
    }

    // ---- drawing ----
    private static final class NoticeView extends View {
        volatile boolean active;
        private int icon;
        private String msg = "";
        private final Bitmap bg;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final Paint white = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Bitmap[] icons = new Bitmap[6]; // by icon constant: RECIRC .. WARN

        NoticeView(Context c) {
            super(c);
            bg = VolumeOsdService.OsdView.raw(c, R.raw.osd_bg_vol_9);
            tp.setTypeface(Fonts.get(c, Fonts.REGULAR)); tp.setColor(0xFFFFFFFF);
            icons[RECIRC] = VolumeOsdService.OsdView.raw(c, R.raw.osd_ic_clm_recirc);
            icons[WASHER] = VolumeOsdService.OsdView.raw(c, R.raw.osd_ic_clm_washer);
            icons[AIR] = VolumeOsdService.OsdView.raw(c, R.raw.osd_ic_clm_air);
            icons[INFO] = VolumeOsdService.OsdView.raw(c, R.raw.osd_ic_ntc_info);
            icons[SEAT] = VolumeOsdService.OsdView.raw(c, R.raw.osd_ic_ntc_seat);
            icons[WARN] = VolumeOsdService.OsdView.raw(c, R.raw.osd_ic_ntc_warning);
            // The seat drawing comes from the settings menu, where it is gold: white here, like the other OSD icons.
            white.setColorFilter(new android.graphics.PorterDuffColorFilter(0xFFFFFFFF, android.graphics.PorterDuff.Mode.SRC_IN));
        }

        void set(Match m) { icon = m.icon; msg = m.msg == null ? "" : m.msg; invalidate(); }

        @Override protected void onDraw(Canvas cv) {
            if (!active) return;
            int iw = iconW(), maxText = MAX_W - 2 * PAD - iw - GAP;
            tp.setTextSize(32);
            if (tp.measureText(msg) > maxText) tp.setTextSize(26);
            String s = TextUtils.ellipsize(msg, tp, maxText, TextUtils.TruncateAt.END).toString();
            float tw = tp.measureText(s);
            int w = Math.max(MIN_W, Math.min(MAX_W, (int) Math.ceil(tw) + 2 * PAD + iw + GAP));
            int x0 = (getWidth() - w) / 2;
            VolumeOsdService.OsdView.nine(cv, bg, new Rect(x0, 0, x0 + w, H), p);
            int start = x0 + (w - (iw + GAP + (int) Math.ceil(tw))) / 2;
            drawIcon(cv, start, (H - iw) / 2, iw);
            Paint.FontMetrics fm = tp.getFontMetrics();
            cv.drawText(s, start + iw + GAP, H / 2f - (fm.ascent + fm.descent) / 2f, tp);
        }

        /** The official ccNC OSD art for the notice (osd-icons.sh), fitted into the 64 px box at (l, t). */
        private void drawIcon(Canvas cv, int l, int t, int size) {
            Bitmap b = icons[icon >= 0 && icon < icons.length ? icon : RECIRC];
            if (b == null || b.getWidth() < 2) return;
            cv.drawBitmap(b, null, new Rect(l, t, l + size, t + size), icon == SEAT ? white : p);
        }

        /** The recirculation car is a wide, low drawing in a square image: give it more room so it reads. */
        private int iconW() { return icon == RECIRC ? 88 : ICON; }
    }
}
