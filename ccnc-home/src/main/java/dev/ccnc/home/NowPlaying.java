package dev.ccnc.home;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

/**
 * Follows the unit's own now-playing feed ("com.hkmc.&lt;source&gt;.widgetupdate", the same one the stock
 * home widget reads) so the Media card can show the track and its cover. A source with nothing to
 * report sends valid_media=false and placeholder text; that is treated as "no track".
 */
final class NowPlaying {

    interface Listener { void onNowPlaying(NowPlaying n); }

    private static final String[] ACTIONS = {
            "com.hkmc.audio.usb.widgetupdate", "com.hkmc.audio.btaudio.widgetupdate", "com.hkmc.radio.widgetupdate",
            "com.hkmc.radio.dab.widgetupdate", "com.hkmc.sxmradio.widgetupdate", "com.hkmc.dipoaudio.widgetupdate",
            "com.hkmc.gpmaudio.widgetupdate", "com.hkmc.audio.mymusic.widgetupdate", "com.hkmc.audio.audiocd.widgetupdate",
            "com.hkmc.audio.mp3cd.widgetupdate", "com.hkmc.audio.ipod.widgetupdate"};

    String action = "", title, artist;
    Bitmap art;
    boolean valid;

    /** True when the track comes from the connected phone (CarPlay, Android Auto or Bluetooth audio). */
    boolean fromPhone() {
        return action.contains("dipoaudio") || action.contains("gpmaudio") || action.contains("btaudio");
    }

    private final Context ctx;
    private final Listener listener;
    private boolean on;

    private final BroadcastReceiver rx = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { handle(i); }
    };

    NowPlaying(Context ctx, Listener l) { this.ctx = ctx.getApplicationContext(); this.listener = l; }

    void start() {
        if (on) return;
        on = true;
        IntentFilter f = new IntentFilter();
        for (String a : ACTIONS) f.addAction(a);
        try { ctx.registerReceiver(rx, f); } catch (Throwable ignored) {}
        try { ctx.sendBroadcast(new Intent("com.hkmc.request.widgetupdate")); } catch (Throwable ignored) {}
    }

    void stop() {
        if (!on) return;
        on = false;
        try { ctx.unregisterReceiver(rx); } catch (Throwable ignored) {}
    }

    private void handle(Intent i) {
        if (!i.getAction().equals(action)) { action = i.getAction(); title = null; artist = null; art = null; valid = false; }
        if (i.hasExtra("valid_media") && !i.getBooleanExtra("valid_media", true)) {
            title = null; artist = null; art = null; valid = false;
        } else {
            if (i.hasExtra("valid_media")) valid = true;
            if (i.hasExtra("filename")) {
                String t = clean(i.getStringExtra("filename"));
                // A new track: its artist and cover arrive in separate updates, so drop the previous ones.
                if (t != null && !t.equals(title)) { artist = null; art = null; }
                title = t; valid = title != null;
            }
            if (i.hasExtra("artist_name")) {
                String a = i.getStringExtra("artist_name");
                // Apple appends the audio format to the artist ("Artist • Lossless").
                artist = clean(a == null ? null : a.replaceAll("\\s*[\u2022\u00B7]\\s*(Hi-Res Lossless|Lossless|Dolby Atmos|Spatial Audio)\\s*$", ""));
            }
            byte[] b = i.getByteArrayExtra("artwork_raw_image");
            if (b != null && b.length > 0) {
                try {
                    BitmapFactory.Options o = new BitmapFactory.Options();
                    o.inSampleSize = 2;
                    art = BitmapFactory.decodeByteArray(b, 0, b.length, o);
                } catch (Throwable ignored) {}
            }
        }
        listener.onNowPlaying(this);
    }

    private static String clean(String s) { return s == null || s.trim().isEmpty() ? null : s.trim(); }
}
