package com.hkmc.telematics.weather.readandsaveweatherparcelable;
import android.os.Parcel; import android.os.Parcelable;
/** Preview-only stand-in for the head unit's bootclasspath class. */
public class ReadAndSavePresentWeatherInfo implements Parcelable {
    public double value; public int icon;
    public ReadAndSavePresentWeatherInfo(double value, int icon) { this.value = value; this.icon = icon; }
    public double getValue() { return value; }
    public int getIcon() { return icon; }
    public int describeContents() { return 0; }
    public void writeToParcel(Parcel d, int f) { d.writeDouble(value); d.writeInt(icon); }
    public static final Creator<ReadAndSavePresentWeatherInfo> CREATOR = new Creator<ReadAndSavePresentWeatherInfo>() {
        public ReadAndSavePresentWeatherInfo createFromParcel(Parcel p) { return new ReadAndSavePresentWeatherInfo(p.readDouble(), p.readInt()); }
        public ReadAndSavePresentWeatherInfo[] newArray(int n) { return new ReadAndSavePresentWeatherInfo[n]; } };
}
