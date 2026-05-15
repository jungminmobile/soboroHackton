package com.example.soboroskin.ui.cosmetics

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.soboroskin.data.db.AppDatabase
import com.example.soboroskin.databinding.FragmentCosmeticsBinding
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CosmeticsFragment : Fragment() {

    private var _binding: FragmentCosmeticsBinding? = null
    private val binding get() = _binding!!

    private val tabTitles = listOf("전체", "토너", "세럼", "크림", "선크림")

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCosmeticsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = CosmeticsPagerAdapter(this)
        binding.viewpagerCosmetics.adapter = adapter

        TabLayoutMediator(binding.tabLayoutCosmetics, binding.viewpagerCosmetics) { tab, pos ->
            tab.text = tabTitles[pos]
        }.attach()

        // 최근 진단 데이터 → GeminiRecommendService에 주입
        lifecycleScope.launch {
            val latest = withContext(Dispatchers.IO) {
                AppDatabase.getInstance(requireContext()).diagnosisDao().getLatestDiagnosis()
            }
            if (latest != null) {
                GeminiRecommendService.skinType      = latest.skinType
                GeminiRecommendService.moistureScore = latest.moistureScore
                GeminiRecommendService.oilScore      = latest.oilScore
                GeminiRecommendService.troubleScore  = latest.troubleScore
                GeminiRecommendService.elasticityScore = latest.elasticityScore

                binding.tvSkinTypeRecommend.text =
                    "${latest.skinType} 피부 타입에 맞는 화장품을 AI가 추천해드려요 ✨"
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
