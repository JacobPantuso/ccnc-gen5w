package dev.ccnc.settings.api;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Gen5W "smart control" bridge (com.hkmc.ssearch, the path the stock voice assistant uses to read and change a
 * setting): each stock settings app answers
 *   com.mobis.intent.action.SCONTROL_GET_REQ {type, keyID}              -> SCONTROL_GET_RES {type, keyID, statusValue[, minValue, maxValue]}
 *   com.mobis.intent.action.SCONTROL_SET_REQ {type, keyID, statusValue} -> SCONTROL_SET_RES {type, keyID, result}
 * for its own type (SSearchConstants$SSearchType: Bt 2, Vehicle 3, Tele 4, VR 5, UserProfile 6, PhoneProjection 7,
 * DeviceConnections 8, Sound 10, Display 11, General 12, Button 13, DataNetwork 14, Theme 15, EV 16).
 * The stock app does the write itself, in its own (system uid) process and with its own checks, so a change made
 * here takes exactly the stock path; nothing is written unless the user taps a row.
 * keyID ranges (shared by all apps): 1xxxx radio group (value = keyID of the selected option), 2xxxx option,
 * 3xxxx check box (0/1), smaller ids = levels/volumes (value with minValue..maxValue). A negative value means the
 * stock app says "not supported on this car": such rows are not shown.
 * None of these broadcasts is a protected broadcast on this firmware (framework-res manifest checked).
 */
public final class Scontrol {
    private static final String TAG = "ccncSctl";
    private static final String GET_REQ = "com.mobis.intent.action.SCONTROL_GET_REQ";
    private static final String GET_RES = "com.mobis.intent.action.SCONTROL_GET_RES";
    private static final String SET_REQ = "com.mobis.intent.action.SCONTROL_SET_REQ";
    private static final String SET_RES = "com.mobis.intent.action.SCONTROL_SET_RES";

    /** No answer from the stock app (yet). */
    public static final int NONE = -1000;

    private static final Map<String, Scontrol> ALL = new HashMap<String, Scontrol>();
    private static Handler bg;

    /** One client per stock package + type. pkg may be null (broadcast not limited to one package). */
    public static synchronized Scontrol of(Context c, String pkg, int type) {
        Demo.init(c);
        String id = pkg + "#" + type;
        Scontrol s = ALL.get(id);
        if (s == null) { s = new Scontrol(c.getApplicationContext(), pkg, type); ALL.put(id, s); }
        return s;
    }

    private final Context app;
    private final String pkg;
    private final int type;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Map<Integer, Integer> vals = new ConcurrentHashMap<Integer, Integer>();
    private final Map<Integer, Integer> mins = new ConcurrentHashMap<Integer, Integer>();
    private final Map<Integer, Integer> maxs = new ConcurrentHashMap<Integer, Integer>();
    private final Map<Integer, Long> asked = new ConcurrentHashMap<Integer, Long>();
    private volatile long lastAnswer;
    private volatile Host host;
    private final Runnable refresh = new Runnable() {
        @Override public void run() { Host h = host; if (h != null) try { h.refresh(); } catch (Throwable ignored) { } }
    };

    private Scontrol(Context app, String pkg, int type) {
        this.app = app; this.pkg = pkg; this.type = type;
        try {
            IntentFilter f = new IntentFilter(GET_RES);
            f.addAction(SET_RES);
            app.registerReceiver(new BroadcastReceiver() {
                @Override public void onReceive(Context c, Intent i) { onResult(i); }
            }, f, null, thread());
        } catch (Throwable t) { Log.w(TAG, "receiver: " + t); }
    }

    private static synchronized Handler thread() {
        if (bg == null) {
            HandlerThread t = new HandlerThread("ccncSctl");
            t.start();
            bg = new Handler(t.getLooper());
        }
        return bg;
    }

    private void onResult(Intent i) {
        try {
            if (i.getIntExtra("type", -1) != type) return;
            final int key = i.getIntExtra("keyID", -1);
            if (key < 0 || !asked.containsKey(key)) return;
            if (SET_RES.equals(i.getAction())) {
                // Re-read what the car really has now (a refused change then shows the old value again).
                asked.put(key, 0L);
                ask(key);
                return;
            }
            // Volume answers carry "result" != 0 without a statusValue when the level isn't available.
            int v = i.hasExtra("statusValue") ? i.getIntExtra("statusValue", -1) : -1;
            if (i.hasExtra("minValue")) mins.put(key, i.getIntExtra("minValue", 0));
            if (i.hasExtra("maxValue")) maxs.put(key, i.getIntExtra("maxValue", 0));
            lastAnswer = System.currentTimeMillis();
            Integer old = vals.put(key, v);
            if (old == null || old != v) { ui.removeCallbacks(refresh); ui.postDelayed(refresh, 150); }
        } catch (Throwable ignored) { }
    }

    /** The page to redraw when answers arrive. */
    public void attach(Host h) { host = h; }

    private void ask(int key) {
        if (Demo.on()) { // emulator demo: the answer comes from the in-memory car
            asked.put(key, System.currentTimeMillis());
            vals.put(key, Demo.sc(type, key));
            if (Demo.scIsLevel(type, key)) { mins.put(key, 0); maxs.put(key, 45); }
            return;
        }
        Long t = asked.get(key);
        long now = System.currentTimeMillis();
        if (t != null && now - t < 5000) return;
        asked.put(key, now);
        try {
            Intent i = new Intent(GET_REQ).putExtra("type", type).putExtra("keyID", key);
            if (pkg != null) i.setPackage(pkg);
            app.sendBroadcast(i);
        } catch (Throwable ignored) { }
    }

    /** Ask for these keys now (answers arrive asynchronously). */
    public void prefetch(int... keys) { for (int k : keys) ask(k); }

    /** First open: wait (max ms) until the stock app has answered and gone quiet for 250 ms. */
    public void await(int ms) {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < ms) {
            long la = lastAnswer;
            if (la > 0 && System.currentTimeMillis() - la > 250) break;
            try { Thread.sleep(40); } catch (InterruptedException e) { break; }
        }
    }

    public boolean anyAnswer() { return !vals.isEmpty(); }

    /** Last value the stock app reported for keyID (asks for a fresh one); {@link #NONE} when it never answered. */
    public int value(int key) { ask(key); Integer v = vals.get(key); return v == null ? NONE : v; }

    /** True when the stock app answered with a real (non-negative) value: the setting exists on this car. */
    public boolean has(int key) { return value(key) >= 0; }

    public int min(int key, int def) { Integer v = mins.get(key); return v == null ? def : v; }
    public int max(int key, int def) { Integer v = maxs.get(key); return v == null ? def : v; }

    /** Ask the stock app to change keyID; it answers SCONTROL_SET_RES and the value is then read back. */
    public void set(int key, int status) {
        if (Demo.on()) { Demo.scSet(type, key, status); vals.put(key, status); return; }
        try {
            asked.put(key, System.currentTimeMillis());
            Intent i = new Intent(SET_REQ).putExtra("type", type).putExtra("keyID", key).putExtra("statusValue", status);
            if (pkg != null) i.setPackage(pkg);
            app.sendBroadcast(i);
            vals.put(key, status);
            Log.i(TAG, "set type " + type + " key " + key + " = " + status);
            // Stock apps that don't send SET_RES: read back anyway.
            final int k = key;
            ui.postDelayed(new Runnable() { @Override public void run() { asked.put(k, 0L); ask(k); } }, 700);
        } catch (Throwable t) { Log.w(TAG, "set: " + t); }
    }

    // ---- row builders -------------------------------------------------------------------------------

    /** Check box row for a 3xxxx key; added only when the car has the setting. Returns true when added. */
    public boolean check(Page p, final int key, String label) {
        if (!has(key)) return false;
        p.toggle(label,
                new Fn.Bool() { @Override public boolean get() { return value(key) == 1; } },
                new Fn.SetBool() { @Override public void set(boolean on) { Scontrol.this.set(key, on ? 1 : 0); } });
        return true;
    }

    /** Choice row for a 1xxxx radio group; opts = option keyIDs (2xxxx), in label order. Added only when supported. */
    public boolean group(Page p, final int key, String label, final int[] opts, String[] labels) {
        if (!has(key)) return false;
        p.choice(label, labels,
                new Fn.Int() { @Override public int get() {
                    int v = value(key);
                    for (int i = 0; i < opts.length; i++) if (opts[i] == v) return i;
                    return -1;
                } },
                new Fn.SetInt() { @Override public void set(int i) { if (i >= 0 && i < opts.length) Scontrol.this.set(key, opts[i]); } });
        return true;
    }

    /** The same group as radio rows directly on the page (a tab that is one list). */
    public boolean radios(Page p, final int key, final int[] opts, String[] labels) {
        if (!has(key)) return false;
        p.radios(labels,
                new Fn.Int() { @Override public int get() {
                    int v = value(key);
                    for (int i = 0; i < opts.length; i++) if (opts[i] == v) return i;
                    return -1;
                } },
                new Fn.SetInt() { @Override public void set(int i) { if (i >= 0 && i < opts.length) Scontrol.this.set(key, opts[i]); } });
        return true;
    }

    /** Slider for a level key (volumes): range = the stock min/max of the answer. Added only when supported. */
    public boolean level(Page p, final int key, String label, int defMin, int defMax) {
        if (!has(key)) return false;
        int lo = min(key, defMin), hi = max(key, defMax);
        if (hi <= lo) { lo = defMin; hi = defMax; }
        p.slider(label, lo, hi,
                new Fn.Int() { @Override public int get() { return value(key); } },
                new Fn.SetInt() { @Override public void set(int v) { Scontrol.this.set(key, v); } });
        return true;
    }
}
