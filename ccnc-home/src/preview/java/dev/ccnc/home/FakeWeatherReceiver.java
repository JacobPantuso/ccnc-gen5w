package dev.ccnc.home;
import android.content.BroadcastReceiver; import android.content.Context; import android.content.Intent;
import com.hkmc.telematics.weather.readandsaveweatherparcelable.*;
import java.util.ArrayList;
/** Preview-only: answers request_weather_update like the head unit's weather service (12.2 C, code 4). */
public class FakeWeatherReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent in) {
        ReadAndSaveWeatherInfo info = new ReadAndSaveWeatherInfo();
        info.items.add(new ReadAndSavePresentWeatherInfo(12.2, 4));
        ReadAndSaveWeatherInfoList list = new ReadAndSaveWeatherInfoList();
        list.items.add(info);
        ArrayList<ReadAndSaveWeatherInfoList> extra = new ArrayList<ReadAndSaveWeatherInfoList>();
        extra.add(list);
        c.sendBroadcast(new Intent(WeatherClient.ACTION_UPDATE).putParcelableArrayListExtra(WeatherClient.EXTRA_LIST, extra));
    }
}
