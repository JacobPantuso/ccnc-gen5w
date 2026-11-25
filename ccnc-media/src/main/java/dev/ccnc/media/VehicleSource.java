package dev.ccnc.media;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.RemoteController;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * Vehicle-backed media source. Transport goes out as global media key events
 * (AudioManager.dispatchMediaKeyEvent, API 19), which the stock HKMC players
 * already respond to. Now-playing metadata comes from a RemoteController
 * registered on AudioManager; on stock HKMC builds that needs
 * MEDIA_CONTENT_CONTROL, so this may require installing as a system app on
 * the bench unit (NEMESIS root). When metadata is unavailable the demo
 * now-playing remains and only transport is live.
 */
final class VehicleSource implements Media.Source, RemoteController.OnClientUpdateListener {

    private static final String TAG = "ccncMedia";

    // RemoteController.PLAYBACK_STATE_* / MetadataEditor.METADATA_KEY_* are hidden
    // from current android.jar stubs; values are stable since API 19.
    static final int PLAYBACK_STATE_PLAYING = 3;
    static final int KEY_TITLE = 7, KEY_ARTIST = 2, KEY_ALBUM = 1, KEY_DURATION = 9;

    private final Context ctx;
    private final AudioManager am;
    private final Media.State state = new Media.State();
    private Listener listener;
    private RemoteController rc;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean registered;
    private boolean receiving;

    /** The units' own now-playing feed: each source app broadcasts "com.hkmc.<source>.widgetupdate". */
    private static final String[][] FEEDS = {
            {"com.hkmc.audio.usb.widgetupdate", "USB Music"},
            {"com.hkmc.audio.btaudio.widgetupdate", "Bluetooth Audio"},
            {"com.hkmc.radio.widgetupdate", "Radio"},
            {"com.hkmc.radio.dab.widgetupdate", "DAB"},
            {"com.hkmc.sxmradio.widgetupdate", "SiriusXM"},
            {"com.hkmc.dipoaudio.widgetupdate", "Apple CarPlay"},
            {"com.hkmc.gpmaudio.widgetupdate", "Android Auto"},
            {"com.hkmc.audio.mymusic.widgetupdate", "My Music"},
            {"com.hkmc.audio.audiocd.widgetupdate", "CD"},
            {"com.hkmc.audio.mp3cd.widgetupdate", "CD"},
            {"com.hkmc.audio.ipod.widgetupdate", "iPod"},
            {"com.hkmc.default.widgetupdate", "Media"},
    };

    private final android.content.BroadcastReceiver feed = new android.content.BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { onFeed(i); }
    };

    /**
     * Extras are only present for the fields that changed ("update_key" says which), so only
     * what's there is applied. Field names are those the stock home reads (Launcher_WP3 BTAudioView).
     */
    private void onFeed(Intent i) {
        String src = null;
        for (String[] f : FEEDS) if (f[0].equals(i.getAction())) src = f[1];
        if (src == null) return;
        Bundle x = i.getExtras();
        Log.i(TAG, "feed " + i.getAction() + " " + (x == null ? "{}" : keysOf(x)));
        if (src != null && !src.equals(state.source)) { state.source = src; state.art = null; state.title = null; state.artist = null; state.album = null; }
        // The stock sources send a placeholder ("Apple CarPlay is running...") with valid_media=false when no track is known.
        if (i.hasExtra("valid_media") && !i.getBooleanExtra("valid_media", true)) {
            state.valid = false;
            state.title = null; state.artist = null; state.album = null; state.art = null;
            state.positionMs = 0; state.durationMs = 0;
            receiving = true; state.fromVehicle = true;
            emit();
            return;
        }
        if (i.hasExtra("valid_media")) state.valid = true;
        if (i.hasExtra("filename")) state.title = i.getStringExtra("filename");
        if (i.hasExtra("artist_name")) { String ar = i.getStringExtra("artist_name"); int b = ar == null ? -1 : ar.indexOf('\u2022'); if (b >= 0) ar = ar.substring(0, b); state.artist = ar == null ? null : ar.trim(); }
        if (i.hasExtra("album_name")) state.album = i.getStringExtra("album_name");
        if (i.hasExtra("bluetooth_device_name")) state.album = i.getStringExtra("bluetooth_device_name");
        if (i.hasExtra("audio_total_time")) state.durationMs = ms(i.getLongExtra("audio_total_time", 0));
        if (i.hasExtra("audio_current_time")) state.positionMs = ms(i.getLongExtra("audio_current_time", 0));
        if (i.hasExtra("playstate")) state.playing = i.getIntExtra("playstate", 1) == 1;
        byte[] art = i.getByteArrayExtra("artwork_raw_image");
        if (art != null && art.length > 0) {
            try { state.art = android.graphics.BitmapFactory.decodeByteArray(art, 0, art.length); } catch (Throwable ignored) {}
        }
        receiving = true;
        state.fromVehicle = true;
        emit();
    }

    /** Times are seconds or milliseconds depending on the source; nothing real is under 20 s long in ms. */
    private static long ms(long t) { return t > 0 && t < 20000 ? t * 1000 : t; }

    private static String keysOf(Bundle b) {
        StringBuilder sb = new StringBuilder();
        for (String k : b.keySet()) {
            Object v = b.get(k);
            sb.append(k).append('=').append(v instanceof byte[] ? "byte[" + ((byte[]) v).length + "]" : String.valueOf(v)).append(' ');
        }
        return sb.toString().trim();
    }

    VehicleSource(Context ctx) {
        this.ctx = ctx;
        this.am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        // Real data only: nothing from the demo defaults.
        state.title = null; state.artist = null; state.album = null;
        state.source = "Media"; state.durationMs = 0; state.positionMs = 0; state.playing = false;
    }

    @Override public void start(Listener l) {
        this.listener = l;
        android.content.IntentFilter f = new android.content.IntentFilter();
        for (String[] s : FEEDS) f.addAction(s[0]);
        ctx.registerReceiver(feed, f);
        // Ask the running source app to send its current track.
        ctx.sendBroadcast(new Intent("com.hkmc.request.widgetupdate"));
        try {
            rc = new RemoteController(ctx, this);
            registered = am.registerRemoteController(rc);
            Log.i(TAG, "RemoteController registered=" + registered);
            state.fromVehicle = registered || receiving;
        } catch (SecurityException e) {
            Log.w(TAG, "RemoteController blocked (needs system app / MEDIA_CONTENT_CONTROL)", e);
        } catch (Throwable t) {
            Log.w(TAG, "RemoteController unavailable", t);
        }
        emit();
    }

    @Override public void stop() {
        try { ctx.unregisterReceiver(feed); } catch (IllegalArgumentException ignored) {}
        if (registered) {
            try { am.unregisterRemoteController(rc); } catch (Throwable ignored) {}
            registered = false;
        }
        listener = null;
    }

    @Override public boolean isVehicle() { return registered || receiving; }

    @Override public void command(int keyCode) {
        KeyEvent down = new KeyEvent(KeyEvent.ACTION_DOWN, keyCode);
        KeyEvent up = new KeyEvent(KeyEvent.ACTION_UP, keyCode);
        am.dispatchMediaKeyEvent(down);
        am.dispatchMediaKeyEvent(up);
    }

    // ---- RemoteController.OnClientUpdateListener ----

    @Override public void onClientChange(boolean clearing) {
        if (clearing) {
            state.title = null;
            state.artist = null;
            state.album = null;
            emit();
        }
    }

    @Override public void onClientPlaybackStateUpdate(int stateCode) {
        state.playing = stateCode == PLAYBACK_STATE_PLAYING;
        emit();
    }

    @Override public void onClientPlaybackStateUpdate(int stateCode, long stateChangeTimeMs,
            long currentPosMs, float speed) {
        state.playing = stateCode == PLAYBACK_STATE_PLAYING;
        state.positionMs = currentPosMs;
        emit();
    }

    @Override public void onClientMetadataUpdate(RemoteController.MetadataEditor editor) {
        String t = (String) editor.getObject(KEY_TITLE, null);
        String a = (String) editor.getObject(KEY_ARTIST, null);
        String al = (String) editor.getObject(KEY_ALBUM, null);
        long d = editor.getLong(KEY_DURATION, 0);
        if (t != null) state.title = t;
        if (a != null) state.artist = a;
        if (al != null) state.album = al;
        if (d > 0) state.durationMs = d;
        state.fromVehicle = true;
        emit();
    }

    @Override public void onClientTransportControlUpdate(int transportControlFlags) { }

    private void emit() {
        handler.post(new Runnable() {
            @Override public void run() {
                if (listener != null) listener.onMedia(state);
            }
        });
    }
}
