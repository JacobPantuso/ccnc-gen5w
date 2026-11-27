package dev.ccnc.profile;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import dev.ccnc.common.Bg;
import dev.ccnc.common.Chrome;
import dev.ccnc.common.Fonts;
import dev.ccnc.common.Palette;
import dev.ccnc.common.Popup;
import dev.ccnc.common.Profiles;

/**
 * ccNC Profile: the profile chooser. Guest, Driver 1 and Driver 2 centred on the screen, no bottom bar.
 *   normal       tap a profile to switch: it lights up at once with a turning ring and "Switching…", and settles
 *                when the car reports the switch (or goes back with a message if it doesn't).
 * The greeting sits on top, as on ccNC Home's welcome page (the date is in the status strip).
 *   startup      (extra "startup"): the start-up chooser. The profile it will pick (ccNC's default profile, else
 *                the car's) is lit, and a ring around it runs backwards for the auto-advance time, then it
 *                continues with that profile. A tap picks that profile at once. Auto-advance off: waits for a tap.
 *   photo        (extra "photo" = profile id, from ccNC Setup): just the photo picker for that profile.
 * The settings (photos, default profile, auto-advance) live in ccNC Setup > User Profile.
 */
public final class MainActivity extends Activity implements Profiles.Listener {

    private static final String TAG = "ccncProfile";
    private static final long SWITCH_TIMEOUT_MS = 25000;   // the car re-applies seats, climate and media first

    private final String[] names = {"Guest", "Driver 1", "Driver 2"};
    private final Bitmap[] photos = new Bitmap[3];
    private final boolean[] locked = new boolean[3];
    private int active = -1;
    private int pending = -1;          // the profile being switched to, -1 when idle
    private long pendingSince;
    private Cards cards;
    private TextView title, status;
    private boolean startup;           // start-up chooser with the countdown
    private int countdownId = -1;      // the profile the countdown will pick
    private int keyProfile = -1;       // the key's / digital key's profile at this start (startup mode)
    private long countdownStart, countdownMs;
    private Object watch;
    private final Handler ui = new Handler();
    private int loadSeq;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        dev.ccnc.common.Themes.apply(this); // shared ccNC theme
        startup = getIntent().getBooleanExtra("startup", false);
        FrameLayout root = new FrameLayout(this);
        root.addView(new Bg(this), new FrameLayout.LayoutParams(-1, -1));
        // A slim status strip (home, date, clock) and nothing at the bottom: this is also the start-up screen.
        root.addView(Chrome.statusStrip(this, false), new FrameLayout.LayoutParams(-1, Palette.px(this, 40), Gravity.TOP));
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER);
        // On top: the greeting (Good morning / afternoon / evening / night, as on ccNC Home). The date is already
        // in the status strip.
        title = text(greeting(), 30, Palette.TEXT, Fonts.SEMIBOLD);
        title.setGravity(Gravity.CENTER);
        content.addView(title, new LinearLayout.LayoutParams(-1, -2));
        cards = new Cards(this);
        content.addView(cards, new LinearLayout.LayoutParams(-1, Palette.px(this, 270)));
        status = text("", 18, Palette.SUBTEXT, Fonts.REGULAR);
        status.setGravity(Gravity.CENTER);
        content.addView(status, new LinearLayout.LayoutParams(-1, Palette.px(this, 32)));
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER);
        cp.topMargin = Palette.px(this, 40);   // centred in the space below the status strip
        root.addView(content, cp);
        setContentView(root);
        final int photoFor = getIntent().getIntExtra("photo", -1);
        if (photoFor >= 0 && photoFor < 3) ui.post(new Runnable() { @Override public void run() { pickPhoto(photoFor, true); } });
    }

    /** Logs reverse on/off (read-only) while the screen is open, for the car checks. */
    private final android.content.BroadcastReceiver reverseLog = new android.content.BroadcastReceiver() {
        @Override public void onReceive(Context c, android.content.Intent i) { Log.i("ccncProfiles", "broadcast " + i.getAction()); }
    };
    private static boolean diagLogged;

    @Override protected void onResume() {
        super.onResume();
        if (!diagLogged) { diagLogged = true; Profiles.logDiagnostics(this); }
        android.content.IntentFilter rf = new android.content.IntentFilter("com.hkmc.intent.action.REVERSE_ON");
        rf.addAction("com.hkmc.intent.action.REVERSE_OFF");
        try { registerReceiver(reverseLog, rf); } catch (Throwable ignored) { }
        watch = Profiles.watch(this, this);
        onProfilesChanged();
    }

    @Override protected void onPause() {
        Profiles.unwatch(this, watch);
        watch = null;
        try { unregisterReceiver(reverseLog); } catch (Throwable ignored) { }
        stopCountdown();
        super.onPause();
    }

    /** Re-reads everything off the UI thread (binder calls), then settles a pending switch if the car reports it. */
    @Override public void onProfilesChanged() {
        final int seq = ++loadSeq;
        new Thread(new Runnable() { @Override public void run() {
            final int act = Profiles.active(MainActivity.this);
            final int key = startup ? Profiles.keyProfile(MainActivity.this) : -1;
            final String[] n = new String[3];
            final Bitmap[] p = new Bitmap[3];
            final boolean[] l = new boolean[3];
            for (int i = 0; i < 3; i++) {
                n[i] = Profiles.name(MainActivity.this, i);
                p[i] = ProfileStore.photo(MainActivity.this, i);
                l[i] = Profiles.needsPassword(MainActivity.this, i);
            }
            ui.post(new Runnable() { @Override public void run() {
                if (seq != loadSeq || isFinishing()) return;
                active = act;
                if (key >= 0) keyProfile = key;
                System.arraycopy(n, 0, names, 0, 3);
                System.arraycopy(p, 0, photos, 0, 3);
                System.arraycopy(l, 0, locked, 0, 3);
                if (pending >= 0 && act == pending) {
                    Log.i(TAG, "switched to " + act + " in " + (SystemClock.uptimeMillis() - pendingSince) + " ms");
                    pending = -1;
                    status.setText("");
                    if (startup) { finish(); return; }
                }
                if (startup && countdownId < 0 && pending < 0 && act >= 0) startCountdown();
                cards.invalidate();
            } });
        } }, "ccncProfileLoad").start();
    }

    /** A tap on profile {@code id}: shows the switch at once, then asks the car. */
    private void switchTo(final int id) {
        stopCountdown();
        if (id == active && pending < 0) { Chrome.beep(this); finish(); return; }   // already this profile: close
        if (pending >= 0) return;   // one switch at a time
        Chrome.beep(this);
        pending = id;
        pendingSince = SystemClock.uptimeMillis();
        status.setText(locked[id] ? "Enter " + names[id] + "'s password on the screen that opens" : "Switching to " + names[id] + "…");
        cards.startSpin();
        Profiles.switchTo(this, id, new Profiles.Done() {
            @Override public void onDone(boolean ok, String message) {
                if (!ok) fail("Couldn't switch profiles" + (message != null ? ": " + message : ""));
            }
        });
        // The car doesn't always signal the change (the profile observer can fire before getUserType() moves, or not
        // at all while the car re-applies the profile's settings): ask it directly until it reports the new profile.
        ui.postDelayed(new Runnable() { @Override public void run() {
            if (pending != id) return;
            onProfilesChanged();
            ui.postDelayed(this, 800);
        } }, 800);
        ui.postDelayed(new Runnable() { @Override public void run() {
            if (pending == id) fail(locked[id] ? "Profile not changed (password not entered)" : "The car didn't switch profiles. Try again.");
        } }, locked[id] ? 60000 : SWITCH_TIMEOUT_MS);
    }

    private void fail(String why) {
        Log.i(TAG, why);
        pending = -1;
        status.setText("");
        cards.invalidate();
        Toast.makeText(this, why, Toast.LENGTH_LONG).show();
        onProfilesChanged();
    }

    /** Same wording as ccNC Home's welcome page. */
    private static String greeting() {
        int hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        return hour < 5 ? "Good night" : hour < 12 ? "Good morning" : hour < 18 ? "Good afternoon" : "Good evening";
    }

    // ---- start-up countdown ----

    /** Lights the profile it will pick and runs the ring backwards for the auto-advance time. */
    private void startCountdown() {
        int def = ProfileStore.defaultProfile(this);
        // Last Used (or a default with a password): the profile the car is on, which follows the key on cars with key-profile linking.
        countdownId = def >= 0 && !locked[def] ? def : keyProfile >= 0 ? keyProfile : active;
        int secs = ProfileStore.autoAdvance(this);
        if (secs < 0) { countdownMs = 0; status.setText("Tap a profile to continue"); cards.invalidate(); return; }
        countdownMs = Math.max(1, secs) * 1000L;
        countdownStart = SystemClock.uptimeMillis();
        cards.startSpin();
        tickCountdown();
    }

    private final Runnable countdownTick = new Runnable() { @Override public void run() { tickCountdown(); } };

    private void tickCountdown() {
        if (countdownId < 0 || countdownMs <= 0) return;
        long left = countdownMs - (SystemClock.uptimeMillis() - countdownStart);
        if (left <= 0) { int id = countdownId; countdownId = -1; switchTo(id); return; }
        status.setText("Continuing as " + names[countdownId] + " in " + ((left + 999) / 1000) + "s. Tap a profile to choose.");
        cards.invalidate();
        ui.postDelayed(countdownTick, 50);
    }

    private void stopCountdown() {
        ui.removeCallbacks(countdownTick);
        if (countdownId >= 0) { countdownId = -1; status.setText(""); cards.invalidate(); }
    }

    /** 1 at the start of the countdown, 0 when it picks; -1 when none is running. */
    private float countdownLeft() {
        if (countdownId < 0 || countdownMs <= 0) return -1f;
        return Math.max(0f, 1f - (SystemClock.uptimeMillis() - countdownStart) / (float) countdownMs);
    }

    /** ccNC avatars, the car's own picture, or a picture from a USB stick, for the active profile. */
    private void pickPhoto(final int id, final boolean finishAfter) {
        if (id < 0 || id > 2) return;
        final Bitmap[] pics = new Bitmap[ProfileStore.AVATARS + 1];
        pics[0] = ProfileStore.avatar(this, 0);
        for (int i = 1; i <= ProfileStore.AVATARS; i++) pics[i] = ProfileStore.avatar(this, i);
        String cur = ProfileStore.photoChoice(this, id);
        int sel = cur.startsWith("avatar:") ? parse(cur.substring(7)) : -1;
        Popup pp = new Popup(this, Profiles.name(this, id) + "'s Photo", null);
        if (finishAfter) pp.setOnDismissListener(new android.content.DialogInterface.OnDismissListener() {
            @Override public void onDismiss(android.content.DialogInterface d) { if (!usbOpen) finish(); }
        });
        pp.pictures(pics, sel, new String[]{"Car's Photo", "From USB Stick…"}, new Popup.Pick() {
                    @Override public void onPick(int i) {
                        if (i <= ProfileStore.AVATARS) ProfileStore.setChoice(MainActivity.this, id, "avatar:" + i);   // 0 = the guest picture
                        else if (i == ProfileStore.AVATARS + 1) ProfileStore.setChoice(MainActivity.this, id, "");  // the car's own avatar
                        else { usbOpen = true; pickUsb(id, finishAfter); }
                    }
                });
        pp.show();
    }

    private static int parse(String s) { try { return Integer.parseInt(s); } catch (NumberFormatException e) { return -1; } }

    /** Pictures (jpg/png) on a USB stick: its top folder and folders one level down. */
    private boolean usbOpen;

    private void pickUsb(final int id, final boolean finishAfter) {
        final List<File> files = new ArrayList<File>();
        for (String root : new String[]{"/storage/usb0", "/storage/usb1", "/storage/usb2"}) {
            File[] top = new File(root).listFiles();
            if (top == null) continue;
            for (File f : top) {
                if (f.isDirectory() && !f.getName().startsWith(".")) {
                    File[] sub = f.listFiles();
                    if (sub != null) for (File g : sub) if (isPicture(g)) files.add(g);
                } else if (isPicture(f)) files.add(f);
                if (files.size() >= 60) break;
            }
        }
        if (files.isEmpty()) {
            Toast.makeText(this, "No pictures found. Put a .jpg or .png on a USB stick and plug it in.", Toast.LENGTH_LONG).show();
            usbOpen = false;
            if (finishAfter) finish();
            return;
        }
        String[] labels = new String[files.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = files.get(i).getPath().replaceFirst("^/storage/usb\\d/", "");
        Popup up = new Popup(this, "Picture from USB Stick", "The centre of the picture is used, cut to a circle.");
        if (finishAfter) up.setOnDismissListener(new android.content.DialogInterface.OnDismissListener() {
            @Override public void onDismiss(android.content.DialogInterface d) { usbOpen = false; finish(); }
        });
        up.options(labels, -1, new Popup.Pick() {
                    @Override public void onPick(int i) {
                        Bitmap b = decodeSampled(files.get(i).getPath(), 600);
                        boolean ok = ProfileStore.setCustomPhoto(MainActivity.this, id, b);
                        Toast.makeText(MainActivity.this, ok ? "Photo updated" : "Couldn't read that picture", Toast.LENGTH_SHORT).show();
                    }
                });
        up.show();
    }

    private static boolean isPicture(File f) {
        String n = f.getName().toLowerCase(Locale.US);
        return f.isFile() && !n.startsWith(".") && (n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png"));
    }

    private static Bitmap decodeSampled(String path, int max) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, o);
        int s = 1;
        while (o.outWidth / (s * 2) >= max && o.outHeight / (s * 2) >= max) s *= 2;
        o = new BitmapFactory.Options();
        o.inSampleSize = s;
        return BitmapFactory.decodeFile(path, o);
    }

    // ---- views ----

    private TextView text(String t, float size, int color, int face) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextSize(size);
        v.setTextColor(color);
        v.setTypeface(Fonts.get(this, face));
        return v;
    }

    /** ccNC USERPROFILE_UserButtonGroup: three round profiles, the active (or pending) one large and framed. */
    private final class Cards extends View {
        private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG), img = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG),
                spin = new Paint(Paint.ANTI_ALIAS_FLAG), press = new Paint(Paint.ANTI_ALIAS_FLAG), frameP = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final RectF r = new RectF();
        private final float[] cx = {-240, 0, 240};
        private final Bitmap frame;
        private int down = -1;
        private final Runnable spinTick = new Runnable() { @Override public void run() { if (pending >= 0) { invalidate(); postDelayed(this, 16); } } };

        Cards(Context c) {
            super(c);
            setClickable(true);
            setSoundEffectsEnabled(false);
            frame = load(c, "frs_ic_profile_l_s");
            spin.setStyle(Paint.Style.STROKE);
            spin.setStrokeCap(Paint.Cap.ROUND);
        }

        private Bitmap load(Context c, String n) {
            try {
                java.io.InputStream in = c.getAssets().open("ccnc/frs/" + n + ".png");
                try { return BitmapFactory.decodeStream(in); } finally { in.close(); }
            } catch (Exception e) { return null; }
        }

        void startSpin() { removeCallbacks(spinTick); post(spinTick); }

        private int hit(float x, float y) {
            float k = getWidth() / 1920f, mx = getWidth() / 2f, my = getHeight() * 0.38f;
            for (int i = 0; i < 3; i++) if (Math.abs(x - (mx + cx[i] * k)) < 120 * k && Math.abs(y - my) < 150 * k) return i;
            return -1;
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: down = hit(e.getX(), e.getY()); invalidate(); return true;
                case MotionEvent.ACTION_MOVE: if (down >= 0 && hit(e.getX(), e.getY()) != down) { down = -1; invalidate(); } return true;
                case MotionEvent.ACTION_UP:
                    int i = down;
                    down = -1;
                    invalidate();
                    if (i >= 0 && hit(e.getX(), e.getY()) == i) switchTo(i);
                    return true;
                case MotionEvent.ACTION_CANCEL: down = -1; invalidate(); return true;
            }
            return true;
        }

        @Override protected void onDraw(Canvas c) {
            float k = getWidth() / 1920f, mx = getWidth() / 2f, my = getHeight() * 0.38f;
            float left = countdownLeft();
            int shown = pending >= 0 ? pending : countdownId >= 0 ? countdownId : active;
            for (int i = 0; i < 3; i++) {
                boolean on = i == shown;
                float d = (on ? 220f : 180f) * k * (i == down ? 0.94f : 1f), x = mx + cx[i] * k;
                Bitmap b = photos[i];
                img.setAlpha(on ? 255 : 170);
                if (b != null) {
                    BitmapShader s = new BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                    Matrix m = new Matrix();
                    float sc = d / Math.min(b.getWidth(), b.getHeight());
                    m.setScale(sc, sc);
                    m.postTranslate(x - b.getWidth() * sc / 2f, my - b.getHeight() * sc / 2f);
                    s.setLocalMatrix(m);
                    img.setShader(s);
                    c.drawCircle(x, my, d / 2f, img);
                    img.setShader(null);
                } else {
                    img.setColor(0x33FFFFFF);
                    c.drawCircle(x, my, d / 2f, img);
                }
                if (i == down) {
                    press.setColor(Palette.PRESS);
                    c.drawCircle(x, my, d / 2f, press);
                }
                if (on && left >= 0) {
                    // Start-up countdown: a full accent ring that runs backwards to nothing, then it continues.
                    spin.setStrokeWidth(7f * k);
                    spin.setColor(Palette.alpha(Palette.TEXT, 0x33));
                    float f = 238f * k;
                    r.set(x - f / 2, my - f / 2, x + f / 2, my + f / 2);
                    c.drawArc(r, 0, 360, false, spin);
                    spin.setColor(Palette.ACCENT);
                    c.drawArc(r, -90, 360f * left, false, spin);
                } else if (on && pending < 0 && frame != null) {
                    float f = 234f * k;
                    r.set(x - f / 2, my - f / 2, x + f / 2, my + f / 2);
                    c.drawBitmap(frame, null, r, frameP);
                } else if (on) {
                    // Switching: an accent ring that turns until the car confirms.
                    spin.setStrokeWidth(6f * k);
                    spin.setColor(Palette.ACCENT);
                    float f = 236f * k;
                    r.set(x - f / 2, my - f / 2, x + f / 2, my + f / 2);
                    float start = (SystemClock.uptimeMillis() % 1200) / 1200f * 360f;
                    c.drawArc(r, start, pending >= 0 ? 270 : 360, false, spin);
                }
                text.setTypeface(Fonts.get(getContext(), on ? Fonts.SEMIBOLD : Fonts.REGULAR));
                text.setTextSize((on ? 38f : 30f) * k);
                text.setColor(on ? Palette.TEXT : Palette.SUBTEXT);
                text.setTextAlign(Paint.Align.CENTER);
                c.drawText(names[i], x, my + 170f * k, text);
                if (locked[i]) {
                    text.setTextSize(22f * k);
                    text.setColor(Palette.SUBTEXT);
                    c.drawText("Password", x, my + 206f * k, text);
                }
            }
        }
    }
}
