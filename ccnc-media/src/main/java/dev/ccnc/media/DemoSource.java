package dev.ccnc.media;

import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;

/** Simulated player used when no vehicle metadata is available. */
final class DemoSource implements Media.Source {

    private final Media.State state = new Media.State();
    private Listener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (state.playing) {
                state.positionMs += 1000;
                if (state.positionMs >= state.durationMs) state.positionMs = 0;
                emit();
            }
            handler.postDelayed(this, 1000);
        }
    };

    @Override public void start(Listener l) {
        listener = l;
        handler.post(tick);
        emit();
    }

    @Override public void stop() {
        handler.removeCallbacks(tick);
        listener = null;
    }

    @Override public boolean isVehicle() { return false; }

    @Override public void command(int keyCode) {
        if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
            state.playing = !state.playing;
        } else if (keyCode == KeyEvent.KEYCODE_MEDIA_NEXT) {
            state.positionMs = 0;
        } else if (keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS) {
            state.positionMs = 0;
        }
        emit();
    }

    private void emit() {
        if (listener != null) listener.onMedia(state);
    }
}
