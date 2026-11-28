package dev.ccnc.ev;

import android.content.Context;
import android.util.Log;

import java.lang.reflect.Method;

/**
 * Battery and charging state from the unit's HEV service, read the way the stock EV app does:
 * {@code com.hkmc.hev.HEVManager.getHEVStatus(flag)} with the flag ids of HEVManager$HEVFlag
 * (framework, by reflection). Read-only: nothing here writes to the vehicle.
 * -1 (INVALID_VALUE) means the car hasn't reported that value.
 */
final class EvData {
    static final String TAG = "ccncEv";

    // HEVManager$HEVFlag ids.
    static final int DISPLAY_SOC = 0x19, ELECTRIC_DTE = 0x9, TOTAL_DTE = 0xb, TRIP_UNIT = 0xc,
            CHARGE_STATE = 0x7, CHARGING_REM_TIME = 0x15, TARGET_SOC_SLOW = 0x22, TARGET_SOC_QUICK = 0x23,
            FAST_CHARGING = 0x28, STD_CHA = 0x1c, ODOMETER = 0x1f, ECO_LEVEL = 0x8, DTE_WARNING = 0x18;

    static final class Snapshot {
        boolean live;
        int soc = -1, dte = -1, tripUnit = -1, chargeState = -1, remMin = -1, targetSlow = -1, targetQuick = -1,
                fast = -1, std = -1, odo = -1, eco = -1;
        int dteSlow = -1, dteQuick = -1, whDrive = -1, whElec = -1, whClimate = -1, whBatt = -1;
        String error;
        /** HEVManager.getLastDepartTimeNClimateSet() (28 ints) and getLastEconomicChargeTime() (12 ints), or null. */
        int[] depart, offPeak;

        /** Departure i (0/1): set, with hour, minute (car sends 10-minute steps) and Sun..Sat bits; layout as the stock EV3 DepartureNClimateData. */
        boolean departSet(int i) { return depart != null && depart.length == 28 && depart[i * 10 + 9] == 1; }
        int departHour(int i) { return depart[i * 10]; }
        int departMin(int i) { return depart[i * 10 + 1] * 10; }
        int departDays(int i) { int d = 0; for (int b = 0; b < 7; b++) if (depart[i * 10 + 2 + b] == 1) d |= 1 << b; return d; }
        /** Scheduled climate for departure i: on, temperature (°C or °F as the car sends it), defrost. */
        boolean climateOn(int i) { return depart != null && depart.length == 28 && depart[20 + i * 4] == 1; }
        boolean climateF(int i) { return depart[20 + i * 4 + 2] == 1; }
        float climateTemp(int i) {
            int raw = depart[20 + i * 4 + 1];
            if (raw < 2 || raw > 36) raw = 20;
            return climateF(i) ? 56 + raw : 14f + raw * 0.5f;
        }
        boolean climateDefrost(int i) { return depart[20 + i * 4 + 3] == 1; }
        /** Off-peak hours: mode 0 = not used, 1 = prioritise, 2 = only during off-peak (stock EconomicData). */
        boolean offPeakKnown() { return offPeak != null && offPeak.length == 12; }
        int offStartH() { return offPeak[0]; }
        int offStartM() { return offPeak[1] * 10; }
        int offEndH() { return offPeak[2]; }
        int offEndM() { return offPeak[3] * 10; }
        int offMode() { return offPeak[8]; }

        boolean charging() { return fast > 0 || std > 0; }
        boolean km() { return tripUnit != 2; } // car reported unit=1 with 351 range (km on a metric-market car: 351 mi at 76 % is impossible)
    }

    /**
     * The car reports the battery level and charge limits in 0.5 % steps (200 = 100 %), always: 38 % is 76. (The old
     * "halve only above 100" guess showed anything under 50 % doubled.) Outside 0..200 = no valid reading (-1).
     */
    static int half(int v) { return v >= 0 && v <= 200 ? Math.round(v / 2f) : -1; }

    private Object mgr;
    private Method get;
    private String error;

    EvData(Context ctx) {
        try {
            Class<?> c = Class.forName("com.hkmc.hev.HEVManager");
            mgr = c.getConstructor(Context.class).newInstance(ctx);
            get = c.getMethod("getHEVStatus", int.class);
            // Ask the car for the schedule frames once (read requests, as the stock EV app does on start).
            for (String m : new String[]{"getDepartTimeNClimateSet", "getEconomicChargeTime"})
                try { c.getMethod(m).invoke(mgr); } catch (Throwable ignored) { }
        } catch (Throwable t) {
            Log.i(TAG, "no HEV service: " + t);
            error = "unavailable";
        }
    }


    boolean available() { return mgr != null; }

    /** Made-up values for the preview mode (no car): a typical evening at 76 %, charging optional. */
    static Snapshot demo(boolean charging) {
        Snapshot s = new Snapshot();
        s.soc = 76; s.dte = 351; s.tripUnit = 1; s.chargeState = charging ? 1 : 0; s.remMin = charging ? 95 : 0;
        s.targetSlow = 90; s.targetQuick = 80; s.fast = 0; s.std = charging ? 1 : 0; s.dteSlow = 412; s.dteQuick = 366;
        s.whDrive = 8400; s.whElec = 1300; s.whClimate = 2100; s.whBatt = 600;
        s.depart = new int[28];
        int[] d1 = {7, 3, 0, 1, 1, 1, 1, 1, 0, 1}; // 7:30, weekdays, set
        System.arraycopy(d1, 0, s.depart, 0, 10);
        s.depart[20] = 1; s.depart[21] = 12; s.depart[23] = 1; // climate 20.0 °C + defog
        s.offPeak = new int[]{23, 0, 6, 0, 0, 0, 0, 0, 1, 0, 0, 0};
        s.live = true;
        return s;
    }

    private int[] arr(String name) {
        try { Object o = mgr.getClass().getMethod(name).invoke(mgr); return o instanceof int[] ? (int[]) o : null; }
        catch (Throwable t) { return null; }
    }

    private int wh(int flag) { int v = read(flag); return v < 0 || v == 255 || v >= 65535 ? -1 : v; }

    private int read(int flag) {
        try { return ((Number) get.invoke(mgr, flag)).intValue(); } catch (Throwable t) { return -1; }
    }

    Snapshot snapshot() {
        Snapshot s = new Snapshot();
        if (mgr == null) { s.error = error; return s; }
        s.soc = half(read(DISPLAY_SOC));
        int e = read(ELECTRIC_DTE), t = read(TOTAL_DTE);
        s.dte = e >= 0 ? e : t;
        s.tripUnit = read(TRIP_UNIT);
        s.chargeState = read(CHARGE_STATE);
        s.remMin = read(CHARGING_REM_TIME);
        s.targetSlow = half(read(TARGET_SOC_SLOW));
        s.targetQuick = half(read(TARGET_SOC_QUICK));
        s.fast = read(FAST_CHARGING);
        s.std = read(STD_CHA);
        s.odo = read(ODOMETER);
        s.eco = read(ECO_LEVEL);
        s.dteSlow = read(0x20);
        s.dteQuick = read(0x21);
        // Trip energy sums (Wh): motors 0x47/0x48, LDC load 0x49, climate 0x42, battery heating 0x44. 255/65535 = signal invalid.
        int m1 = wh(0x47), m2 = wh(0x48);
        s.whDrive = m1 >= 0 && m2 >= 0 ? m1 + m2 : -1;
        s.whElec = wh(0x49);
        s.whClimate = wh(0x42);
        s.whBatt = wh(0x44);
        s.depart = arr("getLastDepartTimeNClimateSet");
        s.offPeak = arr("getLastEconomicChargeTime");
        s.live = s.soc >= 0 || s.dte >= 0;
        Log.i(TAG, "soc=" + s.soc + " dte=" + s.dte + " unit=" + s.tripUnit + " chg=" + s.chargeState + " rem=" + s.remMin
                + " tgt=" + s.targetSlow + "/" + s.targetQuick + " fast=" + s.fast + " std=" + s.std + " odo=" + s.odo + " eco=" + s.eco
                + " depart=" + java.util.Arrays.toString(s.depart) + " offpeak=" + java.util.Arrays.toString(s.offPeak));
        return s;
    }
}
