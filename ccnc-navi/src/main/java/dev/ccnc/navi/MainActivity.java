package dev.ccnc.navi;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import dev.ccnc.common.Art;
import dev.ccnc.common.Bg;
import dev.ccnc.common.Fonts;
import dev.ccnc.common.Palette;

/**
 * ccNC-style navigation hub. The map itself is the unit's MnSoft navigation app, which draws its
 * own screen, so this page opens it at the right place with the same intents the stock home and
 * hard keys use (ACTION_NAVI_SHORTCUT / TOTALSEARCH / CURRENTPOS / MENU / SETUP), and adds the
 * nearest-charging-station lookup EV drivers want (REQUEST_/RESPONSE_NEAREST_CHARGING_STATION).
 */
public final class MainActivity extends Activity {

    private static final String TAG = "ccncNavi";
    private static final ComponentName NAVI = new ComponentName("com.mnsoft.navi", "com.mnsoft.navi.NaviApp");
    private static final String A_PREFIX = "com.hkmc.intent.action.";
    private static final String GIS = "com.hkmc.telematics.gis.";
    private static final String REQ_CHARGER = GIS + "action.REQUEST_NEAREST_CHARGING_STATION";
    private static final String RES_CHARGER = GIS + "action.RESPONSE_NEAREST_CHARGING_STATION";
    private static final String START_NAV = GIS + "action.START_NAVIGATION";

    private final Handler handler = new Handler();
    private Hub hub;
    private String chargerName, chargerDist, chargerNote;
    private double chargerLat = Double.NaN, chargerLon = Double.NaN;

    private final BroadcastReceiver chargerReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            Bundle x = i.getExtras();
            Log.i(TAG, "charger response: " + (x == null ? "{}" : dump(x)));
            if (x == null) return;
            String n = x.getString(GIS + "extra.NAME");
            if (n != null) chargerName = n;
            Object d = x.get(GIS + "extra.REMAIN_DISTANCE");
            if (d != null) chargerDist = d + (x.get(GIS + "extra.REMAIN_DISTANCE_UNIT") != null ? " " + unit(x.get(GIS + "extra.REMAIN_DISTANCE_UNIT")) : "");
            Object la = x.get(GIS + "extra.LAT"), lo = x.get(GIS + "extra.LON");
            if (la instanceof Number && lo instanceof Number) { chargerLat = ((Number) la).doubleValue(); chargerLon = ((Number) lo).doubleValue(); }
            chargerNote = null;
            hub.invalidate();
        }
    };

    private static String unit(Object u) {
        int v = u instanceof Number ? ((Number) u).intValue() : -1;
        return v == 0 ? "m" : v == 1 ? "km" : v == 2 ? "mi" : String.valueOf(u);
    }

    private static String dump(Bundle b) {
        StringBuilder sb = new StringBuilder();
        for (String k : b.keySet()) sb.append(k).append('=').append(b.get(k)).append(' ');
        return sb.toString().trim();
    }

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        dev.ccnc.common.Themes.apply(this); // shared ccNC theme
        FrameLayout root = new FrameLayout(this);
        root.addView(new Bg(this), new FrameLayout.LayoutParams(-1, -1));
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        root.addView(content, new FrameLayout.LayoutParams(-1, -1));
        content.addView(topBar());
        hub = new Hub(this);
        content.addView(hub, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
    }

    @Override protected void onResume() {
        super.onResume();
        registerReceiver(chargerReceiver, new IntentFilter(RES_CHARGER));
    }

    @Override protected void onPause() {
        try { unregisterReceiver(chargerReceiver); } catch (IllegalArgumentException ignored) {}
        handler.removeCallbacksAndMessages(null);
        super.onPause();
    }

    // ---- actions ----

    /** Opens the map, then sends the navigation shortcut so it lands on the wanted screen. */
    private void navi(final String action) {
        try {
            startActivity(new Intent(Intent.ACTION_MAIN).setComponent(NAVI).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Throwable t) {
            Toast.makeText(this, "The navigation app isn't available", Toast.LENGTH_SHORT).show();
            return;
        }
        if (action == null) return;
        handler.postDelayed(new Runnable() {
            @Override public void run() { sendBroadcast(new Intent(A_PREFIX + action)); Log.i(TAG, "sent " + action); }
        }, 800);
    }

    private void findCharger() {
        chargerName = null; chargerDist = null; chargerLat = chargerLon = Double.NaN;
        chargerNote = "…";
        sendBroadcast(new Intent(REQ_CHARGER));
        Log.i(TAG, "requested nearest charging station");
        hub.invalidate();
        handler.postDelayed(new Runnable() {
            @Override public void run() {
                if (chargerName == null && chargerDist == null && "…".equals(chargerNote)) {
                    chargerNote = "--";
                    hub.invalidate();
                }
            }
        }, 6000);
    }

    private void navigateToCharger() {
        if (Double.isNaN(chargerLat) || Double.isNaN(chargerLon)) { navi("ACTION_NAVI_TOTALSEARCH"); return; }
        Intent i = new Intent(START_NAV);
        i.putExtra(GIS + "extra.LAT", chargerLat);
        i.putExtra(GIS + "extra.LON", chargerLon);
        if (chargerName != null) i.putExtra(GIS + "extra.NAME", chargerName);
        sendBroadcast(i);
        navi(null);
    }

    // ---- the page ----

    private final class Hub extends View {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint bmp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final RectF r = new RectF();
        private final List<RectF> boxes = new ArrayList<RectF>();
        private final List<Runnable> runs = new ArrayList<Runnable>();
        private int down = -1;
        private float k;

        Hub(Context c) {
            super(c);
            line.setStyle(Paint.Style.STROKE);
            setClickable(true);
        }

        private int hit(float x, float y) {
            for (int i = boxes.size() - 1; i >= 0; i--) if (boxes.get(i).contains(x, y)) return i;
            return -1;
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: down = hit(e.getX(), e.getY()); invalidate(); return true;
                case MotionEvent.ACTION_UP:
                    int h = hit(e.getX(), e.getY());
                    int d = down;
                    down = -1;
                    invalidate();
                    if (h >= 0 && h == d) runs.get(h).run();
                    return true;
                case MotionEvent.ACTION_CANCEL: down = -1; invalidate(); return true;
                default: return true;
            }
        }

        private float X(float v) { return v * k; }

        private void txt(Canvas c, String t, float x, float y, float size, int color, int face, Paint.Align a, float maxW) {
            text.setTypeface(Fonts.get(getContext(), face));
            text.setTextSize(X(size));
            text.setColor(color);
            text.setTextAlign(a);
            if (maxW > 0 && text.measureText(t) > X(maxW)) t = TextUtils.ellipsize(t, new TextPaint(text), X(maxW), TextUtils.TruncateAt.END).toString();
            c.drawText(t, X(x), X(y), text);
        }

        private void tile(Canvas c, float l, float t, float w, float h, String icon, String title, String sub, final Runnable run, boolean big) {
            RectF b = new RectF(X(l), X(t), X(l + w), X(t + h));
            boxes.add(b);
            runs.add(run);
            boolean on = down == boxes.size() - 1;
            fill.setColor(on ? Palette.PRESS : Palette.CARD);
            c.drawRoundRect(b, X(20f), X(20f), fill);
            line.setStrokeWidth(Math.max(1f, X(2f)));
            line.setColor(Palette.BORDER);
            c.drawRoundRect(b, X(20f), X(20f), line);
            Bitmap ic = Art.icon(getContext(), icon);
            float s = big ? 120f : 84f;
            if (ic != null) {
                r.set(X(l + 30f), X(t + 28f), X(l + 30f + s), X(t + 28f + s));
                c.drawBitmap(ic, null, r, bmp);
            }
            txt(c, title, l + 30f, t + h - (big ? 78f : 56f), big ? 44f : 32f, Palette.TEXT, Fonts.SEMIBOLD, Paint.Align.LEFT, w - 50f);
            if (sub != null) txt(c, sub, l + 30f, t + h - (big ? 30f : 20f), 22f, Palette.SUBTEXT, Fonts.REGULAR, Paint.Align.LEFT, w - 50f);
        }

        @Override protected void onDraw(Canvas c) {
            // Tiles end at y 524 of the 620 design: clear of ccNC's 90 px bottom bar (back/home, now playing).
            k = Math.min(getWidth() / 1920f, getHeight() / 620f);
            // The 440-tall grid is centred in the space above ccNC's 90 px bottom bar.
            final float oy = Math.max(0f, (getHeight() / k - 90f - 440f) / 2f) - 24f;
            boxes.clear();
            runs.clear();
            tile(c, 60f, 24f + oy, 460f, 440f, "navi", "Map", null, new Runnable() { @Override public void run() { navi("ACTION_NAVI_SHORTCUT"); } }, true);
            tile(c, 550f, 24f + oy, 400f, 205f, "navi_search", "Search", null, new Runnable() { @Override public void run() { navi("ACTION_NAVI_TOTALSEARCH"); } }, false);
            tile(c, 980f, 24f + oy, 400f, 205f, "current", "Current Position", null, new Runnable() { @Override public void run() { navi("ACTION_NAVI_CURRENTPOS"); } }, false);
            tile(c, 550f, 259f + oy, 400f, 205f, "navi_poi", "Menu", null, new Runnable() { @Override public void run() { navi("ACTION_NAVI_MENU"); } }, false);
            tile(c, 980f, 259f + oy, 400f, 205f, "set_navi", "Settings", null, new Runnable() { @Override public void run() { navi("ACTION_NAVI_SETUP"); } }, false);

            // Nearest charging station.
            String title = chargerName != null ? chargerName : "Charging Station";
            String sub = chargerNote != null ? chargerNote : chargerDist != null ? chargerDist : null;
            final boolean haveOne = chargerName != null || chargerDist != null;
            tile(c, 1410f, 24f + oy, 450f, 440f, "navi_station", title, sub,
                    new Runnable() { @Override public void run() { if (haveOne) navigateToCharger(); else findCharger(); } }, true);
        }
    }

    private View topBar() {
        LinearLayout bar = dev.ccnc.common.Chrome.topBar(this, "Navigation");
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
