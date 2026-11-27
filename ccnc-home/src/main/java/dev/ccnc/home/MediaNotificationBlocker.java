package dev.ccnc.home;

import android.app.Notification;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Removes the Gen5W media / connection notifications (the "heads up" bar at the top, e.g. CarPlay's
 * carplaymusic_osd) as soon as they are posted: ccNC Home shows its own popup for track changes and
 * connections instead. Only these media and connection sources are touched; ongoing notifications
 * can't be cancelled and are left alone.
 * The climate system's recirculation notices (DATCService) are removed the same way and shown by
 * {@link ClimateNotice} at the bottom. So are Android's "USB debugging connected" and the vehicle settings app's
 * seat memory notices (SmartIMSService: "Settings 1 applied." and the like).
 *
 * Enabled with: settings put secure enabled_notification_listeners dev.ccnc.home/dev.ccnc.home.MediaNotificationBlocker
 * Undo:         settings put secure enabled_notification_listeners ""
 */
public final class MediaNotificationBlocker extends NotificationListenerService {

    static final String TAG = "ccncNoti";

    private static final Set<String> MEDIA = new HashSet<String>(Arrays.asList(
            "com.daudio.app.dipo",                  // Apple CarPlay
            "com.google.android.projection.sink",   // Android Auto
            "com.daudio.av.app.btaudio.ui",         // Bluetooth audio
            "com.android.btsettings",               // Bluetooth connect OSD
            "com.daudio.av.app.usbmusic",           // USB music
            "com.hkmc.av.app.radiona",              // Radio
            "com.hkmc.av.app.mediagate",            // Media
            "com.hkmc.av.app.naturesound",          // Sounds of Nature
            "com.daudio.app.phoneprojection"));     // phone projection connect

    /**
     * The system mode service's "Media off" notice (ModeManagerService, notification id = its notice type 0x3E9,
     * posted by the system process). At boot it waits for the last source to come back, and because the stock
     * launcher isn't in front (ccNC Home is) and ccnc.sideosd makes the right screen answer canShowSSOsd=false,
     * it shows this popup instead of the right screen's own media-off state. The sound comes back by itself
     * once CarPlay or Bluetooth audio connects, so the popup only gets in the way.
     */
    private static final String SYSTEM = "android";
    /**
     * The mode manager's own notices (ModeManagerService$ModeNotificationFactory, package "android", tag "default"):
     * 1000-1002 are its "Media off" family. 1000 is the start-up one (also removed by the LivePatch volume step); 1002
     * is the one a profile switch posts when that profile has no media source yet (av off reason 5, user change).
     */
    private static final int AV_OFF_FIRST = 1000, AV_OFF_LAST = 1002;

    /** A listener can't cancel a notification with these flags: such a one stays stock, with no ccNC popup. */
    private static final int STICKY = Notification.FLAG_ONGOING_EVENT | Notification.FLAG_FOREGROUND_SERVICE;
    /** When the USB debugging popup was last shown (elapsedRealtime): one popup per adb connection. */
    private static volatile long adbShownAt;

    @Override public void onNotificationPosted(StatusBarNotification sbn) {
        try {
            if (SYSTEM.equals(sbn.getPackageName()) && ClimateNotice.DATC_TAG.equals(sbn.getTag()) && sbn.getId() == ClimateNotice.DATC_ID) {
                // The climate system's notice (washer fluid / air quality -> recirculation): ccNC shows it at the bottom.
                android.os.Bundle ex = sbn.getNotification().extras;
                ClimateNotice.Match m = ClimateNotice.match(this, ex == null ? null : ex.getCharSequence(Notification.EXTRA_TITLE));
                if (m == null) return;   // not one ccNC replaces: stays stock
                cancelNotification(sbn.getPackageName(), sbn.getTag(), sbn.getId());
                ClimateNotice.show(this, m);
                Log.i(TAG, "replaced the climate notice");
                return;
            }
            if (SYSTEM.equals(sbn.getPackageName()) && sbn.getTag() == null && sbn.getId() != 0 && sbn.getId() == ClimateNotice.adbId()) {
                // Android's "USB debugging connected": only the notice is removed, adb itself isn't touched.
                if ((sbn.getNotification().flags & STICKY) != 0) return;   // ongoing on this system: can't be removed
                ClimateNotice.Match m = ClimateNotice.matchAdb(this);
                if (m == null) return;
                cancelNotification(sbn.getPackageName(), sbn.getTag(), sbn.getId());
                long now = android.os.SystemClock.elapsedRealtime();
                if (adbShownAt == 0 || now - adbShownAt > 5000) ClimateNotice.show(this, m);   // a repeat post: no second popup
                adbShownAt = now;
                Log.i(TAG, "replaced the USB debugging notice");
                return;
            }
            if (ClimateNotice.IMS_PKG.equals(sbn.getPackageName()) && ClimateNotice.IMS_TAG.equals(sbn.getTag())) {
                // The seat memory notice (settings applied / saved, after a profile switch or a seat button).
                if ((sbn.getNotification().flags & STICKY) != 0) return;
                ClimateNotice.Match m = ClimateNotice.matchSeat(this, sbn.getNotification());
                if (m == null) return;   // not one ccNC replaces: stays stock
                cancelNotification(sbn.getPackageName(), sbn.getTag(), sbn.getId());
                ClimateNotice.show(this, m);
                Log.i(TAG, "replaced the seat memory notice");
                return;
            }
            if (SYSTEM.equals(sbn.getPackageName()) && sbn.getId() >= AV_OFF_FIRST && sbn.getId() <= AV_OFF_LAST
                    && (sbn.getTag() == null || "default".equals(sbn.getTag()))) {
                cancelNotification(sbn.getPackageName(), sbn.getTag(), sbn.getId());
                Log.i(TAG, "removed the system Media off notice (id " + sbn.getId() + ")");
                return;
            }
            if (!MEDIA.contains(sbn.getPackageName())) return;
            if ((sbn.getNotification().flags & (Notification.FLAG_ONGOING_EVENT | Notification.FLAG_FOREGROUND_SERVICE)) != 0) return;
            cancelNotification(sbn.getPackageName(), sbn.getTag(), sbn.getId());
            Log.i(TAG, "removed " + sbn.getPackageName() + " tag=" + sbn.getTag() + " id=" + sbn.getId());
        } catch (Throwable t) {
            Log.w(TAG, "could not remove notification", t);
        }
    }

    @Override public void onNotificationRemoved(StatusBarNotification sbn) {}
}
