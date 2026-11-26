package dev.ccnc.settings;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.lang.reflect.Method;

import dev.ccnc.common.Art;
import dev.ccnc.common.Bg;
import dev.ccnc.common.Fonts;
import dev.ccnc.common.Palette;

/**
 * ccNC driver-assistance settings. Every setting the stock Vehicle Settings app lists is here, grouped
 * the way ccNC does, with ccNC's own illustrations. Current values come from the unit's
 * {@code com.hkmc.usm.USMManager} getters (framework, by reflection) and are shown as the raw code: the
 * codes are multi-state and mean different things per setting, so they are for reference only. These are
 * safety systems, so changes are made in the stock screen, which "Change in vehicle settings" opens.
 */
public final class DriverAssistActivity extends Activity {

    private static final String TAG = "ccncDA";

    private static final class Item {
        final String title, desc, getter;
        Item(String title, String desc, String getter) { this.title = Ui.titleCase(title); this.desc = desc; this.getter = getter; }
    }

    private static final class Group {
        final String title, art;
        final Item[] items;
        Group(String title, String art, Item... items) { this.title = Ui.titleCase(title); this.art = art; this.items = items; }
    }

    private static Item i(String t, String d, String g) { return new Item(t, d, "getAssistance_" + g); }

    private static final Group[] GROUPS = {
        new Group("Forward safety", "usm_da_005_02_sedan",
            i("Forward Collision-Avoidance Assist", "Warns and brakes for vehicles, pedestrians and cyclists ahead", "ForwardSafetyOnOff"),
            i("Warning timing", "How early the warning comes", "ForwardSafetyWarningTiming"),
            i("Cross-traffic assist", "Helps avoid collisions when turning at a junction", "ForwardCrossTrafficSafety"),
            i("Side safety", "Evasive steering assist", "ForwardSideSafety"),
            i("Pedal misapplication assist", "Limits acceleration when the wrong pedal is pressed", "PedalMisapplicationSafetyAssist"),
            i("Leading vehicle departure alert", "Tells you when the car ahead pulls away", "LeadingVehicleDepartureAlert")),
        new Group("Blind-spot safety", "usm_da_013_sedan",
            i("Blind-Spot Collision-Avoidance Assist", "Warns and helps steer clear of cars beside you", "BlindSpotSafetyOnOff"),
            i("Warning level", "Warning only, or warning with assist", "BlindSpotSafetyWarning"),
            i("Blind-Spot View Monitor", "Shows the side camera in the cluster when you signal", "BlindSpotSafetyView"),
            i("Safe Exit Assist", "Holds the door when traffic approaches", "BlindSpotSafetySafeExitAssist"),
            i("Safe Exit Warning", "Warns before opening a door into traffic", "BlindSpotSafetySafeExitWarning"),
            i("Rear cross-traffic safety", "Warns and brakes for cross traffic when reversing", "BlindSpotSafetyRearCrossTraffic")),
        new Group("Lane safety", "usm_da_011_04_sedan",
            i("Lane safety", "Lane departure warning and keeping assist", "LaneSafety"),
            i("Lane safety type", "Warning, assist or both", "LaneSafetyType"),
            i("Lane Following Assist", "Keeps the car centred in its lane", "LaneFollowing"),
            i("Lane warning beep", "Audible lane warning", "LkaWarningBeep")),
        new Group("Parking safety", "usm_da_012",
            i("Parking safety", "Parking distance warning and collision avoidance", "ParkingSafety"),
            i("Parking distance warning", "Sensor warnings while parking", "ParkingDistanceWarning"),
            i("Surround View Monitor auto-on", "Shows the surround view when parking", "SVMAutoOnOff")),
        new Group("Driver attention", "usm_da_015_on",
            i("Driver Attention Warning", "Alerts you when attention drops", "DriverAttentionWarning"),
            i("Driver attention (on/off)", "Warning switch", "DriverAttentionWarningOnOff"),
            i("Forward attention warning", "Alerts when you look away from the road", "ForwardAttentionWarn")),
        new Group("Cruise and highway", "usm_da_001",
            i("Smart Cruise Control", "Cruise control that follows the traffic", "SCCStatus"),
            i("Distance", "Following distance", "SCCDistance"),
            i("Response", "How quickly the car reacts", "SCCResponse"),
            i("Reaction speed", "Speed of the reaction", "SCCReactionSpeed"),
            i("Acceleration", "How firmly the car accelerates", "SCCAcceleration"),
            i("Highway Driving Assist", "Cruise and lane centring on the highway", "HighwayDriving"),
            i("Highway lane change assist", "Helps with lane changes on the highway", "HighwayLaneChangeAssist"),
            i("Auto slowdown for speed zones", "Slows for speed enforcement zones", "HighwayAutoSpeedZoneSlowdown"),
            i("Auto slowdown for curves", "Slows for upcoming curves", "HighwayAutoCurveZoneSlowdown")),
        new Group("Speed limit", "usm_da_003_07",
            i("Intelligent Speed Limit Assist", "Reads signs and shows the speed limit", "SpeedLimitStatus"),
            i("Speed limit offset", "Allowed margin above the limit", "SpeedLimitOffset"),
            i("Auto speed change", "Adjusts cruise speed to the limit", "SpeedLimitAutoSpeedChange"),
            i("Speed limit display", "Where the limit is shown", "SpeedLimitDisplay")),
        new Group("Warnings", "set_bg_sound_driver_assistance_warning",
            i("Warning volume", "Volume of safety warnings", "WarningVolume"),
            i("Warning timing", "When warnings sound", "WarningTiming"),
            i("Haptic warning", "Steering wheel vibration", "HapticWarning"),
            i("Auto off in N drive mode", "Safety features off in N mode", "DrivingSafetyAutoOFFinNDriveMode"),
            i("Driver assistance inhibited", "Systems currently suppressed by the car", "ADASInhibit")),
    };

    private Object usm;
    private LinearLayout rail;
    private FrameLayout pane;
    private int selected;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        dev.ccnc.common.Themes.apply(this); // shared ccNC theme
        try {
            usm = Class.forName("com.hkmc.usm.USMManager").getConstructor(Context.class).newInstance(this);
        } catch (Throwable t) {
            Log.i(TAG, "USM service unavailable: " + t);
        }
        FrameLayout root = new FrameLayout(this);
        root.addView(new Bg(this), new FrameLayout.LayoutParams(-1, -1));
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        root.addView(content, new FrameLayout.LayoutParams(-1, -1));
        content.addView(topBar());

        LinearLayout main = new LinearLayout(this);
        main.setOrientation(LinearLayout.HORIZONTAL);
        content.addView(main, new LinearLayout.LayoutParams(-1, 0, 1f));
        rail = new LinearLayout(this);
        rail.setOrientation(LinearLayout.VERTICAL);
        ScrollView rs = new ScrollView(this);
        rs.setVerticalScrollBarEnabled(false);
        rs.addView(rail);
        main.addView(rs, new LinearLayout.LayoutParams(Palette.px(this, 250), -1));
        View div = new View(this);
        div.setBackgroundColor(Palette.DIVIDER);
        main.addView(div, new LinearLayout.LayoutParams(1, -1));
        pane = new FrameLayout(this);
        main.addView(pane, new LinearLayout.LayoutParams(0, -1, 1f));

        for (int g = 0; g < GROUPS.length; g++) rail.addView(railRow(g));
        setContentView(root);
        show(0);
    }

    private int read(String getter) {
        if (usm == null) return Integer.MIN_VALUE;
        try { return ((Number) usm.getClass().getMethod(getter).invoke(usm)).intValue(); }
        catch (Throwable t) { return Integer.MIN_VALUE; }
    }

    private View railRow(final int g) {
        TextView t = text(GROUPS[g].title, 17f, Palette.SUBTEXT, Fonts.REGULAR);
        t.setPadding(Palette.px(this, 24), 0, Palette.px(this, 12), 0);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setLayoutParams(new LinearLayout.LayoutParams(-1, Palette.px(this, 52)));
        t.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { show(g); } });
        t.setTag(g);
        return t;
    }

    private void show(int g) {
        selected = g;
        for (int c = 0; c < rail.getChildCount(); c++) {
            TextView t = (TextView) rail.getChildAt(c);
            boolean on = (Integer) t.getTag() == g;
            t.setTextColor(on ? Palette.TEXT : Palette.SUBTEXT);
            t.setTypeface(Fonts.get(this, on ? Fonts.SEMIBOLD : Fonts.REGULAR));
            t.setBackgroundColor(on ? Palette.alpha(Palette.ACCENT, 0x33) : 0);
        }
        pane.removeAllViews();
        Group grp = GROUPS[g];
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        int pad = Palette.px(this, 28);
        row.setPadding(pad, Palette.px(this, 26), pad, pad);

        ImageView art = new ImageView(this);
        art.setScaleType(ImageView.ScaleType.FIT_CENTER);
        Bitmap bm = Art.da(this, grp.art);
        if (bm != null) art.setImageBitmap(bm);
        row.addView(art, new LinearLayout.LayoutParams(Palette.px(this, 300), -1));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.addView(col);
        col.addView(text(grp.title, 24f, Palette.TEXT, Fonts.SEMIBOLD));
        for (Item it : grp.items) col.addView(itemRow(it));
        TextView change = text("Change in vehicle settings", 17f, Palette.TEXT, Fonts.SEMIBOLD);
        change.setGravity(Gravity.CENTER);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setCornerRadius(Palette.px(this, 26));
        bg.setColor(Palette.ACCENT_DIM);
        change.setBackground(bg);
        change.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { openStock(); } });
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-1, Palette.px(this, 52));
        cl.topMargin = Palette.px(this, 18);
        col.addView(change, cl);
        row.addView(sv, new LinearLayout.LayoutParams(0, -1, 1f));
        pane.addView(row);
    }

    private View itemRow(Item it) {
        final int v = read(it.getter);
        Log.i(TAG, it.getter + "=" + (v == Integer.MIN_VALUE ? "n/a" : v));
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(Palette.px(this, 18), Palette.px(this, 8), Palette.px(this, 18), Palette.px(this, 8));
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.addView(text(it.title, 17f, Palette.TEXT, Fonts.SEMIBOLD));
        if (Ui.DESCRIPTIONS) t.addView(text(it.desc, 13f, Palette.HINT, Fonts.REGULAR));
        r.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        r.addView(text(v == Integer.MIN_VALUE || v < 0 ? "—" : "code " + v, 14f, Palette.SUBTEXT, Fonts.REGULAR));
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setCornerRadius(Palette.px(this, 14));
        bg.setColor(Palette.CARD);
        r.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Palette.px(this, 8);
        r.setLayoutParams(lp);
        r.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View x) { openStock(); } });
        return r;
    }

    /** ccNC Setup's Vehicle category, whose first menu is Driver Assistance (the stock screen is no longer opened). */
    private void openStock() {
        Ui.openSetup(this, "com.android.vehiclesettings.VehicleSetting");
    }

    private View topBar() {
        LinearLayout bar = dev.ccnc.common.Chrome.topBar(this, "Driver Assistance");
        bar.setLayoutParams(new LinearLayout.LayoutParams(-1, Palette.px(this, 56)));
        return bar;
    }

    private TextView text(String t, float size, int color, int face) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextSize(size);
        v.setTextColor(color);
        v.setTypeface(Fonts.get(this, face));
        return v;
    }
}
