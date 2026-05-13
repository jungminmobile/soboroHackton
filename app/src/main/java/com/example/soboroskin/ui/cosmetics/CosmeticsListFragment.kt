package com.example.soboroskin.ui.cosmetics

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.soboroskin.databinding.FragmentCosmeticsListBinding

class CosmeticsListFragment : Fragment() {

    private var _binding: FragmentCosmeticsListBinding? = null
    private val binding get() = _binding!!

    companion object {
        private const val ARG_TAB = "tab"
        fun newInstance(tabIndex: Int) = CosmeticsListFragment().apply {
            arguments = Bundle().apply { putInt(ARG_TAB, tabIndex) }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCosmeticsListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tabIndex = arguments?.getInt(ARG_TAB) ?: 0
        val products = getMockProducts(tabIndex)

        val adapter = CosmeticsAdapter(products)
        binding.rvCosmetics.layoutManager = LinearLayoutManager(requireContext())
        binding.rvCosmetics.adapter = adapter
    }

    private fun getMockProducts(tab: Int): List<CosmeticProduct> {
        val all = listOf(
            CosmeticProduct("LANEIGE", "워터뱅크 블루 히알루로닉 크림", "42,000원", "건성 추천", "크림"),
            CosmeticProduct("이니스프리", "그린티 씨드 세럼", "28,000원", "지성 추천", "세럼"),
            CosmeticProduct("COSRX", "어드밴스드 스네일 92 올인원 크림", "22,000원", "복합성 추천", "크림"),
            CosmeticProduct("라로슈포제", "안티헬리오스 XL SPF50+", "35,000원", "민감성 추천", "선크림"),
            CosmeticProduct("클리오", "킬커버 파운웨어 쿠션", "18,000원", "중성 추천", "토너"),
            CosmeticProduct("아이소이", "불가리안 로즈 토너", "32,000원", "건성 추천", "토너"),
            CosmeticProduct("파울라스 초이스", "BHA 2% 페이셜 엑스폴리언트", "38,000원", "지성 추천", "세럼"),
            CosmeticProduct("아벤느", "시카렉트 B5 리페어 크림", "25,000원", "민감성 추천", "크림"),
            CosmeticProduct("넥스케어", "하이드로콜로이드 패치", "8,000원", "트러블 추천", "세럼"),
            CosmeticProduct("에뛰드", "순정 콜라겐 물광 선크림", "14,000원", "복합성 추천", "선크림"),
        )

        val categories = listOf("전체", "토너", "세럼", "크림", "선크림")
        return if (tab == 0) all
        else all.filter { it.category == categories[tab] }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

data class CosmeticProduct(
    val brand: String,
    val name: String,
    val price: String,
    val skinMatch: String,
    val category: String
)
