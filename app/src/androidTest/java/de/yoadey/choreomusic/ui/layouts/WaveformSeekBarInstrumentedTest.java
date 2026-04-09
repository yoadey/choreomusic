package de.yoadey.choreomusic.ui.layouts;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.atomic.AtomicReference;

import de.yoadey.choreomusic.testutil.TestHostActivity;

@RunWith(AndroidJUnit4.class)
public class WaveformSeekBarInstrumentedTest {

    @Test
    public void touchUpdatesProgress() {
        AtomicReference<Float> progress = new AtomicReference<>(0f);
        try (ActivityScenario<TestHostActivity> scenario = ActivityScenario.launch(TestHostActivity.class)) {
            scenario.onActivity(activity -> {
                WaveformSeekBar seekBar = activity.getWaveformSeekBar();
                seekBar.setMaxProgress(100);
                seekBar.setSample(new int[]{10, 30, 80, 50, 20});
                seekBar.setOnProgressChanged((bar, p, fromUser) -> progress.set(p));
                seekBar.dispatchTouchEvent(android.view.MotionEvent.obtain(0,0,android.view.MotionEvent.ACTION_DOWN,seekBar.getWidth() * 0.75f,seekBar.getHeight()/2f,0));
            });
        }
        Assert.assertTrue(progress.get() > 60f);
    }
}
