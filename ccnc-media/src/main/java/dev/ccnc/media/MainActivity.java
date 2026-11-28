package dev.ccnc.media;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import dev.ccnc.common.Art;
import dev.ccnc.common.Bg;
import dev.ccnc.common.Fonts;
import dev.ccnc.common.Palette;

/**
 * ccNC Media: media home (now-playing card + one thumbnail per source) and the now-playing
 * screen. Layout numbers come from the recovered mediahome QML (tiles 272x272, spacing 40,
 * title 30 SemiBold, sub text 28 at #80dfedfe, card title 50 / sub 32, art 472).
 */
public final class MainActivity extends Activity implements Media.Source.Listener {

    // name shown (ccNC string), package, activity, icon asset
    private static final String[][] SOURCES = {
            {"Radio", "com.hkmc.av.app.radiona", ".RadioMainActivity", "me_ic_mode_radio_160_n.png"},
            {"USB Music", "com.daudio.av.app.usbmusic", ".ui.MusicActivity", "me_ic_mode_usbmusic_n.png"},
            {"Bluetooth Audio", "com.daudio.av.app.btaudio.ui", ".BtMusicMainActivity", "me_ic_mode_btaudio_n.png"},
            {"Apple CarPlay", "com.daudio.app.dipo", ".ui.DipoActivity", "me_ic_mode_cp_n.png"},
    };

    private Media.Source active;
    private final Map<String, Media.State> known = new HashMap<String, Media.State>();
    private String current;       // source name of the latest feed, or null
    private Media.State now = new Media.State();
    private Home home;
    private Player player;
    private final Map<String, Bitmap> bmp = new HashMap<String, Bitmap>();

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        dev.ccnc.common.Themes.apply(this);
        boolean car = false;
        try { Class.forName("com.hkmc.mode.ModeManager"); car = true; } catch (Throwable t) { }
        active = car ? new VehicleSource(this) : new DemoSource();
        now.title = null; now.artist = null; now.album = null; now.durationMs = 0; now.positionMs = 0; now.playing = false;

        FrameLayout root = new FrameLayout(this);
        root.addView(new Bg(this), new FrameLayout.LayoutParams(-1, -1));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        root.addView(col, new FrameLayout.LayoutParams(-1, -1));
        col.addView(dev.ccnc.common.Chrome.topBar(this, "Media"),
                new LinearLayout.LayoutParams(-1, dev.ccnc.common.Palette.px(this, 64)));
        FrameLayout body = new FrameLayout(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, 0, 1f);
        lp.bottomMargin = 90;
        col.addView(body, lp);
        home = new Home(this);
        player = new Player(this);
        player.setVisibility(View.GONE);
        body.addView(home, new FrameLayout.LayoutParams(-1, -1));
        body.addView(player, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
    }

    /** The car's MEDIA key (ccNC Home's HardKeyReceiver) opens the source list, like the stock MediaGate. */
    @Override protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        if (i != null && "dev.ccnc.action.HARD_KEY".equals(i.getAction())) showPlayer(false);
    }

    @Override protected void onStart() { super.onStart(); active.start(this); }
    @Override protected void onStop() { super.onStop(); active.stop(); }

    @Override public void onBackPressed() {
        if (player.getVisibility() == View.VISIBLE) showPlayer(false); else super.onBackPressed();
    }

    @Override public void onMedia(Media.State s) {
        if (s.source != null && !"Media".equals(s.source)) {
            current = s.source;
            Media.State k = new Media.State();
            k.source = s.source;
            k.title = s.valid ? s.title : null;
            k.artist = s.valid ? s.artist : null;
            k.album = s.valid ? s.album : null;
            k.art = s.valid ? s.art : null;
            known.put(s.source, k);
        }
        now = s;
        home.invalidate();
        player.invalidate();
    }

    private void showPlayer(boolean on) {
        player.setVisibility(on ? View.VISIBLE : View.GONE);
        home.setVisibility(on ? View.GONE : View.VISIBLE);
    }

    private void launchSource(String[] src) {
        // Bluetooth Audio's screen isn't exported: the mode manager opens it, as the stock media list does.
        if (dev.ccnc.common.CarMedia.BT_AUDIO_PKG.equals(src[1])) {
            int r = dev.ccnc.common.CarMedia.launchMode(this, dev.ccnc.common.CarMedia.MODE_BLUETOOTH);
            if (r == dev.ccnc.common.CarMedia.LAUNCHED) return;
            if (r == dev.ccnc.common.CarMedia.NOT_AVAILABLE) {
                Toast.makeText(this, "Connect a phone over Bluetooth to use Bluetooth Audio", Toast.LENGTH_SHORT).show();
                return;
            }
        }
        try {
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.setComponent(new ComponentName(src[1], src[1] + src[2]));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (ActivityNotFoundException e) {
        } catch (SecurityException e) {
            Toast.makeText(this, src[0] + " can't be opened right now", Toast.LENGTH_SHORT).show();
        }
    }

    private Bitmap asset(String n) {
        Bitmap b = bmp.get(n);
        if (b != null) return b;
        try {
            InputStream in = getAssets().open("ccnc/media/" + n);
            b = BitmapFactory.decodeStream(in);
            in.close();
        } catch (Throwable t) { b = null; }
        if (b != null) bmp.put(n, b);
        return b;
    }

    private static String mmss(long ms) {
        long s = ms / 1000;
        return String.format(Locale.US, "%d:%02d", s / 60, s % 60);
    }

    private static boolean has(String s) { return s != null && s.length() > 0; }

    // ---- drawing helpers ----

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

    /** 9-slice of a ".9.png" (1px marker border stripped), fixed corner size c. */
    private void slice(Canvas cv, Bitmap b, float x, float y, float w, float h, int c, Paint paint) {
        if (b == null) return;
        int bw = b.getWidth() - 2, bh = b.getHeight() - 2;
        int[] sx = {1, 1 + c, 1 + bw - c, 1 + bw};
        int[] sy = {1, 1 + c, 1 + bh - c, 1 + bh};
        float[] dx = {x, x + c, x + w - c, x + w};
        float[] dy = {y, y + c, y + h - c, y + h};
        Rect s = new Rect(); RectF d = new RectF();
        for (int i = 0; i < 3; i++) for (int j = 0; j < 3; j++) {
            s.set(sx[i], sy[j], sx[i + 1], sy[j + 1]);
            d.set(dx[i], dy[j], dx[i + 1], dy[j + 1]);
            cv.drawBitmap(b, s, d, paint);
        }
    }

    private void text(Canvas cv, String t, float x, float baseline, float w, int size, int weight, int color) {
        if (!has(t)) return;
        p.setTypeface(Fonts.get(this, weight));
        p.setTextSize(size);
        p.setColor(color);
        p.setAlpha(Color_a(color));
        cv.drawText(TextUtils.ellipsize(t, new android.text.TextPaint(p), w, TextUtils.TruncateAt.END).toString(), x, baseline, p);
    }

    private static int Color_a(int c) { return (c >>> 24) & 0xFF; }

    /** Colour for text/marks on the page background: the dark-theme colour, or ccNC's ink in light themes. */
    private static int ink(int dark, int light) { return Palette.LIGHT ? light : dark; }

    /** White line art drawn on the page background: redrawn in dark ink in light themes. */
    private Bitmap inkAsset(String n) {
        Bitmap b = asset(n);
        return b == null ? null : Art.ink(b);
    }

    /** Art (or default) cropped to square dst, dimmed, corner-masked, stroked, with the source icon over it. */
    private void thumb(Canvas cv, float x, float y, float sz, Bitmap art, String icon, int cornerC) {
        Bitmap art0 = art != null ? art : asset("me_bg_default_01.png");
        cv.saveLayer(x, y, x + sz, y + sz, null, Canvas.ALL_SAVE_FLAG);
        if (art0 != null) {
            int m = Math.min(art0.getWidth(), art0.getHeight());
            Rect s = new Rect((art0.getWidth() - m) / 2, (art0.getHeight() - m) / 2,
                    (art0.getWidth() + m) / 2, (art0.getHeight() + m) / 2);
            cv.drawBitmap(art0, s, new RectF(x, y, x + sz, y + sz), p);
        }
        Paint dim = new Paint();
        dim.setColor(0xB303060A);
        cv.drawRect(x, y, x + sz, y + sz, dim);
        Paint out = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        out.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
        slice(cv, asset("me_bg_home_thumbnail_mask.9.png"), x, y, sz, sz, cornerC, out);
        cv.restore();
        slice(cv, asset("me_bg_home_thumbnail_stroke.9.png"), x, y, sz, sz, cornerC, p);
        Bitmap ic = icon == null ? null : asset(icon);
        if (ic != null) cv.drawBitmap(ic, null, new RectF(x + (sz - 148) / 2, y + (sz - 148) / 2, x + (sz + 148) / 2, y + (sz + 148) / 2), p);
    }

    private String[] cur() {
        for (String[] s : SOURCES) if (s[0].equals(current)) return s;
        return null;
    }

    // ---- media home ----

    private final class Home extends View {
        private final RectF[] hit = new RectF[SOURCES.length + 1];
        private int down = -2;
        Home(Context c) { super(c); }

        @Override protected void onDraw(Canvas cv) {
            // Centred between the top bar and ccNC's 90 px bottom bar (back/home, now playing).
            float ch = 440, top = Math.max(24f, (getHeight() - ch) / 2f); // the view already ends above the bottom bar
            // now-playing card (index 0)
            float cx = 44, cw = 584;
            hit[0] = new RectF(cx, top, cx + cw, top + ch);
            android.graphics.drawable.Drawable cardBg = dev.ccnc.common.NinePatchArt.patch(getContext(), "me_bg_home_thumbnail_bg.9");
            if (cardBg != null) { cardBg.setBounds((int) cx, (int) top, (int) (cx + cw), (int) (top + ch)); cardBg.draw(cv); }
            String[] cs = cur();
            boolean valid = now.valid && has(now.title);
            thumb(cv, cx + 50, top + 44, 192, valid ? now.art : null, cs == null ? null : cs[3], 30);
            String title = valid ? now.title : (cs == null ? null : cs[0]);
            text(cv, title, cx + 50, top + 310, 484, 50, Fonts.REGULAR, 0xFFFFFFFF);
            if (valid) {
                text(cv, now.artist, cx + 50, top + 360, 484, 32, Fonts.REGULAR, 0xFFFFFFFF);
                text(cv, now.album, cx + 50, top + 402, 484, 32, Fonts.REGULAR, 0xFFFFFFFF);
            }
            if (down == 0) { Paint d = new Paint(); d.setColor(0x26FFFFFF); cv.drawRoundRect(hit[0], 30, 30, d); }
            // one tile per source
            float x = cx + cw + 40;
            for (int i = 0; i < SOURCES.length; i++) {
                String[] s = SOURCES[i];
                Media.State k = known.get(s[0]);
                hit[i + 1] = new RectF(x, top, x + 272, top + 272 + 150);
                thumb(cv, x, top, 272, k == null ? null : k.art, s[3], 30);
                text(cv, s[0], x + 11, top + 272 + 32 + 30, 250, 30, Fonts.SEMIBOLD, ink(0xFFFFFFFF, Palette.TEXT));
                if (k != null && has(k.title)) {
                    text(cv, k.title, x + 11, top + 272 + 32 + 38 + 26, 250, 28, Fonts.REGULAR, ink(0x80DFEDFE, Palette.SUBTEXT));
                    text(cv, k.artist, x + 11, top + 272 + 32 + 38 + 34 + 8 + 26, 250, 28, Fonts.REGULAR, ink(0x80DFEDFE, Palette.SUBTEXT));
                }
                if (down == i + 1) { Paint d = new Paint(); d.setColor(0x26FFFFFF); cv.drawRoundRect(new RectF(x, top, x + 272, top + 272), 30, 30, d); }
                x += 272 + 40;
            }
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            int idx = -1;
            for (int i = 0; i < hit.length; i++) if (hit[i] != null && hit[i].contains(e.getX(), e.getY())) idx = i;
            switch (e.getAction()) {
                case MotionEvent.ACTION_DOWN: down = idx; invalidate(); return true;
                case MotionEvent.ACTION_UP:
                    if (idx >= 0 && idx == down) {
                        if (idx == 0) showPlayer(true); else launchSource(SOURCES[idx - 1]);
                    }
                    down = -2; invalidate(); return true;
                case MotionEvent.ACTION_CANCEL: down = -2; invalidate(); return true;
            }
            return true;
        }
    }

    // ---- now playing ----

    private final class Player extends View {
        private final RectF back = new RectF(), prev = new RectF(), play = new RectF(), next = new RectF();
        private int down = -1;
        Player(Context c) { super(c); }

        @Override protected void onDraw(Canvas cv) {
            int W = getWidth(), H = getHeight();
            Bitmap ar = inkAsset("me_ic_arrow_down_n.png");
            back.set(20, 20, 140, 120);
            if (ar != null) cv.drawBitmap(ar, 40, 28, p);
            boolean valid = now.valid && has(now.title);
            // art 472 at right
            float ax = W - 35 - 560 + 44, ay = Math.max(20, H - 551);
            Bitmap art = valid && now.art != null ? now.art : asset("me_bg_default_472.png");
            cv.saveLayer(ax, ay, ax + 472, ay + 472, null, Canvas.ALL_SAVE_FLAG);
            if (art != null) {
                int m = Math.min(art.getWidth(), art.getHeight());
                Rect s = new Rect((art.getWidth() - m) / 2, (art.getHeight() - m) / 2, (art.getWidth() + m) / 2, (art.getHeight() + m) / 2);
                cv.drawBitmap(art, s, new RectF(ax, ay, ax + 472, ay + 472), p);
            }
            Paint out = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            out.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
            Bitmap mk = asset("me_bg_nowplaying_mask.png");
            if (mk != null) cv.drawBitmap(mk, null, new RectF(ax, ay, ax + 472, ay + 472), out);
            cv.restore();
            // song info
            String[] cs = cur();
            String t = valid ? now.title : (cs == null ? null : cs[0]);
            text(cv, t, 104, 190, 1180, 50, Fonts.REGULAR, ink(0xFFFFFFFF, Palette.TEXT));
            if (valid) {
                text(cv, now.artist, 104, 250, 1180, 32, Fonts.REGULAR, ink(0xFFFFFFFF, Palette.TEXT));
                text(cv, now.album, 104, 296, 1180, 32, Fonts.REGULAR, ink(0x80DFEDFE, Palette.SUBTEXT));
            }
            // progress + times (shown only when a duration is known)
            float py = H - 40 - 28 - 20;
            if (valid && now.durationMs > 0) {
                Paint b = new Paint(Paint.ANTI_ALIAS_FLAG);
                b.setColor(ink(0x33FFFFFF, Palette.alpha(Palette.TEXT, 0x33)));
                cv.drawRect(104, py, 104 + 1119, py + 4, b);
                b.setColor(ink(0xFFFFFFFF, Palette.TEXT));
                float f = Math.min(1f, (float) now.positionMs / now.durationMs);
                cv.drawRect(104, py, 104 + 1119 * f, py + 4, b);
                text(cv, mmss(now.positionMs), 104, py + 48, 200, 28, Fonts.REGULAR, ink(0x80FFFFFF, Palette.SUBTEXT));
                p.setTextSize(28);
                String d = mmss(now.durationMs);
                text(cv, d, 104 + 1119 - p.measureText(d), py + 48, 200, 28, Fonts.REGULAR, ink(0x80FFFFFF, Palette.SUBTEXT));
            }
            // transport
            float cy = py - 150, cx0 = 104 + 1119 / 2f;
            prev.set(cx0 - 220, cy - 50, cx0 - 120, cy + 50);
            play.set(cx0 - 50, cy - 50, cx0 + 50, cy + 50);
            next.set(cx0 + 120, cy - 50, cx0 + 220, cy + 50);
            btn(cv, prev, "pre", down == 1);
            btn(cv, play, now.playing ? "pause" : "play", down == 2);
            btn(cv, next, "foward", down == 3);
        }

        private void btn(Canvas cv, RectF r, String n, boolean pressed) {
            Bitmap b = inkAsset("me_btn_" + n + (pressed ? "_p.png" : "_n.png"));
            if (b != null) cv.drawBitmap(b, null, new RectF(r.centerX() - 32, r.centerY() - 32, r.centerX() + 32, r.centerY() + 32), p);
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            float x = e.getX(), y = e.getY();
            int idx = back.contains(x, y) ? 0 : prev.contains(x, y) ? 1 : play.contains(x, y) ? 2 : next.contains(x, y) ? 3 : -1;
            switch (e.getAction()) {
                case MotionEvent.ACTION_DOWN: down = idx; invalidate(); return true;
                case MotionEvent.ACTION_UP:
                    if (idx >= 0 && idx == down) {
                        if (idx == 0) showPlayer(false);
                        else active.command(idx == 1 ? KeyEvent.KEYCODE_MEDIA_PREVIOUS
                                : idx == 2 ? KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE : KeyEvent.KEYCODE_MEDIA_NEXT);
                    }
                    down = -1; invalidate(); return true;
                case MotionEvent.ACTION_CANCEL: down = -1; invalidate(); return true;
            }
            return true;
        }
    }
}
