package dev.ccnc.weather;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;

import java.util.Calendar;

import dev.ccnc.common.Art;
import dev.ccnc.common.Fonts;

/**
 * ccNC Weather scene (WEATHER.Main) painted in 1920 x 720 screen coordinates: location row, then the
 * Forecast tab (6 columns of 292 px) or the Today tab (Now / 3 Hours Later / 6 Hours Later).
 * Coordinates, font sizes and colours come from the recovered weatherhmi QML.
 */
final class WeatherView extends View {

    private static final float DW = 1920f, DH = 720f;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int DIM = 0x80DFEDFE;   // HYUNDAI vendor colour for secondary values
    private static final String[] ICON = {"", "clear", "few_clouds", "broken_clouds", "overcast", "cloudy_clearing_later",
            "clear_cloudy_later", "showers", "showers", "showers", "showers", "showers", "showers",
            "getting_cloudy_rain_later", "getting_cloudy_rain_later", "rain_clear_later", "rain_clear_later", "rain_clear_later",
            "cloudy_snow_later", "cloudy_snow_later", "cloudy_snow_later", "getting_cloudy_snow_later", "getting_cloudy_snow_later",
            "snow_clear_later", "snow_clear_later", "snow_clear_later", "rain_snow", "rain_snow", "rain_snow",
            "getting_cloudy_rain_snow_later", "getting_cloudy_rain_snow_later", "snow_rain", "snow_rain", "snow_rain",
            "getting_cloudy_snow_rain_later", "getting_cloudy_snow_rain_later", "rain_snow_clear_later", "rain_snow_clear_later",
            "rain_snow_clear_later", "thunderstorms", "misty"};
    private static final String[] TEXT = {"", "Clear", "Few Clouds", "Broken Clouds", "Overcast", "Cloudy, Clearing Later",
            "Clear, Cloudy Later", "Showers", "Showers", "Showers", "Rain", "Rain", "Rain", "Rain", "Rain",
            "Rain, Clear Later", "Rain, Clear Later", "Rain, Clear Later", "Snow", "Snow", "Snow", "Snow", "Snow",
            "Snow, Clear Later", "Snow, Clear Later", "Snow, Clear Later", "Sleet", "Sleet", "Sleet", "Sleet", "Sleet",
            "Sleet", "Sleet", "Sleet", "Sleet", "Sleet", "Sleet, Clear Later", "Sleet, Clear Later", "Sleet, Clear Later",
            "Thunderstorms", "Misty"};
    private static final String[] DAYS = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
    private static final String[] MONTHS = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};

    private Feed.Data data;
    boolean forecastTab = true;
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bmp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF r = new RectF();
    private float k;

    WeatherView(Context c) { super(c); }

    void setData(Feed.Data d) { data = d; invalidate(); }

    private float X(float d) { return d * k; }

    private void txt(Canvas c, String s, float x, float cy, float w, float size, int color, int face, Paint.Align a) {
        if (s == null || s.length() == 0) return;
        text.setTypeface(Fonts.get(getContext(), face));
        text.setTextSize(X(size));
        text.setColor(color);
        text.setTextAlign(Paint.Align.LEFT);
        String t = TextUtils.ellipsize(s, new TextPaint(text), X(w), TextUtils.TruncateAt.END).toString();
        float tw = text.measureText(t);
        float px = a == Paint.Align.CENTER ? X(x) + (X(w) - tw) / 2f : a == Paint.Align.RIGHT ? X(x + w) - tw : X(x);
        Paint.FontMetrics fm = text.getFontMetrics();
        c.drawText(t, px, X(cy) - (fm.ascent + fm.descent) / 2f, text);
    }

    private void img(Canvas c, Bitmap b, float x, float y, float w, float h) {
        if (b == null) return;
        if (w <= 0) { w = b.getWidth(); h = b.getHeight(); }
        r.set(X(x), X(y), X(x + w), X(y + h));
        c.drawBitmap(b, null, r, bmp);
    }

    private Bitmap wx(int icon, boolean night) {
        if (icon < 1 || icon >= ICON.length) return null;
        Bitmap b = Art.weather(getContext(), ICON[icon] + (night ? "_night" : "_day"));
        return b != null ? b : Art.weather(getContext(), ICON[icon] + "_day");
    }

    private String temp(double c) {
        if (Double.isNaN(c)) return "";
        return data != null && data.fahrenheit ? Math.round(c * 9 / 5 + 32) + "°F" : Math.round(c) + "°C";
    }

    private static String pct(double v) { return Double.isNaN(v) ? "" : Math.round(v) + "%"; }

    private static String dayLabel(String t) {
        if (t == null || t.length() < 8 || !t.substring(0, 8).matches("\\d{8}")) return "";
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(Integer.parseInt(t.substring(0, 4)), Integer.parseInt(t.substring(4, 6)) - 1, Integer.parseInt(t.substring(6, 8)));
        return DAYS[c.get(Calendar.DAY_OF_WEEK) - 1] + ", " + MONTHS[c.get(Calendar.MONTH)] + " " + c.get(Calendar.DAY_OF_MONTH);
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_UP && e.getY() < X(94)) {
            float x = e.getX() / k;
            if (x > 560 && x < 960) forecastTab = true;
            else if (x >= 960 && x < 1360) forecastTab = false;
            else return true;
            invalidate();
        }
        return true;
    }

    @Override protected void onDraw(Canvas c) {
        k = Math.min(getWidth() / DW, getHeight() / DH);
        // Status-bar tab bar: Forecast | Today
        txt(c, "Forecast", 560, 47, 400, 32, forecastTab ? WHITE : DIM, Fonts.SEMIBOLD, Paint.Align.CENTER);
        txt(c, "Today", 960, 47, 400, 32, forecastTab ? DIM : WHITE, Fonts.SEMIBOLD, Paint.Align.CENTER);
        if (data == null) return;

        // Location row
        img(c, Art.weather(getContext(), "we_ic_current"), 36, 106, 32, 32);
        txt(c, data.place, 84, 122, 532, 24, WHITE, Fonts.SEMIBOLD, Paint.Align.LEFT);
        img(c, Art.weather(getContext(), "we_ic_logo_vaisala"), 24 + 1852 - 156 + 4, 101, 147, 42);

        if (forecastTab) drawForecast(c); else drawToday(c);
    }

    private void drawForecast(Canvas c) {
        float ox = 44, oy = 150;
        Bitmap line = Art.weather(getContext(), "we_bg_list_line_01");
        for (int i = 0; i < 5; i++) img(c, line, ox + 292 + 7 + i * 308, oy, 3, 446);
        for (int i = 0; i < 6 && i < data.days.size(); i++) {
            Feed.Slot s = data.days.get(i);
            float x = ox + i * 308 + 20;
            txt(c, dayLabel(s.time), x, oy + 40, 252, 32, WHITE, Fonts.REGULAR, Paint.Align.CENTER);
            img(c, wx(s.icon, false), x + 58, oy + 104, 136, 136);
            if (s.icon > 0 && s.icon < TEXT.length) txt(c, TEXT[s.icon], x, oy + 289, 252, 32, WHITE, Fonts.REGULAR, Paint.Align.CENTER);
            String mx = temp(s.temp), mn = Double.isNaN(s.min) ? "" : "/ " + temp(s.min);
            text.setTypeface(Fonts.get(getContext(), Fonts.REGULAR));
            text.setTextSize(X(32));
            float wmx = text.measureText(mx) / k, wmn = text.measureText(mn) / k;
            float lx = x + (252 - wmx - 6 - wmn) / 2f;
            txt(c, mx, lx, oy + 446 - 8 - 94 + 20, wmx + 2, 32, WHITE, Fonts.REGULAR, Paint.Align.LEFT);
            txt(c, mn, lx + wmx + 6, oy + 446 - 8 - 94 + 20, wmn + 2, 32, DIM, Fonts.REGULAR, Paint.Align.LEFT);
            String rain = pct(s.rain);
            if (rain.length() > 0) {
                text.setTextSize(X(32));
                float wr = text.measureText(rain) / k;
                float rx = x + (252 - 32 - 8 - wr) / 2f;
                img(c, Art.weather(getContext(), "we_ic_umb_pro"), rx, oy + 446 - 8 - 94 + 54, 32, 32);
                txt(c, rain, rx + 40, oy + 446 - 8 - 94 + 70, wr + 2, 32, DIM, Fonts.REGULAR, Paint.Align.LEFT);
            }
        }
    }

    private Feed.Slot slot(int i) { return i < data.today.size() ? data.today.get(i) : null; }

    private void drawToday(Canvas c) {
        float oy = 150;
        Bitmap line = Art.weather(getContext(), "we_bg_list_line_01");
        img(c, line, 958, oy, 3, 446);
        img(c, line, 1426, oy, 3, 446);
        // Now
        Feed.Slot n = slot(0);
        txt(c, "Now", 153, oy + 20 + 24, 684, 40, WHITE, Fonts.REGULAR, Paint.Align.CENTER);
        if (n != null) {
            img(c, wx(n.icon, n.dayNight == 1), 153, oy + 107, 296, 296);
            txt(c, temp(n.temp), 469, oy + 107 + 40, 368, 60, WHITE, Fonts.REGULAR, Paint.Align.CENTER);
            if (n.icon > 0 && n.icon < TEXT.length) txt(c, TEXT[n.icon], 469, oy + 107 + 108 + 40, 368, 32, WHITE, Fonts.REGULAR, Paint.Align.CENTER);
        }
        later(c, 979, "3 Hours Later", slot(1), oy);
        later(c, 1447, "6 Hours Later", slot(2), oy);
    }

    private void later(Canvas c, float x, String title, Feed.Slot s, float oy) {
        float ix = x + 12, iy = oy + 21;
        txt(c, title, ix, iy + 24, 405, 40, WHITE, Fonts.REGULAR, Paint.Align.CENTER);
        if (s == null) return;
        Bitmap b = wx(s.icon, s.dayNight == 1);
        if (b != null) img(c, b, ix + (405 - b.getWidth()) / 2f, iy + 48 + 5 + (168 - b.getHeight()) / 2f, 0, 0);
        if (s.icon > 0 && s.icon < TEXT.length) txt(c, TEXT[s.icon], ix, iy + 48 + 5 + 168 + 11 + 37, 405, 32, WHITE, Fonts.REGULAR, Paint.Align.CENTER);
        float py = iy + 48 + 5 + 168 + 11 + 74 + 21, cy = py + 39;
        img(c, Art.weather(getContext(), "we_ic_temp"), ix + 50, cy - 16, 32, 32);
        txt(c, temp(s.temp), ix + 86, cy, 94, 32, DIM, Fonts.REGULAR, Paint.Align.CENTER);
        img(c, Art.weather(getContext(), "we_bg_list_line_02"), ix + 202, cy - 16, 1, 32);
        img(c, Art.weather(getContext(), "we_ic_umb_pro"), ix + 217, cy - 16, 32, 32);
        txt(c, pct(s.rain), ix + 253, cy, 94, 32, DIM, Fonts.REGULAR, Paint.Align.CENTER);
    }
}
