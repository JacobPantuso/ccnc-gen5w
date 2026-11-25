package dev.ccnc.radio;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.hkmc.media.MicomRadioListener;
import com.hkmc.media.MicomRadioManager;
import com.hkmc.media.MicomRadioPreset;
import com.hkmc.media.MicomRadioPresetData;
import com.hkmc.media.MicomRadioStatus;

/**
 * The unit's tuner, through the same framework class the stock radio app uses
 * ({@code com.hkmc.media.MicomRadioManager}, in the bootclasspath: the classes here are
 * compile-time stubs and aren't packaged). Only loaded when that class exists.
 */
final class HkmcBackend implements Radio.Backend {
    static final String TAG = "ccncRadio";

    private final Context ctx;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Radio.State state = new Radio.State();
    private MicomRadioManager mgr;
    private Radio.Listener out;

    private final MicomRadioListener listener = new MicomRadioListener() {
        @Override public void onMicomRadioStatusChanged(MicomRadioStatus s) {
            state.live = true;
            state.band = s.mBand;
            state.frequency = s.mFrequency;
            state.preset = s.mPresetNumber;
            state.signal = s.mSignalStrength;
            state.stereo = s.mIsStereo;
            state.af = s.mIsAFOn;
            state.step50 = s.mIsFMStep50KHz;
            Log.i(TAG, "status band=" + s.mBand + " freq=" + s.mFrequency + " preset=" + s.mPresetNumber + " sig=" + s.mSignalStrength);
            emit();
        }

        @Override public void onMicomRadioPSChanged(int a, String ps) {
            state.station = ps == null ? "" : ps.trim();
            emit();
        }

        @Override public void onMicomRadioTextChanged(int a, String rt) {
            state.text = rt == null ? "" : rt.trim();
            emit();
        }

        @Override public void onMicomRadioPresetChanged(boolean b, MicomRadioPresetData d) {
            if (d == null || d.mPresetList == null) return;
            for (MicomRadioPreset p : d.mPresetList) {
                if (p == null || p.mNumber < 1 || p.mNumber > state.presets.length) continue;
                Radio.Preset r = new Radio.Preset();
                r.number = p.mNumber;
                r.band = p.mBand;
                r.frequency = p.mFrequency;
                r.step50 = p.mIsFMStep50KHz;
                r.name = p.mPSName == null ? "" : p.mPSName.trim();
                state.presets[p.mNumber - 1] = r;
            }
            emit();
        }
    };

    HkmcBackend(Context ctx) { this.ctx = ctx; }

    @Override public void start(Radio.Listener l) {
        out = l;
        try {
            mgr = new MicomRadioManager(ctx);
            mgr.addListener(listener);
            mgr.requestRadioStatus(Radio.FM1, true);
            mgr.requestRadioStatus(Radio.AM1, true);
            for (int i = 1; i <= state.presets.length; i++) mgr.readPreset(i);
        } catch (Throwable t) {
            Log.w(TAG, "tuner service unavailable", t);
            state.error = "The radio service didn't accept this app. Use the stock Radio.";
            mgr = null;
        }
        emit();
    }

    @Override public void stop() {
        if (mgr != null) {
            try { mgr.removeListener(listener); } catch (Throwable ignored) {}
            mgr = null;
        }
        out = null;
    }

    @Override public void seek(boolean up) {
        if (mgr == null) return;
        try { if (up) mgr.seekUp(); else mgr.seekDown(); } catch (Throwable t) { Log.w(TAG, "seek failed", t); }
    }

    @Override public void band(int band) {
        if (mgr == null) return;
        try { mgr.userChangeBand(band); mgr.requestRadioStatus(band, true); } catch (Throwable t) { Log.w(TAG, "band failed", t); }
    }

    @Override public void selectPreset(Radio.Preset p) {
        if (mgr == null || p == null) return;
        try {
            if (p.band != state.band) mgr.userChangeBand(p.band);
            mgr.directTune(p.frequency, p.step50);
        } catch (Throwable t) { Log.w(TAG, "preset failed", t); }
    }

    @Override public void tune(int raw) {
        if (mgr == null) return;
        try { mgr.directTune(raw, state.step50); } catch (Throwable t) { Log.w(TAG, "tune failed", t); }
    }

    @Override public boolean isDemo() { return false; }

    private void emit() {
        ui.post(new Runnable() { @Override public void run() { if (out != null) out.onRadio(state); } });
    }
}
