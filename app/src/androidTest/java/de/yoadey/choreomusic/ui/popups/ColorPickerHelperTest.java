package de.yoadey.choreomusic.ui.popups;

import static androidx.test.espresso.Espresso.onData;
import static org.hamcrest.Matchers.anything;
import static org.junit.Assert.assertEquals;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.atomic.AtomicInteger;

import de.yoadey.choreomusic.testutil.TestHostActivity;

@RunWith(AndroidJUnit4.class)
public class ColorPickerHelperTest {

    @Test
    public void selectsColorAndPersistsValueInCallerState() {
        AtomicInteger selected = new AtomicInteger(0x00000000);
        AtomicInteger persisted = new AtomicInteger(0x00000000);

        try (ActivityScenario<TestHostActivity> scenario = ActivityScenario.launch(TestHostActivity.class)) {
            scenario.onActivity(activity -> ColorPickerHelper.show(activity,
                    new int[]{0xFF112233, 0xFF445566, 0xFF778899},
                    0xFF112233,
                    color -> {
                        selected.set(color);
                        persisted.set(color);
                    }));

            onData(anything()).atPosition(2).perform(androidx.test.espresso.action.ViewActions.click());
        }

        assertEquals(0xFF778899, selected.get());
        assertEquals(0xFF778899, persisted.get());
    }
}
