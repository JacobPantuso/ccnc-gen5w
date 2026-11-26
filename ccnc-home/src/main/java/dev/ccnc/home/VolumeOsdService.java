package dev.ccnc.home;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.HandlerThread;
import android.text.TextUtils;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

import dev.ccnc.common.Fonts;

import java.io.InputStream;
import java.lang.reflect.Method;

/**
 * ccNC's volume OSD (systempopuphmi VolumeOSD, 910x97) drawn as our own overlay window in place of the
 * Gen5W volume strip. The stock panel is MicomVolumePanel inside system_server (window "android",
 * type 2032): it is not a notification or broadcast, so it cannot be restyled. We poll the same service
 * (com.hkmc.media.MicomAudioManager, bootclasspath: getVolumePanelState / getVolume) and, while the
 * stock panel is up, call hideVolumeDisplay(false) so it is taken down at once.
 * Layout from ccNC QML: window 910x97, osd_bg_vol.9.png; row = 20 | vol icon 64 | 48 number (32px SemiBold,
 * progress colour) | 12 | 400x4 bar; closes 3000 ms after the last change.
 */
public final class VolumeOsdService extends Service {
    static final String TAG = "ccncVol";
    static final int W = 910, H = 97, MAX = 45, STREAM = 14, POLL_MS = 80, TIMEOUT_MS = 3000, WIN_W = 1920, BAR_X = 505, VOL_TYPE = 2010;

    private final Handler h = new Handler(Looper.getMainLooper());
    private WindowManager wm;
    private OsdView view;
    /** The window's root: a window doesn't apply its root view's translation/alpha, so the bar animates inside this. */
    private android.widget.FrameLayout box;
    private boolean attached;
    private Object mgr;
    private Method mPanel, mGet, mHide, mCur, mParam, mMute, mName;
    private int stream = STREAM, lastStream = -1;
    private boolean lastMute;
    private final java.util.HashMap<Integer, String> names = new java.util.HashMap<Integer, String>();
    private int last = -1;
    private int logged;

    private final Runnable closer = new Runnable() { @Override public void run() { detach(); } };
    private HandlerThread pollThread;
    private Handler pollH;
    private int tickN;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            try { poll(); } catch (Throwable t) { Log.w(TAG, "poll", t); }
            pollH.postDelayed(this, POLL_MS);
        }
    };

    // ---- top OSD (media track change, phone connects) ----
    private TopView top;
    private boolean topAttached;
    private final Runnable topCloser = new Runnable() { @Override public void run() { detachTop(); } };
    private NowPlaying np;
    private PhoneInfo phoneInfo;
    private long startedAt;
    private String lastTrackKey;
    private boolean lastCp, lastAa, lastBt, connInit;

    @Override public IBinder onBind(Intent i) { return null; }

    @Override public int onStartCommand(Intent i, int flags, int id) {
        if (wm == null) {
            try {
                wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
                view = new OsdView(this);
                Class<?> c = Class.forName("com.hkmc.media.MicomAudioManager");
                mgr = c.getConstructor(Context.class).newInstance(getApplicationContext());
                mPanel = c.getMethod("getVolumePanelState");
                mGet = c.getMethod("getVolume", int.class);
                mHide = c.getMethod("hideVolumeDisplay", boolean.class);
                try { mCur = c.getMethod("getCurrentStreamType"); } catch (Throwable t) { }
                try { mParam = c.getMethod("getStreamTypeParameter", int.class); } catch (Throwable t) { }
                try { mMute = c.getMethod("getUserMuteState"); } catch (Throwable t) { }
                try { mName = c.getMethod("getUserVolumeGroupName", int.class, int.class); } catch (Throwable t) { }
                pollThread = new HandlerThread("ccncVolPoll"); pollThread.start();
                pollH = new Handler(pollThread.getLooper());
                pollH.post(tick);
                Log.i(TAG, "volume OSD running");
            } catch (Throwable t) { Log.w(TAG, "volume service not reachable, OSD off", t); }
            try { startTopOsd(); } catch (Throwable t) { Log.w(TAG, "top OSD off", t); }
        }
        return START_STICKY;
    }

    /**
     * Poll thread (every 80 ms): the stock volume strip is patched out of automotive-service (ccnc.volume),
     * so a change of the active stream's level or of mute is what shows the ccNC bar.
     */
    private void poll() throws Exception {
        if (mCur != null && (++tickN % 12) == 0 || lastStream < 0) {
            try { int cs = (Integer) mCur.invoke(mgr); if (cs > 0) stream = cs; } catch (Throwable t) { }
        }
        int v = (Integer) mGet.invoke(mgr, stream);
        boolean mute = false;
        if (mMute != null) try { mute = (Boolean) mMute.invoke(mgr); } catch (Throwable t) { }
        boolean changed = lastStream >= 0 && (v != last || mute != lastMute);
        if (logged++ < 3 || changed) Log.i(TAG, "stream=" + stream + " vol=" + v + " mute=" + mute + " label=" + label(stream));
        last = v; lastMute = mute; lastStream = stream;
        if (changed) {
            final int fv = v; final boolean fm = mute; final String fl = label(stream);
            h.post(new Runnable() { @Override public void run() { view.label = fl; view.muted = fm; show(fv); } });
        }
    }

    /** The system's own name for the stream's volume group (what the stock strip showed, e.g. "Media"). */
    private String label(int st) {
        String n = names.get(st);
        if (n != null) return n;
        int group = -1;
        try {
            Object prm = mParam == null ? null : mParam.invoke(mgr, st);
            if (prm != null) group = prm.getClass().getField("mVolumeGroup").getInt(prm);
        } catch (Throwable t) { }
        try { if (group >= 0 && mName != null) n = (String) mName.invoke(mgr, group, 0); } catch (Throwable t) { }
        if (n != null && n.startsWith("VOLUMEGROUP_")) n = fromGroupName(n); // internal id, e.g. VOLUMEGROUP_DIPOMUSIC_GUEST
        if (n == null || n.trim().isEmpty()) n = fallback(group >= 0 ? group : st);
        Log.i(TAG, "stream " + st + " group " + group + " -> " + n);
        names.put(st, n);
        return n;
    }

    /** The system's internal volume-group ids (VOLUMEGROUP_<SOURCE>_<PROFILE>) as the names the stock strip showed. */
    private static String fromGroupName(String id) {
        String g = id.toUpperCase(java.util.Locale.US);
        if (g.contains("RING")) return "Ringtone";
        if (g.contains("CALL") || g.contains("PHONE") || g.contains("HFP")) return "Phone";
        if (g.contains("NAVI") || g.contains("GUIDE")) return "Navigation";
        if (g.contains("VR") || g.contains("VOICE") || g.contains("TTS") || g.contains("SIRI")) return "Voice";
        return "Media"; // MUSIC, RADIO, USB, BT audio, DIPO/GPM (CarPlay/Android Auto) music, AV
    }

    /** Stream groups from StreamTypeParameter (GROUP_CALL 0xa, AV 0x14, NONAV 0x1e, TTS 0x28, NAVI 0x32). */
    private static String fallback(int g) {
        if (g >= 0x0a && g < 0x14) return "Phone";
        if (g == 0x2c) return "Ringtone";
        if (g == 0x2a || g == 0x2b) return "Voice";
        if (g >= 0x28 && g < 0x32) return "Voice";
        if (g >= 0x32 && g < 0x3c) return "Navigation";
        return "Media";
    }

    private void show(int v) {
        view.level = Math.max(0, Math.min(MAX, v));
        view.invalidate();
        if (!attached) {
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(WIN_W, H,
                VOL_TYPE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                    | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
            // On the driver's side: bottom left, or bottom right on a right-hand-drive car (where back/home are).
            lp.gravity = Gravity.BOTTOM | (dev.ccnc.common.Drive.rhd(this) ? Gravity.RIGHT : Gravity.LEFT);
            lp.y = 0;
            lp.windowAnimations = R.style.OsdNoAnim;   // no fade-in: the stock strip must never show through
            view.active = false;
            if (box == null) { box = new android.widget.FrameLayout(this); box.addView(view, new android.widget.FrameLayout.LayoutParams(-1, -1)); }
            try { wm.addView(box, lp); attached = true; } catch (Throwable t) { Log.w(TAG, "addView", t); return; }
        }
        boolean wasActive = view.active;
        view.active = true; view.invalidate();
        if (!wasActive || view.getTranslationY() > 0) {
            view.animate().cancel();
            if (!wasActive) { view.setTranslationY(H); view.setAlpha(0f); }
            view.animate().translationY(0).alpha(1f).setDuration(260).setInterpolator(new android.view.animation.DecelerateInterpolator()).setListener(null).start();
        }
        h.removeCallbacks(closer);
        h.postDelayed(closer, TIMEOUT_MS);
    }

    /** The window stays added (no add/remove animation or delay on the next press); only its content is hidden. */
    private void detach() {
        if (!attached || view == null) return;
        view.animate().cancel();
        view.animate().translationY(H).alpha(0f).setDuration(240).setInterpolator(new android.view.animation.AccelerateInterpolator())
            .setListener(new android.animation.AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(android.animation.Animator a) { view.active = false; view.invalidate(); view.setTranslationY(0); view.setAlpha(1f); }
            }).start();
    }

    private void removeVolWindow() {
        if (attached) { try { wm.removeView(box != null ? box : view); } catch (Throwable t) { } attached = false; }
    }

    @Override public void onDestroy() {
        h.removeCallbacksAndMessages(null); if (pollH != null) pollH.removeCallbacksAndMessages(null);
        if (pollThread != null) pollThread.quit();
        removeVolWindow(); detachTop();
        if (np != null) np.stop(); if (phoneInfo != null) phoneInfo.stop();
        super.onDestroy();
    }


    // ================= top OSD =================
    static final int TW = 1920, TH = 95, TOP_TIMEOUT_MS = 3000;

    private void startTopOsd() {
        top = new TopView(this);
        startedAt = System.currentTimeMillis();
        np = new NowPlaying(this, new NowPlaying.Listener() {
            @Override public void onNowPlaying(NowPlaying n) { onTrack(n); }
        });
        np.start();
        phoneInfo = new PhoneInfo(this, new PhoneInfo.Listener() {
            @Override public void onPhoneChanged() { scheduleConn(); }
        });
        phoneInfo.start();
        IntentFilter f = new IntentFilter(Projection.ACTION_MODE_AVAILABLE);
        f.addAction(Projection.ACTION_PP_STATUS);
        registerReceiver(new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) { scheduleConn(); }
        }, f);
        scheduleConn();
    }

    private final Runnable connCheck = new Runnable() { @Override public void run() { checkConn(); } };
    private void scheduleConn() { h.removeCallbacks(connCheck); h.postDelayed(connCheck, 1500); }

    private void onTrack(NowPlaying n) {
        if (!n.valid || n.title == null) return;
        String key = n.action + "|" + n.title;
        boolean first = lastTrackKey == null;
        boolean changed = !key.equals(lastTrackKey);
        lastTrackKey = key;
        if (first || System.currentTimeMillis() - startedAt < 5000) return;
        if (changed) {
            // Title, artist and cover come as separate updates: wait for the rest of this track's info.
            h.removeCallbacks(trackShow);
            h.postDelayed(trackShow, 700);
            shownMsg = null;
        } else if (shownMsg != null && topAttached) {
            String msg = trackMsg(n);
            if (!msg.equals(shownMsg)) { shownMsg = msg; showTopGuarded(sourceName(n.action), msg); } // artist arrived late
        }
    }

    private String shownMsg;

    private static String trackMsg(NowPlaying n) { return n.artist != null ? n.title + " - " + n.artist : n.title; }

    private final Runnable trackShow = new Runnable() {
        @Override public void run() {
            if (np == null || !np.valid || np.title == null) return;
            shownMsg = trackMsg(np);
            Log.i(TAG, "top OSD track: " + sourceName(np.action) + " / " + shownMsg);
            showTopGuarded(sourceName(np.action), shownMsg);
        }
    };

    /** ccNC's own source names (translation data: LID_5053, LID_5291, LID_452, LID_450, LID_361, LID_2852). */
    static String sourceName(String action) {
        if (action.contains("dipoaudio")) return "Apple CarPlay";
        if (action.contains("gpmaudio")) return "Android Auto";
        if (action.contains("btaudio")) return "Bluetooth Audio";
        if (action.contains("usb")) return "USB Music";
        if (action.contains("radio")) return "Radio";
        return "Media";
    }

    private void checkConn() {
        try {
            Projection.State st = Projection.read(this);
            String bt = phoneInfo.name(false);
            boolean cp = st.carplay, aa = st.androidAuto, b = bt != null;
            if (connInit) {
                if (cp && !lastCp) showTopGuarded("Apple CarPlay", phoneInfo.name(true));
                else if (aa && !lastAa) showTopGuarded("Android Auto", bt);
                else if (b && !lastBt && !cp && !aa) showTopGuarded("Phone", bt);
            }
            lastCp = cp; lastAa = aa; lastBt = b; connInit = true;
        } catch (Throwable t) { Log.w(TAG, "conn check", t); }
    }

    private boolean projectionForeground() {
        try {
            java.util.List<android.app.ActivityManager.RunningTaskInfo> l =
                ((android.app.ActivityManager) getSystemService(Context.ACTIVITY_SERVICE)).getRunningTasks(1);
            if (l == null || l.isEmpty() || l.get(0).topActivity == null) return false;
            String pk = l.get(0).topActivity.getPackageName();
            return Projection.CARPLAY_PKG.equals(pk) || Projection.ANDROID_AUTO_PKG.equals(pk);
        } catch (Throwable t) { return false; }
    }

    /** Not while CarPlay / Android Auto is on screen: checked now and again 300 ms later. */
    private void showTopGuarded(final String title, final String msg) {
        if (projectionForeground()) { Log.i(TAG, "top OSD skipped (projection in front)"); return; }
        h.postDelayed(new Runnable() { @Override public void run() {
            if (projectionForeground()) Log.i(TAG, "top OSD skipped (projection in front, recheck)");
            else showTop(title, msg);
        } }, 300);
    }

    void showTop(String title, String msg) {
        if (top == null || title == null) return;
        top.set(title, msg == null ? "" : msg);
        if (!topAttached) {
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(TW, TH,
                WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                    | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.TOP | Gravity.LEFT;
            lp.windowAnimations = R.style.OsdNoAnim;
            try { wm.addView(top, lp); topAttached = true; } catch (Throwable t) { Log.w(TAG, "top addView", t); return; }
        }
        h.removeCallbacks(topCloser);
        h.postDelayed(topCloser, TOP_TIMEOUT_MS);
    }

    private void detachTop() {
        if (topAttached) { try { wm.removeView(top); } catch (Throwable t) { } topAttached = false; }
    }

    /** ccNC NormalOsdTitleMessage: osd_bg_title.9, title 32px at x=60 (340 wide), 1x44 divider 20px after it,
     *  message 32px (26px and scrolling when too long) 19px after the divider, 1430 wide. */
    private static final class TopView extends View {
        String title = "", msg = "";
        long since;
        final Bitmap bg, div;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        final Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG), mp = new Paint(Paint.ANTI_ALIAS_FLAG);
        final int divColor;

        TopView(Context c) {
            super(c);
            bg = OsdView.raw(c, R.raw.osd_bg_title_9); div = OsdView.raw(c, R.raw.osd_bg_divider_9);
            divColor = div.getPixel(div.getWidth() / 2, div.getHeight() / 2);
            Typeface tf = Fonts.get(c, Fonts.REGULAR);
            tp.setTypeface(tf); tp.setTextSize(32); tp.setColor(0xFFFFFFFF);
            mp.setTypeface(tf); mp.setTextSize(32); mp.setColor(0xFFFFFFFF);
        }

        void set(String t, String m) { title = t; msg = m; since = System.currentTimeMillis(); invalidate(); }

        @Override protected void onDraw(Canvas cv) {
            OsdView.nine(cv, bg, new Rect(0, 0, TW, TH), p);
            int cy = 44;                                  // body is 88 high
            Paint.FontMetrics fm = tp.getFontMetrics();
            float by = cy - (fm.ascent + fm.descent) / 2f;
            cv.drawText(TextUtils.ellipsize(title, new android.text.TextPaint(tp), 340, TextUtils.TruncateAt.END).toString(), 60, by, tp);
            Paint dp = new Paint(); dp.setColor(divColor);
            cv.drawRect(420, cy - 22, 421, cy + 22, dp);
            int mx = 440, mw = 1430;
            mp.setTextSize(32);
            float w = mp.measureText(msg);
            if (w > mw) { mp.setTextSize(26); w = mp.measureText(msg); }
            fm = mp.getFontMetrics();
            float my = cy - (fm.ascent + fm.descent) / 2f;
            cv.save(); cv.clipRect(mx, 0, mx + mw, 88);
            if (w <= mw) cv.drawText(msg, mx, my, mp);
            else {
                long t = System.currentTimeMillis() - since - 1000;     // marquee starts after 1 s
                float off = t <= 0 ? 0 : (t * 0.08f) % (w + 80);
                cv.drawText(msg, mx - off, my, mp);
                if (off > 0) cv.drawText(msg, mx - off + w + 80, my, mp);
                postInvalidateDelayed(16);
            }
            cv.restore();
        }
    }

    // ---- drawing ----
    private static final class OsdView extends View {
        int level;
        volatile boolean active, muted;
        String label = "Media";
        final Paint lab = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Bitmap bg, track, fill, on, off;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        final Paint txt = new Paint(Paint.ANTI_ALIAS_FLAG);
        final int numColor, coverColor;

        OsdView(Context c) {
            super(c);
            bg = raw(c, R.raw.osd_bg_vol_9); track = raw(c, R.raw.osd_vol_track_9); fill = raw(c, R.raw.osd_vol_fill_9);
            on = raw(c, R.raw.osd_ic_vol_on); off = raw(c, R.raw.osd_ic_vol_off);
            coverColor = bg.getPixel(40, 30) | 0xFF000000;
            numColor = fill.getPixel(fill.getWidth() / 2, fill.getHeight() / 2) | 0xFF000000;
            Typeface tf = Fonts.get(c, Fonts.SEMIBOLD);
            txt.setTypeface(tf); txt.setTextSize(32); txt.setColor(numColor); txt.setTextAlign(Paint.Align.CENTER);
            lab.setTypeface(Fonts.get(c, Fonts.REGULAR)); lab.setTextSize(32); lab.setColor(0xFFFFFFFF);
        }

        static Bitmap raw(Context c, int id) {
            try {
                InputStream in = c.getResources().openRawResource(id);
                BitmapFactory.Options o = new BitmapFactory.Options(); o.inScaled = false;
                Bitmap b = BitmapFactory.decodeStream(in, null, o); in.close(); return b;
            } catch (Throwable t) { return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888); }
        }

        /** Draws a raw Android 9-patch (1px marker border still on the bitmap) into d. */
        void nine(Canvas cv, Bitmap b, Rect d) { nine(cv, b, d, p); }

        static void nine(Canvas cv, Bitmap b, Rect d, Paint p) {
            int w = b.getWidth() - 2, hh = b.getHeight() - 2;
            int x0 = -1, x1 = -1, y0 = -1, y1 = -1;
            for (int x = 1; x <= w; x++) if ((b.getPixel(x, 0) >>> 24) > 128) { if (x0 < 0) x0 = x - 1; x1 = x; }
            for (int y = 1; y <= hh; y++) if ((b.getPixel(0, y) >>> 24) > 128) { if (y0 < 0) y0 = y - 1; y1 = y; }
            if (x0 < 0) { x0 = 0; x1 = w; }
            if (y0 < 0) { y0 = 0; y1 = hh; }
            int[] sx = {1, 1 + x0, 1 + x1, 1 + w}, sy = {1, 1 + y0, 1 + y1, 1 + hh};
            int rw = w - x1, bh = hh - y1;
            int[] dx = {d.left, d.left + x0, d.right - rw, d.right}, dy = {d.top, d.top + y0, d.bottom - bh, d.bottom};
            for (int j = 0; j < 3; j++) for (int i = 0; i < 3; i++) {
                if (sx[i + 1] <= sx[i] || sy[j + 1] <= sy[j] || dx[i + 1] <= dx[i] || dy[j + 1] <= dy[j]) continue;
                cv.drawBitmap(b, new Rect(sx[i], sy[j], sx[i + 1], sy[j + 1]), new Rect(dx[i], dy[j], dx[i + 1], dy[j + 1]), p);
            }
        }

        @Override protected void onDraw(Canvas cv) {
            if (!active) return;
            // The stock strip is switched off in automotive-service (ccnc.volume NEMESIS patch); without the
            // patch it may still flash underneath. Only the 910x97 ccNC bar is drawn.
            cv.translate(BAR_X, 0);
            nine(cv, bg, new Rect(0, 0, W, H));
            // [label] 24 [vol icon 64] [number 48] 12 [bar 400], centred: the active stream's name, mute state, level.
            float lw = label == null || label.isEmpty() ? 0 : lab.measureText(label);
            int content = (int) (lw > 0 ? lw + 24 : 0) + 64 + 48 + 12 + 400;
            int start = (W - content) / 2;
            Paint.FontMetrics fm = txt.getFontMetrics();
            float base = H / 2f - (fm.ascent + fm.descent) / 2f;
            if (lw > 0) cv.drawText(label, start, base, lab);
            int iconX = start + (int) (lw > 0 ? lw + 24 : 0), numCx = iconX + 64 + 24, barX = iconX + 64 + 48 + 12;
            Bitmap ic = muted || level == 0 ? off : on;
            cv.drawBitmap(ic, null, new Rect(iconX, (H - 64) / 2, iconX + 64, (H - 64) / 2 + 64), p);
            txt.setAlpha(muted ? 110 : 255);
            cv.drawText(Integer.toString(level), numCx, base, txt);
            txt.setAlpha(255);
            int by = (H - 4) / 2;
            nine(cv, track, new Rect(barX, by, barX + 400, by + 4));
            int fw = muted ? 0 : Math.round(400f * level / MAX);
            if (fw > 0) nine(cv, fill, new Rect(barX, by, barX + Math.max(fw, 6), by + 4));
        }
    }
}
