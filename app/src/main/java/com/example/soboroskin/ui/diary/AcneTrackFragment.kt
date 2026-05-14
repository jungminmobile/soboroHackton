package com.example.soboroskin.ui.diary

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.soboroskin.AcneTracker
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
        binding.faceMapView.onRegionClick = { partName ->
            TroubleRegionSheet.newInstance(partName)
                .show(parentFragmentManager, TroubleRegionSheet.TAG)
        }

        // 필터 칩
        binding.chipGroupFilter.setOnCheckedStateChangeListener { _, _ ->
            applyFilter()
        }

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

    private fun applyFilter() {
        val filtered = when (binding.chipGroupFilter.checkedChipId) {
            com.example.soboroskin.R.id.chip_active -> allSpots.filter { !it.spot.isHealed }
            com.example.soboroskin.R.id.chip_healed -> allSpots.filter { it.spot.isHealed }
            else -> allSpots
        }

        val isEmpty = filtered.isEmpty()
        binding.layoutEmpty.visibility  = if (isEmpty) View.VISIBLE else View.GONE

        val diagramVisible = !isEmpty
        binding.faceMapView.visibility  = if (diagramVisible) View.VISIBLE else View.INVISIBLE
        binding.layoutLegend.visibility = if (diagramVisible) View.VISIBLE else View.GONE
        binding.tvTapHint.visibility    = if (diagramVisible) View.VISIBLE else View.GONE

        // ── 부위별 그룹화 ────────────────────────────────────────
        // partName 정규화 후 그룹화
        val grouped: Map<String, List<SpotWithRecord>> = filtered.groupBy { swr ->
            AcneTracker.normalizePartName(swr.spot.partName)
        }

        val regionMarkers = grouped.map { (partName, group) ->
            val statuses = group.map { swr ->
                when {
                    swr.spot.isHealed -> "healed"
                    else -> swr.latestRecord?.changeType ?: "existing"
                }
            }
            FaceMapView.RegionMarker(
                partName    = partName,
                count       = group.size,
                worstStatus = FaceMapView.worstStatus(statuses)
            )
        }

        binding.faceMapView.regions = regionMarkers
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
