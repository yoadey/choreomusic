package de.yoadey.choreomusic.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.button.MaterialButton
import de.yoadey.choreomusic.R

class OnboardingActivity : AppCompatActivity() {

    private val pages = listOf(
        OnboardingPage(R.string.intro_split_title, R.string.intro_split, R.drawable.baseline_playlist_add_24),
        OnboardingPage(R.string.intro_loop_title, R.string.intro_loop, R.drawable.onboarding_loop),
        OnboardingPage(R.string.intro_edit_title, R.string.intro_edit, R.drawable.onboarding_edit),
        OnboardingPage(R.string.intro_preptime_title, R.string.intro_preptime, R.drawable.onboarding_time),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)

        val pager = findViewById<ViewPager2>(R.id.onboardingPager)
        pager.adapter = OnboardingAdapter(pages)

        val back = findViewById<MaterialButton>(R.id.onboardingBack)
        val skip = findViewById<MaterialButton>(R.id.onboardingSkip)
        val next = findViewById<MaterialButton>(R.id.onboardingNext)
        val done = findViewById<MaterialButton>(R.id.onboardingDone)

        back.setOnClickListener {
            pager.currentItem = (pager.currentItem - 1).coerceAtLeast(0)
        }
        next.setOnClickListener {
            pager.currentItem = (pager.currentItem + 1).coerceAtMost(pages.lastIndex)
        }
        skip.setOnClickListener { finish() }
        done.setOnClickListener { finish() }

        pager.currentItem = savedInstanceState?.getInt(KEY_PAGE, 0) ?: 0

        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                back.visibility = if (position == 0) View.INVISIBLE else View.VISIBLE
                next.visibility = if (position == pages.lastIndex) View.INVISIBLE else View.VISIBLE
                done.visibility = if (position == pages.lastIndex) View.VISIBLE else View.INVISIBLE
                skip.visibility = if (position == pages.lastIndex) View.INVISIBLE else View.VISIBLE
            }
        })
    }

    data class OnboardingPage(val titleRes: Int, val descriptionRes: Int, val imageRes: Int)

    class OnboardingAdapter(private val pages: List<OnboardingPage>) : RecyclerView.Adapter<OnboardingAdapter.PageViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_onboarding_page, parent, false)
            return PageViewHolder(view)
        }

        override fun getItemCount(): Int = pages.size

        override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
            holder.bind(pages[position])
        }

        class PageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            fun bind(page: OnboardingPage) {
                itemView.findViewById<TextView>(R.id.onboardingPageTitle).setText(page.titleRes)
                itemView.findViewById<TextView>(R.id.onboardingPageDescription).setText(page.descriptionRes)
                itemView.findViewById<ImageView>(R.id.onboardingPageImage).setImageResource(page.imageRes)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_PAGE, findViewById<ViewPager2>(R.id.onboardingPager).currentItem)
    }

    companion object {
        private const val KEY_PAGE = "onboarding_page"

        @JvmField
        var COMPLETED_ONBOARDING_PREF_NAME = "onboardingPreference"
    }
}

