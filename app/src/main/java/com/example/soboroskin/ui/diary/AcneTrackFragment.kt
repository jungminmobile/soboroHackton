package com.example.soboroskin.ui.diary

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.soboroskin.data.db.AppDatabase
import com.example.soboroskin.data.model.AcneSpotEntity
import com.example.soboroskin.data.model.AcneSpotRecordEntity
import com.example.soboroskin.databinding.FragmentAcneTrackBinding
import com.example.soboroskin.databinding.ItemAcneSpotBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class AcneTrackFragment : Fragment() {

    private var _binding: FragmentAcneTrackBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: AcneSpotAdapter
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
        adapter = AcneSpotAdapter(db, viewLifecycleOwner.lifecycleScope) { spotId ->
            AcneSpotDetailSheet.newInstance(spotId)
                .show(parentFragmentManager, AcneSpotDetailSheet.TAG)
        }

        binding.rvAcneSpots.layoutManager = LinearLayoutManager(requireContext())
        binding.rvAcneSpots.adapter = adapter

        // 필터 칩
        binding.chipGroupFilter.setOnCheckedStateChangeListener { _, _ ->
            applyFilter()
        }

        observeSpots(db)
    }

    private fun observeSpots(db: AppDatabase) {
        viewLifecycleOwner.lifecycleScope.launch {
            db.acneSpotDao().getAllSpotsFlow().collectLatest { spots ->
                val spotWithRecords = spots.map { spot ->
                    val records = db.acneSpotRecordDao().getRecordsForSpot(spot.id)
                    val latest = records.lastOrNull()
                    SpotWithRecord(spot, latest, records.size)
                }
                allSpots = spotWithRecords

                val activeCount = spots.count { !it.isHealed }
                val healedCount = spots.count { it.isHealed }
                binding.tvActiveCount.text = activeCount.toString()
                binding.tvHealedCount.text = healedCount.toString()

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
        adapter.submitList(filtered)
        binding.layoutEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        binding.rvAcneSpots.visibility = if (filtered.isEmpty()) View.GONE else View.VISIBLE
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

class AcneSpotAdapter(
    private val db: AppDatabase,
    private val scope: CoroutineScope,
    private val onItemClick: (Long) -> Unit
) : RecyclerView.Adapter<AcneSpotAdapter.ViewHolder>() {

    private var items: List<SpotWithRecord> = emptyList()

    fun submitList(list: List<SpotWithRecord>) {
        items = list
        notifyDataSetChanged()
    }

    inner class ViewHolder(val binding: ItemAcneSpotBinding) :
        RecyclerView.ViewHolder(binding.root) {
        var thumbJob: Job? = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAcneSpotBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val (spot, latest, count) = items[position]
        val b = holder.binding
        val sdf = SimpleDateFormat("yy.MM.dd", Locale.getDefault())

        // 기본 텍스트 바인딩
        b.tvSpotPart.text = partKorean(spot.partName)
        b.tvSpotDates.text = "발견 ${sdf.format(Date(spot.firstSeenDate))}  최근 ${sdf.format(Date(spot.lastSeenDate))}"
        b.tvSpotHistory.text = "${count}회 관측"

        // 신뢰도 바
        val confPct = ((latest?.confidence ?: 0f) * 100).roundToInt()
        b.pbMiniConfidence.progress = confPct
        b.tvSpotConfidence.text = if ((latest?.confidence ?: 0f) > 0f) "${confPct}%" else "-"

        // 상태 배지
        val changeType = latest?.changeType ?: "existing"
        val (label, color) = statusDisplay(spot.isHealed, changeType)
        b.tvStatusBadge.text = label
        b.tvStatusBadge.setTextColor(Color.parseColor(color))
        b.viewStatusDot.setBackgroundColor(Color.parseColor(color))

        // 클릭 → 상세 시트
        holder.itemView.setOnClickListener { onItemClick(spot.id) }

        // 썸네일 비동기 로드
        holder.thumbJob?.cancel()
        b.ivSpotThumb.setImageBitmap(null)
        b.tvThumbPlaceholder.visibility = View.GONE

        if (latest == null) {
            b.tvThumbPlaceholder.visibility = View.VISIBLE
            return
        }

        holder.thumbJob = scope.launch {
            val thumb = loadThumbnail(latest)
            withContext(Dispatchers.Main) {
                if (thumb != null) {
                    b.ivSpotThumb.setImageBitmap(thumb)
                    b.tvThumbPlaceholder.visibility = View.GONE
                } else {
                    b.tvThumbPlaceholder.visibility = View.VISIBLE
                }
            }
        }
    }

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        holder.thumbJob?.cancel()
        holder.thumbJob = null
    }

    private suspend fun loadThumbnail(record: AcneSpotRecordEntity): Bitmap? =
        withContext(Dispatchers.IO) {
            try {
                val diagnosis = db.diagnosisDao().getById(record.diagnosisId) ?: return@withContext null
                val path = diagnosis.photoPath
                if (path.isEmpty() || !File(path).exists()) return@withContext null

                val full = BitmapFactory.decodeFile(path) ?: return@withContext null

                // 정규화 좌표 → 픽셀 좌표로 변환 후 여드름 주변 잘라내기
                val iw = full.width.toFloat()
                val ih = full.height.toFloat()

                val cx = record.normalizedCx * iw
                val cy = record.normalizedCy * ih
                val hw = record.normalizedWidth  * iw * 1.5f   // 1.5× 패딩
                val hh = record.normalizedHeight * ih * 1.5f

                val left   = max(0f, cx - hw).toInt()
                val top    = max(0f, cy - hh).toInt()
                val right  = min(iw, cx + hw).toInt()
                val bottom = min(ih, cy + hh).toInt()

                val cropW = right - left
                val cropH = bottom - top
                if (cropW <= 0 || cropH <= 0) return@withContext null

                val cropped = Bitmap.createBitmap(full, left, top, cropW, cropH)
                // 72dp 크기로 리샘플
                val size = 216  // 72dp × 3 density
                Bitmap.createScaledBitmap(cropped, size, size, true)
            } catch (t: Throwable) {
                null
            }
        }

    private fun statusDisplay(healed: Boolean, changeType: String): Pair<String, String> = when {
        healed                   -> "완치" to "#4CAF50"
        changeType == "new"      -> "신규" to "#FF9800"
        changeType == "worsened" -> "악화" to "#E53935"
        changeType == "improved" -> "호전" to "#1D9E75"
        changeType == "healed"   -> "완치" to "#4CAF50"
        else                     -> "유지" to "#757575"
    }

    private fun partKorean(name: String) = when {
        name.contains("forehead")    -> "이마"
        name.contains("nose")        -> "코"
        name.contains("left_cheek")  -> "왼볼"
        name.contains("right_cheek") -> "오른볼"
        name.contains("left_jaw")    -> "왼턱"
        name.contains("right_jaw")   -> "오른턱"
        name.contains("mouth")       -> "입가"
        name.contains("chin")        -> "턱"
        name.contains("eye")         -> "눈가"
        name == "face"               -> "얼굴"
        else                         -> name
    }
}
