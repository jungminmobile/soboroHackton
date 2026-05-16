package com.example.soboroskin.ui.diary

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.soboroskin.R
import com.example.soboroskin.databinding.FragmentDiaryBinding
import com.google.android.material.tabs.TabLayoutMediator

class DiaryFragment : Fragment() {

    private var _binding: FragmentDiaryBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDiaryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = DiaryPagerAdapter(this)
        binding.viewpagerDiary.adapter = adapter

        TabLayoutMediator(binding.tabLayoutDiary, binding.viewpagerDiary) { tab, position ->
            tab.text = when (position) {
                0 -> "기록"
                1 -> "사진"
                2 -> "트러블 추적"
                else -> ""
            }
        }.attach()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
