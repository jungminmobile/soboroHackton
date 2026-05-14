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
                0 -> getString(R.string.diary_tab_log)
                1 -> getString(R.string.diary_tab_chart)
                2 -> getString(R.string.diary_tab_photo)
                3 -> "여드름"
                else -> ""
            }
        }.attach()

        binding.fabAddRecord.setOnClickListener {
            // 수동 기록 다이얼로그 표시
            AddManualRecordDialog().show(childFragmentManager, "AddManualRecord")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
