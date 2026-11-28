package dev.ccnc.common;

import android.app.Activity;
import android.content.Intent;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Window;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * The car's MEDIA key on every ccNC screen. While a source plays, the unit's key dispatcher hands the key
 * (KEYCODE_SHORTCUT_MEDIA, 251) first to the app in front and only if that app leaves it to the current source's
 * own service, which opens its stock screen (Bluetooth Audio's MusicActivityNew, and so on). So each ccNC screen
 * takes the key itself: ccNC Media opens, or, from ccNC Media, Home (as the stock key closes the source screen).
 * The stock source-list broadcast (MEDIAGATE_SHORTCUT) is still handled by ccNC Home's HardKeyReceiver.
 * Over CarPlay, Android Auto or stock screens the key is left alone.
 */
final class MediaKey {
    private MediaKey() {}

    static final String TAG = "ccncKeys";
    static final int KEYCODE_SHORTCUT_MEDIA = 251;
    static final String MEDIA_PKG = "dev.ccnc.media";

    /** Wraps the activity window's callback so the MEDIA key is seen before the views. */
    static void install(final Activity a) {
        try {
            final Window w = a.getWindow();
            final Window.Callback orig = w.getCallback();
            if (orig == null || Proxy.isProxyClass(orig.getClass())) return;
            Window.Callback wrap = (Window.Callback) Proxy.newProxyInstance(Window.Callback.class.getClassLoader(),
                    new Class<?>[]{Window.Callback.class}, new InvocationHandler() {
                        @Override public Object invoke(Object p, Method m, Object[] args) throws Throwable {
                            if ("dispatchKeyEvent".equals(m.getName()) && args != null && args.length == 1
                                    && args[0] instanceof KeyEvent && ((KeyEvent) args[0]).getKeyCode() == KEYCODE_SHORTCUT_MEDIA) {
                                if (((KeyEvent) args[0]).getAction() == KeyEvent.ACTION_UP) pressed(a);
                                return true;   // taken: the source's own service doesn't open its stock screen
                            }
                            try { return m.invoke(orig, args); }
                            catch (java.lang.reflect.InvocationTargetException e) { throw e.getCause(); }
                        }
                    });
            w.setCallback(wrap);
        } catch (Throwable t) {
            Log.w(TAG, "MEDIA key hook", t);
        }
    }

    private static void pressed(Activity a) {
        if (MEDIA_PKG.equals(a.getPackageName())) {
            Log.i(TAG, "MEDIA key on ccNC Media -> Home");
            Chrome.goHome(a);
            return;
        }
        Intent i = a.getPackageManager().getLaunchIntentForPackage(MEDIA_PKG);
        if (i == null) { Log.i(TAG, "MEDIA key: no ccNC Media, left to the car"); return; }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { a.startActivity(i); Log.i(TAG, "MEDIA key on " + a.getLocalClassName() + " -> ccNC Media"); }
        catch (Throwable t) { Log.w(TAG, "MEDIA key", t); }
    }
}
