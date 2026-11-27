package dev.ccnc.climate;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.util.Log;

import java.util.Locale;

/**
 * Which climate controls this car has, decided the way the stock climate app decides it (HKMC_Climate.odex,
 * ClimateManager / SeatHeatVentPopup; framework DATCService and USM). {@link ClimateView} leaves out what the car
 * doesn't have and re-flows the rest.
 *
 * <p>Sources, all read by {@link DatcSource} / {@link Usm} into {@link Climate.State}:
 * <ul>
 * <li>Seats: USM {@code getFrontSeatHeat_DrvOption / _AstOption}, {@code getRearSeatHeat_LeftOption / _RightOption}.
 *     Stock SeatHeatVentPopup: 1 or 2 = heating, 3 or 4 = ventilation, 5 or 6 = both; odd = 2 levels, even = 3 levels;
 *     anything else = no button. When the options aren't valid (ignition off) the front seats fall back to the
 *     property the stock apps keep, {@code persist.sys.showSWRCFrontSeat} ("110" = heat, vent, wheel).</li>
 * <li>Steering-wheel heat: USM {@code getSWRC_SteeringwheelHeatingOpt == 1 && ...HeatingSWInf == 1} (stock mV()).</li>
 * <li>Second temperature and SYNC: DATC {@code getClimateType()}; 1 and 5 are single zone (stock lB()).</li>
 * <li>AUTO and the fan range: DATC {@code isFrontMTCType()} = manual climate: no AUTO, 6 fan levels instead of 8.</li>
 * </ul>
 * A/C, OFF and the air-flow popup have no equipment check in the stock app and are always there.
 *
 * <p>Nothing read (emulator, or the car hasn't answered yet): everything is shown, as before.
 *
 * <p>Override for developers: the setting {@value #SETTING} lists what the car HAS, anything not listed is left out
 * (adb: {@code settings put global ccnc_climate_equipment heat,wheel,dual,auto}; {@code settings delete global
 * ccnc_climate_equipment} to go back). Words: {@code heat}, {@code vent} (front seats), {@code wheel},
 * {@code rearheat}, {@code rearvent}, {@code dual} (second temperature and SYNC), {@code auto}, {@code steps2}
 * (2-level seats), {@code all}, {@code none}. It is read on every update, so no restart is needed. On the car it
 * always applies; without a car (demo) only in a debuggable build, so the emulator can show a reduced car.
 */
final class Equipment {

    private static final String TAG = "ccncClimate";
    static final String SETTING = "ccnc_climate_equipment";

    private static final int HEAT = 1, VENT = 2, TWO = 4;

    boolean drvHeat = true, drvVent = true, astHeat = true, astVent = true;
    boolean wheel;
    boolean rlHeat, rlVent, rrHeat, rrVent;
    /** Levels per seat: 3, or 2 on cars with low / high only. */
    int drvSteps = 3, astSteps = 3, rlSteps = 3, rrSteps = 3;
    /** A second (passenger) temperature, and with it SYNC. */
    boolean dual = true;
    /** Automatic climate (AUTO). */
    boolean auto = true;
    int fanMax = 8;
    /** The override in force, or null when the equipment was detected. */
    String override;

    boolean driverSeat() { return drvHeat || drvVent; }
    boolean passengerSeat() { return astHeat || astVent; }
    boolean rearLeft() { return rlHeat || rlVent; }
    boolean rearRight() { return rrHeat || rrVent; }
    /** Anything for the seat popup to show. */
    boolean anySeatControl() { return driverSeat() || passengerSeat() || rearLeft() || rearRight() || wheel; }

    /** Seat numbers as in ClimateView: 0 driver, 1 passenger, 2 rear left, 3 rear right. */
    boolean heat(int seat) { return seat == 0 ? drvHeat : seat == 1 ? astHeat : seat == 2 ? rlHeat : rrHeat; }
    boolean vent(int seat) { return seat == 0 ? drvVent : seat == 1 ? astVent : seat == 2 ? rlVent : rrVent; }
    int steps(int seat) { return seat == 0 ? drvSteps : seat == 1 ? astSteps : seat == 2 ? rlSteps : rrSteps; }

    private static boolean valid(int opt) { return opt >= 1 && opt <= 6; }

    /** Stock option value to HEAT / VENT / TWO bits; 0 for a value that isn't an option. */
    private static int option(int opt) {
        if (!valid(opt)) return 0;
        return (opt <= 2 || opt >= 5 ? HEAT : 0) | (opt >= 3 ? VENT : 0) | ((opt & 1) == 1 ? TWO : 0);
    }

    private static int front(int opt, String prop, boolean vehicle, boolean otherValid) {
        if (valid(opt)) return option(opt);
        if (!vehicle) return HEAT | VENT;      // nothing read: show everything
        if (otherValid) return 0;              // the car is answering and this seat has no option
        if (prop != null && prop.length() >= 2)
            return (prop.charAt(0) == '1' ? HEAT : 0) | (prop.charAt(1) == '1' ? VENT : 0);
        if (opt >= 0) return 0;                // answered with "no option", and the stock apps never saw one either
        return HEAT | VENT;                    // the seat service didn't answer
    }

    /** From what the car reported. */
    static Equipment detect(Climate.State s) {
        Equipment e = new Equipment();
        boolean veh = s.fromVehicle;
        int d = front(s.drvOpt, s.seatProp, veh, valid(s.astOpt)), a = front(s.astOpt, s.seatProp, veh, valid(s.drvOpt));
        int l = option(s.rlOpt), r = option(s.rrOpt);
        e.drvHeat = (d & HEAT) != 0; e.drvVent = (d & VENT) != 0; e.drvSteps = (d & TWO) != 0 ? 2 : 3;
        e.astHeat = (a & HEAT) != 0; e.astVent = (a & VENT) != 0; e.astSteps = (a & TWO) != 0 ? 2 : 3;
        e.rlHeat = (l & HEAT) != 0; e.rlVent = (l & VENT) != 0; e.rlSteps = (l & TWO) != 0 ? 2 : 3;
        e.rrHeat = (r & HEAT) != 0; e.rrVent = (r & VENT) != 0; e.rrSteps = (r & TWO) != 0 ? 2 : 3;
        e.wheel = s.wheelAvail;
        e.dual = !(veh && (s.climateType == 1 || s.climateType == 5));
        e.auto = !(veh && s.frontMtc == 1);
        e.fanMax = e.auto ? 8 : 6;
        return e;
    }

    /** The override list (see the class comment); null when it is empty. Unknown words are ignored. */
    static Equipment parse(String text) {
        if (text == null) return null;
        String t = text.trim().toLowerCase(Locale.US);
        if (t.length() == 0) return null;
        Equipment e = new Equipment();
        e.drvHeat = e.drvVent = e.astHeat = e.astVent = e.dual = e.auto = false;
        boolean two = false;
        for (String k : t.split("[,;+|\\s]+")) {
            if (k.equals("all")) {
                e.drvHeat = e.drvVent = e.astHeat = e.astVent = e.wheel = true;
                e.rlHeat = e.rlVent = e.rrHeat = e.rrVent = e.dual = e.auto = true;
            } else if (k.equals("heat")) e.drvHeat = e.astHeat = true;
            else if (k.equals("vent")) e.drvVent = e.astVent = true;
            else if (k.equals("wheel")) e.wheel = true;
            else if (k.equals("rearheat")) e.rlHeat = e.rrHeat = true;
            else if (k.equals("rearvent")) e.rlVent = e.rrVent = true;
            else if (k.equals("dual")) e.dual = true;
            else if (k.equals("auto")) e.auto = true;
            else if (k.equals("steps2")) two = true;
        }
        e.drvSteps = e.astSteps = e.rlSteps = e.rrSteps = two ? 2 : 3;
        e.fanMax = e.auto ? 8 : 6;
        e.override = t;
        return e;
    }

    /** The equipment to draw for this state: the override when one applies, else what was detected. Logs changes. */
    static Equipment of(Context c, Climate.State s) {
        Equipment e = null;
        String o = null;
        try { o = android.provider.Settings.Global.getString(c.getContentResolver(), SETTING); } catch (Throwable ignored) { }
        if (o != null && (s.fromVehicle || debuggable(c))) e = parse(o);
        if (e == null) e = detect(s);
        if (s.equipRead) log(e, s);
        return e;
    }

    private static boolean debuggable(Context c) {
        try { return (c.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0; } catch (Throwable t) { return false; }
    }

    private static String logged;

    /** One line per change (so once per start on a car): what is shown, then the raw values it came from. */
    private static void log(Equipment e, Climate.State s) {
        String d = e + " | source=" + (s.fromVehicle ? "vehicle" : "demo") + " drvOpt=" + s.drvOpt + " astOpt=" + s.astOpt
                + " rlOpt=" + s.rlOpt + " rrOpt=" + s.rrOpt + " swOpt=" + s.swOpt + " swInf=" + s.swInf
                + " seatProp=" + (s.seatProp == null ? "-" : "'" + s.seatProp + "'") + " climateType=" + s.climateType
                + " frontMtc=" + s.frontMtc + " modeType=" + s.modeType + " canSupport=" + s.canSupport;
        if (d.equals(logged)) return;
        logged = d;
        Log.i(TAG, "equipment: " + d);
    }

    private static String seat(boolean heat, boolean vent, int steps) {
        if (!heat && !vent) return "none";
        return (heat ? (vent ? "heat+vent" : "heat") : "vent") + "/" + steps;
    }

    @Override public String toString() {
        return "driver=" + seat(drvHeat, drvVent, drvSteps) + " passenger=" + seat(astHeat, astVent, astSteps)
                + " wheel=" + (wheel ? "yes" : "no") + " rearLeft=" + seat(rlHeat, rlVent, rlSteps)
                + " rearRight=" + seat(rrHeat, rrVent, rrSteps) + " dual=" + (dual ? "yes" : "no")
                + " auto=" + (auto ? "yes" : "no") + " fan=" + fanMax + " override=" + (override == null ? "-" : override);
    }
}
