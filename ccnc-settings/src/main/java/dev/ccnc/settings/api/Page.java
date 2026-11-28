package dev.ccnc.settings.api;

import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * One right-pane list (ccNC SubView1 "menu": HListView 896 wide) and its row builders. Geometry is from the
 * recovered setup QML: CHECKDELEGATE / RADIODELEGATE (indicator 42x42 at x 63, text at x 142), ITEMDELEGATE
 * (text at x 44, subText below, arrow 40x40 at right 50), sectionHeading (84 high, 2px divider at x 44, text x 62),
 * USM slider delegate (229 high: -/+ 60x60 at x 54 / right 54, bar 658 wide), SLIDINGSWITCH (segments 278 x 78 at x 37).
 * Rows are rebuilt every time the page is shown, so getters are always re-read.
 */
public final class Page {
    public final String title;
    private final Host host;
    private final Context c;
    private final LinearLayout list;
    private String guide;

    public Page(Host host, String title, LinearLayout list) {
        this.host = host; this.title = title; this.list = list; this.c = host.activity();
    }

    public Context context() { return c; }
    public Host host() { return host; }
    /** ccNC wording for a menu-tree node (assets/tree.tsv). */
    public String word(int node, String fallback) { return Words.get(c, node, fallback); }

    /** Right-hand guide illustration (ccNC guideImg, 452x508 area) from assets/ccnc/ui; only ccNC art. */
    public Page guide(String asset) { guide = asset; return this; }
    public String guide() { return guide; }

    private android.graphics.Bitmap guideImage;
    /** A picture for the guide area on the right (e.g. a theme preview), instead of a bundled asset. */
    public Page guideImage(android.graphics.Bitmap b) { guideImage = b; return this; }
    public android.graphics.Bitmap guideImage() { return guideImage; }

    // ---- section heading ----------------------------------------------------------------------

    /** ccNC sectionHeading: 28px #788ca1 with the 2px set_etc_h_divider bar at x 44. */
    public Row heading(String t) {
        FrameLayout r = new FrameLayout(c);
        View bar = new View(c);
        Drawable d = Skin.nine(c, "set_etc_h_divider");
        if (d != null) bar.setBackground(d); else bar.setBackgroundColor(Skin.HEAD);
        r.addView(bar, lp(2, 36, 44, 24));
        TextView tv = Skin.text(c, t, 28, Skin.HEAD, false, 32, 2);
        FrameLayout.LayoutParams tl = new FrameLayout.LayoutParams(Skin.LIST_W - 92, -2, Gravity.CENTER_VERTICAL);
        tl.leftMargin = 62;
        r.addView(tv, tl);
        r.setMinimumHeight(84);
        add(r, -2);
        return new Row(r, null, null);
    }

    // ---- image (e.g. a theme preview) ---------------------------------------------------------

    /** A full-width picture row (fitted to the width, rounded), at most h pixels tall. */
    public Row image(android.graphics.Bitmap bmp, int h) {
        FrameLayout r = new FrameLayout(c);
        final android.graphics.Bitmap b = bmp;
        View v = new View(c) {
            private final android.graphics.Paint pt = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG | android.graphics.Paint.FILTER_BITMAP_FLAG);
            private final android.graphics.RectF dst = new android.graphics.RectF();
            @Override protected void onDraw(android.graphics.Canvas cv) {
                if (b == null) return;
                float w = getWidth(), k = w / b.getWidth(), hh = Math.min(getHeight(), b.getHeight() * k); // whole width
                android.graphics.BitmapShader sh = new android.graphics.BitmapShader(b, android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP);
                android.graphics.Matrix m = new android.graphics.Matrix();
                m.setScale(k, k);
                m.postTranslate((w - b.getWidth() * k) / 2f, (hh - b.getHeight() * k) / 2f);
                sh.setLocalMatrix(m);
                pt.setShader(sh);
                dst.set(0, 0, w, hh);
                cv.drawRoundRect(dst, 18, 18, pt);
            }
        };
        r.addView(v, lp(Skin.LIST_W - 124, h - 24, 62, 12));
        add(r, h);
        return new Row(r, null, null);
    }

    // ---- check (toggle) -----------------------------------------------------------------------

    public Row toggle(String t, Fn.Bool get, Fn.SetBool set) { return toggle(t, null, get, set); }

    /**
     * ccNC CHECKDELEGATE: the small 42x42 check box (bco_check_btn on_s with check mark / off_n outline), title
     * 32px SemiBold. Tap writes !get() and then shows what get() reads back. sub: ccNC subText only (or null).
     */
    public Row toggle(String t, String sub, final Fn.Bool get, final Fn.SetBool set) {
        final FrameLayout[] holder = new FrameLayout[1];
        final Row row = indicatorRow(t, sub, "bco_check_btn", holder);
        final FrameLayout r = holder[0];
        r.setSelected(safe(get));
        r.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                boolean want = !safe(get);
                try { set.set(want); } catch (Throwable ignored) { }
                r.setSelected(safe(get));
            }
        });
        return row;
    }

    // ---- radio --------------------------------------------------------------------------------

    /** ccNC RADIODELEGATE rows, one per label (bco_radio_btn); tap calls set(index) and redraws the page. */
    public void radios(String[] labels, final Fn.Int get, final Fn.SetInt set) { radios(labels, null, get, set); }

    /** Radios with an optional caption under each label ({@code subs} may be null, or hold nulls). */
    public void radios(String[] labels, String[] subs, final Fn.Int get, final Fn.SetInt set) {
        int cur = safe(get);
        boolean colours = AmbientColors.isColourList(labels);
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            FrameLayout[] holder = new FrameLayout[1];
            indicatorRow(labels[i], subs != null && i < subs.length ? subs[i] : null, "bco_radio_btn", holder, colours);
            holder[0].setSelected(i == cur);
            holder[0].setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    try { set.set(idx); } catch (Throwable ignored) { }
                    host.refresh();
                }
            });
        }
    }

    private Row indicatorRow(String t, String sub, String art, FrameLayout[] out) { return indicatorRow(t, sub, art, out, false); }

    private Row indicatorRow(String t, String sub, String art, FrameLayout[] out, boolean swatch) {
        boolean hasSub = sub != null && sub.length() > 0;
        FrameLayout r = shell();
        LinearLayout content = new LinearLayout(c);
        content.setOrientation(LinearLayout.HORIZONTAL);
        content.setGravity(hasSub ? Gravity.TOP : Gravity.CENTER_VERTICAL);
        content.setDuplicateParentStateEnabled(true);
        content.setPadding(63, hasSub ? 43 : 18, 30, hasSub ? 33 : 18);
        content.setMinimumHeight(124);
        ImageView ind = Skin.image(c, Skin.indicator(c, art));
        LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(42, 42);
        content.addView(ind, il);
        if (swatch) {
            // Ambient light colour lists: the colour itself left of its name.
            LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(44, 44);
            sl.leftMargin = 30;
            content.addView(AmbientColors.view(c, AmbientColors.of(t)), sl);
        }
        LinearLayout col = column();
        TextView tv = Skin.text(c, t, 32, Skin.TEXT, true, 40, 3);
        col.addView(tv);
        TextView sv = null;
        if (hasSub) { sv = Skin.text(c, sub, 28, Skin.SUB, false, 32, 5); col.addView(sv, top(10)); }
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, -2, 1f);
        cl.leftMargin = 37;
        content.addView(col, cl);
        r.addView(content, new FrameLayout.LayoutParams(-1, -2));
        line(r);
        add(r, -2);
        out[0] = r;
        return new Row(r, tv, sv);
    }

    // ---- value / drill / activity / stock / button rows (ITEMDELEGATE) ----------------------

    /** Title with the current value as ccNC subText under it; arrow when click != null. value may be null. */
    public Row value(String t, Fn.Str value, Fn.Act click) { return item(t, value, click, click != null); }

    /** Drill into a sub-page in place (top bar then shows the sub-page title, like ccNC's InternalBackButton). */
    public Row drill(final String t, Fn.Str value, final Fn.Build sub) {
        return item(t, value, new Fn.Act() { @Override public void run(Host h) { h.push(t, sub); } }, true);
    }

    /** Drill row showing the selected label; its sub-page is the radio list of labels. */
    public Row choice(final String t, final String[] labels, final Fn.Int get, final Fn.SetInt set) {
        return drill(t, new Fn.Str() {
            @Override public String get() { int i = safe(get); return i >= 0 && i < labels.length ? labels[i] : null; }
        }, new Fn.Build() { @Override public void build(Page p) { p.radios(labels, get, set); } });
    }

    /** Row opening one of our own native activities. */
    public Row activity(String t, final Intent i) {
        return item(t, null, new Fn.Act() { @Override public void run(Host h) { h.start(i); } }, true);
    }

    /** Read-only row: title with its current value under it (no arrow, not tappable). */
    public Row info(String t, final String value) {
        return item(t, value == null ? null : new Fn.Str() { @Override public String get() { return value; } }, null, false);
    }

    /**
     * Explanatory text (ccNC description style: 28px sub-text colour, wrapped). Used where a setting can't be
     * changed from a normal app, to say so on the page instead of opening a stock screen.
     */
    public Row note(String t) {
        FrameLayout r = new FrameLayout(c);
        TextView tv = Skin.text(c, t, 28, Skin.SUB, false, 36, 8);
        FrameLayout.LayoutParams tl = new FrameLayout.LayoutParams(Skin.LIST_W - 44 - 50, -2);
        tl.leftMargin = 44; tl.topMargin = 26; tl.bottomMargin = 26;
        r.addView(tv, tl);
        line(r);
        add(r, -2);
        return new Row(r, null, tv);
    }

    /** Plain tappable row without arrow (actions such as a reset that ccNC shows as a list item). */
    public Row button(String t, Fn.Act click) { return item(t, null, click, false); }

    private Row item(String t, Fn.Str value, final Fn.Act click, boolean arrow) {
        String v = value == null ? null : safe(value);
        boolean hasSub = v != null && v.length() > 0;
        FrameLayout r = shell();
        LinearLayout content = new LinearLayout(c);
        content.setOrientation(LinearLayout.HORIZONTAL);
        content.setGravity(Gravity.CENTER_VERTICAL);
        content.setDuplicateParentStateEnabled(true);
        content.setPadding(44, 18, 0, 18);
        content.setMinimumHeight(124);
        LinearLayout col = column();
        TextView tv = Skin.text(c, t, 32, Skin.TEXT, true, 40, 2);
        col.addView(tv);
        TextView sv = null;
        if (hasSub) { sv = Skin.text(c, v, 28, Skin.SUB, false, 32, 2); col.addView(sv, top(10)); }
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, -2, 1f);
        cl.rightMargin = arrow ? 20 : 30;
        content.addView(col, cl);
        if (arrow) {
            ImageView a = Skin.image(c, Skin.states(c, "co_ic_arrow_right"));
            LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(40, 40);
            al.rightMargin = 50;
            content.addView(a, al);
        }
        r.addView(content, new FrameLayout.LayoutParams(-1, -2));
        line(r);
        if (click != null) r.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View x) { try { click.run(host); } catch (Throwable ignored) { } }
        });
        else r.setClickable(false);
        add(r, -2);
        return new Row(r, tv, sv);
    }

    // ---- slider -------------------------------------------------------------------------------

    /** USM slider delegate: title, value at right, -/+ spinner buttons and the bco_slider bar. set() on release / +/-. */
    public Row slider(String t, final int min, final int max, final Fn.Int get, final Fn.SetInt set) {
        FrameLayout r = new FrameLayout(c);
        TextView tv = Skin.text(c, t, 32, Skin.TEXT, true, 40, 1);
        r.addView(tv, lp(Skin.LIST_W - 44 - 160, -2, 44, 18));
        final TextView val = Skin.text(c, String.valueOf(safe(get)), 28, Skin.SUB, false, 32, 1);
        FrameLayout.LayoutParams vl = new FrameLayout.LayoutParams(-2, -2, Gravity.RIGHT | Gravity.TOP);
        vl.rightMargin = 28; vl.topMargin = 26;
        r.addView(val, vl);
        final Bar bar = new Bar(c, min, max, safe(get));
        bar.listener = new Fn.SetInt() {
            @Override public void set(int v) { val.setText(String.valueOf(v)); }
        };
        bar.release = new Fn.SetInt() {
            // The value chosen: some settings reach it over a moment (tone and position step one level at a time).
            @Override public void set(int v) { try { set.set(v); } catch (Throwable ignored) { } val.setText(String.valueOf(v)); }
        };
        r.addView(bar, lp(Skin.LIST_W - 2 * 119, 60, 119, 58 + 55));
        r.addView(spin(false, bar), lp(60, 60, 54, 58 + 55));
        r.addView(spin(true, bar), lp(60, 60, Skin.LIST_W - 54 - 60, 58 + 55));
        r.setMinimumHeight(58 + 171);
        line(r);
        add(r, 58 + 171);
        return new Row(r, tv, val);
    }

    private View spin(final boolean plus, final Bar bar) {
        FrameLayout b = new FrameLayout(c);
        b.setBackground(Skin.states(c, "bco_sp_btn_bg"));
        ImageView ic = Skin.image(c, Skin.states(c, plus ? "bco_sp_ic_plus" : "bco_sp_ic_minus"));
        b.addView(ic, new FrameLayout.LayoutParams(60, 60));
        b.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { bar.step(plus ? 1 : -1); }
        });
        return b;
    }

    static final class Bar extends View {
        final int min, max; int value;
        Fn.SetInt listener, release;
        private final Drawable bg, fill, knob;
        Bar(Context c, int min, int max, int value) {
            super(c);
            this.min = min; this.max = Math.max(min + 1, max); this.value = Math.max(min, Math.min(this.max, value));
            bg = Skin.nine(c, "bco_slider_bg_n");
            fill = Skin.nine(c, "bco_slider_bg_bar_n");
            knob = Skin.bitmap(c, "bco_slider_btn_box_n");
        }
        void step(int d) {
            value = Math.max(min, Math.min(max, value + d));
            invalidate();
            if (release != null) release.set(value);
        }
        private int knobX() { return (int) ((long) (getWidth() - 60) * (value - min) / (max - min)); }
        @Override protected void onDraw(Canvas cv) {
            int w = getWidth(), h = getHeight(), th = 8, ty = (h - th) / 2;
            int kx = knobX();
            if (bg != null) { bg.setBounds(0, ty, w, ty + th); bg.draw(cv); }
            if (fill != null) { fill.setBounds(0, ty, kx + 30, ty + th); fill.draw(cv); }
            if (knob != null) { knob.setBounds(kx, 0, kx + 60, 60); knob.draw(cv); }
        }
        @Override public boolean onTouchEvent(MotionEvent e) {
            int a = e.getAction();
            if (a == MotionEvent.ACTION_DOWN && getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
            if (a == MotionEvent.ACTION_DOWN || a == MotionEvent.ACTION_MOVE || a == MotionEvent.ACTION_UP) {
                float f = (e.getX() - 30) / Math.max(1, getWidth() - 60);
                value = Math.max(min, Math.min(max, min + Math.round(f * (max - min))));
                invalidate();
                if (listener != null) listener.set(value);
                if (a == MotionEvent.ACTION_UP && release != null) release.set(value);
                return true;
            }
            return super.onTouchEvent(e);
        }
    }

    // ---- sliding switch (segmented) -------------------------------------------------------------

    /** ccNC SLIDINGSWITCH delegate: title, then segments 278 x 78 (bco_sdsw_op_bg / _btn_s); tap = set(index). */
    public Row segmented(String t, String[] labels, final Fn.Int get, final Fn.SetInt set) {
        FrameLayout r = new FrameLayout(c);
        TextView tv = Skin.text(c, t, 32, Skin.TEXT, true, 40, 1);
        r.addView(tv, lp(Skin.LIST_W - 74, -2, 44, 18));
        LinearLayout sw = new LinearLayout(c);
        Drawable sbg = Skin.nine(c, "bco_sdsw_op_bg_n");
        if (sbg != null) sw.setBackground(sbg);
        int cur = safe(get);
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            FrameLayout seg = new FrameLayout(c);
            if (i == cur) { Drawable sel = Skin.nine(c, "bco_sdsw_op_btn_s"); if (sel != null) seg.setBackground(sel); }
            TextView l = Skin.text(c, labels[i], 28, Skin.TEXT, i == cur, 0, 1);
            l.setGravity(Gravity.CENTER);
            seg.addView(l, new FrameLayout.LayoutParams(-1, -1));
            seg.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { try { set.set(idx); } catch (Throwable ignored) { } host.refresh(); }
            });
            sw.addView(seg, new LinearLayout.LayoutParams(278, 78));
        }
        r.addView(sw, lp(-2, 78, 37, 18 + 40 + 35));
        r.setMinimumHeight(18 + 40 + 35 + 78 + 30);
        line(r);
        add(r, 18 + 40 + 35 + 78 + 30);
        return new Row(r, tv, null);
    }

    // ---- plumbing -----------------------------------------------------------------------------

    private FrameLayout shell() {
        FrameLayout r = new FrameLayout(c);
        r.setBackground(Skin.bg(c, "co_bg_tabbar_list", false));
        r.setClickable(true);
        return r;
    }

    private LinearLayout column() {
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setDuplicateParentStateEnabled(true);
        return col;
    }

    /** co_bg_list_line under every delegate. */
    private void line(FrameLayout r) {
        View l = new View(c);
        Drawable d = Skin.nine(c, "co_bg_list_line");
        if (d != null) l.setBackground(d); else l.setBackgroundColor(0x33FFFFFF);
        r.addView(l, new FrameLayout.LayoutParams(-1, 1, Gravity.BOTTOM));
    }

    private void add(View v, int h) { list.addView(v, new LinearLayout.LayoutParams(Skin.LIST_W, h)); }

    private static FrameLayout.LayoutParams lp(int w, int h, int x, int y) {
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(w, h);
        p.leftMargin = x; p.topMargin = y;
        return p;
    }

    private static LinearLayout.LayoutParams top(int m) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = m;
        return p;
    }

    static boolean safe(Fn.Bool f) { try { return f.get(); } catch (Throwable t) { return false; } }
    static int safe(Fn.Int f) { try { return f.get(); } catch (Throwable t) { return -1; } }
    static String safe(Fn.Str f) { try { return f.get(); } catch (Throwable t) { return null; } }

}
