package dev.ccnc.settings.sections;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import dev.ccnc.settings.api.Fn;
import dev.ccnc.settings.api.Host;
import dev.ccnc.settings.api.Page;

/**
 * Settings > Screen Theme > Theme Store: the online ccNC Theme Store, run by ccNC Home (ThemeStore / ThemeProvider):
 * this page lists what Home reports and asks Home to refresh, install, update or remove. Nothing installs by itself.
 */
final class ThemeStorePage {
    private ThemeStorePage() {}

    private static final Uri BASE = Uri.parse("content://dev.ccnc.home.theme");
    private static final Handler ui = new Handler(Looper.getMainLooper());
    private static boolean askedOnce;

    static final class Theme {
        String id, name, author, github, version, description, brands, installed, state;
        long size;
    }

    static void build(final Context ctx, Page p) {
        final Host host = p.host();
        long fetched = 0;
        boolean refreshing = false;
        String error = null;
        int fresh = 0, updates = 0;
        Cursor c = null;
        try {
            c = ctx.getContentResolver().query(Uri.withAppendedPath(BASE, "store_info"), null, null, null, null);
            if (c != null && c.moveToFirst()) {
                fetched = c.getLong(0); refreshing = c.getInt(1) != 0; error = c.getString(2); fresh = c.getInt(3); updates = c.getInt(4);
            }
        } catch (Throwable t) {
            p.heading("Theme Store");
            p.value("Needs a newer ccNC Home", null, null);
            return;
        } finally { if (c != null) c.close(); }
        if (fetched == 0 && !refreshing && !askedOnce) { askedOnce = true; call(ctx, "store_refresh", null); refreshing = true; }

        final List<Theme> list = themes(ctx);
        boolean busy = refreshing;
        for (Theme t : list) if (t.state != null && !t.state.startsWith("error")) busy = true;

        p.heading("ccNC Theme Store");
        final String status = refreshing ? "Checking for themes\u2026"
                : error != null ? "Couldn't reach the store: " + error
                : fetched == 0 ? "Connect to Wi-Fi, then check for themes"
                : (fresh + updates == 0 ? "Everything is up to date" : (fresh > 0 ? fresh + " new" : "") + (fresh > 0 && updates > 0 ? " \u00b7 " : "")
                   + (updates > 0 ? updates + " update" + (updates == 1 ? "" : "s") : ""));
        p.value("Check for themes", new Fn.Str() { @Override public String get() { return status; } }, new Fn.Act() {
            @Override public void run(Host h) { call(ctx, "store_refresh", null); poll(h, 600); }
        });
        String brand = dev.ccnc.common.Fonts.brand(ctx);
        for (final Theme t : list) {
            if (t.brands.length() > 0 && !t.brands.contains(brand)) continue; // made for the other brand
            p.drill(t.name, new Fn.Str() { @Override public String get() { return line(t); } }, new Fn.Build() {
                @Override public void build(Page q) { detail(ctx, q, t.id); }
            });
        }
        if (busy) poll(host, 700);
    }

    private static String line(Theme t) {
        if (t.state != null) return t.state.startsWith("error") ? "Couldn't install: " + t.state.substring(7) : cap(t.state) + "\u2026";
        if (t.installed == null) return "by " + t.author + (t.github != null && t.github.length() > 0 ? " (@" + t.github + ")" : "") + " \u00b7 " + mb(t.size);
        if (newer(t.version, t.installed)) return "Update available: " + t.installed + " \u2192 " + t.version;
        return "Installed \u00b7 " + t.version;
    }

    private static void detail(final Context ctx, Page q, final String id) {
        Theme t = null;
        for (Theme x : themes(ctx)) if (x.id.equals(id)) t = x;
        if (t == null) { q.value("This theme is no longer in the store", null, null); return; }
        final Theme th = t;
        Bitmap prev = preview(ctx, id);
        if (prev != null) q.guideImage(rounded(prev)); // on the right, beside the details
        q.value(th.name, new Fn.Str() { @Override public String get() { return th.description; } }, null);
        q.value("Author", new Fn.Str() { @Override public String get() {
            return th.github == null || th.github.length() == 0 ? th.author : th.author + " (@" + th.github + ")"; } }, null);
        q.value("Version", new Fn.Str() { @Override public String get() {
            return th.installed == null ? th.version : th.installed.equals(th.version) ? th.version + " (installed)" : th.installed + " installed, " + th.version + " available"; } }, null);
        q.value("Download size", new Fn.Str() { @Override public String get() { return mb(th.size); } }, null);
        if (th.state != null) {
            q.value(th.state.startsWith("error") ? "Couldn't install" : cap(th.state) + "\u2026",
                    new Fn.Str() { @Override public String get() { return th.state.startsWith("error") ? th.state.substring(7) : null; } }, null);
            if (!th.state.startsWith("error")) { poll(q.host(), 500); return; }
        }
        if (th.installed == null || newer(th.version, th.installed)) {
            q.button(th.installed == null ? "Install" : "Update", new Fn.Act() {
                @Override public void run(Host h) { call(ctx, "store_install", id); poll(h, 300); }
            });
        }
        if (th.installed != null) {
            q.button("Use this theme", new Fn.Act() {
                @Override public void run(Host h) {
                    try {
                        ContentValues v = new ContentValues();
                        v.put("theme", "pack." + id);
                        ctx.getContentResolver().update(Uri.withAppendedPath(BASE, "theme"), v, null, null);
                    } catch (Throwable ignored) { }
                    if (dev.ccnc.common.Themes.apply(ctx) && ctx instanceof dev.ccnc.settings.MainActivity)
                        ((dev.ccnc.settings.MainActivity) ctx).themeChanged();
                    h.refresh();
                }
            });
            q.button("Remove", new Fn.Act() {
                @Override public void run(Host h) { call(ctx, "store_remove", id); poll(h, 400); }
            });
        }
    }

    private static List<Theme> themes(Context ctx) {
        List<Theme> out = new ArrayList<Theme>();
        Cursor c = null;
        try {
            c = ctx.getContentResolver().query(Uri.withAppendedPath(BASE, "store"), null, null, null, null);
            while (c != null && c.moveToNext()) {
                Theme t = new Theme();
                t.id = c.getString(0); t.name = c.getString(1); t.author = c.getString(2); t.version = c.getString(3);
                t.description = c.getString(4); t.size = c.getLong(5); t.brands = c.getString(8) == null ? "" : c.getString(8);
                t.installed = c.getString(9); t.state = c.getString(10);
                int gi = c.getColumnIndex("github");
                t.github = gi >= 0 ? c.getString(gi) : null;
                out.add(t);
            }
        } catch (Throwable ignored) {
        } finally { if (c != null) c.close(); }
        return out;
    }

    private static Bitmap preview(Context ctx, String id) {
        InputStream in = null;
        try {
            in = ctx.getContentResolver().openInputStream(Uri.withAppendedPath(BASE, "storepreview/" + id));
            return BitmapFactory.decodeStream(in);
        } catch (Throwable t) {
            return null;
        } finally { if (in != null) try { in.close(); } catch (Throwable ignored) { } }
    }

    /** The preview with rounded corners, for the guide area. */
    private static Bitmap rounded(Bitmap b) {
        Bitmap out = Bitmap.createBitmap(b.getWidth(), b.getHeight(), Bitmap.Config.ARGB_8888);
        android.graphics.Canvas cv = new android.graphics.Canvas(out);
        android.graphics.Paint pt = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        pt.setShader(new android.graphics.BitmapShader(b, android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP));
        cv.drawRoundRect(new android.graphics.RectF(0, 0, b.getWidth(), b.getHeight()), 36, 36, pt);
        return out;
    }

    private static void call(Context ctx, String method, String arg) {
        try { ctx.getContentResolver().call(BASE, method, arg, null); } catch (Throwable ignored) { }
    }

    /** Redraw the page shortly (while Home downloads or checks). */
    private static void poll(final Host h, long ms) {
        ui.removeCallbacksAndMessages(null);
        ui.postDelayed(new Runnable() { @Override public void run() { try { h.refresh(); } catch (Throwable ignored) { } } }, ms);
    }

    private static boolean newer(String a, String b) {
        String[] x = a.split("\\."), y = b.split("\\.");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int p = i < x.length ? num(x[i]) : 0, q = i < y.length ? num(y[i]) : 0;
            if (p != q) return p > q;
        }
        return false;
    }

    private static int num(String s) { try { return Integer.parseInt(s.trim()); } catch (Throwable t) { return 0; } }

    private static String cap(String s) { return s.length() == 0 ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1); }

    private static String mb(long b) { return String.format(java.util.Locale.US, "%.1f MB", b / 1048576.0); }
}
