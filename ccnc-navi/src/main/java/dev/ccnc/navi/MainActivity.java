package dev.ccnc.navi;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Handler;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import dev.ccnc.common.Bg;
import dev.ccnc.common.Fonts;
import dev.ccnc.common.Palette;
import dev.ccnc.common.Popup;

/**
 * ccNC Navigation hub. The map and route guidance stay the unit's MnSoft engine (a native GL renderer that can't be
 * restyled); everything around it is ccNC: Search, Saved Places, Home, Work, Categories and the nearest charging
 * station, all handing the final route to the engine through {@link StockNavi}. Long press on Map: the engine's own
 * menu, current position, settings and Cancel Route. Mirrored for right-hand drive.
 */
public final class MainActivity extends Activity {

    private final Handler handler = new Handler();
    private Hub hub;
    private Place charger;
    private String chargerDist, chargerNote;
    private int guidance = StockNavi.UNKNOWN;
    private Place destination, home, work;

    private final BroadcastReceiver chargerReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            String[] d = new String[1];
            Place p = StockNavi.charger(i, d);
            if (p == null) return;
            charger = p;
            chargerDist = d[0];
            chargerNote = null;
            hub.invalidate();
        }
    };

    /** Live guidance state while the hub is visible (both broadcasts are sticky: the first delivery is the current value). */
    private final BroadcastReceiver stateReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            if (StockNavi.GUIDE_STATUS.equals(i.getAction())) guidance = i.getIntExtra(StockNavi.GUIDE_STATE, StockNavi.UNKNOWN);
            else destination = StockNavi.destinationFrom(i);
            hub.invalidate();
        }
    };

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        dev.ccnc.common.Themes.apply(this); // shared ccNC theme
        FrameLayout root = new FrameLayout(this);
        root.addView(new Bg(this), new FrameLayout.LayoutParams(-1, -1));
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        root.addView(content, new FrameLayout.LayoutParams(-1, -1));
        content.addView(dev.ccnc.common.Chrome.topBar(this, "Navigation"));
        hub = new Hub(this);
        content.addView(hub, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
    }

    @Override protected void onResume() {
        super.onResume();
        home = Places.home(this);
        work = Places.work(this);
        registerReceiver(chargerReceiver, new IntentFilter(StockNavi.RES_CHARGER));
        IntentFilter f = new IntentFilter(StockNavi.GUIDE_STATUS);
        f.addAction(StockNavi.DEST_INFO);
        registerReceiver(stateReceiver, f);
        hub.invalidate();
    }

    @Override protected void onPause() {
        try { unregisterReceiver(chargerReceiver); } catch (IllegalArgumentException ignored) {}
        try { unregisterReceiver(stateReceiver); } catch (IllegalArgumentException ignored) {}
        handler.removeCallbacksAndMessages(null);
        super.onPause();
    }

    // ---- actions ----

    private void search(int tab) {
        startActivity(new Intent(this, SearchActivity.class).putExtra(SearchActivity.EXTRA_TAB, tab));
    }

    private void goSaved(Place p, String label) {
        if (p != null) { StockNavi.route(this, p); return; }
        Toast.makeText(this, label + " isn't set: hold a place and choose Set as " + label, Toast.LENGTH_LONG).show();
        search(SearchActivity.TAB_RECENT);
    }

    private void findCharger() {
        if (charger != null && charger.valid()) { StockNavi.route(this, charger); return; }
        charger = null; chargerDist = null;
        chargerNote = "Looking…";
        StockNavi.requestCharger(this);
        hub.invalidate();
        handler.postDelayed(new Runnable() {
            @Override public void run() {
                if (charger == null) { chargerNote = "No answer. Tap to try again."; hub.invalidate(); }
            }
        }, 6000);
    }

    private boolean guiding() { return guidance == StockNavi.GUIDING || guidance == StockNavi.SELECTING; }

    private void mapMenu() {
        final List<String> labels = new ArrayList<String>();
        final List<Runnable> acts = new ArrayList<Runnable>();
        if (guiding()) { labels.add("Cancel Route"); acts.add(new Runnable() { @Override public void run() { cancel(); } }); }
        labels.add("Current Position"); acts.add(stock("ACTION_NAVI_CURRENTPOS"));
        labels.add("Navigation Menu"); acts.add(stock("ACTION_NAVI_MENU"));
        labels.add("Map Settings"); acts.add(stock("ACTION_NAVI_SETUP"));
        new Popup(this, "Map", "These open the navigation app's own screens")
                .options(labels.toArray(new String[labels.size()]), -1, new Popup.Pick() {
                    @Override public void onPick(int i) { acts.get(i).run(); }
                }).show();
    }

    private Runnable stock(final String shortcut) {
        return new Runnable() { @Override public void run() { StockNavi.openMap(MainActivity.this, shortcut); } };
    }

    private void cancel() {
        StockNavi.cancelRoute(this);
        Toast.makeText(this, "Route Cancelled", Toast.LENGTH_SHORT).show();
    }

    // ---- the page ----

    private final class Hub extends Board {
        Hub(Context c) { super(c); }

        private void tile(Canvas c, float l, float t, float w, float h, String icon, String title, String sub,
                          Runnable run, Runnable hold, boolean big) {
            l = mx(l, w);
            int i = box(l, t, w, h, run, hold);
            card(c, l, t, w, h, 20, isDown(i), Palette.CARD);
            float s = big ? 120f : 84f;
            if ("*star".equals(icon)) star(c, l + 30 + s / 2f, t + 28 + s / 2f, s * 0.42f, false, Palette.TEXT);
            else icon(c, icon, l + 30, t + 28, s);
            float titleY = sub != null ? t + h - (big ? 78f : 56f) : t + h - (big ? 50f : 34f);
            txt(c, title, l + 30, titleY, big ? 44f : 32f, Palette.TEXT, Fonts.SEMIBOLD, Paint.Align.LEFT, w - 50);
            if (sub != null) txt(c, sub, l + 30, t + h - (big ? 30f : 20f), 22f, Palette.SUBTEXT, Fonts.REGULAR, Paint.Align.LEFT, w - 50);
        }

        @Override protected void paint(Canvas c) {
            // The 440-tall grid is centred in the space above ccNC's 90 px bottom bar.
            final float oy = Math.max(0f, (H() - 90f - 440f) / 2f) - 24f;
            final float top = 24f + oy, low = 259f + oy;

            String mapSub = null;
            if (guiding() && destination != null) mapSub = "To " + destination.title();
            else if (guiding()) mapSub = "Guidance on";
            tile(c, 60f, top, 460f, 440f, "navi", "Map", mapSub,
                    new Runnable() { @Override public void run() { StockNavi.openMap(MainActivity.this, "NAVI_SHORTCUT"); } },
                    new Runnable() { @Override public void run() { mapMenu(); } }, true);
            if (guiding()) { // a Cancel Route pill in the Map tile's top right
                float pw = 190, ph = 56, pl = mx(60f + 460f - pw - 24f, pw), pt = top + 32f;
                int i = box(pl, pt, pw, ph, new Runnable() { @Override public void run() { cancel(); } }, null);
                card(c, pl, pt, pw, ph, ph / 2f, isDown(i), Palette.alpha(0xFFE5484D, 0x66));
                txtMid(c, "Cancel Route", pl + pw / 2f, pt + ph / 2f, 22, Palette.TEXT, Fonts.SEMIBOLD, Paint.Align.CENTER, pw - 16);
            }

            tile(c, 550f, top, 400f, 205f, "navi_search", "Search", null,
                    new Runnable() { @Override public void run() { search(SearchActivity.TAB_RECENT); } }, null, false);
            tile(c, 550f, low, 400f, 205f, "*star", "Saved Places", null,
                    new Runnable() { @Override public void run() { search(SearchActivity.TAB_FAVORITES); } }, null, false);

            tile(c, 980f, top, 400f, 205f, "navi_home", "Home", home != null ? home.subtitle().isEmpty() ? home.title() : home.subtitle() : "Not Set",
                    new Runnable() { @Override public void run() { goSaved(home, "Home"); } },
                    new Runnable() { @Override public void run() { search(SearchActivity.TAB_RECENT); } }, false);
            tile(c, 980f, low, 400f, 205f, "navi_office", "Work", work != null ? work.subtitle().isEmpty() ? work.title() : work.subtitle() : "Not Set",
                    new Runnable() { @Override public void run() { goSaved(work, "Work"); } },
                    new Runnable() { @Override public void run() { search(SearchActivity.TAB_RECENT); } }, false);

            tile(c, 1410f, top, 450f, 205f, "navi_poi", "Categories", null,
                    new Runnable() { @Override public void run() { startActivity(new Intent(MainActivity.this, CategoriesActivity.class)); } }, null, false);
            String title = charger != null && !charger.name.isEmpty() ? charger.name : "Charging Station";
            String sub = chargerNote != null ? chargerNote : chargerDist != null ? chargerDist + " · Tap to Go" : charger != null ? "Tap to Go" : "Find the Nearest";
            tile(c, 1410f, low, 450f, 205f, "navi_station", title, sub,
                    new Runnable() { @Override public void run() { findCharger(); } }, null, false);
        }
    }
}
