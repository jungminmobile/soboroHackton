package com.example.soboroskin.ui.cosmetics

import android.animation.ValueAnimator
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.soboroskin.databinding.FragmentCosmeticsListBinding
import kotlinx.coroutines.launch

class CosmeticsListFragment : Fragment() {

    private var _binding: FragmentCosmeticsListBinding? = null
    private val binding get() = _binding!!

    private var shimmerAnimator: ValueAnimator? = null

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

        // 스켈레톤 RecyclerView 세팅
        binding.rvSkeleton.layoutManager = LinearLayoutManager(requireContext())
        binding.rvSkeleton.adapter = SkeletonCosmeticsAdapter(count = 4)

        binding.rvCosmetics.layoutManager = LinearLayoutManager(requireContext())
        binding.rvCosmetics.adapter = adapter

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
        binding.rvSkeleton.visibility  = View.VISIBLE
        binding.rvCosmetics.visibility = View.GONE
        binding.layoutError.visibility = View.GONE
        startShimmer()
    }

    private fun showContent() {
        stopShimmer()
        binding.rvSkeleton.visibility  = View.GONE
        binding.rvCosmetics.visibility = View.VISIBLE
        binding.layoutError.visibility = View.GONE
    }

    private fun showError() {
        stopShimmer()
        binding.rvSkeleton.visibility  = View.GONE
        binding.rvCosmetics.visibility = View.GONE
        binding.layoutError.visibility = View.VISIBLE
    }

    private fun startShimmer() {
        shimmerAnimator?.cancel()
        shimmerAnimator = ValueAnimator.ofFloat(1f, 0.4f).apply {
            duration = 700
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { animator ->
                _binding?.rvSkeleton?.alpha = animator.animatedValue as Float
            }
            start()
        }
    }

    private fun stopShimmer() {
        shimmerAnimator?.cancel()
        shimmerAnimator = null
        _binding?.rvSkeleton?.alpha = 1f
    }

    override fun onDestroyView() {
        stopShimmer()
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
