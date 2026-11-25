package dev.ccnc.home;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URL;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

/**
 * The online ccNC Theme Store. Packs are only files in Home's own storage (files/themes/<id>), so Home installs them
 * itself: no NEMESIS, no root. Flow:
 *   index    <base>/themes.json + themes.json.sig (RSA-SHA256): only an index signed with the store key (public half
 *            below; private half stays with the publisher) is used, so a changed repo or a network attacker can't
 *            push packs to cars. Cached in files/store/ so the list shows offline.
 *   install  download <base>/<zip> (the pack's NEMESIS package zip), size + SHA-256 must match the signed index,
 *            unpack only files/theme/* entries that a pack may contain (theme.json, the 1920x720 wallpaper, 90x90 icons,
 *            .mp4 videos; size limits, no path tricks) into a temp folder, check theme.json's id, then swap it in.
 *   updates  never automatic: Home only tells the owner (StoreCheckReceiver on Wi-Fi), Settings > Screen Theme >
 *            Theme Store installs.
 * Android 4.4: TLS 1.2 is switched on by hand and the roots GitHub needs (USERTrust, ISRG...) ship in assets.
 */
final class ThemeStore {
    private ThemeStore() {}

    static final String TAG = "ccncStore";
    static final String DEFAULT_INDEX = "https://github.com/JacobPantuso/ccnc-themes/releases/latest/download/themes.json";
    /** Public half of keys/ccnc-themes-store.pem (DER, base64). */
    private static final String STORE_KEY =
            "MIIBojANBgkqhkiG9w0BAQEFAAOCAY8AMIIBigKCAYEAvWAg4FeE1g2BOIAMfb/bPRL93dvLa+pz"
            + "7q8D4CWNOGIr+wmgEucNLB1K8HC195abVCsySco55CRWSZ42wlQroId9hnghOIOxhBeSeJMhDXLo"
            + "wYalrdxEVEHExvodnd6zwXHzDbivXG53vBRZA8R1Ql+cJ2tAmWYKj8J1ND16VyJm6UyUQZVhar4t"
            + "rC6OUg4XVslSwPR/lOaXokiPg3QxXLlRxdBYrASgbIzmfu3QoLQn8WQFqBkQ59oNXAYd+r1QKysI"
            + "U+Q0DzfXPIvnueOo5bfj9xAvsfQEVbSnoRFjyogvuMB5J7/4wVsCapuih/LujWG01eTRCqLS9nys"
            + "pj1slBBYx9N5fVLbt4NifVCptQfqtb49CDQXHkOXlMZHVLzHZYN+7gjFwEGv+bHJJyMsT2pzEpG4"
            + "+aM+QkNQPPnh/fM0kDihZ45lWX0V+3Qjjp87KA+m6G+HIC+CXxwIFv76bUCN9eosrlG9qH3O85qb"
            + "EbqaTHE1JUAY3LqJGF0dxlNZAgMBAAE=";

    static final class Item {
        String id, name, author, github, version, description, accent, sha256, zip, preview;
        boolean light;
        long size;
        final List<String> brands = new ArrayList<String>();
    }

    /** Per-id install state for the UI: "downloading 40%", "installing", "error: ...". Missing = idle. */
    static final Map<String, String> state = new ConcurrentHashMap<String, String>();
    private static List<Item> items = Collections.emptyList();
    static volatile String lastError;
    static volatile long fetchedAt;
    static volatile boolean refreshing;

    private static final Pattern ID = Pattern.compile("[a-z0-9][a-z0-9_-]{1,31}");
    private static final Pattern ICON = Pattern.compile("[a-z0-9_]{1,40}\\.png");
    private static final Pattern FILE = Pattern.compile("[A-Za-z0-9_.-]{1,64}");

    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("store", Context.MODE_PRIVATE); }

    /** The index URL; a debuggable build can point it elsewhere (local test store). */
    static String indexUrl(Context c) {
        boolean debug = (c.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        String u = debug ? prefs(c).getString("url", null) : null;
        return u != null && u.length() > 0 ? u : DEFAULT_INDEX;
    }

    static synchronized List<Item> items(Context c) {
        if (items.isEmpty()) loadCached(c);
        return items;
    }

    static Item find(Context c, String id) {
        for (Item i : items(c)) if (i.id.equals(id)) return i;
        return null;
    }

    // ---- index ----

    /** Fetches and verifies the index (blocking; call off the UI thread). True when it changed or loaded. */
    static boolean refresh(Context c) {
        refreshing = true;
        try {
            String url = indexUrl(c);
            byte[] json = get(c, url, 512 * 1024, null, null);
            byte[] sig = get(c, url + ".sig", 8 * 1024, null, null);
            if (!verified(json, sig)) throw new IOException("index signature doesn't match: not using it");
            List<Item> parsed = parse(json);
            File dir = new File(c.getFilesDir(), "store");
            dir.mkdirs();
            write(new File(dir, "themes.json"), json);
            write(new File(dir, "themes.json.sig"), sig);
            synchronized (ThemeStore.class) { items = parsed; }
            fetchedAt = System.currentTimeMillis();
            prefs(c).edit().putLong("fetched", fetchedAt).apply();
            lastError = null;
            fetchPreviews(c, parsed);
            Log.i(TAG, "index: " + parsed.size() + " themes from " + url);
            return true;
        } catch (Throwable t) {
            lastError = t.getMessage() != null ? t.getMessage() : t.toString();
            Log.w(TAG, "refresh failed", t);
            return false;
        } finally {
            refreshing = false;
        }
    }

    private static void loadCached(Context c) {
        try {
            File dir = new File(c.getFilesDir(), "store");
            byte[] json = read(new File(dir, "themes.json")), sig = read(new File(dir, "themes.json.sig"));
            if (json != null && sig != null && verified(json, sig)) {
                items = parse(json);
                fetchedAt = prefs(c).getLong("fetched", 0);
            }
        } catch (Throwable t) {
            Log.w(TAG, "cached index unreadable", t);
        }
    }

    static boolean verified(byte[] json, byte[] sigB64) {
        try {
            PublicKey k = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.decode(STORE_KEY, Base64.DEFAULT)));
            Signature s = Signature.getInstance("SHA256withRSA");
            s.initVerify(k);
            s.update(json);
            return s.verify(Base64.decode(new String(sigB64, "US-ASCII").trim(), Base64.DEFAULT));
        } catch (Throwable t) {
            Log.w(TAG, "signature check failed", t);
            return false;
        }
    }

    private static List<Item> parse(byte[] json) throws Exception {
        JSONObject o = new JSONObject(new String(json, "UTF-8"));
        if (o.optInt("format") != 1) throw new IOException("unknown store format " + o.optInt("format"));
        JSONArray a = o.getJSONArray("themes");
        List<Item> out = new ArrayList<Item>();
        for (int i = 0; i < a.length(); i++) {
            JSONObject t = a.getJSONObject(i);
            Item it = new Item();
            it.id = t.getString("id");
            if (!ID.matcher(it.id).matches()) continue;
            it.name = t.optString("name", it.id);
            it.author = t.optString("author", "");
            it.github = t.optString("github", "");
            if (!it.github.matches("[A-Za-z0-9-]{0,39}")) it.github = "";
            it.version = t.optString("version", "1.0");
            it.description = t.optString("description", "");
            it.accent = t.optString("accent", "");
            it.light = t.optBoolean("light");
            it.size = t.optLong("size");
            it.sha256 = t.optString("sha256", "").toLowerCase(Locale.US);
            it.zip = t.optString("zip", "");
            it.preview = t.isNull("preview") ? null : t.optString("preview", null);
            JSONArray b = t.optJSONArray("brands");
            if (b != null) for (int k = 0; k < b.length(); k++) it.brands.add(b.optString(k).toLowerCase(Locale.US));
            if (!FILE.matcher(it.zip).matches() || it.sha256.length() != 64 || it.size <= 0 || it.size > 80L * 1024 * 1024) continue;
            if (it.preview != null && !FILE.matcher(it.preview).matches()) it.preview = null;
            out.add(it);
        }
        return out;
    }

    private static void fetchPreviews(Context c, List<Item> list) {
        File dir = new File(c.getCacheDir(), "store-previews");
        dir.mkdirs();
        for (Item i : list) {
            if (i.preview == null) continue;
            File f = new File(dir, i.id + "-" + i.version + ".jpg");
            if (f.isFile()) continue;
            try { write(f, get(c, resolve(c, i.preview), 1024 * 1024, null, null)); } catch (Throwable t) { Log.w(TAG, "preview " + i.id, t); }
        }
    }

    static File preview(Context c, Item i) {
        File f = new File(new File(c.getCacheDir(), "store-previews"), i.id + "-" + i.version + ".jpg");
        return f.isFile() ? f : null;
    }

    private static String resolve(Context c, String name) {
        String u = indexUrl(c);
        return u.substring(0, u.lastIndexOf('/') + 1) + name;
    }

    // ---- installed packs ----

    /** Installed version of a pack (from its theme.json), or null. */
    static String installedVersion(Context c, String id) {
        try {
            byte[] j = read(new File(new File(ThemeProvider.packsDir(c), id), "theme.json"));
            return j == null ? null : new JSONObject(new String(j, "UTF-8")).optString("version", "1.0");
        } catch (Throwable t) { return null; }
    }

    static boolean newer(String a, String b) { // a > b, dotted numbers
        String[] x = a.split("\\."), y = b.split("\\.");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int p = i < x.length ? num(x[i]) : 0, q = i < y.length ? num(y[i]) : 0;
            if (p != q) return p > q;
        }
        return false;
    }

    private static int num(String s) { try { return Integer.parseInt(s.trim()); } catch (Throwable t) { return 0; } }

    /** {not installed, updates} among the packs that fit this car's brand. */
    static int[] counts(Context c) {
        int fresh = 0, upd = 0;
        String brand = dev.ccnc.common.Fonts.brand(c);
        for (Item i : items(c)) {
            if (!i.brands.isEmpty() && !i.brands.contains(brand)) continue;
            String v = installedVersion(c, i.id);
            if (v == null) fresh++;
            else if (newer(i.version, v)) upd++;
        }
        return new int[]{fresh, upd};
    }

    /** Downloads, checks and installs (or updates) a pack. Blocking; call off the UI thread. */
    static boolean install(Context c, String id) {
        Item it = find(c, id);
        if (it == null) { state.put(id, "error: not in the store"); return false; }
        File tmpZip = new File(c.getCacheDir(), "store-" + id + ".zip");
        File root = ThemeProvider.packsDir(c);
        File stage = new File(root, ".install-" + id);
        try {
            state.put(id, "downloading 0%");
            final String sid = id;
            final long total = it.size;
            byte[] zip = get(c, resolve(c, it.zip), it.size, tmpZip, new Progress() {
                @Override public void on(long done) { state.put(sid, "downloading " + (int) (100 * done / Math.max(1, total)) + "%"); }
            });
            if (zip.length != it.size) throw new IOException("size doesn't match the store");
            if (!hex(MessageDigest.getInstance("SHA-256").digest(zip)).equals(it.sha256)) throw new IOException("checksum doesn't match the store");
            state.put(id, "installing");
            deleteTree(stage);
            if (!stage.mkdirs()) throw new IOException("can't write " + stage);
            unpack(zip, stage);
            byte[] tj = read(new File(stage, "theme.json"));
            if (tj == null) throw new IOException("no theme.json in the pack");
            JSONObject t = new JSONObject(new String(tj, "UTF-8"));
            if (!id.equals(t.optString("id"))) throw new IOException("pack id doesn't match");
            String wall = t.optString("wallpaper", null);
            if (wall != null && !t.isNull("wallpaper")) {
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(new File(stage, wall).getPath(), o);
                if (o.outWidth != 1920 || o.outHeight != 720) throw new IOException("wallpaper isn't 1920x720");
            }
            File dest = new File(root, id);
            deleteTree(dest);
            if (!stage.renameTo(dest)) throw new IOException("can't move the pack into place");
            state.remove(id);
            Log.i(TAG, "installed " + id + " " + it.version);
            return true;
        } catch (Throwable e) {
            state.put(id, "error: " + (e.getMessage() != null ? e.getMessage() : e.toString()));
            Log.w(TAG, "install " + id, e);
            deleteTree(stage);
            return false;
        } finally {
            tmpZip.delete();
        }
    }

    /** Removes a pack; if it was in use, ccNC goes back to ccNC Nebula. */
    static void remove(Context c, String id) {
        if (!ID.matcher(id).matches()) return;
        deleteTree(new File(ThemeProvider.packsDir(c), id));
        SharedPreferences p = c.getSharedPreferences("home", Context.MODE_PRIVATE);
        if (("pack." + id).equals(p.getString("theme", ""))) p.edit().putString("theme", "nebula").apply();
        state.remove(id);
    }

    /** Only files/theme/<allowed> entries; everything else in the zip (scripts, NEMESIS.data) is ignored. */
    private static void unpack(byte[] zip, File stage) throws IOException {
        ZipInputStream z = new ZipInputStream(new java.io.ByteArrayInputStream(zip));
        long total = 0;
        int icons = 0;
        ZipEntry e;
        byte[] buf = new byte[16384];
        try {
            while ((e = z.getNextEntry()) != null) {
                String n = e.getName();
                if (e.isDirectory() || !n.startsWith("files/theme/")) continue;
                String rel = n.substring("files/theme/".length());
                long max;
                if (rel.equals("theme.json")) max = 64 * 1024;
                else if (rel.startsWith("icons/") && ICON.matcher(rel.substring(6)).matches()) { max = 200 * 1024; icons++; }
                else if (FILE.matcher(rel).matches() && (rel.endsWith(".jpg") || rel.endsWith(".jpeg"))) max = 2 * 1024 * 1024;
                else if (FILE.matcher(rel).matches() && rel.endsWith(".mp4")) max = 30 * 1024 * 1024;
                else { Log.w(TAG, "skipping " + n); continue; }
                if (icons > 120) throw new IOException("too many icons");
                File f = new File(stage, rel);
                if (!f.getCanonicalPath().startsWith(stage.getCanonicalPath() + File.separator)) throw new IOException("bad path " + n);
                f.getParentFile().mkdirs();
                OutputStream out = new FileOutputStream(f);
                long w = 0;
                int r;
                try {
                    while ((r = z.read(buf)) > 0) {
                        w += r;
                        total += r;
                        if (w > max || total > 80L * 1024 * 1024) throw new IOException(rel + " is too big");
                        out.write(buf, 0, r);
                    }
                } finally { out.close(); }
            }
        } finally { z.close(); }
    }

    // ---- network (TLS 1.2 + bundled roots for Android 4.4) ----

    interface Progress { void on(long done); }

    private static SSLSocketFactory ssl;

    private static synchronized SSLSocketFactory ssl(Context c) throws Exception {
        if (ssl != null) return ssl;
        final X509TrustManager system = trustManager(null);
        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
        ks.load(null, null);
        InputStream in = c.getAssets().open("store-roots.pem");
        try {
            int n = 0;
            for (java.security.cert.Certificate cert : CertificateFactory.getInstance("X.509").generateCertificates(in))
                ks.setCertificateEntry("root" + (n++), cert);
        } finally { in.close(); }
        final X509TrustManager bundled = trustManager(ks);
        TrustManager both = new X509TrustManager() {
            @Override public void checkClientTrusted(X509Certificate[] ch, String a) throws CertificateException { system.checkClientTrusted(ch, a); }
            @Override public void checkServerTrusted(X509Certificate[] ch, String a) throws CertificateException {
                try { system.checkServerTrusted(ch, a); } catch (CertificateException e) { bundled.checkServerTrusted(ch, a); }
            }
            @Override public X509Certificate[] getAcceptedIssuers() { return system.getAcceptedIssuers(); }
        };
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(null, new TrustManager[]{both}, null);
        ssl = new Tls12(ctx.getSocketFactory());
        return ssl;
    }

    private static X509TrustManager trustManager(KeyStore ks) throws Exception {
        TrustManagerFactory f = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        f.init(ks);
        for (TrustManager t : f.getTrustManagers()) if (t instanceof X509TrustManager) return (X509TrustManager) t;
        throw new IllegalStateException("no X509TrustManager");
    }

    /** Android 4.4 supports TLS 1.2 but leaves it off; GitHub requires it. */
    private static final class Tls12 extends SSLSocketFactory {
        private final SSLSocketFactory d;
        Tls12(SSLSocketFactory d) { this.d = d; }
        private Socket on(Socket s) {
            if (s instanceof SSLSocket) {
                List<String> want = new ArrayList<String>();
                for (String p : ((SSLSocket) s).getSupportedProtocols()) if (p.startsWith("TLSv1")) want.add(p);
                ((SSLSocket) s).setEnabledProtocols(want.toArray(new String[0]));
            }
            return s;
        }
        @Override public String[] getDefaultCipherSuites() { return d.getDefaultCipherSuites(); }
        @Override public String[] getSupportedCipherSuites() { return d.getSupportedCipherSuites(); }
        @Override public Socket createSocket(Socket s, String h, int p, boolean a) throws IOException { return on(d.createSocket(s, h, p, a)); }
        @Override public Socket createSocket(String h, int p) throws IOException { return on(d.createSocket(h, p)); }
        @Override public Socket createSocket(String h, int p, InetAddress l, int lp) throws IOException { return on(d.createSocket(h, p, l, lp)); }
        @Override public Socket createSocket(InetAddress h, int p) throws IOException { return on(d.createSocket(h, p)); }
        @Override public Socket createSocket(InetAddress h, int p, InetAddress l, int lp) throws IOException { return on(d.createSocket(h, p, l, lp)); }
    }

    /** GET with redirects followed by hand (so every hop uses our TLS setup), capped at max bytes. */
    static byte[] get(Context c, String url, long max, File spool, Progress prog) throws Exception {
        for (int hop = 0; hop < 6; hop++) {
            HttpURLConnection h = (HttpURLConnection) new URL(url).openConnection();
            if (h instanceof HttpsURLConnection) ((HttpsURLConnection) h).setSSLSocketFactory(ssl(c));
            h.setInstanceFollowRedirects(false);
            h.setConnectTimeout(15000);
            h.setReadTimeout(30000);
            h.setRequestProperty("User-Agent", "ccNC-Home");
            int code = h.getResponseCode();
            if (code >= 300 && code < 400) {
                String loc = h.getHeaderField("Location");
                h.disconnect();
                if (loc == null) throw new IOException("redirect without a location");
                url = new URL(new URL(url), loc).toString();
                continue;
            }
            if (code != 200) { h.disconnect(); throw new IOException("HTTP " + code + " for " + url); }
            InputStream in = h.getInputStream();
            try {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[16384];
                long done = 0;
                int r;
                while ((r = in.read(buf)) > 0) {
                    done += r;
                    if (done > max) throw new IOException("download is larger than expected");
                    out.write(buf, 0, r);
                    if (prog != null) prog.on(done);
                }
                return out.toByteArray();
            } finally { in.close(); h.disconnect(); }
        }
        throw new IOException("too many redirects");
    }

    // ---- files ----

    static byte[] read(File f) {
        if (!f.isFile()) return null;
        try {
            FileInputStream in = new FileInputStream(f);
            try {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] b = new byte[16384];
                int r;
                while ((r = in.read(b)) > 0) out.write(b, 0, r);
                return out.toByteArray();
            } finally { in.close(); }
        } catch (IOException e) { return null; }
    }

    private static void write(File f, byte[] b) throws IOException {
        File t = new File(f.getPath() + ".tmp");
        FileOutputStream out = new FileOutputStream(t);
        try { out.write(b); } finally { out.close(); }
        if (!t.renameTo(f)) throw new IOException("can't write " + f);
    }

    static void deleteTree(File f) {
        if (f == null || !f.exists()) return;
        File[] k = f.listFiles();
        if (k != null) for (File x : k) deleteTree(x);
        f.delete();
    }

    private static String hex(byte[] b) {
        StringBuilder s = new StringBuilder();
        for (byte x : b) s.append(String.format(Locale.US, "%02x", x & 0xff));
        return s.toString();
    }
}
