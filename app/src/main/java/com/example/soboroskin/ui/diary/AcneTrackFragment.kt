package com.example.soboroskin.ui.diary

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.soboroskin.AcneTracker
import com.example.soboroskin.R
import com.example.soboroskin.data.db.AppDatabase
import com.example.soboroskin.data.model.AcneSpotEntity
import com.example.soboroskin.data.model.AcneSpotRecordEntity
import com.example.soboroskin.databinding.FragmentAcneTrackBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AcneTrackFragment : Fragment() {

    private var _binding: FragmentAcneTrackBinding? = null
    private val binding get() = _binding!!

    private var allSpots: List<SpotWithRecord> = emptyList()

    /** true = 완치된 트러블 보기, false = 현재 트러블 보기 */
    private var showHealed = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAcneTrackBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val db = AppDatabase.getInstance(requireContext())

        // 부위 배지 탭 → 해당 부위 트러블 목록 시트
        binding.faceMapView.onRegionClick = { spotIds ->
            TroubleRegionSheet.newInstance(spotIds)
                .show(parentFragmentManager, TroubleRegionSheet.TAG)
        }

        // 현재 트러블 카드 탭
        binding.cardActive.setOnClickListener {
            if (showHealed) {
                showHealed = false
                updateCardSelection()
                applyFilter()
            }
        }

        // 완치된 트러블 카드 탭
        binding.cardHealed.setOnClickListener {
            if (!showHealed) {
                showHealed = true
                updateCardSelection()
                applyFilter()
            }
        }

        // 초기 선택 상태 반영
        updateCardSelection()

        // 스팟 데이터 관찰
        viewLifecycleOwner.lifecycleScope.launch {
            db.acneSpotDao().getAllSpotsFlow().collectLatest { spots ->
                val spotWithRecords = withContext(Dispatchers.IO) {
                    spots.map { spot ->
                        val records = db.acneSpotRecordDao().getRecordsForSpot(spot.id)
                        SpotWithRecord(spot, records.lastOrNull(), records.size)
                    }
                }
                allSpots = spotWithRecords

                binding.tvActiveCount.text = spots.count { !it.isHealed }.toString()
                binding.tvHealedCount.text = spots.count { it.isHealed }.toString()

                applyFilter()
            }
        }
    }

    /** 선택된 카드에 테두리를 강조, 비선택 카드는 테두리 없음 */
    private fun updateCardSelection() {
        val ctx = requireContext()
        val primaryColor   = ContextCompat.getColor(ctx, R.color.primary)
        val secondaryColor = ContextCompat.getColor(ctx, R.color.text_secondary)
        val strokePx       = resources.getDimensionPixelSize(R.dimen.card_stroke_selected)

        if (showHealed) {
            binding.cardActive.strokeWidth  = 0
            binding.cardHealed.strokeWidth  = strokePx
            binding.cardHealed.strokeColor  = primaryColor
            binding.tvActiveLabel.setTextColor(secondaryColor)
            binding.tvHealedLabel.setTextColor(primaryColor)
        } else {
            binding.cardActive.strokeWidth  = strokePx
            binding.cardActive.strokeColor  = primaryColor
            binding.cardHealed.strokeWidth  = 0
            binding.tvActiveLabel.setTextColor(primaryColor)
            binding.tvHealedLabel.setTextColor(secondaryColor)
        }
    }

    private fun applyFilter() {
        val filtered = if (showHealed) {
            allSpots.filter { it.spot.isHealed }
        } else {
            allSpots.filter { !it.spot.isHealed }
        }

        val isEmpty = filtered.isEmpty()
        binding.layoutEmpty.visibility  = if (isEmpty) View.VISIBLE else View.GONE

        val diagramVisible = !isEmpty
        binding.faceMapView.visibility  = if (diagramVisible) View.VISIBLE else View.INVISIBLE
        binding.layoutLegend.visibility = if (diagramVisible) View.VISIBLE else View.GONE
        binding.tvTapHint.visibility    = if (diagramVisible) View.VISIBLE else View.GONE

        binding.faceMapView.regions = clusterSpots(filtered)
    }

    /**
     * 개별 스팟을 각자의 실제 좌표에 배지로 표시 (방안 A).
     * 단, 배지가 시각적으로 겹쳐 탭하기 어려울 정도로 가까운 스팟만 통합.
     *
     * threshold: 정규화 좌표 기준.
     *   배지 반경 = 18dp, 360dp 스크린 기준 ≈ 0.05
     *   두 배지가 겹치는 거리 = 2 × 0.05 = 0.10
     *   → 0.08 이내(거의 겹침)일 때만 통합, 그 외는 모두 개별 배지
     */
    private fun clusterSpots(
        spots: List<SpotWithRecord>,
        threshold: Float = 0.08f
    ): List<FaceMapView.RegionMarker> {

        data class Cluster(
            val members: MutableList<SpotWithRecord>,
            var cx: Float,
            var cy: Float
        )

        val clusters = mutableListOf<Cluster>()

        // 위쪽(y 작은 순) → 왼쪽 순으로 정렬해 순서 의존성 최소화
        val sorted = spots.sortedWith(compareBy({ it.spot.normalizedCy }, { it.spot.normalizedCx }))

        for (swr in sorted) {
            val sx = swr.spot.normalizedCx
            val sy = swr.spot.normalizedCy

            // 가장 가까운 클러스터 탐색
            val nearest = clusters.minByOrNull { c -> dist(c.cx, c.cy, sx, sy) }

            if (nearest != null && dist(nearest.cx, nearest.cy, sx, sy) < threshold) {
                // 기존 클러스터에 합치고 centroid 갱신
                nearest.members.add(swr)
                nearest.cx = nearest.members.map { it.spot.normalizedCx }.average().toFloat()
                nearest.cy = nearest.members.map { it.spot.normalizedCy }.average().toFloat()
            } else {
                // 새 클러스터 생성
                clusters.add(Cluster(mutableListOf(swr), sx, sy))
            }
        }

        return clusters.map { cluster ->
            val statuses = cluster.members.map { swr ->
                when {
                    swr.spot.isHealed -> "healed"
                    else -> swr.latestRecord?.changeType ?: "existing"
                }
            }
            // 클러스터 내 가장 많은 partName을 대표로 사용 (탭 시 시트 조회용)
            val partName = cluster.members
                .groupBy { AcneTracker.normalizePartName(it.spot.partName) }
                .maxByOrNull { it.value.size }!!.key

            FaceMapView.RegionMarker(
                partName    = partName,
                count       = cluster.members.size,
                worstStatus = FaceMapView.worstStatus(statuses),
                cx          = cluster.cx,
                cy          = cluster.cy,
                spotIds     = cluster.members.map { it.spot.id }
            )
        }
    }

    private fun dist(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x1 - x2; val dy = y1 - y2
        return kotlin.math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

data class SpotWithRecord(
    val spot: AcneSpotEntity,
    val latestRecord: AcneSpotRecordEntity?,
    val observationCount: Int
)
