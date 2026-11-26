package dev.ccnc.navi;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * The stock engine's com.mnsoft.navi.action.DestinationInfo (sticky, sent whenever a destination is set or updated,
 * whoever set it: ccNC, voice, the phone, the stock screens). Each new destination becomes a ccNC recent, so ccNC's
 * Recent list matches what the car actually drove to. Only writes ccNC's own preferences.
 */
public final class DestinationReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        Place p = StockNavi.destinationFrom(i);
        if (p == null) {
            Log.d(StockNavi.TAG, "destination info without a position: " + StockNavi.dump(i.getExtras()));
            return;
        }
        if (Places.addRecent(c, p)) Log.i(StockNavi.TAG, "destination info -> recent: " + StockNavi.dump(i.getExtras()));
    }
}
