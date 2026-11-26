package dev.ccnc.settings.sections;

import android.content.Context;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.ccnc.settings.api.Category;
import dev.ccnc.settings.api.Fn;
import dev.ccnc.settings.api.Page;
import dev.ccnc.settings.api.Section;
import dev.ccnc.settings.sections.VehicleSectionUsm.Node;

/**
 * Vehicle (stock classId com.android.vehiclesettings.VehicleSetting). Left list = the stock Vehicle Settings
 * menus this car has (stock order and feature checks, see VehicleSectionUsm), right = their items from the stock
 * app's item table, with values read / changed through the stock app's smart-control receiver (USM path).
 * Level items (4xxxx: e.g. a volume or brightness step) are shown READ ONLY: their valid range is not in the stock
 * table, and a safety-related level must not be written outside it. A stock group whose children this car doesn't
 * report is left out.
 * Nothing opens the stock Vehicle Settings screen. These are driving / safety systems: every write is the stock
 * app's own (its smart-control receiver calls USMManager), and nothing is written unless a row is tapped.
 * Writes are NOT tested on the car.
 */
public final class VehicleSection extends Section {
    @Override public String classId() { return "com.android.vehiclesettings.VehicleSetting"; }
    @Override public int treeNode() { return 1013; }
    @Override public String icon() { return "set_ic_menu_vehicle"; }

    /** Stock left-menu order (uiUtils.checkFirstViewType): DA, DM, ECO, ASD, HUD, Cluster, Climate, Seat, Light, Door, DKey, Conv. */
    private static final int[] ORDER = {3315, 3316, 3318, 3326, 3314, 3321, 3317, 3323, 3319, 3320, 3324, 3322};

    /** ccNC Driver Assistance art (guide area) per stock DA group (item key in the stock table). */
    private static final Map<Integer, String> DA_ART = new HashMap<Integer, String>();
    static {
        DA_ART.put(14, "usm_da_001");                                 // Driving Convenience
        DA_ART.put(38, "set_bg_sound_driver_assistance_warning");    // Warning Volume
        DA_ART.put(40, "set_bg_sound_driver_assistance_warning");    // Warning Method
        DA_ART.put(53, "usm_da_005_02_sedan");                        // Driving Safety
    }

    @Override public void build(Category c) {
        final VehicleSectionUsm u = VehicleSectionUsm.get(c.context());
        boolean dkDone = false;
        List<Node> menus = u.menus();
        u.prefetch(menus);
        if (!u.anyAnswer() && !menus.isEmpty()) u.awaitFirstAnswers();
        for (int minor : ORDER) {
            for (final Node m : menus) {
                if (m.minor != minor || m.minor == 3324 && dkDone) continue;
                Boolean ok = u.menuSupported(minor);
                // stock check decides; if it can't run here, the stock getters' answers do
                if (Boolean.FALSE.equals(ok) || ok == null && u.visible(m).isEmpty()) continue;
                if (m.minor == 3324) { if (u.visible(m).isEmpty()) continue; dkDone = true; }
                c.tab(title(c, u, m), new Fn.Build() {
                    @Override public void build(Page p) { fill(u, p, m); }
                });
            }
        }
    }

    private static String title(Category c, VehicleSectionUsm u, Node m) {
        switch (m.minor) {
            case 3315: return c.word(20001, m.text);
            case 3316: return c.word(21001, m.text);
            case 3318: return c.word(22001, m.text);
            case 3323: return c.word(23001, m.text);
            case 3319: return c.word(24001, m.text);
            case 3320: return c.word(25001, m.text);
            case 3324: return c.word(26001, m.text);
            case 3322: return c.word(27001, m.text);
            case 3326: return c.word(28001, m.text);
            case 3317: return c.word(29001, m.text);
            default: return m.text;
        }
    }

    /** One row per visible stock item under parent. */
    private static void fill(final VehicleSectionUsm u, Page p, Node parent) {
        u.attach(p.host());
        String art = parent.major == 3315 || parent.key == 14 || parent.key == 38 || parent.key == 40 || parent.key == 53 ? DA_ART.get(parent.key) : null;
        if (art != null) p.guide(art);
        for (final Node n : u.visible(parent)) {
            final int k = n.key;
            if (k >= 30000 && k < 40000) {
                p.toggle(n.text,
                        new Fn.Bool() { @Override public boolean get() { return u.value(k) == 1; } },
                        new Fn.SetBool() { @Override public void set(boolean on) { u.set(k, on ? 1 : 0); } });
            } else if (k >= 10000 && k < 20000) {
                final List<Node> opts = new ArrayList<Node>();
                for (Node o : u.visible(n)) if (o.key >= 20000 && o.key < 30000) opts.add(o);
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
                // Level: shown with its value. The valid range of a level is not in the stock table, so it is not
                // written from here (a safety-related level must not be set outside its range): read only.
                final int v = u.value(k);
                p.info(n.text, v < 0 ? null : String.valueOf(v));
            } else if (k < 10000) {
                if (!u.visible(n).isEmpty())
                    p.drill(n.text, null, new Fn.Build() { @Override public void build(Page q) { fill(u, q, n); } });
                // a group none of whose items this car reports: nothing to show
            }
        }
    }

}
