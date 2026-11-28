package dev.ccnc.voicememo;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.StatFs;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import dev.ccnc.common.Art;
import dev.ccnc.common.Bg;
import dev.ccnc.common.Fonts;
import dev.ccnc.common.Palette;

/**
 * ccNC Voice Memo (VOICEMEMO.Main / VOICEMEMO.Delete). Layout in raw px from the recovered QML.
 * Strings: ccNC en_US translation (LID0015623, LID0039743, LID0052359, LID_388, LID_2262, LID_415, LID_416, LID0038953).
 */
public final class MainActivity extends Activity {
    private static final String TAG = "ccncMemo";
    private static final String STOCK_DIR = "/securedata/com.daudio.app.voicememo/voicememo";
    private static final int COL1 = 0xFFD1D1D1, COL2 = 0x80DFEDFE;

    /** Primary / secondary text: the stock light greys on dark themes, the theme's ink on light ones. */
    private static int c1() { return Palette.LIGHT ? Palette.TEXT : COL1; }
    private static int c2() { return Palette.LIGHT ? Palette.SUBTEXT : COL2; }
    /** Faint white wash (button fills, dividers, progress track) on dark themes; faint ink on light ones. */
    private static int wash() { return Palette.LIGHT ? Palette.alpha(Palette.TEXT, 0x22) : 0x33FFFFFF; }

    private File dir;
    private MediaRecorder recorder;
    private MediaPlayer player;
    private Item playing;
    private long startedAt;
    private final Handler handler = new Handler();
    private FrameLayout stage;
    private int top;
    private boolean deleteMode;
    private final List<Item> items = new ArrayList<Item>();
    private final List<Item> marked = new ArrayList<Item>();
    private int amp;

    private static final class Item { File f; boolean own; }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (recorder != null) {
                try { amp = recorder.getMaxAmplitude(); } catch (Throwable ignored) {}
                build();
                handler.postDelayed(this, 250);
            } else if (player != null) {
                build();
                handler.postDelayed(this, 500);
            }
        }
    };

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        dev.ccnc.common.Themes.apply(this);
        File base = Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())
                ? new File(Environment.getExternalStorageDirectory(), "VoiceMemo") : getFilesDir();
        dir = new File(base, "ccNC");
        if (!dir.isDirectory() && !dir.mkdirs()) dir = getFilesDir();
        FrameLayout root = new FrameLayout(this);
        root.addView(new Bg(this), new FrameLayout.LayoutParams(-1, -1));
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        root.addView(content, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout bar = dev.ccnc.common.Chrome.topBar(this, qs("Voice Memo"));
        top = dev.ccnc.common.Palette.px(this, 56);
        bar.setLayoutParams(new LinearLayout.LayoutParams(-1, top));
        content.addView(bar);
        stage = new FrameLayout(this);
        content.addView(stage, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
        reload();
        build();
    }

    @Override protected void onPause() {
        if (recorder != null) stopRecording();
        stopPlayback();
        super.onPause();
    }

    private static String qs(String s) { return s; }

    // ---- data ----
    private void reload() {
        items.clear();
        add(dir, true);
        add(new File(STOCK_DIR), false);
        Collections_sort();
        marked.clear();
    }

    private void add(File d, boolean own) {
        File[] f = null;
        try { f = d.listFiles(); } catch (Throwable ignored) {}
        if (f == null) return;
        for (File x : f) {
            if (!x.isFile()) continue;
            Item i = new Item(); i.f = x; i.own = own; items.add(i);
        }
    }

    private void Collections_sort() {
        Item[] a = items.toArray(new Item[0]);
        Arrays.sort(a, new Comparator<Item>() {
            @Override public int compare(Item p, Item q) { return p.f.lastModified() < q.f.lastModified() ? 1 : p.f.lastModified() > q.f.lastModified() ? -1 : 0; }
        });
        items.clear();
        items.addAll(Arrays.asList(a));
    }

    private int freeMb() {
        try { StatFs s = new StatFs(dir.getAbsolutePath()); return (int) ((long) s.getAvailableBlocks() * s.getBlockSize() / (1024 * 1024)); }
        catch (Throwable t) { return -1; }
    }

    private long duration(Item it) {
        MediaPlayer p = new MediaPlayer();
        try { p.setDataSource(it.f.getAbsolutePath()); p.prepare(); return p.getDuration(); }
        catch (Throwable t) { return -1; } finally { p.release(); }
    }

    // ---- recorder / player ----
    private void toggleRecord() {
        if (recorder != null) return; // pause is not implemented by this build
        stopPlayback();
        File f = new File(dir, "Voice" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".aac");
        MediaRecorder r = new MediaRecorder();
        try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC);
            r.setOutputFormat(6);
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            r.setAudioSamplingRate(44100);
            r.setAudioEncodingBitRate(96000);
            r.setOutputFile(f.getAbsolutePath());
            r.prepare();
            r.start();
        } catch (Throwable t) {
            Log.w(TAG, "start failed", t);
            try { r.release(); } catch (Throwable ignored) {}
            return;
        }
        recorder = r;
        startedAt = System.currentTimeMillis();
        build();
        handler.post(tick);
    }

    private void stopRecording() {
        MediaRecorder r = recorder;
        recorder = null;
        try { r.stop(); } catch (Throwable t) { Log.w(TAG, "stop failed", t); }
        try { r.release(); } catch (Throwable ignored) {}
        reload();
        build();
    }

    private void play(Item it) {
        stopPlayback();
        try {
            MediaPlayer p = new MediaPlayer();
            p.setDataSource(it.f.getAbsolutePath());
            p.prepare();
            p.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override public void onCompletion(MediaPlayer mp) { stopPlayback(); build(); }
            });
            p.start();
            player = p; playing = it;
            handler.post(tick);
        } catch (Throwable t) { Log.w(TAG, "play failed", t); playing = null; }
        build();
    }

    private void stopPlayback() {
        if (player != null) {
            try { player.stop(); } catch (Throwable ignored) {}
            player.release(); player = null;
        }
        playing = null;
    }

    private void step(int d) {
        int i = items.indexOf(playing);
        if (i < 0) return;
        int n = i + d;
        if (n >= 0 && n < items.size()) play(items.get(n));
    }

    // ---- UI ----
    private void build() {
        stage.removeAllViews();
        if (deleteMode) buildDelete(); else buildMain();
    }

    private void buildMain() {
        boolean rec = recorder != null, pl = playing != null && player != null;
        // control area x=44 w=428 h=520
        if (pl) {
            put(txt(playing.f.getName(), 32, c1(), Fonts.SEMIBOLD, Gravity.CENTER), 44 + 16, 16, 428 - 32, 80);
            put(txt(date(playing.f), 28, c2(), Fonts.REGULAR, Gravity.CENTER), 44 + 16, 16 + 80 + 12, 428 - 32, 36);
            int y = 16 + 80 + 12 + 36 + 82;
            int cur = player.getCurrentPosition(), tot = Math.max(1, player.getDuration());
            put(new Progress(this, cur / (float) tot), 44, y, 428, 32);
            put(txt(mmss(cur), 28, c1(), Fonts.REGULAR, Gravity.CENTER_VERTICAL | Gravity.LEFT), 44 + 16, y + 32, 116, 36);
            put(txt(mmss(tot), 28, c2(), Fonts.REGULAR, Gravity.CENTER_VERTICAL | Gravity.RIGHT), 44 + 428 - 16 - 116, y + 32, 116, 36);
            int by = y + 68 + 29, bw = (428 - 40 - 28) / 3;
            put(icon("voice_ic_pre_n", new Runnable() { public void run() { step(-1); } }), 44 + 20, by, bw, 74);
            put(icon(player.isPlaying() ? "voice_ic_pause_n" : "voice_ic_play_n", new Runnable() { public void run() {
                if (player.isPlaying()) player.pause(); else player.start(); build(); } }), 44 + 20 + bw + 14, by, bw, 74);
            put(icon("voice_ic_forward_n", new Runnable() { public void run() { step(1); } }), 44 + 20 + 2 * (bw + 14), by, bw, 74);
        } else if (rec) {
            put(new Wave(this, amp), 44 + 18, 0, 428 - 36, 160);
            put(txt(mmss(System.currentTimeMillis() - startedAt), 38, c1(), Fonts.SEMIBOLD, Gravity.CENTER), 44 + 18, 160 + 19, 428 - 36, 48);
        } else {
            put(img("voice_ic_voicememo", 140, 140), 44 + 144, 20, 140, 140);
            int mb = freeMb();
            put(txt(qs("Available") + " ‎" + (mb < 0 ? "--" : mb) + " MB", 32, c1(), Fonts.REGULAR, Gravity.CENTER), 44 + 20, 20 + 140 + 28, 388, 202);
        }
        int rw = (428 - 40 - 20) / 2, ry = 520 - 28 - 74;
        put(icon(rec ? "voice_ic_pause_d" : "voice_ic_start_n", new Runnable() { public void run() { toggleRecord(); } }), 44 + 20, ry, rw, 74);
        put(icon(rec ? "voice_ic_end_n" : "voice_ic_end_d", new Runnable() { public void run() { if (recorder != null) stopRecording(); } }), 44 + 20 + rw + 20, ry, rw, 74);
        // list x = 44+428+40
        if (items.isEmpty()) {
            put(txt(qs(rec ? "Voice memos are saved in this system until deleted. Please go to the menu to delete." : "There are no saved Voice Memos."),
                    32, c1(), Fonts.REGULAR, Gravity.CENTER), 512, 0, 1920 - 512 - 512, 720 - top - 130);
        } else {
            hscroll(false);
        }
    }

    private void buildDelete() {
        put(txt("Edit", 32, c1(), Fonts.SEMIBOLD, Gravity.BOTTOM), 44 + 50, 0, 300, 84);
        put(txt("(" + marked.size() + ")", 32, c2(), Fonts.REGULAR, Gravity.BOTTOM), 44 + 50 + 110, 0, 200, 84);
        put(line(), 44 + 50, 86, 422, 2);
        put(button("Delete", marked.isEmpty() ? 0x55 : 0xFF, new Runnable() { public void run() {
            for (Item i : marked) if (i.own) { if (i == playing) stopPlayback(); i.f.delete(); }
            deleteMode = false; reload(); build(); } }), 44 + 44, 86 + 2 + 20, 428, 92);
        put(button("Mark All", 0xFF, new Runnable() { public void run() { marked.clear(); marked.addAll(items); build(); } }), 44 + 44, 86 + 2 + 20 + 102, 428, 92);
        put(button("Unmark All", 0xFF, new Runnable() { public void run() { marked.clear(); build(); } }), 44 + 44, 86 + 2 + 20 + 204, 428, 92);
        hscroll(true);
    }

    private void hscroll(boolean del) {
        android.widget.HorizontalScrollView sv = new android.widget.HorizontalScrollView(this);
        sv.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (final Item it : items) {
            Card c = new Card(this, it, del);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(292, 512);
            lp.rightMargin = 16;
            row.addView(c, lp);
        }
        sv.addView(row);
        int x = del ? 44 + 492 : 44 + 428 + 40;
        put(sv, x, 0, 1920 - x, 520);
    }

    private final class Card extends View {
        private final Item it; private final boolean del;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private long dur = Long.MIN_VALUE;
        Card(Context c, Item it, boolean del) {
            super(c); this.it = it; this.del = del; setClickable(true);
            p.setTypeface(Fonts.get(c, Fonts.REGULAR));
            p.setTextAlign(Paint.Align.CENTER);
        }
        @Override public boolean onTouchEvent(MotionEvent e) {
            int a = e.getAction();
            if (a == MotionEvent.ACTION_DOWN || a == MotionEvent.ACTION_CANCEL || a == MotionEvent.ACTION_UP) {
                setPressed(a == MotionEvent.ACTION_DOWN); invalidate();
            }
            if (a == MotionEvent.ACTION_UP) {
                if (e.getY() > 512 - 16 - 74 && !del) {
                    if (it.own) { marked.clear(); marked.add(it); deleteMode = true; build(); }
                } else if (del) {
                    if (marked.contains(it)) marked.remove(it); else marked.add(it);
                    build();
                } else play(it);
            }
            return true;
        }
        @Override protected void onDraw(Canvas c) {
            boolean sel = del && marked.contains(it);
            String bg = sel ? "co_bg_list_h_s" : isPressed() ? "co_bg_list_h_p" : "co_bg_list_h_n";
            nine(c, bitmap(bg + ".9.png"), 0, 0, 292, 512);
            nine(c, bitmap("co_bg_list_h_header.9.png"), 0, 0, 292, 240);
            if (dur == Long.MIN_VALUE) dur = duration(it);
            p.setColor(c2()); p.setTextSize(28);
            c.drawText(dur < 0 ? "--" : mmss(dur), 146, 10 + 28, p);
            Bitmap w = Art.ink(bitmap("voice_ic_wave_default.png"));
            if (w != null) c.drawBitmap(w, null, new Rect(2, 50, 290, 210), null);
            p.setColor(c1());
            String t = it.f.getName();
            while (t.length() > 3 && p.measureText(t) > 260) t = t.substring(0, t.length() - 1);
            c.drawText(t, 146, 240 + 18 + 28, p);
            p.setColor(c2());
            c.drawText(date(it.f), 146, 240 + 18 + 40 + 10 + 28, p);
            if (!del) {
                Bitmap d = Art.ink(bitmap(it.own ? "voice_ic_deleted_n.png" : "voice_ic_deleted_d.png"));
                if (d != null) c.drawBitmap(d, 146 - d.getWidth() / 2f, 512 - 16 - 74 + (74 - d.getHeight()) / 2f, null);
            }
        }
    }

    private final class Progress extends View {
        private final float f; private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Progress(Context c, float f) { super(c); this.f = f; }
        @Override protected void onDraw(Canvas c) {
            int h = getHeight(), w = getWidth();
            p.setColor(wash()); c.drawRoundRect(new RectF(0, h / 2 - 3, w, h / 2 + 3), 3, 3, p);
            p.setColor(Palette.ACCENT); c.drawRoundRect(new RectF(0, h / 2 - 3, w * f, h / 2 + 3), 3, 3, p);
        }
    }

    private static final class Wave extends View {
        private final int amp; private final Paint p = new Paint();
        Wave(Context c, int amp) { super(c); this.amp = amp; }
        @Override protected void onDraw(Canvas c) {
            p.setColor(0xFFD9312A);
            int h = getHeight(), n = getWidth() / 4;
            float a = Math.max(2, Math.min(h, amp / 32767f * h));
            for (int i = 0; i < n; i++) c.drawRect(i * 4, h / 2 - (i == n - 1 ? a / 2 : 1), i * 4 + 2, h / 2 + (i == n - 1 ? a / 2 : 1), p);
        }
    }

    // ---- helpers ----
    private Bitmap bitmap(String n) {
        Bitmap b = cache.get(n);
        if (b != null) return b;
        try {
            InputStream in = getAssets().open("ccnc/voicememo/" + n);
            b = BitmapFactory.decodeStream(in); in.close();
            if (n.endsWith(".9.png") && b != null) b = Bitmap.createBitmap(b, 1, 1, b.getWidth() - 2, b.getHeight() - 2);
        } catch (Throwable t) { b = null; }
        if (b != null) cache.put(n, b);
        return b;
    }
    private final java.util.HashMap<String, Bitmap> cache = new java.util.HashMap<String, Bitmap>();

    private static void nine(Canvas c, Bitmap b, int x, int y, int w, int h) {
        if (b == null) return;
        int k = Math.min(24, Math.min(b.getWidth(), b.getHeight()) / 2), bw = b.getWidth(), bh = b.getHeight();
        int[] sx = {0, k, bw - k, bw}, sy = {0, k, bh - k, bh};
        int[] dx = {x, x + k, x + w - k, x + w}, dy = {y, y + k, y + h - k, y + h};
        for (int i = 0; i < 3; i++) for (int j = 0; j < 3; j++)
            c.drawBitmap(b, new Rect(sx[i], sy[j], sx[i + 1], sy[j + 1]), new Rect(dx[i], dy[j], dx[i + 1], dy[j + 1]), null);
    }

    private void put(View v, int x, int y, int w, int h) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w, h);
        lp.leftMargin = x; lp.topMargin = y;
        stage.addView(v, lp);
    }

    private View img(String n, int w, int h) {
        android.widget.ImageView v = new android.widget.ImageView(this);
        v.setImageBitmap(Art.ink(bitmap(n + ".png")));
        v.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        return v;
    }

    private View icon(String n, final Runnable r) {
        android.widget.ImageView v = (android.widget.ImageView) img(n, 0, 0);
        if (!n.endsWith("_d")) { v.setClickable(true); v.setOnClickListener(new View.OnClickListener() { public void onClick(View x) { r.run(); } }); }
        return v;
    }

    private View button(String t, int alpha, final Runnable r) {
        TextView v = txt(t, 40, c1(), Fonts.REGULAR, Gravity.CENTER);
        v.setBackgroundColor(wash());
        v.setAlpha(alpha / 255f);
        if (alpha == 0xFF) { v.setClickable(true); v.setOnClickListener(new View.OnClickListener() { public void onClick(View x) { r.run(); } }); }
        return v;
    }

    private View line() { View v = new View(this); v.setBackgroundColor(wash()); return v; }

    private TextView txt(String t, float px, int color, int face, int grav) {
        TextView v = new TextView(this);
        v.setText(t); v.setTextSize(TypedValue.COMPLEX_UNIT_PX, px);
        v.setTextColor(color); v.setTypeface(Fonts.get(this, face)); v.setGravity(grav);
        return v;
    }

    private static String mmss(long ms) {
        long s = (ms + 500) / 1000;
        return String.format(Locale.US, "%d:%02d", s / 60, s % 60);
    }

    private static String date(File f) {
        return new SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.US).format(new Date(f.lastModified()));
    }
}
