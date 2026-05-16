package com.example.soboroskin.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.soboroskin.databinding.FragmentHomeBinding
import com.example.soboroskin.ui.profile.ProfileBottomSheet
import com.google.android.material.tabs.TabLayoutMediator
import com.google.firebase.auth.FirebaseAuth

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        loadUserProfile()
        setupViewPager()

        binding.btnProfile.setOnClickListener {
            ProfileBottomSheet().show(parentFragmentManager, ProfileBottomSheet.TAG)
        }
    }

    private fun loadUserProfile() {
        val user = FirebaseAuth.getInstance().currentUser
        if (user != null) {
            val name = user.displayName?.takeIf { it.isNotEmpty() } ?: "사용자"
            binding.tvAvatar.text = name.firstOrNull()?.toString() ?: "S"
            binding.tvProfileName.text = name
        } else {
            binding.tvAvatar.text = "G"
            binding.tvProfileName.text = "게스트"
        }
    }

    private fun setupViewPager() {
        val adapter = HomePagerAdapter(this)
        binding.viewpagerHome.adapter = adapter

        TabLayoutMediator(binding.tabLayoutHome, binding.viewpagerHome) { tab, position ->
            tab.text = when (position) {
                0 -> "기록"
                1 -> "사진"
                2 -> "트러블 추적"
                else -> ""
            }
        }.attach()
    }

    fun switchToTab(index: Int) {
        binding.viewpagerHome.currentItem = index
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
