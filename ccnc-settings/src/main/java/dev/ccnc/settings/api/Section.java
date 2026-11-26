package dev.ccnc.settings.api;

import android.content.ComponentName;
import android.content.Intent;

/**
 * One Setup category (one stock Gen5W category, keyed by its stock classId from StockMenu.MAP).
 * Stage-2 agents each own exactly one subclass in dev.ccnc.settings.sections.
 */
public abstract class Section {
    /** The stock Gen5W Setup classId of this category (StockMenu.MAP). */
    public abstract String classId();

    /** ccNC menu-tree node of the category (fallback list on units where the stock Setup can't be read), or -1. */
    public int treeNode() { return -1; }

    /** Card icon: an asset name in assets/ccnc/ui (set_ic_menu_*), without ".png". */
    public abstract String icon();

    /**
     * Add the category's left-list menus (c.tab(...)). Only menus with rows we really read/write on this car.
     * Called each time the category opens. Add nothing and the card shows a "Not available on this unit." page:
     * ccNC Setup never opens a stock Gen5W screen.
     */
    public abstract void build(Category c);

    /** An explicit-component intent (ccNC's own apps only); cls may start with "." (relative to pkg). */
    public static Intent component(String pkg, String cls) {
        return new Intent(Intent.ACTION_MAIN).setComponent(new ComponentName(pkg, cls.startsWith(".") ? pkg + cls : cls))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }
}
