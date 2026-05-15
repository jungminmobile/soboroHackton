package com.example.soboroskin.ui.diary

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.sqrt

/**
 * 얼굴 지도 뷰 — partName별 트러블 상태 마커를 그리고 탭 이벤트를 전달한다.
 */
class FaceMapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    // ── 데이터 ─────────────────────────────────────────────────
    data class RegionMarker(
        val partName: String,
        val count: Int,
        val worstStatus: String   // "new" | "worsened" | "improved" | "existing" | "healed"
    )

    var regions: List<RegionMarker> = emptyList()
        set(value) { field = value; invalidate() }

    var onRegionClick: ((String) -> Unit)? = null

    companion object {
        // 상태 우선순위: worsened > new > existing > improved > healed
        private val STATUS_PRIORITY = listOf("worsened", "new", "existing", "improved", "healed")

        fun worstStatus(statuses: List<String>): String {
            if (statuses.isEmpty()) return "existing"
            return statuses.minByOrNull { STATUS_PRIORITY.indexOf(it).let { i -> if (i < 0) Int.MAX_VALUE else i } }
                ?: "existing"
        }

        // 상태 → 색상
        fun statusColor(status: String) = when (status) {
            "new"      -> Color.parseColor("#FF9800")
            "worsened" -> Color.parseColor("#E53935")
            "improved" -> Color.parseColor("#1D9E75")
            "healed"   -> Color.parseColor("#4CAF50")
            else       -> Color.parseColor("#9E9E9E")
        }
    }

    // ── 레이아웃 상수 (뷰 크기에 비례한 상대 좌표, 0..1) ───────
    // 얼굴 실루엣 및 각 부위 중심 — 비율 기준
    private data class RegionAnchor(val cx: Float, val cy: Float)

    private val regionAnchors: Map<String, RegionAnchor> = mapOf(
        "forehead"    to RegionAnchor(0.50f, 0.18f),
        "nose"        to RegionAnchor(0.50f, 0.46f),
        "left_cheek"  to RegionAnchor(0.25f, 0.52f),
        "right_cheek" to RegionAnchor(0.75f, 0.52f),
        "left_jaw"    to RegionAnchor(0.28f, 0.72f),
        "right_jaw"   to RegionAnchor(0.72f, 0.72f),
        "chin"        to RegionAnchor(0.50f, 0.80f),
        "mouth"       to RegionAnchor(0.50f, 0.64f),
        "eye"         to RegionAnchor(0.50f, 0.32f)
    )

    // ── Paint ───────────────────────────────────────────────────
    private val facePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F0EEE8")
        style = Paint.Style.FILL
    }
    private val faceStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#BDBDBD")
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val dotPaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style  = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color     = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize  = 28f
        typeface  = Typeface.DEFAULT_BOLD
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color     = Color.parseColor("#757575")
        textAlign = Paint.Align.CENTER
        textSize  = 26f
    }

    // ── 크기 ────────────────────────────────────────────────────
    private val dotRadius  = 36f   // px — 조정 가능
    private val viewHeight = 520f  // dp 단위 참고치; 실제는 MeasureSpec으로 결정

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        // 뷰의 가로:세로 = 1:1.1 비율로 유지
        val h = (w * 1.1f).toInt()
        setMeasuredDimension(w, h)
    }

    // ── 그리기 ──────────────────────────────────────────────────
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()

        drawFaceSilhouette(canvas, w, h)
        drawRegionMarkers(canvas, w, h)
    }

    private fun drawFaceSilhouette(canvas: Canvas, w: Float, h: Float) {
        // 단순 타원 실루엣
        val faceRect = RectF(w * 0.15f, h * 0.04f, w * 0.85f, h * 0.96f)
        canvas.drawOval(faceRect, facePaint)
        canvas.drawOval(faceRect, faceStrokePaint)

        // 눈 (좌/우 타원)
        val eyeY   = h * 0.32f
        val eyeHW  = w * 0.09f
        val eyeHH  = h * 0.04f
        canvas.drawOval(RectF(w * 0.28f - eyeHW, eyeY - eyeHH, w * 0.28f + eyeHW, eyeY + eyeHH), faceStrokePaint)
        canvas.drawOval(RectF(w * 0.72f - eyeHW, eyeY - eyeHH, w * 0.72f + eyeHW, eyeY + eyeHH), faceStrokePaint)

        // 코 (삼각형 힌트)
        val nosePath = Path().apply {
            moveTo(w * 0.50f, h * 0.40f)
            lineTo(w * 0.44f, h * 0.52f)
            lineTo(w * 0.56f, h * 0.52f)
            close()
        }
        canvas.drawPath(nosePath, faceStrokePaint)

        // 입
        val mouthPath = Path().apply {
            moveTo(w * 0.38f, h * 0.63f)
            quadTo(w * 0.50f, h * 0.70f, w * 0.62f, h * 0.63f)
        }
        canvas.drawPath(mouthPath, faceStrokePaint)
    }

    private fun drawRegionMarkers(canvas: Canvas, w: Float, h: Float) {
        // 데이터 없는 부위는 회색 작은 점으로 표시
        regionAnchors.forEach { (partName, anchor) ->
            val marker = regions.find { it.partName == partName }
            val cx = anchor.cx * w
            val cy = anchor.cy * h

            if (marker != null && marker.count > 0) {
                val color = statusColor(marker.worstStatus)
                // 반투명 링
                ringPaint.color = color
                ringPaint.alpha = 80
                canvas.drawCircle(cx, cy, dotRadius + 10f, ringPaint)
                ringPaint.alpha = 255

                // 채워진 원
                dotPaint.color = color
                canvas.drawCircle(cx, cy, dotRadius, dotPaint)

                // 카운트 텍스트
                textPaint.textSize = dotRadius * 0.85f
                canvas.drawText(marker.count.toString(), cx, cy + textPaint.textSize * 0.35f, textPaint)
            } else {
                // 빈 부위 — 작은 회색 점
                dotPaint.color = Color.parseColor("#DDD8CE")
                canvas.drawCircle(cx, cy, 10f, dotPaint)
            }
        }
    }

    // ── 터치 ────────────────────────────────────────────────────
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_UP) return true
        val w = width.toFloat()
        val h = height.toFloat()
        val tx = event.x
        val ty = event.y

        // 마커가 있는 부위 중 탭 위치와 가장 가까운 것 찾기
        val touchRadius = dotRadius + 20f
        for ((partName, anchor) in regionAnchors) {
            val cx = anchor.cx * w
            val cy = anchor.cy * h
            val dx = tx - cx
            val dy = ty - cy
            val dist = sqrt(dx * dx + dy * dy)
            if (dist <= touchRadius) {
                val hasData = regions.any { it.partName == partName && it.count > 0 }
                if (hasData) {
                    onRegionClick?.invoke(partName)
                    return true
                }
            }
        }
        return true
    }
}
