package com.example.soboroskin.onboarding

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.example.soboroskin.MainActivity
import com.example.soboroskin.R
import com.example.soboroskin.databinding.ActivityOnboardingBinding

class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding

    companion object {
        private const val PREF_NAME = "soboroskin_prefs"
        private const val KEY_ONBOARDING_DONE = "onboarding_done"
    }

    private val pages = listOf(
        OnboardingPage(R.string.onboarding_title_1, R.string.onboarding_desc_1, "🔬"),
        OnboardingPage(R.string.onboarding_title_2, R.string.onboarding_desc_2, "📔"),
        OnboardingPage(R.string.onboarding_title_3, R.string.onboarding_desc_3, "✨")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 이미 온보딩을 완료했으면 바로 MainActivity로
        val prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE)
        if (prefs.getBoolean(KEY_ONBOARDING_DONE, false)) {
            goToMain()
            return
        }

        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupViewPager()
        setupButtons()
    }

    private fun setupViewPager() {
        val adapter = OnboardingAdapter(pages)
        binding.viewpagerOnboarding.adapter = adapter

        binding.viewpagerOnboarding.registerOnPageChangeCallback(object :
            ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateDots(position)
                updateButton(position)
            }
        })
    }

    private fun updateDots(position: Int) {
        val dots = listOf(binding.dot1, binding.dot2, binding.dot3)
        val activeDp = resources.getDimensionPixelSize(R.dimen.onboarding_dot_active_width)
        val inactiveDp = resources.getDimensionPixelSize(R.dimen.onboarding_dot_size)

        dots.forEachIndexed { index, view ->
            val lp = view.layoutParams
            if (index == position) {
                lp.width = activeDp
                view.setBackgroundResource(R.drawable.bg_onboarding_dot_active)
            } else {
                lp.width = inactiveDp
                view.setBackgroundResource(R.drawable.bg_onboarding_dot_inactive)
            }
            view.layoutParams = lp
        }
    }

    private fun updateButton(position: Int) {
        binding.btnNext.text = getString(
            if (position == pages.size - 1) R.string.btn_start else R.string.btn_next
        )
    }

    private fun setupButtons() {
        binding.btnNext.setOnClickListener {
            val current = binding.viewpagerOnboarding.currentItem
            if (current < pages.size - 1) {
                binding.viewpagerOnboarding.currentItem = current + 1
            } else {
                finishOnboarding()
            }
        }
        binding.btnSkip.setOnClickListener { finishOnboarding() }
    }

    private fun finishOnboarding() {
        getSharedPreferences(PREF_NAME, MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ONBOARDING_DONE, true)
            .apply()
        goToMain()
    }

    private fun goToMain() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        @Suppress("DEPRECATION")
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }
}

data class OnboardingPage(
    val titleRes: Int,
    val descRes: Int,
    val emoji: String
)
