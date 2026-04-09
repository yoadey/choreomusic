package de.yoadey.choreomusic.ui.popups;

import static androidx.test.espresso.Espresso.onData;
import static org.hamcrest.Matchers.anything;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.atomic.AtomicInteger;

import de.yoadey.choreomusic.testutil.TestHostActivity;

@RunWith(AndroidJUnit4.class)
public class ColorPickerHelperTest {

    @Test
    public void selectsColorFromDialog() {
        AtomicInteger selected = new AtomicInteger(0);
        try (ActivityScenario<TestHostActivity> scenario = ActivityScenario.launch(TestHostActivity.class)) {
            scenario.onActivity(activity -> ColorPickerHelper.show(activity, new int[]{0xFF112233, 0xFF445566}, 0xFF112233, selected::set));
            onData(anything()).atPosition(1).perform(androidx.test.espresso.action.ViewActions.click());
        }
        Assert.assertEquals(0xFF445566, selected.get());
    }
}
