package dev.ccnc.climate;

import android.content.Context;
import android.graphics.PixelFormat;
import android.util.Log;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.Toast;

/**
 * The seat heat/vent popup as a system overlay window. It is drawn over whatever is on screen, CarPlay
 * included, without starting an activity, so the app underneath stays in front. Opened and closed by the
 * car's seat buttons through {@link SeatOverlayActivity}: pressing the seat button again, or tapping outside
 * the popup, closes it. Climate source and control rules match {@link MainActivity}.
 *
 * <p>Needs android.permission.SYSTEM_ALERT_WINDOW (declared in the manifest; granted at install for an app
 * targeting below API 23). If the window can't be added, {@link #toggle} returns false and the caller falls
 * back to the full climate screen.
 */
final class SeatOverlay implements Climate.Source.Listener, ClimateView.Callback {

    static final String TAG = "ccncSeats";

    /** The overlay that is up now, or null. Only touched on the main thread. */
    private static SeatOverlay shown;

    private final Context app;
    private final WindowManager wm;
    private final DatcSource datc = new DatcSource();
    private final ClimateView view;
    private Climate.Source source = datc;
    private Climate.State state = new Climate.State();
    private Climate.State vehicleState;
    private boolean control;
    private boolean attached;

    private SeatOverlay(Context app) {
        this.app = app;
        dev.ccnc.common.Themes.apply(app); // shared ccNC theme, as MainActivity does
        wm = (WindowManager) app.getSystemService(Context.WINDOW_SERVICE);
        view = new ClimateView(app, this, true);
    }

    /** Opens the popup, or closes it when it is already up. False only if it could not be shown. */
    static boolean toggle(Context c) {
        if (shown != null) {
            shown.hide();
            return true;
        }
        return new SeatOverlay(c.getApplicationContext()).show();
    }

    private boolean show() {
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY,
                // Not focusable: the app underneath keeps focus, and its back/keys are not taken away.
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.LEFT;
        try {
            wm.addView(view, lp);
        } catch (Throwable t) {
            Log.w(TAG, "overlay not shown", t);
            return false;
        }
        attached = true;
        shown = this;

        control = app.getSharedPreferences("climate", Context.MODE_PRIVATE).getBoolean("vehicle_control", true);
        source.start(this);
        if (!source.isVehicle()) { // no DATC service (bench, emulator): interactive demo
            source.stop();
            source = new Climate.DemoSource();
            source.start(this);
        }
        view.setState(state, source.isVehicle());
        return true;
    }

    private void hide() {
        if (!attached) return;
        attached = false;
        if (shown == this) shown = null;
        try {
            wm.removeView(view);
        } catch (Throwable t) {
            Log.w(TAG, "remove", t);
        }
        source.stop();
    }

    @Override public void onClimate(Climate.State s) {
        if (source.isVehicle()) vehicleState = s.copy();
        state = s;
        view.setState(state, source.isVehicle());
    }

    /** The popup changed {@link #state}. Same rules as MainActivity: controls the car unless Vehicle control was turned off. */
    @Override public void onChanged() {
        if (source.isVehicle()) {
            if (control && vehicleState != null) {
                datc.sendChanges(vehicleState, state); // the next reading from the car replaces the optimistic state
            } else {
                if (vehicleState != null) state = vehicleState.copy();
                Toast.makeText(app, control ? "This car doesn't allow climate control from the head unit"
                        : "View only: turn on Vehicle control in Climate to change settings", Toast.LENGTH_SHORT).show();
            }
        } else {
            source.apply(state);
        }
        view.setState(state, source.isVehicle());
    }

    /** Tap outside the popup, or its close button. */
    @Override public void onBack() {
        hide();
    }
}
