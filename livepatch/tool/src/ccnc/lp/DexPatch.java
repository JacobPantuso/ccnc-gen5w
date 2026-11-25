/*
 * DRAFT / UNTESTED ON A HEAD UNIT.
 *
 * ccnc-lp: name-based odex patcher for NEMESIS LivePatch's `exec` engine.
 *
 *   DexPatch <mode> <in.odex> <out.odex>
 *   DexPatch check <mode> <in.odex>        (exit 0 patchable, 10 already patched, 1 no fit)
 *
 * modes:
 *   volume   Lcom/hkmc/micom/MicomVolumePanel; onShowVolumeChanged(..)V and onShowVolumeChangedNew(..)V
 *            -> insns[0] = return-void
 *   sideosd  every class under Lcom/mobis/splitscreen/right/ that declares canShowSSOsd()Z,
 *            canShowSSOsdWithoutVR()Z or canShowSSOsdWithDispOff()Z -> "const/4 v0, 0; return v0".
 *            canShowSSOsd(ZZZ)Z (the binder entry ModeManagerService asks at boot) stays stock.
 *   climate  every launch site under Lcom/hkmc/telematics/service/apps/climate/ of the shape
 *              new-instance vA, Intent; invoke-direct {vA}, Intent.<init>()V;
 *              const-string vB, "<pkg>"; const-string vC, "<pkg>.activities.(NewClimateControl|SeatHeatVentPopup)";
 *              invoke-virtual[-quick] {vA, vB, vC}, setClassName; move-result-object vA
 *            -> new-instance vA, Intent; const-string vB, "<pkg>"; const-string vC, "<cls>";
 *               invoke-direct {vA, vC}, Intent.<init>(String); nop...
 *            (same registers hold the same values afterwards as in stock; the intent's action is the
 *             stock class name, which ccNC Climate claims)
 *
 * Classes and methods are found by stable names, string constants and code shape, never by the
 * obfuscated names (ClimateManager.aa/mD, RightScreenHome k/r), so one tool fits any firmware where
 * the shapes hold. Anything that does not fit exactly -> exit 1 and nothing is written (LivePatch
 * then refuses the install with the last printed line as the reason, the unit stays stock).
 *
 * The output depends only on the input bytes and the mode (LivePatch rule). Only code units inside
 * existing methods change, plus the dex checksum and SHA-1 signature; odex deps/opt data untouched.
 * Plain Java 7, no libraries, so it runs under the unit's dalvikvm (see bin/lp-ccnc).
 */
package ccnc.lp;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.Adler32;

public final class DexPatch {
    static final int PATCHABLE = 0, DONE = 10, NOFIT = 1;

    byte[] b;
    int base;           // dex start inside the odex (0 for a plain dex)
    int strOff, strN, typeOff, typeN, protoOff, protoN, methOff, methN, cdefOff, cdefN, fileSize;

    // ---------------------------------------------------------------- main

    public static void main(String[] a) {
        try {
            if (a.length == 3 && a[0].equals("check")) {
                DexPatch d = new DexPatch(read(new File(a[2])));
                System.exit(d.run(a[1], false));
            }
            if (a.length != 3) {
                System.out.println("usage: DexPatch <volume|sideosd|climate> <in> <out> | check <mode> <in>");
                System.exit(2);
            }
            DexPatch d = new DexPatch(read(new File(a[1])));
            int rc = d.run(a[0], true);
            if (rc == PATCHABLE) {
                d.fixSums();
                FileOutputStream o = new FileOutputStream(a[2]);
                try { o.write(d.b); } finally { o.close(); }
                DexPatch v = new DexPatch(read(new File(a[2])));     // re-read the output: must now be "done"
                if (v.run(a[0], false) != DONE) { new File(a[2]).delete(); fail("patch did not take"); }
                System.out.println(a[0] + ": patched");
            }
            System.exit(rc);
        } catch (Fail f) {
            System.out.println(f.getMessage());
            System.exit(1);
        } catch (Throwable t) {
            System.out.println("ccnc-lp: " + t);
            System.exit(1);
        }
    }

    int run(String mode, boolean write) {
        if (mode.equals("volume")) return volume(write);
        if (mode.equals("sideosd")) return sideosd(write);
        if (mode.equals("climate")) return climate(write);
        throw new Fail("unknown mode " + mode);
    }

    // ---------------------------------------------------------------- modes

    int volume(boolean write) {
        int cls = classDef("Lcom/hkmc/micom/MicomVolumePanel;");
        if (cls < 0) throw new Fail("volume: no MicomVolumePanel in this file");
        List<int[]> ms = new ArrayList<int[]>();
        for (int[] m : methods(cls)) {
            String n = methName(m[0]);
            if ((n.equals("onShowVolumeChanged") || n.equals("onShowVolumeChangedNew")) && retType(m[0]).equals("V") && m[2] != 0)
                ms.add(m);
        }
        if (ms.size() < 1) throw new Fail("volume: no onShowVolumeChanged* method");
        int done = 0;
        for (int[] m : ms) {
            int insns = m[2] + 16;
            if (u2(insns) == 0x000e) { done++; continue; }
            if (write) p2(insns, 0x000e);
            say("volume: " + methName(m[0]) + protoStr(m[0]) + (write ? " -> return-void" : " patchable"));
        }
        return done == ms.size() ? DONE : PATCHABLE;
    }

    int sideosd(boolean write) {
        List<int[]> ms = new ArrayList<int[]>();
        boolean plain = false;
        for (int c = 0; c < cdefN; c++) {
            if (!typeName(u4(cdefOff + c * 32)).startsWith("Lcom/mobis/splitscreen/right/")) continue;
            for (int[] m : methods(c)) {
                String n = methName(m[0]);
                if (!(n.equals("canShowSSOsd") || n.equals("canShowSSOsdWithoutVR") || n.equals("canShowSSOsdWithDispOff"))) continue;
                if (!protoStr(m[0]).equals("()Z") || m[2] == 0) continue;     // (ZZZ)Z stays stock on purpose
                if (u2(m[2]) < 1 || u4(m[2] + 12) < 2) throw new Fail("sideosd: " + n + " too small to stub");
                if (n.equals("canShowSSOsd")) plain = true;
                ms.add(m);
            }
        }
        if (!plain) throw new Fail("sideosd: no canShowSSOsd()Z in this file");
        int done = 0;
        for (int[] m : ms) {
            int insns = m[2] + 16;
            if (u2(insns) == 0x0012 && u2(insns + 2) == 0x000f) { done++; continue; }
            if (write) { p2(insns, 0x0012); p2(insns + 2, 0x000f); }
            say("sideosd: " + typeName(methClass(m[0])) + "->" + methName(m[0]) + "()Z" + (write ? " -> false" : " patchable"));
        }
        return done == ms.size() ? DONE : PATCHABLE;
    }

    static final String CLIMATE_PKG = "com.hkmc.telematics.service.apps.climate";
    static final String[] CLIMATE_TARGETS = {CLIMATE_PKG + ".activities.NewClimateControl", CLIMATE_PKG + ".activities.SeatHeatVentPopup"};

    int climate(boolean write) {
        int tIntent = typeIdx("Landroid/content/Intent;");
        int sPkg = stringIdx(CLIMATE_PKG);
        if (tIntent < 0 || sPkg < 0) throw new Fail("climate: not the climate app (no Intent type / package string)");
        int[] sCls = new int[CLIMATE_TARGETS.length];
        for (int i = 0; i < sCls.length; i++) sCls[i] = stringIdx(CLIMATE_TARGETS[i]);
        int mInit0 = methodIdx("Landroid/content/Intent;", "<init>", "()V");
        int mInitS = methodIdx("Landroid/content/Intent;", "<init>", "(Ljava/lang/String;)V");
        int mSetCls = methodIdx("Landroid/content/Intent;", "setClassName", "(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;");
        if (mInitS < 0) throw new Fail("climate: this build never calls new Intent(String); can't rewrite in place (use the dex-engine draft)");
        int todo = 0, done = 0;
        int[] seen = new int[sCls.length];
        for (int c = 0; c < cdefN; c++) {
            if (!typeName(u4(cdefOff + c * 32)).startsWith("Lcom/hkmc/telematics/service/apps/climate/")) continue;
            for (int[] m : methods(c)) {
                if (m[2] == 0) continue;
                int insns = m[2] + 16, n = u4(m[2] + 12);
                for (int pc = 0; pc < n; pc += width(insns, pc, n)) {
                    int r = launchSite(insns, pc, n, tIntent, sPkg, sCls, mInit0, mInitS, mSetCls);
                    if (r < 0) continue;
                    int k = r & 0xff;
                    seen[k]++;
                    if ((r & 0x100) != 0) { done++; continue; }
                    todo++;
                    if (write) rewrite(insns, pc, mInitS);
                    say("climate: " + typeName(u4(cdefOff + c * 32)) + "->" + methName(m[0]) + protoStr(m[0]) + " @" + pc
                            + " " + CLIMATE_TARGETS[k].substring(CLIMATE_PKG.length() + 1) + (write ? " -> implicit" : " patchable"));
                }
            }
        }
        if (todo + done == 0) throw new Fail("climate: no explicit launch of NewClimateControl/SeatHeatVentPopup found");
        return todo == 0 ? DONE : PATCHABLE;
    }

    /* index of the matched target | 0x100 when the site is already rewritten; -1 no match */
    int launchSite(int at, int pc, int n, int tIntent, int sPkg, int[] sCls, int mInit0, int mInitS, int mSetCls) {
        if (pc + 13 > n) return -1;
        int w0 = u2(at + 2 * pc);
        if ((w0 & 0xff) != 0x22 || u2(at + 2 * pc + 2) != tIntent) return -1;
        int A = w0 >> 8;
        if (A > 15) return -1;
        int q = pc + 2;
        // stock: invoke-direct {vA}, Intent.<init>()V
        if (u2(at + 2 * q) == 0x1070 && u2(at + 2 * q + 2) == mInit0 && (u2(at + 2 * q + 4) & 0xf) == A) {
            q += 3;
            int[] s1 = constString(at, q, n); if (s1 == null || s1[1] != sPkg) return -1; int B = s1[0]; q += s1[2];
            int[] s2 = constString(at, q, n); if (s2 == null) return -1; int C = s2[0]; q += s2[2];
            int k = idxOf(sCls, s2[1]); if (k < 0 || B > 15 || C > 15) return -1;
            int iv = u2(at + 2 * q), op = iv & 0xff, regs = u2(at + 2 * q + 4);
            if (iv >> 12 != 3 || regs != (A | B << 4 | C << 8)) return -1;
            if (op == 0x6e) { if (u2(at + 2 * q + 2) != mSetCls) return -1; }
            else if (op != 0xf8) return -1;                       // invoke-virtual-quick: vtable index of setClassName on this framework
            q += 3;
            if (u2(at + 2 * q) != (0x0c | A << 8)) return -1;       // move-result-object vA
            return k;
        }
        // already rewritten: const-string vB pkg; const-string vC cls; invoke-direct {vA, vC} <init>(String); nops
        int[] s1 = constString(at, q, n); if (s1 == null || s1[1] != sPkg) return -1; q += s1[2];
        int[] s2 = constString(at, q, n); if (s2 == null) return -1; int C = s2[0]; q += s2[2];
        int k = idxOf(sCls, s2[1]); if (k < 0) return -1;
        if (u2(at + 2 * q) != 0x2070 || u2(at + 2 * q + 2) != mInitS || u2(at + 2 * q + 4) != (A | C << 4)) return -1;
        return k | 0x100;
    }

    void rewrite(int at, int pc, int mInitS) {
        int A = u2(at + 2 * pc) >> 8;
        int q = pc + 2;
        int len = 2 + 3;
        int[] s1 = constString(at, q + 3, Integer.MAX_VALUE);
        int[] s2 = constString(at, q + 3 + s1[2], Integer.MAX_VALUE);
        len += s1[2] + s2[2] + 3 + 1;
        int[] u = new int[len - 2];
        int o = 0;
        for (int i = 0; i < s1[2]; i++) u[o++] = u2(at + 2 * (q + 3 + i));
        for (int i = 0; i < s2[2]; i++) u[o++] = u2(at + 2 * (q + 3 + s1[2] + i));
        u[o++] = 0x2070; u[o++] = mInitS; u[o++] = A | s2[0] << 4;
        while (o < u.length) u[o++] = 0x0000;                        // nop
        for (int i = 0; i < u.length; i++) p2(at + 2 * (q + i), u[i]);
    }

    /* {register, string idx, width} for const-string / const-string/jumbo at unit q, else null */
    int[] constString(int at, int q, int n) {
        if (q + 2 > n) return null;
        int w = u2(at + 2 * q);
        if ((w & 0xff) == 0x1a) return new int[] {w >> 8, u2(at + 2 * q + 2), 2};
        if ((w & 0xff) == 0x1b && q + 3 <= n) return new int[] {w >> 8, u2(at + 2 * q + 2) | u2(at + 2 * q + 4) << 16, 3};
        return null;
    }

    static int idxOf(int[] a, int v) {
        for (int i = 0; i < a.length; i++) if (a[i] == v && v >= 0) return i;
        return -1;
    }

    /* Dalvik (4.4, odex) instruction width in code units, payload pseudo-instructions included */
    int width(int at, int pc, int n) {
        int w = u2(at + 2 * pc), op = w & 0xff;
        if (op == 0) {
            if (w == 0x0100) return 4 + 2 * u2(at + 2 * pc + 2);
            if (w == 0x0200) return 2 + 4 * u2(at + 2 * pc + 2);
            if (w == 0x0300) { int ew = u2(at + 2 * pc + 2); long cnt = u2(at + 2 * pc + 4) | (long) u2(at + 2 * pc + 6) << 16; return (int) (4 + (cnt * ew + 1) / 2); }
            return 1;
        }
        return W[op];
    }

    static final int[] W = new int[256];
    static {
        java.util.Arrays.fill(W, 1);
        int[][] r = {
            {0x02, 2}, {0x03, 3}, {0x05, 2}, {0x06, 3}, {0x08, 2}, {0x09, 3}, {0x13, 2}, {0x14, 3}, {0x15, 2}, {0x16, 2},
            {0x17, 3}, {0x18, 5}, {0x19, 2}, {0x1a, 2}, {0x1b, 3}, {0x1c, 2}, {0x1f, 2}, {0x20, 2}, {0x22, 2}, {0x23, 2},
            {0x24, 3}, {0x25, 3}, {0x26, 3}, {0x29, 2}, {0x2a, 3}, {0x2b, 3}, {0x2c, 3}};
        for (int[] x : r) W[x[0]] = x[1];
        for (int i = 0x2d; i <= 0x3d; i++) W[i] = 2;              // cmp*, if-*
        for (int i = 0x44; i <= 0x6d; i++) W[i] = 2;              // aget/aput/iget/iput/sget/sput
        for (int i = 0x6e; i <= 0x72; i++) W[i] = 3;              // invoke-*
        for (int i = 0x74; i <= 0x78; i++) W[i] = 3;              // invoke-*/range
        for (int i = 0x90; i <= 0xaf; i++) W[i] = 2;              // binop
        for (int i = 0xd0; i <= 0xe2; i++) W[i] = 2;              // binop/lit16, lit8
        for (int i = 0xe3; i <= 0xeb; i++) W[i] = 2;              // *-volatile
        W[0xed] = 2; W[0xee] = 3; W[0xef] = 3; W[0xf0] = 3;      // throw-verification-error, execute-inline*, invoke-object-init/range
        for (int i = 0xf2; i <= 0xf7; i++) W[i] = 2;              // iget/iput-quick
        for (int i = 0xf8; i <= 0xfb; i++) W[i] = 3;              // invoke-*-quick
        for (int i = 0xfc; i <= 0xfe; i++) W[i] = 2;              // more *-volatile
    }

    // ---------------------------------------------------------------- dex tables

    DexPatch(byte[] f) {
        b = f;
        if (f.length > 40 && f[0] == 'd' && f[1] == 'e' && f[2] == 'y' && f[3] == '\n') base = rawU4(8);
        else if (f.length > 112 && f[0] == 'd' && f[1] == 'e' && f[2] == 'x' && f[3] == '\n') base = 0;
        else throw new Fail("ccnc-lp: not an odex/dex file");
        if (!(b[base] == 'd' && b[base + 1] == 'e' && b[base + 2] == 'x')) throw new Fail("ccnc-lp: bad dex header");
        fileSize = u4(32);
        strN = u4(56); strOff = u4(60); typeN = u4(64); typeOff = u4(68); protoN = u4(72); protoOff = u4(76);
        methN = u4(88); methOff = u4(92); cdefN = u4(96); cdefOff = u4(100);
    }

    int rawU4(int o) { return (b[o] & 0xff) | (b[o + 1] & 0xff) << 8 | (b[o + 2] & 0xff) << 16 | (b[o + 3] & 0xff) << 24; }
    int u4(int o) { return rawU4(base + o); }      // dex-relative
    int u2(int o) { return (b[base + o] & 0xff) | (b[base + o + 1] & 0xff) << 8; }
    void p2(int o, int v) { b[base + o] = (byte) v; b[base + o + 1] = (byte) (v >> 8); }

    int pos;
    int uleb() {
        int r = 0, s = 0, x;
        do { x = b[base + pos++] & 0xff; r |= (x & 0x7f) << s; s += 7; } while ((x & 0x80) != 0);
        return r;
    }

    String str(int i) {
        pos = u4(strOff + 4 * i);
        int n = uleb();
        StringBuilder sb = new StringBuilder(n);
        while (true) {                                           // MUTF-8
            int c = b[base + pos++] & 0xff;
            if (c == 0) break;
            if (c < 0x80) sb.append((char) c);
            else if ((c & 0xe0) == 0xc0) sb.append((char) ((c & 0x1f) << 6 | (b[base + pos++] & 0x3f)));
            else { int c2 = b[base + pos++] & 0x3f, c3 = b[base + pos++] & 0x3f; sb.append((char) ((c & 0x0f) << 12 | c2 << 6 | c3)); }
        }
        return sb.toString();
    }

    int stringIdx(String s) {                                    // string_ids are sorted, but a scan is cheap enough
        for (int i = 0; i < strN; i++) if (str(i).equals(s)) return i;
        return -1;
    }

    String typeName(int t) { return str(u4(typeOff + 4 * t)); }

    int typeIdx(String d) {
        for (int i = 0; i < typeN; i++) if (typeName(i).equals(d)) return i;
        return -1;
    }

    int methClass(int m) { return u2(methOff + 8 * m); }
    String methName(int m) { return str(u4(methOff + 8 * m + 4)); }
    String retType(int m) { return typeName(u4(protoOff + 12 * u2(methOff + 8 * m + 2) + 4)); }

    String protoStr(int m) {
        int p = protoOff + 12 * u2(methOff + 8 * m + 2);
        StringBuilder sb = new StringBuilder("(");
        int pl = u4(p + 8);
        if (pl != 0) for (int i = 0, n = u4(pl); i < n; i++) sb.append(typeName(u2(pl + 4 + 2 * i)));
        return sb.append(')').append(typeName(u4(p + 4))).toString();
    }

    int methodIdx(String cls, String name, String proto) {
        for (int i = 0; i < methN; i++)
            if (typeName(methClass(i)).equals(cls) && methName(i).equals(name) && protoStr(i).equals(proto)) return i;
        return -1;
    }

    int classDef(String d) {
        for (int c = 0; c < cdefN; c++) if (typeName(u4(cdefOff + c * 32)).equals(d)) return c;
        return -1;
    }

    /* {method idx, access, code_off (dex-relative)} for direct + virtual methods */
    List<int[]> methods(int c) {
        List<int[]> out = new ArrayList<int[]>();
        int cd = u4(cdefOff + c * 32 + 24);
        if (cd == 0) return out;
        pos = cd;
        int sf = uleb(), inf = uleb(), dm = uleb(), vm = uleb();
        for (int i = 0; i < 2 * (sf + inf); i++) uleb();
        for (int part = 0; part < 2; part++) {
            int idx = 0;
            for (int i = 0, n = part == 0 ? dm : vm; i < n; i++) {
                idx += uleb();
                int acc = uleb(), code = uleb();
                out.add(new int[] {idx, acc, code});
            }
        }
        return out;
    }

    /* Only the adler32 checksum is redone. The SHA-1 signature is kept on purpose: it is the dex's identity
     * in other odex files' dependency lists and in LivePatch's own odex_sig(), Dalvik never re-hashes it at
     * load, and the ccnc.volume / ccnc.sideosd byte patches that run on the car (CEEV.CAN 250818) kept it too.
     * (-Dccnc.lp.resign=true redoes it as well, like ccnc-climatebtn/patch.py does.) */
    void fixSums() throws Exception {
        if (Boolean.getBoolean("ccnc.lp.resign")) {
            MessageDigest sha = MessageDigest.getInstance("SHA-1");
            sha.update(b, base + 32, fileSize - 32);
            System.arraycopy(sha.digest(), 0, b, base + 12, 20);
        }
        Adler32 ad = new Adler32();
        ad.update(b, base + 12, fileSize - 12);
        int v = (int) ad.getValue();
        b[base + 8] = (byte) v; b[base + 9] = (byte) (v >> 8); b[base + 10] = (byte) (v >> 16); b[base + 11] = (byte) (v >> 24);
    }

    // ---------------------------------------------------------------- util

    static final class Fail extends RuntimeException { Fail(String s) { super(s); } }
    static void fail(String s) { throw new Fail(s); }
    static void say(String s) { System.out.println(s); }

    static byte[] read(File f) throws IOException {
        InputStream in = new FileInputStream(f);
        try {
            ByteArrayOutputStream o = new ByteArrayOutputStream((int) f.length());
            byte[] buf = new byte[1 << 16];
            for (int n; (n = in.read(buf)) > 0; ) o.write(buf, 0, n);
            return o.toByteArray();
        } finally { in.close(); }
    }
}
