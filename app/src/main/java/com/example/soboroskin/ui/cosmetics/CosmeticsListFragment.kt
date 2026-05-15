package com.example.soboroskin.ui.cosmetics

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.soboroskin.databinding.FragmentCosmeticsListBinding
import kotlinx.coroutines.launch

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
        val adapter  = CosmeticsAdapter()

        binding.rvCosmetics.layoutManager = LinearLayoutManager(requireContext())
        binding.rvCosmetics.adapter       = adapter

        // 모델 목록 조회 (디버깅용 — 확인 후 제거)
        if (tabIndex == 0) {
            viewLifecycleOwner.lifecycleScope.launch {
                GeminiRecommendService.listModels()
            }
        }

        loadRecommendations(tabIndex, adapter)

        // 새로고침
        binding.btnRetry.setOnClickListener {
            GeminiRecommendService.clearCache()
            loadRecommendations(tabIndex, adapter, forceRefresh = true)
        }
    }

    private fun loadRecommendations(
        tabIndex: Int,
        adapter: CosmeticsAdapter,
        forceRefresh: Boolean = false
    ) {
        showLoading()

        viewLifecycleOwner.lifecycleScope.launch {
            val products = GeminiRecommendService.recommend(tabIndex, forceRefresh)

            if (_binding == null) return@launch

            if (products.isEmpty()) {
                showError()
            } else {
                adapter.submitList(products)
                showContent()
            }
        }
    }

    private fun showLoading() {
        binding.layoutLoading.visibility = View.VISIBLE
        binding.rvCosmetics.visibility   = View.GONE
        binding.layoutError.visibility   = View.GONE
    }

    private fun showContent() {
        binding.layoutLoading.visibility = View.GONE
        binding.rvCosmetics.visibility   = View.VISIBLE
        binding.layoutError.visibility   = View.GONE
    }

    private fun showError() {
        binding.layoutLoading.visibility = View.GONE
        binding.rvCosmetics.visibility   = View.GONE
        binding.layoutError.visibility   = View.VISIBLE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

data class CosmeticProduct(
    val brand: String,
    val name: String,
    val category: String,
    val price: String,
    val description: String,
    val whyRecommended: String,
    val officialUrl: String,
    val imageUrl: String
)
