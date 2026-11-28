package dev.ccnc.home;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

/**
 * Tells the other ccNC apps which theme and accent the owner picked in ccNC Home
 * ({@code content://dev.ccnc.home.theme/theme}, columns "theme" and "accent"); ccNC Settings can change them.
 * Also serves installed theme packs (files/themes/<id>/, put there by theme-pack NEMESIS packages):
 * {@code .../packs} lists them (columns "id", "json": the pack's theme.json) and {@code .../file/<id>/<path>} opens
 * one of a pack's files read-only, so every ccNC app can use a pack without reading Home's storage itself.
 */
public final class ThemeProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }

    /** Installed theme packs: one folder per pack with a theme.json. */
    static java.io.File packsDir(Context c) { return new java.io.File(c.getFilesDir(), "themes"); }

    private Cursor packs(Context c) {
        MatrixCursor m = new MatrixCursor(new String[]{"id", "json"});
        java.io.File[] dirs = packsDir(c).listFiles();
        if (dirs == null) return m;
        java.util.Arrays.sort(dirs);
        for (java.io.File d : dirs) {
            java.io.File j = new java.io.File(d, "theme.json");
            if (!d.isDirectory() || !j.isFile() || j.length() > 64 * 1024) continue;
            try {
                java.io.FileInputStream in = new java.io.FileInputStream(j);
                byte[] buf = new byte[(int) j.length()];
                int n = 0, r;
                while (n < buf.length && (r = in.read(buf, n, buf.length - n)) > 0) n += r;
                in.close();
                m.addRow(new Object[]{d.getName(), new String(buf, 0, n, "UTF-8")});
            } catch (Throwable ignored) { }
        }
        return m;
    }

    // ---- Theme Store (see ThemeStore): list, refresh/install/remove, previews ----

    private static final java.util.concurrent.ExecutorService work = java.util.concurrent.Executors.newSingleThreadExecutor();

    /** .../store: one row per store theme (id, name, author, version, description, size, light, accent, brands,
     *  installed = installed version or null, state = "downloading 40%" / "installing" / "error: ..." / null). */
    private Cursor store(Context c) {
        MatrixCursor m = new MatrixCursor(new String[]{"id", "name", "author", "version", "description", "size", "light",
                "accent", "brands", "installed", "state", "github"});
        for (ThemeStore.Item i : ThemeStore.items(c)) {
            m.addRow(new Object[]{i.id, i.name, i.author, i.version, i.description, i.size, i.light ? 1 : 0, i.accent,
                    android.text.TextUtils.join(",", i.brands), ThemeStore.installedVersion(c, i.id), ThemeStore.state.get(i.id), i.github});
        }
        return m;
    }

    /** .../store_info: fetched (ms), refreshing (0/1), error, fresh (not installed), updates, url. */
    private Cursor storeInfo(Context c) {
        MatrixCursor m = new MatrixCursor(new String[]{"fetched", "refreshing", "error", "fresh", "updates", "url"});
        int[] n = ThemeStore.counts(c);
        m.addRow(new Object[]{ThemeStore.fetchedAt, ThemeStore.refreshing ? 1 : 0, ThemeStore.lastError, n[0], n[1], ThemeStore.indexUrl(c)});
        return m;
    }

    /** store_refresh, store_install <id>, store_remove <id>: run on Home's own thread; the caller polls .../store. */
    @Override public android.os.Bundle call(String method, final String arg, android.os.Bundle extras) {
        final Context c = getContext();
        if (c == null || method == null) return null;
        if ("store_refresh".equals(method)) {
            if (!ThemeStore.refreshing) { ThemeStore.refreshing = true; work.execute(new Runnable() { @Override public void run() { ThemeStore.refresh(c); } }); }
        } else if ("store_install".equals(method) && arg != null) {
            ThemeStore.state.put(arg, "waiting");
            work.execute(new Runnable() { @Override public void run() { ThemeStore.install(c, arg); } });
        } else if ("store_remove".equals(method) && arg != null) {
            work.execute(new Runnable() { @Override public void run() { ThemeStore.remove(c, arg); } });
        }
        return new android.os.Bundle();
    }

    /** Read-only access to a pack's own files; anything outside files/themes is refused. */
    @Override public android.os.ParcelFileDescriptor openFile(Uri uri, String mode) throws java.io.FileNotFoundException {
        Context c = getContext();
        java.util.List<String> seg = uri.getPathSegments();
        if (c != null && seg.size() == 2 && "storepreview".equals(seg.get(0))) { // .../storepreview/<id>
            ThemeStore.Item it = ThemeStore.find(c, seg.get(1));
            java.io.File f = it == null ? null : ThemeStore.preview(c, it);
            if (f == null) throw new java.io.FileNotFoundException(uri.toString());
            return android.os.ParcelFileDescriptor.open(f, android.os.ParcelFileDescriptor.MODE_READ_ONLY);
        }
        if (c != null && seg.size() == 3 && "npart".equals(seg.get(0))) { // .../npart/<action>/<title>: the collected cover
            java.io.File f = dev.ccnc.common.NowPlayingArt.serve(c, seg.get(1), seg.get(2));
            if (f == null) throw new java.io.FileNotFoundException(uri.toString());
            return android.os.ParcelFileDescriptor.open(f, android.os.ParcelFileDescriptor.MODE_READ_ONLY);
        }
        if (c == null || !"r".equals(mode) || seg.size() < 3 || !"file".equals(seg.get(0))) throw new java.io.FileNotFoundException(uri.toString());
        try {
            java.io.File root = packsDir(c).getCanonicalFile();
            java.io.File f = new java.io.File(root, android.text.TextUtils.join("/", seg.subList(1, seg.size()))).getCanonicalFile();
            if (!f.getPath().startsWith(root.getPath() + "/") || !f.isFile()) throw new java.io.FileNotFoundException(uri.toString());
            return android.os.ParcelFileDescriptor.open(f, android.os.ParcelFileDescriptor.MODE_READ_ONLY);
        } catch (java.io.IOException e) {
            throw new java.io.FileNotFoundException(uri.toString());
        }
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        Context c = getContext();
        if (c != null && "packs".equals(uri.getLastPathSegment())) return packs(c);
        if (c != null && "store".equals(uri.getLastPathSegment())) return store(c);
        if (c != null && "store_info".equals(uri.getLastPathSegment())) return storeInfo(c);
        MatrixCursor m = new MatrixCursor(new String[]{"theme", "accent"});
        if (c != null) {
            android.content.SharedPreferences p = c.getSharedPreferences("home", Context.MODE_PRIVATE);
            m.addRow(new Object[]{p.getString("theme", "nebula"), p.getInt("accent", 0)});
        }
        return m;
    }

    @Override public String getType(Uri uri) { return "vnd.android.cursor.item/vnd.dev.ccnc.theme"; }
    @Override public Uri insert(Uri uri, ContentValues v) { return null; }
    @Override public int delete(Uri uri, String s, String[] a) { return 0; }
    /** ccNC Settings' Appearance page sets the theme / accent here. */
    @Override public int update(Uri uri, ContentValues v, String s, String[] a) {
        Context c = getContext();
        if (c == null || v == null) return 0;
        android.content.SharedPreferences.Editor e = c.getSharedPreferences("home", Context.MODE_PRIVATE).edit();
        if (v.containsKey("theme")) e.putString("theme", v.getAsString("theme"));
        if (v.containsKey("store_url") && (c.getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            ThemeStore.prefs(c).edit().putString("url", v.getAsString("store_url")).apply(); // test builds only
        }
        if (v.containsKey("accent")) e.putInt("accent", v.getAsInteger("accent"));
        e.apply();
        return 1;
    }
}
