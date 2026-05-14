package com.example.soboroskin

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class OverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var detections: List<AcneDetection> = emptyList()
    private var faceContours: List<FaceContour> = emptyList()
    private var imageWidth: Int = 1
    private var imageHeight: Int = 1

    // 제외된(비선택) 인덱스
    private val deselectedIndices = mutableSetOf<Int>()

    // 선택 변경 콜백 (선택된 수, 전체 수)
    var onSelectionChanged: ((selected: Int, total: Int) -> Unit)? = null

    // ─── Paints ───────────────────────────────────────────────

    // 선택된 여드름 — 빨간 실선
    private val acneStrokePaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 3f
        isAntiAlias = true
    }
    private val acneTextPaint = Paint().apply {
        color = Color.RED
        textSize = 26f
        isAntiAlias = true
    }
    private val acneBgPaint = Paint().apply {
        color = Color.argb(120, 0, 0, 0)
        style = Paint.Style.FILL
    }

    // 제외된 여드름 — 회색 점선 + X
    private val deselectedStrokePaint = Paint().apply {
        color = Color.argb(160, 180, 180, 180)
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
        isAntiAlias = true
        pathEffect = DashPathEffect(floatArrayOf(8f, 6f), 0f)
    }
    private val deselectedFillPaint = Paint().apply {
        color = Color.argb(60, 0, 0, 0)
        style = Paint.Style.FILL
    }
    private val deselectedXPaint = Paint().apply {
        color = Color.argb(200, 220, 80, 80)
        style = Paint.Style.STROKE
        strokeWidth = 3f
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
    }
    private val deselectedTextPaint = Paint().apply {
        color = Color.argb(180, 180, 180, 180)
        textSize = 22f
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }

    // 얼굴 부위 곡선
    private val contourFillPaint = Paint().apply {
        color = Color.argb(35, 255, 255, 255)
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    private val contourStrokePaint = Paint().apply {
        color = Color.argb(200, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
        isAntiAlias = true
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val labelPaint = Paint().apply {
        color = Color.WHITE
        textSize = 22f
        isAntiAlias = true
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
    }
    private val labelBgPaint = Paint().apply {
        color = Color.argb(130, 0, 0, 0)
        style = Paint.Style.FILL
    }

    // ─── Public API ───────────────────────────────────────────

    fun setResults(detections: List<AcneDetection>, imageWidth: Int, imageHeight: Int) {
        this.detections = detections
        this.faceContours = emptyList()
        this.imageWidth = imageWidth
        this.imageHeight = imageHeight
        deselectedIndices.clear()
        isClickable = detections.isNotEmpty()
        invalidate()
        onSelectionChanged?.invoke(detections.size, detections.size)
    }

    fun setFaceContours(contours: List<FaceContour>, imageWidth: Int, imageHeight: Int) {
        this.faceContours = contours
        this.detections = emptyList()
        this.imageWidth = imageWidth
        this.imageHeight = imageHeight
        deselectedIndices.clear()
        isClickable = false
        invalidate()
    }

    fun clear() {
        detections = emptyList()
        faceContours = emptyList()
        deselectedIndices.clear()
        isClickable = false
        invalidate()
    }

    /** 현재 선택된(저장될) 여드름 목록 반환 */
    fun getSelectedDetections(): List<AcneDetection> =
        detections.filterIndexed { i, _ -> i !in deselectedIndices }

    /** 현재 선택된 수 */
    fun getSelectedCount(): Int = detections.size - deselectedIndices.size

    // ─── Touch ───────────────────────────────────────────────

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_UP) return true
        if (detections.isEmpty()) return true

        val scale = minOf(width.toFloat() / imageWidth, height.toFloat() / imageHeight)
        val offsetX = (width - imageWidth * scale) / 2f
        val offsetY = (height - imageHeight * scale) / 2f

        val touchX = event.x
        val touchY = event.y

        // 탭된 박스 찾기 (뒤에서부터 — 앞에 그려진 박스 우선)
        for (i in detections.indices.reversed()) {
            val det = detections[i]
            val scaledRect = RectF(
                det.left  * scale + offsetX,
                det.top   * scale + offsetY,
                det.right * scale + offsetX,
                det.bottom * scale + offsetY
            )
            // 탭 영역 12dp 확장
            val expand = 12f * resources.displayMetrics.density
            scaledRect.inset(-expand, -expand)

            if (scaledRect.contains(touchX, touchY)) {
                if (i in deselectedIndices) deselectedIndices.remove(i)
                else deselectedIndices.add(i)
                invalidate()
                val selected = detections.size - deselectedIndices.size
                onSelectionChanged?.invoke(selected, detections.size)
                return true
            }
        }
        return true
    }

    // ─── Draw ─────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (detections.isEmpty() && faceContours.isEmpty()) return

        val scale = minOf(width.toFloat() / imageWidth, height.toFloat() / imageHeight)
        val offsetX = (width  - imageWidth  * scale) / 2f
        val offsetY = (height - imageHeight * scale) / 2f

        // 얼굴 부위 곡선
        for (contour in faceContours) {
            val scaled = contour.points.map { pt ->
                PointF(pt.x * scale + offsetX, pt.y * scale + offsetY)
            }
            val path = smoothClosedPath(scaled)
            canvas.drawPath(path, contourFillPaint)
            canvas.drawPath(path, contourStrokePaint)

            val cx = scaled.map { it.x }.average().toFloat()
            val cy = scaled.map { it.y }.average().toFloat()
            val label = partKorean(contour.name)
            val tw = labelPaint.measureText(label)
            val th = labelPaint.textSize
            canvas.drawRoundRect(
                cx - tw / 2f - 6f, cy - th, cx + tw / 2f + 6f, cy + 6f,
                6f, 6f, labelBgPaint
            )
            canvas.drawText(label, cx, cy, labelPaint)
        }

        // 여드름 박스
        for ((i, det) in detections.withIndex()) {
            val scaled = RectF(
                det.left  * scale + offsetX,
                det.top   * scale + offsetY,
                det.right * scale + offsetX,
                det.bottom * scale + offsetY
            )

            if (i in deselectedIndices) {
                // ── 제외된 박스: 회색 점선 + 반투명 오버레이 + X
                canvas.drawRect(scaled, deselectedFillPaint)
                canvas.drawRect(scaled, deselectedStrokePaint)
                // X 대각선
                val pad = 10f
                canvas.drawLine(scaled.left + pad, scaled.top + pad,
                    scaled.right - pad, scaled.bottom - pad, deselectedXPaint)
                canvas.drawLine(scaled.right - pad, scaled.top + pad,
                    scaled.left + pad, scaled.bottom - pad, deselectedXPaint)
                // "제외" 텍스트
                val label = "제외"
                val cx = scaled.centerX()
                val cy = scaled.centerY() + deselectedTextPaint.textSize / 3f
                val tw = deselectedTextPaint.measureText(label)
                canvas.drawRoundRect(
                    cx - tw / 2f - 6f, cy - deselectedTextPaint.textSize - 2f,
                    cx + tw / 2f + 6f, cy + 4f,
                    4f, 4f, acneBgPaint
                )
                canvas.drawText(label, cx, cy, deselectedTextPaint)
            } else {
                // ── 선택된 박스: 빨간 실선 + 신뢰도
                canvas.drawRect(scaled, acneStrokePaint)
                val label = "${(det.confidence * 100).toInt()}%"
                val textX = scaled.left
                val textY = (scaled.top - 5f).coerceAtLeast(acneTextPaint.textSize)
                canvas.drawRect(
                    textX, textY - acneTextPaint.textSize,
                    textX + acneTextPaint.measureText(label) + 8f, textY + 4f,
                    acneBgPaint
                )
                canvas.drawText(label, textX + 4f, textY, acneTextPaint)
            }
        }
    }

    // ─── Helpers ─────────────────────────────────────────────

    private fun smoothClosedPath(pts: List<PointF>): Path {
        val path = Path()
        if (pts.size < 2) return path
        val startX = (pts.last().x + pts[0].x) / 2f
        val startY = (pts.last().y + pts[0].y) / 2f
        path.moveTo(startX, startY)
        for (i in pts.indices) {
            val curr = pts[i]
            val next = pts[(i + 1) % pts.size]
            val midX = (curr.x + next.x) / 2f
            val midY = (curr.y + next.y) / 2f
            path.quadTo(curr.x, curr.y, midX, midY)
        }
        path.close()
        return path
    }

    private fun partKorean(name: String) = when {
        name.contains("forehead") -> "이마"
        name == "nose"            -> "코"
        name == "left_cheek"      -> "왼볼"
        name == "right_cheek"     -> "오른볼"
        name == "left_jaw"        -> "왼턱"
        name == "right_jaw"       -> "오른턱"
        name.contains("mouth")   -> "입가"
        name == "chin"            -> "턱"
        name.contains("eye")     -> "눈가"
        name == "face"            -> "얼굴"
        else                      -> name
    }
}
