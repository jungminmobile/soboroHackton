package com.example.soboroskin.ui.diary

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.example.soboroskin.R
import kotlin.math.sqrt

/**
 * 3D 얼굴 모델 이미지 위에 부위별 트러블 배지를 표시하는 커스텀 뷰.
 * 같은 partName 의 트러블은 하나의 배지로 통합된다.
 */
class FaceMapView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    // ── 데이터 모델 ──────────────────────────────────────────────
    data class RegionMarker(
        val partName: String,
        val count: Int,
        val worstStatus: String  // "new" | "worsened" | "improved" | "existing" | "healed"
    )

    // 얼굴 모델 이미지 위의 각 부위 중심 좌표 (이미지 기준 정규화 0~1)
    companion object {
        val PART_POSITIONS: Map<String, Pair<Float, Float>> = mapOf(
            "forehead"    to Pair(0.50f, 0.20f),
            "left_cheek"  to Pair(0.20f, 0.57f),
            "right_cheek" to Pair(0.80f, 0.57f),
            "nose"        to Pair(0.50f, 0.57f),
            "left_jaw"    to Pair(0.28f, 0.76f),
            "right_jaw"   to Pair(0.72f, 0.76f),
            "mouth"       to Pair(0.50f, 0.70f),
            "chin"        to Pair(0.50f, 0.83f),
            "eye"         to Pair(0.50f, 0.42f),
            "face"        to Pair(0.50f, 0.45f)
        )

        fun worstStatus(statuses: List<String>): String {
            val priority = listOf("worsened", "new", "existing", "improved", "healed")
            return priority.firstOrNull { it in statuses } ?: "existing"
        }

        fun statusColor(status: String): Int = when (status) {
            "new"      -> Color.parseColor("#FF9800")
            "worsened" -> Color.parseColor("#E53935")
            "improved" -> Color.parseColor("#1D9E75")
            "healed"   -> Color.parseColor("#4CAF50")
            else       -> Color.parseColor("#9E9E9E")
        }

        fun statusLabel(status: String): String = when (status) {
            "new"      -> "신규"
            "worsened" -> "악화"
            "improved" -> "호전"
            "healed"   -> "완치"
            else       -> "유지"
        }

        fun partKorean(name: String) = when {
            name.contains("forehead")    -> "이마"
            name.contains("left_cheek")  -> "왼볼"
            name.contains("right_cheek") -> "오른볼"
            name.contains("nose")        -> "코"
            name.contains("left_jaw")    -> "왼턱"
            name.contains("right_jaw")   -> "오른턱"
            name.contains("mouth")       -> "입가"
            name.contains("chin")        -> "턱"
            name.contains("eye")         -> "눈가"
            else                         -> "얼굴"
        }
    }

    // ── Paints ───────────────────────────────────────────────────
    private val dp = context.resources.displayMetrics.density
    private val BADGE_R = 18f * dp
    private val TOUCH_R = 26f * dp

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val badgeBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2.5f * dp; color = Color.WHITE
    }
    private val countTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; isFakeBoldText = true
        textAlign = Paint.Align.CENTER
    }

    // ── State ────────────────────────────────────────────────────
    private var faceBitmap: Bitmap? = null
    private var bitmapAspect = 1f  // height / width

    var regions: List<RegionMarker> = emptyList()
        set(value) { field = value; invalidate() }

    var highlightedPart: String? = null
        set(value) { field = value; invalidate() }

    var onRegionClick: ((String) -> Unit)? = null

    // ── Init ─────────────────────────────────────────────────────
    init {
        val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 }
        val bmp = BitmapFactory.decodeResource(resources, R.drawable.img_face_model, opts)
        if (bmp != null) {
            faceBitmap = bmp
            bitmapAspect = bmp.height.toFloat() / bmp.width
        }
    }

    // ── Measure: 이미지 비율 유지 ────────────────────────────────
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = (w * bitmapAspect).toInt().coerceAtLeast(1)
        setMeasuredDimension(w, h)
    }

    // ── Draw ─────────────────────────────────────────────────────
    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()

        val bitmap = faceBitmap
        if (bitmap != null) {
            // 이미지를 뷰에 꽉 차게 그리기 (비율 유지됨, onMeasure에서 이미 맞춰짐)
            canvas.drawBitmap(bitmap, null, RectF(0f, 0f, w, h), null)
        } else {
            canvas.drawColor(Color.parseColor("#2A2A2A"))
        }

        // 부위 배지 그리기
        for (marker in regions) {
            val (nx, ny) = PART_POSITIONS[marker.partName] ?: PART_POSITIONS["face"]!!
            val px = nx * w; val py = ny * h
            drawBadge(canvas, marker, px, py)
        }
    }

    private fun drawBadge(canvas: Canvas, marker: RegionMarker, px: Float, py: Float) {
        val color = statusColor(marker.worstStatus)
        val isHighlighted = marker.partName == highlightedPart

        // 글로우
        glowPaint.color = color
        glowPaint.alpha = if (isHighlighted) 100 else 55
        canvas.drawCircle(px, py, BADGE_R + 8f * dp, glowPaint)

        // 배지 배경
        badgePaint.color = color
        badgePaint.alpha = if (isHighlighted) 255 else 210
        canvas.drawCircle(px, py, BADGE_R, badgePaint)

        // 테두리
        canvas.drawCircle(px, py, BADGE_R, badgeBorderPaint)

        // 카운트 텍스트
        val label = marker.count.toString()
        countTextPaint.textSize = if (marker.count >= 10) 11f * dp else 13f * dp
        val ty = py - (countTextPaint.ascent() + countTextPaint.descent()) / 2f
        canvas.drawText(label, px, ty, countTextPaint)
    }

    // ── Touch ────────────────────────────────────────────────────
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            val tx = event.x; val ty = event.y
            val w = width.toFloat(); val h = height.toFloat()
            var closest: RegionMarker? = null
            var minDist = TOUCH_R
            for (marker in regions) {
                val (nx, ny) = PART_POSITIONS[marker.partName] ?: PART_POSITIONS["face"]!!
                val dist = sqrt(((tx - nx * w).let { it * it } + (ty - ny * h).let { it * it }).toDouble()).toFloat()
                if (dist < minDist) { minDist = dist; closest = marker }
            }
            closest?.let {
                highlightedPart = it.partName
                onRegionClick?.invoke(it.partName)
                performClick()
            }
        }
        return true
    }

    override fun performClick(): Boolean { super.performClick(); return true }
}
