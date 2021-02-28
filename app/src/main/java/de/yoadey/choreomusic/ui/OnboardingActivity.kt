package de.yoadey.choreomusic.ui

import android.os.Bundle
import androidx.fragment.app.Fragment
import com.github.appintro.AppIntro
import com.github.appintro.AppIntroFragment
import de.yoadey.choreomusic.R

class OnboardingActivity : AppIntro() {
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Make sure you don't call setContentView!

        addSlide(AppIntroFragment.newInstance(
                title = getString(R.string.intro_split_title),
                imageDrawable = R.drawable.baseline_playlist_add_24,
                description = getString(R.string.intro_split),
                backgroundDrawable = R.drawable.intro_background
        ))
        addSlide(AppIntroFragment.newInstance(
                title = getString(R.string.intro_loop_title),
                imageDrawable = R.drawable.onboarding_loop,
                description = getString(R.string.intro_loop),
                backgroundDrawable = R.drawable.intro_background
        ))
        addSlide(AppIntroFragment.newInstance(
                title = getString(R.string.intro_edit_title),
                imageDrawable = R.drawable.onboarding_edit,
                description = getString(R.string.intro_edit),
                backgroundDrawable = R.drawable.intro_background
        ))
        addSlide(AppIntroFragment.newInstance(
                title = getString(R.string.intro_preptime_title),
                imageDrawable = R.drawable.onboarding_time,
                description = getString(R.string.intro_preptime),
                backgroundDrawable = R.drawable.intro_background
        ))
    }

    public override fun onSkipPressed(currentFragment: Fragment?) {
        super.onSkipPressed(currentFragment)
        finish()
    }

    public override fun onDonePressed(currentFragment: Fragment?) {
        super.onDonePressed(currentFragment)
        finish()
    }

    companion object {
        @JvmField
        var COMPLETED_ONBOARDING_PREF_NAME = "onboardingPreference"
    }
}