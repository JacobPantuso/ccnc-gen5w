package dev.ccnc.welcome;

import java.io.*;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.*;

/**
 * Rewrites an APK with some entries replaced by files: {@code Patch in.apk out.apk entry=file ...}.
 * Everything else is copied as it was (same method, same order, same timestamps), so the
 * manifest, resources.arsc and META-INF stay byte-for-byte. Replaced entries are STORED, as
 * res/raw videos must be for the app to open them by file descriptor.
 * Runs on the head unit with app_process (java.util.zip only), or on a Mac for testing.
 */
public final class Patch {
    public static void main(String[] a) throws Exception {
        if (a.length == 2 && a[0].equals("list")) { // Patch list in.apk: every res/raw entry with its size
            ZipFile z = new ZipFile(a[1]);
            for (Enumeration<? extends ZipEntry> en = z.entries(); en.hasMoreElements();) {
                ZipEntry e = en.nextElement();
                if (e.getName().startsWith("res/raw/") && (e.getName().contains("welcome") || e.getName().contains("goodbye") || e.getSize() > 1000000))
                    System.out.println(e.getSize() + " " + e.getName());
            }
            return;
        }
        if (a.length >= 2 && a[0].equals("zipinfo")) { // Patch zipinfo file.zip [text-entry ...]: entry names/sizes, then the text entries' contents
            ZipFile z = new ZipFile(a[1]);
            int n = 0;
            for (Enumeration<? extends ZipEntry> en = z.entries(); en.hasMoreElements();) {
                ZipEntry e = en.nextElement();
                if (n++ < 60 || e.getName().endsWith(".txt") || e.getName().endsWith(".mp4")) System.out.println(e.getSize() + " " + e.getName());
            }
            System.out.println("entries: " + n);
            for (int i = 2; i < a.length; i++) {
                ZipEntry e = z.getEntry(a[i]);
                if (e != null && e.getSize() < 20000) { System.out.println("--- " + a[i]); System.out.println(new String(read(z.getInputStream(e)), "UTF-8")); }
            }
            return;
        }
        if (a.length < 3) { System.err.println("usage: Patch in.apk out.apk entry=file ..."); System.exit(2); }
        Map<String, File> repl = new HashMap<String, File>();
        for (int i = 2; i < a.length; i++) {
            int eq = a[i].indexOf('=');
            repl.put(a[i].substring(0, eq), new File(a[i].substring(eq + 1)));
        }
        ZipFile in = new ZipFile(a[0]);
        ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(a[1])));
        int done = 0;
        // "res/raw/welcome_*.mp4=file": every real (over 1 MB, i.e. not a stub) entry matching the pattern.
        Map<String, File> wild = new HashMap<String, File>();
        for (Map.Entry<String, File> w : new java.util.ArrayList<Map.Entry<String, File>>(repl.entrySet()))
            if (w.getKey().indexOf('*') >= 0) { wild.put(w.getKey(), w.getValue()); repl.remove(w.getKey()); }
        int wildHits = 0;
        for (Enumeration<? extends ZipEntry> en = in.entries(); en.hasMoreElements();) {
            ZipEntry e = en.nextElement();
            File r = repl.get(e.getName());
            if (r == null && e.getSize() > 1000000) {
                for (Map.Entry<String, File> w : wild.entrySet()) {
                    String[] part = w.getKey().split("\\*", 2);
                    if (e.getName().startsWith(part[0]) && e.getName().endsWith(part[1])) { r = w.getValue(); wildHits++; System.out.println("replacing " + e.getName() + " (" + e.getSize() + " bytes)"); break; }
                }
            }
            ZipEntry n = new ZipEntry(e.getName());
            n.setTime(e.getTime());
            if (r != null) {
                byte[] data = read(new FileInputStream(r));
                CRC32 crc = new CRC32();
                crc.update(data);
                n.setMethod(ZipEntry.STORED);
                n.setSize(data.length);
                n.setCompressedSize(data.length);
                n.setCrc(crc.getValue());
                out.putNextEntry(n);
                out.write(data);
                done++;
            } else if (e.getMethod() == ZipEntry.STORED) {
                n.setMethod(ZipEntry.STORED);
                n.setSize(e.getSize());
                n.setCompressedSize(e.getSize());
                n.setCrc(e.getCrc());
                out.putNextEntry(n);
                copy(in.getInputStream(e), out);
            } else {
                n.setMethod(ZipEntry.DEFLATED);
                out.putNextEntry(n);
                copy(in.getInputStream(e), out);
            }
            out.closeEntry();
        }
        out.close();
        in.close();
        if (done - wildHits != repl.size() || (!wild.isEmpty() && wildHits == 0)) { System.err.println("replaced " + done + " of " + repl.size() + " entries (+" + wildHits + " wildcard)"); System.exit(3); }
        System.out.println("replaced " + done + " entries");
    }

    private static byte[] read(InputStream s) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        copy(s, b);
        return b.toByteArray();
    }

    private static void copy(InputStream s, OutputStream o) throws IOException {
        byte[] buf = new byte[65536];
        int n;
        while ((n = s.read(buf)) > 0) o.write(buf, 0, n);
        s.close();
    }
}
