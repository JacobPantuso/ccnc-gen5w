package dev.ccnc.settings;

import android.app.Activity;
import android.content.Context;
import android.media.AudioManager;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.SeekBar;

import dev.ccnc.common.Palette;

/**
 * Sound: Android's own stream volumes with a slider each. The car's amplifier, equalizer, beep and
 * sound-field settings live in the stock Sound screen (below), which this page opens.
 */
public final class SoundActivity extends Activity {

    private static final Object[][] STREAMS = {
            {"Media", AudioManager.STREAM_MUSIC},
            {"Phone call", AudioManager.STREAM_VOICE_CALL},
            {"Notifications", AudioManager.STREAM_NOTIFICATION},
            {"Alarm", AudioManager.STREAM_ALARM},
            {"System sounds", AudioManager.STREAM_SYSTEM},
    };

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout col = Ui.page(this, "Sound");
        final AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        Ui.section(this, col, "Volume");
        for (Object[] st : STREAMS) {
            final int stream = (Integer) st[1];
            int max = 1, cur = 0;
            try { max = Math.max(1, am.getStreamMaxVolume(stream)); cur = am.getStreamVolume(stream); } catch (Throwable ignored) {}
            final LinearLayout r = Ui.row(this, (String) st[0], null, cur + " / " + max, null);
            final android.widget.TextView val = (android.widget.TextView) r.getChildAt(1);
            SeekBar sb = new SeekBar(this);
            sb.setMax(max);
            sb.setProgress(cur);
            final int m = max;
            sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar s, int p, boolean user) {
                    if (!user) return;
                    try { am.setStreamVolume(stream, p, 0); } catch (Throwable ignored) {}
                    val.setText(p + " / " + m);
                }
                @Override public void onStartTrackingTouch(SeekBar s) {}
                @Override public void onStopTrackingTouch(SeekBar s) {}
            });
            r.addView(sb, 1, new LinearLayout.LayoutParams(Palette.px(this, 360), -2));
            col.addView(r);
        }
        Ui.note(this, col, "The car may apply its own volume curve on top of these.");
        Ui.section(this, col, "Car audio");
        col.addView(Ui.row(this, "Sound Settings", null, null, new android.view.View.OnClickListener() {
            @Override public void onClick(android.view.View v) { Ui.openStock(SoundActivity.this, "com.android.soundsettings", "com.android.soundsettings.SoundSetting", "Sound settings"); }
        }));
    }
}
