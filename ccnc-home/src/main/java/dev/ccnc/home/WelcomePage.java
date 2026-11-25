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
 * left, and two widgets on the right, the weather and the connected phone. Tapping a
 * widget opens it; the app cards follow on the next pages.
 */
final class WelcomePage extends View {

    interface Listener {
        void onWeatherTapped();
        void onPhoneTapped();
    }

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
    private final RectF weatherBox = new RectF(), phoneBox = new RectF(), tmp = new RectF();
    private Theme shaded;
    private String driver; // active user profile's name, if it has a real one
    private int pressed; // 0 none, 1 weather, 2 phone

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
                pressed = weatherBox.contains(x, y) ? 1 : phoneBox.contains(x, y) ? 2 : 0;
                invalidate();
                return pressed != 0;
            case MotionEvent.ACTION_UP:
                int p = pressed;
                pressed = 0;
                invalidate();
                if (p == 1 && weatherBox.contains(x, y)) listener.onWeatherTapped();
                if (p == 2 && phoneBox.contains(x, y)) listener.onPhoneTapped();
                return true;
            case MotionEvent.ACTION_CANCEL:
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
        phoneBox.set(right - boxW, 0, right, h);
        weatherBox.set(right - 2 * boxW - gap, 0, right - boxW - gap, h);
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
        float leftW = weatherBox.left - side - 24f * dp;
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

        // Weather widget.
        drawBox(c, weatherBox, pressed == 1);
        float pad = 26f * dp, avail = weatherBox.width() - 2 * pad;
        float icon = Math.min(weatherBox.width() * 0.34f, 90f * dp);
        if (weatherIcon != null) {
            tmp.set(weatherBox.left + pad, weatherBox.top + pad, weatherBox.left + pad + icon, weatherBox.top + pad + icon);
            c.drawBitmap(weatherIcon, null, tmp, bmp);
        }
        title.setTextSize(22f * dp);
        c.drawText("Weather", weatherBox.left + pad, weatherBox.top + pad + icon + 34f * dp, title);
        big.setTextSize(64f * dp);
        String tv = temp.isEmpty() ? "--" : temp;
        fit(big, tv, avail);
        c.drawText(tv, weatherBox.left + pad, weatherBox.bottom - pad - 52f * dp, big);
        sub.setTextSize(18f * dp);
        if (!condition.isEmpty()) line(c, condition, weatherBox.left + pad, weatherBox.bottom - pad - 26f * dp, avail);
        if (!place.isEmpty()) line(c, place, weatherBox.left + pad, weatherBox.bottom - pad, avail);

        // Phone widget.
        drawBox(c, phoneBox, pressed == 2);
        avail = phoneBox.width() - 2 * pad;
        float lx = phoneBox.left + pad, ly = phoneBox.top + pad;
        if (npTitle != null && (phoneLogo != null || phoneArt != null)) {
            drawNowPlaying(c, lx, ly, avail, pad);
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
    private void drawNowPlaying(Canvas c, float lx, float ly, float avail, float pad) {
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
