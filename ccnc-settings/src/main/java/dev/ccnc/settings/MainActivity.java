package dev.ccnc.settings;

import android.animation.TimeInterpolator;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Layout;
import android.text.StaticLayout;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import dev.ccnc.common.Art;
import dev.ccnc.common.Bg;
import dev.ccnc.common.Chrome;
import dev.ccnc.common.Palette;
import dev.ccnc.settings.api.Category;
import dev.ccnc.settings.api.Fn;
import dev.ccnc.settings.api.Host;
import dev.ccnc.settings.api.Page;
import dev.ccnc.settings.api.Section;
import dev.ccnc.settings.api.Skin;
import dev.ccnc.settings.sections.Sections;

/**
 * ccNC Setup, laid out like ccNC's setuphmi:
 *  - SETUP.Gate: a grid of category cards (292x250 in 308x266 cells, 6 x 2 per page, x 44 / y 94, paged sideways);
 *  - SETUP.&lt;Category&gt;.Main: left HTabButton list (512 wide, rows 472 x 96, 42px) of the category's menus, the selected
 *    menu's list on the right (896 wide at x 512) and the guide-image area (512 wide) after it;
 *  - drilling from a row replaces the list in place; the top bar then shows the sub-page title (ccNC
 *    InternalBackButton: 32px #d1d1d1, co_ic_status_subdepth_arrow between depths). Back pops one level.
 * The category list is the stock Gen5W Setup's own (StockMenu: names, order and feature checks only), each backed by
 * a Section (sections/). No category, tab or row opens a stock Gen5W screen: what a normal app cannot change is shown
 * read-only with a note on the page.
 */
public final class MainActivity extends Activity implements Host {

    private static final String TAG = "ccncSetup";
    private static final int TOP = 94, BODY_H = 720 - 94 - 90;   // content ends above ccNC's bottom bar (y 630)
    private static final int CELL_W = 308, CELL_H = 252, CARD_W = 292, CARD_H = 236, PAGE_W = 1848; // 236 (ccNC 250): both rows end ~50 px above the 90 px bottom bar

    /** One gate card. */
    static final class Entry {
        final String label, icon, special;
        final Section section;
        final StockMenu.Item item;
        Entry(String label, Section section, StockMenu.Item item, String icon, String special) {
            this.label = label; this.section = section; this.item = item; this.icon = icon; this.special = special;
        }
    }

    private final List<Entry> entries = new ArrayList<Entry>();
    private FrameLayout body;
    private TextView titleView;
    private LinearLayout crumb;
    private String setupTitle;

    // category scene state
    private Entry cur;
    private Category cat;
    private int tab = -1;
    private final List<String> depthNames = new ArrayList<String>();
    private final List<Fn.Build> depthPages = new ArrayList<Fn.Build>();
    private LinearLayout tabList, list;
    private Skin.Scroll listScroll;
    private ImageView guideImg;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        dev.ccnc.common.Themes.apply(this);
        dev.ccnc.settings.api.Demo.init(this);   // emulator-only demo data; never on (and nothing changes) on a car build
        Tree tree = Tree.load(this);
        setupTitle = tree.byId.get(Tree.ROOT).title;   // ccNC LID_1228 "Setup"
        buildEntries(tree);

        FrameLayout root = new FrameLayout(this);
        root.addView(new Bg(this), new FrameLayout.LayoutParams(-1, -1));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        root.addView(col, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout bar = Chrome.topBar(this, setupTitle);
        for (int i = 0; i < bar.getChildCount(); i++) if (bar.getChildAt(i) instanceof TextView) { titleView = (TextView) bar.getChildAt(i); break; }
        crumb = new LinearLayout(this);
        crumb.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-2, -1);
        cl.leftMargin = 30;
        bar.addView(crumb, cl);
        if (dev.ccnc.settings.api.Demo.on()) {
            // A small "Demo" tag after the title / depth text, so emulator screenshots are never taken for car data.
            TextView demo = Skin.text(this, "Demo", 22, Skin.head(), true, 0, 1);
            android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
            g.setCornerRadius(14);
            g.setStroke(2, Skin.head());
            demo.setBackground(g);
            demo.setPadding(14, 4, 14, 4);
            LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(-2, -2);
            dl.leftMargin = 24;
            dl.gravity = Gravity.CENTER_VERTICAL;
            bar.addView(demo, dl);
        }
        col.addView(bar);
        body = new FrameLayout(this);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-1, BODY_H);
        bl.topMargin = Math.max(0, TOP - Palette.px(this, 56));
        col.addView(body, bl);
        setContentView(root);
        showGate();

        // adb: am start -n dev.ccnc.settings/.MainActivity --es open General [--ei tab 0]
        if (getIntent().getBooleanExtra("preview_ambient", false)) { previewAmbient(); return; }
        handleOpen(getIntent());
    }

    /** singleTask: a second start (e.g. from one of the legacy ccNC pages) lands here. */
    @Override protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        setIntent(i);
        if (i != null && i.hasExtra("open")) { showGate(); handleOpen(i); }
    }

    /** Extra "open": a category by its label or stock classId; optional int extra "tab". */
    private void handleOpen(Intent i) {
        String open = i == null ? null : i.getStringExtra("open");
        if (open != null) for (Entry e : entries) {
            if (open.equalsIgnoreCase(e.label) || (e.item != null && open.equals(e.item.classId))
                    || (e.section != null && open.equals(e.section.classId()))) {
                openEntry(e, false);
                int t = i.getIntExtra("tab", -1);
                if (cat != null && t >= 0 && t < cat.tabs.size()) selectTab(t);
                break;
            }
        }
    }

    /** Preview of the ambient-light colour list (adb: --ez preview_ambient true) for checking the swatches off the car. */
    private void previewAmbient() {
        Entry host = null; // a category that always has ccNC pages (Screen Theme/Layout: the ccNC theme tabs)
        for (Entry e : entries) if (e.section != null && "com.android.ThemeSettings".equals(e.section.classId())) host = e;
        if (host == null) return;
        openEntry(host, false);
        if (cat == null) return;
        final String[] names = {"Polar White", "Moon White", "Ice Blue", "Ocean Blue", "Jade Green", "Orchid Green",
                "Freesia Yellow", "Sunrise Red", "Aurora Purple", "Lightning Violet", "Concentration", "Custom Color"};
        final int[] sel = {3};
        push("Ambient Light", new Fn.Build() { @Override public void build(dev.ccnc.settings.api.Page p) {
            p.radios(names, new Fn.Int() { @Override public int get() { return sel[0]; } },
                    new Fn.SetInt() { @Override public void set(int i) { sel[0] = i; } });
        } });
    }

    @Override public void onResume() { super.onResume(); if (cat != null && list != null) refresh(); }

    @Override public void onBackPressed() {
        if (!depthPages.isEmpty()) {
            depthPages.remove(depthPages.size() - 1);
            depthNames.remove(depthNames.size() - 1);
            updateCrumb();
            renderPage(true);
        } else if (cat != null) {
            showGate();
        } else super.onBackPressed();
    }

    // ---- entries (the stock Gen5W category list) -------------------------------------------------

    private void buildEntries(Tree tree) {
        List<StockMenu.Item> stock = StockMenu.load(this);
        if (stock != null) {
            for (StockMenu.Item it : stock) {
                if (isAlexa(it.label) || it.classId.toLowerCase(java.util.Locale.US).contains("alexa")) continue;
                Section s = Sections.byClassId(it.classId);
                if (s instanceof dev.ccnc.settings.sections.NavigationSection) continue; // its settings live in the navigation app
                entries.add(new Entry(it.label, s, it, s != null ? s.icon() : null, null));
            }
        } else {
            // Stock Setup unreadable (e.g. emulator): ccNC's own top level from its menu tree.
            for (Tree.Node n : tree.byId.get(Tree.ROOT).kids) {
                if (isAlexa(n.title)) continue;
                Section s = Sections.byTreeNode(n.id);
                if (s instanceof dev.ccnc.settings.sections.NavigationSection) continue;
                boolean cluster = n.title.startsWith("Cluster");
                if (s == null && cluster) continue; // Cluster/HUD is a menu of the Vehicle category on Gen5W
                entries.add(new Entry(n.title, s, null, s != null ? s.icon() : null, null));
            }
        }
        entries.add(new Entry("Stock Launcher", null, null, null, "launcher"));
        entries.add(new Entry("NEMESIS", null, null, null, "nemesis"));
    }

    private static boolean isAlexa(String s) { return s != null && s.trim().equalsIgnoreCase("Alexa"); }

    // ---- gate --------------------------------------------------------------------------------------

    private void showGate() {
        cur = null; cat = null; tab = -1; list = null;
        depthNames.clear(); depthPages.clear();
        setTitleText(setupTitle);
        updateCrumb();
        body.removeAllViews();
        int pages = (entries.size() + 11) / 12;
        FrameLayout grid = new FrameLayout(this);
        for (int i = 0; i < entries.size(); i++) {
            int page = i / 12, k = i % 12;
            FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(CARD_W, CARD_H);
            p.leftMargin = page * PAGE_W + (k % 6) * CELL_W;
            p.topMargin = (k / 6) * CELL_H;
            grid.addView(card(entries.get(i)), p);
        }
        View content;
        if (pages > 1) {
            Pager hs = new Pager(this);
            // A HorizontalScrollView measures its child unbounded: without this the scroll range ends at the
            // last card of the last page (292 px), not at the full page.
            grid.setMinimumWidth(pages * PAGE_W);
            hs.addView(grid, new FrameLayout.LayoutParams(pages * PAGE_W, -1));
            content = hs;
        } else content = grid;
        FrameLayout.LayoutParams gp = new FrameLayout.LayoutParams(PAGE_W, -1);
        gp.leftMargin = 44;
        body.addView(content, gp);
    }

    /** SETUP.Gate card: set_bg_black + co_bg_list_grid n/p, name at (30,30) 232 wide, icon 80x80 bottom-right (15). */
    private View card(final Entry e) {
        FrameLayout c = new FrameLayout(this);
        Drawable black = Skin.nine(this, "set_bg_black");
        if (black != null) c.setBackground(black);
        View grid = new View(this);
        grid.setBackground(Skin.bg(this, "co_bg_list_grid", false));
        grid.setDuplicateParentStateEnabled(true);
        c.addView(grid, new FrameLayout.LayoutParams(-1, -1));
        // The cards stay ccNC's dark tiles (with their coloured icons) in every theme, so the name stays light.
        TextView t = Skin.text(this, e.label, 32, Skin.TEXT, true, 40, 4);
        if (lines(t, e.label, 232) > 3) t = Skin.text(this, e.label, 26, Skin.TEXT, true, 30, 4);
        FrameLayout.LayoutParams tl = new FrameLayout.LayoutParams(232, -2);
        tl.leftMargin = 30; tl.topMargin = 30;
        c.addView(t, tl);
        Bitmap ic = e.icon != null ? Skin.bmp(this, e.icon) : null;
        if (ic == null) ic = Art.icon(this, "launcher".equals(e.special) ? "general" : "nemesis".equals(e.special) ? "customization_frs" : null);
        if (ic != null) {
            ImageView iv = new ImageView(this);
            iv.setImageBitmap(ic);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            FrameLayout.LayoutParams il = new FrameLayout.LayoutParams(80, 80, Gravity.RIGHT | Gravity.BOTTOM);
            il.rightMargin = 15; il.bottomMargin = 15;
            c.addView(iv, il);
        }
        c.setClickable(true);
        c.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openEntry(e, true); }
        });
        return c;
    }

    private static int lines(TextView t, String s, int w) {
        try {
            return new StaticLayout(s, t.getPaint(), w, Layout.Alignment.ALIGN_NORMAL, 1f, 0f, false).getLineCount();
        } catch (Throwable x) { return 1; }
    }

    /** Horizontal pages of 1848 px (the gate GridView width), snapping like ccNC's page animation. */
    /** Gate pages of 1848 px: a sideways drag anywhere (also on a card) pages; a tap still opens the card. */
    private static final class Pager extends HorizontalScrollView {
        private float downX;
        private int startX;
        private boolean swiping;
        Pager(android.content.Context c) { super(c); setHorizontalScrollBarEnabled(false); setOverScrollMode(OVER_SCROLL_NEVER); }
        private int pages() { return getChildCount() == 0 ? 1 : Math.max(1, getChildAt(0).getWidth() / PAGE_W); }
        @Override public boolean dispatchTouchEvent(MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = e.getX(); startX = getScrollX(); swiping = false;
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (!swiping && Math.abs(e.getX() - downX) > 24) {
                        swiping = true;
                        MotionEvent c = MotionEvent.obtain(e);
                        c.setAction(MotionEvent.ACTION_CANCEL);
                        super.dispatchTouchEvent(c); // the card under the finger lets go of its press
                        c.recycle();
                    }
                    if (swiping) {
                        int max = (pages() - 1) * PAGE_W;
                        scrollTo(Math.max(0, Math.min(max, startX + (int) (downX - e.getX()))), 0);
                        return true;
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (swiping) {
                        int page = startX / PAGE_W;
                        float dx = downX - e.getX();
                        if (dx > 80) page = Math.min(page + 1, pages() - 1);
                        else if (dx < -80) page = Math.max(page - 1, 0);
                        smoothScrollTo(page * PAGE_W, 0);
                        swiping = false;
                        return true;
                    }
                    break;
                default:
                    break;
            }
            return super.dispatchTouchEvent(e);
        }
        @Override public boolean onTouchEvent(MotionEvent e) { return true; } // paging is handled above
    }

    // ---- category scene ----------------------------------------------------------------------------

    private void openEntry(Entry e, boolean animate) {
        if (e.special != null) { openSpecial(e.special); return; }
        Category c = new Category(this, e.label);
        if (e.section != null) {
            try { e.section.build(c); } catch (Throwable t) { Log.w(TAG, "section " + e.label + " failed", t); c.tabs.clear(); }
        }
        if (c.tabs.isEmpty()) {
            // Nothing readable for this category on this unit (e.g. emulator, or a stock category ccNC has no
            // section for): say so here. The stock Gen5W screen is never opened.
            c.tab(e.label, new Fn.Build() { @Override public void build(Page p) {
                p.note("Not available on this unit.");
            } });
        }
        cur = e; cat = c; tab = -1;
        for (int i = 0; i < c.tabs.size(); i++) if (c.tabs.get(i).page != null) { tab = i; break; }
        depthNames.clear(); depthPages.clear();
        setTitleText(c.title);
        updateCrumb();
        body.removeAllViews();
        LinearLayout scene = new LinearLayout(this);

        // left: the category's menus (HListView 492 wide in a 512 container, 536 high)
        FrameLayout left = new FrameLayout(this);
        Skin.Scroll ls = new Skin.Scroll(this);
        tabList = new LinearLayout(this);
        tabList.setOrientation(LinearLayout.VERTICAL);
        ls.addView(tabList, new FrameLayout.LayoutParams(492, -2));
        // Ends above the back/home bar, which covers the bottom-left corner: the last menus scroll up into view
        // instead of sitting under it.
        FrameLayout.LayoutParams lsl = new FrameLayout.LayoutParams(492, -1);
        lsl.bottomMargin = 44;
        ls.setVerticalFadingEdgeEnabled(true);
        ls.setFadingEdgeLength(36);
        left.addView(ls, lsl);
        for (int i = 0; i < c.tabs.size(); i++) tabList.addView(tabButton(i), new LinearLayout.LayoutParams(472, -2));
        scene.addView(left, new LinearLayout.LayoutParams(512, -1));

        // right: the selected menu's list (896) and the guide-image area (512)
        listScroll = new Skin.Scroll(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        listScroll.addView(list, new FrameLayout.LayoutParams(Skin.LIST_W, -2));
        scene.addView(listScroll, new LinearLayout.LayoutParams(Skin.LIST_W, -1));
        FrameLayout guide = new FrameLayout(this);
        guideImg = new ImageView(this);
        guideImg.setScaleType(ImageView.ScaleType.CENTER);
        FrameLayout.LayoutParams gl = new FrameLayout.LayoutParams(452, 508, Gravity.RIGHT | Gravity.TOP);
        gl.rightMargin = 20;
        guide.addView(guideImg, gl);
        scene.addView(guide, new LinearLayout.LayoutParams(512, -1));

        body.addView(scene, new FrameLayout.LayoutParams(-1, -1));
        renderPage(true);
        if (animate) pushIn(scene);
    }

    /** HTabButton: co_bg_tabbar_list n/p/s/d, 42px Regular, left padding 50, line height 48, selected bar at left. */
    private View tabButton(final int i) {
        FrameLayout b = new FrameLayout(this);
        b.setBackground(Skin.bg(this, "co_bg_tabbar_list", true));
        b.setMinimumHeight(96);
        TextView t = Skin.text(this, cat.tabs.get(i).title, 42, Skin.fg(), false, 48, 3);
        FrameLayout.LayoutParams tl = new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER_VERTICAL);
        tl.leftMargin = 50; tl.rightMargin = 12; tl.topMargin = 24; tl.bottomMargin = 24;
        b.addView(t, tl);
        View ind = new View(this);
        Drawable d = Skin.nine(this, "co_ic_tabbar_list_s");
        if (d != null) ind.setBackground(d);
        ind.setTag("ind");
        b.addView(ind, new FrameLayout.LayoutParams(10, 44, Gravity.LEFT | Gravity.CENTER_VERTICAL));
        b.setClickable(true);
        b.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { selectTab(i); }
        });
        markTab(b, i == tab);
        return b;
    }

    private void markTab(View b, boolean on) {
        b.setSelected(on);
        View ind = b.findViewWithTag("ind");
        if (ind != null) ind.setVisibility(on ? View.VISIBLE : View.INVISIBLE);
    }

    private void selectTab(int i) {
        Category.Tab t = cat.tabs.get(i);
        if (t.activity != null) { start(t.activity); return; }
        if (t.action != null) { try { t.action.run(this); } catch (Throwable x) { Log.w(TAG, "tab action", x); } return; }
        tab = i;
        depthNames.clear(); depthPages.clear();
        updateCrumb();
        for (int k = 0; k < tabList.getChildCount(); k++) markTab(tabList.getChildAt(k), k == tab);
        renderPage(true);
    }

    private void renderPage(boolean top) {
        if (list == null) return;
        final int y = listScroll.getScrollY();
        list.removeAllViews();
        Fn.Build b = null;
        String title = null;
        if (!depthPages.isEmpty()) { b = depthPages.get(depthPages.size() - 1); title = depthNames.get(depthNames.size() - 1); }
        else if (tab >= 0) { b = cat.tabs.get(tab).page; title = cat.tabs.get(tab).title; }
        guideImg.setImageDrawable(null);
        if (b == null) return;
        Page p = new Page(this, title, list);
        try { b.build(p); } catch (Throwable t) { Log.w(TAG, "page " + title + " failed", t); }
        guideImg.setScaleType(ImageView.ScaleType.CENTER);
        if (p.guide() != null) guideImg.setImageDrawable(Skin.bitmap(this, p.guide()));
        else if (p.guideImage() != null) { // e.g. a Theme Store preview: fit the area's width, at the top
            guideImg.setScaleType(ImageView.ScaleType.FIT_START);
            guideImg.setImageBitmap(p.guideImage());
        }
        listScroll.post(new Runnable() { @Override public void run() { listScroll.scrollTo(0, top ? 0 : y); } });
    }

    // ---- top bar title / depth text ----------------------------------------------------------------

    private void setTitleText(String s) { if (titleView != null) titleView.setText(s); }

    /** ccNC InternalBackButton: depth names in the top bar, "a > b", or "a > ... > last" when deeper. */
    private void updateCrumb() {
        crumb.removeAllViews();
        int n = depthNames.size();
        if (n == 0) return;
        crumbText(depthNames.get(0));
        if (n > 1) { crumbArrow(); crumbText(n > 2 ? "..." : depthNames.get(1)); }
        if (n > 2) { crumbArrow(); crumbText(depthNames.get(n - 1)); }
    }

    private void crumbText(String s) {
        TextView t = Skin.text(this, s, 32, Skin.fg(), false, 0, 1);
        t.setMaxWidth(560);
        crumb.addView(t, new LinearLayout.LayoutParams(-2, -2));
    }

    private void crumbArrow() {
        ImageView a = new ImageView(this);
        a.setImageDrawable(Skin.inked(this, "co_ic_status_subdepth_arrow"));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(40, 40);
        p.leftMargin = 10; p.rightMargin = 10;
        crumb.addView(a, p);
    }

    // ---- Host --------------------------------------------------------------------------------------

    @Override public Activity activity() { return this; }

    @Override public void push(String title, Fn.Build page) {
        if (cat == null) return;
        depthNames.add(title);
        depthPages.add(page);
        updateCrumb();
        renderPage(true);
        pushIn(listScroll);
    }

    @Override public void refresh() { renderPage(false); }

    /** The shared ccNC theme changed: redraw everything with the new palette. */
    public void themeChanged() {
        renderPage(false);
        invalidateAll(getWindow().getDecorView());
    }

    private static void invalidateAll(android.view.View v) {
        v.invalidate();
        if (v instanceof android.view.ViewGroup)
            for (int i = 0; i < ((android.view.ViewGroup) v).getChildCount(); i++) invalidateAll(((android.view.ViewGroup) v).getChildAt(i));
    }

    @Override public void back() { if (!depthPages.isEmpty()) onBackPressed(); }

    @Override public void start(Intent i) {
        try { startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
        catch (Throwable t) { Log.w(TAG, "start " + i, t); Toast.makeText(this, "--", Toast.LENGTH_SHORT).show(); }
    }

    // ---- extra entries ------------------------------------------------------------------------

    private void openSpecial(String s) {
        try {
            if (s.equals("launcher")) {
                Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage("com.android.launcher3")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                if (getPackageManager().resolveActivity(i, 0) == null) i = getPackageManager().getLaunchIntentForPackage("com.android.launcher3");
                if (i != null) startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                else Toast.makeText(this, "--", Toast.LENGTH_SHORT).show();
            } else if (s.equals("nemesis")) {
                startActivity(new Intent().setComponent(new ComponentName("com.nemesis.manager", "com.nemesis.manager.MainActivity"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            }
        } catch (Throwable t) {
            Toast.makeText(this, "--", Toast.LENGTH_SHORT).show();
        }
    }

    // ---- ccNC scene push: x 500 -> 0 and opacity 0 -> 1, 583 ms, Easing.InOutQuint ---------------

    private static final TimeInterpolator IN_OUT_QUINT = new TimeInterpolator() {
        @Override public float getInterpolation(float t) {
            return t < 0.5f ? 16 * t * t * t * t * t : 1 - (float) Math.pow(-2 * t + 2, 5) / 2;
        }
    };

    private static void pushIn(View v) {
        if (v == null) return;
        v.setTranslationX(500);
        v.setAlpha(0f);
        v.animate().translationX(0).alpha(1f).setDuration(583).setInterpolator(IN_OUT_QUINT).start();
    }

}
