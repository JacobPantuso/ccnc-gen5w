package dev.ccnc.home;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.MotionEvent;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * First home page, like ccNC's widget page: a greeting, the time and date large on the
 * left, and two cards on the right. Each card shows what the owner chose (long-press a card):
 *   weather       the weather
 *   phone         the connected phone and what it is playing (CarPlay / Android Auto / Bluetooth)
 *   ev            battery level and range
 *   app:<key>     any app: its icon and name
 * Default: weather, phone. Tapping a card opens it; the app cards follow on the next pages.
 */
final class WelcomePage extends View {

    interface Listener {
        void onSlotTapped(int slot, String kind);
        void onSlotLongPressed(int slot);
    }

    /** What each card shows (see the class comment). */
    final String[] slots = {"weather", "phone"};
    // EV card
    private int evSoc = -1, evRange = -1;
    private boolean evKm = true;
    // App cards
    private final Bitmap[] appArt = new Bitmap[2];
    private final Drawable[] appIcon = new Drawable[2];
    private final String[] appLabel = {"", ""};

    private final float dp;
    private final Listener listener;
    private final TextPaint greet = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint clock = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint ampm = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint date = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint big = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint title = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint sub = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint card = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint press = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bmp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF[] boxes = {new RectF(), new RectF()};
    private final RectF tmp = new RectF();
    private Theme shaded;
    private String driver; // active user profile's name, if it has a real one
    private int pressed; // 0 none, 1 first card, 2 second card
    private boolean longFired;
    private final Runnable longPress = new Runnable() { @Override public void run() {
        if (pressed == 0) return;
        int p = pressed;
        longFired = true;
        pressed = 0;
        invalidate();
        listener.onSlotLongPressed(p - 1);
    } };

    // Weather widget
    private Bitmap weatherIcon;
    private String condition = "", temp = "", place = "";
    // Phone widget
    private Drawable phoneLogo;
    private Bitmap phoneArt;
    // What the phone is playing (CarPlay / Android Auto / Bluetooth audio): shown on the phone widget in place of the plain status.
    private String npTitle, npArtist;
    private Bitmap npArt;
    private android.graphics.BitmapShader npShader;
    private String phoneTitle = "Phone", phoneLine = "", phoneName;

    WelcomePage(Context c, Typeface bold, Typeface regular, Listener listener) {
        super(c);
        this.listener = listener;
        dp = c.getResources().getDisplayMetrics().density;
        greet.setTypeface(regular);
        clock.setTypeface(regular);
        ampm.setTypeface(bold);
        date.setTypeface(regular);
        big.setTypeface(regular);
        title.setTypeface(bold);
        sub.setTypeface(regular);
        border.setStyle(Paint.Style.STROKE);
        border.setStrokeWidth(Math.max(1f, dp * 0.75f));
        setClickable(true);
    }

    void setWeather(Bitmap icon, String condition, String temp, String place) {
        weatherIcon = icon;
        this.condition = nz(condition);
        this.temp = nz(temp);
        this.place = nz(place);
        invalidate();
    }

    /** {@code logo} (real CarPlay/AA logo) or {@code art} (ccNC icon) is drawn; name may be null. */
    void setPhone(Drawable logo, Bitmap art, String title, String line, String name) {
        phoneLogo = logo;
        phoneArt = art;
        phoneTitle = nz(title);
        phoneLine = nz(line);
        phoneName = name;
        invalidate();
    }

    /** The track the connected phone is playing, or title == null for none. */
    void setNowPlaying(String title, String artist, Bitmap art) {
        if (art != npArt) { npArt = art; npShader = null; }
        npTitle = title;
        npArtist = artist;
        invalidate();
    }

    /** Battery % and range for the EV card (-1 = unknown). */
    void setEv(int soc, int range, boolean km) { evSoc = soc; evRange = range; evKm = km; invalidate(); }

    /** An app card's picture (ccNC art, else the app's own icon) and name. */
    void setApp(int slot, Bitmap art, Drawable icon, String label) {
        appArt[slot] = art; appIcon[slot] = icon; appLabel[slot] = nz(label); invalidate();
    }

    /** The active profile's name; the greeting uses it ahead of the phone owner. */
    void setDriver(String name) {
        driver = name;
        invalidate();
    }

    private static String nz(String s) { return s == null ? "" : s; }

    /** "Jacob's iPhone" → "Jacob"; null when the name doesn't say whose it is. */
    static String owner(String phone) {
        if (phone == null) return null;
        Matcher m = Pattern.compile("^\\s*([\\p{L}][\\p{L}\\-]{0,20})['’]s\\b").matcher(phone);
        return m.find() ? m.group(1) : null;
    }

    @Override public boolean onTouchEvent(MotionEvent ev) {
        float x = ev.getX(), y = ev.getY();
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                pressed = boxes[0].contains(x, y) ? 1 : boxes[1].contains(x, y) ? 2 : 0;
                longFired = false;
                removeCallbacks(longPress);
                if (pressed != 0) postDelayed(longPress, android.view.ViewConfiguration.getLongPressTimeout());
                invalidate();
                return pressed != 0;
            case MotionEvent.ACTION_MOVE:
                if (pressed != 0 && !boxes[pressed - 1].contains(x, y)) { removeCallbacks(longPress); pressed = 0; invalidate(); }
                return true;
            case MotionEvent.ACTION_UP:
                removeCallbacks(longPress);
                int p = pressed;
                pressed = 0;
                invalidate();
                if (!longFired && p != 0 && boxes[p - 1].contains(x, y)) listener.onSlotTapped(p - 1, slots[p - 1]);
                return true;
            case MotionEvent.ACTION_CANCEL:
                removeCallbacks(longPress);
                pressed = 0;
                invalidate();
                return true;
            default:
                return pressed != 0;
        }
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) { shaded = null; }

    @Override protected void onDraw(Canvas c) {
        Theme t = Theme.current;
        float w = getWidth(), h = getHeight();
        float side = 56f * dp;

        // Right half: the two widgets, as tall as the app cards.
        float gap = 18f * dp;
        float boxW = Math.min(330f * dp, (w * 0.56f - gap) / 2f);
        float right = w - 36f * dp;
        boxes[1].set(right - boxW, 0, right, h);
        boxes[0].set(right - 2 * boxW - gap, 0, right - boxW - gap, h);
        if (shaded != t) {
            shaded = t;
            card.setShader(new LinearGradient(0, 0, 0, h, t.cardTop, t.cardBottom, Shader.TileMode.CLAMP));
            border.setColor(t.border);
            press.setColor(t.press);
        }
        greet.setColor(t.subtitle);
        clock.setColor(t.title);
        ampm.setColor(t.title);
        date.setColor(t.subtitle);
        big.setColor(t.title);
        title.setColor(t.title);
        sub.setColor(t.subtitle);

        // Left: greeting, clock, date.
        float leftW = boxes[0].left - side - 24f * dp;
        Date now = new Date();
        Locale loc = Locale.getDefault();
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String g = hour < 5 ? "Good night" : hour < 12 ? "Good morning" : hour < 18 ? "Good afternoon" : "Good evening";
        String who = driver != null ? driver : owner(phoneName);
        if (who != null) g += ", " + who;
        greet.setTextSize(30f * dp);
        fit(greet, g, leftW);
        c.drawText(TextUtils.ellipsize(g, greet, leftW, TextUtils.TruncateAt.END).toString(), side, h * 0.22f, greet);

        boolean h24 = DateFormat.is24HourFormat(getContext());
        String tm = new SimpleDateFormat(h24 ? "H:mm" : "h:mm", loc).format(now);
        clock.setTextSize(Math.min(132f * dp, h * 0.42f));
        float clockBase = h * 0.66f;
        c.drawText(tm, side - 4f * dp, clockBase, clock);
        if (!h24) {
            ampm.setTextSize(26f * dp);
            String ap = new SimpleDateFormat("a", Locale.US).format(now).toUpperCase(Locale.US);
            c.drawText(ap, side + clock.measureText(tm) + 8f * dp, clockBase, ampm);
        }
        date.setTextSize(24f * dp);
        String d = new SimpleDateFormat("EEEE, MMMM d", loc).format(now);
        c.drawText(TextUtils.ellipsize(d, date, leftW, TextUtils.TruncateAt.END).toString(), side, h * 0.82f, date);

        for (int i = 0; i < 2; i++) {
            drawBox(c, boxes[i], pressed == i + 1);
            String k = slots[i];
            if (k.equals("weather")) drawWeather(c, boxes[i]);
            else if (k.equals("ev")) drawEv(c, boxes[i]);
            else if (k.startsWith("app:")) drawApp(c, boxes[i], i);
            else drawPhone(c, boxes[i]);
        }
    }

    private float iconSize(RectF b) { return Math.min(b.width() * 0.34f, 90f * dp); }

    private void drawWeather(Canvas c, RectF b) {
        float pad = 26f * dp, avail = b.width() - 2 * pad, icon = iconSize(b);
        if (weatherIcon != null) {
            tmp.set(b.left + pad, b.top + pad, b.left + pad + icon, b.top + pad + icon);
            c.drawBitmap(weatherIcon, null, tmp, bmp);
        }
        title.setTextSize(22f * dp);
        c.drawText("Weather", b.left + pad, b.top + pad + icon + 34f * dp, title);
        big.setTextSize(64f * dp);
        String tv = temp.isEmpty() ? "--" : temp;
        fit(big, tv, avail);
        c.drawText(tv, b.left + pad, b.bottom - pad - 52f * dp, big);
        sub.setTextSize(18f * dp);
        if (!condition.isEmpty()) line(c, condition, b.left + pad, b.bottom - pad - 26f * dp, avail);
        if (!place.isEmpty()) line(c, place, b.left + pad, b.bottom - pad, avail);
    }

    /** Battery level large, a level bar, and the range. */
    private void drawEv(Canvas c, RectF b) {
        float pad = 26f * dp, avail = b.width() - 2 * pad, icon = iconSize(b);
        Bitmap ic = dev.ccnc.common.Art.icon(getContext(), "ev");
        if (ic != null) { tmp.set(b.left + pad, b.top + pad, b.left + pad + icon, b.top + pad + icon); c.drawBitmap(ic, null, tmp, bmp); }
        title.setTextSize(22f * dp);
        c.drawText("Battery", b.left + pad, b.top + pad + icon + 34f * dp, title);
        big.setTextSize(64f * dp);
        String sv = evSoc < 0 ? "--" : evSoc + "%";
        fit(big, sv, avail);
        c.drawText(sv, b.left + pad, b.bottom - pad - 52f * dp, big);
        // level bar
        float by = b.bottom - pad - 40f * dp;
        tmp.set(b.left + pad, by, b.right - pad, by + 6f * dp);
        c.drawRoundRect(tmp, 3f * dp, 3f * dp, press);
        if (evSoc >= 0) {
            Paint f = new Paint(Paint.ANTI_ALIAS_FLAG);
            f.setColor(Theme.current.accent());
            tmp.set(b.left + pad, by, b.left + pad + (b.width() - 2 * pad) * Math.min(100, evSoc) / 100f, by + 6f * dp);
            c.drawRoundRect(tmp, 3f * dp, 3f * dp, f);
        }
        sub.setTextSize(18f * dp);
        line(c, evRange < 0 ? "Range --" : "Range " + evRange + (evKm ? " km" : " mi"), b.left + pad, b.bottom - pad, avail);
    }

    /** Any app: its picture and name. */
    private void drawApp(Canvas c, RectF b, int i) {
        float pad = 26f * dp, avail = b.width() - 2 * pad, icon = Math.min(b.width() * 0.42f, 110f * dp);
        float cx = b.centerX(), cy = b.top + b.height() * 0.42f;
        if (appArt[i] != null) {
            tmp.set(cx - icon / 2f, cy - icon / 2f, cx + icon / 2f, cy + icon / 2f);
            c.drawBitmap(appArt[i], null, tmp, bmp);
        } else if (appIcon[i] != null) {
            appIcon[i].setBounds((int) (cx - icon / 2f), (int) (cy - icon / 2f), (int) (cx + icon / 2f), (int) (cy + icon / 2f));
            appIcon[i].draw(c);
        }
        title.setTextSize(26f * dp);
        String l = appLabel[i].isEmpty() ? "App" : appLabel[i];
        fit(title, l, avail);
        title.setTextAlign(Paint.Align.CENTER);
        c.drawText(TextUtils.ellipsize(l, title, avail, TextUtils.TruncateAt.END).toString(), cx, b.bottom - pad - 10f * dp, title);
        title.setTextAlign(Paint.Align.LEFT);
    }

    private void drawPhone(Canvas c, RectF phoneBox) {
        float pad = 26f * dp, avail = phoneBox.width() - 2 * pad, icon = iconSize(phoneBox);
        float lx = phoneBox.left + pad, ly = phoneBox.top + pad;
        if (npTitle != null && (phoneLogo != null || phoneArt != null)) {
            drawNowPlaying(c, phoneBox, lx, ly, avail, pad);
            return;
        }
        if (phoneLogo != null) {
            phoneLogo.setBounds((int) lx, (int) ly, (int) (lx + icon), (int) (ly + icon));
            phoneLogo.draw(c);
        } else if (phoneArt != null) {
            tmp.set(lx, ly, lx + icon, ly + icon);
            c.drawBitmap(phoneArt, null, tmp, bmp);
        }
        title.setTextSize(22f * dp);
        fit(title, phoneTitle, avail);
        c.drawText(TextUtils.ellipsize(phoneTitle, title, avail, TextUtils.TruncateAt.END).toString(),
                lx, phoneBox.top + pad + icon + 34f * dp, title);
        String nm = phoneName != null ? phoneName : "";
        if (!nm.isEmpty()) {
            big.setTextSize(30f * dp);
            fit(big, nm, avail);
            c.drawText(TextUtils.ellipsize(nm, big, avail, TextUtils.TruncateAt.END).toString(),
                    lx, phoneBox.bottom - pad - 30f * dp, big);
        }
        sub.setTextSize(18f * dp);
        if (!phoneLine.isEmpty()) line(c, phoneLine, lx, phoneBox.bottom - pad, avail);
    }

    /** Cover (or the phone's logo) on top, then the track and artist, then who it is playing from. */
    private void drawNowPlaying(Canvas c, RectF phoneBox, float lx, float ly, float avail, float pad) {
        float art = Math.min(avail, phoneBox.height() * 0.46f);
        float rad = 12f * dp;
        tmp.set(lx, ly, lx + art, ly + art);
        if (npArt != null) {
            if (npShader == null) npShader = new android.graphics.BitmapShader(npArt, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            android.graphics.Matrix m = new android.graphics.Matrix();
            float sc = Math.max(art / npArt.getWidth(), art / npArt.getHeight());
            m.setScale(sc, sc);
            m.postTranslate(lx + (art - npArt.getWidth() * sc) / 2f, ly + (art - npArt.getHeight() * sc) / 2f);
            npShader.setLocalMatrix(m);
            bmp.setShader(npShader);
            c.drawRoundRect(tmp, rad, rad, bmp);
            bmp.setShader(null);
        } else {
            // No cover from the phone: the CarPlay / Android Auto logo, centred in a plain tile.
            c.drawRoundRect(tmp, rad, rad, press);
            float s = art * 0.55f, cx = lx + art / 2f, cy = ly + art / 2f;
            if (phoneLogo != null) {
                phoneLogo.setBounds((int) (cx - s / 2f), (int) (cy - s / 2f), (int) (cx + s / 2f), (int) (cy + s / 2f));
                phoneLogo.draw(c);
            } else if (phoneArt != null) {
                tmp.set(cx - s / 2f, cy - s / 2f, cx + s / 2f, cy + s / 2f);
                c.drawBitmap(phoneArt, null, tmp, bmp);
            }
        }
        float ty = ly + art + 40f * dp;
        title.setTextSize(23f * dp);
        fit(title, npTitle, avail);
        c.drawText(TextUtils.ellipsize(npTitle, title, avail, TextUtils.TruncateAt.END).toString(), lx, ty, title);
        sub.setTextSize(19f * dp);
        if (npArtist != null && !npArtist.isEmpty()) line(c, npArtist, lx, ty + 29f * dp, avail);
        sub.setTextSize(15f * dp);
        String from = phoneName != null && !phoneName.isEmpty() ? phoneTitle + "  \u00B7  " + phoneName : phoneTitle;
        line(c, from, lx, phoneBox.bottom - pad, avail);
    }

    private void drawBox(Canvas c, RectF r, boolean down) {
        float rad = 14f * dp;
        c.drawRoundRect(r, rad, rad, card);
        tmp.set(r);
        tmp.inset(border.getStrokeWidth() / 2, border.getStrokeWidth() / 2);
        c.drawRoundRect(tmp, rad, rad, border);
        if (down) c.drawRoundRect(r, rad, rad, press);
    }

    private void line(Canvas c, String s, float x, float y, float avail) {
        float size = sub.getTextSize();
        fit(sub, s, avail);
        c.drawText(TextUtils.ellipsize(s, sub, avail, TextUtils.TruncateAt.END).toString(), x, y, sub);
        sub.setTextSize(size);
    }

    private static void fit(TextPaint p, String text, float avail) {
        float w = p.measureText(text);
        if (w > avail) p.setTextSize(p.getTextSize() * Math.max(0.6f, avail / w * 0.97f));
    }
}
