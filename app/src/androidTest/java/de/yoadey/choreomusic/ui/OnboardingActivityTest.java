package de.yoadey.choreomusic.ui;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.swipeLeft;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;

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
}
