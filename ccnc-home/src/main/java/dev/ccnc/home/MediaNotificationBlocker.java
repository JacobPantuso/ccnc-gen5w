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
    private static final int AV_OFF_NOTICE = 0x3E9;

    @Override public void onNotificationPosted(StatusBarNotification sbn) {
        try {
            if (SYSTEM.equals(sbn.getPackageName()) && sbn.getId() == AV_OFF_NOTICE) {
                cancelNotification(sbn.getPackageName(), sbn.getTag(), sbn.getId());
                Log.i(TAG, "removed the system Media off notice");
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
