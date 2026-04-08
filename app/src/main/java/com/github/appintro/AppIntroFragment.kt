package com.github.appintro

import androidx.fragment.app.Fragment

class AppIntroFragment : Fragment() {
    companion object {
        @JvmStatic
        fun newInstance(
            title: String,
            description: String,
            imageDrawable: Int,
            backgroundDrawable: Int,
        ): AppIntroFragment {
            return AppIntroFragment()
        }
    }
}
