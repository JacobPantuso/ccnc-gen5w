package dev.ccnc.home;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

/**
 * When the head unit joins Wi-Fi, checks the ccNC Theme Store (at most every 6 h) and, if there are new themes or
 * updates it hasn't mentioned yet, tells the owner where to find them. It never installs anything by itself:
 * the owner installs from Settings > Screen Theme > Theme Store.
 */
public final class StoreCheckReceiver extends BroadcastReceiver {
    private static final long EVERY = 6 * 3600 * 1000L;

    @Override public void onReceive(final Context context, Intent intent) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo n = cm == null ? null : cm.getActiveNetworkInfo();
        if (n == null || !n.isConnected() || n.getType() != ConnectivityManager.TYPE_WIFI) return;
        final Context c = context.getApplicationContext();
        long last = ThemeStore.prefs(c).getLong("checked", 0);
        if (System.currentTimeMillis() - last < EVERY) return;
        ThemeStore.prefs(c).edit().putLong("checked", System.currentTimeMillis()).apply();
        final PendingResult pr = goAsync();
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    if (!ThemeStore.refresh(c)) return;
                    int[] k = ThemeStore.counts(c);
                    String key = k[0] + "/" + k[1] + "/" + ThemeStore.items(c).size();
                    if (k[0] + k[1] == 0 || key.equals(ThemeStore.prefs(c).getString("told", ""))) return;
                    ThemeStore.prefs(c).edit().putString("told", key).apply();
                    final String msg = "ccNC Theme Store: " + (k[0] > 0 ? k[0] + " new theme" + (k[0] == 1 ? "" : "s") : "")
                            + (k[0] > 0 && k[1] > 0 ? ", " : "") + (k[1] > 0 ? k[1] + " update" + (k[1] == 1 ? "" : "s") : "")
                            + ". Settings > Screen Theme > Theme Store";
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override public void run() { Toast.makeText(c, msg, Toast.LENGTH_LONG).show(); }
                    });
                } finally {
                    pr.finish();
                }
            }
        }, "ccncStoreCheck").start();
    }
}
