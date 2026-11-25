package dev.ccnc.home;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Region;

/**
 * Line-art card icons in the ccNC style, drawn straight in pixel coordinates.
 * (No canvas scaling: KitKat's HWUI caches paths as textures at their local size,
 * so scaled paths come out blurry.)
 */
final class Icons {

    enum Kind {
        PROJECTION, PHONE, VOICE_MEMO, WEATHER, SETTINGS,
        NAVIGATION, RADIO, MEDIA, CLIMATE, EV, PROFILE, APP
    }

    private Icons() {}

    static int color(Kind k) {
        switch (k) {
            case PROJECTION: return 0xFFFFFFFF;
            case PHONE:      return 0xFF7DE3CF;
            case VOICE_MEMO: return 0xFFF0C85A;
            case WEATHER:    return 0xFF9EDCF5;
            case SETTINGS:   return 0xFFC9CED3;
            case NAVIGATION: return 0xFF8FD0FF;
            case RADIO:      return 0xFFF0A35E;
            case MEDIA:      return 0xFFE3A6F0;
            case CLIMATE:    return 0xFF8FE3F0;
            case EV:         return 0xFF9BE58F;
            case PROFILE:    return 0xFFC9CED3;
            default:         return 0xFFFFFFFF;
        }
    }

    /** Maps unit-box coordinates (0..1) to pixels. */
    private static final class U {
        final float ox, oy, s;
        U(float cx, float cy, float size) { ox = cx - size / 2f; oy = cy - size / 2f; s = size; }
        float x(float u) { return ox + u * s; }
        float y(float u) { return oy + u * s; }
        float d(float u) { return u * s; }
        RectF oval(float cx, float cy, float r) { return new RectF(x(cx - r), y(cy - r), x(cx + r), y(cy + r)); }
        Path poly(float... p) {
            Path path = new Path();
            path.moveTo(x(p[0]), y(p[1]));
            for (int i = 2; i < p.length; i += 2) path.lineTo(x(p[i]), y(p[i + 1]));
            path.close();
            return path;
        }
    }

    static void draw(Canvas c, Kind k, float cx, float cy, float size, int color) {
        U u = new U(cx, cy, size);
        Paint st = new Paint(Paint.ANTI_ALIAS_FLAG);
        st.setStyle(Paint.Style.STROKE);
        st.setStrokeWidth(Math.max(2f, size * 0.045f));
        st.setStrokeCap(Paint.Cap.ROUND);
        st.setStrokeJoin(Paint.Join.ROUND);
        st.setColor(color);
        Paint fl = new Paint(Paint.ANTI_ALIAS_FLAG);
        fl.setStyle(Paint.Style.FILL);
        fl.setColor(color);

        switch (k) {
            case PROJECTION: projection(c, u, fl); break;
            case PHONE: phone(c, u, st); break;
            case VOICE_MEMO: voiceMemo(c, u, st); break;
            case WEATHER: weather(c, u, st); break;
            case SETTINGS: gear(c, u, st); break;
            case NAVIGATION: c.drawPath(u.poly(.5f, .12f, .80f, .86f, .5f, .70f, .20f, .86f), st); break;
            case RADIO: radio(c, u, st, fl); break;
            case MEDIA: media(c, u, st, fl); break;
            case CLIMATE: fan(c, u, st, cx, cy); break;
            case EV: c.drawPath(u.poly(.58f, .08f, .26f, .56f, .48f, .56f, .42f, .92f, .76f, .42f, .53f, .42f), st); break;
            case PROFILE:
                c.drawCircle(u.x(.5f), u.y(.34f), u.d(.15f), st);
                c.drawArc(new RectF(u.x(.22f), u.y(.58f), u.x(.78f), u.y(1.1f)), 180, 180, false, st);
                break;
            default: break;
        }
    }

    private static void projection(Canvas c, U u, Paint fl) {
        int base = fl.getColor();
        c.drawPath(u.poly(.5f, .08f, .84f, .66f, .5f, .50f, .16f, .66f), fl);
        fl.setColor((base & 0x00FFFFFF) | 0xC0000000);
        c.drawPath(u.poly(.5f, .58f, .78f, .94f, .5f, .82f, .22f, .94f), fl);
        fl.setColor(base);
    }

    private static void phone(Canvas c, U u, Paint st) {
        Path p = new Path();
        p.moveTo(u.x(.28f), u.y(.20f));
        p.lineTo(u.x(.37f), u.y(.20f));
        p.lineTo(u.x(.44f), u.y(.36f));
        p.lineTo(u.x(.37f), u.y(.43f));
        p.cubicTo(u.x(.43f), u.y(.56f), u.x(.50f), u.y(.63f), u.x(.60f), u.y(.69f));
        p.lineTo(u.x(.67f), u.y(.62f));
        p.lineTo(u.x(.82f), u.y(.69f));
        p.lineTo(u.x(.82f), u.y(.79f));
        p.cubicTo(u.x(.82f), u.y(.86f), u.x(.77f), u.y(.90f), u.x(.70f), u.y(.89f));
        p.cubicTo(u.x(.45f), u.y(.86f), u.x(.19f), u.y(.60f), u.x(.17f), u.y(.32f));
        p.cubicTo(u.x(.16f), u.y(.25f), u.x(.21f), u.y(.20f), u.x(.28f), u.y(.20f));
        p.close();
        c.drawPath(p, st);
        c.drawArc(u.oval(.56f, .44f, .16f), -80, 70, false, st);
        c.drawArc(u.oval(.56f, .44f, .30f), -80, 70, false, st);
    }

    private static void voiceMemo(Canvas c, U u, Paint st) {
        c.drawRect(u.x(.22f), u.y(.18f), u.x(.78f), u.y(.94f), st);
        c.drawRect(u.x(.38f), u.y(.10f), u.x(.62f), u.y(.22f), st);
        RectF cap = new RectF(u.x(.43f), u.y(.33f), u.x(.57f), u.y(.60f));
        c.drawRoundRect(cap, u.d(.07f), u.d(.07f), st);
        c.drawArc(u.oval(.5f, .52f, .13f), 0, 180, false, st);
        c.drawLine(u.x(.5f), u.y(.65f), u.x(.5f), u.y(.76f), st);
        c.drawLine(u.x(.41f), u.y(.76f), u.x(.59f), u.y(.76f), st);
    }

    private static Path cloud(U u) {
        Path p = new Path();
        p.moveTo(u.x(.22f), u.y(.74f));
        p.arcTo(u.oval(.27f, .64f, .10f), 125, 160, false);
        p.arcTo(u.oval(.45f, .53f, .15f), 200, 145, false);
        p.arcTo(u.oval(.64f, .63f, .11f), 260, 190, false);
        p.close();
        return p;
    }

    private static void weather(Canvas c, U u, Paint st) {
        Path cl = cloud(u);
        c.save();
        c.clipPath(cloudHalo(u), Region.Op.DIFFERENCE);
        c.drawCircle(u.x(.66f), u.y(.36f), u.d(.11f), st);
        for (int a = -165; a <= 45; a += 35) {
            double r = Math.toRadians(a);
            float cs = (float) Math.cos(r), sn = (float) Math.sin(r);
            c.drawLine(u.x(.66f + .16f * cs), u.y(.36f + .16f * sn), u.x(.66f + .22f * cs), u.y(.36f + .22f * sn), st);
        }
        c.restore();
        c.drawPath(cl, st);
    }

    /** Cloud area grown a little so the sun stops short of the outline. */
    private static Path cloudHalo(U u) {
        Path p = new Path();
        p.addCircle(u.x(.27f), u.y(.64f), u.d(.14f), Path.Direction.CW);
        p.addCircle(u.x(.45f), u.y(.53f), u.d(.19f), Path.Direction.CW);
        p.addCircle(u.x(.64f), u.y(.63f), u.d(.15f), Path.Direction.CW);
        p.addRect(u.x(.18f), u.y(.60f), u.x(.78f), u.y(.80f), Path.Direction.CW);
        return p;
    }

    private static void gear(Canvas c, U u, Paint st) {
        int teeth = 8;
        float outer = .40f, root = .31f;
        double step = 2 * Math.PI / teeth;
        double half = Math.toRadians(10);
        Path p = new Path();
        for (int i = 0; i < teeth; i++) {
            double a = i * step - Math.PI / 2;
            double a0 = a - half * 1.35, a1 = a - half, a2 = a + half, a3 = a + half * 1.35;
            pt(p, u, root, a0, i == 0);
            pt(p, u, outer, a1, false);
            pt(p, u, outer, a2, false);
            pt(p, u, root, a3, false);
            double next = (i + 1) * step - Math.PI / 2 - half * 1.35;
            for (int s = 1; s <= 4; s++) pt(p, u, root, a3 + (next - a3) * s / 4.0, false);
        }
        p.close();
        c.drawPath(p, st);
        c.drawCircle(u.x(.5f), u.y(.5f), u.d(.13f), st);
    }

    private static void pt(Path p, U u, float r, double a, boolean move) {
        float x = u.x(.5f + r * (float) Math.cos(a)), y = u.y(.5f + r * (float) Math.sin(a));
        if (move) p.moveTo(x, y); else p.lineTo(x, y);
    }

    private static void radio(Canvas c, U u, Paint st, Paint fl) {
        c.drawCircle(u.x(.5f), u.y(.5f), u.d(.06f), fl);
        c.drawArc(u.oval(.5f, .5f, .18f), -45, 90, false, st);
        c.drawArc(u.oval(.5f, .5f, .32f), -45, 90, false, st);
        c.drawArc(u.oval(.5f, .5f, .18f), 135, 90, false, st);
        c.drawArc(u.oval(.5f, .5f, .32f), 135, 90, false, st);
    }

    private static void media(Canvas c, U u, Paint st, Paint fl) {
        c.drawOval(new RectF(u.x(.24f), u.y(.64f), u.x(.46f), u.y(.82f)), fl);
        c.drawOval(new RectF(u.x(.58f), u.y(.56f), u.x(.80f), u.y(.74f)), fl);
        c.drawLine(u.x(.45f), u.y(.72f), u.x(.45f), u.y(.24f), st);
        c.drawLine(u.x(.79f), u.y(.64f), u.x(.79f), u.y(.16f), st);
        c.drawPath(u.poly(.45f, .22f, .79f, .14f, .79f, .26f, .45f, .34f), fl);
    }

    private static void fan(Canvas c, U u, Paint st, float cx, float cy) {
        for (int i = 0; i < 4; i++) {
            c.save();
            c.rotate(90 * i, cx, cy);
            Path p = new Path();
            p.moveTo(u.x(.5f), u.y(.5f));
            p.quadTo(u.x(.40f), u.y(.26f), u.x(.52f), u.y(.12f));
            p.quadTo(u.x(.70f), u.y(.26f), u.x(.5f), u.y(.5f));
            c.drawPath(p, st);
            c.restore();
        }
        c.drawCircle(cx, cy, u.d(.05f), st);
    }
}
