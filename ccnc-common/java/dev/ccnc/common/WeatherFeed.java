package dev.ccnc.common;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The head unit's telematics weather, in full (current conditions, humidity, wind, rain chance,
 * sun times and the forecast list). Same feed as the launcher's WeatherClient, which documents
 * how it was recovered from the stock home:
 * request broadcast {@code com.hkmc.intent.action.request_weather_update}, reply
 * {@code com.hkmc.intent.action.weather_update} with {@code ReadAndSaveWeatherParcelable}
 * (bootclasspath classes, read by reflection), unit from VehicleSettingProvider/tmu_info,
 * condition names from the stock launcher's own strings.
 */
public final class WeatherFeed {

    public static final String TAG = "ccncWeather";

    public static final class Day {
        public String time = "";   // raw weatherTime from the service
        public int code;
        public String condition = "";
        public double minC = Double.NaN, maxC = Double.NaN, rain = Double.NaN;
    }

    public static final class Report {
        public String place = "", updated = "", sunrise = "", sunset = "";
        public int code, humidity = -1;
        public String condition = "";
        public double tempC = Double.NaN, windSpeed = Double.NaN, rain = Double.NaN;
        public boolean fahrenheit;
        public final List<Day> days = new ArrayList<Day>();

        public boolean hasTemp() { return !Double.isNaN(tempC); }

        public String temp(double c) {
            if (Double.isNaN(c)) return "--";
            return fahrenheit ? Math.round(c * 1.8 + 32) + "°F" : Math.round(c) + "°C";
        }
    }

    public interface Listener { void onReport(Report r); }

    private static final String ACTION_UPDATE = "com.hkmc.intent.action.weather_update";
    private static final String ACTION_REQUEST = "com.hkmc.intent.action.request_weather_update";
    private static final String EXTRA_LIST = "ReadAndSaveWeatherParcelable";
    private static final Uri TMU_INFO = Uri.parse("content://com.hkmc.telematics.service.util.db.VehicleSettingProvider/tmu_info");
    private static final String STOCK_LAUNCHER = "com.android.launcher3";
    private static final int STRINGS_CLASSIC = 0x7f0a00f5, STRINGS_NEW_GUI = 0x7f0a011d, CODES = 40;
    private static final double NONE = 9999.0;

    private final Context ctx;
    private final Listener listener;
    private boolean registered;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { handle(i); }
    };

    public WeatherFeed(Context ctx, Listener listener) {
        this.ctx = ctx.getApplicationContext();
        this.listener = listener;
    }

    public void start() {
        if (!registered) {
            Intent sticky = ctx.registerReceiver(receiver, new IntentFilter(ACTION_UPDATE));
            registered = true;
            if (sticky != null) handle(sticky);
        }
        ctx.sendBroadcast(new Intent(ACTION_REQUEST));
    }

    public void stop() {
        if (registered) {
            try { ctx.unregisterReceiver(receiver); } catch (IllegalArgumentException ignored) {}
            registered = false;
        }
    }

    private void handle(Intent i) {
        try {
            i.setExtrasClassLoader(ctx.getClassLoader());
            ArrayList<?> list = i.getParcelableArrayListExtra(EXTRA_LIST);
            if (list == null || list.isEmpty()) return;
            Object info = first(call(list.get(0), "getmReadAndSaveWeatherInfoList"));
            Object present = info == null ? null : first(call(info, "getmReadAndSavePresentWeatherInfoList"));
            if (present == null) return;
            Report r = new Report();
            r.fahrenheit = useFahrenheit();
            r.tempC = valid(num(present, "getValue"));
            r.code = (int) num(present, "getIcon");
            r.condition = conditionText(r.code);
            r.humidity = (int) num(present, "getHumidity");
            r.windSpeed = valid(num(present, "getWindspeed"));
            r.rain = valid(num(present, "getRainfallProbability"));
            r.sunrise = str(present, "getSunriseTime");
            r.sunset = str(present, "getSunsetTime");
            r.place = str(info, "getCurrentLocTitle").trim();
            r.updated = str(info, "getUpdatedTime");
            Object fl = call(info, "getmReadAndSaveForecastWeatherInfoList");
            if (fl instanceof List) for (Object f : (List<?>) fl) {
                Day d = new Day();
                d.time = str(f, "getWeatherTime");
                d.code = (int) num(f, "getIcon");
                d.condition = conditionText(d.code);
                d.minC = valid(num(f, "getValueMinTemp"));
                d.maxC = valid(num(f, "getValueMaxTemp"));
                d.rain = valid(num(f, "getRainfallproability"));
                r.days.add(d);
            }
            Log.i(TAG, "report: " + r.place + " " + r.condition + " " + r.tempC + "C hum=" + r.humidity
                    + " wind=" + r.windSpeed + " rain=" + r.rain + " days=" + r.days.size());
            listener.onReport(r);
        } catch (Throwable t) {
            Log.w(TAG, "could not read weather update", t);
        }
    }

    private static double valid(double v) { return Double.isNaN(v) || Math.abs(v) >= NONE ? Double.NaN : v; }

    private static double num(Object o, String m) {
        try { return ((Number) call(o, m)).doubleValue(); } catch (Throwable t) { return Double.NaN; }
    }

    private static String str(Object o, String m) {
        try { Object v = call(o, m); return v == null ? "" : v.toString(); } catch (Throwable t) { return ""; }
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
            Log.w(TAG, "TempUnit not readable, using locale", t);
        } finally {
            if (cur != null) cur.close();
        }
        return Locale.US.getCountry().equals(Locale.getDefault().getCountry());
    }

    private String conditionText(int code) {
        if (code < 1 || code > CODES) return "";
        try {
            Resources r = ctx.createPackageContext(STOCK_LAUNCHER, 0).getResources();
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
            Log.w(TAG, "stock launcher strings unavailable", t);
        }
        return "";
    }

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

    private static Object call(Object o, String m) throws Exception { return o.getClass().getMethod(m).invoke(o); }

    private static Object first(Object list) {
        if (!(list instanceof List)) return null;
        List<?> l = (List<?>) list;
        return l.isEmpty() ? null : l.get(0);
    }

    /**
     * ccNC icon name for a condition, matched on the (English) text the stock home shows.
     * Unknown text falls back to the generic overcast cloud.
     */
    public static String iconName(String condition) {
        String c = condition == null ? "" : condition.toLowerCase(Locale.US);
        boolean rain = c.contains("rain") || c.contains("shower") || c.contains("drizzle");
        boolean snow = c.contains("snow") || c.contains("flurr") || c.contains("sleet");
        boolean later = c.contains("later") || c.contains("then");
        if (c.contains("thunder") || c.contains("storm")) return "thunderstorms";
        if (rain && snow) return later ? "rain_snow_clear_later" : "rain_snow";
        if (snow) return c.contains("clear") ? "snow_clear_later" : later ? "cloudy_snow_later" : "snow_rain";
        if (rain) return c.contains("clear") ? "rain_clear_later" : later ? "getting_cloudy_rain_later" : "showers";
        if (c.contains("mist") || c.contains("fog") || c.contains("haze")) return "misty";
        if (c.contains("overcast")) return "overcast";
        if (c.contains("broken") || c.contains("mostly cloud") || c.contains("cloudy")) {
            if (c.contains("clear") && later) return c.startsWith("clear") ? "clear_cloudy_later" : "cloudy_clearing_later";
            return "broken_clouds";
        }
        if (c.contains("few") || c.contains("partly") || c.contains("scattered")) return "few_clouds";
        if (c.contains("clear") || c.contains("sun") || c.contains("fair")) return "clear";
        return "overcast";
    }
}
