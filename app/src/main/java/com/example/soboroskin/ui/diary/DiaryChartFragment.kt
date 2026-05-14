package com.example.soboroskin.ui.diary

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.soboroskin.data.db.AppDatabase
import com.example.soboroskin.data.model.DiagnosisEntity
import com.example.soboroskin.databinding.FragmentDiaryChartBinding
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class DiaryChartFragment : Fragment() {

    private var _binding: FragmentDiaryChartBinding? = null
    private val binding get() = _binding!!

    // DB에서 실시간으로 받는 전체 목록
    private var allEntries: List<DiagnosisEntity> = emptyList()
    private var dayRange = 7

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDiaryChartBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupChart()
        setupChipGroup()
        observeData()   // Flow로 실시간 관찰 (삭제 시 자동 갱신)
    }

    private fun setupChart() {
        binding.lineChart.apply {
            description.isEnabled = false
            setTouchEnabled(true)
            isDragEnabled = true
            setScaleEnabled(false)
            setPinchZoom(false)
            setDrawGridBackground(false)
            legend.isEnabled = true
            axisRight.isEnabled = false

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                granularity = 1f
                labelRotationAngle = -45f
            }

            axisLeft.apply {
                setDrawGridLines(true)
                axisMinimum = 0f
                axisMaximum = 100f
                granularity = 20f
            }
        }
    }

    private fun setupChipGroup() {
        binding.chip7days.setOnCheckedChangeListener { _, checked ->
            if (checked) { dayRange = 7; updateChart() }
        }
        binding.chip30days.setOnCheckedChangeListener { _, checked ->
            if (checked) { dayRange = 30; updateChart() }
        }
        binding.chip90days.setOnCheckedChangeListener { _, checked ->
            if (checked) { dayRange = 90; updateChart() }
        }
    }

    /** Flow를 collectLatest로 관찰 → 삭제/추가 즉시 차트 갱신 */
    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                AppDatabase.getInstance(requireContext())
                    .diagnosisDao()
                    .getAllEntries()
                    .collectLatest { entries ->
                        allEntries = entries
                        updateChart()
                    }
            }
        }
    }

    private fun updateChart() {
        if (_binding == null) return

        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(dayRange.toLong())
        val filtered = allEntries
            .filter { it.date >= cutoff && !it.isManual }
            .sortedBy { it.date }

        if (filtered.isEmpty()) {
            binding.lineChart.clear()
            binding.lineChart.setNoDataText(
                if (allEntries.isEmpty()) "아직 진단 기록이 없어요"
                else "이 기간에 진단 기록이 없어요"
            )
            binding.lineChart.invalidate()
            updateAverages(emptyList())
            return
        }

        val sdf = SimpleDateFormat("MM/dd", Locale.getDefault())
        val labels = filtered.map { sdf.format(Date(it.date)) }

        val moistureEntries   = filtered.mapIndexed { i, e -> Entry(i.toFloat(), e.moistureScore.toFloat()) }
        val oilEntries        = filtered.mapIndexed { i, e -> Entry(i.toFloat(), e.oilScore.toFloat()) }
        val troubleEntries    = filtered.mapIndexed { i, e -> Entry(i.toFloat(), e.troubleScore.toFloat()) }
        val elasticityEntries = filtered.mapIndexed { i, e -> Entry(i.toFloat(), e.elasticityScore.toFloat()) }

        fun makeDataSet(entries: List<Entry>, label: String, color: Int): LineDataSet =
            LineDataSet(entries, label).apply {
                this.color = color
                setCircleColor(color)
                lineWidth = 2f
                circleRadius = 3f
                setDrawValues(false)
                mode = LineDataSet.Mode.CUBIC_BEZIER
            }

        val lineData = LineData(
            makeDataSet(moistureEntries,   "💧 수분",   Color.parseColor("#4FC3F7")),
            makeDataSet(oilEntries,        "✨ 유분",   Color.parseColor("#FFB74D")),
            makeDataSet(troubleEntries,    "🔴 트러블", Color.parseColor("#EF5350")),
            makeDataSet(elasticityEntries, "💜 탄력",   Color.parseColor("#BA68C8"))
        )

        binding.lineChart.xAxis.valueFormatter = IndexAxisValueFormatter(labels)
        binding.lineChart.data = lineData
        binding.lineChart.invalidate()

        updateAverages(filtered)
    }

    private fun updateAverages(entries: List<DiagnosisEntity>) {
        if (_binding == null) return
        if (entries.isEmpty()) {
            binding.tvAvgMoisture.text   = "-"
            binding.tvAvgOil.text        = "-"
            binding.tvAvgTrouble.text    = "-"
            binding.tvAvgElasticity.text = "-"
            return
        }
        binding.tvAvgMoisture.text   = "${entries.map { it.moistureScore }.average().toInt()}점"
        binding.tvAvgOil.text        = "${entries.map { it.oilScore }.average().toInt()}점"
        binding.tvAvgTrouble.text    = "${entries.map { it.troubleScore }.average().toInt()}점"
        binding.tvAvgElasticity.text = "${entries.map { it.elasticityScore }.average().toInt()}점"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
