package dev.ccnc.radio;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import dev.ccnc.common.Bg;
import dev.ccnc.common.Fonts;
import dev.ccnc.common.Palette;

/** ccNC-style AM/FM radio on the unit's own tuner service. */
public final class MainActivity extends Activity implements Radio.Listener, RadioView.Actions {

    private Radio.Backend backend;
    private RadioView view;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        dev.ccnc.common.Themes.apply(this); // shared ccNC theme
        backend = hasClass("com.hkmc.media.MicomRadioManager") ? new HkmcBackend(this) : new DemoBackend();

        FrameLayout root = new FrameLayout(this);
        root.addView(new Bg(this), new FrameLayout.LayoutParams(-1, -1));
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        root.addView(content, new FrameLayout.LayoutParams(-1, -1));
        content.addView(topBar());
        view = new RadioView(this, this);
        content.addView(view, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
    }

    @Override protected void onResume() { super.onResume(); backend.start(this); }

    @Override protected void onPause() { backend.stop(); super.onPause(); }

    @Override public void onRadio(Radio.State s) { view.setState(s); }

    @Override public void seek(boolean up) { backend.seek(up); }

    @Override public void band(int band) { backend.band(band); }

    @Override public void tune(int raw) { backend.tune(raw); }

    @Override public void preset(Radio.Preset p) { backend.selectPreset(p); }

    private View topBar() {
        LinearLayout bar = dev.ccnc.common.Chrome.topBar(this, "Radio");
        bar.setLayoutParams(new LinearLayout.LayoutParams(-1, Palette.px(this, 56)));
        return bar;
    }

    private TextView label(String t, float size, int color, int face) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextSize(size);
        v.setTextColor(color);
        v.setTypeface(Fonts.get(this, face));
        return v;
    }

    private static boolean hasClass(String n) {
        try { Class.forName(n); return true; } catch (Throwable t) { return false; }
    }
}
