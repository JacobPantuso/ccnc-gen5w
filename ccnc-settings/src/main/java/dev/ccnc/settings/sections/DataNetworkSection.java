package dev.ccnc.settings.sections;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.ccnc.settings.api.Category;
import dev.ccnc.settings.api.Fn;
import dev.ccnc.settings.api.Host;
import dev.ccnc.settings.api.Page;
import dev.ccnc.settings.api.Section;

/**
 * Data/Network (stock HKMC_WifiSettings NetworkConnectionSettings, a TabActivity). Stock tabs, in order:
 *  1. Bluelink/Kia Connect/... (Vrm or vitlite settings activity chosen by the stock static checks common.e.isGenesis/
 *     O/U/K/M/C, called here through the stock class loader; label = the stock string the stock code picks).
 *  2. Wi-Fi (only if PackageManager.hasSystemFeature("android.hardware.wifi"), stock e.m(ctx)).
 *  3. Wi-Fi Hotspot (only if hasSystemFeature("com.hkmc.software.wifihotspot"), stock e.n(ctx)).
 * Reads: WifiManager (isWifiEnabled, scan results, connection info; hidden getWifiApState/getWifiApMode/
 * getWifiApConfiguration by reflection, stock e.g(ctx): enabled = state 13 && mode 1).
 * Writes are NOT done here: Wi-Fi client and hotspot share the radio with the wireless phone projection
 * (CarPlay mod), and the stock screens run their own projection checks/popups first, so every tap opens the
 * stock Wi-Fi / Hotspot screen. Not tested on car.
 */
public final class DataNetworkSection extends Section {
    @Override public String classId() { return "com.hkmc.wifisettings.settings.ui.NetworkConnectionSettings"; }
    @Override public int treeNode() { return 1009; }
    @Override public String icon() { return "set_ic_menu_datanetwork"; }

    private static final String TAG = "ccncSetup";
    private static final String PKG = "com.hkmc.wifisettings";

    @Override public void build(Category c) {
        final Context x = c.context();
        bluelinkTab(c, x);
        PackageManager pm = x.getPackageManager();
        if (pm.hasSystemFeature("android.hardware.wifi")) {
            c.tab("Wi-Fi", new Fn.Build() {
                @Override public void build(Page p) { wifiPage(p, x); }
            });
        }
        if (pm.hasSystemFeature("com.hkmc.software.wifihotspot")) {
            c.tab(c.word(8004, "Wi-Fi Hotspot"), new Fn.Build() {
                @Override public void build(Page p) { hotspotPage(p, x); }
            });
        }
    }

    @Override public Intent stockIntent(Context c) { return component(PKG, ".settings.ui.NetworkConnectionSettings"); }

    // ---- Bluelink tab: same activity + label as the stock first tab ----
    private static void bluelinkTab(Category c, Context x) {
        try {
            Context sc = x.createPackageContext(PKG, Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
            Class<?> e = sc.getClassLoader().loadClass("com.hkmc.wifisettings.common.e");
            boolean genesis = st(e, "isGenesis"), o = st(e, "O"), u = st(e, "U");
            int label = genesis ? 0x7f090081 : o ? 0x7f090080 : u ? 0x7f09007f : 0x7f09007e;
            String pkg = null, cls = null;
            boolean k = st(e, "K"), m = st(e, "M");
            if (o || (k && m)) {
                pkg = "com.hkmc.telematics.apps.Vrm.main";
                cls = st(e, "C") ? "com.hkmc.telematics.apps.Vrm.settings.ui.IN.SettingsMenuIN"
                        : "com.hkmc.telematics.apps.Vrm.settings.ui.BluelinkUvoSettingsMenu";
            } else if (k) {
                pkg = "com.hkmc.vitlite.app";
                cls = "com.hkmc.vitlite.app.settings.SettingsActivity";
            }
            if (cls == null) return;
            final Intent menu = new Intent().setClassName(pkg, cls).putExtra("root", "settings")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (x.getPackageManager().resolveActivity(menu, 0) == null) return;
            String t = sc.getResources().getString(label);
            if ("Bluelink".equalsIgnoreCase(t)) t = c.word(8011, t);
            final String vrm = pkg;
            // Rows of the stock Bluelink menu as shown on this car (Activate Service, Modem Information). Both are
            // telematics service screens: they open the stock activity (Modem Information directly when startable).
            c.tab(t, new Fn.Build() {
                @Override public void build(Page p) {
                    p.value("Activate Service", null, startAct(x, null, menu));
                    p.value("Modem Information", null, startAct(x,
                            new Intent().setClassName(vrm, "com.hkmc.telematics.apps.Vrm.settings.ui.ModemInformation")
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), menu));
                }
            });
        } catch (Throwable t) {
            Log.w(TAG, "datanet: bluelink tab unavailable: " + t);
        }
    }

    /** Starts first (if given), falling back to the stock menu activity. */
    private static Fn.Act startAct(final Context x, final Intent first, final Intent fallback) {
        return new Fn.Act() {
            @Override public void run(Host h) {
                if (first != null) try { x.startActivity(first); return; } catch (Throwable t) {
                    Log.w(TAG, "datanet: " + first.getComponent() + " not startable: " + t);
                }
                try { x.startActivity(fallback); } catch (Throwable t) { Log.w(TAG, "datanet: bluelink open failed: " + t); }
            }
        };
    }

    private static boolean st(Class<?> e, String m) {
        try { return Boolean.TRUE.equals(e.getMethod(m).invoke(null)); } catch (Throwable t) { return false; }
    }

    // ---- Wi-Fi ----
    private static Fn.SetBool open(final Context x, final String cls, final boolean hotspotExtra) {
        return new Fn.SetBool() {
            @Override public void set(boolean v) { start(x, cls, hotspotExtra); }
        };
    }

    private static Fn.Act openAct(final Context x, final String cls, final boolean hotspotExtra) {
        return new Fn.Act() {
            @Override public void run(Host h) { start(x, cls, hotspotExtra); }
        };
    }

    private static void start(Context x, String cls, boolean hotspotExtra) {
        try {
            Intent i = new Intent().setClassName(PKG, cls).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (hotspotExtra) i.putExtra("tab_Wifi_Hotspot", true);
            x.startActivity(i);
        } catch (Throwable t) {
            Log.w(TAG, "datanet: stock open failed: " + t);
        }
    }

    private static WifiManager wifi(Context x) {
        return (WifiManager) x.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
    }

    private static String unquote(String s) {
        if (s != null && s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) return s.substring(1, s.length() - 1);
        return s;
    }

    private static void wifiPage(Page p, final Context x) {
        final String client = "com.hkmc.wifisettings.WifiClientSettings";
        p.toggle("Wi-Fi", new Fn.Bool() {
            @Override public boolean get() { WifiManager w = wifi(x); return w != null && w.isWifiEnabled(); }
        }, open(x, client, false));
        WifiManager w = wifi(x);
        if (w == null || !w.isWifiEnabled()) return;
        String cur = null;
        try {
            WifiInfo info = w.getConnectionInfo();
            if (info != null && info.getNetworkId() != -1) cur = unquote(info.getSSID());
        } catch (Throwable ignored) { }
        final Map<String, Integer> nets = new LinkedHashMap<String, Integer>();
        try {
            List<ScanResult> sr = w.getScanResults();
            if (sr != null) for (ScanResult r : sr) {
                if (r.SSID == null || r.SSID.length() == 0) continue;
                Integer old = nets.get(r.SSID);
                if (old == null || r.level > old) nets.put(r.SSID, r.level);
            }
        } catch (Throwable t) {
            Log.w(TAG, "datanet: scan results unreadable: " + t);
        }
        if (cur != null && !nets.containsKey(cur)) nets.put(cur, Integer.MAX_VALUE);
        List<String> order = new ArrayList<String>(nets.keySet());
        final String connected = cur;
        Collections.sort(order, new Comparator<String>() {
            @Override public int compare(String a, String b) {
                if (a.equals(connected)) return -1;
                if (b.equals(connected)) return 1;
                return nets.get(b).compareTo(nets.get(a));
            }
        });
        for (final String ssid : order) {
            final boolean on = ssid.equals(connected);
            p.value(ssid, new Fn.Str() {
                @Override public String get() { return on ? "Connected" : null; }
            }, openAct(x, client, false));
        }
    }

    // ---- Wi-Fi Hotspot ----
    /** Stock e.g(ctx): getWifiApState() == 13 (enabled) && getWifiApMode() == 1 (hotspot, not projection). */
    private static boolean hotspotOn(Context x) {
        WifiManager w = wifi(x);
        if (w == null) return false;
        try {
            int st = ((Number) WifiManager.class.getMethod("getWifiApState").invoke(w)).intValue();
            int mode = ((Number) WifiManager.class.getMethod("getWifiApMode").invoke(w)).intValue();
            return st == 13 && mode == 1;
        } catch (Throwable t) {
            Log.w(TAG, "datanet: hotspot state unreadable: " + t);
            return false;
        }
    }

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

    /** Stock hotspot list (car): Enable Hotspot, SSID, Password (each char shown as '*'), Frequency Bands,
     *  Connected Devices, How to Use. All taps open the stock WifiHotspot screen (its sub-screens are not exported). */
    private static void hotspotPage(Page p, final Context x) {
        final String hs = "com.hkmc.wifisettings.WifiHotspot";
        p.toggle(p.word(8005, "Enable Hotspot"), new Fn.Bool() {
            @Override public boolean get() { return hotspotOn(x); }
        }, open(x, hs, true));
        WifiConfiguration cfg = apConfig(x);
        final Fn.Act stock = openAct(x, hs, true);
        if (cfg != null) {
            final String ssid = unquote(cfg.SSID);
            final String psk = cfg.preSharedKey;
            final String b = band(cfg);
            p.value("SSID", new Fn.Str() { @Override public String get() { return ssid; } }, stock);
            p.value("Password", new Fn.Str() {
                @Override public String get() { return psk == null ? null : psk.replaceAll(".", "*"); }
            }, stock);
            if (b != null) p.value("Frequency Bands", new Fn.Str() { @Override public String get() { return b; } }, stock);
        }
        p.value("Connected Devices", null, stock);
        p.value("How to Use", null, stock);
    }
}
