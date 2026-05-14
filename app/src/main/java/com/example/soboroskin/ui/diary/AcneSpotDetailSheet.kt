package com.example.soboroskin.ui.diary

import android.graphics.*
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.soboroskin.AcneDetection
import com.example.soboroskin.data.db.AppDatabase
import com.example.soboroskin.data.model.AcneSpotEntity
import com.example.soboroskin.data.model.AcneSpotRecordEntity
import com.example.soboroskin.databinding.FragmentAcneSpotDetailBinding
import com.example.soboroskin.databinding.ItemAcneHistoryBinding
import com.example.soboroskin.databinding.ItemAcneCropPhotoBinding
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class AcneSpotDetailSheet : BottomSheetDialogFragment() {

    private var _binding: FragmentAcneSpotDetailBinding? = null
    private val binding get() = _binding!!

    private var spotId: Long = 0L

    companion object {
        const val TAG = "AcneSpotDetailSheet"
        private const val ARG_SPOT_ID = "spot_id"
        fun newInstance(spotId: Long) = AcneSpotDetailSheet().apply {
            arguments = Bundle().apply { putLong(ARG_SPOT_ID, spotId) }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAcneSpotDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        spotId = arguments?.getLong(ARG_SPOT_ID) ?: return

        val db = AppDatabase.getInstance(requireContext())
        lifecycleScope.launch {
            val spot    = db.acneSpotDao().getAllSpots().find { it.id == spotId } ?: return@launch
            val records = db.acneSpotRecordDao().getRecordsForSpot(spotId)
            // healed 제외한 실제 감지 기록
            val activeRecords = records.filter { it.changeType != "healed" && it.confidence > 0f }

            withContext(Dispatchers.Main) {
                bindHeader(spot, records)
                bindCroppedPhotos(db, activeRecords)
                bindChart(records)
                bindFullPhoto(db, activeRecords.lastOrNull(), spot)
                bindHistory(records)
            }
        }
    }

    // ── 헤더 ──────────────────────────────────────────────────────
    private fun bindHeader(spot: AcneSpotEntity, records: List<AcneSpotRecordEntity>) {
        binding.tvDetailPart.text = partKorean(spot.partName)

        val days = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - spot.firstSeenDate)
        binding.tvDetailPeriod.text = if (spot.isHealed) "완치됨" else "발견 후 ${days}일째"

        val latest = records.lastOrNull()
        val (label, color) = statusDisplay(spot.isHealed, latest?.changeType ?: "existing")
        binding.tvDetailStatus.text = label
        binding.tvDetailStatus.setTextColor(Color.parseColor(color))
    }

    // ── 회차별 크롭 사진 (가로 스크롤) ───────────────────────────
    private fun bindCroppedPhotos(db: AppDatabase, records: List<AcneSpotRecordEntity>) {
        if (records.isEmpty()) return

        val adapter = CropPhotoAdapter(db, lifecycleScope, records)
        binding.rvCropPhotos.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvCropPhotos.adapter = adapter
    }

    // ── 전체 얼굴 사진 + 오버레이 ────────────────────────────────
    private suspend fun bindFullPhoto(
        db: AppDatabase,
        latestRecord: AcneSpotRecordEntity?,
        spot: AcneSpotEntity
    ) {
        if (latestRecord == null) {
            binding.tvNoPhoto.visibility = View.VISIBLE
            return
        }

        val diagnosis = db.diagnosisDao().getById(latestRecord.diagnosisId)
        val photoPath = diagnosis?.photoPath ?: ""

        if (photoPath.isEmpty() || !File(photoPath).exists()) {
            binding.tvNoPhoto.visibility = View.VISIBLE
            return
        }

        val bmp = withContext(Dispatchers.IO) { loadBitmap(photoPath) } ?: run {
            binding.tvNoPhoto.visibility = View.VISIBLE
            return
        }

        binding.ivDetailPhoto.setImageBitmap(bmp)

        val iw = bmp.width.toFloat()
        val ih = bmp.height.toFloat()
        val det = AcneDetection(
            left       = (latestRecord.normalizedCx - latestRecord.normalizedWidth  / 2f) * iw,
            top        = (latestRecord.normalizedCy - latestRecord.normalizedHeight / 2f) * ih,
            right      = (latestRecord.normalizedCx + latestRecord.normalizedWidth  / 2f) * iw,
            bottom     = (latestRecord.normalizedCy + latestRecord.normalizedHeight / 2f) * ih,
            confidence = latestRecord.confidence,
            part       = spot.partName
        )
        binding.overlayDetail.setResults(listOf(det), bmp.width, bmp.height)
    }

    // ── 신뢰도 차트 ───────────────────────────────────────────────
    private fun bindChart(records: List<AcneSpotRecordEntity>) {
        val activeRecords = records.filter { it.confidence > 0f }
        if (activeRecords.isEmpty()) {
            binding.chartConfidence.visibility = View.GONE
            return
        }

        val sdf = SimpleDateFormat("MM/dd", Locale.getDefault())
        val entries = activeRecords.mapIndexed { i, r ->
            Entry(i.toFloat(), (r.confidence * 100f).roundToInt().toFloat())
        }
        val labels = activeRecords.map { sdf.format(Date(it.date)) }

        val colorMap = mapOf(
            "new"      to Color.parseColor("#FF9800"),
            "worsened" to Color.parseColor("#E53935"),
            "improved" to Color.parseColor("#1D9E75"),
            "existing" to Color.parseColor("#9E9E9E"),
            "healed"   to Color.parseColor("#4CAF50")
        )
        val circleColors = activeRecords.map { colorMap[it.changeType] ?: Color.GRAY }

        val dataSet = LineDataSet(entries, "감지 신뢰도 (%)").apply {
            color = Color.parseColor("#1D9E75")
            lineWidth = 2f
            setCircleColors(circleColors)
            circleRadius = 5f
            setDrawCircleHole(false)
            setDrawValues(true)
            valueTextSize = 10f
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float) = "${value.toInt()}%"
            }
            mode = LineDataSet.Mode.CUBIC_BEZIER
            setDrawFilled(true)
            fillColor = Color.parseColor("#1D9E75")
            fillAlpha = 30
        }

        binding.chartConfidence.apply {
            data = LineData(dataSet)
            description.isEnabled = false
            legend.isEnabled = false
            setTouchEnabled(false)
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                granularity = 1f
                setDrawGridLines(false)
                textSize = 10f
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        val idx = value.toInt()
                        return if (idx in labels.indices) labels[idx] else ""
                    }
                }
            }
            axisLeft.apply {
                axisMinimum = 0f
                axisMaximum = 100f
                setDrawGridLines(true)
                textSize = 10f
            }
            axisRight.isEnabled = false
            animateX(600)
            invalidate()
        }
    }

    // ── 이력 목록 ─────────────────────────────────────────────────
    private fun bindHistory(records: List<AcneSpotRecordEntity>) {
        val sorted = records.sortedByDescending { it.date }
        binding.rvHistory.layoutManager = LinearLayoutManager(requireContext())
        binding.rvHistory.adapter = HistoryAdapter(sorted)
    }

    private fun loadBitmap(path: String): Bitmap? =
        try { BitmapFactory.decodeFile(path) } catch (t: Throwable) { null }

    private fun cropAcne(bmp: Bitmap, record: AcneSpotRecordEntity): Bitmap? {
        val iw = bmp.width.toFloat()
        val ih = bmp.height.toFloat()
        val cx = record.normalizedCx * iw
        val cy = record.normalizedCy * ih
        val hw = record.normalizedWidth  * iw * 1.8f
        val hh = record.normalizedHeight * ih * 1.8f

        val left   = max(0f, cx - hw).toInt()
        val top    = max(0f, cy - hh).toInt()
        val right  = min(iw, cx + hw).toInt()
        val bottom = min(ih, cy + hh).toInt()
        val cropW  = right - left
        val cropH  = bottom - top
        if (cropW <= 0 || cropH <= 0) return null

        return Bitmap.createBitmap(bmp, left, top, cropW, cropH)
    }

    private fun statusDisplay(healed: Boolean, changeType: String): Pair<String, String> = when {
        healed                    -> "완치" to "#4CAF50"
        changeType == "new"       -> "신규" to "#FF9800"
        changeType == "worsened"  -> "악화" to "#E53935"
        changeType == "improved"  -> "호전" to "#1D9E75"
        changeType == "healed"    -> "완치" to "#4CAF50"
        else                      -> "유지" to "#757575"
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
        else                         -> name
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ── 회차별 크롭 사진 어댑터 ──────────────────────────────────
    inner class CropPhotoAdapter(
        private val db: AppDatabase,
        private val scope: kotlinx.coroutines.CoroutineScope,
        private val items: List<AcneSpotRecordEntity>
    ) : RecyclerView.Adapter<CropPhotoAdapter.VH>() {

        inner class VH(val binding: ItemAcneCropPhotoBinding) :
            RecyclerView.ViewHolder(binding.root) {
            var job: Job? = null
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(ItemAcneCropPhotoBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val record = items[position]
            val b = holder.binding
            val sdf = SimpleDateFormat("MM.dd", Locale.getDefault())

            // 회차 표시 (1회, 2회...)
            b.tvCropDate.text = "${position + 1}회  ${sdf.format(Date(record.date))}"

            val (changeLabel, changeColor) = changeDisplay(record.changeType)
            b.tvCropChange.text = changeLabel
            b.tvCropChange.setTextColor(Color.parseColor(changeColor))

            // 크롭 사진 비동기 로드
            holder.job?.cancel()
            b.ivCropPhoto.setImageBitmap(null)
            b.tvCropPlaceholder.visibility = View.GONE

            holder.job = scope.launch {
                val cropped = withContext(Dispatchers.IO) {
                    try {
                        val diagnosis = db.diagnosisDao().getById(record.diagnosisId)
                            ?: return@withContext null
                        val path = diagnosis.photoPath
                        if (path.isEmpty() || !File(path).exists()) return@withContext null
                        val full = BitmapFactory.decodeFile(path) ?: return@withContext null
                        cropAcne(full, record)
                    } catch (t: Throwable) { null }
                }
                withContext(Dispatchers.Main) {
                    if (cropped != null) {
                        b.ivCropPhoto.setImageBitmap(cropped)
                    } else {
                        b.tvCropPlaceholder.visibility = View.VISIBLE
                    }
                }
            }
        }

        override fun onViewRecycled(holder: VH) {
            super.onViewRecycled(holder)
            holder.job?.cancel()
        }

        private fun changeDisplay(type: String): Pair<String, String> = when (type) {
            "new"      -> "신규 발견" to "#FF9800"
            "worsened" -> "↑ 악화"   to "#E53935"
            "improved" -> "↓ 호전"   to "#1D9E75"
            "healed"   -> "✓ 완치"   to "#4CAF50"
            else       -> "→ 유지"   to "#757575"
        }
    }

    // ── 이력 어댑터 ───────────────────────────────────────────────
    inner class HistoryAdapter(private val items: List<AcneSpotRecordEntity>) :
        RecyclerView.Adapter<HistoryAdapter.VH>() {

        inner class VH(val binding: ItemAcneHistoryBinding) :
            RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(ItemAcneHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val record = items[position]
            val b = holder.binding
            val sdf = SimpleDateFormat("yy.MM.dd", Locale.getDefault())

            b.tvHistoryDate.text = sdf.format(Date(record.date))

            val confPct = (record.confidence * 100).roundToInt()
            b.pbConfidence.progress = confPct
            b.tvConfidenceVal.text = if (record.confidence > 0f) "${confPct}%" else "-"

            val (label, color) = changeDisplay(record.changeType)
            b.tvHistoryChange.text = label
            b.tvHistoryChange.setTextColor(Color.parseColor(color))
            b.viewTimelineDot.setBackgroundColor(Color.parseColor(color))
        }

        private fun changeDisplay(type: String): Pair<String, String> = when (type) {
            "new"      -> "신규 발견" to "#FF9800"
            "worsened" -> "↑ 악화"   to "#E53935"
            "improved" -> "↓ 호전"   to "#1D9E75"
            "healed"   -> "✓ 완치"   to "#4CAF50"
            else       -> "→ 유지"   to "#757575"
        }
    }
}
