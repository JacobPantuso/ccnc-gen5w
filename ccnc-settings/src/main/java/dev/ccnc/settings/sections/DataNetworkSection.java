package dev.ccnc.settings.sections;

import android.content.Context;
import android.content.pm.PackageManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.ccnc.settings.api.Ask;
import dev.ccnc.settings.api.Category;
import dev.ccnc.settings.api.Fn;
import dev.ccnc.settings.api.Host;
import dev.ccnc.settings.api.Page;
import dev.ccnc.settings.api.Scontrol;
import dev.ccnc.settings.api.Section;

/**
 * Data/Network (stock HKMC_WifiSettings NetworkConnectionSettings, a TabActivity). Stock tabs, in order:
 *  1. Bluelink / Kia Connect (the telematics settings): see {@link #telematics}.
 *  2. Wi-Fi (only if PackageManager.hasSystemFeature("android.hardware.wifi"), stock e.m(ctx)).
 *  3. Wi-Fi Hotspot (only if hasSystemFeature("com.hkmc.software.wifihotspot"), stock e.n(ctx)).
 * All three are ccNC pages; no stock screen is opened.
 *  - Wi-Fi: WifiManager (CHANGE_WIFI_STATE is granted at install): the switch, the scan list, join (saved, open or
 *    WPA/WPA2 with a password typed in a ccNC panel) and forget. The Wi-Fi radio is shared with wireless phone
 *    projection: while the projection access point is up (getWifiApState() == 13 with getWifiApMode() != 1, the stock
 *    e.g(ctx) test inverted) the page changes nothing and says why.
 *  - Wi-Fi Hotspot: Enable Hotspot goes through the stock HotspotService smart-control key 30004 (type 14), so the
 *    stock projection checks still run. SSID / Password / Frequency Band are shown READ ONLY on purpose: writing the
 *    access-point configuration directly (WifiManager.setWifiApConfiguration) would bypass those checks and the
 *    configuration is shared with wireless CarPlay. Owner decision.
 * Not tested on car.
 */
public final class DataNetworkSection extends Section {
    @Override public String classId() { return "com.hkmc.wifisettings.settings.ui.NetworkConnectionSettings"; }
    @Override public int treeNode() { return 1009; }
    @Override public String icon() { return "set_ic_menu_datanetwork"; }

    private static final String TAG = "ccncSetup";
    private static final String PKG = "com.hkmc.wifisettings";
    private static final int K_HOTSPOT = 30004;

    @Override public void build(Category c) {
        final Context x = c.context();
        telematics(c, x, c.word(8011, "Bluelink"));
        PackageManager pm = x.getPackageManager();
        if (pm.hasSystemFeature("android.hardware.wifi") || dev.ccnc.settings.api.Demo.on()) {
            c.tab("Wi-Fi", new Fn.Build() {
                @Override public void build(Page p) { wifiPage(p, x); }
            });
        }
        if (pm.hasSystemFeature("com.hkmc.software.wifihotspot") || dev.ccnc.settings.api.Demo.on()) {
            final Scontrol sc = Scontrol.of(x, PKG, 14);
            sc.prefetch(K_HOTSPOT);
            c.tab(c.word(8004, "Wi-Fi Hotspot"), new Fn.Build() {
                @Override public void build(Page p) { hotspotPage(p, x, sc); }
            });
        }
    }

    // ---- Bluelink / telematics (also the Bluelink category) --------------------------------------------

    /**
     * The stock Bluelink menu on this car is "Activate Service" and "Modem Information".
     *  - Activate Service is the telematics provisioning flow (HKMC_TAppVRM talking to the Bluelink centre through the
     *    system-uid telematics service): BLOCKED for a normal app; the page says so.
     *  - Modem Information: the identifiers TelephonyManager reports (READ_PHONE_STATE), read only like the stock page.
     */
    static void telematics(Category c, final Context x, String title) {
        boolean vrm = false;
        try { x.getPackageManager().getPackageInfo("com.hkmc.telematics.apps.Vrm.main", 0); vrm = true; } catch (Throwable ignored) { }
        if (!vrm && !dev.ccnc.settings.api.Demo.on()) return;
        c.tab(title, new Fn.Build() {
            @Override public void build(Page p) {
                p.heading(p.word(8024, "Activate Service"));
                p.note("Bluelink activation is carried out by the unit's telematics service and cannot be started from ccNC Setup.");
                p.heading(p.word(8026, "Modem Information"));
                if (dev.ccnc.settings.api.Demo.on()) { // emulator demo: invented identifiers
                    p.info("IMEI", "000000000000000");
                    p.info("ICCID", "00000000000000000000");
                    p.info("Network", "Demo Network");
                    return;
                }
                TelephonyManager tm = (TelephonyManager) x.getSystemService(Context.TELEPHONY_SERVICE);
                boolean any = false;
                if (tm != null) {
                    any |= row(p, "IMEI", safe(tm, 0));
                    any |= row(p, "ICCID", safe(tm, 1));
                    any |= row(p, "Phone Number", safe(tm, 2));
                    any |= row(p, "Network", safe(tm, 3));
                }
                if (!any) p.note("Not available on this unit.");
            }
        });
    }

    private static boolean row(Page p, String title, String v) {
        if (v == null || v.trim().length() == 0) return false;
        p.info(title, v.trim());
        return true;
    }

    private static String safe(TelephonyManager tm, int what) {
        try {
            switch (what) {
                case 0: return tm.getDeviceId();
                case 1: return tm.getSimSerialNumber();
                case 2: return tm.getLine1Number();
                default: return tm.getNetworkOperatorName();
            }
        } catch (Throwable t) { return null; }
    }

    // ---- Wi-Fi ----------------------------------------------------------------------------------------

    private static WifiManager wifi(Context x) {
        return (WifiManager) x.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
    }

    private static String unquote(String s) {
        if (s != null && s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) return s.substring(1, s.length() - 1);
        return s;
    }

    private static int apInt(Context x, String m) {
        try { return ((Number) WifiManager.class.getMethod(m).invoke(wifi(x))).intValue(); }
        catch (Throwable t) { return -1; }
    }

    /** Stock e.g(ctx): getWifiApState() == 13 (enabled) && getWifiApMode() == 1 (hotspot, not projection). */
    private static boolean hotspotOn(Context x) {
        if (dev.ccnc.settings.api.Demo.on()) return dev.ccnc.settings.api.Demo.getBool("hotspot.on", false);
        return apInt(x, "getWifiApState") == 13 && apInt(x, "getWifiApMode") == 1;
    }

    /** The access point is up for wireless phone projection (enabled, but not in hotspot mode). */
    private static boolean projectionAp(Context x) {
        int mode = apInt(x, "getWifiApMode");
        return apInt(x, "getWifiApState") == 13 && mode >= 0 && mode != 1;
    }

    private static void later(final Host h, int ms) {
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override public void run() { try { h.refresh(); } catch (Throwable ignored) { } }
        }, ms);
    }

    /** Emulator demo (dev.ccnc.settings.api.Demo): the Wi-Fi page over three invented networks kept in memory. */
    private static void demoWifi(final Page p) {
        p.toggle("Wi-Fi", new Fn.Bool() {
            @Override public boolean get() { return dev.ccnc.settings.api.Demo.getBool("wifi.on", true); }
        }, new Fn.SetBool() {
            @Override public void set(boolean on) { dev.ccnc.settings.api.Demo.put("wifi.on", on); p.host().refresh(); }
        });
        if (!dev.ccnc.settings.api.Demo.getBool("wifi.on", true)) return;
        p.button("Search for Networks", new Fn.Act() { @Override public void run(Host h) { h.refresh(); } });
        final String cur = dev.ccnc.settings.api.Demo.getStr("wifi.connected", "");
        String[] nets = {"Demo Home Wi-Fi", "Demo Cafe", "Demo Guest"};
        java.util.List<String> order = new ArrayList<String>();
        for (String n : nets) if (n.equals(cur)) order.add(n);
        for (String n : nets) if (!n.equals(cur)) order.add(n);
        for (final String ssid : order) {
            final boolean on = ssid.equals(cur);
            final boolean open = ssid.endsWith("Guest");
            p.drill(ssid, new Fn.Str() {
                @Override public String get() { return on ? "Connected" : open ? "Open" : "Secured"; }
            }, new Fn.Build() {
                @Override public void build(Page q) {
                    q.info(ssid, on ? "Connected" : open ? "Open" : "Secured");
                    q.info("Signal Strength", (open ? 2 : 4) + " / 4");
                    if (!on) q.button("Connect", new Fn.Act() {
                        @Override public void run(final Host h) {
                            if (open) { dev.ccnc.settings.api.Demo.put("wifi.connected", ssid); h.back(); return; }
                            Ask.text(h.activity(), ssid, "", true, 63, new Ask.Text() {
                                @Override public void onText(String pw) { if (pw.length() >= 8) { dev.ccnc.settings.api.Demo.put("wifi.connected", ssid); h.back(); } }
                            });
                        }
                    });
                    else q.button("Forget", new Fn.Act() {
                        @Override public void run(Host h) { dev.ccnc.settings.api.Demo.put("wifi.connected", ""); h.back(); }
                    });
                }
            });
        }
    }

    private static void wifiPage(final Page p, final Context x) {
        if (dev.ccnc.settings.api.Demo.on()) { demoWifi(p); return; }
        final WifiManager w = wifi(x);
        if (w == null) { p.note("Not available on this unit."); return; }
        final boolean busy = projectionAp(x);
        if (busy) p.note("Wi-Fi is in use by wireless phone projection. Disconnect the phone to change Wi-Fi.");
        p.toggle("Wi-Fi", new Fn.Bool() {
            @Override public boolean get() { return w.isWifiEnabled(); }
        }, new Fn.SetBool() {
            @Override public void set(boolean on) {
                if (projectionAp(x)) return; // nothing is changed while projection uses the radio
                try { w.setWifiEnabled(on); } catch (Throwable t) { Log.w(TAG, "datanet: setWifiEnabled: " + t); }
                later(p.host(), 1500); later(p.host(), 5000);
            }
        }).enabled(!busy);
        if (busy || !w.isWifiEnabled()) return;
        String cur = null;
        try {
            WifiInfo info = w.getConnectionInfo();
            if (info != null && info.getNetworkId() != -1) cur = unquote(info.getSSID());
        } catch (Throwable ignored) { }
        final Map<String, ScanResult> nets = new LinkedHashMap<String, ScanResult>();
        try {
            List<ScanResult> sr = w.getScanResults();
            if (sr != null) for (ScanResult r : sr) {
                if (r.SSID == null || r.SSID.length() == 0) continue;
                ScanResult old = nets.get(r.SSID);
                if (old == null || r.level > old.level) nets.put(r.SSID, r);
            }
        } catch (Throwable t) {
            Log.w(TAG, "datanet: scan results unreadable: " + t);
        }
        final Map<String, WifiConfiguration> saved = new LinkedHashMap<String, WifiConfiguration>();
        try {
            List<WifiConfiguration> cfgs = w.getConfiguredNetworks();
            if (cfgs != null) for (WifiConfiguration cf : cfgs) if (cf.SSID != null) saved.put(unquote(cf.SSID), cf);
        } catch (Throwable ignored) { }
        List<String> order = new ArrayList<String>(nets.keySet());
        if (cur != null && !order.contains(cur)) order.add(cur);
        final String connected = cur;
        Collections.sort(order, new Comparator<String>() {
            @Override public int compare(String a, String b) {
                if (a.equals(connected)) return -1;
                if (b.equals(connected)) return 1;
                ScanResult ra = nets.get(a), rb = nets.get(b);
                return (rb == null ? -200 : rb.level) - (ra == null ? -200 : ra.level);
            }
        });
        p.button("Search for Networks", new Fn.Act() {
            @Override public void run(Host h) { try { w.startScan(); } catch (Throwable ignored) { } later(h, 3000); }
        });
        for (final String ssid : order) {
            final boolean on = ssid.equals(connected);
            final ScanResult r = nets.get(ssid);
            final WifiConfiguration cf = saved.get(ssid);
            final boolean secure = r != null && r.capabilities != null && (r.capabilities.contains("WPA") || r.capabilities.contains("WEP") || r.capabilities.contains("EAP"));
            p.drill(ssid, new Fn.Str() {
                @Override public String get() { return on ? "Connected" : cf != null ? "Saved" : secure ? "Secured" : "Open"; }
            }, new Fn.Build() {
                @Override public void build(Page q) { networkPage(q, x, w, ssid, r, cf, on, secure); }
            });
        }
    }

    private static void networkPage(Page q, final Context x, final WifiManager w, final String ssid, final ScanResult r,
                                    final WifiConfiguration cf, boolean on, final boolean secure) {
        q.info(ssid, on ? "Connected" : cf != null ? "Saved" : secure ? "Secured" : "Open");
        if (r != null) q.info("Signal Strength", (WifiManager.calculateSignalLevel(r.level, 5)) + " / 4");
        if (!on) q.button("Connect", new Fn.Act() {
            @Override public void run(final Host h) {
                if (projectionAp(x)) return;
                if (cf != null) { join(w, cf.networkId, h); return; }
                if (!secure) { join(w, add(w, ssid, null), h); return; }
                if (r.capabilities.contains("EAP") || r.capabilities.contains("WEP") && !r.capabilities.contains("WPA")) {
                    Toast.makeText(h.activity(), "This network type is not supported", Toast.LENGTH_SHORT).show();
                    return;
                }
                Ask.text(h.activity(), ssid, "", true, 63, new Ask.Text() {
                    @Override public void onText(String pw) { if (pw.length() >= 8) join(w, add(w, ssid, pw), h); }
                });
            }
        });
        if (cf != null) q.button("Forget", new Fn.Act() {
            @Override public void run(final Host h) {
                Ask.confirm(h.activity(), ssid, "Forget This Network?", "Forget", new Runnable() {
                    @Override public void run() {
                        try { w.removeNetwork(cf.networkId); w.saveConfiguration(); } catch (Throwable ignored) { }
                        h.back();
                    }
                });
            }
        });
    }

    private static int add(WifiManager w, String ssid, String pw) {
        try {
            WifiConfiguration c = new WifiConfiguration();
            c.SSID = "\"" + ssid + "\"";
            if (pw == null) c.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
            else c.preSharedKey = "\"" + pw + "\"";
            return w.addNetwork(c);
        } catch (Throwable t) { Log.w(TAG, "datanet: addNetwork: " + t); return -1; }
    }

    private static void join(WifiManager w, int id, Host h) {
        if (id < 0) { Toast.makeText(h.activity(), "--", Toast.LENGTH_SHORT).show(); return; }
        try { w.enableNetwork(id, true); w.saveConfiguration(); } catch (Throwable t) { Log.w(TAG, "datanet: enableNetwork: " + t); }
        h.back();
        later(h, 3000); later(h, 8000);
    }

    // ---- Wi-Fi Hotspot ---------------------------------------------------------------------------------

    private static WifiConfiguration apConfig(Context x) {
        try {
            return (WifiConfiguration) WifiManager.class.getMethod("getWifiApConfiguration").invoke(wifi(x));
        } catch (Throwable t) {
            Log.w(TAG, "datanet: hotspot config unreadable: " + t);
            return null;
        }
    }

    /** Car framework WifiConfiguration has public boolean is24GHzBand (stock shows "2.4 GHz" / "5 GHz"). */
    private static String band(WifiConfiguration cfg) {
        try {
            return WifiConfiguration.class.getField("is24GHzBand").getBoolean(cfg) ? "2.4 GHz" : "5 GHz";
        } catch (Throwable t) {
            return null;
        }
    }

    /** Stock hotspot list (car): Enable Hotspot, SSID, Password, Frequency Bands. */
    private static void hotspotPage(final Page p, final Context x, final Scontrol sc) {
        sc.attach(p.host());
        final boolean viaStock = sc.has(K_HOTSPOT);
        p.toggle(p.word(8005, "Enable Hotspot"), new Fn.Bool() {
            @Override public boolean get() { return hotspotOn(x); }
        }, new Fn.SetBool() {
            @Override public void set(boolean on) {
                if (!viaStock) return; // only through the stock HotspotService (its projection checks); never directly
                sc.set(K_HOTSPOT, on ? 1 : 0);
                later(p.host(), 2000); later(p.host(), 6000);
            }
        }).enabled(viaStock);
        if (!viaStock) p.note("The hotspot cannot be switched from ccNC Setup right now.");
        if (dev.ccnc.settings.api.Demo.on()) { // emulator demo: invented access point
            p.info("SSID", "Demo Vehicle Hotspot");
            p.info("Password", "************");
            p.info("Frequency Bands", "5 GHz");
            p.note("The hotspot name, password and band are shared with wireless phone projection and are shown read only.");
            return;
        }
        WifiConfiguration cfg = apConfig(x);
        if (cfg != null) {
            p.info("SSID", unquote(cfg.SSID));
            // each character shown as '*', as the stock list does
            p.info("Password", cfg.preSharedKey == null ? null : cfg.preSharedKey.replaceAll(".", "*"));
            String b = band(cfg);
            if (b != null) p.info("Frequency Bands", b);
            p.note("The hotspot name, password and band are shared with wireless phone projection and are shown read only.");
        }
    }
}
