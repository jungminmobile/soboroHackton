package com.example.soboroskin.ui.diary

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter

class DiaryPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {

    override fun getItemCount(): Int = 4

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> DiaryLogFragment()
            1 -> DiaryChartFragment()
            2 -> DiaryPhotoFragment()
            3 -> AcneTrackFragment()
            else -> DiaryLogFragment()
        }
    }
}
