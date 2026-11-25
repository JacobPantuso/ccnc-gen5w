package dev.ccnc.home;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;

/** One ccNC home card: frosted panel, line icon, title and optional subtitle. */
final class CardView extends View {

    interface Listener {
        void onOpen(AppRepo.Entry e);
        void onGear(AppRepo.Entry e);
        void onEdit(AppRepo.Entry e, CardView v);
    }

    final AppRepo.Entry entry;
    private final Listener listener;
    private final float dp;
    private final Paint body = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint band = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint press = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint title = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint sub = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final RectF artDst = new RectF();
    private final Paint artPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    /** Day theme: the ccNC icons are pale line art for dark screens; darken them to ~40%. */
    private static final android.graphics.ColorFilter LIGHT_FILTER =
            new android.graphics.ColorMatrixColorFilter(new float[]{
                    0.4f, 0, 0, 0, 0,
                    0, 0.4f, 0, 0, 0,
                    0, 0, 0.4f, 0, 0,
                    0, 0, 0, 1, 0});
    private final Path bandPath = new Path();
    private float downX, downY;
    private Theme drawnTheme;
    private boolean gearDown;
    private boolean editMode;
    private boolean lifted; // dragged card: full opacity, no dimming

    CardView(Context ctx, AppRepo.Entry entry, Typeface bold, Typeface regular, Listener listener) {
        super(ctx);
        this.entry = entry;
        this.listener = listener;
        this.dp = ctx.getResources().getDisplayMetrics().density;
        band.setColor(0x16FFFFFF);
        border.setStyle(Paint.Style.STROKE);
        border.setStrokeWidth(Math.max(1f, dp * 0.75f));
        border.setColor(0x24FFFFFF);
        press.setColor(0x26FFFFFF);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(2f * dp);
        ring.setColor(0xFFE39A5B);
        title.setColor(0xFFFFFFFF);
        title.setTypeface(bold);
        sub.setColor(0xFFB4BEC6);
        sub.setTypeface(regular);
        setClickable(true);
        setLongClickable(true);
        setContentDescription(entry.label);
        setOnLongClickListener(new OnLongClickListener() {
            @Override public boolean onLongClick(View v) {
                CardView.this.listener.onEdit(CardView.this.entry, CardView.this);
                return true;
            }
        });
        setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) {
                if (CardView.this.entry.stackBack != null) {
                    // A stack is two half-height cards: the touch lands on the top or the bottom one.
                    CardView.this.listener.onOpen(downY > getHeight() / 2f ? CardView.this.entry.stackBack : CardView.this.entry);
                } else if (gearDown && inGear(downX, downY)) CardView.this.listener.onGear(CardView.this.entry);
                else CardView.this.listener.onOpen(CardView.this.entry);
            }
        });
    }

    private float peekPx() { return 0f; }

    private boolean hasGear() {
        return entry.kind == Icons.Kind.PROJECTION && !Curated.PROJECTION_SETTINGS.equals(entry.key());
    }

    private float gearR() { return Math.min(getWidth() * 0.085f, 19f * dp); }

    private boolean inGear(float x, float y) {
        if (!hasGear()) return false;
        float cx = getWidth() / 2f, cy = peekPx() + (getHeight() - peekPx()) * 0.92f, r = gearR() * 1.8f;
        return (x - cx) * (x - cx) + (y - cy) * (y - cy) <= r * r;
    }

    /** Edit-mode "hide" badge, top-left corner. */
    float badgeCx() { return 26f * dp; }
    float badgeCy() { return 26f * dp; }
    float badgeR() { return 15f * dp; }

    boolean inBadge(float x, float y) {
        if (!editMode) return false;
        float dx = x - badgeCx(), dy = y - badgeCy();
        return dx * dx + dy * dy <= badgeR() * badgeR() * 2.25f;
    }

    /** Edit-mode "ungroup" badge on a stack, top-right corner. */
    float ungroupCx() { return getWidth() - 26f * dp; }

    boolean inUngroup(float x, float y) {
        if (!editMode || entry.stackBack == null) return false;
        float dx = x - ungroupCx(), dy = y - badgeCy();
        return dx * dx + dy * dy <= badgeR() * badgeR() * 2.25f;
    }

    /** Drawn while a dragged card is held over this one: dropping it here groups the two. */
    private boolean stackTarget;
    void setStackTarget(boolean on) { if (on != stackTarget) { stackTarget = on; invalidate(); } }

    void setEditMode(boolean on) {
        editMode = on;
        if (!on) lifted = false;
        invalidate();
    }

    boolean isEditMode() { return editMode; }

    /** The dragged card stays lit while its siblings dim, like ccNC's edit mode. */
    void setLifted(boolean on) {
        lifted = on;
        invalidate();
    }

    @Override public boolean onTouchEvent(MotionEvent ev) {
        if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
            downX = ev.getX();
            downY = ev.getY();
            gearDown = inGear(downX, downY);
            stackDown = downY > getHeight() / 2f ? 1 : 0;
        }
        return super.onTouchEvent(ev);
    }

    @Override protected void drawableStateChanged() {
        super.drawableStateChanged();
        invalidate();
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        rect.set(0, 0, w, h);
        drawnTheme = null;
        float r = 14f * dp;
        bandPath.reset();
        bandPath.addRoundRect(new RectF(0, h * 0.60f, w, h), new float[]{0, 0, 0, 0, r, r, r, r}, Path.Direction.CW);
    }

    @Override protected void onDraw(Canvas c) {
        if (entry.stackBack != null) { drawStack(c); return; }
        float peek = peekPx();
        float w = getWidth(), h = getHeight() - peek, r = 14f * dp;
        Theme t = Theme.current;
        c.save();
        c.translate(0, peek);
        rect.set(0, 0, w, h);
        bandPath.reset();
        bandPath.addRoundRect(new RectF(0, h * 0.60f, w, h), new float[]{0, 0, 0, 0, r, r, r, r}, Path.Direction.CW);
        if (drawnTheme != t) {
            drawnTheme = t;
            body.setShader(new LinearGradient(0, 0, 0, h, t.cardTop, t.cardBottom, Shader.TileMode.CLAMP));
            band.setColor(t.band);
            border.setColor(t.border);
            press.setColor(t.press);
            ring.setColor(t.accent());
            title.setColor(t.title);
            sub.setColor(t.subtitle);
        }
        c.drawRoundRect(rect, r, r, body);
        c.drawPath(bandPath, band);
        RectF inset = new RectF(rect);
        inset.inset(border.getStrokeWidth() / 2, border.getStrokeWidth() / 2);
        c.drawRoundRect(inset, r, r, border);

        // ccNC edit mode: the card chrome dims to half strength and an edit
        // outline is drawn over it; the dragged card stays lit.
        boolean dim = editMode && !lifted;
        int titleA = title.getAlpha(), subA = sub.getAlpha();
        if (dim) {
            title.setAlpha(128);
            sub.setAlpha(110);
        }

        float iconSize = Math.min(w * 0.44f, h * 0.26f);
        float iconCy = h * 0.30f;
        if (entry.art != null) {
            // ccNC artwork: 90 px icons drawn on a 1920x720 screen like ours, so ~1:1 scale.
            float s = iconSize * 0.95f;
            artDst.set(w / 2f - s / 2f, iconCy - s / 2f, w / 2f + s / 2f, iconCy + s / 2f);
            artPaint.setAlpha(dim ? 128 : 255);
            // Built-in icons are light-on-dark: darken them on light themes. A theme pack's icons are made for its
            // own background, so they're drawn as they are.
            if (t.light && dev.ccnc.common.Palette.ICONS == null) artPaint.setColorFilter(LIGHT_FILTER); else artPaint.setColorFilter(null);
            c.drawBitmap(entry.art, null, artDst, artPaint);
        } else if (entry.appIcon != null) {
            // Projection logos are full app icons: draw them a bit larger than line icons.
            int s = (int) (iconSize * (entry.kind == Icons.Kind.PROJECTION ? 1.35f : 0.9f));
            int l = (int) (w / 2 - s / 2f), top = (int) (iconCy - s / 2f);
            entry.appIcon.setBounds(l, top, l + s, top + s);
            if (dim) entry.appIcon.setAlpha(128);
            entry.appIcon.draw(c);
            entry.appIcon.setAlpha(255);
        } else {
            int color = t.icon(entry.kind);
            if (dim) color = (color & 0x00FFFFFF) | 0x80000000;
            Icons.draw(c, entry.kind, w / 2f, iconCy, iconSize, color);
        }

        float pad = w * 0.12f;
        float avail = w - pad * 2;
        // Gear cards (CarPlay / Android Auto) keep the bottom for the gear: text sits higher.
        boolean lift = hasGear() && !TextUtils.isEmpty(entry.subtitle);
        title.setTextSize(Math.min(21f * dp, w * 0.115f) * (hasGear() ? 0.9f : 1f));
        sub.setTextSize(Math.min(15f * dp, w * 0.085f) * (hasGear() ? 0.92f : 1f));
        float titleY = h * 0.74f; // same line on every card; the subtitle and gear sit below it
        fit(title, entry.label, avail);
        drawWrapped(c, entry.label, title, pad, titleY, avail, 2);

        if (!TextUtils.isEmpty(entry.subtitle)) {
            String[] lines = entry.subtitle.split("\n");
            float y = h * (lift ? 0.80f : 0.855f);
            for (String line : lines) {
                float subSize = sub.getTextSize();
                fit(sub, line, avail);
                c.drawText(TextUtils.ellipsize(line, sub, avail, TextUtils.TruncateAt.END).toString(), pad, y, sub);
                sub.setTextSize(subSize);
                y += sub.getTextSize() * 1.25f;
            }
        }
        if (hasGear()) {
            float gx = w / 2f, gy = h * 0.92f, gr = gearR();
            c.drawCircle(gx, gy, gr, ring);
            Icons.draw(c, Icons.Kind.SETTINGS, gx, gy, gr * 1.15f, t.title);
        }

        if (isPressed()) c.drawRoundRect(rect, r, r, press);

        if (dim) {
            title.setAlpha(titleA);
            sub.setAlpha(subA);
            Paint outline = new Paint(Paint.ANTI_ALIAS_FLAG);
            outline.setStyle(Paint.Style.STROKE);
            outline.setStrokeWidth(2f * dp);
            outline.setColor(t.light ? 0x66000000 : 0x8CFFFFFF);
            RectF o = new RectF(rect);
            o.inset(dp, dp);
            c.drawRoundRect(o, r - dp, r - dp, outline);
        }

        c.restore();
        if (editMode) {
            // ccNC-style remove badge: dark disc, pale ring, white minus.
            float bx = badgeCx(), by = badgeCy(), br = badgeR();
            Paint badge = new Paint(Paint.ANTI_ALIAS_FLAG);
            badge.setColor(t.light ? 0xD9FFFFFF : 0xD9212B34);
            c.drawCircle(bx, by, br, badge);
            Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
            stroke.setColor(t.light ? 0x8C000000 : 0xB3FFFFFF);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(1.6f * dp);
            c.drawCircle(bx, by, br - 0.8f * dp, stroke);
            stroke.setStyle(Paint.Style.FILL);
            stroke.setStrokeWidth(0f);
            float m = br * 0.42f, th = 2.6f * dp;
            c.drawRect(bx - m, by - th / 2f, bx + m, by + th / 2f, stroke);
        }
        drawStackTarget(c);
    }

    // ---- stacks: two half-height cards sharing one slot ----

    private int stackDown = -1; // 0 top, 1 bottom while pressed

    private void drawStack(Canvas c) {
        Theme t = Theme.current;
        float w = getWidth(), gap = 12f * dp, hh = (getHeight() - gap) / 2f, r = 14f * dp;
        drawHalf(c, entry, 0, hh, w, r, t, isPressed() && stackDown == 0);
        drawHalf(c, entry.stackBack, hh + gap, hh, w, r, t, isPressed() && stackDown == 1);
        if (editMode) {
            float bx = badgeCx(), by = badgeCy(), br = badgeR();
            Paint badge = new Paint(Paint.ANTI_ALIAS_FLAG);
            badge.setColor(t.light ? 0xD9FFFFFF : 0xD9212B34);
            c.drawCircle(bx, by, br, badge);
            badge.setColor(t.light ? 0xFF16212A : 0xFFFFFFFF);
            float m = br * 0.42f, th = 2.6f * dp;
            c.drawRect(bx - m, by - th / 2f, bx + m, by + th / 2f, badge);
            // Ungroup: two small cards pulled apart.
            float ux = ungroupCx();
            badge.setColor(t.light ? 0xD9FFFFFF : 0xD9212B34);
            c.drawCircle(ux, by, br, badge);
            badge.setColor(t.light ? 0xFF16212A : 0xFFFFFFFF);
            float bw = br * 0.62f, bh = br * 0.30f, rr = 1.6f * dp;
            c.drawRoundRect(new RectF(ux - bw / 2f, by - bh - 2f * dp, ux + bw / 2f, by - 2f * dp), rr, rr, badge);
            c.drawRoundRect(new RectF(ux - bw / 2f, by + 2f * dp, ux + bw / 2f, by + bh + 2f * dp), rr, rr, badge);
        }
        drawStackTarget(c);
    }

    private void drawStackTarget(Canvas c) {
        if (!stackTarget) return;
        Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(3f * dp);
        ring.setColor(Theme.current.accent());
        RectF o = new RectF(0, 0, getWidth(), getHeight());
        o.inset(2f * dp, 2f * dp);
        c.drawRoundRect(o, 14f * dp, 14f * dp, ring);
    }

    private void drawHalf(Canvas c, AppRepo.Entry e, float top, float hh, float w, float r, Theme t, boolean down) {
        RectF b = new RectF(0, top, w, top + hh);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(0, top, 0, top + hh, t.cardTop, t.cardBottom, Shader.TileMode.CLAMP));
        c.drawRoundRect(b, r, r, p);
        Paint ln = new Paint(Paint.ANTI_ALIAS_FLAG);
        ln.setStyle(Paint.Style.STROKE);
        ln.setStrokeWidth(Math.max(1f, dp * 0.75f));
        ln.setColor(t.border);
        RectF in = new RectF(b);
        in.inset(ln.getStrokeWidth() / 2, ln.getStrokeWidth() / 2);
        c.drawRoundRect(in, r, r, ln);

        // Icon on the left, title and subtitle on the right.
        float is = Math.min(46f * dp, hh * 0.46f), ix = 14f * dp, iy = top + (hh - is) / 2f - (TextUtils.isEmpty(e.subtitle) ? 0 : 4f * dp);
        if (e.art != null) {
            RectF d = new RectF(ix, iy, ix + is, iy + is);
            artPaint.setAlpha(255);
            if (t.light) artPaint.setColorFilter(LIGHT_FILTER); else artPaint.setColorFilter(null);
            c.drawBitmap(e.art, null, d, artPaint);
        } else if (e.appIcon != null) {
            e.appIcon.setBounds((int) ix, (int) iy, (int) (ix + is), (int) (iy + is));
            e.appIcon.draw(c);
        } else {
            Icons.draw(c, e.kind, ix + is / 2f, iy + is / 2f, is * 0.9f, t.icon(e.kind));
        }
        float tx = ix + is + 12f * dp, avail = w - tx - 12f * dp;
        title.setTextSize(Math.min(19f * dp, w * 0.105f));
        sub.setTextSize(Math.min(13f * dp, w * 0.075f));
        title.setColor(t.title);
        sub.setColor(t.subtitle);
        float ty = top + hh / 2f + (TextUtils.isEmpty(e.subtitle) ? title.getTextSize() * 0.35f : -2f * dp);
        float ts = title.getTextSize();
        fit(title, e.label, avail);
        c.drawText(TextUtils.ellipsize(e.label, title, avail, TextUtils.TruncateAt.END).toString(), tx, ty, title);
        title.setTextSize(ts);
        if (!TextUtils.isEmpty(e.subtitle)) {
            String line = e.subtitle.split("\n")[0];
            float ss = sub.getTextSize();
            fit(sub, line, avail);
            c.drawText(TextUtils.ellipsize(line, sub, avail, TextUtils.TruncateAt.END).toString(), tx, ty + sub.getTextSize() * 1.5f, sub);
            sub.setTextSize(ss);
        }
        if (down) {
            Paint pr = new Paint(Paint.ANTI_ALIAS_FLAG);
            pr.setColor(t.press);
            c.drawRoundRect(b, r, r, pr);
        }
    }

    /** Shrinks the paint (to 72% at most) so {@code text} fits on one line; wraps/ellipsizes after that. */
    private static void fit(TextPaint p, String text, float avail) {
        float w = p.measureText(text);
        if (w > avail) p.setTextSize(p.getTextSize() * Math.max(0.72f, avail / w * 0.97f));
    }

    /** Draws up to {@code maxLines} lines, growing upward so the last line sits on {@code baseY}. */
    private static void drawWrapped(Canvas c, String text, TextPaint p, float x, float baseY, float avail, int maxLines) {
        if (p.measureText(text) <= avail || maxLines == 1) {
            c.drawText(TextUtils.ellipsize(text, p, avail, TextUtils.TruncateAt.END).toString(), x, baseY, p);
            return;
        }
        int cut = p.breakText(text, true, avail, null);
        int space = text.lastIndexOf(' ', cut);
        if (space > 0) cut = space;
        String first = text.substring(0, cut).trim();
        String rest = text.substring(cut).trim();
        float lh = p.getTextSize() * 1.2f;
        c.drawText(first, x, baseY - lh, p);
        c.drawText(TextUtils.ellipsize(rest, p, avail, TextUtils.TruncateAt.END).toString(), x, baseY, p);
    }
}
