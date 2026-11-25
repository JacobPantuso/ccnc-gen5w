package dev.ccnc.radio;

/** Off-car stand-in so the screen can be previewed and used on an emulator. */
final class DemoBackend implements Radio.Backend {
    private final Radio.State s = new Radio.State();
    private Radio.Listener out;
    private static final int[] FM = {881, 933, 977, 1011, 1039, 1071};

    DemoBackend() {
        s.live = true;
        s.band = Radio.FM1;
        s.frequency = FM[2];
        s.preset = 3;
        s.signal = 4;
        s.stereo = true;
        s.station = "CBC RADIO";
        s.text = "Now playing: Midnight City – M83";
        for (int i = 0; i < 6; i++) {
            Radio.Preset p = new Radio.Preset();
            p.number = i + 1; p.band = Radio.FM1; p.frequency = FM[i];
            p.name = new String[]{"CBC 1", "CHUM", "CBC 2", "JAZZ", "KISS", "Q107"}[i];
            s.presets[i] = p;
        }
    }

    @Override public void start(Radio.Listener l) { out = l; l.onRadio(s); }
    @Override public void stop() { out = null; }

    @Override public void seek(boolean up) {
        int i = 0;
        for (; i < FM.length; i++) if (FM[i] == s.frequency) break;
        i = Math.max(0, Math.min(FM.length - 1, i + (up ? 1 : -1)));
        s.frequency = FM[i];
        s.preset = i + 1;
        s.station = s.presets[i].name;
        if (out != null) out.onRadio(s);
    }

    @Override public void band(int band) {
        s.band = band;
        s.frequency = band == Radio.AM1 ? 1010 : FM[2];
        s.preset = 0;
        s.station = "";
        s.text = "";
        if (out != null) out.onRadio(s);
    }

    @Override public void selectPreset(Radio.Preset p) {
        s.band = p.band; s.frequency = p.frequency; s.preset = p.number; s.station = p.name;
        if (out != null) out.onRadio(s);
    }

    @Override public void tune(int raw) {}

    @Override public boolean isDemo() { return true; }
}
