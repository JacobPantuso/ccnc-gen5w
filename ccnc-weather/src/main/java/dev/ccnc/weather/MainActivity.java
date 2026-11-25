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
    }

    @Override protected void onResume() { super.onResume(); feed.start(); }

    @Override protected void onPause() { feed.stop(); super.onPause(); }

    @Override public void onData(final Feed.Data d) {
        runOnUiThread(new Runnable() { @Override public void run() { view.setData(d); } });
    }
}
