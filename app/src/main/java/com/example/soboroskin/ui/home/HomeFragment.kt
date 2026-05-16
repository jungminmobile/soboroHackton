package com.example.soboroskin.ui.home

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.example.soboroskin.MainActivity
import com.example.soboroskin.R
import com.example.soboroskin.data.db.AppDatabase
import com.example.soboroskin.data.model.DiagnosisEntity
import com.example.soboroskin.databinding.FragmentHomeBinding
import com.example.soboroskin.ui.profile.ProfileBottomSheet
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

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

        setupUI()
        observeData()
    }

    private fun setupUI() {
        // 오늘 날짜 표시
        val today = SimpleDateFormat("yyyy년 M월 d일 EEEE", Locale.KOREAN)
            .format(Date())
        binding.tvTodayDate.text = today

        // 프로필 버튼
        binding.btnProfile.setOnClickListener {
            ProfileBottomSheet().show(parentFragmentManager, ProfileBottomSheet.TAG)
        }

        // 진단 시작 버튼
        binding.btnQuickScan.setOnClickListener {
            (activity as? MainActivity)?.openScanOverlay()
        }

        setupMiniChart()
        loadUserProfile()
    }

    private fun setupMiniChart() {
        binding.miniLineChart.apply {
            description.isEnabled = false
            legend.isEnabled = false
            setTouchEnabled(false)
            setScaleEnabled(false)
            setPinchZoom(false)
            setDrawGridBackground(false)
            xAxis.isEnabled = false
            axisLeft.isEnabled = false
            axisRight.isEnabled = false
            setNoDataText("데이터를 불러오는 중...")
        }
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            AppDatabase.getInstance(requireContext())
                .diagnosisDao()
                .getAllEntries()
                .collectLatest { entries ->
                    if (entries.isNotEmpty()) {
                        val latest = entries.first()
                        showDiagnosis(latest)
                        updateMiniChart(entries.take(7).reversed())
                        updateAiInsight(latest)
                        loadRecentPhotos(entries.take(5))
                    } else {
                        showEmpty()
                    }
                }
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

    private fun showDiagnosis(entity: DiagnosisEntity) {
        binding.layoutDiagnosisContent.visibility = View.VISIBLE
        binding.layoutDiagnosisEmpty.visibility = View.GONE

        val sdf = SimpleDateFormat("yyyy. MM. dd", Locale.getDefault())
        binding.tvDiagnosisDate.text = sdf.format(Date(entity.date))
        
        // 종합 점수 계산 (가중치: 트러블이 점수를 많이 깎음)
        val totalScore = (entity.moistureScore + (100 - entity.oilScore) + (100 - entity.troubleScore) + entity.elasticityScore) / 4
        binding.tvTotalScoreBanner.text = totalScore.toString()

        // 세부 점수
        binding.tvScoreMoisture.text   = getString(R.string.score_format, entity.moistureScore)
        binding.tvScoreOil.text        = getString(R.string.score_format, entity.oilScore)
        binding.tvScoreTrouble.text    = getString(R.string.score_format, entity.troubleScore)
        binding.tvScoreElasticity.text = getString(R.string.score_format, entity.elasticityScore)
    }

    private fun updateMiniChart(recentEntries: List<DiagnosisEntity>) {
        if (recentEntries.size < 2) {
            binding.miniLineChart.setNoDataText("기록이 더 필요해요")
            binding.miniLineChart.invalidate()
            return
        }

        val entries = recentEntries.mapIndexed { index, entity ->
            val score = (entity.moistureScore + (100 - entity.oilScore) + (100 - entity.troubleScore) + entity.elasticityScore) / 4
            Entry(index.toFloat(), score.toFloat())
        }

        val dataSet = LineDataSet(entries, "Total Score").apply {
            color = Color.WHITE
            setCircleColor(Color.WHITE)
            lineWidth = 2f
            circleRadius = 3f
            setDrawValues(false)
            mode = LineDataSet.Mode.CUBIC_BEZIER
            setDrawFilled(true)
            fillColor = Color.WHITE
            fillAlpha = 30
        }

        binding.miniLineChart.data = LineData(dataSet)
        binding.miniLineChart.invalidate()
    }

    private fun updateAiInsight(latest: DiagnosisEntity) {
        val insight = when {
            latest.troubleScore > 15 -> "트러블이 많이 감지되었어요. 진정 성분이 포함된 시카 제품을 사용해보세요."
            latest.moistureScore < 50 -> "피부가 많이 건조해요. 오늘은 수분 팩으로 보습에 집중하는 게 좋겠어요."
            latest.oilScore > 70 -> "유분기가 많은 편이에요. 꼼꼼한 세안 후 가벼운 수분 젤을 발라주세요."
            else -> "현재 피부 밸런스가 아주 좋아요! 지금 루틴을 잘 유지해주세요."
        }
        binding.tvAiInsight.text = insight
    }

    private fun loadRecentPhotos(entries: List<DiagnosisEntity>) {
        binding.layoutRecentPhotos.removeAllViews()
        val photos = entries.mapNotNull { it.photoPath.takeIf { p -> p.isNotEmpty() } }
        
        if (photos.isEmpty()) {
            val tv = TextView(requireContext()).apply {
                text = "최근 분석 사진이 없습니다."
                setTextColor(Color.GRAY)
                textSize = 12f
            }
            binding.layoutRecentPhotos.addView(tv)
            return
        }

        for (path in photos) {
            val iv = ImageView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(dpToPx(80), dpToPx(80)).apply {
                    setMargins(0, 0, dpToPx(8), 0)
                }
                scaleType = ImageView.ScaleType.CENTER_CROP
            }
            Glide.with(this)
                .load(path)
                .centerCrop()
                .transform(RoundedCorners(dpToPx(8)))
                .into(iv)
            binding.layoutRecentPhotos.addView(iv)
        }
    }

    private fun showEmpty() {
        binding.layoutDiagnosisContent.visibility = View.GONE
        binding.layoutDiagnosisEmpty.visibility = View.VISIBLE
        binding.tvDiagnosisDate.text = "최근 기록 없음"
        binding.tvTotalScoreBanner.text = "--"
        binding.miniLineChart.clear()
        binding.miniLineChart.setNoDataText("기록이 없습니다")
        binding.tvAiInsight.text = "최근 데이터가 없습니다. 분석을 시작해보세요!"
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
