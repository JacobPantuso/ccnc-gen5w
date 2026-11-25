package dev.ccnc.weather;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The unit's weather service reply, read the way ccNC's Weather screen needs it: the "Today" tab
 * (now / 3 hours later / 6 hours later) and the forecast days. Same broadcast as ccnc-common's
 * WeatherFeed (request com.hkmc.intent.action.request_weather_update, reply ...weather_update with
 * the ReadAndSaveWeatherParcelable bootclasspath class, read by reflection), but keeps every present-weather
 * entry and the day/night flag, which WeatherFeed drops.
 */
final class Feed {

    static final String TAG = "ccncWeather";

    /** One "Today" column or one forecast day. NaN / -1 = no value. */
    static final class Slot {
        int icon;            // ccNC icon code 1..40, 0 = none
        int dayNight;        // 0 day, 1 night
        double temp = Double.NaN;      // now / 3h / 6h temperature, or the day's max
        double min = Double.NaN;       // forecast days only
        double rain = Double.NaN;      // rainfall probability in percent
        String time = "";
    }

    static final class Data {
        String place = "", updated = "";
        boolean fahrenheit;
        final List<Slot> today = new ArrayList<Slot>();
        final List<Slot> days = new ArrayList<Slot>();
    }

    interface Listener { void onData(Data d); }

    private static final String ACTION_UPDATE = "com.hkmc.intent.action.weather_update";
    private static final String ACTION_REQUEST = "com.hkmc.intent.action.request_weather_update";
    private static final String EXTRA_LIST = "ReadAndSaveWeatherParcelable";
    private static final Uri TMU_INFO = Uri.parse("content://com.hkmc.telematics.service.util.db.VehicleSettingProvider/tmu_info");
    private static final double NONE = 9999.0;

    private final Context ctx;
    private final Listener listener;
    private boolean registered;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { handle(i); }
    };

    Feed(Context ctx, Listener l) {
        this.ctx = ctx.getApplicationContext();
        this.listener = l;
    }

    void start() {
        if (!registered) {
            Intent sticky = ctx.registerReceiver(receiver, new IntentFilter(ACTION_UPDATE));
            registered = true;
            if (sticky != null) handle(sticky);
        }
        request();
    }

    /** Asks the weather service for a fresh report (ccNC's option menu "Refresh"). */
    void request() { ctx.sendBroadcast(new Intent(ACTION_REQUEST)); }

    void stop() {
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
            if (info == null) return;
            Data d = new Data();
            d.fahrenheit = useFahrenheit();
            d.place = str(info, "getCurrentLocTitle").trim();
            d.updated = str(info, "getUpdatedTime");
            Object pl = call(info, "getmReadAndSavePresentWeatherInfoList");
            if (pl instanceof List) for (Object p : (List<?>) pl) {
                Slot s = new Slot();
                s.icon = (int) num(p, "getIcon", 0);
                s.dayNight = (int) num(p, "getDayNight", 0);
                s.temp = valid(num(p, "getValue", Double.NaN));
                s.rain = valid(num(p, "getRainfallProbability", Double.NaN));
                s.time = str(p, "getWeatherTime");
                d.today.add(s);
                Log.i(TAG, "present " + dump(p));
            }
            Object fl = call(info, "getmReadAndSaveForecastWeatherInfoList");
            if (fl instanceof List) for (Object f : (List<?>) fl) {
                Slot s = new Slot();
                s.icon = (int) num(f, "getIcon", 0);
                s.dayNight = (int) num(f, "getDayNight", 0);
                s.temp = valid(num(f, "getValueMaxTemp", Double.NaN));
                s.min = valid(num(f, "getValueMinTemp", Double.NaN));
                s.rain = valid(num(f, "getRainfallproability", Double.NaN));
                s.time = str(f, "getWeatherTime");
                d.days.add(s);
                Log.i(TAG, "forecast " + dump(f));
            }
            Log.i(TAG, "info place=" + d.place + " updated=" + d.updated + " present=" + d.today.size() + " forecast=" + d.days.size());
            listener.onData(d);
        } catch (Throwable t) {
            Log.w(TAG, "could not read weather update", t);
        }
    }

    /** Every no-arg getter, for the log (what the service really carries on this unit). */
    private static String dump(Object o) {
        StringBuilder b = new StringBuilder();
        for (Method m : o.getClass().getMethods()) {
            if (m.getParameterTypes().length != 0 || !m.getName().startsWith("get") || m.getName().equals("getClass")) continue;
            try { b.append(m.getName().substring(3)).append('=').append(m.invoke(o)).append(' '); } catch (Throwable ignored) {}
        }
        return b.toString();
    }

    private static double valid(double v) { return Double.isNaN(v) || Math.abs(v) >= NONE ? Double.NaN : v; }

    private static double num(Object o, String m, double dflt) {
        try { return ((Number) call(o, m)).doubleValue(); } catch (Throwable t) { return dflt; }
    }

    private static String str(Object o, String m) {
        try { Object v = call(o, m); return v == null ? "" : v.toString(); } catch (Throwable t) { return ""; }
    }

    private static Object call(Object o, String m) throws Exception { return o.getClass().getMethod(m).invoke(o); }

    private static Object first(Object list) {
        if (!(list instanceof List)) return null;
        List<?> l = (List<?>) list;
        return l.isEmpty() ? null : l.get(0);
    }

    /** The unit's temperature setting (tmu_info.TempUnit: 2 = Fahrenheit). */
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
}
