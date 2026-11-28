package dev.ccnc.ev;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import dev.ccnc.common.Bg;
import dev.ccnc.common.Fonts;
import dev.ccnc.common.Palette;

/** ccNC-style EV overview: battery, range, charging and trip data from the unit's HEV service (read only). */
public final class MainActivity extends Activity implements EvView.Actions {

    private static final String STOCK = "com.hkmc.daudio.app.ev3/.MainActivity";

    private EvData data;
    private EvView view;
    private final Handler handler = new Handler();
    private boolean demo;
    private final Runnable poll = new Runnable() {
        @Override public void run() {
            view.setSnapshot(demo ? EvData.demo(getIntent().getBooleanExtra("charging", false)) : data.snapshot());
            handler.postDelayed(this, 3000);
        }
    };

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        dev.ccnc.common.Themes.apply(this); // shared ccNC theme
        data = new EvData(this);
        // Preview without a car (emulator, screenshots): am start ... --ez demo true [--ez charging true]. Never on a car with the HEV service.
        demo = !data.available() && getIntent().getBooleanExtra("demo", false);

        FrameLayout root = new FrameLayout(this);
        root.addView(new Bg(this), new FrameLayout.LayoutParams(-1, -1));
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        root.addView(content, new FrameLayout.LayoutParams(-1, -1));
        content.addView(topBar());
        view = new EvView(this, this);
        content.addView(view, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
    }

    @Override protected void onResume() { super.onResume(); handler.post(poll); }

    @Override protected void onPause() { handler.removeCallbacks(poll); super.onPause(); }

    @Override public void openStock() {
        // The Setting tab opens ccNC Setup's EV category; the stock EV app only if ccNC Setup isn't there.
        try {
            startActivity(new Intent().setComponent(new ComponentName("dev.ccnc.settings", "dev.ccnc.settings.MainActivity"))
                    .putExtra("open", "com.android.settings.ev")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            return;
        } catch (Throwable ignored) { }
        try {
            startActivity(new Intent(Intent.ACTION_MAIN).setComponent(ComponentName.unflattenFromString(STOCK))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Throwable t) {
            Toast.makeText(this, "The stock EV app isn't available", Toast.LENGTH_SHORT).show();
        }
    }

    private View topBar() {
        LinearLayout bar = dev.ccnc.common.Chrome.topBar(this, "Electric Vehicle");
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
