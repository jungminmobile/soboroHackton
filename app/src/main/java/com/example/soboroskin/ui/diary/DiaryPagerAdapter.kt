package com.example.soboroskin.ui.diary

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter

class DiaryPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {

    override fun getItemCount(): Int = 3

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> DiaryLogFragment()
            1 -> DiaryChartFragment()
            2 -> DiaryPhotoFragment()
            else -> DiaryLogFragment()
        }
    }
}
