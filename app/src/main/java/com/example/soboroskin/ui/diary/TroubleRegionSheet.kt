package com.example.soboroskin.ui.diary

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.soboroskin.AcneTracker
import com.example.soboroskin.data.db.AppDatabase
import com.example.soboroskin.data.model.AcneSpotEntity
import com.example.soboroskin.data.model.AcneSpotRecordEntity
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * 특정 얼굴 부위의 트러블 목록을 보여주는 바텀시트.
 * 각 항목 탭 → AcneSpotDetailSheet 열기
 */
class TroubleRegionSheet : BottomSheetDialogFragment() {

    companion object {
        const val TAG = "TroubleRegionSheet"
        private const val ARG_PART = "part_name"

        fun newInstance(partName: String) = TroubleRegionSheet().apply {
            arguments = Bundle().apply { putString(ARG_PART, partName) }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setPadding(0, 24, 0, 48)
        }

        // 핸들
        val handle = View(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(80.dp, 5.dp).also { it.gravity = android.view.Gravity.CENTER_HORIZONTAL; it.topMargin = 8.dp; it.bottomMargin = 16.dp }
            setBackgroundColor(Color.parseColor("#E0E0E0"))
            // 둥글게
        }
        root.addView(handle)

        // 제목
        val partName = arguments?.getString(ARG_PART) ?: ""
        val title = TextView(requireContext()).apply {
            text = "${partKorean(partName)} 트러블 목록"
            textSize = 16f
            setTextColor(Color.parseColor("#1A1A1A"))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(48.dp, 0, 48.dp, 12.dp)
        }
        root.addView(title)

        // RecyclerView
        val rv = RecyclerView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            layoutManager = LinearLayoutManager(requireContext())
        }
        root.addView(rv)

        val adapter = RegionSpotAdapter { spotId ->
            AcneSpotDetailSheet.newInstance(spotId)
                .show(parentFragmentManager, AcneSpotDetailSheet.TAG)
        }
        rv.adapter = adapter

        // 데이터 로드
        lifecycleScope.launch {
            val db = AppDatabase.getInstance(requireContext())
            val spots = withContext(Dispatchers.IO) {
                db.acneSpotDao().getAllSpots()
                    .filter { AcneTracker.normalizePartName(it.partName) == partName }
                    .map { spot ->
                        val records = db.acneSpotRecordDao().getRecordsForSpot(spot.id)
                        SpotWithRecord(spot, records.lastOrNull(), records.size)
                    }
                    .sortedWith(compareBy({ it.spot.isHealed }, { -it.spot.lastSeenDate }))
            }
            adapter.submitList(spots)
        }

        return root
    }

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()

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
}

// ── 어댑터 ────────────────────────────────────────────────────────
private class RegionSpotAdapter(
    private val onItemClick: (Long) -> Unit
) : ListAdapter<SpotWithRecord, RegionSpotAdapter.VH>(DiffCb()) {

    inner class VH(val row: LinearLayout) : RecyclerView.ViewHolder(row)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val ctx = parent.context
        val dp = { n: Int -> (n * ctx.resources.displayMetrics.density).toInt() }

        val dot = View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(dp(10), dp(10)).also { it.gravity = android.view.Gravity.CENTER_VERTICAL; it.marginEnd = dp(10) }
            setBackgroundColor(Color.GRAY)
        }
        val tvStatus = TextView(ctx).apply {
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(dp(48), ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        val tvPeriod = TextView(ctx).apply {
            textSize = 12f
            setTextColor(Color.parseColor("#9E9E9E"))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val tvCount = TextView(ctx).apply {
            textSize = 12f
            setTextColor(Color.parseColor("#757575"))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity     = android.view.Gravity.CENTER_VERTICAL
            setPadding(dp(24), dp(14), dp(24), dp(14))
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            addView(dot)
            addView(tvStatus)
            addView(tvPeriod)
            addView(tvCount)
        }
        return VH(row)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val (spot, latest, count) = getItem(position)
        val ctx   = holder.row.context
        val dp    = { n: Int -> (n * ctx.resources.displayMetrics.density).toInt() }
        val sdf   = SimpleDateFormat("yy.MM.dd", Locale.getDefault())

        val dot      = holder.row.getChildAt(0) as View
        val tvStatus = holder.row.getChildAt(1) as TextView
        val tvPeriod = holder.row.getChildAt(2) as TextView
        val tvCount  = holder.row.getChildAt(3) as TextView

        val changeType = latest?.changeType ?: "existing"
        val (label, color) = statusDisplay(spot.isHealed, changeType)
        tvStatus.text = label
        tvStatus.setTextColor(Color.parseColor(color))
        dot.setBackgroundColor(Color.parseColor(color))

        val days = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - spot.firstSeenDate)
        tvPeriod.text = if (spot.isHealed) "완치됨  ${sdf.format(Date(spot.lastSeenDate))}"
                        else "발견 후 ${days}일"
        tvCount.text = "${count}회 관측"

        holder.row.setOnClickListener { onItemClick(spot.id) }
    }

    private fun statusDisplay(healed: Boolean, changeType: String): Pair<String, String> = when {
        healed                   -> "완치" to "#4CAF50"
        changeType == "new"      -> "신규" to "#FF9800"
        changeType == "worsened" -> "악화" to "#E53935"
        changeType == "improved" -> "호전" to "#1D9E75"
        changeType == "healed"   -> "완치" to "#4CAF50"
        else                     -> "유지" to "#757575"
    }

    class DiffCb : DiffUtil.ItemCallback<SpotWithRecord>() {
        override fun areItemsTheSame(a: SpotWithRecord, b: SpotWithRecord) = a.spot.id == b.spot.id
        override fun areContentsTheSame(a: SpotWithRecord, b: SpotWithRecord) = a == b
    }
}
