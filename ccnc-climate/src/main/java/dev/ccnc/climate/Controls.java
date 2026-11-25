package dev.ccnc.climate;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import dev.ccnc.common.Fonts;
import dev.ccnc.common.Palette;

/** Painted controls for the ccNC climate panel. */
final class Controls {

    private Controls() {}

    public interface Tap { void onTap(); }

    static TextView label(Context c, String text, float sizeSp, int color, int style) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextSize(sizeSp);
        t.setTextColor(color);
        t.setTypeface(Fonts.get(c, style));
        return t;
    }

    /** Rounded pill toggle with an icon drawn by {@link Icon#draw}. */
    static final class Pill extends View {
        interface Icon { void draw(Canvas c, float cx, float cy, float r, Paint p); }

        private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fg = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        final String caption;
        final Icon icon;
        boolean on;
        private Tap tap;

        Pill(Context c, String caption, Icon icon) {
            super(c);
            this.caption = caption;
            this.icon = icon;
            setOnClickListener(new OnClickListener() {
                @Override public void onClick(View v) { if (tap != null) tap.onTap(); }
            });
        }

        void setOnTap(Tap t) { tap = t; }
        void setOn(boolean v) { on = v; invalidate(); }

        @Override protected void onMeasure(int w, int h) {
            setMeasuredDimension(resolveSize(Palette.px(getContext(), 96), w),
                    resolveSize(Palette.px(getContext(), 56), h));
        }

        @Override protected void onDraw(Canvas c) {
            float r = Palette.px(getContext(), 14);
            rect.set(0, 0, getWidth(), getHeight());
            bg.setColor(on ? Palette.ACCENT_DIM : Palette.CARD);
            c.drawRoundRect(rect, r, r, bg);
            bg.setStyle(Paint.Style.STROKE);
            bg.setStrokeWidth(Palette.px(getContext(), 1.5f));
            bg.setColor(on ? Palette.ACCENT : Palette.BORDER);
            c.drawRoundRect(rect, r, r, bg);
            bg.setStyle(Paint.Style.FILL);

            float pad = Palette.px(getContext(), 12);
            fg.setColor(on ? Palette.TEXT : Palette.SUBTEXT);
            fg.setStyle(Paint.Style.STROKE);
            fg.setStrokeWidth(Palette.px(getContext(), 2f));
            fg.setStrokeCap(Paint.Cap.ROUND);
            fg.setStrokeJoin(Paint.Join.ROUND);
            if (icon != null) {
                float s = getHeight() - pad * 2;
                icon.draw(c, pad + s / 2f, getHeight() / 2f, s / 2f, fg);
            }
            Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
            text.setTypeface(Fonts.get(getContext(), on ? Fonts.SEMIBOLD : Fonts.REGULAR));
            text.setTextSize(Palette.px(getContext(), 15));
            text.setColor(on ? Palette.TEXT : Palette.SUBTEXT);
            float tx = icon != null ? pad * 2 + (getHeight() - pad * 2) : pad * 1.5f;
            c.drawText(caption, tx, getHeight() / 2f + text.getTextSize() * 0.36f, text);
        }
    }

    /** Vertical seat heat / vent stepper: 3 bars, tap cycles 0..3. */
    static final class Seat extends View {
        enum Kind { HEAT, VENT }
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Kind kind;
        int level;
        private Tap tap;

        private final Paint cap = new Paint(Paint.ANTI_ALIAS_FLAG);

        Seat(Context c, Kind kind) {
            super(c);
            this.kind = kind;
            cap.setColor(Palette.HINT);
            cap.setTextSize(Palette.px(c, 12));
            cap.setTextAlign(Paint.Align.CENTER);
            cap.setTypeface(Fonts.get(c, Fonts.REGULAR));
            setOnClickListener(new OnClickListener() {
                @Override public void onClick(View v) { if (tap != null) tap.onTap(); }
            });
        }

        void setOnTap(Tap t) { tap = t; }
        void setLevel(int v) { level = v; invalidate(); }

        @Override protected void onMeasure(int w, int h) {
            setMeasuredDimension(resolveSize(Palette.px(getContext(), 64), w), resolveSize(Palette.px(getContext(), 78), h));
        }

        @Override protected void onDraw(Canvas c) {
            int w = getWidth(), h = getHeight();
            c.drawText(kind == Kind.HEAT ? "HEAT" : "VENT", w / 2f, h - Palette.px(getContext(), 2), cap);
            h -= Palette.px(getContext(), 8);
            float bw = w / 3.4f, gap = (w - 3 * bw) / 2f;
            for (int i = 0; i < 3; i++) {
                boolean lit = i < level;
                p.setColor(lit ? (kind == Kind.HEAT ? 0xFFE08A5B : Palette.ACCENT) : Palette.alpha(Palette.OFF, 0x59));
                float bh = Palette.px(getContext(), 8);
                float top = h - Palette.px(getContext(), 18) - (i + 1) * bh - i * Palette.px(getContext(), 3);
                RectF r = new RectF(i * (bw + gap), top, i * (bw + gap) + bw, top + bh);
                c.drawRoundRect(r, bh / 2, bh / 2, p);
            }
        }
    }

    /** Fan speed: 8 segments in a row; tap a segment to set, AUTO handled separately. */
    static final class FanBar extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        int level; // 0..7
        private Tap tap;

        FanBar(Context c) {
            super(c);
            setOnClickListener(new OnClickListener() {
                @Override public void onClick(View v) { if (tap != null) tap.onTap(); }
            });
        }

        void setOnTap(Tap t) { tap = t; }
        void setLevel(int v) { level = v; invalidate(); }

        @Override protected void onMeasure(int w, int h) {
            setMeasuredDimension(resolveSize(Palette.px(getContext(), 280), w),
                    resolveSize(Palette.px(getContext(), 40), h));
        }

        @Override protected void onDraw(Canvas c) {
            int n = 8;
            float gap = Palette.px(getContext(), 5);
            float seg = (getWidth() - (n - 1) * gap) / n;
            for (int i = 0; i < n; i++) {
                p.setColor(i < level ? Palette.ACCENT : Palette.alpha(Palette.OFF, 0x59));
                float h = getHeight() * (0.45f + 0.55f * (i + 1) / n);
                RectF r = new RectF(i * (seg + gap), getHeight() - h, i * (seg + gap) + seg, getHeight());
                c.drawRoundRect(r, seg / 3f, seg / 3f, p);
            }
        }

        /** Segment index for an x coordinate, for tap-to-set. */
        int segmentAt(float x) {
            int n = 8;
            float gap = Palette.px(getContext(), 5);
            float seg = (getWidth() - (n - 1) * gap) / n;
            int idx = Math.round(x / (seg + gap));
            return Math.max(0, Math.min(n - 1, idx)) + 1;
        }
    }

    /** Big temperature stepper: − value + with LO/HI at the ends. */
    static final class TempStepper extends LinearLayout {
        interface OnTemp { void onTemp(float celsius); }

        private final TextView value;
        private float celsius;
        private boolean celsiusUnit = true;
        private OnTemp cb;

        TempStepper(Context c) {
            super(c);
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);

            value = new TextView(c);
            value.setTextColor(Palette.TEXT);
            value.setTextSize(52f);
            value.setTypeface(Fonts.get(c, Fonts.SEMIBOLD)); // the numeric subset has no degree sign or LO/HI
            value.setGravity(Gravity.CENTER);
            addView(value, new LayoutParams(0, -2, 1f));

            LinearLayout buttons = new LinearLayout(c);
            buttons.setOrientation(VERTICAL);
            addView(buttons, new LayoutParams(Palette.px(c, 48), -2));

            android.widget.Button minus = stepButton(c, "\u2212");
            android.widget.Button plus = stepButton(c, "+");
            buttons.addView(minus);
            buttons.addView(plus);

            minus.setOnClickListener(new OnClickListener() {
                @Override public void onClick(View v) { step(-0.5f); }
            });
            plus.setOnClickListener(new OnClickListener() {
                @Override public void onClick(View v) { step(0.5f); }
            });
        }

        private static android.widget.Button stepButton(Context c, String glyph) {
            android.widget.Button b = new android.widget.Button(c);
            b.setText(glyph);
            b.setTextSize(22f);
            b.setTypeface(Fonts.get(c, Fonts.SEMIBOLD));
            b.setTextColor(Palette.TEXT);
            b.setBackgroundColor(Palette.CARD);
            b.setMinHeight(0);
            b.setMinimumHeight(0);
            b.setPadding(0, 0, 0, 0);
            return b;
        }

        void setOnTemp(OnTemp cb) { this.cb = cb; }

        void setTemp(float celsius, boolean celsiusUnit) {
            this.celsius = celsius;
            this.celsiusUnit = celsiusUnit;
            render();
        }

        private void step(float d) {
            float t = Math.max(15.5f, Math.min(29f, celsius + d));
            if (cb != null) cb.onTemp(t);
        }

        private void render() {
            if (celsius <= 15.5f) value.setText("LO");
            else if (celsius >= 29f) value.setText("HI");
            else if (celsiusUnit) value.setText(String.format(java.util.Locale.US, "%.1f\u00b0", celsius));
            else value.setText(String.format(java.util.Locale.US, "%d\u00b0", Math.round(celsius * 9f / 5f + 32f)));
        }
    }

    /** Path icons shared by the pills. */
    static final class Icon {
        private Icon() {}

        static final Pill.Icon AC = new Pill.Icon() {
            @Override public void draw(Canvas c, float cx, float cy, float r, Paint p) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(r * 0.22f);
                p.setStrokeCap(Paint.Cap.ROUND);
                for (int i = 0; i < 3; i++) {
                    double a = -Math.PI / 2 + i * 2 * Math.PI / 3;
                    c.drawLine(cx, cy, cx + (float) (r * 0.85 * Math.cos(a)), cy + (float) (r * 0.85 * Math.sin(a)), p);
                }
            }
        };

        static final Pill.Icon RECIRC = new Pill.Icon() {
            @Override public void draw(Canvas c, float cx, float cy, float r, Paint p) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(r * 0.2f);
                p.setStrokeCap(Paint.Cap.ROUND);
                RectF a = new RectF(cx - r * 0.8f, cy - r * 0.8f, cx + r * 0.8f, cy + r * 0.8f);
                c.drawArc(a, 40, 280, false, p);
                float end = (float) Math.toRadians(40);
                float ex = cx + (float) (r * 0.8 * Math.cos(end)), ey = cy + (float) (r * 0.8 * Math.sin(end));
                Path arrow = new Path();
                arrow.moveTo(ex - r * 0.4f, ey - r * 0.35f);
                arrow.lineTo(ex, ey);
                arrow.lineTo(ex + r * 0.35f, ey - r * 0.4f);
                c.drawPath(arrow, p);
            }
        };

        static final Pill.Icon DEFROST_FRONT = new Pill.Icon() {
            @Override public void draw(Canvas c, float cx, float cy, float r, Paint p) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(r * 0.2f);
                p.setStrokeCap(Paint.Cap.ROUND);
                RectF win = new RectF(cx - r * 0.85f, cy - 0.4f * r, cx + r * 0.85f, cy + r * 0.75f);
                c.drawRoundRect(win, r * 0.2f, r * 0.2f, p);
                for (int i = -1; i <= 1; i++) {
                    float x = cx + i * r * 0.45f;
                    c.drawLine(x, cy - r * 0.75f, x, cy - r * 0.2f, p);
                }
            }
        };

        static final Pill.Icon DEFROST_REAR = new Pill.Icon() {
            @Override public void draw(Canvas c, float cx, float cy, float r, Paint p) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(r * 0.2f);
                p.setStrokeCap(Paint.Cap.ROUND);
                RectF win = new RectF(cx - r * 0.85f, cy - r * 0.75f, cx + r * 0.85f, cy + 0.4f * r);
                c.drawRoundRect(win, r * 0.2f, r * 0.2f, p);
                for (int i = -1; i <= 1; i++) {
                    float x = cx + i * r * 0.45f;
                    c.drawLine(x, cy + r * 0.2f, x, cy + r * 0.75f, p);
                }
            }
        };

        static final Pill.Icon AUTO = new Pill.Icon() {
            @Override public void draw(Canvas c, float cx, float cy, float r, Paint p) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(r * 0.2f);
                c.drawCircle(cx, cy, r * 0.75f, p);
            }
        };

        static final Pill.Icon POWER = new Pill.Icon() {
            @Override public void draw(Canvas c, float cx, float cy, float r, Paint p) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(r * 0.22f);
                p.setStrokeCap(Paint.Cap.ROUND);
                RectF a = new RectF(cx - r * 0.7f, cy - r * 0.7f, cx + r * 0.7f, cy + r * 0.7f);
                c.drawArc(a, -60, 300, false, p);
                c.drawLine(cx, cy - r * 0.85f, cx, cy, p);
            }
        };
    }
}
