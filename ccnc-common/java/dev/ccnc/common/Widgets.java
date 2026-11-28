package dev.ccnc.common;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/** Small painted widgets shared by the ccNC-style apps (switches, chevrons, glyphs). */
public final class Widgets {

    private Widgets() {}

    /** ccNC pill switch. Toggles via performClick; reports changes to the listener. */
    public static final class Switch extends View {
        public interface OnChanged { void onChanged(boolean on); }

        private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint thumb = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final int pad;
        private boolean on;
        private OnChanged listener;

        public Switch(Context c) {
            super(c);
            pad = Palette.px(c, 3);
            setOnClickListener(new OnClickListener() {
                @Override public void onClick(View v) { setOn(!on); }
            });
        }

        public void setOnChanged(OnChanged l) { listener = l; }

        public boolean isOn() { return on; }

        /** Sets state from an external source without notifying the listener. */
        public void setOnQuiet(boolean value) {
            if (on == value) return;
            on = value;
            invalidate();
        }

        public void setOn(boolean value) {
            if (on == value) return;
            on = value;
            if (listener != null) listener.onChanged(on);
            invalidate();
        }

        @Override protected void onMeasure(int w, int h) {
            int width = Palette.px(getContext(), 46);
            int height = Palette.px(getContext(), 26);
            setMeasuredDimension(resolveSize(width, w), resolveSize(height, h));
        }

        @Override protected void onDraw(Canvas c) {
            int w = getWidth(), h = getHeight();
            rect.set(0, 0, w, h);
            // Light themes: a darker off track and a white thumb (the pale thumb vanishes on a light card).
            track.setColor(on ? Palette.ACCENT_DIM : Palette.alpha(Palette.OFF, Palette.LIGHT ? 0x8C : 0x66));
            c.drawRoundRect(rect, h / 2f, h / 2f, track);
            int s = h - pad * 2;
            int left = on ? w - pad - s : pad;
            thumb.setColor(on ? Palette.ACCENT : Palette.LIGHT ? 0xFFFFFFFF : 0xFFDDE3E8);
            rect.set(left, pad, left + s, pad + s);
            c.drawOval(rect, thumb);
        }
    }

    /** Right-pointing disclosure chevron drawn with strokes. */
    public static final class Chevron extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        public Chevron(Context c) {
            super(c);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Palette.px(c, 2));
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setColor(Palette.HINT);
        }

        @Override protected void onMeasure(int w, int h) {
            int s = Palette.px(getContext(), 8);
            setMeasuredDimension(resolveSize(s, w), resolveSize(s * 2, h));
        }

        @Override protected void onDraw(Canvas c) {
            int w = getWidth(), h = getHeight();
            Path path = new Path();
            path.moveTo(w * 0.25f, h * 0.2f);
            path.lineTo(w * 0.75f, h * 0.5f);
            path.lineTo(w * 0.25f, h * 0.8f);
            c.drawPath(path, p);
        }
    }

    /** Simple vector-ish glyphs for the category rail, drawn from paths. */
    public static final class Glyph extends View {

        public static final int VEHICLE = 0, DISPLAY = 1, SOUND = 2, BLUETOOTH = 3,
                WIFI = 4, GENERAL = 5, APPEARANCE = 6, SYSTEM = 7, LINK = 8, REAR = 9;

        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int kind;
        private int color = Palette.SUBTEXT;
        private boolean active;

        public Glyph(Context c, int kind) {
            super(c);
            this.kind = kind;
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Palette.px(c, 1.6f));
            p.setStrokeCap(Paint.Cap.ROUND);
            fill.setStyle(Paint.Style.FILL);
        }

        public void setActive(boolean a) {
            active = a;
            color = a ? Palette.ACCENT : Palette.SUBTEXT;
            invalidate();
        }

        @Override protected void onMeasure(int w, int h) {
            int s = Palette.px(getContext(), 24);
            setMeasuredDimension(resolveSize(s, w), resolveSize(s, h));
        }

        @Override protected void onDraw(Canvas c) {
            int w = getWidth(), h = getHeight();
            float u = w / 24f; // design grid: 24 units
            p.setColor(color);
            fill.setColor(color);
            RectF r = new RectF();
            Path path = new Path();
            switch (kind) {
                case VEHICLE: // car silhouette
                    path.moveTo(3 * u, 13 * u); path.lineTo(6 * u, 13 * u);
                    path.quadTo(8 * u, 7 * u, 12 * u, 7 * u);
                    path.quadTo(16 * u, 7 * u, 18 * u, 13 * u);
                    path.lineTo(21 * u, 13 * u);
                    path.lineTo(21 * u, 17 * u); path.lineTo(19 * u, 17 * u);
                    path.lineTo(19 * u, 19 * u); path.lineTo(16.5f * u, 19 * u);
                    path.lineTo(16.5f * u, 17 * u); path.lineTo(7.5f * u, 17 * u);
                    path.lineTo(7.5f * u, 19 * u); path.lineTo(5 * u, 19 * u);
                    path.lineTo(5 * u, 17 * u); path.lineTo(3 * u, 17 * u);
                    path.close();
                    c.drawPath(path, fill);
                    break;
                case DISPLAY: // rectangle with stand
                    r.set(4 * u, 5 * u, 20 * u, 16 * u);
                    c.drawRoundRect(r, 1.5f * u, 1.5f * u, p);
                    c.drawLine(12 * u, 19 * u, 12 * u, 21 * u, p);
                    c.drawLine(8 * u, 21 * u, 16 * u, 21 * u, p);
                    break;
                case SOUND: // speaker cone
                    path.moveTo(4 * u, 10 * u); path.lineTo(8 * u, 10 * u);
                    path.lineTo(13 * u, 5 * u); path.lineTo(13 * u, 19 * u);
                    path.lineTo(8 * u, 14 * u); path.lineTo(4 * u, 14 * u);
                    path.close();
                    c.drawPath(path, p);
                    c.drawArc(new RectF(13 * u, 8 * u, 19 * u, 16 * u), -60, 120, false, p);
                    c.drawArc(new RectF(13 * u, 5.5f * u, 22 * u, 18.5f * u), -55, 110, false, p);
                    break;
                case BLUETOOTH: // rune
                    path.moveTo(7 * u, 8 * u); path.lineTo(17 * u, 16 * u);
                    path.lineTo(12 * u, 19.5f * u); path.lineTo(12 * u, 4.5f * u);
                    path.lineTo(17 * u, 8 * u); path.lineTo(7 * u, 16 * u);
                    c.drawPath(path, p);
                    break;
                case WIFI:
                    c.drawArc(new RectF(4 * u, 9 * u, 20 * u, 25 * u), -135, 90, false, p);
                    c.drawArc(new RectF(7.5f * u, 12.5f * u, 16.5f * u, 21.5f * u), -135, 90, false, p);
                    c.drawArc(new RectF(11 * u, 16 * u, 13 * u, 18 * u), 0, 360, false, p);
                    break;
                case GENERAL: // gear-ish circle with spokes
                    c.drawCircle(12 * u, 12 * u, 5 * u, p);
                    for (int i = 0; i < 6; i++) {
                        double a = Math.PI / 3 * i;
                        c.drawLine((float) (12 + 6.4 * Math.cos(a)) * u, (float) (12 + 6.4 * Math.sin(a)) * u,
                                (float) (12 + 8.6 * Math.cos(a)) * u, (float) (12 + 8.6 * Math.sin(a)) * u, p);
                    }
                    break;
                case APPEARANCE: // paint brush
                    path.moveTo(15 * u, 4 * u); path.lineTo(20 * u, 9 * u);
                    path.lineTo(11 * u, 18 * u); path.lineTo(6 * u, 18 * u);
                    path.lineTo(6 * u, 13 * u); path.close();
                    c.drawPath(path, p);
                    c.drawLine(4 * u, 22 * u, 9 * u, 17 * u, p);
                    break;
                case SYSTEM: // sliders
                    for (int row = 0; row < 3; row++) {
                        float y = (7 + row * 5) * u;
                        c.drawLine(4 * u, y, 20 * u, y, p);
                        float cx = (row == 0 ? 14 : row == 1 ? 8 : 17) * u;
                        c.drawCircle(cx, y, 2 * u, fill);
                    }
                    break;
                case LINK: // chain / bluelink: globe with orbit
                    c.drawCircle(12 * u, 12 * u, 7 * u, p);
                    c.drawLine(5 * u, 12 * u, 19 * u, 12 * u, p);
                    c.drawArc(new RectF(8 * u, 5 * u, 16 * u, 19 * u), 0, 360, false, p);
                    break;
                case REAR: // seat profile
                    path.moveTo(7 * u, 4 * u); path.lineTo(11 * u, 4 * u);
                    path.lineTo(11 * u, 12 * u); path.lineTo(17 * u, 13 * u);
                    path.quadTo(19 * u, 13.5f * u, 19 * u, 15.5f * u);
                    path.lineTo(19 * u, 20 * u); path.lineTo(16 * u, 20 * u);
                    path.lineTo(15 * u, 16 * u); path.lineTo(7 * u, 15 * u);
                    path.close();
                    c.drawPath(path, p);
                    break;
            }
        }
    }
}
