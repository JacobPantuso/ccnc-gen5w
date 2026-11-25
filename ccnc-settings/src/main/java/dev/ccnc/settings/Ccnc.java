package dev.ccnc.settings;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import dev.ccnc.common.Fonts;
import dev.ccnc.common.NinePatchArt;
import dev.ccnc.common.Palette;

/** ccNC setup list widgets built from the ccNC art (9-patches in assets/ccnc/ui) at the QML pixel sizes (1920x720 px). */
final class Ccnc {
    private Ccnc() {}

    static final int ROW_H = 96;       // "height: 96*count" in the vehicle setup lists
    static final int LIST_W = 896;     // sectionHeading width in setup QML
    static final int TEXT_PX = 30, VALUE_PX = 28, HEAD_PX = 28;
    static final int HEAD_COLOR = 0xFF788CA1;

    static TextView text(Context c, String t, int px, int color, int face) {
        TextView v = new TextView(c);
        v.setText(t);
        v.setTextSize(TypedValue.COMPLEX_UNIT_PX, px);
        v.setTextColor(color);
        v.setTypeface(Fonts.get(c, face));
        v.setSingleLine(true);
        v.setEllipsize(android.text.TextUtils.TruncateAt.END);
        return v;
    }

    static Drawable rowBg(Context c, boolean enabled) {
        StateListDrawable s = new StateListDrawable();
        Drawable p = NinePatchArt.patch(c, "co_bg_list_h_p.9");
        Drawable d = NinePatchArt.patch(c, "co_bg_list_h_d.9");
        Drawable n = NinePatchArt.patch(c, "co_bg_list_h_n.9");
        if (p != null) s.addState(new int[]{android.R.attr.state_pressed}, p);
        if (d != null && !enabled) s.addState(new int[]{-android.R.attr.state_enabled}, d);
        if (n != null) s.addState(new int[]{}, n);
        return s;
    }

    /** One list row: ccNC list background, title (left), optional value, optional trailing view. */
    static LinearLayout row(Context c, String title, String value, View trailing, View.OnClickListener click) {
        LinearLayout r = new LinearLayout(c);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setBackground(rowBg(c, true));
        r.setPadding(40, 0, 24, 0);
        TextView t = text(c, title, TEXT_PX, Palette.TEXT, Fonts.REGULAR);
        r.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        if (value != null) {
            TextView v = text(c, value, VALUE_PX, HEAD_COLOR, Fonts.REGULAR);
            v.setMaxWidth(420);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.leftMargin = 20;
            r.addView(v, lp);
        }
        if (trailing != null) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.leftMargin = 12;
            r.addView(trailing, lp);
        }
        if (click != null) r.setOnClickListener(click); else r.setEnabled(false);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(LIST_W, ROW_H);
        r.setLayoutParams(rp);
        return r;
    }

    static ImageView arrow(Context c) {
        ImageView i = new ImageView(c);
        Bitmap b = NinePatchArt.bitmap(c, "co_ic_arrow_right_n");
        if (b != null) i.setImageBitmap(b);
        return i;
    }

    /** ccNC menu toggle art (co_bg_menu_toggle_on/off). */
    static final class Toggle extends ImageView {
        interface OnChanged { void onChanged(boolean on); }
        private boolean on;
        private OnChanged l;
        Toggle(Context c, boolean on) { super(c); this.on = on; setScaleType(ScaleType.FIT_CENTER); setAdjustViewBounds(true); setMaxWidth(80); setMaxHeight(80); update(); }
        void setListener(OnChanged l) { this.l = l; }
        boolean isOn() { return on; }
        void set(boolean v) { on = v; update(); }
        void flip() { on = !on; update(); if (l != null) l.onChanged(on); }
        private void update() {
            Bitmap b = NinePatchArt.bitmap(getContext(), on ? "co_bg_menu_toggle_on_n" : "co_bg_menu_toggle_off_n");
            if (b != null) setImageBitmap(b);
        }
    }

    /** Slider drawn with ccNC's quick-slider 9-patches. */
    static final class Slider extends View {
        interface OnChanged { void onChanged(int value, boolean done); }
        private final Drawable bg, bar;
        private final int max;
        private int value;
        private OnChanged l;
        private final Rect r = new Rect();
        Slider(Context c, int max, int value) {
            super(c);
            this.max = Math.max(1, max); this.value = value;
            bg = NinePatchArt.patch(c, "co_bg_quick_sliderbg_n.9");
            bar = NinePatchArt.patch(c, "co_bg_quick_sliderbar_n.9");
        }
        void setListener(OnChanged l) { this.l = l; }
        @Override protected void onMeasure(int w, int h) { setMeasuredDimension(420, 64); }
        @Override protected void onDraw(Canvas c) {
            int w = getWidth(), h = getHeight();
            if (bg != null) { bg.setBounds(0, 0, w, h); bg.draw(c); }
            int bw = Math.max(h, (int) ((long) w * value / max));
            if (bar != null) { bar.setBounds(0, 0, bw, h); bar.draw(c); }
        }
        @Override public boolean onTouchEvent(MotionEvent e) {
            int a = e.getAction();
            if (a == MotionEvent.ACTION_DOWN) getParent().requestDisallowInterceptTouchEvent(true);
            if (a == MotionEvent.ACTION_DOWN || a == MotionEvent.ACTION_MOVE || a == MotionEvent.ACTION_UP) {
                value = Math.max(0, Math.min(max, (int) (max * e.getX() / getWidth())));
                invalidate();
                if (l != null) l.onChanged(value, a == MotionEvent.ACTION_UP);
                return true;
            }
            return super.onTouchEvent(e);
        }
    }

    /** ccNC sectionHeading: 28px #788ca1 text with the 2px vertical divider (set_etc_h_divider). */
    static View heading(Context c, String t) {
        LinearLayout r = new LinearLayout(c);
        r.setGravity(Gravity.CENTER_VERTICAL);
        View line = new View(c);
        Drawable d = NinePatchArt.patch(c, "set_etc_h_divider.9");
        if (d != null) line.setBackground(d); else line.setBackgroundColor(HEAD_COLOR);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(2, 36);
        lp.leftMargin = 44;
        r.addView(line, lp);
        TextView tv = text(c, t, HEAD_PX, HEAD_COLOR, Fonts.REGULAR);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, -2, 1f);
        tp.leftMargin = 16;
        r.addView(tv, tp);
        r.setLayoutParams(new LinearLayout.LayoutParams(LIST_W, 36 + 24 + 24));
        return r;
    }
}
