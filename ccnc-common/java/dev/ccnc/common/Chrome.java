package dev.ccnc.common;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * ccNC's system bar for our apps. ccNC draws it in its own process (statusbarhmi): indicators and
 * clock along the top, back/home buttons on the left, and a now-playing / media control at the
 * bottom right. Our apps don't get that window, so each draws it: {@link #topBar} is the 56 dp
 * title row with back, home, the page title, the date, Bluetooth and the clock; the media
 * button floats over the bottom-right corner.
 */
public final class Chrome {

    /** Bluetooth state for the status glyph; a missing permission must never take the app down. */
    static boolean btOn(BluetoothAdapter ad) {
        try { return ad != null && ad.isEnabled(); } catch (Throwable t) { return false; }
    }

    private Chrome() {}

    private static final String HOME_PKG = "dev.ccnc.home", MEDIA_PKG = "dev.ccnc.media";
    private static final String OVERLAY_TAG = "ccnc-chrome-media";

    /** The top bar: back, home, title; extras can be added after the title. The right side is reserved for the clock. */
    public static LinearLayout topBar(Activity a, String title) {
        TopBar bar = new TopBar(a, title);
        bar.setLayoutParams(new LinearLayout.LayoutParams(-1, Palette.px(a, 56)));
        addBottomBars(a);
        return bar;
    }

    /**
     * A slim status strip (home button, date, Bluetooth, clock) for full-screen layouts that draw
     * their own controls to the edges, like ccNC's climate page. No title and no media button.
     */
    public static View statusStrip(final Activity a) { return statusStrip(a, true); }

    /** {@code profile}: false on the profile chooser itself (it shows the profiles in full). */
    public static View statusStrip(final Activity a, boolean profile) {
        final float dp = a.getResources().getDisplayMetrics().density;
        final Paint date = new Paint(Paint.ANTI_ALIAS_FLAG), time = new Paint(Paint.ANTI_ALIAS_FLAG),
                ampm = new Paint(Paint.ANTI_ALIAS_FLAG), bt = new Paint(Paint.ANTI_ALIAS_FLAG), p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG),
                hi = new Paint(Paint.ANTI_ALIAS_FLAG);
        date.setTypeface(Fonts.get(a, Fonts.SEMIBOLD)); date.setTextSize(16 * dp); date.setTextAlign(Paint.Align.CENTER);
        time.setTypeface(Fonts.get(a, Fonts.SEMIBOLD)); time.setTextSize(19 * dp);
        ampm.setTypeface(Fonts.get(a, Fonts.SEMIBOLD)); ampm.setTextSize(10 * dp);
        bt.setStyle(Paint.Style.STROKE); bt.setStrokeWidth(1.6f * dp); bt.setStrokeJoin(Paint.Join.ROUND); bt.setStrokeCap(Paint.Cap.ROUND);
        final Bitmap home = Art.chrome(a, "home");
        final RectF r = new RectF();
        View v = new View(a) {
            private final Runnable tick = new Runnable() { @Override public void run() { invalidate(); postDelayed(this, 20000); } };
            @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); post(tick); }
            @Override protected void onDetachedFromWindow() { removeCallbacks(tick); super.onDetachedFromWindow(); }
            @Override public void setPressed(boolean pr) { super.setPressed(pr); invalidate(); }
            @Override public boolean onTouchEvent(MotionEvent e) {
                if (e.getX() > getWidth() - 140 * dp) { // the clock: 7 taps open the developer menu
                    if (e.getActionMasked() == MotionEvent.ACTION_UP) DevMenu.tap(a);
                    return true;
                }
                if (e.getX() > 80 * dp) return false; // only the home button takes touches
                return super.onTouchEvent(e);
            }
            @Override protected void onDraw(Canvas c) {
                int col = Palette.TEXT;
                date.setColor(col); time.setColor(col); ampm.setColor(col); bt.setColor(col);
                float w = getWidth(), h = getHeight(), base = h / 2f + 6 * dp;
                if (isPressed()) { hi.setColor(Palette.PRESS); c.drawCircle(26 * dp, h / 2f, 20 * dp, hi); }
                if (home != null) { r.set(8 * dp, h / 2f - 18 * dp, 44 * dp, h / 2f + 18 * dp); c.drawBitmap(home, null, r, p); }
                Date now = new Date();
                Locale loc = Locale.getDefault();
                c.drawText(new SimpleDateFormat("EEE, MMM", loc).format(now) + ". " + new SimpleDateFormat("d", loc).format(now), w / 2f, base, date);
                boolean h24 = DateFormat.is24HourFormat(getContext());
                String t = new SimpleDateFormat(h24 ? "H:mm" : "h:mm", loc).format(now);
                String ap = h24 ? "" : new SimpleDateFormat("a", Locale.US).format(now).toUpperCase(Locale.US);
                float right = w - 22 * dp, apW = ap.isEmpty() ? 0 : ampm.measureText(ap) + 3 * dp, tW = time.measureText(t);
                if (!ap.isEmpty()) c.drawText(ap, right - apW + 3 * dp, base, ampm);
                c.drawText(t, right - apW - tW, base, time);
                BluetoothAdapter ad = null;
                try { ad = BluetoothAdapter.getDefaultAdapter(); } catch (Throwable ignored) {}
                if (btOn(ad)) {
                    float cx = right - apW - tW - 20 * dp, cy = base - 7 * dp, s = 8 * dp;
                    Path q = new Path();
                    q.moveTo(cx - s * 0.55f, cy - s * 0.45f); q.lineTo(cx + s * 0.5f, cy + s * 0.45f); q.lineTo(cx, cy + s);
                    q.lineTo(cx, cy - s); q.lineTo(cx + s * 0.5f, cy - s * 0.45f); q.lineTo(cx - s * 0.55f, cy + s * 0.45f);
                    c.drawPath(q, bt);
                }
            }
        };
        v.setClickable(true);
        v.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View x) { goHome(a); } });
        if (!profile) return v;
        // The active profile next to the home button, as on the full top bar.
        android.widget.FrameLayout f = new android.widget.FrameLayout(a);
        f.addView(v, new android.widget.FrameLayout.LayoutParams(-1, -1));
        android.widget.FrameLayout.LayoutParams cp = new android.widget.FrameLayout.LayoutParams(-2, -1, Gravity.LEFT | Gravity.CENTER_VERTICAL);
        cp.leftMargin = (int) (56 * dp);
        f.addView(new ProfileChip(a), cp);
        return f;
    }

    private static Object audio;
    private static java.lang.reflect.Method beepM;

    /** The head unit's own touch beep (com.hkmc.media.MicomAudioManager.beep()), as ccNC plays on its bar buttons. */
    public static void beep(Context c) {
        try {
            if (beepM == null) {
                Class<?> k = Class.forName("com.hkmc.media.MicomAudioManager");
                audio = k.getConstructor(Context.class).newInstance(c.getApplicationContext());
                beepM = k.getMethod("beep");
            }
            beepM.invoke(audio);
        } catch (Throwable ignored) { }
    }

    public static void goHome(Context c) {
        try {
            Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            Intent ours = new Intent(i).setPackage(HOME_PKG);
            c.startActivity(c.getPackageManager().resolveActivity(ours, 0) != null ? ours : i);
        } catch (Throwable ignored) {}
    }

    private static void openMedia(Activity a) {
        Intent i = a.getPackageManager().getLaunchIntentForPackage(MEDIA_PKG);
        if (i == null) return;
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        a.startActivity(i);
    }

    /**
     * ccNC's bottom row: a back/home pill on the left and the now-playing pill on the right (not in the media app).
     * Right-hand drive (Drive.rhd()): swapped, so back and home are next to the driver.
     */
    private static void addBottomBars(final Activity a) {
        a.getWindow().getDecorView().post(new Runnable() {
            @Override public void run() {
                ViewGroup content = (ViewGroup) a.findViewById(android.R.id.content);
                if (!(content instanceof FrameLayout) || content.findViewWithTag(OVERLAY_TAG) != null) return;
                BottomBars m = new BottomBars(a, !MEDIA_PKG.equals(a.getPackageName())
                        && a.getPackageManager().getLaunchIntentForPackage(MEDIA_PKG) != null);
                m.setTag(OVERLAY_TAG);
                content.addView(m, new FrameLayout.LayoutParams(-1, BottomBars.H, Gravity.BOTTOM));
            }
        });
    }

    /**
     * ccNC's BOTTOMBAR windows (statusbarhmi): two translucent glass pills 90 px high on the bottom
     * row. Left: back and home. Right: now playing, with the cover (90 x 90), a 4 px progress line,
     * the title and the artist; tapping it opens the media app. Sizes are ccNC's 1920 x 720 pixels.
     */
    private static final class BottomBars extends View {
        static final int H = 90;
        private static final float PILL_L = 330f, PILL_R = 472f, RAD = 20f;
        private static final String[] ACTIONS = {"com.hkmc.audio.usb.widgetupdate", "com.hkmc.audio.btaudio.widgetupdate",
                "com.hkmc.radio.widgetupdate", "com.hkmc.radio.dab.widgetupdate", "com.hkmc.sxmradio.widgetupdate",
                "com.hkmc.dipoaudio.widgetupdate", "com.hkmc.gpmaudio.widgetupdate", "com.hkmc.audio.mymusic.widgetupdate",
                "com.hkmc.audio.audiocd.widgetupdate", "com.hkmc.audio.mp3cd.widgetupdate", "com.hkmc.audio.ipod.widgetupdate"};

        private final Activity a;
        private final boolean nowPlaying;
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), line = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint bmp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG), bar = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private final Bitmap back, home;
        private String title = "", artist = "", action = "";
        private Bitmap art;
        private long pos, dur;
        private int down = -1; // 0 back, 1 home, 2 now playing
        /** Right-hand drive: back/home on the right (next to the driver), now playing on the left. */
        private final boolean rhd = Drive.rhd(getContext());

        /** Left edges of the two pills, in ccNC pixels. */
        private float navL() { return rhd ? getWidth() / scale() - PILL_L : 0f; }
        private float npL() { return rhd ? 0f : getWidth() / scale() - PILL_R; }

        private final android.content.BroadcastReceiver rx = new android.content.BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) { onFeed(i); }
        };

        BottomBars(Activity a, boolean nowPlaying) {
            super(a);
            this.a = a;
            this.nowPlaying = nowPlaying;
            back = Art.chrome(a, "back");
            home = Art.chrome(a, "home");
            tp.setTypeface(Fonts.get(a, Fonts.REGULAR));
            tp.setTextSize(28f);
            line.setStyle(Paint.Style.STROKE);
            line.setStrokeWidth(1f);
            setClickable(true);
        }

        private void onFeed(Intent i) {
            if (!i.getAction().equals(action)) { action = i.getAction(); title = ""; artist = ""; art = null; pos = 0; dur = 0; }
            if (i.hasExtra("valid_media") && !i.getBooleanExtra("valid_media", true)) {
                title = ""; artist = ""; art = null; pos = 0; dur = 0;
            } else {
                if (i.hasExtra("filename")) title = nz(i.getStringExtra("filename")).trim();
                if (i.hasExtra("artist_name")) {
                    String t = nz(i.getStringExtra("artist_name"));
                    int bullet = t.indexOf('\u2022');
                    artist = (bullet >= 0 ? t.substring(0, bullet) : t).trim();
                }
                if (i.hasExtra("audio_total_time")) { long t = i.getLongExtra("audio_total_time", 0); secs = seconds(t); dur = secs ? t * 1000 : t; }
                if (i.hasExtra("audio_current_time")) { long t = i.getLongExtra("audio_current_time", 0); pos = secs ? t * 1000 : t; }
                byte[] b = i.getByteArrayExtra("artwork_raw_image");
                if (b != null && b.length > 0) {
                    try {
                        android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options();
                        o.inSampleSize = 4;
                        art = android.graphics.BitmapFactory.decodeByteArray(b, 0, b.length, o);
                    } catch (Throwable ignored) {}
                }
            }
            invalidate();
        }

        private static String nz(String s) { return s == null ? "" : s; }
        /**
         * Times come in seconds or milliseconds depending on the source. The unit is read from the song's length
         * (no song is under 20 s long in ms) and used for the position too: the position on its own is under 20000
         * for the first 20 s of every song in ms, which read as seconds put the bar at full.
         */
        private boolean secs;
        private static boolean seconds(long total) { return total > 0 && total < 20000; }

        @Override protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            if (!nowPlaying) return;
            try {
                android.content.IntentFilter f = new android.content.IntentFilter();
                for (String act : ACTIONS) f.addAction(act);
                getContext().registerReceiver(rx, f);
                getContext().sendBroadcast(new Intent("com.hkmc.request.widgetupdate"));
            } catch (Throwable ignored) {}
        }

        @Override protected void onDetachedFromWindow() {
            if (nowPlaying) try { getContext().unregisterReceiver(rx); } catch (Throwable ignored) {}
            super.onDetachedFromWindow();
        }

        private float scale() { return getWidth() / 1920f; }

        private int hit(float x, float y) {
            float k = scale();
            if (y < 0 || y > getHeight()) return -1;
            float px = x / k, nl = navL(), ml = npL();
            if (px >= nl && px <= nl + PILL_L) return px < nl + PILL_L / 2f ? 0 : 1;
            if (nowPlaying && px >= ml && px <= ml + PILL_R) return 2;
            return -1;
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    down = hit(e.getX(), e.getY());
                    if (down < 0) return false; // outside the pills the app underneath gets the touch
                    invalidate();
                    return true;
                case MotionEvent.ACTION_UP:
                    int h = hit(e.getX(), e.getY()), d = down;
                    down = -1;
                    invalidate();
                    if (h == d) {
                        beep(a);
                        if (d == 0) a.onBackPressed(); // the app's own back (sub-page, then screen), like ccNC's GNB Back
                        else if (d == 1) goHome(a);
                        else if (d == 2) openMedia(a);
                    }
                    return true;
                case MotionEvent.ACTION_CANCEL: down = -1; invalidate(); return true;
                default: return down >= 0;
            }
        }

        private void pill(Canvas c, float l, float rt, boolean pressed) {
            r.set(l, 1f, rt, getHeight() / scale() - 1f);
            fill.setColor(0x1F85BAFF);
            c.drawRoundRect(r, RAD, RAD, fill);
            if (pressed) { fill.setColor(Palette.PRESS); c.drawRoundRect(r, RAD, RAD, fill); }
            line.setColor(0x3185BAFF);
            c.drawRoundRect(r, RAD, RAD, line);
        }

        @Override protected void onDraw(Canvas c) {
            float k = scale();
            c.save();
            c.scale(k, k); // draw in ccNC's own pixels
            // Back and home.
            float nl = navL();
            pill(c, nl, nl + PILL_L, false);
            float[] cx = {nl + 83f, nl + 235f};
            Bitmap[] ic = {back, home};
            for (int i = 0; i < 2; i++) {
                if (down == i) { fill.setColor(Palette.PRESS); c.drawCircle(cx[i], 45f, 38f, fill); }
                if (ic[i] != null) { r.set(cx[i] - 40f, 5f, cx[i] + 40f, 85f); c.drawBitmap(ic[i], null, r, bmp); }
            }
            if (nowPlaying) {
                float l = npL(); // now-playing pill origin in ccNC pixels
                pill(c, l, l + PILL_R, down == 2);
                // Cover (or the media icon on a plain tile), 90 x 90, rounded.
                r.set(l, 0f, l + 90f, 90f);
                if (art != null) {
                    android.graphics.BitmapShader sh = new android.graphics.BitmapShader(art, android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP);
                    android.graphics.Matrix m = new android.graphics.Matrix();
                    float sc = Math.max(90f / art.getWidth(), 90f / art.getHeight());
                    m.setScale(sc, sc);
                    m.postTranslate(l + (90f - art.getWidth() * sc) / 2f, (90f - art.getHeight() * sc) / 2f);
                    sh.setLocalMatrix(m);
                    bmp.setShader(sh);
                    c.drawRoundRect(r, RAD, RAD, bmp);
                    bmp.setShader(null);
                } else {
                    fill.setColor(0x2285BAFF);
                    c.drawRoundRect(r, RAD, RAD, fill);
                    Bitmap mi = Art.icon(getContext(), sourceIcon());
                    if (mi != null) { r.set(l + 21f, 21f, l + 69f, 69f); c.drawBitmap(mi, null, r, bmp); }
                }
                // Progress line along the top of the text area.
                float tx = l + 90f, tw = PILL_R - 90f;
                if (dur > 0) {
                    // Clipped to the card's rounded outline so the line follows its top-right corner.
                    c.save();
                    android.graphics.Path clip = new android.graphics.Path();
                    r.set(l, 1f, l + PILL_R, getHeight() / k - 1f);
                    clip.addRoundRect(r, RAD, RAD, android.graphics.Path.Direction.CW);
                    c.clipPath(clip);
                    bar.setColor(0x33FFFFFF);
                    r.set(tx, 1f, l + PILL_R, 5f);
                    c.drawRect(r, bar);
                    bar.setColor(Palette.ACCENT);
                    r.set(tx, 1f, tx + tw * Math.min(1f, (float) pos / dur), 5f);
                    c.drawRect(r, bar);
                    c.restore();
                }
                // Title (28 px) and artist, left margin 18.
                if (!title.isEmpty()) {
                    tp.setColor(Palette.TEXT);
                    float avail = tw - 28f;
                    boolean two = !artist.isEmpty();
                    String t = android.text.TextUtils.ellipsize(title, new android.text.TextPaint(tp), avail, android.text.TextUtils.TruncateAt.END).toString();
                    c.drawText(t, tx + 18f, two ? 40f : 54f, tp);
                    if (two) {
                        tp.setColor(Palette.SUBTEXT);
                        c.drawText(android.text.TextUtils.ellipsize(artist, new android.text.TextPaint(tp), avail, android.text.TextUtils.TruncateAt.END).toString(), tx + 18f, 76f, tp);
                    }
                }
            }
            c.restore();
        }

        private String sourceIcon() {
            if (action.contains("dipoaudio")) return "carplay";
            if (action.contains("usb")) return "usb_music";
            if (action.contains("btaudio")) return "bt_audio";
            if (action.contains("radio") || action.contains("sxm")) return "media_radio";
            return "media";
        }
    }

    /** Back and home on the left, the title, then date / Bluetooth / clock painted over the right. */
    public static final class TopBar extends LinearLayout {
        /** Off for screens whose own ccNC tab bar sits in the centre of the top row (e.g. Weather). */
        public boolean showDate = true;
        private final Activity a;
        private final float dp;
        private final Paint date = new Paint(Paint.ANTI_ALIAS_FLAG), time = new Paint(Paint.ANTI_ALIAS_FLAG),
                ampm = new Paint(Paint.ANTI_ALIAS_FLAG), bt = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Runnable tick = new Runnable() {
            @Override public void run() { invalidate(); postDelayed(this, 20000); }
        };

        TopBar(Activity activity, String title) {
            super(activity);
            this.a = activity;
            dp = a.getResources().getDisplayMetrics().density;
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setPadding((int) (10 * dp), 0, (int) (24 * dp), 0);
            setWillNotDraw(false);
            date.setTypeface(Fonts.get(a, Fonts.SEMIBOLD));
            date.setTextSize(20 * dp);
            date.setTextAlign(Paint.Align.CENTER);
            time.setTypeface(Fonts.get(a, Fonts.SEMIBOLD));
            time.setTextSize(24 * dp);
            ampm.setTypeface(Fonts.get(a, Fonts.SEMIBOLD));
            ampm.setTextSize(12 * dp);
            bt.setStyle(Paint.Style.STROKE);
            bt.setStrokeWidth(1.8f * dp);
            bt.setStrokeJoin(Paint.Join.ROUND);
            bt.setStrokeCap(Paint.Cap.ROUND);

            // Top left: the active profile (photo + name), like stock Gen5W and ccNC. Tap: ccNC Profile.
            addView(new ProfileChip(a), new LinearLayout.LayoutParams(-2, -1));
            View sep = new View(a);
            sep.setBackgroundColor(Palette.alpha(Palette.TEXT, 0x33));
            LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(Math.max(1, (int) dp), (int) (26 * dp));
            sp.leftMargin = (int) (12 * dp);
            addView(sep, sp);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.leftMargin = (int) (16 * dp);
            TextView t = new TextView(a);
            t.setText(title);
            t.setTextSize(22f);
            t.setTextColor(Palette.TEXT);
            t.setTypeface(Fonts.get(a, Fonts.SEMIBOLD));
            t.setSingleLine(true);
            addView(t, lp);
        }

        private View button(String icon, OnClickListener click) {
            final Bitmap b = Art.chrome(a, icon);
            View v = new View(a) {
                private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
                private final Paint hi = new Paint(Paint.ANTI_ALIAS_FLAG);
                private final RectF r = new RectF();
                @Override protected void onDraw(Canvas c) {
                    float w = getWidth(), h = getHeight();
                    if (isPressed()) { hi.setColor(Palette.PRESS); c.drawCircle(w / 2, h / 2, Math.min(w, h) / 2 - 2, hi); }
                    if (b != null) { r.set(0, 0, w, h); c.drawBitmap(b, null, r, p); }
                }
                @Override public void setPressed(boolean pressed) { super.setPressed(pressed); invalidate(); }
            };
            v.setClickable(true);
            v.setOnClickListener(click);
            v.setLayoutParams(new LinearLayout.LayoutParams((int) (50 * dp), (int) (50 * dp)));
            return v;
        }

        @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); post(tick); }

        @Override protected void onDetachedFromWindow() { removeCallbacks(tick); super.onDetachedFromWindow(); }

        /** The clock (right end of the bar): 7 taps open the developer menu. */
        @Override public boolean onTouchEvent(MotionEvent e) {
            if (e.getX() < getWidth() - 170 * dp) return super.onTouchEvent(e);
            if (e.getActionMasked() == MotionEvent.ACTION_UP) DevMenu.tap(a);
            return true;
        }

        /** Right edge of what a child actually draws (a LinearLayout row ends at its last visible child). */
        private static float contentRight(android.view.View v) {
            if (v instanceof android.view.ViewGroup && !(v instanceof android.widget.AdapterView)) {
                android.view.ViewGroup g = (android.view.ViewGroup) v;
                float r = 0;
                for (int i = 0; i < g.getChildCount(); i++) {
                    android.view.View k = g.getChildAt(i);
                    if (k.getVisibility() == VISIBLE) r = Math.max(r, k.getLeft() + contentRight(k));
                }
                return r;
            }
            if (v instanceof android.widget.TextView) {
                android.widget.TextView t = (android.widget.TextView) v;
                float tw = t.getPaint().measureText(String.valueOf(t.getText())) + t.getPaddingLeft() + t.getPaddingRight();
                return Math.min(v.getWidth(), tw);
            }
            return v.getWidth();
        }

        @Override protected void dispatchDraw(Canvas c) {
            super.dispatchDraw(c);
            int col = Palette.TEXT;
            date.setColor(col); time.setColor(col); ampm.setColor(col); bt.setColor(col);
            float w = getWidth(), base = getHeight() / 2f + 8 * dp;
            Date now = new Date();
            Locale loc = Locale.getDefault();
            String ds = new SimpleDateFormat("EEE, MMM", loc).format(now) + ". " + new SimpleDateFormat("d", loc).format(now);
            // The date steps aside whenever the left side (title, breadcrumb, tags) reaches into its space, so a long
            // sub-menu title never runs into it.
            float half = date.measureText(ds) / 2f + 24 * dp, leftEnd = 0;
            for (int i = 0; i < getChildCount(); i++) {
                android.view.View v = getChildAt(i);
                if (v.getVisibility() == VISIBLE && v.getLeft() < w / 2f) leftEnd = Math.max(leftEnd, v.getLeft() + contentRight(v));
            }
            if (showDate && leftEnd < w / 2f - half) c.drawText(ds, w / 2f, base, date);
            boolean h24 = DateFormat.is24HourFormat(getContext());
            String t = new SimpleDateFormat(h24 ? "H:mm" : "h:mm", loc).format(now);
            String ap = h24 ? "" : new SimpleDateFormat("a", Locale.US).format(now).toUpperCase(Locale.US);
            float right = w - 24f * dp;
            float apW = ap.isEmpty() ? 0 : ampm.measureText(ap) + 3f * dp;
            float tW = time.measureText(t);
            if (!ap.isEmpty()) c.drawText(ap, right - apW + 3f * dp, base, ampm);
            c.drawText(t, right - apW - tW, base, time);
            BluetoothAdapter ad = null;
            try { ad = BluetoothAdapter.getDefaultAdapter(); } catch (Throwable ignored) {}
            if (btOn(ad)) glyph(c, right - apW - tW - 22f * dp, base - 8f * dp, 9f * dp);
        }

        private void glyph(Canvas c, float cx, float cy, float s) {
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
}
