package dev.ccnc.settings;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;

import java.util.Set;

import dev.ccnc.common.Widgets;

/** Bluetooth: the switch and the paired devices. Pairing and connecting stay in the stock screen. */
public final class BluetoothActivity extends Activity {
    private LinearLayout col;
    private BluetoothAdapter bt;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        col = Ui.page(this, "Bluetooth");
        try { bt = BluetoothAdapter.getDefaultAdapter(); } catch (Throwable ignored) {}
        build();
    }

    @Override protected void onResume() { super.onResume(); if (col != null) build(); }

    private void build() {
        col.removeAllViews();
        if (bt == null) { Ui.note(this, col, "No Bluetooth adapter on this unit"); return; }
        LinearLayout sw = Ui.row(this, "Bluetooth", null, null, null);
        final Widgets.Switch s = new Widgets.Switch(this);
        s.setOnQuiet(bt.isEnabled());
        sw.addView(s, new LinearLayout.LayoutParams(dev.ccnc.common.Palette.px(this, 46), dev.ccnc.common.Palette.px(this, 26)));
        s.setOnChanged(new Widgets.Switch.OnChanged() {
            @Override public void onChanged(boolean on) { try { if (on) bt.enable(); else bt.disable(); } catch (Throwable t) { Ui.note(BluetoothActivity.this, col, "Couldn't change Bluetooth"); } }
        });
        col.addView(sw);
        Ui.section(this, col, "Paired devices");
        Set<BluetoothDevice> bonded = null;
        try { if (bt.isEnabled()) bonded = bt.getBondedDevices(); } catch (Throwable ignored) {}
        if (bonded == null || bonded.isEmpty()) {
            Ui.note(this, col, bt.isEnabled() ? "No paired devices" : "--");
        } else {
            for (BluetoothDevice d : bonded) {
                String kind = d.getBluetoothClass() != null && d.getBluetoothClass().getMajorDeviceClass() == android.bluetooth.BluetoothClass.Device.Major.PHONE ? "Phone" : "Device";
                col.addView(Ui.row(this, d.getName() == null ? d.getAddress() : d.getName(), d.getAddress(), kind, new View.OnClickListener() {
                    @Override public void onClick(View v) { Ui.openStock(BluetoothActivity.this, "com.android.btsettings", "com.android.settings2.bluetooth.BluetoothSettings", "Bluetooth settings"); }
                }));
            }
        }
        col.addView(Ui.row(this, "Bluetooth Settings", null, null, new View.OnClickListener() {
            @Override public void onClick(View v) { Ui.openStock(BluetoothActivity.this, "com.android.btsettings", "com.android.settings2.bluetooth.BluetoothSettings", "Bluetooth settings"); }
        }));
    }
}
