package dev.ccnc.settings;

import android.app.Activity;
import android.content.Context;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.util.List;

import dev.ccnc.common.Palette;
import dev.ccnc.common.Widgets;

/** Wi-Fi: the switch, the current network, and the saved networks (tap one to connect). */
public final class WifiActivity extends Activity {
    private LinearLayout col;
    private WifiManager wifi;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        col = Ui.page(this, "Wi-Fi");
        wifi = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        build();
    }

    @Override protected void onResume() { super.onResume(); if (col != null) build(); }

    private void build() {
        col.removeAllViews();
        if (wifi == null) { Ui.note(this, col, "No Wi-Fi on this unit"); return; }
        LinearLayout sw = Ui.row(this, "Wi-Fi", null, null, null);
        final Widgets.Switch s = new Widgets.Switch(this);
        s.setOnQuiet(wifi.isWifiEnabled());
        sw.addView(s, new LinearLayout.LayoutParams(Palette.px(this, 46), Palette.px(this, 26)));
        s.setOnChanged(new Widgets.Switch.OnChanged() {
            @Override public void onChanged(boolean on) { try { wifi.setWifiEnabled(on); } catch (Throwable t) { Toast.makeText(WifiActivity.this, "Couldn't change Wi-Fi", Toast.LENGTH_SHORT).show(); } }
        });
        col.addView(sw);

        WifiInfo info = null;
        try { info = wifi.getConnectionInfo(); } catch (Throwable ignored) {}
        Ui.section(this, col, "Connected network");
        if (info != null && info.getNetworkId() != -1 && info.getSSID() != null) {
            int level = WifiManager.calculateSignalLevel(info.getRssi(), 5);
            col.addView(Ui.row(this, info.getSSID().replace("\"", ""), "Signal " + level + " of 4 · " + info.getLinkSpeed() + " Mbps", "Connected", null));
        } else {
            Ui.note(this, col, wifi.isWifiEnabled() ? "Not connected" : "Wi-Fi is off");
        }
        Ui.section(this, col, "Saved networks");
        List<WifiConfiguration> nets = null;
        try { nets = wifi.getConfiguredNetworks(); } catch (Throwable ignored) {}
        if (nets == null || nets.isEmpty()) {
            Ui.note(this, col, "No saved networks");
        } else {
            for (final WifiConfiguration n : nets) {
                final String name = n.SSID == null ? "Hidden network" : n.SSID.replace("\"", "");
                col.addView(Ui.row(this, name, null, null, new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        try { wifi.enableNetwork(n.networkId, true); Toast.makeText(WifiActivity.this, "Connecting to " + name, Toast.LENGTH_SHORT).show(); }
                        catch (Throwable t) { Toast.makeText(WifiActivity.this, "Couldn't connect", Toast.LENGTH_SHORT).show(); }
                    }
                }));
            }
        }
        col.addView(Ui.row(this, "Wi-Fi Settings", null, null, new View.OnClickListener() {
            @Override public void onClick(View v) { Ui.openStock(WifiActivity.this, "com.hkmc.wifisettings", "com.hkmc.wifisettings.settings.ui.NetworkConnectionSettings", "Wi-Fi settings"); }
        }));
    }
}
