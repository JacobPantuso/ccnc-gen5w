package dev.ccnc.common;

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
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import android.view.Choreographer;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;


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
 *
 * Runs in ccNC Home's process, which is already up at start-up (nothing has to start first), or in ccNC Profile's.
 */
public final class StartupChooser {
    private static final String TAG = "ccncStartup";
    private static final long MAX_SHOWN_MS = 45000;         // never over the car's screen longer than this
    private static final long SETTLE_MS = 600;              // the stock screen lays out its buttons after it is enabled
    private static final long ANSWER_MS = 4000, ANSWER_SWITCH_MS = 20000;   // the car changes profile before it continues
    private static final int MIN_SECS = 3, STOCK_SECS = 10; // shown at least 3 s; the car continues at 10 s itself

    private static final int WAITING = 0, COUNTING = 1, ASKED = 2, GONE = 3;

    private final Handler ui;
    private final String[] names = {"Guest", "Driver 1", "Driver 2"};
    private final Bitmap[] photos = new Bitmap[3];
    private final boolean[] locked = new boolean[3];
    private int start = -1;            // the profile the car is on at this start
    private int def = -1, secs = MIN_SECS;
    /** Emulator preview (debuggable builds only): shows the chooser without the car's start-up screen. */
    private final boolean preview;
    private int state = WAITING;
    private int chosen = -1;           // the profile the countdown will ask for, then the one asked for
    private long shownAt, enabledAt, countFrom, askedAt;
    private View root;
    private Cards cards;
    private TextView status;
    private boolean receiving;

    /** Where the chooser gets ccNC Profile's photos and settings: ccNC Profile's own store, or Home's copy of them. */
    public interface Data {
        Bitmap photo(Context c, int id);
        int defaultProfile(Context c);
        /** Seconds; -1 = Off (the car's own 10 s). */
        int autoAdvance(Context c);
    }

    private static StartupChooser current;

    /** True while a chooser is up (or loading) in this process. */
    public static synchronized boolean running() { return current != null; }

    /**
     * Opens the chooser in this process, from any thread. It runs on a UI thread of its own (its window belongs to
     * that thread), so the app's main thread building its own screen at start-up doesn't hold up its frames.
     * {@code preview}: emulator only (debuggable builds), without the car's start-up screen. {@code closed} runs on
     * the app's main thread once it has gone. False if one is already up.
     */
    public static synchronized boolean open(Context c, boolean preview, Data data, Runnable closed) {
        if (current != null) return false;
        Context app = c.getApplicationContext();
        boolean dbg = (app.getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        android.os.HandlerThread t = new android.os.HandlerThread("ccncStartupChooser", android.os.Process.THREAD_PRIORITY_DISPLAY);
        t.start();
        final StartupChooser sc = new StartupChooser(app, preview && dbg, data, closed, t.getLooper());
        current = sc;
        sc.ui.post(new Runnable() { @Override public void run() { sc.begin(); } });
        return true;
    }

    /** Closes it now (its process or service is going away). */
    public static synchronized void closeNow(final String why) {
        final StartupChooser sc = current;
        if (sc != null) sc.ui.post(new Runnable() { @Override public void run() { sc.leave(why); } });
    }

    private final Context ctx;
    private final Data data;
    private final Runnable closed;

    private static final Handler main = new Handler(android.os.Looper.getMainLooper());

    private StartupChooser(Context ctx, boolean preview, Data data, Runnable closed, android.os.Looper looper) {
        this.ui = new Handler(looper);
        this.ctx = ctx;
        this.preview = preview;
        this.data = data;
        this.closed = closed;
    }

    private void begin() {
        // The cover (background + greeting) goes up at once, so the car's own start-up screen isn't seen while the
        // profiles load; the profiles and the countdown follow in show().
        if (preview || StartupScreen.pending()) cover();
        new Thread(new Runnable() { @Override public void run() { prepare(); } }, "ccncStartupLoad").start();
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
                start = Profiles.active(ctx);
                for (int k = 0; k < 3; k++) {
                    names[k] = Profiles.name(ctx, k);
                    photos[k] = data.photo(ctx, k);
                    preRounds[k] = makeRound(photos[k], roundSize());
                    locked[k] = Profiles.needsPassword(ctx, k);
                }
                def = data.defaultProfile(ctx);
                int auto = data.autoAdvance(ctx);
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
            Object pm = pk.getConstructor(Context.class).newInstance(ctx);
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

    private FrameLayout frame;
    private LinearLayout col;

    private View bgWin;
    private WindowManager.LayoutParams bgLp;
    private boolean bgFading;

    /**
     * A system-alert window: above the car's start-up screen (same layer with ccNC's SystemUI step, added later),
     * under the rear camera. Not focusable, so the car's keys still reach the car's screen. The whole screen, the
     * status bar's strip included: this window's layer (17) is above the status bar (15).
     */
    private WindowManager.LayoutParams params(int format, String title) {
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(-1, -1, android.os.Build.VERSION.SDK_INT >= 26 ? 2038 : WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_FULLSCREEN, format);
        lp.setTitle(title);
        lp.gravity = Gravity.TOP | Gravity.LEFT;
        lp.x = 0; lp.y = 0;
        android.util.DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
        lp.width = dm.widthPixels; lp.height = Math.max(dm.heightPixels, 720);
        lp.windowAnimations = 0;
        return lp;
    }

    /**
     * Hand-over: the background window goes with the system's own fade (the window manager animates it; this app
     * draws nothing for it). Called at the start of the hand-over to set the fade, then again to remove the window.
     */
    private void fadeBackground(boolean remove) {
        if (bgWin == null) return;
        WindowManager wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
        try {
            if (!remove) {
                if (bgFading) return;
                bgFading = true;
                bgLp.windowAnimations = android.R.style.Animation_Toast;   // fade out (about 0.5 s)
                wm.updateViewLayout(bgWin, bgLp);
            } else {
                wm.removeView(bgWin);
                bgWin = null;
            }
        } catch (Throwable t) {
            Log.w(TAG, "background fade", t);
        }
    }

    /** Background and greeting, in their windows above the car's start-up screen; nothing to tap yet. */
    private void cover() {
        try {
            Themes.apply(ctx);   // shared ccNC theme
            FrameLayout f = new FrameLayout(ctx);
            bgView = new Bg(ctx);
            col = new LinearLayout(ctx);
            col.setOrientation(LinearLayout.VERTICAL);
            // Centred in the space the window gets: below the car's status bar on the car (about 630 px), the whole
            // screen in the emulator preview.
            col.setGravity(Gravity.CENTER);
            col.setClipChildren(false); col.setClipToPadding(false);   // the profile glides out of its row on the way to the chip
            f.setClipChildren(false);
            TextView title = text(greeting(), 30, Palette.TEXT, Fonts.SEMIBOLD);
            this.title = title;
            col.addView(title, new LinearLayout.LayoutParams(-1, Palette.px(ctx, 50)));
            f.addView(col, new FrameLayout.LayoutParams(-1, -1));
            frame = f;
            WindowManager wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
            // Two windows. The background alone, opaque and drawn once: nothing under it has to be blended any more
            // (a see-through full-screen window over the car's screen was blended on every frame, and redrawing the
            // background's five full-screen layers with each frame of motion was most of the head unit's GPU time).
            // Above it, the greeting, profiles and status: only they redraw as they move.
            bgLp = params(PixelFormat.OPAQUE, "ccNC Start-Up Background");
            // (Touchable: the chooser's window above it takes every touch anyway, and Android 12+ draws a not-touchable
            // overlay at most 80% opaque.)
            try {
                wm.addView(bgView, bgLp);
                bgWin = bgView;
            } catch (Throwable t) {
                Log.w(TAG, "background window", t);
                f.addView(bgView, 0, new FrameLayout.LayoutParams(-1, -1));   // as one window, as before
            }
            wm.addView(f, params(PixelFormat.TRANSLUCENT, "ccNC Start-Up Chooser"));
            root = f;
            IntentFilter rf = new IntentFilter("com.hkmc.intent.action.REVERSE_ON");
            ctx.registerReceiver(reverse, rf);
            receiving = true;
        } catch (Throwable t) {
            Log.w(TAG, "cover", t);
        }
    }

    private void show() {
        try {
            if (!preview && !StartupScreen.pending()) { leave("the car's start-up screen continued meanwhile"); return; }
            if (root == null) cover();
            if (root == null) { leave("couldn't show the window"); return; }
            final FrameLayout f = frame;
            final LinearLayout col = this.col;
            // the profiles and status join the greeting, fading in together
            final LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setGravity(Gravity.CENTER);
            row.setClipChildren(false); row.setClipToPadding(false);
            cards = new Cards(ctx);
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-1, Palette.px(ctx, 300));
            cl.topMargin = Palette.px(ctx, 16);
            cl.bottomMargin = Palette.px(ctx, 8);
            row.addView(cards, cl);
            status = text("Starting…", 18, Palette.SUBTEXT, Fonts.REGULAR);
            row.addView(status, new LinearLayout.LayoutParams(-1, Palette.px(ctx, 30)));
            col.addView(row, new LinearLayout.LayoutParams(-1, -2));
            // The chosen profile's glide to the chip is drawn here, over the whole window: inside the row's own view it
            // would be cropped as soon as it left the row.
            glide = new View(ctx) {
                @Override protected void onDraw(Canvas c) { if (cards != null) cards.drawGlide(c, this); }
            };
            f.addView(glide, new FrameLayout.LayoutParams(-1, -1));
            shownAt = SystemClock.uptimeMillis();
            chosen = pick();
            for (int i = 0; i < 3; i++) { slotFrom[i] = slotTo[i] = slot(i, chosen); }
            slotsSet = true;
            unfoldAt = Long.MAX_VALUE;   // set once the car's screen is ready (tick): before that the start-up video covers it
            // The background is there from the first frame (a fade of the whole window let the car's own start-up screen
            // show through for a moment: a flicker); only the greeting, profiles and status fade in on top of it.
            row.setAlpha(0f);
            row.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            row.animate().alpha(1f).setDuration(FADE_MS).withEndAction(new Runnable() {
                @Override public void run() { row.setLayerType(View.LAYER_TYPE_NONE, null); }
            }).start();
            Log.i(TAG, "start-up chooser shown: car profile " + start + ", default " + def + ", countdown " + secs + " s");
            next();
        } catch (Throwable t) {
            leave("couldn't show: " + t);
        }
    }

    /** ccNC's default profile, unless it has a password (the car asks for that on its own screen): then the car's. */
    // ---- motion, after ccNC's own profile screen (userprofilehmi): the selected profile alone in the middle and a
    // little lower, then the row unfolds (the others grow in beside it, names fade in); taps slide the row so the
    // chosen profile is in the middle (400 ms) with a pulse of its ring; continuing shrinks it to 80% while it glides
    // towards the profile chip at the top left and everything else fades (1167 ms, ease in-out expo).
    // Shorter than ccNC's own (unfold 450, swap 400, hand-over 1167 ms): the head unit is at its busiest at start-up.
    static final long FADE_MS = 250, UNFOLD_DELAY_MS = 150, UNFOLD_MS = 300, SWAP_MS = 300, PULSE_UP_MS = 120,
            PULSE_DOWN_MS = 250, EXIT_MS = 750, EXIT_FADE_MS = 350;
    private long unfoldAt, swapAt = -1, pulseAt = -1, exitAt = -1;
    /**
     * Motion time (ms): the wall clock, except that one tick adds at most MAX_STEP_MS. When the head unit is too busy
     * to draw (the car closes its start-up screen, switches profile and brings Home up at the same moment; 62 frames
     * were skipped on the car), the motion waits and then plays in full instead of jumping to its end.
     */
    static final long MAX_STEP_MS = 40;
    private long motion, lastTick;
    private boolean unfoldStarted;

    private long motionNow() { return motion; }

    private long realExitAt;

    /** Greeting and status fade first; the background fades over the rest so Home shows through as the profile lands. */
    private void applyExitFades(long e) {
        float f = 1 - clamp01(e / (float) EXIT_FADE_MS);
        if (title != null) title.setAlpha(f);
        if (status != null) status.setAlpha(f);
        if (bgWin != null) { if (e >= EXIT_MS / 3) fadeBackground(true); }
        else if (bgView != null) bgView.setAlpha(1 - clamp01((e - EXIT_MS / 3f) / (EXIT_MS * 2 / 3f)));   // one window
    }

    /** Ticks run once per display frame (Choreographer), so every frame moves by its own frame time: no judder. */
    private final Choreographer.FrameCallback frameCb = new Choreographer.FrameCallback() {
        @Override public void doFrame(long ns) {
            frameMs = ns / 1000000L;
            countFrame(frameMs);
            tick.run();
        }
    };
    private long frameMs;

    private void next() { Choreographer.getInstance().postFrameCallback(frameCb); }

    // Frame timing for the log (the car's log only reports stalls of 30 frames or more).
    private long lastFrameMs;
    private int frames, slow, slowExit, framesExit;
    private long worst, worstExit;

    private void countFrame(long ms) {
        if (lastFrameMs != 0) {
            long gap = ms - lastFrameMs;
            boolean ex = exitAt >= 0;
            if (ex) framesExit++; else frames++;
            if (gap > 34) { if (ex) slowExit++; else slow++; }   // at least one frame missed
            if (ex) worstExit = Math.max(worstExit, gap); else worst = Math.max(worst, gap);
        }
        lastFrameMs = ms;
    }

    private void stepMotion() {
        long now = frameMs != 0 ? frameMs : SystemClock.uptimeMillis();
        if (lastTick != 0) motion += Math.min(MAX_STEP_MS, Math.max(0, now - lastTick));
        lastTick = now;
    }
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

    private float since(long at) { return at < 0 ? -1 : motionNow() - at; }

    /** Slides the row so profile {@code id} is in the middle. */
    private void moveTo(int id) {
        for (int i = 0; i < 3; i++) {
            float cur = slotsSet ? slotNow(i) : slot(i, id);
            slotFrom[i] = cur;
            slotTo[i] = slot(i, id);
        }
        slotsSet = true;
        swapAt = motionNow();
    }

    /** Where profile {@code i} is drawn now, in slots (-1, 0, 1). */
    private float slotNow(int i) {
        if (!slotsSet) return slot(i, chosen);
        float t = swapAt < 0 ? 1 : clamp01(since(swapAt) / SWAP_MS);
        return slotFrom[i] + (slotTo[i] - slotFrom[i]) * inOutCubic(t);
    }

    /** Round photos made in prepare() (off the UI thread), at the size the row shows the chosen profile. */
    private final Bitmap[] preRounds = new Bitmap[3];

    /** The chosen profile's diameter on this screen: 220 x the row's scale (row 300dp high, design 1920 x 400). */
    private int roundSize() {
        android.util.DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
        float k = Math.min(dm.widthPixels / 1920f, 300 * dm.density / 400f);
        return Math.max(8, Math.round(220f * k));
    }

    static Bitmap makeRound(Bitmap b, int size) {
        if (b == null) return null;
        Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        Matrix mm = new Matrix();
        float sc = size / (float) Math.min(b.getWidth(), b.getHeight());
        mm.setScale(sc, sc);
        mm.postTranslate((size - b.getWidth() * sc) / 2f, (size - b.getHeight() * sc) / 2f);
        BitmapShader sh = new BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        sh.setLocalMatrix(mm);
        p.setShader(sh);
        c.drawCircle(size / 2f, size / 2f, size / 2f, p);
        return out;
    }

    private int pick() {
        return def >= 0 && def < 3 && !locked[def] ? def : start >= 0 && start < 3 ? start : 0;
    }

    private final Runnable tick = new Runnable() { @Override public void run() {
        if (state == GONE) return;
        try {
            long now = SystemClock.uptimeMillis();
            stepMotion();
            if (!unfoldStarted && (preview || StartupScreen.enabled())) { unfoldStarted = true; unfoldAt = motionNow() + UNFOLD_DELAY_MS; }
            if (exitAt >= 0) {                                       // playing the hand-over to Home
                long e = motionNow() - exitAt;
                applyExitFades(e);
                if (e >= EXIT_MS || now - realExitAt > 4000) { leave("the car continued" + (chosen >= 0 ? " (asked for profile " + chosen + ")" : "")); return; }
                cards.invalidate();
                if (glide != null) glide.invalidate();
                next();
                return;
            }
            if ((preview ? state == ASKED && now - askedAt > 900 : StartupScreen.confirmed())) {
                exitAt = motionNow();
                realExitAt = now;
                // Faded as cached layers (cheap on the head unit's GPU), driven by the motion clock in applyExitFades.
                fadeBackground(false);
                for (View v : new View[]{title, status, bgWin == null ? bgView : null}) if (v != null) v.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                cards.invalidate();
                next();
                return;
            }
            if (now - shownAt > MAX_SHOWN_MS) { leave("still waiting after " + MAX_SHOWN_MS / 1000 + " s"); return; }
            if (state == WAITING) {
                if (enabledAt == 0 && (preview || StartupScreen.enabled())) enabledAt = now;
                if (enabledAt != 0 && now - enabledAt >= SETTLE_MS) {
                    if (chosen != start) StartupScreen.select(ctx, chosen);
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
            next();
        } catch (Throwable t) {
            leave("error: " + t);
        }
    } };

    /** A tap or the end of the countdown: selection first, then the request; the car's screen decides. */
    private void ask(int id, boolean tapped) {
        if (state != COUNTING || id < 0 || id > 2) return;
        if (id != chosen) moveTo(id);
        chosen = id;
        pulseAt = motionNow();
        if (!preview && (tapped || id != start)) StartupScreen.select(ctx, id);
        if (preview) Profiles.switchTo(ctx, id, null);   // the emulator demo: the car would switch on confirm
        if (!preview && !StartupScreen.askContinue(ctx)) { leave("ccNC's SystemUI step isn't active"); return; }
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
        if (state == GONE && root == null) return;   // (on the chooser's own thread, like everything here)
        state = GONE;
        Log.i(TAG, "start-up chooser closed: " + why);
        ui.removeCallbacks(tick);
        try { Choreographer.getInstance().removeFrameCallback(frameCb); } catch (Throwable ignored) { }
        Log.i(TAG, "start-up chooser frames: " + frames + " (" + slow + " late, worst " + worst + " ms); hand-over "
                + framesExit + " (" + slowExit + " late, worst " + worstExit + " ms)");
        if (bgWin != null) {
            try { ((WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE)).removeView(bgWin); } catch (Throwable ignored) { }
            bgWin = null;
        }
        if (receiving) { try { ctx.unregisterReceiver(reverse); } catch (Throwable ignored) { } receiving = false; }
        if (root != null) {
            try { ((WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE)).removeView(root); } catch (Throwable ignored) { }
            root = null;
        }
        synchronized (StartupChooser.class) { if (current == this) current = null; }
        // The thread ends once the window's last messages are through; the app hears about it on its main thread.
        ui.post(new Runnable() { @Override public void run() { ui.getLooper().quit(); } });
        if (closed != null) main.post(new Runnable() { @Override public void run() {
            try { closed.run(); } catch (Throwable t) { Log.w(TAG, "closed", t); }
        } });
    }


    /** Same wording as ccNC Home's welcome page. */
    private static String greeting() {
        int hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        return hour < 5 ? "Good night" : hour < 12 ? "Good morning" : hour < 18 ? "Good afternoon" : "Good evening";
    }

    private TextView text(String t, float size, int color, int face) {
        TextView v = new TextView(ctx);
        v.setText(t);
        v.setTextSize(size);
        v.setTextColor(color);
        v.setTypeface(Fonts.get(ctx, face));
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
        /** Each profile photo cut round once at the largest size it is shown (220 x scale): a frame only stamps it. */
        private final Bitmap[] rounds = new Bitmap[3];
        private final Rect src = new Rect();
        private final RectF dst = new RectF();

        private Bitmap round(int i) {
            if (rounds[i] == null && preRounds[i] != null) rounds[i] = preRounds[i];
            if (rounds[i] != null || photos[i] == null || shaders[i] == null) return rounds[i];
            int size = Math.max(8, Math.round(220f * k()));
            Bitmap b = photos[i];
            Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(out);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            Matrix mm = new Matrix();
            float sc = size / (float) Math.min(b.getWidth(), b.getHeight());
            mm.setScale(sc, sc);
            mm.postTranslate((size - b.getWidth() * sc) / 2f, (size - b.getHeight() * sc) / 2f);
            BitmapShader sh = new BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            sh.setLocalMatrix(mm);
            p.setShader(sh);
            c.drawCircle(size / 2f, size / 2f, size / 2f, p);
            rounds[i] = out;
            return out;
        }

        @Override protected void onSizeChanged(int w, int h, int ow, int oh) { for (int i = 0; i < 3; i++) rounds[i] = null; }

        /** The round photo {@code i} centred at (x, y), diameter d. */
        private void photo(Canvas c, int i, float x, float y, float d, float alpha) {
            Bitmap r = round(i);
            if (r == null) {
                img.setShader(null);
                img.setColor(0x33FFFFFF);
                img.setAlpha((int) (0x33 * alpha));
                c.drawCircle(x, y, d / 2f, img);
                return;
            }
            src.set(0, 0, r.getWidth(), r.getHeight());
            dst.set(x - d / 2f, y - d / 2f, x + d / 2f, y + d / 2f);
            img.setShader(null);
            img.setAlpha((int) (255 * alpha));
            c.drawBitmap(r, src, dst, img);
        }
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
            long now = motionNow();
            float exit = inOutExpo(clamp01((now - exitAt) / (float) EXIT_MS));
            int[] a = new int[2], b = new int[2];
            getLocationInWindow(a); on.getLocationInWindow(b);
            float k = k(), ox = a[0] - b[0], oy = a[1] - b[1];
            float x0 = ox + getWidth() / 2f + 240f * slotNow(chosen) * k, y0 = oy + my();
            // ccNC Home's profile chip, in screen pixels: left margin 20dp, photo 36dp wide starting 4dp in, in a
            // 64dp-high top bar (HomeActivity / ProfileChip). The window starts at the screen's top left.
            float dp = ctx.getResources().getDisplayMetrics().density;
            float tx = (20 + 4 + 18) * dp - b[0], ty = 32 * dp - b[1], endScale = 36 * dp / (220f * k);
            float x = x0 + (tx - x0) * exit, y = y0 + (ty - y0) * exit;
            float scale = 1f + (endScale - 1f) * exit;
            float alpha = 1f - Math.max(0f, exit - 0.92f) / 0.08f;   // lands, then hands over to the chip
            if (alpha <= 0.01f || scale <= 0.01f) return;
            float d = 220f * k * scale;
            photo(c, chosen, x, y, d, alpha);
        }

        @Override protected void onDraw(Canvas c) {
            float k = k(), mx = getWidth() / 2f, my = my();
            long now = SystemClock.uptimeMillis(), mnow = motionNow();   // wall clock for the countdown, motion clock for movement
            // Opening: until the row unfolds, only the chosen profile shows, 35 px lower; then the others grow in.
            float unfold = inOutCubic(clamp01((mnow - unfoldAt) / (float) UNFOLD_MS));
            // Hand-over to Home: the chosen profile shrinks to 80% and glides to the profile chip at the top left.
            float exit = exitAt < 0 ? 0 : inOutExpo(clamp01((mnow - exitAt) / (float) EXIT_MS));
            float exitFade = exitAt < 0 ? 1 : 1 - clamp01((mnow - exitAt) / (float) EXIT_FADE_MS);
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
                if (alpha <= 0.01f || scale <= 0.01f) continue;
                d *= scale;
                photo(c, i, x, y, d, alpha);
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
                    }   // asked: the ring stays empty and the photo still until the car continues (a turning ring
                        // looked like a second countdown while the head unit stalled)
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
