package dev.ccnc.profile;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.IBinder;

/**
 * ccNC Profile's way in to the start-up chooser (dev.ccnc.common.StartupChooser): ccNC Home runs the chooser in its
 * own process at start-up and only starts this service when it can't (an older ccNC Home, or its window couldn't be
 * added). Also the emulator preview: am startservice -n dev.ccnc.profile/.StartupChooser --ez preview true
 */
public final class StartupChooser extends Service {

    static final dev.ccnc.common.StartupChooser.Data DATA = new dev.ccnc.common.StartupChooser.Data() {
        @Override public Bitmap photo(Context c, int id) { return ProfileStore.photo(c, id); }
        @Override public int defaultProfile(Context c) { return ProfileStore.defaultProfile(c); }
        @Override public int autoAdvance(Context c) { return ProfileStore.autoAdvance(c); }
    };

    private boolean started;

    @Override public IBinder onBind(Intent i) { return null; }

    @Override public int onStartCommand(Intent i, int flags, int id) {
        if (!started) {
            started = true;
            boolean preview = i != null && i.getBooleanExtra("preview", false);
            if (!dev.ccnc.common.StartupChooser.open(this, preview, DATA, new Runnable() {
                @Override public void run() { stopSelf(); }
            })) stopSelf();
        }
        return START_NOT_STICKY;
    }

    @Override public void onDestroy() {
        dev.ccnc.common.StartupChooser.closeNow("service stopped");
        super.onDestroy();
    }
}
