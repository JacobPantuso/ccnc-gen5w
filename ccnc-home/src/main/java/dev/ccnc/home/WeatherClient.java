package dev.ccnc.home;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.os.SystemClock;
import android.util.Log;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Current weather from the head unit's own telematics weather service, the same feed
 * the stock home widget uses (reverse-engineered from Launcher_WP3, firmware on this unit):
 *
 * <ul>
 * <li>Request: broadcast {@code com.hkmc.intent.action.request_weather_update} (no extras).</li>
 * <li>Reply: broadcast {@code com.hkmc.intent.action.weather_update}, extra
 *     {@code ReadAndSaveWeatherParcelable} = ArrayList of
 *     {@code com.hkmc.telematics.weather.readandsaveweatherparcelable.ReadAndSaveWeatherInfoList}
 *     (classes live in the unit's bootclasspath, so they're read by reflection).
 *     [0].getmReadAndSaveWeatherInfoList()[0] is the current location;
 *     its getmReadAndSavePresentWeatherInfoList()[0] has getValue() (temperature in °C,
 *     9999.9999 = none) and getIcon() (condition code 1..40).</li>
 * <li>Unit: {@code content://com.hkmc.telematics.service.util.db.VehicleSettingProvider/tmu_info},
 *     last row, column {@code TempUnit}: 2 = °F, otherwise °C.</li>
 * <li>Condition text: the stock launcher's string resources, 40 in a row per table, picked by
 *     the Mobis "new GUI" build flags, read at runtime from {@code com.android.launcher3}.</li>
 * </ul>
 */
final class WeatherClient {

    interface Listener { void onWeather(String subtitle); }

    static final String TAG = "ccNCHome";
    static final String ACTION_UPDATE = "com.hkmc.intent.action.weather_update";
    static final String ACTION_ERROR = "com.hkmc.intent.action.weather_update_error";
    static final String ACTION_REQUEST = "com.hkmc.intent.action.request_weather_update";
    static final String EXTRA_LIST = "ReadAndSaveWeatherParcelable";
    private static final Uri TMU_INFO = Uri.parse("content://com.hkmc.telematics.service.util.db.VehicleSettingProvider/tmu_info");
    private static final int STRINGS_CLASSIC = 0x7f0a00f5;
    private static final int STRINGS_NEW_GUI = 0x7f0a011d;
    private static final int CODES = 40;
    private static final long REQUEST_INTERVAL_MS = 10 * 60 * 1000L;

    private final Context ctx;
    private final Listener listener;
    private final SharedPreferences prefs;
    private long lastRequest = -REQUEST_INTERVAL_MS;
    private boolean registered;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { handle(i); }
    };

    WeatherClient(Context ctx, Listener listener) {
        this.ctx = ctx;
        this.listener = listener;
        this.prefs = ctx.getSharedPreferences("home", Context.MODE_PRIVATE);
    }

    /** Last weather we showed (kept across restarts), or null. */
    String cached() { return prefs.getString("weather", null); }

    /** Last weather as {condition, temperature, place}; empty strings for parts not known. */
    String[] details() {
        return new String[]{prefs.getString("weather_cond", ""), prefs.getString("weather_temp", ""),
                prefs.getString("weather_place", "")};
    }

    void start() {
        if (!registered) {
            IntentFilter f = new IntentFilter(ACTION_UPDATE);
            f.addAction(ACTION_ERROR);
            Intent sticky = ctx.registerReceiver(receiver, f);
            registered = true;
            if (sticky != null) handle(sticky);
        }
        long now = SystemClock.elapsedRealtime();
        if (now - lastRequest >= REQUEST_INTERVAL_MS) {
            lastRequest = now;
            ctx.sendBroadcast(new Intent(ACTION_REQUEST));
            Log.i(TAG, "weather: requested update");
        }
    }

    void stop() {
        if (registered) {
            ctx.unregisterReceiver(receiver);
            registered = false;
        }
    }

    private void handle(Intent i) {
        if (ACTION_ERROR.equals(i.getAction())) {
            Log.w(TAG, "weather: service reported an error");
            return;
        }
        try {
            i.setExtrasClassLoader(ctx.getClassLoader());
            ArrayList<?> list = i.getParcelableArrayListExtra(EXTRA_LIST);
            if (list == null || list.isEmpty()) {
                Log.w(TAG, "weather: update without " + EXTRA_LIST);
                return;
            }
            Object info = first(call(list.get(0), "getmReadAndSaveWeatherInfoList"));
            Object present = info == null ? null : first(call(info, "getmReadAndSavePresentWeatherInfoList"));
            if (present == null) {
                Log.w(TAG, "weather: no present weather in update");
                return;
            }
            double celsius = ((Number) call(present, "getValue")).doubleValue();
            int code = ((Number) call(present, "getIcon")).intValue();
            String place = info == null ? null : (String) call(info, "getCurrentLocTitle");
            String condition = conditionText(code);
            String temp = formatTemp(celsius);
            Log.i(TAG, "weather: code=" + code + " (" + condition + ") value=" + celsius + "C place=" + place);

            StringBuilder sb = new StringBuilder();
            if (condition != null && !condition.isEmpty()) sb.append(condition);
            if (temp != null) {
                if (sb.length() > 0) sb.append('\n');
                sb.append(temp);
            }
            if (sb.length() == 0) return;
            String subtitle = sb.toString();
            prefs.edit().putString("weather", subtitle)
                    .putString("weather_cond", condition == null ? "" : condition)
                    .putString("weather_temp", temp == null ? "" : temp)
                    .putString("weather_place", place == null ? "" : place.trim())
                    .apply();
            listener.onWeather(subtitle);
        } catch (Throwable t) {
            // Different firmware, missing classes, or a changed parcel: keep the card without weather.
            Log.w(TAG, "weather: could not read update", t);
        }
    }

    private String formatTemp(double c) {
        if (Double.isNaN(c) || c >= 9999.0 || c <= -9999.0) return null;
        if (useFahrenheit()) return Math.round(c * 1.8 + 32) + "°F";
        return Math.round(c) + "°C";
    }

    private boolean useFahrenheit() {
        Cursor cur = null;
        try {
            cur = ctx.getContentResolver().query(TMU_INFO, null, null, null, null);
            if (cur != null && cur.moveToLast()) {
                int col = cur.getColumnIndex("TempUnit");
                if (col >= 0) return cur.getInt(col) == 2;
            }
        } catch (Throwable t) {
            Log.w(TAG, "weather: TempUnit not readable, using locale", t);
        } finally {
            if (cur != null) cur.close();
        }
        return Locale.US.getCountry().equals(Locale.getDefault().getCountry());
    }

    /** On-car check ({@code --ez diag true}): logs flags, unit source and both condition tables. */
    void logDiagnostics() {
        Log.i(TAG, "diag: newGui=" + newGui() + " fahrenheit=" + useFahrenheit());
        try {
            Resources r = ctx.createPackageContext(AppRepo.STOCK_LAUNCHER, 0).getResources();
            for (int base : new int[]{STRINGS_CLASSIC, STRINGS_NEW_GUI}) {
                for (int code = 1; code <= CODES; code++) {
                    int id = base + code - 1;
                    String v;
                    try {
                        v = r.getResourceTypeName(id) + "/" + r.getResourceEntryName(id) + " = " + r.getString(id);
                    } catch (Resources.NotFoundException e) {
                        v = "(missing)";
                    }
                    Log.i(TAG, "diag: table 0x" + Integer.toHexString(base) + " code " + code + ": " + v);
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "diag: stock launcher strings unavailable", t);
        }
    }

    /** Condition name for a weather code, from the stock launcher's own strings. */
    private String conditionText(int code) {
        if (code < 1 || code > CODES) return null;
        try {
            Resources r = ctx.createPackageContext(AppRepo.STOCK_LAUNCHER, 0).getResources();
            int[] bases = newGui() ? new int[]{STRINGS_NEW_GUI, STRINGS_CLASSIC} : new int[]{STRINGS_CLASSIC, STRINGS_NEW_GUI};
            for (int base : bases) {
                int id = base + code - 1;
                try {
                    if (!"string".equals(r.getResourceTypeName(id))) continue;
                    String s = r.getString(id).replace('\n', ' ').trim();
                    if (!s.isEmpty()) return s;
                } catch (Resources.NotFoundException ignored) {
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "weather: stock launcher strings unavailable", t);
        }
        return null;
    }

    /** Mirrors Launcher_WP3's table choice (LauncherUtils.K()): Mobis new-GUI build flags. */
    private static boolean newGui() {
        try {
            Class<?> f = Class.forName("com.mobis.config.MobisBuildFeature");
            for (String n : new String[]{"USE_ADVANCEDUI_KIA", "USE_ADVANCEDUI_HYUNDAI",
                    "USE_NEW_GUI_4TH_HYUNDAI", "USE_NEW_GUI_4TH_KIA"}) {
                try {
                    Field fl = f.getField(n);
                    if (fl.getBoolean(null)) return true;
                } catch (NoSuchFieldException ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static Object call(Object o, String method) throws Exception {
        return o.getClass().getMethod(method).invoke(o);
    }

    private static Object first(Object list) {
        if (!(list instanceof List)) return null;
        List<?> l = (List<?>) list;
        return l.isEmpty() ? null : l.get(0);
    }
}
