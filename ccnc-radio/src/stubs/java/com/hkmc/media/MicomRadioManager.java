package com.hkmc.media;
import android.content.Context;
public class MicomRadioManager {
    public MicomRadioManager(Context c) {}
    public void addListener(MicomRadioListener l) {}
    public void removeListener(MicomRadioListener l) {}
    public void requestRadioStatus(int deviceId, boolean force) {}
    public void userChangeBand(int deviceId) {}
    public void seekUp() {}
    public void seekDown() {}
    public void cancelSeek() {}
    public boolean directTune(int frequency, boolean isFMStep50KHz) { return false; }
    public void readPreset(int presetNumber) {}
}
