package dev.ccnc.common;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.SystemClock;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.HashMap;

/**
 * The cover of what's playing, for ccNC screens that open after it was sent. The car's sources send the cover
 * once, in its own now-playing update (artwork_raw_image), about a second after the track changes (Bluetooth:
 * after the phone delivers it over AVRCP); asking for an update again brings the title back but not the cover.
 * So ccNC Home, which always runs, collects the latest cover per source with the title it belongs to, and serves
 * it through its provider: content://dev.ccnc.home.theme/npart/&lt;action&gt;/&lt;title&gt; (only for that title).
 */
public final class NowPlayingArt {
    private NowPlayingArt() {}

    static final String TAG = "ccncArt";
    public static final String[] ACTIONS = {"com.hkmc.audio.usb.widgetupdate", "com.hkmc.audio.btaudio.widgetupdate",
            "com.hkmc.radio.widgetupdate", "com.hkmc.radio.dab.widgetupdate", "com.hkmc.sxmradio.widgetupdate",
            "com.hkmc.dipoaudio.widgetupdate", "com.hkmc.gpmaudio.widgetupdate", "com.hkmc.audio.mymusic.widgetupdate",
            "com.hkmc.audio.audiocd.widgetupdate", "com.hkmc.audio.mp3cd.widgetupdate", "com.hkmc.audio.ipod.widgetupdate"};
    /** A cover that came this shortly before a new title belongs to the new track (the order isn't fixed). */
    private static final long GRACE_MS = 3000;

    // ---- ccNC Home: collect ----

    private static final HashMap<String, String> lastTitle = new HashMap<String, String>(), artTitle = new HashMap<String, String>();
    private static final HashMap<String, Long> artAt = new HashMap<String, Long>();
    private static boolean collecting;

    /** ccNC Home's process only: listens to every source's now-playing update for good. */
    public static synchronized void collect(Context ctx) {
        if (collecting) return;
        collecting = true;
        final Context c = ctx.getApplicationContext();
        IntentFilter f = new IntentFilter();
        for (String a : ACTIONS) f.addAction(a);
        try {
            c.registerReceiver(new BroadcastReceiver() {
                @Override public void onReceive(Context x, Intent i) { onFeed(c, i); }
            }, f);
        } catch (Throwable t) { Log.w(TAG, "collect", t); }
    }

    private static File file(Context c, String action) { return new File(new File(c.getFilesDir(), "npart"), action + ".png"); }

    private static synchronized void onFeed(Context c, Intent i) {
        String a = i.getAction();
        if (a == null) return;
        if (i.hasExtra("valid_media") && !i.getBooleanExtra("valid_media", true)) {
            lastTitle.remove(a); artTitle.remove(a); file(c, a).delete();
            return;
        }
        long now = SystemClock.uptimeMillis();
        if (i.hasExtra("filename")) {
            String t = clean(i.getStringExtra("filename"));
            if (t != null && !t.equals(lastTitle.get(a))) {
                lastTitle.put(a, t);
                Long at = artAt.get(a);
                if (at != null && now - at < GRACE_MS) artTitle.put(a, t);   // the cover came just before its title
                else { artTitle.remove(a); file(c, a).delete(); }            // a new track: the old cover is stale
            }
        }
        byte[] b = i.getByteArrayExtra("artwork_raw_image");
        if (b != null && b.length > 0) {
            File f = file(c, a), tmp = new File(f.getPath() + ".tmp");
            try {
                f.getParentFile().mkdirs();
                FileOutputStream o = new FileOutputStream(tmp);
                try { o.write(b); } finally { o.close(); }
                if (!tmp.renameTo(f)) tmp.delete();
                artAt.put(a, now);
                String t = lastTitle.get(a);
                if (t != null) artTitle.put(a, t); else artTitle.remove(a);
            } catch (Throwable t) { tmp.delete(); Log.w(TAG, "save", t); }
        }
    }

    /** For ccNC Home's provider: the cover file for {@code action}, if it belongs to {@code title}; else null. */
    public static synchronized File serve(Context c, String action, String title) {
        if (action == null || title == null || !title.equals(artTitle.get(action))) return null;
        File f = file(c, action);
        return f.isFile() ? f : null;
    }

    // ---- every ccNC app: fetch ----

    /** The collected cover of {@code title} on source {@code action}, or null. Off the UI thread. */
    public static Bitmap fetch(Context c, String action, String title, int sample) {
        if (action == null || title == null || title.isEmpty()) return null;
        InputStream in = null;
        try {
            Uri u = Uri.parse("content://dev.ccnc.home.theme/npart/" + Uri.encode(action) + "/" + Uri.encode(title));
            in = c.getContentResolver().openInputStream(u);
            if (in == null) return null;
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inSampleSize = Math.max(1, sample);
            return BitmapFactory.decodeStream(in, null, o);
        } catch (Throwable t) {
            return null;   // no cover for this title (yet), or no ccNC Home
        } finally {
            if (in != null) try { in.close(); } catch (Throwable ignored) { }
        }
    }

    /** Title as the car sends it: trimmed, null when empty. */
    public static String clean(String s) {
        if (s == null) return null;
        s = s.trim();
        return s.isEmpty() ? null : s;
    }
}
