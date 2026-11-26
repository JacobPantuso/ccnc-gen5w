package dev.ccnc.navi;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import java.util.Locale;

/**
 * Everything ccNC Navigation asks of the stock navigation (com.mnsoft.navi, MnSoft's native engine) goes through this
 * class, so the interfaces can be checked on the car in one place: logcat -s ccncNavi. Found offline in the deodexed
 * HKMC_Navi_ExSL (private/navi/STOCK-NAVI.md has the smali references):
 *
 *   route      com.hkmc.intent.action.ACTION_ROUTE_SEARCH   (NaviOpenAPIRecevier; String lat/lon in degrees + name)
 *   show map   com.hkmc.intent.action.ACTION_SHOW_MAP       (same receiver)
 *   search     com.hkmc.intent.action.ACTION_SEARCH          (the map's own search screen: the stock fallback)
 *   cancel     com.mobis.intent.action.CANCEL_ROUTE_SHORTCUT (NaviETCReceiver, reason 3 = custom key)
 *   state      sticky com.mnsoft.navi.action.Route_Guide_Status / DestinationInfo, periodic NAVI_GPS_CHANGED
 *   charger    com.hkmc.telematics.gis.action.REQUEST_/RESPONSE_NEAREST_CHARGING_STATION
 *
 * Nothing here can break guidance: the engine treats all of these as it treats the same broadcasts from voice
 * recognition or the stock home. A hand-off that gets no answer opens the stock map instead (logged as a fallback).
 */
final class StockNavi {
    private StockNavi() {}

    static final String TAG = "ccncNavi";
    static final String PKG = "com.mnsoft.navi";
    static final ComponentName APP = new ComponentName(PKG, PKG + ".NaviApp");

    private static final String HKMC = "com.hkmc.intent.action.", EX = "com.hkmc.navi.";
    static final String ROUTE_SEARCH = HKMC + "ACTION_ROUTE_SEARCH", SHOW_MAP = HKMC + "ACTION_SHOW_MAP", SEARCH = HKMC + "ACTION_SEARCH";
    static final String CANCEL_ROUTE = "com.mobis.intent.action.CANCEL_ROUTE_SHORTCUT", SHORTCUT_REASON = "com.hkmc.mode.EXTRA_SHORTCUT_REASON";
    static final String GUIDE_STATUS = "com.mnsoft.navi.action.Route_Guide_Status", GUIDE_STATE = "ROUTE_GUIDE_STATE";
    static final String DEST_INFO = "com.mnsoft.navi.action.DestinationInfo", DX = "com.mnsoft.navi.action.extra.";
    static final String GPS = "com.hkmc.telematics.gis.action.NAVI_GPS_CHANGED";
    private static final String GIS = "com.hkmc.telematics.gis.";
    static final String REQ_CHARGER = GIS + "action.REQUEST_NEAREST_CHARGING_STATION", RES_CHARGER = GIS + "action.RESPONSE_NEAREST_CHARGING_STATION";
    /** What the engine sends while it works on a route (any one of them answers a hand-off). */
    private static final String[] ROUTE_ANSWERS = {"android.intent.action.VIT_START_ROUTESEARCH", "android.intent.action.VIT_END_ROUTESEARCH",
            "com.mnsoft.navi.action.RouteSearchState", "com.mnsoft.navi.action.Route_Guide_NewStatus", "com.mnsoft.navi.action.Route_Guide_Set",
            "com.hkmc.intent.actioin.ERROR_ROUTE_SEARCH" /* sic, the engine's spelling */, GUIDE_STATUS, DEST_INFO};
    private static final long ANSWER_MS = 10000, OPEN_DELAY_MS = 800;

    /** Guidance states of Route_Guide_Status (ModeManagerService: 0 no destination, 1 on route, 2 finished; 3 route select). */
    static final int NONE = 0, GUIDING = 1, ARRIVED = 2, SELECTING = 3, UNKNOWN = -1;

    private static final Handler main = new Handler(Looper.getMainLooper());

    static boolean installed(Context c) {
        try { c.getPackageManager().getPackageInfo(PKG, 0); return true; } catch (Throwable t) { return false; }
    }

    // ---- opening the stock map ----

    /** Opens the map, then (optionally) sends one of the hub shortcuts so it lands on that screen. */
    static boolean openMap(final Context c, final String shortcut) {
        final Context app = c.getApplicationContext();
        try {
            c.startActivity(new Intent(Intent.ACTION_MAIN).setComponent(APP).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Throwable t) {
            Log.w(TAG, "map: NaviApp not startable (" + t + ")");
            Toast.makeText(c, "The navigation app isn't available", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (shortcut != null) main.postDelayed(new Runnable() {
            @Override public void run() { app.sendBroadcast(new Intent(HKMC + shortcut)); Log.i(TAG, "map: sent " + shortcut); }
        }, OPEN_DELAY_MS);
        return true;
    }

    // ---- route hand-off ----

    /**
     * Starts guidance to the place in the stock engine. The engine brings its map to the front by itself; if nothing
     * answers within 10 s (engine still booting, or the interface differs on this firmware) the stock map is opened.
     */
    static boolean route(Context c, Place p) {
        if (p == null || !p.valid()) { Log.w(TAG, "route: no valid position"); return false; }
        Intent i = point(ROUTE_SEARCH, p);
        if (!p.address.isEmpty()) i.putExtra(EX + "EXTRA_ADDR", p.address);
        if (!p.phone.isEmpty()) i.putExtra(EX + "EXTRA_PHONENUM", p.phone);
        // EXTRA_ROUTE_OPTION (default true) and EXTRA_BG_SEARCH (default 0: bring the map up) are left to the engine.
        watchAnswer(c.getApplicationContext(), "route");
        try {
            c.sendBroadcast(i);
        } catch (Throwable t) {
            Log.w(TAG, "route: broadcast failed (" + t + "), opening the stock map");
            return openMap(c, "NAVI_SHORTCUT");
        }
        Log.i(TAG, "route: sent ACTION_ROUTE_SEARCH " + where(p));
        Places.addRecent(c, p);
        return true;
    }

    /** Centres the stock map on the place (no route). */
    static boolean showOnMap(Context c, Place p) {
        if (p == null || !p.valid()) return false;
        try { c.sendBroadcast(point(SHOW_MAP, p)); } catch (Throwable t) { Log.w(TAG, "show map: " + t); }
        Log.i(TAG, "show map: sent ACTION_SHOW_MAP " + where(p));
        return openMap(c, null);
    }

    /**
     * Stock fallback: the map's own search with the text (its results screen is stock-styled). Used only for free-text
     * place search and categories, which no stock interface answers to another app.
     */
    static boolean stockSearch(Context c, final String query) {
        final Context app = c.getApplicationContext();
        if (query == null || query.trim().isEmpty()) return openMap(c, "ACTION_NAVI_TOTALSEARCH");
        if (!openMap(c, null)) return false;
        main.postDelayed(new Runnable() {
            @Override public void run() {
                app.sendBroadcast(new Intent(SEARCH).setPackage(PKG).putExtra(EX + "EXTRA_SEARCH", query.trim()));
                Log.i(TAG, "search: FALLBACK to the stock search, sent ACTION_SEARCH (" + query.trim().length() + " chars)");
            }
        }, OPEN_DELAY_MS);
        return true;
    }

    static void cancelRoute(Context c) {
        c.sendBroadcast(new Intent(CANCEL_ROUTE).setPackage(PKG).putExtra(SHORTCUT_REASON, 3));
        Log.i(TAG, "cancel: sent CANCEL_ROUTE_SHORTCUT reason=3");
        watchAnswer(c.getApplicationContext(), "cancel");
    }

    private static Intent point(String action, Place p) {
        Intent i = new Intent(action).setPackage(PKG).addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        i.putExtra(EX + "EXTRA_LATITUDE", String.format(Locale.US, "%.6f", p.lat));
        i.putExtra(EX + "EXTRA_LONGITUDE", String.format(Locale.US, "%.6f", p.lon));
        String name = !p.name.isEmpty() ? p.name : p.address;
        if (!name.isEmpty()) i.putExtra(EX + "EXTRA_KEYWORD", name);
        return i;
    }

    private static String where(Place p) {
        // lat=/lon= keys: the developer menu's log export scrubs them.
        return String.format(Locale.US, "lat=%.4f lon=%.4f", p.lat, p.lon) + " (name " + p.name.length() + " chars)";
    }

    private static BroadcastReceiver watcher;

    /** Logs what the engine sends after a hand-off; opens the stock map when it stays silent (route only). */
    private static void watchAnswer(final Context app, final String what) {
        stopWatch(app);
        final boolean[] answered = {false};
        final BroadcastReceiver r = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                if (isInitialStickyBroadcast()) return; // the old sticky value, not an answer
                answered[0] = true;
                Log.i(TAG, what + " answered: " + i.getAction() + " " + dump(i.getExtras()));
            }
        };
        IntentFilter f = new IntentFilter();
        for (String a : ROUTE_ANSWERS) f.addAction(a);
        try { app.registerReceiver(r, f); watcher = r; } catch (Throwable t) { Log.w(TAG, "watch: " + t); return; }
        main.postDelayed(new Runnable() {
            @Override public void run() {
                if (watcher == r) stopWatch(app);
                if (answered[0]) return;
                if ("route".equals(what)) {
                    Log.w(TAG, "route hand-off unanswered after " + ANSWER_MS + " ms: FALLBACK, opening the stock map");
                    openMap(app, "NAVI_SHORTCUT");
                } else {
                    Log.w(TAG, what + " unanswered after " + ANSWER_MS + " ms");
                }
            }
        }, ANSWER_MS);
    }

    private static void stopWatch(Context app) {
        if (watcher == null) return;
        try { app.unregisterReceiver(watcher); } catch (Throwable ignored) {}
        watcher = null;
    }

    // ---- state ----

    /** The current guidance state from the engine's sticky broadcast, or UNKNOWN. */
    static int guidance(Context c) {
        Intent i = sticky(c, GUIDE_STATUS);
        return i == null ? UNKNOWN : i.getIntExtra(GUIDE_STATE, UNKNOWN);
    }

    /** The engine's current destination (sticky), or null. */
    static Place destination(Context c) {
        return destinationFrom(sticky(c, DEST_INFO));
    }

    static Place destinationFrom(Intent i) {
        if (i == null) return null;
        Place p = new Place(i.getStringExtra(DX + "PoiName"), i.getStringExtra(DX + "Address"),
                deg(i.getDoubleExtra(DX + "LAT", Double.NaN), 90), deg(i.getDoubleExtra(DX + "LON", Double.NaN), 180));
        return p.valid() ? p : null;
    }

    /** Current position from NAVI_GPS_CHANGED, or null. */
    static Place position(Intent i) {
        if (i == null) return null;
        Place p = new Place("", "", deg(i.getDoubleExtra(GIS + "extra.LAT", Double.NaN), 90), deg(i.getDoubleExtra(GIS + "extra.LON", Double.NaN), 180));
        return p.valid() ? p : null;
    }

    /**
     * The engine works in 1/360000 degree (its Open API multiplies by 360000); its broadcasts' unit isn't confirmed,
     * so a value out of the degree range is taken as MnSoft units.
     */
    static double deg(double v, double limit) {
        if (Double.isNaN(v)) return v;
        return Math.abs(v) > limit ? v / 360000.0 : v;
    }

    private static Intent sticky(Context c, String action) {
        try { return c.getApplicationContext().registerReceiver(null, new IntentFilter(action)); } catch (Throwable t) {
            Log.w(TAG, "sticky " + action + ": " + t);
            return null;
        }
    }

    // ---- nearest charging station ----

    static void requestCharger(Context c) {
        c.sendBroadcast(new Intent(REQ_CHARGER));
        Log.i(TAG, "charger: requested nearest charging station");
    }

    /** The answer of RESPONSE_NEAREST_CHARGING_STATION as a place (name, distance in address), or null. */
    static Place charger(Intent i, String[] distanceOut) {
        Bundle x = i.getExtras();
        Log.i(TAG, "charger response: " + dump(x));
        if (x == null) return null;
        Object d = x.get(GIS + "extra.REMAIN_DISTANCE");
        if (d != null && distanceOut != null) {
            Object u = x.get(GIS + "extra.REMAIN_DISTANCE_UNIT");
            distanceOut[0] = d + (u != null ? " " + unit(u) : "");
        }
        Object la = x.get(GIS + "extra.LAT"), lo = x.get(GIS + "extra.LON");
        Place p = new Place(x.getString(GIS + "extra.NAME"), "", Double.NaN, Double.NaN);
        if (la instanceof Number && lo instanceof Number) {
            p.lat = deg(((Number) la).doubleValue(), 90);
            p.lon = deg(((Number) lo).doubleValue(), 180);
        }
        return p;
    }

    private static String unit(Object u) {
        int v = u instanceof Number ? ((Number) u).intValue() : -1;
        return v == 0 ? "m" : v == 1 ? "km" : v == 2 ? "mi" : String.valueOf(u);
    }

    static String dump(Bundle b) {
        if (b == null) return "{}";
        StringBuilder sb = new StringBuilder();
        for (String k : b.keySet()) {
            Object v = b.get(k);
            String key = k.substring(k.lastIndexOf('.') + 1);
            // Positions under lat=/lon= (scrubbed on export); names and addresses only as lengths.
            if (key.equalsIgnoreCase("LAT") || key.equalsIgnoreCase("LON")) sb.append(key.toLowerCase(Locale.US)).append('=').append(v);
            else if (v instanceof String && (key.contains("Name") || key.contains("NAME") || key.contains("Address") || key.contains("ADDR")))
                sb.append(key).append("=<").append(((String) v).length()).append(" chars>");
            else sb.append(key).append('=').append(v);
            sb.append(' ');
        }
        return sb.toString().trim();
    }
}
