package dev.ccnc.radio;

import java.util.Locale;

/** Tuner model and the backend that feeds it. */
final class Radio {
    private Radio() {}

    // Band ids as the unit's radio service takes them (MicomRadioConstants: AM1 0x71, FM1 -0x7f, FM2 -0x7e).
    static final int AM1 = 0x71, FM1 = -0x7f, FM2 = -0x7e;

    static final class Preset {
        int number;
        int band;
        int frequency;
        boolean step50;
        String name = "";
    }

    static final class State {
        int band = FM1;
        int frequency;          // raw, as the service reports it
        int preset;             // 0 = none
        int signal;
        boolean stereo, af, step50;
        String station = "";    // RDS PS
        String text = "";       // RDS RadioText
        final Preset[] presets = new Preset[6];
        boolean live;           // true once real data has arrived
        String error;
    }

    interface Listener { void onRadio(State s); }

    interface Backend {
        void start(Listener l);
        void stop();
        void seek(boolean up);
        void band(int band);
        void selectPreset(Preset p);
        void tune(int raw);
        boolean isDemo();
    }

    static String bandName(int b) { return b == AM1 ? "AM" : b == FM2 ? "FM2" : "FM"; }

    /**
     * Formats the raw frequency. From MicomRadioConstants on this unit: FM runs 875..1080 in 0.1 MHz
     * units (FM_HIGH = 0x438 = 1080, 0.2 MHz step) and AM up to 1620 kHz (AM_HIGH = 0x654). The wider
     * ranges (10 kHz or kHz units) are kept as a fallback for other builds.
     */
    static String freq(int band, int raw) {
        if (raw <= 0) return "--.-";
        if (band == AM1) return String.valueOf(raw);
        double mhz = raw >= 70000 ? raw / 1000.0 : raw >= 7000 ? raw / 100.0 : raw / 10.0;
        return String.format(Locale.US, "%.1f", mhz);
    }

    static String unit(int band) { return band == AM1 ? "kHz" : "MHz"; }
}
