package dev.ccnc.weather;

import android.app.Activity;
import android.os.Bundle;
import android.widget.FrameLayout;

import dev.ccnc.common.Bg;

/** ccNC Weather: Forecast / Today tabs from the unit's weather service. */
public final class MainActivity extends Activity implements Feed.Listener {

    private Feed feed;
    private WeatherView view;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        dev.ccnc.common.Themes.apply(this);
        feed = new Feed(this, this);
        FrameLayout root = new FrameLayout(this);
        root.addView(new Bg(this), new FrameLayout.LayoutParams(-1, -1));
        view = new WeatherView(this);
        root.addView(view, new FrameLayout.LayoutParams(-1, -1));
        android.widget.LinearLayout bar = dev.ccnc.common.Chrome.topBar(this, "");
        if (bar instanceof dev.ccnc.common.Chrome.TopBar) ((dev.ccnc.common.Chrome.TopBar) bar).showDate = false; // ccNC's Forecast | Today tabs take the centre
        root.addView(bar, new FrameLayout.LayoutParams(-1, dev.ccnc.common.Palette.px(this, 56)));
        setContentView(root);
        // Debug builds only: "am start ... --ez demo true" fills the screen with made-up data (emulator has no service)
        if ((getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
                && getIntent().getBooleanExtra("demo", false)) view.setData(demo());
    }

    private static Feed.Data demo() {
        Feed.Data d = new Feed.Data();
        d.place = "Toronto, ON";
        int[] icons = {1, 3, 7, 4, 26, 39};
        java.util.Calendar cal = java.util.Calendar.getInstance();
        for (int i = 0; i < 6; i++) {
            Feed.Slot s = new Feed.Slot();
            s.icon = icons[i];
            s.temp = 8 + i;
            s.min = 1 + i;
            s.rain = i * 15;
            s.time = String.format(java.util.Locale.US, "%1$tY%1$tm%1$td", cal);
            cal.add(java.util.Calendar.DAY_OF_MONTH, 1);
            d.days.add(s);
        }
        int[] now = {2, 7, 1};
        for (int i = 0; i < 3; i++) {
            Feed.Slot s = new Feed.Slot();
            s.icon = now[i];
            s.dayNight = i == 2 ? 1 : 0;
            s.temp = 12 - i * 2;
            s.rain = 10 + i * 20;
            d.today.add(s);
        }
        return d;
    }

    @Override protected void onResume() { super.onResume(); feed.start(); }

    @Override protected void onPause() { feed.stop(); super.onPause(); }

    @Override public void onData(final Feed.Data d) {
        runOnUiThread(new Runnable() { @Override public void run() { view.setData(d); } });
    }
}
