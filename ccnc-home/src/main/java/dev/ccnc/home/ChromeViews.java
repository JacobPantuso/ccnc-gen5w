package dev.ccnc.home;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.text.format.DateFormat;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Background, top bar and page indicator. */
final class ChromeViews {
    private ChromeViews() {}

    /** Dark blue-grey sky with a soft light streak, like the ccNC default theme. */
    static final class Background extends View {
        private final Paint base = new Paint();
        private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint glow2 = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint shade = new Paint();

        private Theme drawnTheme;
        private android.graphics.Bitmap wall;
        private final RectF dst = new RectF();
        private final Paint bmp = new Paint(Paint.FILTER_BITMAP_FLAG);

        Background(Context c) { super(c); }

        @Override protected void onSizeChanged(int w, int h, int ow, int oh) { drawnTheme = null; }

        private void shaders(Theme t, int w, int h) {
            base.setShader(new LinearGradient(0, 0, w, h, t.bg,
                    new float[]{0f, 0.35f, 0.6f, 1f}, Shader.TileMode.CLAMP));
            glow.setShader(new RadialGradient(w * 0.42f, -h * 0.1f, Math.max(w, h) * 0.45f,
                    t.glow1, t.glow1 & 0x00FFFFFF, Shader.TileMode.CLAMP));
            glow2.setShader(new RadialGradient(w * 0.78f, h * 0.25f, Math.max(w, h) * 0.3f,
                    t.glow2, t.glow2 & 0x00FFFFFF, Shader.TileMode.CLAMP));
            shade.setShader(new LinearGradient(0, h * 0.55f, 0, h, t.shade & 0x00FFFFFF, t.shade, Shader.TileMode.CLAMP));
        }

        @Override protected void onDraw(Canvas c) {
            int w = getWidth(), h = getHeight();
            if (drawnTheme != Theme.current) {
                drawnTheme = Theme.current;
                shaders(drawnTheme, w, h);
                wall = dev.ccnc.common.Art.wallpaper(getContext(), drawnTheme.wallpaper);
            }
            if (wall != null) {
                // Centre-crop the 1920x720 wallpaper to the view, then the theme's bottom shade.
                float s = Math.max(w / (float) wall.getWidth(), h / (float) wall.getHeight());
                float bw = wall.getWidth() * s, bh = wall.getHeight() * s;
                dst.set((w - bw) / 2f, (h - bh) / 2f, (w + bw) / 2f, (h + bh) / 2f);
                c.drawBitmap(wall, null, dst, bmp);
                c.drawRect(0, 0, w, h, shade);
                return;
            }
            c.drawRect(0, 0, w, h, base);
            c.drawRect(0, 0, w, h, glow);
            c.drawRect(0, 0, w, h, glow2);
            c.drawRect(0, 0, w, h, shade);
        }
    }

    /** Centred date, Bluetooth glyph and clock (faded out over the welcome page). */
    static final class TopBar extends View {
        private final float dp;
        private final Paint handle = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float clockAlpha = 1f;

        /** The clock (right end): 7 taps open ccNC's developer menu. */
        @Override public boolean onTouchEvent(android.view.MotionEvent e) {
            if (e.getActionMasked() == android.view.MotionEvent.ACTION_UP && e.getX() > getWidth() - 170 * dp
                    && getContext() instanceof android.app.Activity)
                dev.ccnc.common.DevMenu.tap((android.app.Activity) getContext());
            // Take the touch from its start so the tap's ACTION_UP arrives (7 taps on the clock: developer menu).
            if (e.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) return true;
            return super.onTouchEvent(e);
        }

        /** 0 on the welcome page (its own big clock), 1 on the app pages; in between while swiping. */
        void setClockAlpha(float a) {
            a = Math.max(0f, Math.min(1f, a));
            if (a != clockAlpha) { clockAlpha = a; invalidate(); }
        }
        private final Paint date = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint time = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ampm = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint bt = new Paint(Paint.ANTI_ALIAS_FLAG);

        TopBar(Context c, Typeface bold, Typeface regular) {
            super(c);
            dp = c.getResources().getDisplayMetrics().density;
            handle.setColor(0xA6FFFFFF);
            handle.setStrokeWidth(3f * dp);
            handle.setStrokeCap(Paint.Cap.ROUND);
            date.setColor(0xFFFFFFFF);
            date.setTypeface(bold);
            date.setTextSize(22f * dp);
            date.setTextAlign(Paint.Align.CENTER);
            time.setColor(0xFFFFFFFF);
            time.setTypeface(bold);
            time.setTextSize(26f * dp);
            ampm.setColor(0xFFFFFFFF);
            ampm.setTypeface(bold);
            ampm.setTextSize(13f * dp);
            bt.setColor(0xFFE6E9EC);
            bt.setStyle(Paint.Style.STROKE);
            bt.setStrokeWidth(1.8f * dp);
            bt.setStrokeJoin(Paint.Join.ROUND);
            bt.setStrokeCap(Paint.Cap.ROUND);
        }

        @Override protected void onDraw(Canvas c) {
            Theme th = Theme.current;
            handle.setColor(th.handle);
            date.setColor(th.text);
            time.setColor(th.text);
            ampm.setColor(th.text);
            bt.setColor(th.text);
            float w = getWidth();
            // No pull handle: there is no pull-down panel, and no other ccNC screen shows one.
            if (clockAlpha <= 0f) return; // the welcome page already shows the time and date large
            int ca = Math.round(255 * clockAlpha);
            date.setAlpha(ca); time.setAlpha(ca); ampm.setAlpha(ca); bt.setAlpha(ca);

            Date now = new Date();
            Locale loc = Locale.getDefault();
            String d = new SimpleDateFormat("EEE, MMM", loc).format(now) + ". " + new SimpleDateFormat("d", loc).format(now);
            float base = 44f * dp;
            c.drawText(d, w / 2, base, date);

            boolean h24 = DateFormat.is24HourFormat(getContext());
            String t = new SimpleDateFormat(h24 ? "H:mm" : "h:mm", loc).format(now);
            String ap = h24 ? "" : new SimpleDateFormat("a", Locale.US).format(now).toUpperCase(Locale.US);
            float right = w - 40f * dp;
            float apW = ap.isEmpty() ? 0 : ampm.measureText(ap) + 3f * dp;
            float tW = time.measureText(t);
            c.drawText(ap, right - apW + 3f * dp, base, ampm);
            c.drawText(t, right - apW - tW, base, time);

            BluetoothAdapter a = BluetoothAdapter.getDefaultAdapter();
            if (a != null && a.isEnabled()) drawBt(c, right - apW - tW - 22f * dp, base - 9f * dp, 9f * dp);
        }

        private void drawBt(Canvas c, float cx, float cy, float s) {
            Path p = new Path();
            p.moveTo(cx - s * 0.55f, cy - s * 0.45f);
            p.lineTo(cx + s * 0.5f, cy + s * 0.45f);
            p.lineTo(cx, cy + s);
            p.lineTo(cx, cy - s);
            p.lineTo(cx + s * 0.5f, cy - s * 0.45f);
            p.lineTo(cx - s * 0.55f, cy + s * 0.45f);
            c.drawPath(p, bt);
        }
    }

    /** ccNC page dashes: the current page is a longer, brighter bar. */
    static final class PageIndicator extends View {
        private final float dp;
        private final Paint on = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint off = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int count = 1;
        private float position;

        PageIndicator(Context c) {
            super(c);
            dp = c.getResources().getDisplayMetrics().density;
            on.setColor(0xFFFFFFFF);
            off.setColor(0x66FFFFFF);
        }

        void setCount(int n) { count = Math.max(1, n); invalidate(); }

        void setPosition(float p) { position = p; invalidate(); }

        @Override protected void onDraw(Canvas c) {
            if (count < 2) return;
            on.setColor(Theme.current.dashOn);
            off.setColor(Theme.current.dashOff);
            float dash = 20f * dp, gap = 8f * dp, th = 3f * dp;
            float total = count * dash + (count - 1) * gap;
            float x = (getWidth() - total) / 2f, y = getHeight() / 2f;
            for (int i = 0; i < count; i++) {
                c.drawRoundRect(new RectF(x, y - th / 2, x + dash, y + th / 2), th / 2, th / 2, off);
                float k = Math.max(0f, 1f - Math.abs(position - i));
                if (k > 0) {
                    on.setAlpha((int) (255 * k));
                    c.drawRoundRect(new RectF(x, y - th / 2, x + dash, y + th / 2), th / 2, th / 2, on);
                }
                x += dash + gap;
            }
        }
    }

    /**
     * Edit mode's page chips: one numbered chip per page of cards. Tap one to go to that page;
     * drop a dragged card on one to move the card there.
     */
    static final class PageTray extends View {
        interface Listener { void onChip(int page); }

        private final float dp;
        private final Listener listener;
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint num = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private int count = 1, current = -1, hover = -1, down = -1;

        PageTray(Context c, Typeface bold, Listener l) {
            super(c);
            listener = l;
            dp = c.getResources().getDisplayMetrics().density;
            num.setTypeface(bold);
            num.setTextAlign(Paint.Align.CENTER);
            line.setStyle(Paint.Style.STROKE);
            setClickable(true);
        }

        int count() { return count; }

        void set(int count, int current) {
            if (count != this.count) { this.count = Math.max(1, count); requestLayout(); }
            this.current = current;
            invalidate();
        }

        void setHover(int h) { if (h != hover) { hover = h; invalidate(); } }

        private float chipW() { return 54f * dp; }
        private float gap() { return 10f * dp; }

        @Override protected void onMeasure(int w, int h) {
            setMeasuredDimension((int) (count * chipW() + (count - 1) * gap()), (int) (42f * dp));
        }

        /** Chip under a screen point, or -1. */
        int chipAt(float rawX, float rawY) {
            int[] o = new int[2];
            getLocationOnScreen(o);
            float x = rawX - o[0], y = rawY - o[1];
            if (y < -8f * dp || y > getHeight() + 8f * dp) return -1;
            for (int i = 0; i < count; i++) {
                float l = i * (chipW() + gap());
                if (x >= l - gap() / 2f && x <= l + chipW() + gap() / 2f) return i;
            }
            return -1;
        }

        @Override public boolean onTouchEvent(android.view.MotionEvent e) {
            int hit = chipAt(e.getRawX(), e.getRawY());
            switch (e.getActionMasked()) {
                case android.view.MotionEvent.ACTION_DOWN: down = hit; return true;
                case android.view.MotionEvent.ACTION_UP:
                    if (down >= 0 && hit == down) listener.onChip(down);
                    down = -1;
                    return true;
                case android.view.MotionEvent.ACTION_CANCEL: down = -1; return true;
                default: return true;
            }
        }

        @Override protected void onDraw(Canvas c) {
            Theme t = Theme.current;
            float rad = 12f * dp;
            for (int i = 0; i < count; i++) {
                float l = i * (chipW() + gap());
                r.set(l, 0, l + chipW(), getHeight());
                boolean cur = i == current, hov = i == hover;
                // Light themes: white chips with dark ink and a dark hairline (white-on-white otherwise).
                fill.setColor(hov ? (t.accent() & 0x00FFFFFF) | 0x66000000 : cur ? (t.accent() & 0x00FFFFFF) | 0xCC000000
                        : t.light ? 0xB3FFFFFF : 0x33FFFFFF);
                c.drawRoundRect(r, rad, rad, fill);
                line.setStrokeWidth(Math.max(1f, hov ? 2.5f * dp : 1f * dp));
                line.setColor(hov ? t.accent() : t.light ? 0x4D203040 : 0x55FFFFFF);
                c.drawRoundRect(r, rad, rad, line);
                num.setTextSize(17f * dp);
                num.setColor(cur ? 0xFF10161C : t.light ? t.text : 0xFFFFFFFF);
                c.drawText(String.valueOf(i + 1), r.centerX(), r.centerY() + 6f * dp, num);
            }
        }
    }
}
