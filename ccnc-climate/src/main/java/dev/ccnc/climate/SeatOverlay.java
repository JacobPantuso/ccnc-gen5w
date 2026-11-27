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
        view.setOnTouchListener(new android.view.View.OnTouchListener() {   // any touch keeps it open another 10 s
            @Override public boolean onTouch(android.view.View v, android.view.MotionEvent e) { bumpIdle(); return false; }
        });
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
                // TYPE_SYSTEM_ALERT, not TYPE_SYSTEM_OVERLAY: on Android 4.4 a system overlay never receives touches,
                // so its close button and tap-outside did nothing and the popup could only go with the seat button.
                // (Android 8+, emulator tests only: TYPE_APPLICATION_OVERLAY, the same thing there.)
                android.os.Build.VERSION.SDK_INT >= 26 ? 2038 : WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
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
        bumpIdle();
        return true;
    }

    /** Closes by itself after this long without a touch, like the stock popup: it can never be left stuck on screen. */
    private static final long IDLE_MS = 10000;
    private final android.os.Handler ui = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable idle = new Runnable() { @Override public void run() { Log.i(TAG, "closed after " + IDLE_MS + " ms idle"); hide(); } };

    private void bumpIdle() { ui.removeCallbacks(idle); if (attached) ui.postDelayed(idle, IDLE_MS); }

    private void hide() {
        ui.removeCallbacks(idle);
        if (!attached) return;
        attached = false;
        if (shown == this) shown = null;
        try {
            wm.removeView(view);
        } catch (Throwable t) {
            Log.w(TAG, "remove", t);
        }
        source.stop();
        // The stock climate service told CarPlay "car popup opening" (setoempopupstatus.ACTIVE) when the seat button
        // was pressed, so CarPlay lent the screen to the car and went Home. On DISMISS CarPlay only gives the screen
        // back if the popup is still in front, if the app in front is the STOCK launcher (it checks the class name
        // com.android.launcher3.view.Launcher), or if the DISMISS says isSSOSDType. With ccNC Home in front the first
        // two never match and CarPlay keeps the screen for the car; isSSOSDType makes it give the screen back.
        SeatOverlayActivity.dismissCarPlay(app);
    }

    private final Climate.Hold hold = new Climate.Hold();

    @Override public void onClimate(Climate.State s) {
        if (source.isVehicle()) vehicleState = s.copy();
        if (source.isVehicle() && !hold.accept(s)) return;   // a tap the car hasn't applied yet stays on screen
        state = s;
        view.setState(state, source.isVehicle());
    }

    /** The popup changed {@link #state}. Same rules as MainActivity: controls the car unless Vehicle control was turned off. */
    @Override public void onChanged() {
        bumpIdle();
        if (source.isVehicle()) {
            if (control && vehicleState != null) {
                hold.tapped(state);
                datc.sendChanges(vehicleState, state); // shown at once; the car's reading takes over once it matches
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
