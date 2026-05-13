package com.example.soboroskin.ui.community

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.soboroskin.R
import com.example.soboroskin.databinding.FragmentCommunityBinding
import com.google.android.material.tabs.TabLayoutMediator

class CommunityFragment : Fragment() {

    private var _binding: FragmentCommunityBinding? = null
    private val binding get() = _binding!!

    private val tabTitles = listOf("인기", "최신", "질문")

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCommunityBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = CommunityPagerAdapter(this)
        binding.viewpagerCommunity.adapter = adapter

        TabLayoutMediator(binding.tabLayoutCommunity, binding.viewpagerCommunity) { tab, position ->
            tab.text = tabTitles[position]
        }.attach()

        binding.fabWrite.setOnClickListener {
            Toast.makeText(requireContext(), "글쓰기 기능은 준비 중이에요", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
