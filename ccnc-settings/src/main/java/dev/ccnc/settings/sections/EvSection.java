package dev.ccnc.settings.sections;

import android.content.Context;
import android.util.Log;

import java.lang.reflect.Method;

import dev.ccnc.settings.api.Category;
import dev.ccnc.settings.api.Fn;
import dev.ccnc.settings.api.Page;
import dev.ccnc.settings.api.Section;

/**
 * EV (stock classId com.android.settings.ev): the menus of the stock EV3 EvSettingActivity as it shows them on this
 * car (Max. % Charge, AC Charging Current, Battery Conditioning Mode, Utility Mode, Smart Regeneration System), with
 * the stock EV3 app's own wording, built from its settings table and changed through its smart-control receiver
 * (buildFromStockTable). When the table can't be read the charge limits are shown read only from
 * HEVManager.getHEVStatus(TARGET_SOC_QUICK / TARGET_SOC_SLOW) (automotive.jar). No stock screen is opened.
 */
public final class EvSection extends Section {
    @Override public String classId() { return "com.android.settings.ev"; }
    @Override public int treeNode() { return -1; }
    @Override public String icon() { return "set_ic_menu_electric_vehicle"; }

    private static final String TAG = "ccncSetup";
    private static final String EV3 = "com.hkmc.daudio.app.ev3";
    private static final int TARGET_SOC_SLOW = 0x22, TARGET_SOC_QUICK = 0x23;  // HEVManager$HEVFlag

    @Override public void build(Category c) {
        if (buildFromStockTable(c)) return;
        // The EV3 settings table did not answer (EV3 not running yet / not an EV): the charge limits read only, from
        // HEVManager. No stock screen is opened; the full menus appear once the table is readable.
        final Context x = c.context();
        Context ec;
        try { ec = x.createPackageContext(EV3, 0); } catch (Throwable t) { Log.w(TAG, "ev: no EV3 app"); return; }
        final Context e = ec;
        String max = str(e, "battery_capacity_charging_completeion");
        final String dc = str(e, "eco_30_evsettings_dccharger"), ac = str(e, "eco_30_evsettings_accharger");
        if (max == null || dc == null || ac == null) return;
        c.tab(max, new Fn.Build() {
            @Override public void build(Page p) {
                p.info(dc, pct(x, TARGET_SOC_QUICK));
                p.info(ac, pct(x, TARGET_SOC_SLOW));
                p.note("EV settings are not readable right now. Open this page again in a moment.");
            }
        });
    }

    /**
     * Native EV menus: the stock EV3 app's own settings table (EvSettingsProvider, the rows its search uses, in its
     * wording) with values read and changed through its smart-control receiver, as the Vehicle category does. The stock
     * app does each write itself (HEVManager.setTargetSOCext, setBatteryConditioning, USMManager...). Not tested on car.
     */
    private boolean buildFromStockTable(Category c) {
        final VehicleSectionUsm u = VehicleSectionUsm.ev(c.context());
        java.util.List<VehicleSectionUsm.Node> menus = u.menus();
        if (menus.isEmpty()) return false;
        u.prefetch(menus);
        if (!u.anyAnswer()) u.awaitFirstAnswers();
        Context e = null;
        try { e = c.context().createPackageContext(EV3, 0); } catch (Throwable ignored) { }
        String general = e == null ? null : str(e, "ev_settings");
        int tabs = 0;
        for (final VehicleSectionUsm.Node m : menus) {
            if (u.visible(m).isEmpty()) continue;
            String title = m.text != null ? m.text : general != null ? general : "EV Settings";
            c.tab(title, new Fn.Build() { @Override public void build(Page p) { fill(u, p, m); } });
            tabs++;
        }
        Log.i(TAG, "ev: " + tabs + " native tabs from the EV3 table");
        return tabs > 0;
    }

    private static final String[] PCT = {"50%", "60%", "70%", "80%", "90%", "100%"};

    /** Like VehicleSection.fill; EV levels (4xxxx: charge limits) are a 50-100 % choice, as the stock EV3 offers. */
    private static void fill(final VehicleSectionUsm u, Page p, VehicleSectionUsm.Node parent) {
        u.attach(p.host());
        for (final VehicleSectionUsm.Node n : u.visible(parent)) {
            final int k = n.key;
            if (k >= 30000 && k < 40000) {
                p.toggle(n.text,
                        new Fn.Bool() { @Override public boolean get() { return u.value(k) == 1; } },
                        new Fn.SetBool() { @Override public void set(boolean on) { u.set(k, on ? 1 : 0); } });
            } else if (k >= 10000 && k < 20000) {
                final java.util.List<VehicleSectionUsm.Node> opts = new java.util.ArrayList<VehicleSectionUsm.Node>();
                for (VehicleSectionUsm.Node o : u.visible(n)) if (o.key >= 20000 && o.key < 30000) opts.add(o);
                if (opts.isEmpty()) continue;
                String[] labels = new String[opts.size()];
                for (int i = 0; i < labels.length; i++) labels[i] = opts.get(i).text;
                p.choice(n.text, labels,
                        new Fn.Int() { @Override public int get() {
                            int v = u.value(k);
                            for (int i = 0; i < opts.size(); i++) if (opts.get(i).key == v) return i;
                            return -1;
                        } },
                        new Fn.SetInt() { @Override public void set(int i) { if (i >= 0 && i < opts.size()) u.set(k, opts.get(i).key); } });
            } else if (k >= 40000 && k < 50000) {
                p.choice(n.text, PCT,
                        new Fn.Int() { @Override public int get() { int v = u.value(k); return v >= 50 && v <= 100 ? (v - 50) / 10 : -1; } },
                        new Fn.SetInt() { @Override public void set(int i) { if (i >= 0 && i < PCT.length) u.set(k, 50 + i * 10); } });
            } else if (k < 10000) {
                if (!u.visible(n).isEmpty())
                    p.drill(n.text, null, new Fn.Build() { @Override public void build(Page q) { fill(u, q, n); } });
            }
        }
    }

    /** Charge limit in %, as EV3 shows it (the car reports 0.5 % steps: 200 = 100 %), or null when not reported. */
    static String pct(Context x, int flag) {
        try {
            Class<?> c = Class.forName("com.hkmc.hev.HEVManager");
            Object m = c.getConstructor(Context.class).newInstance(x);
            Method g = c.getMethod("getHEVStatus", int.class);
            int v = ((Number) g.invoke(m, flag)).intValue();
            if (v > 100 && v <= 200) v /= 2;
            Log.i(TAG, "ev: flag " + flag + " = " + v);
            return v > 0 && v <= 100 ? v + "%" : null;
        } catch (Throwable t) {
            Log.w(TAG, "ev: HEVManager unavailable: " + t);
            return null;
        }
    }

    static String str(Context e, String name) {
        try {
            int id = e.getResources().getIdentifier(name, "string", EV3);
            return id == 0 ? null : e.getResources().getString(id);
        } catch (Throwable t) { return null; }
    }
}
