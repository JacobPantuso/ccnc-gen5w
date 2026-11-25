package dev.ccnc.media;

/** Now-playing model plus the source that feeds it. */
final class Media {

    static final class State {
        String source = "Demo";           // Radio / USB / Bluetooth / Android Auto / Demo
        String title = "Midnight Drive";
        String artist = "Analog Sky";
        String album = "Neon Roads";
        long durationMs = 247000;
        long positionMs = 36400;
        boolean playing = true;
        boolean fromVehicle;
        boolean valid = true;             // false: the source has no track (placeholder text only)
        android.graphics.Bitmap art;
    }

    interface Source {
        interface Listener { void onMedia(State s); }
        void start(Listener l);
        void stop();
        boolean isVehicle();
        void command(int keyCode); // KeyEvent.KEYCODE_MEDIA_*
    }
}
