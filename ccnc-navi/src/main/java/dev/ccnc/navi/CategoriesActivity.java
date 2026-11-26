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
import android.util.Log;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import dev.ccnc.common.Bg;
import dev.ccnc.common.Chrome;
import dev.ccnc.common.Fonts;
import dev.ccnc.common.Palette;

/**
 * ccNC POI categories. Charging Stations is answered by the engine itself (REQUEST_NEAREST_CHARGING_STATION: the
 * nearest one, shown here, tap again to go). The engine has no category search another app can read, so the other
 * categories hand their name to the map's own search (stock results list; logged by StockNavi as a fallback).
 */
public final class CategoriesActivity extends Activity {

    private static final String[][] CATS = {
            // label, keyword for the map's search, icon
            {"Charging Stations", null, "navi_station"},
            {"Gas Stations", "Gas Station", "navi_poi"},
            {"Parking", "Parking", "navi_poi"},
            {"Restaurants", "Restaurant", "navi_poi"},
            {"Coffee", "Coffee", "navi_poi"},
            {"Hotels", "Hotel", "navi_poi"},
            {"Hospitals", "Hospital", "navi_poi"},
            {"Pharmacies", "Pharmacy", "navi_poi"},
            {"Grocery Stores", "Grocery", "navi_poi"},
            {"Banks & ATMs", "ATM", "navi_poi"},
            {"Car Wash", "Car Wash", "navi_poi"},
            {"Dealers", null, "navi_poi"},
    };

    private final Handler handler = new Handler();
    private Grid grid;
    private Place charger;
    private String chargerDist, chargerNote;

    private final BroadcastReceiver chargerReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            String[] d = new String[1];
            Place p = StockNavi.charger(i, d);
            if (p == null) return;
            charger = p;
            chargerDist = d[0];
            chargerNote = null;
            grid.invalidate();
        }
    };

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        dev.ccnc.common.Themes.apply(this);
        FrameLayout root = new FrameLayout(this);
        root.addView(new Bg(this), new FrameLayout.LayoutParams(-1, -1));
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        root.addView(content, new FrameLayout.LayoutParams(-1, -1));
        content.addView(Chrome.topBar(this, "Categories"));
        grid = new Grid(this);
        content.addView(grid, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
    }

    @Override protected void onResume() {
        super.onResume();
        registerReceiver(chargerReceiver, new IntentFilter(StockNavi.RES_CHARGER));
    }

    @Override protected void onPause() {
        try { unregisterReceiver(chargerReceiver); } catch (IllegalArgumentException ignored) {}
        handler.removeCallbacksAndMessages(null);
        super.onPause();
    }

    private void tap(int i) {
        String[] cat = CATS[i];
        if (i == 0) { chargers(); return; }
        String keyword = cat[1] != null ? cat[1] : Fonts.kia() ? "Kia" : "Hyundai";
        Log.i(StockNavi.TAG, "category " + cat[0] + ": no ccNC result source, using the map's search");
        StockNavi.stockSearch(this, keyword);
    }

    private void chargers() {
        if (charger != null && charger.valid()) { StockNavi.route(this, charger); return; }
        charger = null; chargerDist = null; chargerNote = "Looking…";
        StockNavi.requestCharger(this);
        grid.invalidate();
        handler.postDelayed(new Runnable() {
            @Override public void run() {
                if (charger == null) { chargerNote = "No answer. Tap to try again."; grid.invalidate(); }
            }
        }, 6000);
    }

    private final class Grid extends Board {
        Grid(Context c) { super(c); }

        @Override protected void paint(Canvas c) {
            int cols = 4, rows = 3;
            float gap = 24, top = 24, side = 60, bottom = 90 + 24; // clear of ccNC's 90 px bottom bar
            float w = (W() - side * 2 - gap * (cols - 1)) / cols, h = (H() - top - bottom - gap * (rows - 1)) / rows;
            for (int i = 0; i < CATS.length; i++) {
                final int idx = i;
                float l = mx(side + (i % cols) * (w + gap), w), t = top + (i / cols) * (h + gap);
                String title = CATS[i][0], sub = null;
                if (i == 0) {
                    if (charger != null) { title = charger.name.isEmpty() ? "Charging Station" : charger.name; sub = chargerDist != null ? chargerDist + " · Tap to Go" : "Tap to Go"; }
                    else sub = chargerNote != null ? chargerNote : "Nearest One";
                }
                int b = box(l, t, w, h, new Runnable() { @Override public void run() { tap(idx); } }, null);
                card(c, l, t, w, h, 20, isDown(b), i == 0 && charger != null ? Palette.CARD_HIGHLIGHT : Palette.CARD);
                float s = Math.min(64, h - 70);
                icon(c, CATS[i][2], l + 24, t + (h - s) / 2f, s);
                float tx = l + 24 + s + 20, maxW = w - (tx - l) - 20;
                txtMid(c, title, tx, sub != null ? t + h * 0.38f : t + h / 2f, 28, Palette.TEXT, Fonts.SEMIBOLD, Paint.Align.LEFT, maxW);
                if (sub != null) txtMid(c, sub, tx, t + h * 0.7f, 21, Palette.SUBTEXT, Fonts.REGULAR, Paint.Align.LEFT, maxW);
            }
        }
    }
}
