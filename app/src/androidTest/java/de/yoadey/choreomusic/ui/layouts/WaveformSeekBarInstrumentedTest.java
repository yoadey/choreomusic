package de.yoadey.choreomusic.ui.layouts;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

import de.yoadey.choreomusic.testutil.TestHostActivity;

@RunWith(AndroidJUnit4.class)
public class WaveformSeekBarInstrumentedTest {

    @Test
    public void touchAndDragProduceDeterministicProgress() {
        List<Float> progressEvents = new ArrayList<>();

        try (ActivityScenario<TestHostActivity> scenario = ActivityScenario.launch(TestHostActivity.class)) {
            scenario.onActivity(activity -> {
                WaveformSeekBar seekBar = activity.getWaveformSeekBar();
                seekBar.layout(0, 0, 1000, 100);
                seekBar.setMaxProgress(100);
                seekBar.setSample(new int[]{10, 30, 80, 50, 20});
                seekBar.setOnProgressChanged((bar, p, fromUser) -> progressEvents.add(p));

                seekBar.dispatchTouchEvent(android.view.MotionEvent.obtain(0, 0,
                        android.view.MotionEvent.ACTION_DOWN, 250, 50, 0));
                seekBar.dispatchTouchEvent(android.view.MotionEvent.obtain(0, 16,
                        android.view.MotionEvent.ACTION_MOVE, 750, 50, 0));
            });
        }

        Assert.assertTrue(progressEvents.size() >= 2);
        Assert.assertEquals(25f, progressEvents.get(0), 2f);
        Assert.assertEquals(75f, progressEvents.get(1), 2f);
    }
}
