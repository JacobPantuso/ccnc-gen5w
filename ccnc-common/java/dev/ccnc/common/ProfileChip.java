package dev.ccnc.common;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.text.TextUtils;
import android.view.View;

/**
 * The profile chip at the top left of every ccNC top bar, like stock Gen5W and ccNC: the active profile's photo
 * and name. Tap: ccNC Profile (switch profile, photos, default profile). Follows profile changes made anywhere.
 */
public final class ProfileChip extends View implements Profiles.Listener {
    private final Activity a;
    private final float dp;
    private final Paint name = new Paint(Paint.ANTI_ALIAS_FLAG), img = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG),
            ring = new Paint(Paint.ANTI_ALIAS_FLAG), hi = new Paint(Paint.ANTI_ALIAS_FLAG), fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private Object watch;
    private int active = -1;
    private String label = "";
    private Bitmap photo;
    private int loadSeq;

    public ProfileChip(Activity activity) {
        super(activity);
        a = activity;
        dp = a.getResources().getDisplayMetrics().density;
        name.setTypeface(Fonts.get(a, Fonts.SEMIBOLD));
        name.setTextSize(17 * dp);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(1.5f * dp);
        setClickable(true);
        setSoundEffectsEnabled(false);
        setContentDescription("Profile");
        setOnClickListener(new OnClickListener() { @Override public void onClick(View v) { Chrome.beep(a); open(a); } });
    }

    /** Opens ccNC Profile (or the stock profile screen if ccNC Profile isn't installed). */
    public static void open(Activity a) {
        try {
            Intent i = new Intent().setComponent(ComponentName.unflattenFromString(Profiles.PROFILE_ACTIVITY))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (a.getPackageManager().resolveActivity(i, 0) == null)
                i = new Intent("com.mobis.systemui.avnstatusbar.PROFILE_CLICKED");
            if (i.getComponent() != null) a.startActivity(i); else a.sendBroadcast(i);
        } catch (Throwable ignored) { }
    }

    /** Width it wants: photo + gap + name, capped. */
    @Override protected void onMeasure(int wSpec, int hSpec) {
        int w = (int) (40 * dp + 10 * dp + Math.min(160 * dp, name.measureText(label.isEmpty() ? "Driver 1" : label)) + 16 * dp);
        setMeasuredDimension(resolveSize(w, wSpec), resolveSize((int) (48 * dp), hSpec));
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        watch = Profiles.watch(a, this);
        onProfilesChanged();
    }

    @Override protected void onDetachedFromWindow() {
        Profiles.unwatch(a, watch);
        watch = null;
        super.onDetachedFromWindow();
    }

    /** Re-reads off the UI thread (binder + provider calls), then redraws. */
    @Override public void onProfilesChanged() {
        final int seq = ++loadSeq;
        final boolean held = StartupHold.holding();   // at start-up: this app's copy (ProfileCache), ccNC Profile isn't woken
        new Thread(new Runnable() { @Override public void run() {
            final int act = Profiles.active(a);
            final String n = act >= 0 ? Profiles.name(a, act) : "";
            final Bitmap p = act >= 0 ? (held ? ProfileCache.photo(a, act, false) : Profiles.photo(a, act)) : null;
            post(new Runnable() { @Override public void run() {
                if (seq != loadSeq) return;
                active = act;
                label = n;
                photo = p;
                setVisibility(act >= 0 ? VISIBLE : GONE);
                requestLayout();
                invalidate();
            } });
        } }, "ccncProfileChip").start();
    }

    @Override public void setPressed(boolean p) { super.setPressed(p); invalidate(); }

    @Override protected void onDraw(Canvas c) {
        float h = getHeight(), cy = h / 2f, d = Math.min(36 * dp, h - 4 * dp), cx = 4 * dp + d / 2f;
        name.setTextSize(h < 40 * dp ? 14 * dp : 17 * dp);   // the slim status strip (Climate) is 32 dp tall
        if (isPressed()) {
            hi.setColor(Palette.PRESS);
            float ph = Math.min(22 * dp, h / 2f);
            r.set(0, cy - ph, getWidth(), cy + ph);
            c.drawRoundRect(r, ph, ph, hi);
        }
        if (photo != null) {
            BitmapShader s = new BitmapShader(photo, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            Matrix m = new Matrix();
            float k = d / Math.min(photo.getWidth(), photo.getHeight());
            m.setScale(k, k);
            m.postTranslate(cx - photo.getWidth() * k / 2f, cy - photo.getHeight() * k / 2f);
            s.setLocalMatrix(m);
            img.setShader(s);
            c.drawCircle(cx, cy, d / 2f, img);
        } else {
            fill.setColor(Palette.alpha(Palette.TEXT, 0x33));
            c.drawCircle(cx, cy, d / 2f, fill);
            if (!label.isEmpty()) {
                name.setTextAlign(Paint.Align.CENTER);
                name.setColor(Palette.TEXT);
                c.drawText(label.substring(0, 1).toUpperCase(java.util.Locale.getDefault()), cx, cy + 6 * dp, name);
            }
        }
        ring.setColor(Palette.alpha(Palette.TEXT, 0x59));
        c.drawCircle(cx, cy, d / 2f, ring);
        name.setTextAlign(Paint.Align.LEFT);
        name.setColor(Palette.TEXT);
        float x = cx + d / 2f + 10 * dp, avail = getWidth() - x - 8 * dp;
        c.drawText(TextUtils.ellipsize(label, new android.text.TextPaint(name), avail, TextUtils.TruncateAt.END).toString(), x, cy + 6 * dp, name);
    }
}
