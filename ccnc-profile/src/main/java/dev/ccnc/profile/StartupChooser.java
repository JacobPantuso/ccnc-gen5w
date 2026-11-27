package dev.ccnc.profile;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import dev.ccnc.common.Bg;
import dev.ccnc.common.Fonts;
import dev.ccnc.common.Palette;
import dev.ccnc.common.Profiles;
import dev.ccnc.common.StartupScreen;
import dev.ccnc.common.Themes;

/**
 * ccNC's start-up chooser: the greeting, the three round profiles, a countdown ring, shown
 * ON TOP of the car's own start-up profile screen (SystemUI's ConsentScreen) while that screen waits.
 *
 * The car's screen stays in charge. This is only a window above it (a system-alert window, which the window policy
 * puts under the rear camera), and it only talks to the screen with the screen's own two broadcasts:
 *   - a tap, or the default profile, moves the screen's selection (CHANGE_FOCUS_CONSENT);
 *   - a tap, or the end of the countdown, asks the screen to continue (HIDE_CONSENT_SCREEN). With ccNC's SystemUI
 *     step that request passes the same checks as the screen's own 10 s timer; the screen says no by doing nothing.
 * It never switches the profile itself and never closes the car's screen. Passwords and face recognition are the
 * car's own screens, opened by the car after it continued.
 *
 * It leaves, and the car's screen is what is shown, when: the car's screen reports it continued; the screen didn't
 * continue within a few seconds of being asked; reverse gear is reported; 45 s passed; anything goes wrong here. It
 * does not start at all unless the SystemUI in use is ccNC's patched file (StartupScreen.patchActive()), the
 * screen is waiting, and the unit started less than five minutes ago. If this process dies its window goes with it.
 */
public final class StartupChooser extends Service {
    private static final String TAG = "ccncStartup";
    private static final long MAX_SHOWN_MS = 45000;         // never over the car's screen longer than this
    private static final long SETTLE_MS = 600;              // the stock screen lays out its buttons after it is enabled
    private static final long ANSWER_MS = 4000, ANSWER_SWITCH_MS = 20000;   // the car changes profile before it continues
    private static final int MIN_SECS = 3, STOCK_SECS = 10; // shown at least 3 s; the car continues at 10 s itself

    private static final int WAITING = 0, COUNTING = 1, ASKED = 2, GONE = 3;

    private final Handler ui = new Handler();
    private final String[] names = {"Guest", "Driver 1", "Driver 2"};
    private final Bitmap[] photos = new Bitmap[3];
    private final boolean[] locked = new boolean[3];
    private int start = -1;            // the profile the car is on at this start
    private int def = -1, secs = MIN_SECS;
    /** Emulator preview (debuggable builds only): shows the chooser without the car's start-up screen. */
    private boolean preview;
    private boolean started;
    private int state = WAITING;
    private int chosen = -1;           // the profile the countdown will ask for, then the one asked for
    private long shownAt, enabledAt, countFrom, askedAt;
    private View root;
    private Cards cards;
    private TextView status;
    private boolean receiving;

    @Override public IBinder onBind(Intent i) { return null; }

    @Override public int onStartCommand(Intent i, int flags, int id) {
        if (!started) {
            started = true;
            preview = i != null && i.getBooleanExtra("preview", false)
                    && (getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0;
            new Thread(new Runnable() { @Override public void run() { prepare(); } }, "ccncStartupLoad").start();
        }
        return START_NOT_STICKY;
    }

    /** Off the UI thread: the checks, then everything the view shows. */
    private void prepare() {
        String why = null;
        try {
            if (preview) { /* emulator preview: no car checks */ }
            else if (!StartupScreen.pending()) why = "the car's start-up screen isn't waiting";
            else if (!StartupScreen.patchActive()) why = "ccNC's SystemUI step isn't active";
            else if (reverseNow()) why = "reverse gear";
            if (why == null) {
                start = Profiles.active(this);
                for (int k = 0; k < 3; k++) {
                    names[k] = Profiles.name(this, k);
                    photos[k] = ProfileStore.photo(this, k);
                    locked[k] = Profiles.needsPassword(this, k);
                }
                def = ProfileStore.defaultProfile(this);
                int auto = ProfileStore.autoAdvance(this);
                secs = auto < 0 ? STOCK_SECS : Math.max(MIN_SECS, Math.min(STOCK_SECS, auto));
            }
        } catch (Throwable t) {
            why = "couldn't prepare: " + t;
        }
        final String stop = why;
        ui.post(new Runnable() { @Override public void run() {
            if (stop != null) leave(stop); else show();
        } });
    }

    /** PgsManager.requestReverseStatus(), which the stock screen reads too; false when an app can't read it. */
    private boolean reverseNow() {
        try {
            Class<?> pk = Class.forName("com.hkmc.pgs.PgsManager");
            Object pm = pk.getConstructor(Context.class).newInstance(getApplicationContext());
            return Boolean.TRUE.equals(pk.getMethod("requestReverseStatus").invoke(pm));
        } catch (Throwable t) {
            return false;
        }
    }

    private final BroadcastReceiver reverse = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            if ("com.hkmc.intent.action.REVERSE_ON".equals(i.getAction())) leave("reverse gear");
        }
    };

    private void show() {
        try {
            if (!preview && !StartupScreen.pending()) { leave("the car's start-up screen continued meanwhile"); return; }
            Themes.apply(this);   // shared ccNC theme
            FrameLayout f = new FrameLayout(this);
            bgView = new Bg(this);
            f.addView(bgView, new FrameLayout.LayoutParams(-1, -1));
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            // Centred in the space the window gets: below the car's status bar on the car (about 630 px), the whole
            // screen in the emulator preview.
            col.setGravity(Gravity.CENTER);
            col.setClipChildren(false); col.setClipToPadding(false);   // the profile glides out of its row on the way to the chip
            f.setClipChildren(false);
            TextView title = text(greeting(), 30, Palette.TEXT, Fonts.SEMIBOLD);
            this.title = title;
            col.addView(title, new LinearLayout.LayoutParams(-1, Palette.px(this, 50)));
            cards = new Cards(this);
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-1, Palette.px(this, 300));
            cl.topMargin = Palette.px(this, 16);
            cl.bottomMargin = Palette.px(this, 8);
            col.addView(cards, cl);
            status = text("Starting…", 18, Palette.SUBTEXT, Fonts.REGULAR);
            col.addView(status, new LinearLayout.LayoutParams(-1, Palette.px(this, 30)));
            f.addView(col, new FrameLayout.LayoutParams(-1, -1));
            // The chosen profile's glide to the chip is drawn here, over the whole window: inside the row's own view it
            // would be cropped as soon as it left the row.
            glide = new View(this) {
                @Override protected void onDraw(Canvas c) { if (cards != null) cards.drawGlide(c, this); }
            };
            f.addView(glide, new FrameLayout.LayoutParams(-1, -1));
            // A system-alert window: above the car's start-up screen (same layer with ccNC's SystemUI step, added
            // later), under the rear camera. Not focusable, so the car's keys still reach the car's screen.
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(-1, -1, android.os.Build.VERSION.SDK_INT >= 26 ? 2038 : WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
                            // The whole screen, the status bar's strip included: this window's layer (17) is above
                            // the status bar (15), it was only being laid out below it.
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                            | WindowManager.LayoutParams.FLAG_FULLSCREEN, PixelFormat.TRANSLUCENT);
            lp.setTitle("ccNC Start-Up Chooser");
            lp.gravity = Gravity.TOP | Gravity.LEFT;
            lp.x = 0; lp.y = 0;
            android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
            lp.width = dm.widthPixels; lp.height = Math.max(dm.heightPixels, 720);
            lp.windowAnimations = 0;
            ((WindowManager) getSystemService(WINDOW_SERVICE)).addView(f, lp);
            root = f;
            IntentFilter rf = new IntentFilter("com.hkmc.intent.action.REVERSE_ON");
            registerReceiver(reverse, rf);
            receiving = true;
            shownAt = SystemClock.uptimeMillis();
            chosen = pick();
            for (int i = 0; i < 3; i++) { slotFrom[i] = slotTo[i] = slot(i, chosen); }
            slotsSet = true;
            unfoldAt = shownAt + UNFOLD_DELAY_MS;
            f.setAlpha(0f);
            f.animate().alpha(1f).setDuration(FADE_MS).start();
            Log.i(TAG, "start-up chooser shown: car profile " + start + ", default " + def + ", countdown " + secs + " s");
            ui.post(tick);
        } catch (Throwable t) {
            leave("couldn't show: " + t);
        }
    }

    /** ccNC's default profile, unless it has a password (the car asks for that on its own screen): then the car's. */
    // ---- motion, after ccNC's own profile screen (userprofilehmi): the selected profile alone in the middle and a
    // little lower, then the row unfolds (the others grow in beside it, names fade in); taps slide the row so the
    // chosen profile is in the middle (400 ms) with a pulse of its ring; continuing shrinks it to 80% while it glides
    // towards the profile chip at the top left and everything else fades (1167 ms, ease in-out expo).
    static final long FADE_MS = 333, UNFOLD_DELAY_MS = 450, UNFOLD_MS = 450, SWAP_MS = 400, PULSE_UP_MS = 150,
            PULSE_DOWN_MS = 333, EXIT_MS = 1167, EXIT_FADE_MS = 500;
    private long unfoldAt, swapAt = -1, pulseAt = -1, exitAt = -1;
    private final float[] slotFrom = new float[3], slotTo = new float[3];
    private boolean slotsSet;
    private View title, bgView, glide;

    /** -1 left, 0 middle, 1 right: the chosen profile is always in the middle, as on ccNC. */
    static int slot(int i, int chosen) {
        int o = ((i - chosen) % 3 + 3) % 3;
        return o == 0 ? 0 : o == 1 ? 1 : -1;
    }

    static float clamp01(float v) { return v < 0 ? 0 : v > 1 ? 1 : v; }
    static float inOutCubic(float t) { return t < 0.5f ? 4 * t * t * t : 1 - (float) Math.pow(-2 * t + 2, 3) / 2; }
    static float inOutExpo(float t) {
        if (t <= 0) return 0; if (t >= 1) return 1;
        return t < 0.5f ? (float) Math.pow(2, 20 * t - 10) / 2 : (2 - (float) Math.pow(2, -20 * t + 10)) / 2;
    }

    private float since(long at) { return at < 0 ? -1 : SystemClock.uptimeMillis() - at; }

    /** Slides the row so profile {@code id} is in the middle. */
    private void moveTo(int id) {
        for (int i = 0; i < 3; i++) {
            float cur = slotsSet ? slotNow(i) : slot(i, id);
            slotFrom[i] = cur;
            slotTo[i] = slot(i, id);
        }
        slotsSet = true;
        swapAt = SystemClock.uptimeMillis();
    }

    /** Where profile {@code i} is drawn now, in slots (-1, 0, 1). */
    private float slotNow(int i) {
        if (!slotsSet) return slot(i, chosen);
        float t = swapAt < 0 ? 1 : clamp01(since(swapAt) / SWAP_MS);
        return slotFrom[i] + (slotTo[i] - slotFrom[i]) * inOutCubic(t);
    }

    private int pick() {
        return def >= 0 && def < 3 && !locked[def] ? def : start >= 0 && start < 3 ? start : 0;
    }

    private final Runnable tick = new Runnable() { @Override public void run() {
        if (state == GONE) return;
        try {
            long now = SystemClock.uptimeMillis();
            if (exitAt >= 0) {                                       // playing the hand-over to Home
                if (now - exitAt >= EXIT_MS) { leave("the car continued" + (chosen >= 0 ? " (asked for profile " + chosen + ")" : "")); return; }
                cards.invalidate();
                if (glide != null) glide.invalidate();
                ui.postDelayed(this, 16);
                return;
            }
            if ((preview ? state == ASKED && now - askedAt > 900 : StartupScreen.confirmed())) {
                exitAt = now;
                if (title != null) title.animate().alpha(0f).setDuration(EXIT_FADE_MS).start();
                if (status != null) status.animate().alpha(0f).setDuration(EXIT_FADE_MS).start();
                // Home shows through as the profile lands on its chip (the window is opaque only through its background)
                if (bgView != null) bgView.animate().alpha(0f).setStartDelay(EXIT_MS / 3).setDuration(EXIT_MS * 2 / 3).start();
                cards.invalidate();
                ui.postDelayed(this, 16);
                return;
            }
            if (now - shownAt > MAX_SHOWN_MS) { leave("still waiting after " + MAX_SHOWN_MS / 1000 + " s"); return; }
            if (state == WAITING) {
                if (enabledAt == 0 && (preview || StartupScreen.enabled())) enabledAt = now;
                if (enabledAt != 0 && now - enabledAt >= SETTLE_MS) {
                    if (chosen != start) StartupScreen.select(StartupChooser.this, chosen);
                    countFrom = now;
                    state = COUNTING;
                }
            } else if (state == COUNTING) {
                long left = secs * 1000L - (now - countFrom);
                if (left <= 0) ask(chosen, false);
                else say("Continuing as " + names[chosen] + " in " + ((left + 999) / 1000) + "s. Tap a profile to choose.");
            } else if (state == ASKED) {
                if (now - askedAt > (chosen != start ? ANSWER_SWITCH_MS : ANSWER_MS)) { leave("the car didn't continue: its own screen takes over"); return; }
            }
            cards.invalidate();
            ui.postDelayed(this, 16);
        } catch (Throwable t) {
            leave("error: " + t);
        }
    } };

    /** A tap or the end of the countdown: selection first, then the request; the car's screen decides. */
    private void ask(int id, boolean tapped) {
        if (state != COUNTING || id < 0 || id > 2) return;
        if (id != chosen) moveTo(id);
        chosen = id;
        pulseAt = SystemClock.uptimeMillis();
        if (!preview && (tapped || id != start)) StartupScreen.select(this, id);
        if (preview) Profiles.switchTo(this, id, null);   // the emulator demo: the car would switch on confirm
        if (!preview && !StartupScreen.askContinue(this)) { leave("ccNC's SystemUI step isn't active"); return; }
        askedAt = SystemClock.uptimeMillis();
        state = ASKED;
        say(locked[id] ? "Enter " + names[id] + "'s password on the screen that opens" : "Continuing as " + names[id] + "…");
        Log.i(TAG, "asked the car to continue as profile " + id + (tapped ? " (tap)" : " (countdown)"));
    }

    private String said = "";

    private void say(String s) {
        if (s.equals(said)) return;
        said = s;
        status.setText(s);
    }

    private void leave(String why) {
        if (state == GONE && root == null) { stopSelf(); return; }
        state = GONE;
        Log.i(TAG, "start-up chooser closed: " + why);
        ui.removeCallbacks(tick);
        if (receiving) { try { unregisterReceiver(reverse); } catch (Throwable ignored) { } receiving = false; }
        if (root != null) {
            try { ((WindowManager) getSystemService(WINDOW_SERVICE)).removeView(root); } catch (Throwable ignored) { }
            root = null;
        }
        stopSelf();
    }

    @Override public void onDestroy() {
        if (state != GONE || root != null) leave("service stopped");
        super.onDestroy();
    }

    /** Same wording as ccNC Home's welcome page. */
    private static String greeting() {
        int hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        return hour < 5 ? "Good night" : hour < 12 ? "Good morning" : hour < 18 ? "Good afternoon" : "Good evening";
    }

    private TextView text(String t, float size, int color, int face) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextSize(size);
        v.setTextColor(color);
        v.setTypeface(Fonts.get(this, face));
        v.setGravity(Gravity.CENTER);
        return v;
    }

    /** Three round profiles; the one that will be asked for is large, with the countdown ring (or a turning ring once asked). */
    private final class Cards extends View {
        private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG), img = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG),
                ring = new Paint(Paint.ANTI_ALIAS_FLAG), press = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private final float[] cx = {-240, 0, 240};
        private final BitmapShader[] shaders = new BitmapShader[3];
        private final Matrix m = new Matrix();
        private int down = -1;

        Cards(Context c) {
            super(c);
            setClickable(true);
            setSoundEffectsEnabled(false);
            ring.setStyle(Paint.Style.STROKE);
            ring.setStrokeCap(Paint.Cap.ROUND);
            for (int i = 0; i < 3; i++) if (photos[i] != null) shaders[i] = new BitmapShader(photos[i], Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        }

        private float k() { return Math.min(getWidth() / 1920f, getHeight() / 400f); }
        private float my() { return getHeight() * 0.40f; }

        private int hit(float x, float y) {
            float k = k(), mx = getWidth() / 2f;
            for (int i = 0; i < 3; i++) if (Math.abs(x - (mx + 240f * slotNow(i) * k)) < 120 * k && Math.abs(y - my()) < 150 * k) return i;
            return -1;
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: down = state == COUNTING ? hit(e.getX(), e.getY()) : -1; invalidate(); return true;
                case MotionEvent.ACTION_MOVE: if (down >= 0 && hit(e.getX(), e.getY()) != down) { down = -1; invalidate(); } return true;
                case MotionEvent.ACTION_UP:
                    int i = down;
                    down = -1;
                    invalidate();
                    if (i >= 0 && hit(e.getX(), e.getY()) == i) ask(i, true);
                    return true;
                case MotionEvent.ACTION_CANCEL: down = -1; invalidate(); return true;
            }
            return true;
        }

        /** The chosen profile on its way to the chip, drawn on the full-window layer {@code on}. */
        void drawGlide(Canvas c, View on) {
            if (exitAt < 0 || chosen < 0 || chosen > 2) return;
            long now = SystemClock.uptimeMillis();
            float exit = inOutExpo(clamp01((now - exitAt) / (float) EXIT_MS));
            int[] a = new int[2], b = new int[2];
            getLocationInWindow(a); on.getLocationInWindow(b);
            float k = k(), ox = a[0] - b[0], oy = a[1] - b[1];
            float x0 = ox + getWidth() / 2f + 240f * slotNow(chosen) * k, y0 = oy + my();
            // ccNC Home's profile chip, in screen pixels: left margin 20dp, photo 36dp wide starting 4dp in, in a
            // 64dp-high top bar (HomeActivity / ProfileChip). The window starts at the screen's top left.
            float dp = getResources().getDisplayMetrics().density;
            float tx = (20 + 4 + 18) * dp - b[0], ty = 32 * dp - b[1], endScale = 36 * dp / (220f * k);
            float x = x0 + (tx - x0) * exit, y = y0 + (ty - y0) * exit;
            float scale = 1f + (endScale - 1f) * exit;
            float alpha = 1f - Math.max(0f, exit - 0.92f) / 0.08f;   // lands, then hands over to the chip
            if (alpha <= 0.01f || scale <= 0.01f) return;
            float d = 220f * k * scale;
            Bitmap bm = photos[chosen];
            if (bm != null && shaders[chosen] != null) {
                float sc = d / Math.min(bm.getWidth(), bm.getHeight());
                m.setScale(sc, sc);
                m.postTranslate(x - bm.getWidth() * sc / 2f, y - bm.getHeight() * sc / 2f);
                shaders[chosen].setLocalMatrix(m);
                img.setShader(shaders[chosen]);
                img.setAlpha((int) (255 * alpha));
                c.drawCircle(x, y, d / 2f, img);
                img.setShader(null);
            } else {
                img.setColor(0x33FFFFFF);
                c.drawCircle(x, y, d / 2f, img);
            }
        }

        @Override protected void onDraw(Canvas c) {
            float k = k(), mx = getWidth() / 2f, my = my();
            long now = SystemClock.uptimeMillis();
            // Opening: until the row unfolds, only the chosen profile shows, 35 px lower; then the others grow in.
            float unfold = inOutCubic(clamp01((now - unfoldAt) / (float) UNFOLD_MS));
            // Hand-over to Home: the chosen profile shrinks to 80% and glides to the profile chip at the top left.
            float exit = exitAt < 0 ? 0 : inOutExpo(clamp01((now - exitAt) / (float) EXIT_MS));
            float exitFade = exitAt < 0 ? 1 : 1 - clamp01((now - exitAt) / (float) EXIT_FADE_MS);
            int[] order = new int[3];   // the chosen profile last, so it's drawn on top
            for (int i = 0, n = 0; i < 3; i++) if (i != chosen) order[n++] = i;
            order[2] = chosen >= 0 ? chosen : 2;
            for (int i : order) {
                boolean on = i == chosen;
                float slot = slotNow(i);
                float base = on ? 220f : 180f;
                float d = base * k * (i == down ? 0.94f : 1f);
                float x = mx + 240f * slot * k, y = my;
                float alpha = on ? 1f : 0.7f, scale = 1f;
                if (on) y += 35f * k * (1 - unfold);
                else { scale = unfold; alpha *= unfold; }
                if (exitAt >= 0) {
                    if (on) continue;   // drawn by the glide layer
                    alpha *= exitFade;
                }
                // tap: the ring widens and settles; asked: the photo breathes until the car continues
                if (on && state == ASKED && exitAt < 0) alpha *= 0.6f + 0.4f * (float) Math.abs(Math.cos(Math.PI * ((now - askedAt) % 500) / 500.0));
                if (alpha <= 0.01f || scale <= 0.01f) continue;
                d *= scale;
                Bitmap b = photos[i];
                if (b != null && shaders[i] != null) {
                    float sc = d / Math.min(b.getWidth(), b.getHeight());
                    m.setScale(sc, sc);
                    m.postTranslate(x - b.getWidth() * sc / 2f, y - b.getHeight() * sc / 2f);
                    shaders[i].setLocalMatrix(m);
                    img.setShader(shaders[i]);
                    img.setAlpha((int) (255 * alpha));
                    c.drawCircle(x, y, d / 2f, img);
                    img.setShader(null);
                } else {
                    img.setColor(0x33FFFFFF);
                    img.setAlpha((int) (0x33 * alpha));
                    c.drawCircle(x, y, d / 2f, img);
                }
                if (i == down) {
                    press.setColor(Palette.PRESS);
                    c.drawCircle(x, y, d / 2f, press);
                }
                if (on && exitAt < 0) {
                    float p = since(pulseAt), widen = 0;
                    if (p >= 0 && p < PULSE_UP_MS) widen = inOutCubic(p / PULSE_UP_MS);
                    else if (p >= PULSE_UP_MS && p < PULSE_UP_MS + PULSE_DOWN_MS) widen = 1 - inOutCubic((p - PULSE_UP_MS) / PULSE_DOWN_MS);
                    float f = (238f + 22f * widen) * k * scale;
                    r.set(x - f / 2, y - f / 2, x + f / 2, y + f / 2);
                    ring.setStrokeWidth(7f * k);
                    ring.setColor(Palette.alpha(Palette.TEXT, (int) (0x33 * unfold)));
                    c.drawArc(r, 0, 360, false, ring);
                    ring.setColor(Palette.ACCENT);
                    if (state == COUNTING) {          // a full ring that runs backwards to nothing
                        float left = Math.max(0f, 1f - (now - countFrom) / (secs * 1000f));
                        c.drawArc(r, -90, 360f * left, false, ring);
                    } else if (state == ASKED) {      // turning until the car continues
                        c.drawArc(r, (now % 1200) / 1200f * 360f, 270, false, ring);
                    }
                }
                float nameAlpha = (on ? 1f : 0.7f) * unfold * exitFade;
                if (nameAlpha <= 0.01f) continue;
                text.setTypeface(Fonts.get(getContext(), on ? Fonts.SEMIBOLD : Fonts.REGULAR));
                text.setTextSize((on ? 38f : 30f) * k);
                text.setColor(on ? Palette.TEXT : Palette.SUBTEXT);
                text.setAlpha((int) (255 * nameAlpha));
                text.setTextAlign(Paint.Align.CENTER);
                c.drawText(names[i], x, y + 170f * k * scale, text);
                if (locked[i]) {
                    text.setTextSize(22f * k);
                    text.setColor(Palette.SUBTEXT);
                    text.setAlpha((int) (255 * nameAlpha));
                    c.drawText("Password", x, y + 206f * k * scale, text);
                }
            }
        }
    }
}
