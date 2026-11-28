package dev.ccnc.navi;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

import dev.ccnc.common.Art;
import dev.ccnc.common.Chrome;
import dev.ccnc.common.Drive;
import dev.ccnc.common.Fonts;
import dev.ccnc.common.Palette;

/**
 * A canvas-drawn ccNC surface in design pixels (ccNC's 1920 x 720 screen): cards with press states, taps and long
 * presses, text with ellipsis, and right-hand-drive mirroring. The hub, the keyboard, the search field and the
 * category grid are all Boards, so they look and behave alike.
 */
abstract class Board extends View {
    /** Screen pixels per design pixel (the unit is 1920 wide). */
    static float k(Context c) { return Math.max(0.1f, c.getResources().getDisplayMetrics().widthPixels / 1920f); }

    static int px(Context c, float design) { return Math.round(design * k(c)); }

    protected final float k;
    protected final boolean rhd;
    protected final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), line = new Paint(Paint.ANTI_ALIAS_FLAG),
            text = new Paint(Paint.ANTI_ALIAS_FLAG), bmp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    protected final RectF r = new RectF();
    private final List<RectF> boxes = new ArrayList<RectF>();
    private final List<Runnable> taps = new ArrayList<Runnable>(), holds = new ArrayList<Runnable>();
    private int down = -1;
    private boolean held;
    private final Runnable hold = new Runnable() {
        @Override public void run() {
            if (down < 0 || down >= holds.size() || holds.get(down) == null) return;
            held = true;
            Chrome.beep(getContext());
            Runnable h = holds.get(down);
            down = -1;
            invalidate();
            h.run();
        }
    };

    Board(Context c) {
        super(c);
        k = k(c);
        rhd = Drive.rhd(c);
        line.setStyle(Paint.Style.STROKE);
        setClickable(true);
        setSoundEffectsEnabled(false);
    }

    /** Design width of this view. */
    protected float W() { return getWidth() / k; }

    protected float H() { return getHeight() / k; }

    protected float X(float v) { return v * k; }

    /** Left edge of a box after right-hand-drive mirroring inside this view. */
    protected float mx(float l, float w) { return rhd ? W() - l - w : l; }

    /** Registers a touch box (design px); returns its index for {@link #isDown}. */
    protected int box(float l, float t, float w, float h, Runnable tap, Runnable longPress) {
        boxes.add(new RectF(X(l), X(t), X(l + w), X(t + h)));
        taps.add(tap);
        holds.add(longPress);
        return boxes.size() - 1;
    }

    protected boolean isDown(int index) { return down == index; }

    protected abstract void paint(Canvas c);

    @Override protected final void onDraw(Canvas c) {
        boxes.clear();
        taps.clear();
        holds.clear();
        paint(c);
    }

    private int hit(float x, float y) {
        for (int i = boxes.size() - 1; i >= 0; i--) if (boxes.get(i).contains(x, y)) return i;
        return -1;
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                down = hit(e.getX(), e.getY());
                held = false;
                removeCallbacks(hold);
                if (down >= 0 && holds.get(down) != null) postDelayed(hold, 650);
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (down >= 0 && hit(e.getX(), e.getY()) != down) { down = -1; removeCallbacks(hold); invalidate(); }
                return true;
            case MotionEvent.ACTION_UP:
                removeCallbacks(hold);
                int h = hit(e.getX(), e.getY()), d = down;
                down = -1;
                invalidate();
                if (!held && h >= 0 && h == d && taps.get(h) != null) { Chrome.beep(getContext()); taps.get(h).run(); }
                return true;
            case MotionEvent.ACTION_CANCEL:
                removeCallbacks(hold);
                down = -1;
                invalidate();
                return true;
            default:
                return true;
        }
    }

    // ---- drawing ----

    protected void card(Canvas c, float l, float t, float w, float h, float radius, boolean pressed, int color) {
        r.set(X(l), X(t), X(l + w), X(t + h));
        fill.setColor(pressed ? Palette.PRESS : color);
        c.drawRoundRect(r, X(radius), X(radius), fill);
        line.setStrokeWidth(Math.max(1f, X(2f)));
        line.setColor(Palette.BORDER);
        c.drawRoundRect(r, X(radius), X(radius), line);
    }

    protected void icon(Canvas c, String name, float l, float t, float s) {
        Bitmap ic = Art.icon(getContext(), name);
        if (ic == null) return;
        ic = Art.ink(ic); // light themes: the white line art in dark ink
        r.set(X(l), X(t), X(l + s), X(t + s));
        c.drawBitmap(ic, null, r, bmp);
    }

    /** A five-point star (favourites), outline or filled. */
    protected void star(Canvas c, float cx, float cy, float rad, boolean filled, int color) {
        Path p = new Path();
        for (int i = 0; i < 10; i++) {
            double a = Math.PI / 2 + i * Math.PI / 5;
            float rr = i % 2 == 0 ? rad : rad * 0.45f;
            float x = X(cx) + (float) Math.cos(a) * X(rr), y = X(cy) - (float) Math.sin(a) * X(rr);
            if (i == 0) p.moveTo(x, y); else p.lineTo(x, y);
        }
        p.close();
        Paint s = new Paint(Paint.ANTI_ALIAS_FLAG);
        s.setColor(color);
        s.setStyle(filled ? Paint.Style.FILL : Paint.Style.STROKE);
        s.setStrokeWidth(Math.max(1f, X(3f)));
        s.setStrokeJoin(Paint.Join.ROUND);
        c.drawPath(p, s);
    }

    /** Text at a baseline (design px), ellipsized to maxW when > 0. */
    protected void txt(Canvas c, String t, float x, float y, float size, int color, int face, Paint.Align a, float maxW) {
        if (t == null || t.isEmpty()) return;
        text.setTypeface(Fonts.get(getContext(), face));
        text.setTextSize(X(size));
        text.setColor(color);
        text.setTextAlign(a);
        if (maxW > 0 && text.measureText(t) > X(maxW)) t = TextUtils.ellipsize(t, new TextPaint(text), X(maxW), TextUtils.TruncateAt.END).toString();
        c.drawText(t, X(x), X(y), text);
    }

    /** Text vertically centred on cy. */
    protected void txtMid(Canvas c, String t, float x, float cy, float size, int color, int face, Paint.Align a, float maxW) {
        text.setTypeface(Fonts.get(getContext(), face));
        text.setTextSize(X(size));
        Paint.FontMetrics m = text.getFontMetrics();
        txt(c, t, x, cy - (m.ascent + m.descent) / 2f / k, size, color, face, a, maxW);
    }
}
