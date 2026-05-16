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
        private const val ARG_IDS = "spotIds"

        fun newInstance(spotIds: List<Long>) = TroubleRegionSheet().apply {
            arguments = Bundle().also { it.putLongArray(ARG_IDS, spotIds.toLongArray()) }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = SheetTroubleRegionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val spotIds = arguments?.getLongArray(ARG_IDS)?.toList() ?: return
        val db = AppDatabase.getInstance(requireContext())

        binding.rvRegionSpots.layoutManager = LinearLayoutManager(requireContext())

        viewLifecycleOwner.lifecycleScope.launch {
            val items: List<SpotItem> = withContext(Dispatchers.IO) {
                spotIds.mapNotNull { id ->
                    val spot = db.acneSpotDao().getAllSpots().find { it.id == id } ?: return@mapNotNull null
                    val records = db.acneSpotRecordDao().getRecordsForSpot(id)
                    SpotItem(spot, records.lastOrNull(), records.size)
                }
            }

            // 부위명 없이 개수만 표시
            binding.tvRegionTitle.visibility = android.view.View.GONE
            binding.tvRegionCount.text = "트러블 ${items.size}개"

            val worstStatus = FaceMapView.worstStatus(
                items.map { it.latest?.changeType ?: if (it.spot.isHealed) "healed" else "existing" }
            )
            val dotColor = FaceMapView.statusColor(worstStatus)
            (binding.viewRegionColor.background as? GradientDrawable)?.setColor(dotColor)
                ?: run {
                    binding.viewRegionColor.background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL; setColor(dotColor)
                    }
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
