package dev.ccnc.climate;

import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.util.Log;

import java.lang.reflect.Method;

/**
 * Vehicle bridge to the HKMC climate service. The framework service lives in
 * ServiceManager as "com.hkmc.micom.datc" and speaks the AIDL interface
 * com.hkmc.datc.IDATCService (transaction IDs recovered from the KVT49L smali dump).
 *
 * Everything goes through raw Binder transactions + reflection so the app needs
 * no HKMC classes on its compile classpath. On a unit without the service this
 * simply never connects and the demo source takes over.
 */
final class DatcSource implements Climate.Source {

    private static final String TAG = "ccncClimate";
    private static final String SERVICE = "com.hkmc.micom.datc";
    private static final String IFACE = "com.hkmc.datc.IDATCService";

    // IDATCService transaction codes.
    static final int T_REGISTER = 0x1;
    static final int T_GET_CLIMATE_TYPE = 0x1f;
    static final int T_GET_CLIMATE_INFO = 0x26;
    static final int T_IS_UNIT_CELSIUS = 0x6;
    static final int T_SUPPORT_FRONT_CONTROL = 0x40;
    static final int T_IS_FRONT_MTC = 0x24;
    /** getClimateInfo flags (DATCFlag$DATCConstants; not the control ids below). */
    static final int F_CLIMATE_MODE_TYPE = 0x18, F_CLIMATE_CAN_SUPPORT = 0xe0;
    static final int T_SET_FRONT_CONTROL = 0x41;

    // DATCFlag$DATCControl / DATCConstants ids (KVT49L smali), used for getClimateInfo(id) and
    // setFrontClimateControl(id, value).
    static final int C_BLOWER_UP_DN = 0x4, C_BLOWER_LEVEL = 0x5, C_BLOWER_MODE = 0x6, C_AUTO = 0x8, C_AC = 0x9,
            C_FRONT_DEFOG = 0xa, C_INTAKE = 0xb, C_SYNC = 0x16, C_TEMP_PS_UPDN = 0x31, C_TEMP_DR_UPDN = 0x32, C_OFF_REQ = 0x33;
    private static final int[] READ_IDS = {C_AUTO, C_AC, C_BLOWER_LEVEL, C_BLOWER_MODE, C_INTAKE, C_FRONT_DEFOG, C_SYNC};

    // IDATCListener transaction codes.
    static final int L_TEMP_INFO = 0x1;
    static final int L_DATC_STATUS = 0x3;

    private final Binder listener = new Binder() {
        @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
            if (code == L_TEMP_INFO) {
                data.enforceInterface("com.hkmc.datc.IDATCListener");
                // onTemperatureInformation(F,I,F,I,F,I,F,I,F,I,F,I,F,I)
                final float[] f = new float[7];
                final int[] st = new int[7];
                for (int i = 0; i < 7; i++) {
                    f[i] = data.readFloat();
                    st[i] = data.readInt();
                }
                work(new Runnable() { @Override public void run() { onTemps(f, st); } });
                return true;
            }
            if (code == L_DATC_STATUS) {
                data.enforceInterface("com.hkmc.datc.IDATCListener");
                // DatcStatus parcelable: copied here (binder thread), decoded on the worker.
                final int n = data.dataAvail() / 4;
                final int[] cur = new int[n];
                for (int i = 0; i < n; i++) cur[i] = data.readInt();
                work(new Runnable() { @Override public void run() {
                    try { onStatus(cur); } catch (Throwable t) { Log.w(TAG, "datc status parse failed", t); }
                } });
                return true;
            }
            try {
                return super.onTransact(code, data, reply, flags);
            } catch (android.os.RemoteException e) {
                return false;
            }
        }
    };

    /** The unit's own constant (com.hkmc.Service.DATC_SERVICE), falling back to the recovered literal. */
    private static String serviceName() {
        try {
            Object v = Class.forName("com.hkmc.Service").getField("DATC_SERVICE").get(null);
            if (v instanceof String && !((String) v).isEmpty()) return (String) v;
        } catch (Throwable ignored) {
        }
        return SERVICE;
    }

    private IBinder service;
    private Listener listenerCb;
    private final Climate.State state = new Climate.State();
    private boolean connected;

    /**
     * Everything that talks to the car (polling, the climate and seat services, sending changes) runs on this one
     * worker thread, never on the UI thread: a slow answer from the car must not freeze the screen. Updates go to
     * the screen on the main thread (emit).
     */
    private android.os.HandlerThread workerThread;
    private android.os.Handler worker;
    private final android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
    private int celsiusUnit = Integer.MIN_VALUE;
    private boolean celsiusCached;

    private void work(Runnable r) { android.os.Handler w = worker; if (w != null) w.post(r); }
    private final java.util.HashMap<Integer, Integer> raw = new java.util.HashMap<Integer, Integer>();
    private boolean frontControl;
    final Usm usm = new Usm();
    private static final String CAL = "ccncCal";
    private int[] lastStatus = new int[0];

    private final Runnable poll = new Runnable() {
        @Override public void run() {
            if (!connected || listenerCb == null || worker == null) return;
            readState();
            worker.postDelayed(this, 1500);
        }
    };

    /** getClimateInfo(id) for the controls we show; raw values are logged when they change (to decode the encodings on the car). */
    private void readState() {
        usm.read();
        state.driverSeatHeat = Usm.heatLevel(usm.drv); state.driverSeatVent = Usm.ventLevel(usm.drv);
        state.passengerSeatHeat = Usm.heatLevel(usm.ast); state.passengerSeatVent = Usm.ventLevel(usm.ast);
        state.rearLeftHeat = Usm.heatLevel(usm.rl); state.rearLeftVent = Usm.ventLevel(usm.rl);
        state.rearRightHeat = Usm.heatLevel(usm.rr); state.rearRightVent = Usm.ventLevel(usm.rr);
        state.wheelAvail = usm.wheelAvailable(); state.wheelHeat = usm.wheelOn();
        state.drvOpt = usm.drvOpt; state.astOpt = usm.astOpt; state.rlOpt = usm.rlOpt; state.rrOpt = usm.rrOpt;
        state.swOpt = usm.swOpt; state.swInf = usm.swInf;
        state.seatProp = prop("persist.sys.showSWRCFrontSeat");
        if (equipPolls++ % 10 == 0) readEquipment(); // at once, then every 15 s: the DATC variant can arrive after boot
        state.equipRead = true;
        emit();
    }

    private int equipPolls;
    private Object named;        // the unit's com.hkmc.datc.IDATCService proxy, or null
    private Class<?> namedClass;
    private boolean namedTried;

    /**
     * What the stock climate app asks the DATC service to decide which controls exist (see {@link Equipment}):
     * getClimateType(), isFrontMTCType(), getClimateInfo(CLIMATE_MODE_TYPE) and getClimateInfo(CLIMATE_CAN_SUPPORT).
     * Asked by method name through the unit's own proxy class where it is there, so a firmware that numbers the
     * transactions differently still answers the right question; the recovered codes are the fallback.
     */
    private void readEquipment() {
        if (service == null) return;
        if (!namedTried) {
            namedTried = true;
            try {
                named = Class.forName("com.hkmc.datc.IDATCService$Stub").getMethod("asInterface", IBinder.class).invoke(null, service);
                namedClass = Class.forName("com.hkmc.datc.IDATCService");
            } catch (Throwable t) {
                named = null;
            }
            Log.i(TAG, "equipment queries by " + (named != null ? "method name" : "transaction code"));
        }
        state.climateType = ask("getClimateType", T_GET_CLIMATE_TYPE, null);
        state.frontMtc = ask("isFrontMTCType", T_IS_FRONT_MTC, null);
        state.modeType = ask("getClimateInfo", T_GET_CLIMATE_INFO, F_CLIMATE_MODE_TYPE);
        state.canSupport = ask("getClimateInfo", T_GET_CLIMATE_INFO, F_CLIMATE_CAN_SUPPORT);
    }

    /** One int or boolean query, with an optional int argument; -1 when it can't be asked. */
    private int ask(String name, int code, Integer arg) {
        if (named != null) {
            try {
                Object v = arg == null ? namedClass.getMethod(name).invoke(named)
                        : namedClass.getMethod(name, int.class).invoke(named, arg);
                if (v instanceof Integer) return (Integer) v;
                if (v instanceof Boolean) return ((Boolean) v) ? 1 : 0;
            } catch (Throwable ignored) { } // fall through to the code
        }
        if (arg != null) return txInt(code, arg);
        Parcel d = Parcel.obtain(), r = Parcel.obtain();
        try {
            d.writeInterfaceToken(IFACE);
            service.transact(code, d, r, 0);
            r.readException();
            return r.readInt();
        } catch (Throwable t) {
            return -1;
        } finally {
            d.recycle();
            r.recycle();
        }
    }

    /** A system property, null when it isn't set or can't be read. */
    private static String prop(String key) {
        try {
            String v = (String) Class.forName("android.os.SystemProperties").getMethod("get", String.class).invoke(null, key);
            return v == null || v.trim().length() == 0 ? null : v.trim();
        } catch (Throwable t) {
            return null;
        }
    }

    /** IDATCService.isUnitCelsius(int tempUnit): the stock app passes DatcStatus.getTempUnit() (field 4). */
    private boolean isCelsius(int unit) {
        Parcel d = Parcel.obtain(), r = Parcel.obtain();
        try {
            d.writeInterfaceToken(IFACE);
            d.writeInt(unit);
            service.transact(T_IS_UNIT_CELSIUS, d, r, 0);
            r.readException();
            boolean c = r.readInt() != 0;
            Log.i(CAL, "isUnitCelsius(" + unit + ")=" + c);
            return c;
        } catch (Throwable t) {
            return unit == 0;
        } finally { d.recycle(); r.recycle(); }
    }

    private boolean pos(int id) { Integer v = raw.get(id); return v != null && v > 0; }

    /** One int-in/int-out transaction (getClimateInfo style); -1 on any failure. */
    private int txInt(int code, int arg) {
        Parcel d = Parcel.obtain(), r = Parcel.obtain();
        try {
            d.writeInterfaceToken(IFACE);
            d.writeInt(arg);
            service.transact(code, d, r, 0);
            r.readException();
            return r.readInt();
        } catch (Throwable t) {
            return -1;
        } finally {
            d.recycle();
            r.recycle();
        }
    }

    /** True when the car's DATC reports that the head unit may control the front climate. */
    boolean supportsControl() { return frontControl; }

    /** setFrontClimateControl(control, value). Returns false when the service refused. */
    boolean control(int control, int value) {
        Parcel d = Parcel.obtain(), r = Parcel.obtain();
        try {
            d.writeInterfaceToken(IFACE);
            d.writeInt(control);
            d.writeInt(value);
            service.transact(T_SET_FRONT_CONTROL, d, r, 0);
            r.readException();
            Log.i(TAG, "SENT setFrontClimateControl(0x" + Integer.toHexString(control) + ", " + value + ")");
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "setFrontClimateControl refused", t);
            return false;
        } finally {
            d.recycle();
            r.recycle();
        }
    }

    /**
     * Sends what changed between the car's reported state and the one the UI edited, for the
     * controls with known ids (id-to-packet table confirmed in DATCService.setFrontClimateControl:
     * 0x8 AutoSet, 0x9 AcSet, 0xa FrontDefog, 0xb IntakeSet, 0x16 SyncSet, 0x5 MainBlower, 0x31/0x32 Ps/DrTempUpDn,
     * 0x33 = MainBlower 0). Toggles use 1 = on, 0 = off and the UP/DN ids 1 = up, 2 = down: the values are
     * passed through as bytes to the micom, whose signal table isn't in the firmware, so these stay assumed. Only called when the owner enabled vehicle
     * control: the encodings are unconfirmed on this car and are logged as "SENT" lines.
     */
    void sendChanges(Climate.State fromState, Climate.State toState) {
        final Climate.State from = fromState.copy(), to = toState.copy();
        // Ahead of any queued reads, so a tap reaches the car straight away.
        android.os.Handler w = worker;
        if (w != null) w.postAtFrontOfQueue(new Runnable() { @Override public void run() { send(from, to); } });
    }

    private void send(Climate.State from, Climate.State to) {
        if (from.driverSeatHeat != to.driverSeatHeat || from.driverSeatVent != to.driverSeatVent)
            usm.setDriver(to.driverSeatHeat > 0 ? Usm.stateOf(true, to.driverSeatHeat) : Usm.stateOf(false, to.driverSeatVent));
        if (from.passengerSeatHeat != to.passengerSeatHeat || from.passengerSeatVent != to.passengerSeatVent)
            usm.setPassenger(to.passengerSeatHeat > 0 ? Usm.stateOf(true, to.passengerSeatHeat) : Usm.stateOf(false, to.passengerSeatVent));
        if (from.rearLeftHeat != to.rearLeftHeat || from.rearLeftVent != to.rearLeftVent)
            usm.setRearLeft(to.rearLeftHeat > 0 ? Usm.stateOf(true, to.rearLeftHeat) : Usm.stateOf(false, to.rearLeftVent));
        if (from.rearRightHeat != to.rearRightHeat || from.rearRightVent != to.rearRightVent)
            usm.setRearRight(to.rearRightHeat > 0 ? Usm.stateOf(true, to.rearRightHeat) : Usm.stateOf(false, to.rearRightVent));
        if (from.wheelHeat != to.wheelHeat) {
            usm.pressWheel(); // a toggle: press 1, release 0 (on the worker thread, like every write)
        }
        if (!frontControl) return;
        // Climate off (OFF, or the fan stepped down from 1): the stock OFF button's own request (NewClimateControl
        // onClickBlowerOffButton: setFrontClimateControl(0x33, 1)) and nothing else. A fan level of 0 can't be sent
        // as a level (the stock control only takes 2..9), and a power flag left on meant nothing was sent at all.
        if (from.power && !to.power) { control(C_OFF_REQ, 1); return; }
        if (from.auto != to.auto) control(C_AUTO, to.auto ? 1 : 0);
        if (from.ac != to.ac) control(C_AC, to.ac ? 1 : 0);
        if (from.recirculated != to.recirculated) control(C_INTAKE, to.recirculated ? 1 : 0);
        if (from.frontDefrost != to.frontDefrost) control(C_FRONT_DEFOG, to.frontDefrost ? 1 : 0);
        if (from.dual != to.dual) control(C_SYNC, to.dual ? 0 : 1);
        // The stock fan control only accepts 2..9 for its 8 steps (BlowerGraphView: "only able to set (2~9)").
        if (from.blower != to.blower && to.blower >= 1) control(C_BLOWER_LEVEL, to.blower + 1);
        if (from.ccMode != to.ccMode && to.ccMode > 0) control(C_BLOWER_MODE, to.ccMode);
        int dr = Math.round((to.driverTemp - from.driverTemp) / 0.5f);
        for (int i = 0; i < Math.min(Math.abs(dr), 8); i++) control(C_TEMP_DR_UPDN, dr > 0 ? 1 : 2);
        int ps = Math.round((to.passengerTemp - from.passengerTemp) / 0.5f);
        if (to.dual) for (int i = 0; i < Math.min(Math.abs(ps), 8); i++) control(C_TEMP_PS_UPDN, ps > 0 ? 1 : 2);
    }

    @Override public void start(Listener l) {
        this.listenerCb = l;
        if (workerThread == null) {
            workerThread = new android.os.HandlerThread("ccncDatc");
            workerThread.start();
            worker = new android.os.Handler(workerThread.getLooper());
        }
        try {
            Class<?> sm = Class.forName("android.os.ServiceManager");
            Method get = sm.getMethod("getService", String.class);
            service = (IBinder) get.invoke(null, serviceName());
            if (service == null) {
                Log.i(TAG, "no DATC service on this unit; demo mode");
                return;
            }
            // Vehicle sanity check: getClimateType must transact cleanly.
            Parcel d = Parcel.obtain(), r = Parcel.obtain();
            int type = -1;
            try {
                d.writeInterfaceToken(IFACE);
                service.transact(T_GET_CLIMATE_TYPE, d, r, 0);
                r.readException();
                type = r.readInt();
            } finally {
                d.recycle();
                r.recycle();
            }
            connected = true;
            state.fromVehicle = true;
            state.modeKnown = false; // becomes true once getClimateInfo(BLOWER_MODE) returns a valid mode number
            Log.i(TAG, "DATC connected, climateType=" + type);

            d = Parcel.obtain();
            r = Parcel.obtain();
            try {
                d.writeInterfaceToken(IFACE);
                d.writeStrongBinder(listener);
                d.writeStrongBinder(listener);
                service.transact(T_REGISTER, d, r, 0);
                r.readException();
            } finally {
                d.recycle();
                r.recycle();
            }

            Parcel sd = Parcel.obtain(), sr = Parcel.obtain();
            try {
                sd.writeInterfaceToken(IFACE);
                service.transact(T_SUPPORT_FRONT_CONTROL, sd, sr, 0);
                sr.readException();
                frontControl = sr.readInt() != 0;
            } catch (Throwable t) {
                frontControl = false;
            } finally {
                sd.recycle();
                sr.recycle();
            }
            Log.i(TAG, "supportFrontClimateControl=" + frontControl);
            emit();
            worker.post(poll);
        } catch (Throwable t) {
            Log.w(TAG, "DATC bridge failed; demo mode", t);
            connected = false;
            state.fromVehicle = false;
        }
    }

    @Override public void stop() {
        listenerCb = null;
        if (worker != null) worker.removeCallbacks(poll);       // stop polling; changes already queued are still sent
        if (workerThread != null) workerThread.quitSafely();
        workerThread = null;
        worker = null;
    }

    @Override public boolean isVehicle() { return connected; }

    @Override public void apply(Climate.State s) {
        // Deliberately read-only. On this car the front climate is physical buttons; the stock
        // app only writes rear-seat settings and voice commands (IDATCService 0x2a-0x36, 0x28,
        // 0x41, 0x43), whose control ids are in odex code that doesn't deodex. Not guessing
        // vehicle commands: the UI reverts and says so.
        Log.i(TAG, "apply: vehicle climate is view-only");
        emit();
    }

    private void onTemps(float[] f, int[] st) {
        // Order per IDATCListener: DrC, DrF, PsC, PsF, RearC, RearF, Ambient(F?) —
        // confirmed on bench; index 0 is the driver setpoint.
        boolean c = state.celsius;
        float dr = c ? f[0] : (f[1] - 32f) * 5f / 9f, ps = c ? f[2] : (f[3] - 32f) * 5f / 9f;
        if (dr > -40f && dr < 60f) state.driverTemp = dr;
        if (ps > -40f && ps < 60f) state.passengerTemp = ps;
        Log.i(TAG, "temps=" + java.util.Arrays.toString(f));
        emit();
    }

    /** Whole DatcStatus parcel: idx 0 is the non-null flag, then fields in wire order (the smali field names are not trustworthy). Logs changed indices as ccncCal STATUS. */
    private void onStatus(int[] cur) {
        int n = cur.length;
        StringBuilder sb = new StringBuilder();
        if (cur.length != lastStatus.length) sb.append(" len=").append(n);
        for (int i = 0; i < n; i++) {
            if (i >= lastStatus.length || lastStatus[i] != cur[i]) {
                sb.append(" [").append(i).append("]=");
                if (i < lastStatus.length) sb.append(lastStatus[i]).append("->");
                sb.append(cur[i]).append("(f").append(Float.intBitsToFloat(cur[i])).append(")");
            }
        }
        if (sb.length() > 0) Log.i(CAL, "STATUS" + sb);
        decodeStatus(cur);
        if (lastStatus.length == 0) {
            StringBuilder all = new StringBuilder("STATUS-FULL");
            for (int i = 0; i < n; i++) all.append(' ').append(cur[i]);
            Log.i(CAL, all.toString());
        }
        lastStatus = cur;
        emit();
    }

    /**
     * Wire order of the DatcStatus parcel, from the deodexed automotive.odex constructor (field n = parcel int n,
     * int 0 is the non-null flag): 1 DrMode, 3 AcOnOff, 4 TempUnit (0 C, 1 F), 5 Intake, 6 Auto, 7 OpStatus
     * (0 off, 1 normal, 2 system off), 8 Dual, 11 AutoDefogBlink, 14 MainBlowerLevel (0 off, 2..9 = levels 1..8),
     * 32 SyncDisp (1 on, 2 off), 60 HeatOnOff. Mode values are DatcModeDisp (the ccNC mode numbers).
     */
    private void decodeStatus(int[] s) {
        if (s.length < 61) return;
        int op = s[7], blower = s[14], mode = s[1];
        state.power = op == 1;
        state.auto = s[6] == 1;
        state.ac = s[3] == 1;
        state.recirculated = s[5] == 1;
        state.frontDefrost = s[11] == 1;
        state.dual = s[32] != 1;
        if (!celsiusCached || celsiusUnit != s[4]) { celsiusUnit = s[4]; state.celsius = isCelsius(s[4]); celsiusCached = true; }
        state.blower = (blower >= 2 && blower <= 9) ? blower - 1 : 0;
        boolean valid = (mode >= 1 && mode <= 5) || mode == 7 || (mode >= 9 && mode <= 14);
        state.ccMode = valid ? mode : 0;
        state.modeKnown = valid || mode == 0;
        Log.i(CAL, "DECODED off=" + (op != 1) + " op=" + op + " blowerWire=" + blower + " fan=" + state.blower + " mode=" + mode
                + " auto=" + s[6] + " ac=" + s[3] + " intake=" + s[5] + " defogBlink=" + s[11] + " dual=" + s[8]
                + " sync=" + s[32] + " heat=" + s[60] + " unit=" + s[4] + " driverOnly=" + s[22]);
    }

    /** Hands a copy of the state to the screen, on the main thread. */
    private void emit() {
        if (listenerCb == null) return;
        final Climate.State c = state.copy();
        main.post(new Runnable() { @Override public void run() { Listener l = listenerCb; if (l != null) l.onClimate(c); } });
    }
}
