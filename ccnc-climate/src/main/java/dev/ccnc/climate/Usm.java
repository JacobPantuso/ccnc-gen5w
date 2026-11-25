package dev.ccnc.climate;

import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.util.Log;

import java.lang.reflect.Method;

/**
 * Seat heat/vent and steering-wheel heat through the HKMC USM service ("com.hkmc.usm", AIDL
 * com.hkmc.usm.IUSMService), exactly as the stock climate app does (deodexed automotive.odex and
 * HKMC_Climate.odex). Seat state values (SEAT_STATES): 2 off, 3/4/5 vent low/mid/high, 6/7/8 heat low/mid/high.
 * Writes: setFrontSeatHeatDrvStep/AstStep, setRearSeatHeatLeftStep/RightStep take that state value; the
 * wheel warmer is pressed with 1 then released with 0 (setSWRC_SteeringwheelWarmerSet). Log tag: ccncUsm.
 */
final class Usm {
    static final String TAG = "ccncUsm";
    private static final String IFACE = "com.hkmc.usm.IUSMService";
    private static final String LIFACE = "com.hkmc.usm.IUSMListener";

    static final int T_ADD_LISTENER = 0x1;
    static final int G_DRV_OPT = 0x243, G_AST_OPT = 0x244, G_DRV = 0x245, G_AST = 0x246,
            G_RL_OPT = 0x211, G_RR_OPT = 0x212, G_RL = 0x213, G_RR = 0x214,
            G_SW_OPT = 0x23f, G_SW_INF = 0x240, G_SW_STEP1 = 0x241, G_SW_STEP2 = 0x242;
    static final int S_DRV = 0xd4, S_AST = 0xd5, S_RL = 0xba, S_RR = 0xbb, S_SW = 0xd3;
    static final int L_REAR = 0x18, L_SWRC = 0x20, L_FRONT = 0x21;

    interface Events { void onSeatEvent(String what); }

    private IBinder svc;
    Events events;
    long lastWrite;

    // raw readings
    int drvOpt = -1, astOpt = -1, rlOpt = -1, rrOpt = -1, drv = -1, ast = -1, rl = -1, rr = -1,
            swOpt = -1, swInf = -1, swStep1 = -1, swStep2 = -1;

    private final Binder listener = new Binder() {
        @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
            try {
                if (code == L_FRONT || code == L_REAR || code == L_SWRC) {
                    data.enforceInterface(LIFACE);
                    int a = data.readInt(), b = data.readInt(), c = data.readInt(), d = data.readInt();
                    String n = code == L_FRONT ? "front" : code == L_REAR ? "rear" : "swrc";
                    Log.i(TAG, "event " + n + " [" + a + "," + b + "," + c + "," + d + "]");
                    boolean own = System.currentTimeMillis() - lastWrite < 2500;
                    if (!own && events != null) events.onSeatEvent(n);
                    return true;
                }
            } catch (Throwable t) {
                Log.w(TAG, "listener", t);
            }
            return true;
        }
    };

    boolean connect() {
        if (svc != null) return true;
        try {
            String name = "com.hkmc.micom.usm";
            try {
                Object v = Class.forName("com.hkmc.Service").getField("USM_SERVICE").get(null);
                if (v instanceof String) name = (String) v;
            } catch (Throwable ignored) { }
            Method get = Class.forName("android.os.ServiceManager").getMethod("getService", String.class);
            svc = (IBinder) get.invoke(null, name);
            Log.i(TAG, "service " + name + " = " + (svc != null));
        } catch (Throwable t) {
            Log.w(TAG, "connect", t);
        }
        return svc != null;
    }

    boolean addListener() {
        if (!connect()) return false;
        Parcel d = Parcel.obtain(), r = Parcel.obtain();
        try {
            d.writeInterfaceToken(IFACE);
            d.writeStrongBinder(listener);
            svc.transact(T_ADD_LISTENER, d, r, 0);
            r.readException();
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "addListener", t);
            return false;
        } finally { d.recycle(); r.recycle(); }
    }

    private int get(int code) {
        Parcel d = Parcel.obtain(), r = Parcel.obtain();
        try {
            d.writeInterfaceToken(IFACE);
            svc.transact(code, d, r, 0);
            r.readException();
            return r.readInt();
        } catch (Throwable t) {
            return -1;
        } finally { d.recycle(); r.recycle(); }
    }

    private boolean set(int code, int v) {
        Parcel d = Parcel.obtain(), r = Parcel.obtain();
        try {
            d.writeInterfaceToken(IFACE);
            d.writeInt(v);
            lastWrite = System.currentTimeMillis();
            svc.transact(code, d, r, 0);
            r.readException();
            Log.i(TAG, "SENT usm 0x" + Integer.toHexString(code) + "(" + v + ")");
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "write refused", t);
            return false;
        } finally { d.recycle(); r.recycle(); }
    }

    /** Re-read everything; logs changes (grep ccncUsm). */
    void read() {
        if (!connect()) return;
        int[] n = {get(G_DRV_OPT), get(G_AST_OPT), get(G_RL_OPT), get(G_RR_OPT), get(G_DRV), get(G_AST), get(G_RL), get(G_RR),
                get(G_SW_OPT), get(G_SW_INF), get(G_SW_STEP1), get(G_SW_STEP2)};
        int[] o = {drvOpt, astOpt, rlOpt, rrOpt, drv, ast, rl, rr, swOpt, swInf, swStep1, swStep2};
        boolean ch = false;
        for (int i = 0; i < n.length; i++) if (n[i] != o[i]) ch = true;
        drvOpt = n[0]; astOpt = n[1]; rlOpt = n[2]; rrOpt = n[3]; drv = n[4]; ast = n[5]; rl = n[6]; rr = n[7];
        swOpt = n[8]; swInf = n[9]; swStep1 = n[10]; swStep2 = n[11];
        if (ch) Log.i(TAG, "STATE drvOpt=" + drvOpt + " astOpt=" + astOpt + " rlOpt=" + rlOpt + " rrOpt=" + rrOpt
                + " drv=" + drv + " ast=" + ast + " rl=" + rl + " rr=" + rr + " swOpt=" + swOpt + " swInf=" + swInf
                + " swStep1=" + swStep1 + " swStep2=" + swStep2);
    }

    boolean setDriver(int state) { return connect() && set(S_DRV, state); }
    boolean setPassenger(int state) { return connect() && set(S_AST, state); }
    boolean setRearLeft(int state) { return connect() && set(S_RL, state); }
    boolean setRearRight(int state) { return connect() && set(S_RR, state); }

    /** Press (1) and release (0), like the stock wheel button. */
    boolean pressWheel() {
        if (!connect()) return false;
        boolean a = set(S_SW, 1);
        try { Thread.sleep(150); } catch (InterruptedException ignored) { }
        return set(S_SW, 0) && a;
    }

    /** Wheel heat is available when HeatingOpt == 1 and SWInf == 1; on when IndStep2 == 1 (stock mV / mU). */
    boolean wheelAvailable() { return swOpt == 1 && swInf == 1; }
    boolean wheelOn() { return swStep2 == 1; }

    /** SEAT_STATES value to (heat level, vent level): 6..8 heat 1..3, 3..5 vent 1..3. */
    static int heatLevel(int st) { return st >= 6 && st <= 8 ? st - 5 : 0; }
    static int ventLevel(int st) { return st >= 3 && st <= 5 ? st - 2 : 0; }
    static int stateOf(boolean heat, int level) { return level <= 0 ? 2 : (heat ? 5 + level : 2 + level); }

    /** Stock cycle (SeatHeatVentPopup arrays): heat 8,7,6,2 and vent 5,4,3,2 (3 steps), 8,6,2 / 5,3,2 with 2 steps; off or other mode starts at the first. */
    static int nextState(int cur, boolean heat) {
        int[] arr = heat ? new int[]{8, 7, 6, 2} : new int[]{5, 4, 3, 2};
        for (int i = 0; i < arr.length; i++) if (arr[i] == cur) return arr[(i + 1) % arr.length];
        return arr[0];
    }
}
