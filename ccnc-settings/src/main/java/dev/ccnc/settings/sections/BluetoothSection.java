package dev.ccnc.settings.sections;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;

import dev.ccnc.settings.api.Ask;
import dev.ccnc.settings.api.Category;
import dev.ccnc.settings.api.Fn;
import dev.ccnc.settings.api.Host;
import dev.ccnc.settings.api.Page;
import dev.ccnc.settings.api.Scontrol;
import dev.ccnc.settings.api.Section;

/**
 * Bluetooth (stock classId com.android.settings.bt, shown by the stock hub when Utils.usedBluetoothSetting()).
 *   Bluetooth Connections          paired devices: connect / disconnect / delete / add new ({@link BluetoothSectionDevices})
 *   Message Notification           the stock BtSettings smart-control key 30004 (type 2: SearchReceiver -> iO.a(bool, ctx),
 *                                  the stock write into its contacts_bt profile table); shown only when the stock app answers
 *   Bluetooth System Information   Vehicle Name = BluetoothAdapter.getName() / setName() (stock gG; BLUETOOTH_ADMIN)
 *   Privacy Mode                   BLOCKED: stored in the stock provider content://com.android.contacts_bt (not readable
 *                                  by a normal app) and has no smart-control key. Note only.
 * Not ported: Auto Connection Priority (HfDeviceBTCMManager.IbtcmSetAutoConnectionPriorityOrder takes an order string
 * whose format is not decoded), Bluetooth Voice Prompts and Predefined Messages (same private provider).
 * Nothing here opens the stock Bluetooth screen.
 */
public final class BluetoothSection extends Section {
    @Override public String classId() { return "com.android.settings.bt"; }
    @Override public int treeNode() { return -1; }
    @Override public String icon() { return "set_ic_menu_bt"; }

    static final String PKG = "com.android.btsettings";
    static final int K_MESSAGE_NOTI = 30004;

    @Override public void build(Category c) {
        c.tab("Bluetooth Connections", new Fn.Build() {
            @Override public void build(Page p) { BluetoothSectionDevices.addRows(p); }
        });
        extras(c);
    }

    /** The tabs after the device list (also used by Device Connections, which holds them in ccNC's own tree). */
    static void extras(Category c) {
        final Context x = c.context();
        final Scontrol sc = Scontrol.of(x, PKG, 2);
        sc.prefetch(K_MESSAGE_NOTI);
        if (!sc.anyAnswer()) sc.await(800);
        if (sc.has(K_MESSAGE_NOTI)) c.tab(c.word(7004, "Message Notification"), new Fn.Build() {
            @Override public void build(Page p) {
                sc.attach(p.host());
                sc.check(p, K_MESSAGE_NOTI, p.word(7005, "Message Notification"));
            }
        });
        c.tab(c.word(7047, "Bluetooth System Information"), new Fn.Build() {
            @Override public void build(Page p) {
                if (dev.ccnc.settings.api.Demo.on()) { // emulator demo: the name lives in memory
                    p.value(p.word(7048, "Vehicle Name"), new Fn.Str() {
                        @Override public String get() { return dev.ccnc.settings.api.Demo.getStr("bt.name", "Demo Vehicle"); }
                    }, new Fn.Act() {
                        @Override public void run(final Host h) {
                            Ask.text(h.activity(), "Vehicle Name", dev.ccnc.settings.api.Demo.getStr("bt.name", "Demo Vehicle"), false, 32, new Ask.Text() {
                                @Override public void onText(String v) { if (v.length() > 0) { dev.ccnc.settings.api.Demo.put("bt.name", v); h.refresh(); } }
                            });
                        }
                    });
                    p.info("Bluetooth Address", "00:00:00:00:00:00");
                    return;
                }
                final BluetoothAdapter a = BluetoothAdapter.getDefaultAdapter();
                if (a == null) { p.note("Not available on this unit."); return; }
                p.value(p.word(7048, "Vehicle Name"), new Fn.Str() {
                    @Override public String get() { try { return a.getName(); } catch (Throwable t) { return null; } }
                }, new Fn.Act() {
                    @Override public void run(final Host h) {
                        String cur = null;
                        try { cur = a.getName(); } catch (Throwable ignored) { }
                        Ask.text(h.activity(), "Vehicle Name", cur, false, 32, new Ask.Text() {
                            @Override public void onText(String v) {
                                if (v.length() == 0) return;
                                try { a.setName(v); } catch (Throwable ignored) { }
                                new android.os.Handler().postDelayed(new Runnable() { @Override public void run() { h.refresh(); } }, 500);
                            }
                        });
                    }
                });
                String addr = null;
                try { addr = a.getAddress(); } catch (Throwable ignored) { }
                if (addr != null) p.info("Bluetooth Address", addr);
            }
        });
    }
}
