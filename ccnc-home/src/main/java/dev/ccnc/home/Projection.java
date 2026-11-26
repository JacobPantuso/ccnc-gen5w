package dev.ccnc.home;

import android.content.Context;
import android.util.Log;

/**
 * Which phone projection is connected, from the unit's mode manager
 * ({@code com.hkmc.mode.ModeManager.getAvailableMediaModes()}, bootclasspath, by reflection):
 * CarPlay (DIPO) is mode 0x73 and Android Auto (GPM) is 0x74 while a phone is connected.
 * Changes are announced by {@link #ACTION_MODE_AVAILABLE}.
 */
final class Projection {
    private Projection() {}

    static final String ACTION_MODE_AVAILABLE = "com.hkmc.intent.action.MEDIA_MODE_AVAILABLE";
    static final String ACTION_PP_STATUS = "com_phoneprojection_status_change";
    static final String CARPLAY_PKG = "com.daudio.app.dipo";
    static final String ANDROID_AUTO_PKG = "com.google.android.projection.sink";
    private static final int MODE_DIPO = 0x73;
    private static final int MODE_GPM = 0x74;

    /** Connection state; {@code known} is false when the mode service couldn't be read. */
    static final class State {
        final boolean known, carplay, androidAuto;
        State(boolean known, boolean carplay, boolean androidAuto) {
            this.known = known; this.carplay = carplay; this.androidAuto = androidAuto;
        }
        boolean shows(String pkg) {
            if (!known) return true;
            if (CARPLAY_PKG.equals(pkg)) return carplay;
            if (ANDROID_AUTO_PKG.equals(pkg)) return androidAuto;
            return true;
        }
        boolean none() { return known && !carplay && !androidAuto; }
    }

    /**
     * Logo for a projection card, from the unit's own apps: CarPlay's pro_ic_cp (in DiPO),
     * Android Auto's pro_ic_aa (in the projection sink), and the stock home's
     * hom_phone_projection_66 for the "no phone connected" card. Null: fall back to the app icon.
     */
    static android.graphics.drawable.Drawable logo(Context ctx, String key) {
        String pkg, name;
        if (key.startsWith(CARPLAY_PKG + "/")) { pkg = CARPLAY_PKG; name = "pro_ic_cp"; }
        else if (key.startsWith(ANDROID_AUTO_PKG + "/")) { pkg = ANDROID_AUTO_PKG; name = "pro_ic_aa"; }
        else if (key.equals(Curated.PROJECTION_SETTINGS)) { pkg = AppRepo.STOCK_LAUNCHER; name = "hom_phone_projection_66"; }
        else return null;
        try {
            android.content.res.Resources r = ctx.createPackageContext(pkg, 0).getResources();
            int id = r.getIdentifier(name, "drawable", pkg);
            return id == 0 ? null : r.getDrawable(id);
        } catch (Throwable t) {
            Log.w(WeatherClient.TAG, "projection: no logo " + pkg + "/" + name, t);
            return null;
        }
    }

    static State read(Context ctx) {
        try {
            Class<?> c = Class.forName("com.hkmc.mode.ModeManager");
            Object mm = c.getConstructor(Context.class).newInstance(ctx);
            int[] modes = (int[]) c.getMethod("getAvailableMediaModes").invoke(mm);
            boolean cp = false, aa = false;
            StringBuilder sb = new StringBuilder();
            if (modes != null) for (int m : modes) {
                sb.append(Integer.toHexString(m)).append(' ');
                if (m == MODE_DIPO) cp = true;
                if (m == MODE_GPM) aa = true;
            }
            Log.i(WeatherClient.TAG, "projection: modes=[" + sb.toString().trim() + "] carplay=" + cp + " androidAuto=" + aa);
            return new State(true, cp, aa);
        } catch (Throwable t) {
            Log.w(WeatherClient.TAG, "projection: mode manager unavailable, showing both", t);
            return new State(false, true, true);
        }
    }

    /**
     * Opens Android Auto. Its screen (projection.sink/.ui.MainActivity) isn't exported: only the system launcher may
     * start it. Apps go through the mode manager instead, which sends Android Auto its GPM_SHORTCUT the way the MODE
     * key does; failing that, the same GPM_SHORTCUT straight to Android Auto's exported service. False if neither worked.
     */
    static boolean openAndroidAuto(Context ctx) {
        try {
            Class<?> c = Class.forName("com.hkmc.mode.ModeManager");
            Object mm = c.getConstructor(Context.class).newInstance(ctx);
            Object ok = c.getMethod("changeMode", Integer.class).invoke(mm, Integer.valueOf(MODE_GPM));
            Log.i(WeatherClient.TAG, "projection: changeMode(GPM) = " + ok);
            if (Boolean.TRUE.equals(ok)) return true;
        } catch (Throwable t) {
            Log.w(WeatherClient.TAG, "projection: changeMode(GPM) failed", t);
        }
        try {
            android.content.Intent i = new android.content.Intent("com.hkmc.intent.action.GPM_SHORTCUT")
                    .setPackage(ANDROID_AUTO_PKG).putExtra("com.hkmc.mode.EXTRA_SHORTCUT_REASON", 3);
            boolean ok = ctx.startService(i) != null;
            Log.i(WeatherClient.TAG, "projection: GPM_SHORTCUT to the service = " + ok);
            return ok;
        } catch (Throwable t) {
            Log.w(WeatherClient.TAG, "projection: GPM_SHORTCUT failed", t);
            return false;
        }
    }
}
