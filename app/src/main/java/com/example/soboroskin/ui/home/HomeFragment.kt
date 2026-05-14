package com.example.soboroskin.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.soboroskin.MainActivity
import com.example.soboroskin.R
import com.example.soboroskin.data.db.AppDatabase
import com.example.soboroskin.data.model.DiagnosisEntity
import com.example.soboroskin.databinding.FragmentHomeBinding
import com.example.soboroskin.ui.profile.ProfileBottomSheet
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val tips = listOf(
        "자외선 차단제는 외출 30분 전에 바르는 것이 효과적이에요.",
        "세안 후 3분 안에 보습제를 바르면 수분 유지에 도움이 돼요.",
        "충분한 수면은 피부 재생에 가장 중요한 요소예요.",
        "따뜻한 물보다 미지근한 물로 세안하면 피부 자극이 줄어요.",
        "비타민 C 세럼은 아침에, 레티놀은 저녁에 사용하면 좋아요.",
        "하루 2리터 이상의 물 섭취가 피부 건강에 도움이 돼요."
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 랜덤 팁
        binding.tvTip.text = tips.random()

        // 프로필 버튼
        binding.btnProfile.setOnClickListener {
            ProfileBottomSheet().show(parentFragmentManager, ProfileBottomSheet.TAG)
        }

        // 진단 시작 버튼
        binding.btnQuickScan.setOnClickListener {
            (activity as? MainActivity)?.openScanOverlay()
        }

        loadUserProfile()
        loadLatestDiagnosis()
    }

    override fun onResume() {
        super.onResume()
        loadUserProfile()
        loadLatestDiagnosis()
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

    private fun loadLatestDiagnosis() {
        lifecycleScope.launch {
            val latest = AppDatabase.getInstance(requireContext())
                .diagnosisDao()
                .getLatestDiagnosis()

            requireActivity().runOnUiThread {
                if (latest != null) {
                    showDiagnosis(latest)
                } else {
                    showEmpty()
                }
            }
        }
    }

    private fun showDiagnosis(entity: DiagnosisEntity) {
        binding.layoutDiagnosisContent.visibility = View.VISIBLE
        binding.layoutDiagnosisEmpty.visibility = View.GONE

        binding.tvSkinTypeBadge.text = entity.skinType

        val sdf = SimpleDateFormat("yyyy. MM. dd", Locale.getDefault())
        binding.tvDiagnosisDate.text = sdf.format(Date(entity.date))

        binding.tvScoreMoisture.text = getString(R.string.score_format, entity.moistureScore)
        binding.tvScoreOil.text = getString(R.string.score_format, entity.oilScore)
        binding.tvScoreTrouble.text = getString(R.string.score_format, entity.troubleScore)
        binding.tvScoreElasticity.text = getString(R.string.score_format, entity.elasticityScore)
    }

    private fun showEmpty() {
        binding.layoutDiagnosisContent.visibility = View.GONE
        binding.layoutDiagnosisEmpty.visibility = View.VISIBLE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
