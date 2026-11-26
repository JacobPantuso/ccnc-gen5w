package dev.ccnc.settings;

import android.content.Context;
import android.content.ContextWrapper;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * The Gen5W Setup app's own top-level menu, read at runtime from the stock app
 * (com.android.settings, Settings_W.apk): SettingMenu.getList() builds exactly the categories the
 * stock Setup shows on this car (its feature checks: EV, theme, data network, Bluelink/UVO, ...), in
 * its order, with its wording (the stock app's own string resources). Only the list is read: every
 * category opens a ccNC page; the stock screens themselves are never started.
 */
final class StockMenu {

    static final String TAG = "ccncSetup";
    static final String STOCK = "com.android.settings";

    static final class Item {
        final String classId, label;
        final int node;          // ccNC tree node for this category, or -1
        Item(String classId, String label, int node) {
            this.classId = classId; this.label = label; this.node = node;
        }
    }

    /** classId -> {ccNC tree node, name of the stock opener (kept for reference; never called)}. */
    private static final Object[][] MAP = {
            {"com.android.vehiclesettings.VehicleSetting", 1013, "startIntentVehicle"},
            {"com.android.settings.ev", -1, "startIntentEvSetting"},
            {"com.hkmc.intent.action.ACTION_NAVI_SETUP", -1, "startIntentNavi"},
            {"com.android.SoundSetting", 1002, "startIntentSound"},
            {"com.android.settings.bt", -1, "startIntentBtSetting"},
            {"com.daudio.app.phoneprojection", 1008, null},
            {"com.hkmc.system.app.userprofile.UserProfileMainActivity", 1007, "startIntentUserProfile"},
            {"com.android.settings.voice", 1010, "startIntentVoice"},
            {"com.android.ThemeSettings", 1005, "startIntentTheme"},
            {"com.android.DisplaySettingsModeCheck", 1003, "startIntentDisplay"},
            {"com.android.ButtonSettings", 1006, "startIntentButtonSetting"},
            {"com.hkmc.wifisettings.settings.ui.NetworkConnectionSettings", 1009, "startIntentDataNetworkSetting"},
            {"com.hkmc.telematics.apps.Vrm.main.bluelink", 1017, "startIntentBlueLink"},
            {"com.android.GeneralSettings", 1004, "startIntentGeneralSettings"},
    };

    private static Class<?> menu;

    /** The stock categories, or null when the stock Setup app can't be read (then the caller keeps its own list). */
    static List<Item> load(Context c) {
        if (dev.ccnc.settings.api.Demo.on()) { // emulator demo: the stock hub's categories for an EV, in the stock order
            String[] labels = {"Vehicle", "EV", "Navigation", "Sound", "Bluetooth", "Device Connections", "User Profile",
                    "Voice Recognition", "Theme/Layout", "Display", "Button", "Data/Network", "Bluelink", "General"};
            List<Item> out = new ArrayList<Item>();
            for (int i = 0; i < MAP.length && i < labels.length; i++) out.add(new Item((String) MAP[i][0], labels[i], (Integer) MAP[i][1]));
            return out;
        }
        try {
            Context sc = c.createPackageContext(STOCK, Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
            ClassLoader cl = sc.getClassLoader();
            // The stock menu asks SettingsApp.getAppContext() for its context: give it one, as its Application would.
            Class<?> app = cl.loadClass("com.android.settings.SettingsApp");
            Field inst = app.getDeclaredField("mInstance");
            inst.setAccessible(true);
            if (inst.get(null) == null) {
                Object a = app.newInstance();
                Method attach = ContextWrapper.class.getDeclaredMethod("attachBaseContext", Context.class);
                attach.setAccessible(true);
                // A foreign package context has no application context of its own (HEVManager and others
                // call getApplicationContext() and got null): hand it a base that answers with itself.
                final Context base = sc;
                attach.invoke(a, new ContextWrapper(base) {
                    @Override public Context getApplicationContext() { return this; }
                });
                inst.set(null, a);
            }
            menu = cl.loadClass("com.android.settings.SettingMenu");
            List<?> list = (List<?>) menu.getMethod("getList").invoke(null);
            List<Item> out = new ArrayList<Item>();
            for (Object it : list) {
                String cls = (String) it.getClass().getField("mClassId").get(it);
                int id = it.getClass().getField("mItemId").getInt(it);
                if (cls == null || id == 0) continue;
                String label = sc.getResources().getString(id);
                int node = -1;
                for (Object[] m : MAP) if (m[0].equals(cls)) node = (Integer) m[1];
                out.add(new Item(cls, label, node));
            }
            Log.i(TAG, "stock Setup menu: " + out.size() + " categories");
            return out.isEmpty() ? null : out;
        } catch (Throwable t) {
            Log.w(TAG, "stock Setup menu not readable", t);
            return null;
        }
    }
}
