package dev.ccnc.settings;

import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * The Gen5W Setup app's own top-level menu, read at runtime from the stock app
 * (com.android.settings, Settings_W.apk): SettingMenu.getList() builds exactly the categories the
 * stock Setup shows on this car (its feature checks: EV, theme, data network, Bluelink/UVO, ...), in
 * its order, with its wording (the stock app's own string resources). Each category opens our ccNC
 * page where we have one, otherwise the stock screen through the stock app's own opener.
 */
final class StockMenu {

    static final String TAG = "ccncSetup";
    static final String STOCK = "com.android.settings";

    static final class Item {
        final String classId, label;
        final int node;          // ccNC tree node for this category, or -1
        final String opener;     // SettingMenu.startIntent*(Context) for the stock screen, or null
        Item(String classId, String label, int node, String opener) {
            this.classId = classId; this.label = label; this.node = node; this.opener = opener;
        }
    }

    /** classId -> {ccNC tree node, stock opener}. */
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
                int node = -1; String opener = null;
                for (Object[] m : MAP) if (m[0].equals(cls)) { node = (Integer) m[1]; opener = (String) m[2]; }
                out.add(new Item(cls, label, node, opener));
            }
            Log.i(TAG, "stock Setup menu: " + out.size() + " categories");
            return out.isEmpty() ? null : out;
        } catch (Throwable t) {
            Log.w(TAG, "stock Setup menu not readable", t);
            return null;
        }
    }

    /** Opens the stock Gen5W screen for a category. */
    static boolean openStock(Context c, Item it) {
        try {
            if ("com.daudio.app.phoneprojection".equals(it.classId)) {
                c.startActivity(new Intent().setComponent(new ComponentName("com.daudio.app.phoneprojection",
                        "com.daudio.app.phoneprojection.settings.ui.DeviceConnectionSettings")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return true;
            }
            if (menu == null || it.opener == null) return false;
            menu.getMethod(it.opener, Context.class).invoke(null, c);
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "stock screen for " + it.classId + " didn't open", t);
            return false;
        }
    }
}
