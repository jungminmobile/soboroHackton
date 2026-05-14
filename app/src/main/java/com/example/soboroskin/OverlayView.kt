package com.example.soboroskin

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
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

    // 여드름 박스 — 빨간 실선
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

    // 얼굴 부위 곡선 — 반투명 채우기 + 흰 윤곽
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

    fun setResults(detections: List<AcneDetection>, imageWidth: Int, imageHeight: Int) {
        this.detections = detections
        this.faceContours = emptyList()
        this.imageWidth = imageWidth
        this.imageHeight = imageHeight
        invalidate()
    }

    fun setFaceContours(contours: List<FaceContour>, imageWidth: Int, imageHeight: Int) {
        this.faceContours = contours
        this.detections = emptyList()
        this.imageWidth = imageWidth
        this.imageHeight = imageHeight
        invalidate()
    }

    fun clear() {
        detections = emptyList()
        faceContours = emptyList()
        invalidate()
    }

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

            // 부위 이름 — 무게중심
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
        for (det in detections) {
            val scaled = RectF(
                det.bbox.left   * scale + offsetX,
                det.bbox.top    * scale + offsetY,
                det.bbox.right  * scale + offsetX,
                det.bbox.bottom * scale + offsetY
            )
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

    // 포인트 리스트를 smooth 닫힌 곡선 Path로 변환 (quadTo 중점 기법)
    private fun smoothClosedPath(pts: List<PointF>): Path {
        val path = Path()
        if (pts.size < 2) return path

        // 첫 시작점: 마지막 포인트와 첫 포인트의 중점
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
        name.contains("forehead")         -> "이마"
        name == "nose"                    -> "코"
        name == "left_cheek"              -> "왼볼"
        name == "right_cheek"             -> "오른볼"
        name == "left_jaw"                -> "왼턱"
        name == "right_jaw"               -> "오른턱"
        name.contains("mouth")            -> "입가"
        name == "chin"                    -> "턱"
        name.contains("eye")              -> "눈가"
        name == "face"                    -> "얼굴"
        else                              -> name
    }
}
