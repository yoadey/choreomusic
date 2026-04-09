package de.yoadey.choreomusic.testutil;

import android.os.Bundle;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import de.yoadey.choreomusic.ui.layouts.WaveformSeekBar;

public class TestHostActivity extends AppCompatActivity {

    private WaveformSeekBar waveformSeekBar;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FrameLayout root = new FrameLayout(this);
        waveformSeekBar = new WaveformSeekBar(this);
        waveformSeekBar.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                (int) (120 * getResources().getDisplayMetrics().density)));
        root.addView(waveformSeekBar);
        setContentView(root);
    }

    public WaveformSeekBar getWaveformSeekBar() {
        return waveformSeekBar;
    }
}
