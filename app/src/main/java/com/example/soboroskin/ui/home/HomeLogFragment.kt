package com.example.soboroskin.ui.home

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import android.app.Dialog
import android.view.WindowManager
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.example.soboroskin.MainActivity
import com.example.soboroskin.R
import com.example.soboroskin.data.db.AppDatabase
import com.example.soboroskin.data.model.DiagnosisEntity
import com.example.soboroskin.databinding.FragmentHomeLogBinding
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs

class HomeLogFragment : Fragment() {

    private var _binding: FragmentHomeLogBinding? = null
    private val binding get() = _binding!!

    private var allEntries: List<DiagnosisEntity> = emptyList()
    private var currentIndex: Int = 0
    private var firstLoad: Boolean = true

    private var chartFilteredEntries: List<DiagnosisEntity> = emptyList()
    private var dayRange: Int = 30
    private var suppressChipListener = false

    private val dateFmt  = SimpleDateFormat("yy.MM.dd HH:mm", Locale.getDefault())
    private val chartFmt = SimpleDateFormat("MM/dd", Locale.getDefault())

    // ── Lifecycle ─────────────────────────────────────────────────────

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeLogBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupNavButtons()
        setupChart()
        setupChipGroup()
        setupJournal()
        observeEntries()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        firstLoad = true
        _binding = null
    }

    // ── 네비게이션 버튼 ────────────────────────────────────────────

    private fun setupNavButtons() {
        binding.btnPrev.setOnClickListener {
            if (currentIndex < allEntries.size - 1) {
                autoSaveJournal()
                currentIndex++
                showEntry(currentIndex)
            }
        }
        binding.btnNext.setOnClickListener {
            if (currentIndex > 0) {
                autoSaveJournal()
                currentIndex--
                showEntry(currentIndex)
            }
        }
        binding.btnDeleteEntry.setOnClickListener { showDeleteConfirmDialog() }
        binding.btnQuickScan.setOnClickListener {
            (activity as? MainActivity)?.openScanOverlay()
        }
        binding.btnQuickScanEmpty.setOnClickListener {
            (activity as? MainActivity)?.openScanOverlay()
        }
    }

    // ── 데이터 관찰 ────────────────────────────────────────────────

    private fun observeEntries() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                AppDatabase.getInstance(requireContext())
                    .diagnosisDao()
                    .getAllEntries()
                    .collectLatest { entries ->
                        allEntries = entries
                        if (entries.isEmpty()) {
                            showEmpty()
                            firstLoad = true
                        } else {
                            if (firstLoad) {
                                currentIndex = 0
                                firstLoad = false
                            } else {
                                currentIndex = currentIndex.coerceIn(0, entries.size - 1)
                            }
                            showEntry(currentIndex)
                        }
                    }
            }
        }
    }

    // ── 항목 표시 ──────────────────────────────────────────────────

    private fun showEntry(index: Int) {
        val entry     = allEntries.getOrNull(index) ?: return
        val prevEntry = allEntries.getOrNull(index + 1)

        binding.tvDateNav.text      = dateFmt.format(Date(entry.date))
        binding.tvEntryCounter.text = "${index + 1} / ${allEntries.size}"

        val canGoPrev = index < allEntries.size - 1
        val canGoNext = index > 0
        binding.btnPrev.alpha     = if (canGoPrev) 1f else 0.25f
        binding.btnPrev.isEnabled = canGoPrev
        binding.btnNext.alpha     = if (canGoNext) 1f else 0.25f
        binding.btnNext.isEnabled = canGoNext

        val moisture   = entry.moistureScore
        val dryness    = (100 - moisture).coerceIn(0, 100)
        val acne       = entry.troubleScore
        val pore       = entry.oilScore
        val elasticity = entry.elasticityScore

        binding.pentagonChart.scores = floatArrayOf(
            moisture.toFloat(),
            dryness.toFloat(),
            acne.toFloat(),
            pore.toFloat(),
            elasticity.toFloat()
        )

        binding.tvValMoisture.text   = "${moisture}점"
        binding.tvValDryness.text    = "${dryness}점"
        binding.tvValAcne.text       = "${acne}점"
        binding.tvValPore.text       = "${pore}점"
        binding.tvValElasticity.text = "${elasticity}점"

        if (prevEntry != null) {
            showDelta(binding.tvDeltaMoisture,   moisture   - prevEntry.moistureScore)
            showDelta(binding.tvDeltaDryness,    dryness    - (100 - prevEntry.moistureScore))
            showDelta(binding.tvDeltaAcne,       acne       - prevEntry.troubleScore)
            showDelta(binding.tvDeltaPore,       pore       - prevEntry.oilScore)
            showDelta(binding.tvDeltaElasticity, elasticity - prevEntry.elasticityScore)
        } else {
            listOf(
                binding.tvDeltaMoisture, binding.tvDeltaDryness,
                binding.tvDeltaAcne, binding.tvDeltaPore, binding.tvDeltaElasticity
            ).forEach { it.visibility = View.GONE }
        }

        // 사진 있으면 "보러가기" 카드 표시 → 클릭 시 사진 팝업
        if (entry.photoPath.isNotEmpty()) {
            binding.cardPhotoNotes.visibility = View.VISIBLE
            binding.cardPhotoNotes.setOnClickListener {
                showPhotoDialog(entry.photoPath)
            }
        } else {
            binding.cardPhotoNotes.visibility = View.GONE
        }

        val currentJournal = binding.etJournal.text?.toString() ?: ""
        if (currentJournal != entry.notes) {
            binding.etJournal.setText(entry.notes)
        }
        binding.tvJournalSaved.visibility = View.GONE

        binding.layoutHasData.visibility = View.VISIBLE
        binding.layoutEmpty.visibility   = View.GONE

        updateChart()
    }

    private fun showPhotoDialog(photoPath: String) {
        val dialog = Dialog(requireContext(), android.R.style.Theme_Black_NoTitleBar)

        val imageView = ImageView(requireContext()).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(android.graphics.Color.parseColor("#CC000000"))
            setOnClickListener { dialog.dismiss() }
        }

        Glide.with(this)
            .load(photoPath)
            .into(imageView)

        dialog.setContentView(imageView)
        dialog.window?.apply {
            setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT
            )
            setBackgroundDrawableResource(android.R.color.transparent)
        }
        dialog.show()
    }

    private fun showDelta(tv: TextView, delta: Int) {
        if (delta == 0) { tv.visibility = View.GONE; return }
        tv.visibility = View.VISIBLE
        tv.text = "${if (delta > 0) "▲" else "▼"}${abs(delta)}"
        tv.setTextColor(
            if (delta > 0) Color.parseColor("#4CAF50") else Color.parseColor("#EF5350")
        )
    }

    // ── 변화 그래프 ────────────────────────────────────────────────

    private fun setupChart() {
        binding.lineChart.apply {
            description.isEnabled = false
            setTouchEnabled(true)
            isDragEnabled = true
            setScaleEnabled(false)
            setPinchZoom(false)
            setDrawGridBackground(false)
            legend.isEnabled = true
            legend.textSize  = 11f
            axisRight.isEnabled = false
            setNoDataText("아직 진단 기록이 없어요")

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                granularity = 1f
                labelRotationAngle = -40f
                textSize = 10f
            }
            axisLeft.apply {
                setDrawGridLines(true)
                axisMinimum = 0f
                axisMaximum = 100f
                granularity = 25f
                textSize = 10f
            }
        }
    }

    private fun setupChipGroup() {
        binding.chipGroupPeriod.setOnCheckedStateChangeListener { _, checkedIds ->
            if (suppressChipListener) return@setOnCheckedStateChangeListener
            dayRange = when (checkedIds.firstOrNull()) {
                R.id.chip_7days  -> 7
                R.id.chip_30days -> 30
                R.id.chip_90days -> 90
                else -> dayRange
            }
            updateChart()
        }
    }

    private fun updateChart() {
        if (_binding == null) return

        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(dayRange.toLong())
        chartFilteredEntries = allEntries
            .filter { it.date >= cutoff }
            .sortedBy { it.date }

        val chart = binding.lineChart

        if (chartFilteredEntries.isEmpty()) {
            chart.clear()
            chart.setNoDataText(
                if (allEntries.isEmpty()) "아직 진단 기록이 없어요"
                else "이 기간에 진단 기록이 없어요"
            )
            chart.invalidate()
            updateAverages(emptyList())
            return
        }

        val labels = chartFilteredEntries.map { chartFmt.format(Date(it.date)) }

        fun makeSet(data: List<Entry>, label: String, hexColor: String): LineDataSet =
            LineDataSet(data, label).apply {
                color = Color.parseColor(hexColor)
                setCircleColor(Color.parseColor(hexColor))
                lineWidth = 2f
                circleRadius = 3.5f
                setDrawValues(false)
                mode = LineDataSet.Mode.CUBIC_BEZIER
                isHighlightEnabled = false
            }

        val lineData = LineData(
            makeSet(chartFilteredEntries.mapIndexed { i, e -> Entry(i.toFloat(), e.moistureScore.toFloat()) },   "수분",   "#4FC3F7"),
            makeSet(chartFilteredEntries.mapIndexed { i, e -> Entry(i.toFloat(), e.elasticityScore.toFloat()) }, "탄력",   "#BA68C8"),
            makeSet(chartFilteredEntries.mapIndexed { i, e -> Entry(i.toFloat(), e.oilScore.toFloat()) },        "모공",   "#4CAF50"),
            makeSet(chartFilteredEntries.mapIndexed { i, e -> Entry(i.toFloat(), e.troubleScore.toFloat()) },    "여드름", "#EF5350")
        )

        chart.xAxis.valueFormatter = IndexAxisValueFormatter(labels)
        chart.data = lineData

        chart.xAxis.removeAllLimitLines()
        val selectedEntry = allEntries.getOrNull(currentIndex)
        if (selectedEntry != null) {
            val chartPos = chartFilteredEntries.indexOfFirst { it.id == selectedEntry.id }
            if (chartPos >= 0) {
                val ll = LimitLine(chartPos.toFloat()).apply {
                    lineColor     = Color.parseColor("#6EBA93")
                    lineWidth     = 2f
                    enableDashedLine(10f, 6f, 0f)
                    label         = chartFmt.format(Date(selectedEntry.date))
                    labelPosition = LimitLine.LimitLabelPosition.RIGHT_TOP
                    textColor     = Color.parseColor("#4A9972")
                    textSize      = 9f
                }
                chart.xAxis.addLimitLine(ll)
            } else {
                autoSwitchPeriod(selectedEntry.date)
            }
        }

        chart.invalidate()
        updateAverages(chartFilteredEntries)
    }

    private fun autoSwitchPeriod(entryDate: Long) {
        val now = System.currentTimeMillis()
        val idealRange = when {
            entryDate >= now - TimeUnit.DAYS.toMillis(7)  -> 7
            entryDate >= now - TimeUnit.DAYS.toMillis(30) -> 30
            else -> 90
        }
        if (idealRange != dayRange) {
            dayRange = idealRange
            suppressChipListener = true
            when (idealRange) {
                7  -> binding.chip7days.isChecked  = true
                30 -> binding.chip30days.isChecked = true
                90 -> binding.chip90days.isChecked = true
            }
            suppressChipListener = false
            updateChart()
        }
    }

    private fun updateAverages(entries: List<DiagnosisEntity>) {
        if (_binding == null) return
        if (entries.isEmpty()) {
            binding.tvAvgMoisture.text   = "-"
            binding.tvAvgElasticity.text = "-"
            binding.tvAvgPore.text       = "-"
            binding.tvAvgAcne.text       = "-"
            return
        }
        binding.tvAvgMoisture.text   = "${entries.map { it.moistureScore }.average().toInt()}점"
        binding.tvAvgElasticity.text = "${entries.map { it.elasticityScore }.average().toInt()}점"
        binding.tvAvgPore.text       = "${entries.map { it.oilScore }.average().toInt()}점"
        binding.tvAvgAcne.text       = "${entries.map { it.troubleScore }.average().toInt()}점"
    }

    // ── 피부 일기 ─────────────────────────────────────────────────

    private fun setupJournal() {
        val tagChips = listOf(
            binding.chipTagSleepLate  to "😴 늦게 잠",
            binding.chipTagJunkFood   to "🍕 자극적 음식",
            binding.chipTagDrinkWater to "💧 물 많이 마심",
            binding.chipTagStress     to "😤 스트레스",
            binding.chipTagExercise   to "🏃 운동함",
            binding.chipTagAlcohol    to "🍺 음주"
        )
        for ((chip, tag) in tagChips) {
            chip.setOnClickListener {
                val current = binding.etJournal.text?.toString() ?: ""
                val newText = if (current.isEmpty()) tag else "$current  $tag"
                binding.etJournal.setText(newText)
                binding.etJournal.setSelection(newText.length)
                binding.tvJournalSaved.visibility = View.GONE
            }
        }
        binding.btnSaveJournal.setOnClickListener {
            saveJournal(showToast = true)
        }
    }

    private fun saveJournal(showToast: Boolean) {
        val entry = allEntries.getOrNull(currentIndex) ?: return
        val newText = binding.etJournal.text?.toString()?.trim() ?: ""
        if (newText == entry.notes) {
            if (showToast) binding.tvJournalSaved.visibility = View.VISIBLE
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                AppDatabase.getInstance(requireContext())
                    .diagnosisDao()
                    .update(entry.copy(notes = newText))
            }
            if (_binding != null) {
                binding.tvJournalSaved.visibility = View.VISIBLE
                if (showToast) {
                    Toast.makeText(requireContext(), "저장되었습니다", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun autoSaveJournal() {
        val entry = allEntries.getOrNull(currentIndex) ?: return
        val newText = binding.etJournal.text?.toString()?.trim() ?: ""
        if (newText == entry.notes) return
        viewLifecycleOwner.lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                AppDatabase.getInstance(requireContext())
                    .diagnosisDao()
                    .update(entry.copy(notes = newText))
            }
        }
    }

    // ── 빈 상태 ────────────────────────────────────────────────────

    private fun showEmpty() {
        binding.layoutHasData.visibility = View.GONE
        binding.layoutEmpty.visibility   = View.VISIBLE
    }

    // ── 삭제 ──────────────────────────────────────────────────────

    private fun showDeleteConfirmDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("기록 삭제")
            .setMessage("이 진단 기록을 삭제할까요?\n관련 트러블 기록도 함께 정리됩니다.")
            .setNegativeButton("취소", null)
            .setPositiveButton("삭제") { _, _ -> deleteCurrentEntry() }
            .show()
    }

    private fun deleteCurrentEntry() {
        val entry = allEntries.getOrNull(currentIndex) ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val db = AppDatabase.getInstance(requireContext())
                if (entry.photoPath.isNotEmpty()) {
                    try { File(entry.photoPath).delete() } catch (_: Exception) {}
                }
                db.acneSpotRecordDao().deleteByDiagnosisIds(listOf(entry.id))
                db.acneSpotDao().deleteOrphaned()
                db.diagnosisDao().deleteByIds(listOf(entry.id))
            }
            Toast.makeText(requireContext(), "삭제되었습니다", Toast.LENGTH_SHORT).show()
        }
    }
}
