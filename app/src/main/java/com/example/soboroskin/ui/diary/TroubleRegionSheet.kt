package com.example.soboroskin.ui.diary

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.soboroskin.data.db.AppDatabase
import com.example.soboroskin.data.model.AcneSpotEntity
import com.example.soboroskin.data.model.AcneSpotRecordEntity
import com.example.soboroskin.databinding.ItemRegionSpotBinding
import com.example.soboroskin.databinding.SheetTroubleRegionBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class TroubleRegionSheet : BottomSheetDialogFragment() {

    private var _binding: SheetTroubleRegionBinding? = null
    private val binding get() = _binding!!

    companion object {
        const val TAG = "TroubleRegionSheet"
        private const val ARG_PART = "partName"

        fun newInstance(partName: String) = TroubleRegionSheet().apply {
            arguments = Bundle().also { it.putString(ARG_PART, partName) }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = SheetTroubleRegionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val partName = arguments?.getString(ARG_PART) ?: return
        val db = AppDatabase.getInstance(requireContext())

        binding.rvRegionSpots.layoutManager = LinearLayoutManager(requireContext())

        viewLifecycleOwner.lifecycleScope.launch {
            val spots: List<AcneSpotEntity> = withContext(Dispatchers.IO) {
                db.acneSpotDao().getAllSpots().filter {
                    FaceMapView.normalizePartNamePublic(it.partName) == partName
                }
            }

            val items: List<SpotItem> = withContext(Dispatchers.IO) {
                spots.map { spot ->
                    val records = db.acneSpotRecordDao().getRecordsForSpot(spot.id)
                    val latest = records.lastOrNull()
                    SpotItem(spot, latest, records.size)
                }
            }

            // 헤더 업데이트
            val title = FaceMapView.partKorean(partName)
            binding.tvRegionTitle.text = title
            binding.tvRegionCount.text = "${items.size}개"

            val worstStatus = FaceMapView.worstStatus(
                items.map { it.latest?.changeType ?: if (it.spot.isHealed) "healed" else "existing" }
            )
            val dotColor = FaceMapView.statusColor(worstStatus)
            (binding.viewRegionColor.background as? GradientDrawable)?.setColor(dotColor)
                ?: run {
                    val dot = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(dotColor)
                    }
                    binding.viewRegionColor.background = dot
                }

            binding.rvRegionSpots.adapter = RegionSpotAdapter(items) { spotId ->
                dismiss()
                AcneSpotDetailSheet.newInstance(spotId)
                    .show(parentFragmentManager, AcneSpotDetailSheet.TAG)
            }
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }

    // ── 내부 모델 / 어댑터 ───────────────────────────────────────

    data class SpotItem(
        val spot: AcneSpotEntity,
        val latest: AcneSpotRecordEntity?,
        val obsCount: Int
    )

    class RegionSpotAdapter(
        private val items: List<SpotItem>,
        private val onClick: (Long) -> Unit
    ) : RecyclerView.Adapter<RegionSpotAdapter.VH>() {

        private val sdf = SimpleDateFormat("yy.MM.dd", Locale.getDefault())

        inner class VH(val binding: ItemRegionSpotBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(ItemRegionSpotBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val (spot, latest, obsCount) = items[position]
            val b = holder.binding

            val first = sdf.format(Date(spot.firstSeenDate))
            val last  = sdf.format(Date(spot.lastSeenDate))
            b.tvSpotDateRange.text = if (first == last) first else "$first ~ $last"
            b.tvSpotObsCount.text  = "${obsCount}회 관측"

            val status = when {
                spot.isHealed -> "healed"
                else -> latest?.changeType ?: "existing"
            }
            val (label, color) = FaceMapView.statusLabel(status) to FaceMapView.statusColor(status)
            b.tvSpotStatus.text = label
            b.tvSpotStatus.setTextColor(color)

            val dot = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }
            b.viewStatusDot.background = dot

            b.root.setOnClickListener { onClick(spot.id) }
        }
    }
}

// AcneTracker와 FaceMapView 가 package-private이므로 여기서 노출
fun FaceMapView.Companion.normalizePartNamePublic(raw: String): String =
    com.example.soboroskin.AcneTracker.normalizePartName(raw)
