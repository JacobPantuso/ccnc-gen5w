package dev.ccnc.settings.api;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.StateListDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;

import dev.ccnc.common.Art;
import dev.ccnc.common.Fonts;
import dev.ccnc.common.NinePatchArt;
import dev.ccnc.common.Palette;

/**
 * ccNC Setup look: colours/sizes from the recovered setup QML, art from the ccNC dump (assets/ccnc/ui).
 * All sizes are screen px (the unit is 1920x720, like ccNC's QML coordinates).
 */
public final class Skin {
    private Skin() {}

    public static final int LIST_W = 896;                 // every setup list/delegate: width 896
    // ccNC's own (dark-theme) colours. Light themes use the fg()/sub()/head() ink below instead; TEXT itself stays
    // for text on dark art (the gate cards, a selected segment) that is dark in every theme.
    public static final int TEXT = 0xFFD1D1D1;            // main text
    public static final int TEXT_D = 0x4DD1D1D1;          // main text, disabled
    public static final int SUB = 0x80DFEDFE;             // subText (isHMC)
    public static final int SUB_D = 0x26DFEDFE;           // subText disabled (isHMC)
    public static final int HEAD = 0xFF788CA1;            // sectionHeading (isHMC)
    private static final int HEAD_LIGHT = 0xFF4E6378;     // sectionHeading on a light background (same hue, darker)

    /** Main text on the page background: TEXT, or the light theme's ink. Read when a view is built. */
    public static int fg() { return Palette.LIGHT ? Palette.TEXT : TEXT; }
    /** Main text, disabled. */
    public static int fgOff() { return Palette.LIGHT ? Palette.alpha(Palette.TEXT, 0x4D) : TEXT_D; }
    /** subText. */
    public static int sub() { return Palette.LIGHT ? Palette.SUBTEXT : SUB; }
    /** subText, disabled. */
    public static int subOff() { return Palette.LIGHT ? Palette.alpha(Palette.SUBTEXT, 0x4D) : SUB_D; }
    /** sectionHeading text and bar. */
    public static int head() { return Palette.LIGHT ? HEAD_LIGHT : HEAD; }
    /** Thin lines (list separators) on the page background; only used in light themes (dark ones keep ccNC's art). */
    public static int line() { return Palette.DIVIDER; }

    static final int EN = android.R.attr.state_enabled, PR = android.R.attr.state_pressed, SEL = android.R.attr.state_selected;

    /** Plain bitmap from assets/ccnc/ui, with its density set so 1 image px = 1 screen px. */
    public static Bitmap bmp(Context c, String name) {
        Bitmap b = NinePatchArt.bitmap(c, name);
        if (b != null) b.setDensity(c.getResources().getDisplayMetrics().densityDpi);
        return b;
    }

    /** A raw ".9.png" from assets/ccnc/ui as a NinePatchDrawable; name without ".9". */
    public static Drawable nine(Context c, String name) {
        bmp(c, name + ".9");
        return NinePatchArt.patch(c, name + ".9");
    }

    public static Drawable bitmap(Context c, String name) {
        Bitmap b = bmp(c, name);
        return b == null ? null : new BitmapDrawable(c.getResources(), b);
    }

    /** {@link #bitmap} for white line art on the page background: in light themes redrawn in the text ink. */
    public static Drawable inked(Context c, String name) {
        Bitmap b = Art.ink(bmp(c, name));
        return b == null ? null : new BitmapDrawable(c.getResources(), b);
    }

    private static void add(StateListDrawable s, int[] st, Drawable d) { if (d != null) s.addState(st, d); }

    /** 9-patch background with ccNC's HNinePatchImageSelector states: d (disabled), p (pressed), s (selected, optional), n. */
    public static Drawable bg(Context c, String base, boolean selectable) {
        StateListDrawable s = new StateListDrawable();
        add(s, new int[]{-EN}, nine(c, base + "_d"));
        add(s, new int[]{PR}, nine(c, base + "_p"));
        if (selectable) add(s, new int[]{SEL}, nine(c, base + "_s"));
        add(s, new int[]{}, nine(c, base + "_n"));
        return s;
    }

    /** Plain-png state list: d, p, n (e.g. co_ic_arrow_right, bco_sp_btn_bg). */
    public static Drawable states(Context c, String base) {
        StateListDrawable s = new StateListDrawable();
        add(s, new int[]{-EN}, bitmap(c, base + "_d"));
        add(s, new int[]{PR}, bitmap(c, base + "_p"));
        add(s, new int[]{}, bitmap(c, base + "_n"));
        return s;
    }

    /** {@link #states} of white line art (list arrow, +/- glyphs): in light themes each state in the text ink. */
    public static Drawable statesInked(Context c, String base) {
        if (!Palette.LIGHT) return states(c, base);
        StateListDrawable s = new StateListDrawable();
        add(s, new int[]{-EN}, inked(c, base + "_d"));
        add(s, new int[]{PR}, inked(c, base + "_p"));
        add(s, new int[]{}, inked(c, base + "_n"));
        return s;
    }

    /**
     * Check / radio indicator (basic/checkbutton/bco_check_btn, basic/radiobutton/bco_radio_btn), CHECKDELEGATE state
     * order: on_d, on_p, on_s, off_d, off_p, off_n. "Checked" is the view's selected state.
     */
    public static Drawable indicator(Context c, String base) {
        StateListDrawable s = new StateListDrawable();
        add(s, new int[]{SEL, -EN}, bitmap(c, base + "_on_d"));
        add(s, new int[]{SEL, PR}, bitmap(c, base + "_on_p"));
        add(s, new int[]{SEL}, bitmap(c, base + "_on_s"));
        add(s, new int[]{-EN}, bitmap(c, base + "_off_d"));
        add(s, new int[]{PR}, bitmap(c, base + "_off_p"));
        add(s, new int[]{}, bitmap(c, base + "_off_n"));
        return s;
    }

    public static ImageView image(Context c, Drawable d) {
        ImageView v = new ImageView(c);
        v.setImageDrawable(d);
        v.setScaleType(ImageView.ScaleType.FIT_XY);
        v.setDuplicateParentStateEnabled(true);
        return v;
    }

    /** HText: px size, colour, SemiBold or Regular, fixed line height (lineHeightMode FixedHeight), max lines. */
    public static TextView text(Context c, String s, int px, int color, boolean semibold, int lineH, int maxLines) {
        TextView v = new TextView(c);
        v.setText(s);
        v.setTextSize(TypedValue.COMPLEX_UNIT_PX, px);
        v.setTextColor(color);
        v.setTypeface(Fonts.get(c, semibold ? Fonts.SEMIBOLD : Fonts.REGULAR));
        // Keep the font's own padding: without it (or with a line height below the font's), descenders
        // (g, y, p) are clipped on the car. A larger ccNC line height only adds space, never removes it.
        v.setIncludeFontPadding(true);
        if (lineH > 0) {
            int fh = v.getPaint().getFontMetricsInt(null);
            int extra = lineH - fh;
            if (extra > 0) {
                if (maxLines > 1) v.setLineSpacing(extra, 1f);
                v.setPadding(0, extra / 2, 0, extra - extra / 2);
            }
        }
        if (maxLines <= 1) v.setSingleLine(true); else v.setMaxLines(maxLines);
        v.setEllipsize(TextUtils.TruncateAt.END);
        v.setDuplicateParentStateEnabled(true);
        return v;
    }

    /** A vertical list scroller with ccNC's HScrollBar handle (basic/scrollbar bco_scrollbar_bg_bar_v_n) at the right edge. */
    public static final class Scroll extends ScrollView {
        private final Drawable bar;
        private android.graphics.Paint paint;
        public Scroll(Context c) {
            super(c);
            setVerticalScrollBarEnabled(false);
            setOverScrollMode(View.OVER_SCROLL_NEVER);
            setFillViewport(false);
            bar = nine(c, "bco_scrollbar_bg_bar_v_n");
        }
        @Override public void draw(Canvas cv) {
            super.draw(cv);
            View k = getChildCount() > 0 ? getChildAt(0) : null;
            if (k == null || bar == null) return;
            int h = getHeight(), ch = k.getHeight();
            if (ch <= h) return;
            int th = Math.max(48, h * h / ch);
            int top = getScrollY() + (int) ((long) (h - th) * getScrollY() / (ch - h));
            int w = getWidth();
            if (Palette.LIGHT) { // the handle art is light grey: draw it in the theme's ink instead
                if (paint == null) paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
                paint.setColor(Palette.alpha(Palette.TEXT, 0x59));
                cv.drawRoundRect(new android.graphics.RectF(w - 12, top, w - 7, top + th), 2.5f, 2.5f, paint);
                return;
            }
            bar.setBounds(w - 12, top, w - 7, top + th);
            bar.draw(cv);
        }
    }
}
