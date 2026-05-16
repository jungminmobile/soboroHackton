package com.example.soboroskin.ui.home

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.soboroskin.ui.diary.AcneTrackFragment
import com.example.soboroskin.ui.diary.DiaryPhotoFragment

class HomePagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {

    override fun getItemCount(): Int = 3

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> HomeLogFragment()
            1 -> DiaryPhotoFragment()
            2 -> AcneTrackFragment()
            else -> HomeLogFragment()
        }
    }
}
