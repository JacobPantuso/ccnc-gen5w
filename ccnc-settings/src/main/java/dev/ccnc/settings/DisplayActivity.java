package dev.ccnc.settings;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import dev.ccnc.common.Bg;
import dev.ccnc.common.Fonts;
import dev.ccnc.common.Palette;

/**
 * Display settings as the unit reports them, through {@code com.hkmc.setup.VideoSetupManager} (framework,
 * by reflection: the same service the stock Display app uses). Read-only: the stock app decides which mode
 * (auto, day, night, manual) a brightness change applies to, and a wrong guess could leave the screen too dim
 * to read, so changes open the stock screen.
 */
public final class DisplayActivity extends Activity {

    private static final String[][] ROWS = {
            {"Brightness", "getBright"},
            {"Contrast", "getContrast"},
            {"Saturation", "getSaturation"},
            {"Hue", "getHue"},
            {"Day dimming brightness", "getDimmingDayBrightness"},
            {"Night dimming brightness", "getDimmingNightBrightness"},
            {"Dimming mode", "getDim"},
            {"Minimum manual brightness", "getMinManualBrightLevel"},
            {"Last manual brightness", "getLastManualBrightLevel"},
            {"Brightness setting option", "getNewBrightnessSettingOption"},
            {"Light sensor", "isNewLightSensorOn"},
            {"Illumination", "getIlluminateStatus"},
            {"Rheostat (knob) dimming", "getRheostatOnOffStatus"},
    };

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        dev.ccnc.common.Themes.apply(this);
        Object mgr = null;
        try {
            mgr = Class.forName("com.hkmc.setup.VideoSetupManager").getConstructor(Context.class).newInstance(this);
        } catch (Throwable ignored) {
        }

        FrameLayout root = new FrameLayout(this);
        root.addView(new Bg(this), new FrameLayout.LayoutParams(-1, -1));
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        root.addView(content, new FrameLayout.LayoutParams(-1, -1));
        content.addView(topBar());
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        int pad = Palette.px(this, 28);
        col.setPadding(pad, Palette.px(this, 20), pad, pad);
        sv.addView(col);
        content.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        col.addView(text(mgr == null ? "The display service isn't available on this unit (sample values shown)" : "Current values from the display service", 14f, Palette.HINT, Fonts.REGULAR));
        col.addView(Ui.row(this, "Open Gen5W Stock Launcher", null, null, new View.OnClickListener() {
            @Override public void onClick(View v) { Ui.openStockLauncher(DisplayActivity.this); }
        }));

        for (int i = 0; i < ROWS.length; i++) col.addView(row(ROWS[i][0], value(mgr, ROWS[i][1], i)));

        TextView change = text("Change in display settings", 17f, Palette.TEXT, Fonts.SEMIBOLD);
        change.setGravity(Gravity.CENTER);
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setCornerRadius(Palette.px(this, 26));
        g.setColor(Palette.ACCENT_DIM);
        change.setBackground(g);
        change.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { openStock(); } });
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-1, Palette.px(this, 52));
        cl.topMargin = Palette.px(this, 18);
        col.addView(change, cl);
        setContentView(root);
    }

    private String value(Object mgr, String getter, int i) {
        String out = value0(mgr, getter, i);
        android.util.Log.i("ccncDisplay", getter + "=" + out);
        return out;
    }

    private String value0(Object mgr, String getter, int i) {
        if (mgr == null) return "code " + (i * 7 % 10); // preview off the car
        try {
            Object v = mgr.getClass().getMethod(getter).invoke(mgr);
            if (v instanceof Boolean) return (Boolean) v ? "On" : "Off";
            return "code " + v;
        } catch (Throwable t) {
            return "—";
        }
    }

    private View row(String name, String value) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(Palette.px(this, 18), Palette.px(this, 14), Palette.px(this, 18), Palette.px(this, 14));
        r.addView(text(name, 17f, Palette.TEXT, Fonts.SEMIBOLD), new LinearLayout.LayoutParams(0, -2, 1f));
        r.addView(text(value, 15f, Palette.SUBTEXT, Fonts.REGULAR));
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setCornerRadius(Palette.px(this, 14));
        g.setColor(Palette.CARD);
        r.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Palette.px(this, 8);
        r.setLayoutParams(lp);
        r.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { openStock(); } });
        return r;
    }

    private void openStock() {
        try {
            startActivity(new Intent(Intent.ACTION_MAIN).setComponent(
                    new ComponentName("com.android.displaysettings", "com.android.displaysettings.DisplaySetting"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Throwable t) {
            Toast.makeText(this, "Display settings aren't available", Toast.LENGTH_SHORT).show();
        }
    }

    private View topBar() {
        LinearLayout bar = dev.ccnc.common.Chrome.topBar(this, "Display");
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
