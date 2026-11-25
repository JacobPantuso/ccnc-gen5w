package dev.ccnc.settings;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.LinearLayout;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** General: language, region, clock and date as the unit has them now, plus software info. Changes open the stock screen. */
public final class GeneralActivity extends Activity {

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout col = Ui.page(this, "General");
        final View.OnClickListener stock = new View.OnClickListener() {
            @Override public void onClick(View v) { Ui.openStock(GeneralActivity.this, "com.android.generalsettings", "com.android.generalsettings.GeneralSettings", "General settings"); }
        };
        Locale l = Locale.getDefault();
        Date now = new Date();
        Ui.section(this, col, "Language and region");
        col.addView(Ui.row(this, "Language", null, l.getDisplayLanguage(l), stock));
        col.addView(Ui.row(this, "Region", null, l.getDisplayCountry(l).isEmpty() ? "—" : l.getDisplayCountry(l), stock));
        Ui.section(this, col, "Date and time");
        col.addView(Ui.row(this, "Time", null, new SimpleDateFormat(DateFormat.is24HourFormat(this) ? "H:mm" : "h:mm a", l).format(now), stock));
        col.addView(Ui.row(this, "Date", null, new SimpleDateFormat("EEE, MMM d, yyyy", l).format(now), stock));
        col.addView(Ui.row(this, "Clock format", null, DateFormat.is24HourFormat(this) ? "24-hour" : "12-hour", stock));
        TimeZone tz = TimeZone.getDefault();
        col.addView(Ui.row(this, "Time zone", null, tz.getDisplayName(tz.inDaylightTime(now), TimeZone.SHORT, l), stock));
        Ui.section(this, col, "System");
        col.addView(Ui.row(this, "Android", null, Build.VERSION.RELEASE, null));
        col.addView(Ui.row(this, "Build", null, Build.DISPLAY, null));
        col.addView(Ui.row(this, "General Settings", null, null, stock));
    }
}
