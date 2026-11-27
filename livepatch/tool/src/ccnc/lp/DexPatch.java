/*
 * ccnc-lp: name-based odex patcher for NEMESIS LivePatch's `exec` engine (bin/lp-ccnc runs it).
 *
 *   DexPatch <mode> <in.odex> <out.odex>   exit 0 written, 10 already patched, 1 doesn't fit this build
 *                                          (nothing written), 3 tool error (bad file, crash, patch didn't take)
 *   DexPatch check <mode> <in.odex>        exit 0 patchable, 10 already patched, 1 doesn't fit, 3 error
 *
 * modes:
 *   volume   Lcom/hkmc/micom/MicomVolumePanel; onShowVolumeChanged(..)V and onShowVolumeChangedNew(..)V
 *            -> insns[0] = return-void; and ModeManagerService.handleAvOff's AV off OSD (showNotification(1000)
 *            after "show AV off OSD") -> nops, when that shape is there (else left as it is)
 *   sideosd  every class under Lcom/mobis/splitscreen/right/ that declares canShowSSOsd()Z,
 *            canShowSSOsd(ZZZ)Z (also the binder stub's), canShowSSOsdWithoutVR()Z or canShowSSOsdWithDispOff()Z
 *            -> "const/4 v0, 0; return v0". (ZZZ) has to go too: track-change popups ask through it. The
 *            system's own AV off OSD that this causes at boot is removed by the volume step.
 *   climate  every launch site under Lcom/hkmc/telematics/service/apps/climate/ of the shape
 *              new-instance vA, Intent; invoke-direct {vA}, Intent.<init>()V;
 *              const-string vB, "<pkg>"; const-string vC, "<pkg>.activities.(NewClimateControl|SeatHeatVentPopup)";
 *              invoke-virtual[-quick] {vA, vB, vC}, setClassName; move-result-object vA
 *            -> new-instance vA, Intent; const-string vB, "<pkg>"; const-string vC, "<cls>";
 *               invoke-direct {vA, vC}, Intent.<init>(String); nop...
 *            (same registers hold the same values afterwards as in stock; the intent's action is the
 *             stock class name, which ccNC Climate claims)
 *   consent  SystemUI's start-up profile screen (Lcom/android/systemui/consentscreen/, found by its log strings),
 *            so ccNC Profile's start-up chooser can sit on top of it and ask it to continue. Three edits, all or
 *            none (see consent() below and private/systemui/DESIGN.txt):
 *              1. its window type 2033 -> 2003 (TYPE_SYSTEM_ALERT): one layer lower, still under the rear camera
 *              2. handleMessage: message 1 keeps calling confirm(); unused message 4 becomes
 *                 "if (<the stock timer's gate>()) confirm()" in the room of message 1's log line
 *              3. the HIDE_CONSENT_SCREEN broadcast sends message 4 instead of calling confirm() directly
 *
 * Classes and methods are found by stable names, string constants and code shape, never by the
 * obfuscated names (ClimateManager.aa/mD, RightScreenHome k/r), so one tool fits any firmware where
 * the shapes hold. Anything that does not fit exactly -> exit 1 and nothing is written (bin/lp-ccnc then
 * leaves that file stock and the rest of the package installs).
 *
 * The output depends only on the input bytes and the mode (LivePatch rule). Only code units inside
 * existing methods change, plus the dex adler32 checksum. The SHA-1 signature is kept (it is the dex's
 * identity in odex dependency lists); odex deps/opt data untouched.
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
    static final int PATCHABLE = 0, DONE = 10, NOFIT = 1, ERROR = 3;

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
                System.out.println("usage: DexPatch <volume|sideosd|climate|consent> <in> <out> | check <mode> <in>");
                System.exit(2);
            }
            DexPatch d = new DexPatch(read(new File(a[1])));
            int rc = d.run(a[0], true);
            if (rc == PATCHABLE) {
                d.fixSums();
                FileOutputStream o = new FileOutputStream(a[2]);
                try { o.write(d.b); } finally { o.close(); }
                int back;                                            // re-read the output: must now be "done"
                try { back = new DexPatch(read(new File(a[2]))).run(a[0], false); } catch (RuntimeException e) { back = -1; }
                if (back != DONE) { new File(a[2]).delete(); throw new IllegalStateException("patch did not take"); }
                System.out.println(a[0] + ": patched");
            }
            System.exit(rc);
        } catch (Fail f) {                       // the file is fine but this build doesn't have the shape
            System.out.println(f.getMessage());
            System.exit(NOFIT);
        } catch (Throwable t) {                  // bad/truncated file, crash, or the patch didn't verify
            System.out.println("ccnc-lp: error: " + t);
            System.exit(ERROR);
        }
    }

    int run(String mode, boolean write) {
        if (mode.equals("volume")) return volume(write);
        if (mode.equals("sideosd")) return sideosd(write);
        if (mode.equals("climate")) return climate(write);
        if (mode.equals("consent")) return consent(write);
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
        int osd = avOffOsd(write);   // 1 already, 0 patched/patchable, -1 not on this build (left as it is)
        int done = osd == 0 ? -1 : 0;
        for (int[] m : ms) {
            int insns = m[2] + 16;
            if (u2(insns) == 0x000e) { done++; continue; }
            if (write) p2(insns, 0x000e);
            say("volume: " + methName(m[0]) + protoStr(m[0]) + (write ? " -> return-void" : " patchable"));
        }
        if (osd < 0) say("volume: no AV off OSD call found on this build; left as it is");
        return done == ms.size() ? DONE : PATCHABLE;
    }

    /**
     * The system's own "AV off" OSD at boot (ModeManagerService.handleAvOff: Slog "show AV off OSD", then
     * this.showNotification(1000)). It is shown when the right screen answers canShowSSOsd=false, which sideosd
     * makes it do. Found by shape: after that string, "const/16 vX, #1000" then "invoke-virtual-quick {this, vX}";
     * the invoke becomes three nops. Returns 1 if already nops, 0 if patched (or patchable), -1 if not found.
     */
    int avOffOsd(boolean write) {
        int cls = classDef("Lcom/hkmc/micom/ModeManagerService;");
        int str = stringIdx("show AV off OSD");
        if (cls < 0 || str < 0) return -1;
        for (int[] m : methods(cls)) {
            if (!methName(m[0]).equals("handleAvOff") || m[2] == 0) continue;
            int regs = u2(m[2]), ins = u2(m[2] + 2), self = regs - ins, insns = m[2] + 16, n = u4(m[2] + 12);
            for (int pc = 0; pc < n; pc += width(insns, pc, n)) {
                int w0 = u2(insns + pc * 2);
                if ((w0 & 0xff) != 0x1a || u2(insns + pc * 2 + 2) != str) continue;
                for (int q = pc, k = 0; q < n && k < 12; q += width(insns, q, n), k++) {
                    int c = u2(insns + q * 2);
                    if ((c & 0xff) != 0x13 || u2(insns + q * 2 + 2) != 1000) continue;
                    int vx = c >> 8, at = q + 2;
                    if (u2(insns + at * 2) == 0 && u2(insns + at * 2 + 2) == 0 && u2(insns + at * 2 + 4) == 0) return 1;
                    int i0 = u2(insns + at * 2), args = u2(insns + at * 2 + 4);
                    if ((i0 & 0xff) != 0xf8 || (i0 >> 12) != 2 || (args & 0xf) != self || ((args >> 4) & 0xf) != vx) return -1;
                    if (write) { p2(insns + at * 2, 0); p2(insns + at * 2 + 2, 0); p2(insns + at * 2 + 4, 0); }
                    say("volume: ModeManagerService.handleAvOff AV off OSD (showNotification 1000) -> " + (write ? "skipped" : "patchable"));
                    return 0;
                }
                return -1;
            }
        }
        return -1;
    }

    int sideosd(boolean write) {
        List<int[]> ms = new ArrayList<int[]>();
        boolean plain = false;
        for (int c = 0; c < cdefN; c++) {
            if (!typeName(u4(cdefOff + c * 32)).startsWith("Lcom/mobis/splitscreen/right/")) continue;
            for (int[] m : methods(c)) {
                String n = methName(m[0]);
                if (!(n.equals("canShowSSOsd") || n.equals("canShowSSOsdWithoutVR") || n.equals("canShowSSOsdWithDispOff"))) continue;
                String pr = protoStr(m[0]);
                if (m[2] == 0 || !(pr.equals("()Z") || (n.equals("canShowSSOsd") && pr.equals("(ZZZ)Z")))) continue;
                if (u2(m[2]) < 1 || u4(m[2] + 12) < 2) throw new Fail("sideosd: " + n + " too small to stub");
                if (n.equals("canShowSSOsd") && pr.equals("()Z")) plain = true;
                ms.add(m);
            }
        }
        if (!plain) throw new Fail("sideosd: no canShowSSOsd()Z in this file");
        int view = avOffView(write);   // 1 already, 0 patched/patchable, -1 not on this build (left as it is)
        int done = view == 0 ? -1 : 0;
        for (int[] m : ms) {
            int insns = m[2] + 16;
            if (u2(insns) == 0x0012 && u2(insns + 2) == 0x000f) { done++; continue; }
            if (write) { p2(insns, 0x0012); p2(insns + 2, 0x000f); }
            say("sideosd: " + typeName(methClass(m[0])) + "->" + methName(m[0]) + protoStr(m[0]) + (write ? " -> false" : " patchable"));
        }
        if (view < 0) say("sideosd: no right-screen AV off view call found on this build; left as it is");
        return done == ms.size() ? DONE : PATCHABLE;
    }

    /**
     * The right screen's own "Media off" panel (SplitScreenHome.avOff: log " avOff() AvOff View Osd Show", then
     * nN.f(true) and nN.v() on the SplitScreenViewAvOff view). Found by shape: between that string and the next goto,
     * exactly two calls on that view (invoke-virtual r.f(Z)/r.v(), or their invoke-virtual-quick form); both become three nops, so the panel keeps showing what it showed. Nothing
     * else in avOff changes (the AV off state itself, the radio/media views, the log). Returns 1 if already nops,
     * 0 if patched (or patchable), -1 if the shape isn't there.
     */
    int avOffView(boolean write) {
        int cls = classDef("Lcom/mobis/splitscreen/right/SplitScreenHome;");
        int str = stringIdx(" avOff() AvOff View Osd Show");
        if (cls < 0 || str < 0) return -1;
        for (int[] m : methods(cls)) {
            if (m[2] == 0) continue;
            int insns = m[2] + 16, n = u4(m[2] + 12);
            for (int pc = 0; pc < n; pc += width(insns, pc, n)) {
                int[] cs = constString(insns, pc, n);
                if (cs == null || cs[1] != str) continue;
                List<Integer> calls = new ArrayList<Integer>();
                int nopRuns = 0, run = 0;
                for (int q = pc + cs[2], k = 0; q < n && k < 16; k++) {
                    int w = u2(insns + q * 2), op = w & 0xff;
                    if (w == 0) { run++; q++; continue; }
                    if (run > 0) { if (run == 3) nopRuns++; run = 0; }
                    if (op == 0x28 || op == 0x29 || op == 0x2a || op == 0x0e) break;   // goto / return: end of the branch
                    if (op == 0xf8) calls.add(q);                       // optimised: vtable call
                    else if (op == 0x6e) {                                 // plain: must be SplitScreenViewAvOff.f(Z) / v()
                        int mi = u2(insns + q * 2 + 2);
                        String nm = methName(mi), pr = protoStr(mi);
                        if (typeName(methClass(mi)).equals("Lcom/mobis/splitscreen/right/r;")
                                && ((nm.equals("f") && pr.equals("(Z)V")) || (nm.equals("v") && pr.equals("()V")))) calls.add(q);
                    }
                    q += width(insns, q, n);
                }
                if (calls.isEmpty() && nopRuns == 2) return 1;
                if (calls.size() != 2 || nopRuns != 0) return -1;
                for (int at : calls) {
                    if (write) { p2(insns + at * 2, 0); p2(insns + at * 2 + 2, 0); p2(insns + at * 2 + 4, 0); }
                }
                say("sideosd: SplitScreenHome." + methName(m[0]) + " AV off view (f(true), v()) -> " + (write ? "skipped" : "patchable"));
                return 0;
            }
        }
        return -1;
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
        // Both launchers or none: one alone would leave one button on the stock screen.
        for (int k = 0; k < seen.length; k++)
            if (seen[k] == 0) throw new Fail("climate: no launch of " + CLIMATE_TARGETS[k] + " found; left stock");
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

    // ---------------------------------------------------------------- consent (SystemUI start-up profile screen)

    static final String CS_PKG = "Lcom/android/systemui/consentscreen/";
    static final int TYPE_CONSENT = 2033, TYPE_SYSTEM_ALERT = 2003, MSG_GATED = 4;

    /**
     * SystemUI's ConsentScreen (the agreement, the three profiles and Done at start-up). After this:
     *   - its window is TYPE_SYSTEM_ALERT (policy layer 17) instead of the car's type 2033 (layer 18), so an app's
     *     system-alert window added later (ccNC Profile's start-up chooser) is drawn above it. The rear camera's
     *     windows (type 2035, layer 20) stay above both. Without the chooser the stock screen shows and works.
     *   - HIDE_CONSENT_SCREEN no longer calls confirm() directly: it sends the screen's own handler message 4, and
     *     message 4 confirms only if the method the stock 10 s timer asks ("mScreenEnabled : ..", here: the gate)
     *     says yes at that moment: screen enabled, no early camera, no reverse, power on or a call, ACC state 0,
     *     no language dialog, no update view, no valet, profile known. Otherwise nothing happens.
     *   - message 1 (the stock timer, and the firmware-update path) calls confirm() as before; only its debug log
     *     line is gone, because the gate sits in those 7 code units.
     * Everything is found by strings and shape. All three edits must be in the stock shape (-> patch) or all in
     * the patched shape (-> done); anything else leaves the file alone.
     */
    int consent(boolean write) {
        try {
            return consent0(write);
        } catch (Fail f) {
            throw f;
        } catch (RuntimeException e) {       // an index ran off a method: not the layout this was written for
            throw new Fail("consent: unexpected code layout (" + e + "); left stock");
        }
    }

    private int consent0(boolean write) {
        int sGate = stringIdx("mScreenEnabled : "), sClose = stringIdx("handleMessage : MSG_CLOSE_CONSENT");
        int sHide = stringIdx("com.mobis.intent.action.HIDE_CONSENT_SCREEN"), sTitle = stringIdx("ConsentScreen");
        int sConfirm = stringIdx("confirm() : ConsentScreen is going to close");
        if (sGate < 0 || sClose < 0 || sHide < 0 || sTitle < 0 || sConfirm < 0) throw new Fail("consent: not SystemUI's consent screen (strings missing)");

        // the screen's class and its gate: the one ()Z method that logs "mScreenEnabled : "
        int cls = -1, mGate = -1;
        for (int c = 0; c < cdefN; c++) {
            if (!typeName(u4(cdefOff + c * 32)).startsWith(CS_PKG)) continue;
            for (int[] m : methods(c)) {
                if (m[2] == 0 || !protoStr(m[0]).equals("()Z") || !refsString(m, sGate)) continue;
                if (mGate >= 0) throw new Fail("consent: more than one gate method");
                cls = c; mGate = m[0];
            }
        }
        if (mGate < 0) throw new Fail("consent: no gate method (\"mScreenEnabled : \")");
        String E = typeName(u4(cdefOff + cls * 32));

        // confirm(): the ()V method of that class that logs "confirm() : ConsentScreen is going to close"
        int mConfirm = -1;
        int[] handle = null, params = null;
        int mSendAcc = -1, mConfirmAcc = -1;
        for (int[] m : methods(cls)) {
            if (m[2] == 0) continue;
            String n = methName(m[0]), pr = protoStr(m[0]);
            if (pr.equals("()V") && refsString(m, sConfirm)) { if (mConfirm >= 0) throw new Fail("consent: two confirm methods"); mConfirm = m[0]; }
            if (n.equals("handleMessage") && pr.equals("(Landroid/os/Message;)Z")) handle = m;
            if (pr.equals("()Landroid/view/WindowManager$LayoutParams;") && refsString(m, sTitle)) { if (params != null) throw new Fail("consent: two window-params methods"); params = m; }
        }
        if (mConfirm < 0 || handle == null || params == null) throw new Fail("consent: confirm / handleMessage / window params not found");
        // the two static bridges other classes use: X(E)V = { confirm() } and Y(E,I)V = { sendEmptyMessage(I) }
        for (int[] m : methods(cls)) {
            if (m[2] == 0 || (m[1] & 0x8) == 0 || u4(m[2] + 12) != 4) continue;
            int in = m[2] + 16;
            String pr = protoStr(m[0]);
            if (pr.equals("(" + E + ")V") && u2(in) == 0x1070 && u2(in + 2) == mConfirm && u2(in + 4) == 0 && u2(in + 6) == 0x000e) {
                if (mConfirmAcc >= 0) throw new Fail("consent: two confirm bridges"); mConfirmAcc = m[0];
            }
            if (pr.equals("(" + E + "I)V") && u2(in) == 0x2070 && u2(in + 4) == 0x0010 && u2(in + 6) == 0x000e) {
                int t = u2(in + 2);
                if (typeName(methClass(t)).equals(E) && methName(t).equals("sendEmptyMessage") && protoStr(t).equals("(I)V")) {
                    if (mSendAcc >= 0) throw new Fail("consent: two sendEmptyMessage bridges"); mSendAcc = m[0];
                }
            }
        }
        if (mConfirmAcc < 0 || mSendAcc < 0) throw new Fail("consent: confirm / sendEmptyMessage bridge not found");

        int[] type = consentType(params);                       // {offset of the value unit, state}
        int[] gate = consentGate(handle, mGate, mConfirm, sClose);   // {T (old case 1), S (switch), payload, R, this, ret, state}
        int[] hide = consentHide(E, sHide, mConfirmAcc, mSendAcc);   // {offset of the first changed unit, object reg, spare reg, state}
        int st = type[1] + gate[6] + hide[3];                   // each 0 stock, 1 patched
        if (st == 3) return DONE;
        if (st != 0) throw new Fail("consent: partly patched file (type " + type[1] + ", gate " + gate[6] + ", hide " + hide[3] + "); left as it is");
        if (write) {
            p2(type[0], TYPE_SYSTEM_ALERT);
            int at = handle[2] + 16, T = gate[0], S = gate[1], pl = gate[2], R = gate[3];
            p2(at + 2 * T, 0x1070); p2(at + 2 * (T + 1), mGate); p2(at + 2 * (T + 2), gate[4]);   // invoke-direct {this}, gate()Z
            p2(at + 2 * (T + 3), 0x0a | R << 8);                                                  // move-result vR
            p2(at + 2 * (T + 4), 0x38 | R << 8); p2(at + 2 * (T + 5), (gate[5] - (T + 4)) & 0xffff);  // if-eqz vR, <return vR>
            p2(at + 2 * (T + 6), 0x12 | R << 8);                                                  // const/4 vR, 0
            p4(at + 2 * pl + 8, T + 7 - S);                                                       // message 1 -> confirm()
            p4(at + 2 * pl + 8 + 4 * (MSG_GATED - 1), T - S);                                     // message 4 -> gate, then confirm()
            int h = hide[0];
            p2(h, 0x12 | hide[2] << 8 | MSG_GATED << 12);                                         // const/4 vK, 4
            p2(h + 2, 0x2071); p2(h + 4, mSendAcc); p2(h + 6, hide[1] | hide[2] << 4);            // invoke-static {vO, vK}, Y(E,I)V
            p2(h + 8, 0x000e);                                                                    // return-void
        }
        say("consent: " + E + " window type " + TYPE_CONSENT + " -> " + TYPE_SYSTEM_ALERT + (write ? "" : " patchable"));
        say("consent: handleMessage: message " + MSG_GATED + " = if (" + methName(mGate) + "()) " + methName(mConfirm) + "(); message 1 unchanged" + (write ? "" : " (patchable)"));
        say("consent: HIDE_CONSENT_SCREEN -> message " + MSG_GATED + " instead of " + methName(mConfirm) + "()" + (write ? "" : " (patchable)"));
        return PATCHABLE;
    }

    boolean refsString(int[] m, int str) {
        int insns = m[2] + 16, n = u4(m[2] + 12);
        for (int pc = 0; pc < n; pc += width(insns, pc, n)) {
            int[] cs = constString(insns, pc, n);
            if (cs != null && cs[1] == str) return true;
        }
        return false;
    }

    /* The main window's type: "const/16 vX, #type" feeding invoke-direct/range {v0..v7} LayoutParams.<init>(IIIIIII)V as its 5th int. */
    int[] consentType(int[] m) {
        int insns = m[2] + 16, n = u4(m[2] + 12), found = -1, state = -1;
        for (int pc = 0; pc < n; pc += width(insns, pc, n)) {
            int w = u2(insns + pc * 2);
            if ((w & 0xff) != 0x76 || (w >> 8) != 8) continue;
            int t = u2(insns + pc * 2 + 2), first = u2(insns + pc * 2 + 4);
            if (!typeName(methClass(t)).equals("Landroid/view/WindowManager$LayoutParams;") || !methName(t).equals("<init>") || !protoStr(t).equals("(IIIIIII)V")) continue;
            int reg = first + 5, at = -1;                         // this, w, h, x, y, type
            for (int q = 0; q < pc; q += width(insns, q, n)) {     // the last "const/16 vReg" before the call
                int c = u2(insns + q * 2);
                if ((c & 0xff) == 0x13 && (c >> 8) == reg) at = q;
            }
            if (at < 0 || found >= 0) throw new Fail("consent: window type constant not found (or two windows)");
            int v = u2(insns + at * 2 + 2);
            if (v != TYPE_CONSENT && v != TYPE_SYSTEM_ALERT) throw new Fail("consent: window type is " + v + ", not " + TYPE_CONSENT);
            found = insns + at * 2 + 2; state = v == TYPE_SYSTEM_ALERT ? 1 : 0;
        }
        if (found < 0) throw new Fail("consent: no LayoutParams(w, h, x, y, type, flags, format) call");
        return new int[] {found, state};
    }

    /*
     * handleMessage's packed-switch (first key 1). Stock: key 4 has no case (falls to the instruction after the
     * switch) and key 1 is
     *     T:    const-string vR, tag; const-string vB, "handleMessage : MSG_CLOSE_CONSENT"; invoke-static {vR, vB}, log   (7 units)
     *     T+7:  invoke-direct {this}, confirm()V
     *     T+10: move vR, vTrue; goto <return vR>
     * Patched: key 4 -> T, key 1 -> T+7, and T is
     *     invoke-direct {this}, gate()Z; move-result vR; if-eqz vR, <return vR>; const/4 vR, 0                           (7 units)
     * vR is 0 again at T+7, where stock held the tag string in it; it is overwritten by the move at T+10 either way.
     */
    int[] consentGate(int[] m, int mGate, int mConfirm, int sClose) {
        int insns = m[2] + 16, n = u4(m[2] + 12), self = u2(m[2]) - u2(m[2] + 2), S = -1;
        if (u2(m[2] + 6) != 0) throw new Fail("consent: handleMessage has try blocks");
        for (int pc = 0; pc < n; pc += width(insns, pc, n)) if ((u2(insns + pc * 2) & 0xff) == 0x2b) { S = pc; break; }
        if (S < 0) throw new Fail("consent: handleMessage has no packed-switch");
        int pl = S + (u2(insns + S * 2 + 2) | u2(insns + S * 2 + 4) << 16);
        if (pl < 0 || pl + 4 > n || u2(insns + pl * 2) != 0x0100 || u2(insns + pl * 2 + 2) < MSG_GATED || u4(insns + pl * 2 + 4) != 1)
            throw new Fail("consent: handleMessage's switch doesn't start at message 1");
        int t1 = S + u4(insns + pl * 2 + 8), t4 = S + u4(insns + pl * 2 + 8 + 4 * (MSG_GATED - 1));
        boolean patched = t1 == t4 + 7;
        int T = patched ? t4 : t1;
        if (T < 0 || T + 12 > n) throw new Fail("consent: message 1's case is out of range");
        if (!patched && t4 != S + 3) throw new Fail("consent: message " + MSG_GATED + " already has a case");
        // T+7: invoke-direct {this}, confirm; move vR, vX; goto <return vR>
        if (u2(insns + (T + 7) * 2) != 0x1070 || u2(insns + (T + 8) * 2) != mConfirm || u2(insns + (T + 9) * 2) != self || self > 15)
            throw new Fail("consent: message 1 doesn't call confirm() where expected");
        int mv = u2(insns + (T + 10) * 2), gt = u2(insns + (T + 11) * 2);
        if ((mv & 0xff) != 0x01 || (gt & 0xff) != 0x28) throw new Fail("consent: message 1 doesn't end with move + goto");
        int R = (mv >> 8) & 0xf, ret = T + 11 + (byte) (gt >> 8);
        if (ret < 0 || ret >= n || u2(insns + ret * 2) != (0x0f | R << 8)) throw new Fail("consent: message 1 doesn't end at 'return vR'");
        if (R == self || R >= self) throw new Fail("consent: result register is a parameter");
        if (patched) {
            if (u2(insns + T * 2) != 0x1070 || u2(insns + (T + 1) * 2) != mGate || u2(insns + (T + 2) * 2) != self
                    || u2(insns + (T + 3) * 2) != (0x0a | R << 8) || u2(insns + (T + 4) * 2) != (0x38 | R << 8)
                    || (short) u2(insns + (T + 5) * 2) != ret - (T + 4) || u2(insns + (T + 6) * 2) != (0x12 | R << 8))
                throw new Fail("consent: message " + MSG_GATED + " has a case that isn't ours");
            return new int[] {T, S, pl, R, self, ret, 1};
        }
        int[] s1 = constString(insns, T, n), s2 = constString(insns, T + 2, n);
        int lg = u2(insns + (T + 4) * 2);
        if (s1 == null || s2 == null || s1[2] != 2 || s2[2] != 2 || s2[1] != sClose || s1[0] != R || s2[0] > 15
                || lg != 0x2071 || u2(insns + (T + 6) * 2) != (s1[0] | s2[0] << 4))
            throw new Fail("consent: message 1 doesn't start with its log line");
        if (ret - (T + 4) < -32768 || ret - (T + 4) > 32767) throw new Fail("consent: return is too far");
        // nothing else may jump into the 7 units that change (T+1 .. T+6): every case target must be outside them
        for (int i = 0, cnt = u2(insns + pl * 2 + 2); i < cnt; i++) {
            int t = S + u4(insns + pl * 2 + 8 + 4 * i);
            if (t > T && t < T + 7) throw new Fail("consent: another case jumps into message 1's log line");
        }
        return new int[] {T, S, pl, R, self, ret, 0};
    }

    /*
     * The receiver's HIDE_CONSENT_SCREEN branch. Stock (after the string compare and its if-eqz):
     *     iget-object[-quick] vO, this, screen; invoke-static {vO}, X(E)V; goto/16 <return-void>          (2 + 3 + 2)
     * Patched:
     *     iget-object[-quick] vO, this, screen; const/4 vK, 4; invoke-static {vO, vK}, Y(E,I)V; return-void  (2 + 1 + 3 + 1)
     */
    int[] consentHide(String E, int sHide, int mConfirmAcc, int mSendAcc) {
        int[] out = null;
        for (int c = 0; c < cdefN; c++) {
            if (!typeName(u4(cdefOff + c * 32)).startsWith(CS_PKG)) continue;
            for (int[] m : methods(c)) {
                if (m[2] == 0) continue;
                int insns = m[2] + 16, n = u4(m[2] + 12);
                for (int pc = 0; pc < n; pc += width(insns, pc, n)) {
                    int[] cs = constString(insns, pc, n);
                    if (cs == null || cs[1] != sHide) continue;
                    int q = pc + cs[2];
                    int cmp = u2(insns + q * 2) & 0xff;
                    if (cmp != 0xee && cmp != 0x6e && cmp != 0xf8) continue;       // equals(): inline, virtual or quick (the intent filter's addAction isn't followed by a move-result)
                    q += 3;
                    if ((u2(insns + q * 2) & 0xff) != 0x0a) continue;              // move-result
                    int res = u2(insns + q * 2) >> 8;
                    q += 1;
                    if (u2(insns + q * 2) != (0x38 | res << 8)) continue;          // if-eqz
                    q += 2;
                    if (out != null) throw new Fail("consent: HIDE_CONSENT_SCREEN is handled in two places");
                    if (!methName(m[0]).equals("onReceive") || !protoStr(m[0]).equals("(Landroid/content/Context;Landroid/content/Intent;)V") || u2(m[2] + 6) != 0)
                        throw new Fail("consent: HIDE_CONSENT_SCREEN isn't handled in a plain onReceive");
                    int ig = u2(insns + q * 2), op = ig & 0xff, self = u2(m[2]) - u2(m[2] + 2), locals = self;
                    if ((op != 0xf4 && op != 0x54) || ((ig >> 12) & 0xf) != self || q + 7 > n) throw new Fail("consent: HIDE branch doesn't load the screen first");
                    int O = (ig >> 8) & 0xf, K = O == 1 ? 2 : 1;
                    if (locals <= K || O >= locals || u2(m[2] + 4) < 2) throw new Fail("consent: no spare register / outgoing slots in onReceive");
                    int a = insns + (q + 2) * 2;
                    if (u2(a) == 0x1071 && u2(a + 2) == mConfirmAcc && u2(a + 4) == O && u2(a + 6) == 0x0029) {
                        int tgt = q + 5 + (short) u2(a + 8);
                        if (tgt < 0 || tgt >= n || u2(insns + tgt * 2) != 0x000e) throw new Fail("consent: HIDE branch doesn't end at return-void");
                        out = new int[] {a, O, K, 0};
                    } else if ((u2(a) & 0xf0ff) == (0x12 | MSG_GATED << 12) && u2(a + 2) == 0x2071 && u2(a + 4) == mSendAcc
                            && u2(a + 6) == (O | ((u2(a) >> 8) & 0xf) << 4) && u2(a + 8) == 0x000e) {
                        out = new int[] {a, O, (u2(a) >> 8) & 0xf, 1};
                    } else throw new Fail("consent: HIDE branch has another shape");
                }
            }
        }
        if (out == null) throw new Fail("consent: no HIDE_CONSENT_SCREEN branch");
        return out;
    }

    void p4(int o, int v) { p2(o, v & 0xffff); p2(o + 2, v >>> 16); }

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
        if (fileSize < 112 || base < 0 || (long) base + fileSize > f.length)
            throw new IllegalArgumentException("dex size " + fileSize + " at " + base + " doesn't fit the file (" + f.length + " bytes)");
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
