package dev.ccnc.climate;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

/**
 * Entry point for the car's seat buttons: the stock climate service starts this (through ccnc.climatebtn).
 * It has no screen of its own. It opens or closes the {@link SeatOverlay} popup and finishes at once, so the
 * app on screen (CarPlay included) stays where it is. If the overlay can't be shown, the full climate screen
 * opens with the seat popup instead.
 */
public final class SeatOverlayActivity extends Activity {

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        boolean ok = false;
        try {
            ok = SeatOverlay.toggle(this);
        } catch (Throwable t) {
            Log.w(SeatOverlay.TAG, "toggle failed", t);
        }
        if (!ok) startActivity(new Intent(this, MainActivity.class).setAction(MainActivity.SEAT_POPUP));
        finish();
    }
}
