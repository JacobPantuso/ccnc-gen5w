package com.hkmc.telematics.weather.readandsaveweatherparcelable;
import android.os.Parcel; import android.os.Parcelable; import java.util.ArrayList; import java.util.List;
/** Preview-only stand-in for the head unit's bootclasspath class. */
public class ReadAndSaveWeatherInfoList implements Parcelable {
    public List<Object> items = new ArrayList<Object>();
    public String title = "Home";
    public ReadAndSaveWeatherInfoList() {}
    ReadAndSaveWeatherInfoList(Parcel p) { items = new ArrayList<Object>(); p.readList(items, getClass().getClassLoader()); title = p.readString(); }
    public List<Object> getmReadAndSaveWeatherInfoList() { return items; }
    public String getCurrentLocTitle() { return title; }
    public int describeContents() { return 0; }
    public void writeToParcel(Parcel d, int f) { d.writeList(items); d.writeString(title); }
    public static final Creator<ReadAndSaveWeatherInfoList> CREATOR = new Creator<ReadAndSaveWeatherInfoList>() {
        public ReadAndSaveWeatherInfoList createFromParcel(Parcel p) { return new ReadAndSaveWeatherInfoList(p); }
        public ReadAndSaveWeatherInfoList[] newArray(int n) { return new ReadAndSaveWeatherInfoList[n]; } };
}
