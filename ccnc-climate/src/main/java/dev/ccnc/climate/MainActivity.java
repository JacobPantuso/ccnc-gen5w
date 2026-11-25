package dev.ccnc.climate;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import dev.ccnc.common.Bg;
import dev.ccnc.common.Chrome;
import dev.ccnc.common.Fonts;
import dev.ccnc.common.Palette;

/**
 * ccNC-style climate screen ({@link ClimateView}). On the car it shows what the vehicle
 * reports and is view only; off the car it is an interactive demo.
 */
public final class MainActivity extends Activity implements Climate.Source.Listener, ClimateView.Callback {

    private Climate.Source source;
    private Climate.State state = new Climate.State();
    private Climate.State vehicleState;
    private ClimateView panel;
    private TextView controlPill;
    private DatcSource datc;
    private boolean control; // vehicle control opt-in (saved)

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        dev.ccnc.common.Themes.apply(this); // shared ccNC theme
        datc = new DatcSource();
        source = datc;
        control = getSharedPreferences("climate", MODE_PRIVATE).getBoolean("vehicle_control", false);

        FrameLayout root = new FrameLayout(this);
        root.addView(new Bg(this), new FrameLayout.LayoutParams(-1, -1));
        // ccNC's climate page fills the whole 1920 x 720 screen and draws its own bottom bar,
        // so it gets a slim status strip (home, date, clock) instead of the usual title bar.
        panel = new ClimateView(this, this);
        root.addView(panel, new FrameLayout.LayoutParams(-1, -1));
        root.addView(Chrome.statusStrip(this), new FrameLayout.LayoutParams(-1, Palette.px(this, 32), Gravity.TOP));
        root.addView(sourceBar(), lpLongPress());

        setContentView(root);
        seatsFrom(getIntent());
        source.start(this);
        refreshControlPill();
        if (!source.isVehicle()) { // no DATC service (bench, emulator): interactive demo
            source.stop();
            source = new Climate.DemoSource();
            source.start(this);
        }
    }

    @Override protected void onNewIntent(android.content.Intent i) {
        super.onNewIntent(i);
        setIntent(i);
        seatsFrom(i);
    }

    /** What the patched stock climate service sends for the car's seat and climate buttons (ccnc.climatebtn). */
    static final String SEAT_BUTTON = "com.hkmc.telematics.service.apps.climate.activities.SWRCFrontSeatHeatVentControl",
            CLIMATE_BUTTON = "com.hkmc.telematics.service.apps.climate.activities.InteriorClimateNew",
            // ClimateManager.mD() (seat key) and aa(Z) (climate panel/key), the launchers ccnc.climatebtn redirects:
            SEAT_POPUP = "com.hkmc.telematics.service.apps.climate.activities.SeatHeatVentPopup",
            CLIMATE_PANEL = "com.hkmc.telematics.service.apps.climate.activities.NewClimateControl";

    /** "seats" extra (or the stock popup's action): open the seat heat/vent popup, like the car's seat buttons do. */
    private void seatsFrom(android.content.Intent i) {
        if (i == null) return;
        String a = i.getAction();
        if (i.getBooleanExtra("seats", false) || SEAT_BUTTON.equals(a) || SEAT_POPUP.equals(a)) panel.showSeats();
        else if (CLIMATE_BUTTON.equals(a) || CLIMATE_PANEL.equals(a)) panel.hideSeats();
    }

    @Override protected void onDestroy() {
        source.stop();
        super.onDestroy();
    }

    @Override public void onClimate(Climate.State s) {
        if (source.isVehicle()) vehicleState = s.copy();
        state = s;
        render();
    }

    /** The panel changed {@link #state}. */
    @Override public void onChanged() {
        if (source.isVehicle()) {
            if (control && vehicleState != null) {
                datc.sendChanges(vehicleState, state); // the next reading from the car replaces the optimistic state
            } else { // view only: put back what the car reports
                if (vehicleState != null) state = vehicleState.copy();
                Toast.makeText(this, control ? "This car doesn't allow climate control from the head unit"
                        : "View only: turn on Vehicle control to change settings", Toast.LENGTH_SHORT).show();
            }
        } else {
            source.apply(state);
        }
        render();
    }

    private void refreshControlPill() {
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setCornerRadius(Palette.px(this, 18));
        g.setColor(control ? Palette.ACCENT_DIM : Palette.CARD);
        controlPill.setBackground(g);
        controlPill.setText(control ? "Vehicle control: ON" : "Vehicle control: OFF");
        controlPill.setTextColor(control ? Palette.TEXT : Palette.SUBTEXT);
    }

    private void toggleControl() {
        if (control) { setControl(false); return; }
        if (!datc.supportsControl()) {
            Toast.makeText(this, "This car doesn't allow climate control from the head unit", Toast.LENGTH_LONG).show();
            return;
        }
        new android.app.AlertDialog.Builder(this, android.app.AlertDialog.THEME_HOLO_DARK)
                .setTitle("Control the car's climate?")
                .setMessage("The commands sent here (A/C, Auto, Sync, recirculation, defog, fan, temperature) are the ones the stock "
                        + "climate service accepts, but their values are unconfirmed on this car. Nothing happens to safety systems, "
                        + "but a control may do the opposite of what you tap. Turn it off again from this switch.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Turn on", new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int w) { setControl(true); }
                }).show();
    }

    private void setControl(boolean on) {
        control = on;
        getSharedPreferences("climate", MODE_PRIVATE).edit().putBoolean("vehicle_control", on).apply();
        refreshControlPill();
    }

    private void render() {
        panel.setState(state, source.isVehicle());
    }

    private FrameLayout.LayoutParams lpLongPress() {
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(Palette.px(this, 200), Palette.px(this, 32), Gravity.TOP | Gravity.LEFT);
        p.leftMargin = Palette.px(this, 60);
        return p;
    }

    /** Invisible long-press area over the home button: the only way to reach the vehicle-control switch. */
    private View sourceBar() {
        controlPill = Controls.label(this, "", 13f, Palette.SUBTEXT, Fonts.SEMIBOLD); // never attached; keeps state text
        View v = new View(this);
        v.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View x) { if (source.isVehicle()) toggleControl(); return true; }
        });
        return v;
    }

    @Override public void onBack() { finish(); }
}
