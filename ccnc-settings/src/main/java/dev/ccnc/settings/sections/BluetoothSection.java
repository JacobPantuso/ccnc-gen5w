package dev.ccnc.settings.sections;

import android.content.Context;
import android.content.Intent;

import dev.ccnc.settings.api.Category;
import dev.ccnc.settings.api.Fn;
import dev.ccnc.settings.api.Page;
import dev.ccnc.settings.api.Section;

/**
 * Bluetooth (stock classId com.android.settings.bt, shown by the stock hub when Utils.usedBluetoothSetting()).
 * Tab "Bluetooth Connections" (stock wording; ccNC 1249 "Device Connections" would repeat the stock entry name): paired devices with their connected
 * profiles (BluetoothAdapter bonded devices + profile proxies, read only). Connect / disconnect / delete, Auto
 * Connection Priority, Voice Prompts, Privacy Mode, messages, System Info stay in the stock entry (tap opens it).
 */
public final class BluetoothSection extends Section {
    @Override public String classId() { return "com.android.settings.bt"; }
    @Override public int treeNode() { return -1; }
    @Override public String icon() { return "set_ic_menu_bt"; }

    @Override public void build(Category c) {
        c.tab("Bluetooth Connections", new Fn.Build() {
            @Override public void build(Page p) { BluetoothSectionDevices.addRows(p); }
        });
    }

    @Override public Intent stockIntent(Context c) { return component("com.android.btsettings", "com.android.settings2.bluetooth.BluetoothSettings"); }
}
