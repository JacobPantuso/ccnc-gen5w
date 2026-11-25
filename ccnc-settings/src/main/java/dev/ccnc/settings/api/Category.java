package dev.ccnc.settings.api;

import android.content.Context;
import android.content.Intent;

import java.util.ArrayList;
import java.util.List;

/** A category screen being built: its left list of menus (ccNC HTabButton list), each with a page or a native activity. */
public final class Category {
    public static final class Tab {
        public final String title;
        public final Fn.Build page;     // shown in the right pane, or
        public final Intent activity;   // started instead (own native screens, e.g. DriverAssistActivity)
        public final Fn.Act action;     // or run on tap (the stock entry)
        Tab(String title, Fn.Build page, Intent activity, Fn.Act action) {
            this.title = title; this.page = page; this.activity = activity; this.action = action;
        }
    }

    public final String title;
    private final Context ctx;
    public final List<Tab> tabs = new ArrayList<Tab>();

    public Category(Context ctx, String title) { this.ctx = ctx; this.title = title; }

    public Context context() { return ctx; }

    /** ccNC wording for a menu-tree node (assets/tree.tsv), e.g. word(3013, "Date & Time"). */
    public String word(int node, String fallback) { return Words.get(ctx, node, fallback); }

    /** A left-list menu whose page is built by b. */
    public Category tab(String title, Fn.Build b) { tabs.add(new Tab(title, b, null, null)); return this; }

    /** A left-list menu that opens one of our own native activities. */
    public Category tab(String title, Intent activity) { tabs.add(new Tab(title, null, activity, null)); return this; }

    /** A left-list menu that runs an action (framework use: the stock entry). */
    public Category action(String title, Fn.Act a) { tabs.add(new Tab(title, null, null, a)); return this; }
}
