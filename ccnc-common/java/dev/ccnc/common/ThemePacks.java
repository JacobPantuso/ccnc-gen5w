package dev.ccnc.common;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Community theme packs. A pack is a folder installed (by its NEMESIS package) into ccNC Home's storage,
 * files/themes/<id>/, holding:
 *   theme.json   {"name", "author", "version", "brands": ["hyundai","kia"], "light": false,
 *                 "colors": {"background": ["#rrggbb" x4], "glow1", "glow2", "shade", "card", "subtext", "accent"},
 *                 "wallpaper": "wallpaper.jpg", "icons": "icons", "boot_video": "boot.mp4", "shutdown_video": "shutdown.mp4"}
 *   wallpaper.jpg  1920 x 720 (optional; the gradient is drawn without one)
 *   icons/<name>.png  optional overrides of ccNC icon names (missing ones fall back to the built-in set)
 * ccNC Home lists them through its theme provider (.../packs) and serves their files (.../file/<id>/<path>), so
 * every ccNC app reads packs the same way. Colours accept #rrggbb or #aarrggbb.
 */
public final class ThemePacks {
    private ThemePacks() {}

    static final String TAG = "ccncTheme";
    static final String URI = "content://dev.ccnc.home.theme";
    /** Prefix of pack file paths for {@link Art}: "pack:<id>/<path>". */
    static final String SCHEME = "pack:";

    public static final class Pack {
        public final String id, name, author, version, bootVideo, shutdownVideo;
        public final List<String> brands;
        public final Themes.T theme;
        final String json;
        Pack(String id, String name, String author, String version, List<String> brands, String boot, String shutdown,
             Themes.T theme, String json) {
            this.id = id; this.name = name; this.author = author; this.version = version; this.brands = brands;
            this.bootVideo = boot; this.shutdownVideo = shutdown; this.theme = theme; this.json = json;
        }
        /** True when the pack is made for this car's brand (or for any). */
        public boolean fits(Context c) { return brands.isEmpty() || brands.contains(Fonts.brand(c)); }
    }

    private static List<Pack> cached = new ArrayList<Pack>();
    private static long cachedAt;
    /** Same json -> same Pack instance, so the Palette doesn't see a "new" theme on every read. */
    private static final Map<String, Pack> byJson = new HashMap<String, Pack>();

    /** Installed packs (re-read at most every 3 s). */
    public static synchronized List<Pack> all(Context c) {
        if (System.currentTimeMillis() - cachedAt < 3000) return cached;
        List<Pack> out = new ArrayList<Pack>();
        Cursor cur = null;
        try {
            cur = c.getContentResolver().query(Uri.parse(URI + "/packs"), null, null, null, null);
            while (cur != null && cur.moveToNext()) {
                String id = cur.getString(0), json = cur.getString(1);
                Pack p = byJson.get(id + "\n" + json);
                if (p == null) {
                    p = parse(id, json);
                    if (p == null) continue;
                    byJson.put(id + "\n" + json, p);
                }
                out.add(p);
            }
        } catch (Throwable t) {
            // ccNC Home missing or older than packs: no packs
        } finally {
            if (cur != null) cur.close();
        }
        cached = out;
        cachedAt = System.currentTimeMillis();
        return out;
    }

    public static Pack find(Context c, String id) {
        if (id == null) return null;
        for (Pack p : all(c)) if (p.id.equals(id)) return p;
        return null;
    }

    /** Pack theme ids look like "pack.<id>" so they can't clash with the built-in ones. */
    public static String themeId(String packId) { return "pack." + packId; }

    static Pack parse(String id, String json) {
        try {
            JSONObject o = new JSONObject(json);
            JSONObject k = o.optJSONObject("colors");
            if (k == null) k = new JSONObject();
            boolean light = o.optBoolean("light", false);
            Themes.T base = light ? Themes.DAY : Themes.NEBULA;
            int[] bg = base.bg.clone();
            JSONArray a = k.optJSONArray("background");
            if (a != null) for (int i = 0; i < 4 && i < a.length(); i++) bg[i] = color(a.optString(i), bg[i]);
            String wall = o.optString("wallpaper", null);
            String icons = o.optString("icons", null);
            List<String> brands = new ArrayList<String>();
            JSONArray b = o.optJSONArray("brands");
            if (b != null) for (int i = 0; i < b.length(); i++) brands.add(b.optString(i).toLowerCase(java.util.Locale.US));
            String name = o.optString("name", id);
            Themes.T t = new Themes.T(themeId(id), name, light, bg,
                    color(k.optString("glow1"), base.glow1), color(k.optString("glow2"), base.glow2),
                    color(k.optString("shade"), base.shade), color(k.optString("card"), base.card),
                    color(k.optString("subtext"), base.subtext), color(k.optString("accent"), base.accent),
                    wall == null || wall.length() == 0 ? null : SCHEME + id + "/" + wall,
                    icons == null || icons.length() == 0 ? null : SCHEME + id + "/" + icons);
            return new Pack(id, name, o.optString("author", ""), o.optString("version", ""), brands,
                    o.optString("boot_video", null), o.optString("shutdown_video", null), t, json);
        } catch (Throwable e) {
            Log.w(TAG, "theme pack " + id + " not readable: " + e);
            return null;
        }
    }

    private static int color(String s, int def) {
        if (s == null || s.length() == 0) return def;
        try { return Color.parseColor(s.trim()); } catch (Throwable e) { return def; }
    }

    /** Opens a pack file ("pack:<id>/<path>") through ccNC Home's provider. */
    static java.io.InputStream open(Context c, String packPath) throws java.io.IOException {
        String rel = packPath.substring(SCHEME.length());
        return c.getContentResolver().openInputStream(Uri.parse(URI + "/file/" + rel));
    }
}
