package dev.ccnc.settings.sections;

import android.content.Context;
import android.util.Base64;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;

/**
 * Loads a tiny subclass of com.hkmc.setup.VideoSetupListener (automotive.jar is not on our compile classpath) so
 * DisplaySection can receive VideoSetupManager.onCurrentBrightness, the value the stock Illumination bar shows.
 * Source of the embedded dex (d8 --min-api 19):
 *   public class DisplayBrightListener extends com.hkmc.setup.VideoSetupListener {
 *       public static volatile int last = -1; public static volatile Runnable cb;
 *       @Override public void onCurrentBrightness(int b) { last = b; Runnable r = cb; if (r != null) r.run(); } }
 */
final class DisplaySectionDex {
    private static final String DEX = "ZGV4CjAzNQB0noaHDwdeVXD55Ox4IkzuDTiUBw9X/M68AwAAcAAAAHhWNBIAAAAAAAAAABwDAAAOAAAAcAAAAAUAAACoAAAAAgAAALwAAAACAAAA1AAAAAUAAADkAAAAAQAAAAwBAACQAgAALAEAAJYBAACgAQAAqAEAAMQBAADHAQAA7AEAACACAAA2AgAAOQIAAD0CAABBAgAARwIAAFwCAABhAgAAAwAAAAQAAAAFAAAABgAAAAcAAAAHAAAABAAAAAAAAAAIAAAABAAAAJABAAACAAMACQAAAAIAAAAKAAAAAQAAAAEAAAACAAAAAAAAAAIAAAABAAAAAgABAAsAAAADAAAADAAAAAIAAAABAAAAAQAAAAAAAAACAAAAAAAAAAEDAAAAAAAAAQAAAAAAAACAAQAABAAAABLwZwABAA4AAQABAAEAAACEAQAABAAAAHAQAAAAAA4AAgACAAEAAACIAQAACgAAAGcBAQBiAQAAOAEFAHIQBAABAA4ABAAOAAMADgAGAQAOAAAAAAEAAAAAAAg8Y2xpbml0PgAGPGluaXQ+ABpEaXNwbGF5QnJpZ2h0TGlzdGVuZXIuamF2YQABSQAjTGNvbS9oa21jL3NldHVwL1ZpZGVvU2V0dXBMaXN0ZW5lcjsAMkxkZXYvY2NuYy9zZXR0aW5ncy9zZWN0aW9ucy9EaXNwbGF5QnJpZ2h0TGlzdGVuZXI7ABRMamF2YS9sYW5nL1J1bm5hYmxlOwABVgACVkkAAmNiAARsYXN0ABNvbkN1cnJlbnRCcmlnaHRuZXNzAANydW4AnQF+fkQ4eyJiYWNrZW5kIjoiZGV4IiwiY29tcGlsYXRpb24tbW9kZSI6ImRlYnVnIiwiaGFzLWNoZWNrc3VtcyI6ZmFsc2UsIm1pbi1hcGkiOjE5LCJzaGEtMSI6ImE3YWQxOGE3MDQ2MGI3OTlkMDQ4MmU0OTdjMTA5YTc1YmY3ZjkxZGUiLCJ2ZXJzaW9uIjoiOC4xMC45LWRldiJ9AAIAAgEASQFJAYiABKwCAYGABMQCAwHcAgAAAA0AAAAAAAAAAQAAAAAAAAABAAAADgAAAHAAAAACAAAABQAAAKgAAAADAAAAAgAAALwAAAAEAAAAAgAAANQAAAAFAAAABQAAAOQAAAAGAAAAAQAAAAwBAAABIAAAAwAAACwBAAADIAAAAwAAAIABAAABEAAAAQAAAJABAAACIAAADgAAAJYBAAAAIAAAAQAAAAEDAAAAEAAAAQAAABwDAAA=";
    private static Class<?> cls;

    private DisplaySectionDex() { }

    /** The listener class, or null. */
    static synchronized Class<?> listener(Context x) {
        if (cls != null) return cls;
        try {
            File dir = x.getDir("ccnc_dex", Context.MODE_PRIVATE);
            File f = new File(dir, "display_listener.dex");
            FileOutputStream o = new FileOutputStream(f);
            try { o.write(Base64.decode(DEX, Base64.DEFAULT)); } finally { o.close(); }
            dalvik.system.DexClassLoader l = new dalvik.system.DexClassLoader(f.getAbsolutePath(),
                    x.getDir("ccnc_odex", Context.MODE_PRIVATE).getAbsolutePath(), null, DisplaySectionDex.class.getClassLoader());
            cls = l.loadClass("dev.ccnc.settings.sections.DisplayBrightListener");
        } catch (Throwable t) {
            Log.w("ccnc.DisplaySection", "listener dex not loadable: " + t);
        }
        return cls;
    }

    static int last() {
        try { Field f = cls.getField("last"); return f.getInt(null); } catch (Throwable t) { return -1; }
    }

    static void callback(Runnable r) {
        try { cls.getField("cb").set(null, r); } catch (Throwable ignored) { }
    }
}
