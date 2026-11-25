package dev.ccnc.settings.api;

import android.content.ComponentName;
import android.content.Context;
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
     * Called each time the category opens. Add nothing and the card opens the stock screen directly.
     */
    public abstract void build(Category c);

    /** Stock Gen5W screen for this category when the stock Setup's own opener can't be used; null if none. */
    public Intent stockIntent(Context c) { return null; }

    /** True once every stock setting of the category is covered here: the stock entry is then left out. */
    public boolean complete() { return false; }

    /** An explicit-component intent; cls may start with "." (relative to pkg). */
    public static Intent component(String pkg, String cls) {
        return new Intent(Intent.ACTION_MAIN).setComponent(new ComponentName(pkg, cls.startsWith(".") ? pkg + cls : cls))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }
}
