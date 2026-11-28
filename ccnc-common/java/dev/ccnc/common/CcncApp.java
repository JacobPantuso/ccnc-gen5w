package dev.ccnc.common;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

import java.util.WeakHashMap;

/**
 * Application class of every ccNC app: installs the crash catcher before any activity loads, and
 * keeps the shared ccNC theme current: an activity that comes back to the front re-reads the theme
 * from ccNC Home and is rebuilt when it changed while the app was in the background.
 */
public final class CcncApp extends Application {
    /** Palette version each live activity was last drawn with. */
    private final WeakHashMap<Activity, Integer> drawn = new WeakHashMap<Activity, Integer>();

    @Override public void onCreate() {
        super.onCreate();
        CrashLog.install(this);
        // The car's MEDIA key on every ccNC screen (MediaKey), Home included.
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override public void onActivityCreated(Activity a, Bundle b) { MediaKey.install(a); }
            @Override public void onActivityResumed(Activity a) {}
            @Override public void onActivityStarted(Activity a) {}
            @Override public void onActivityPaused(Activity a) {}
            @Override public void onActivityStopped(Activity a) {}
            @Override public void onActivitySaveInstanceState(Activity a, Bundle b) {}
            @Override public void onActivityDestroyed(Activity a) {}
        });
        if (getPackageName().equals("dev.ccnc.home")) {
            NowPlayingArt.collect(this);   // the latest cover per source, for screens that open later
            return;                        // Home owns the theme and refreshes itself
        }
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override public void onActivityCreated(Activity a, Bundle b) {}
            @Override public void onActivityResumed(Activity a) {
                Themes.apply(a);
                Integer v = drawn.put(a, Palette.VERSION);
                if (v != null && v != Palette.VERSION) a.recreate();
            }
            @Override public void onActivityStarted(Activity a) {}
            @Override public void onActivityPaused(Activity a) {}
            @Override public void onActivityStopped(Activity a) {}
            @Override public void onActivitySaveInstanceState(Activity a, Bundle b) {}
            @Override public void onActivityDestroyed(Activity a) { drawn.remove(a); }
        });
    }
}
