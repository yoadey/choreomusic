package de.yoadey.choreomusic.ui;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.swipeLeft;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static org.junit.Assert.assertEquals;

import android.content.pm.ActivityInfo;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.viewpager2.widget.ViewPager2;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import de.yoadey.choreomusic.R;

@RunWith(AndroidJUnit4.class)
public class OnboardingActivityTest {

    @Rule
    public ActivityScenarioRule<OnboardingActivity> rule = new ActivityScenarioRule<>(OnboardingActivity.class);

    @Test
    public void showsDoneButtonOnLastPage() {
        onView(withId(R.id.onboardingPager)).perform(swipeLeft(), swipeLeft(), swipeLeft());
        onView(withId(R.id.onboardingDone)).check(matches(isDisplayed()));
    }

    @Test
    public void skipClosesOnboarding() {
        onView(withId(R.id.onboardingSkip)).perform(click());
        rule.getScenario().onActivity(activity -> assertEquals(true, activity.isFinishing()));
    }

    @Test
    public void keepsPageOnRotation() {
        onView(withId(R.id.onboardingPager)).perform(swipeLeft(), swipeLeft());

        rule.getScenario().onActivity(activity ->
                activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));

        rule.getScenario().onActivity(activity -> {
            ViewPager2 pager = activity.findViewById(R.id.onboardingPager);
            assertEquals(2, pager.getCurrentItem());
        });
    }
}
