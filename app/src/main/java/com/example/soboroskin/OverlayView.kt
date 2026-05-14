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

    // 여드름 감지 박스
    private var detections: List<AcneDetection> = emptyList()
    // 얼굴 부위 영역 (name, rect in image coords)
    private var faceRegions: List<Pair<String, RectF>> = emptyList()

    private var imageWidth: Int = 1
    private var imageHeight: Int = 1

    // 여드름 박스 — 빨간 실선
    private val acnePaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 3f
        isAntiAlias = true
    }

    // 얼굴 부위 — 흰색 점선
    private val regionPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2f
        isAntiAlias = true
        pathEffect = DashPathEffect(floatArrayOf(12f, 6f), 0f)
    }

    private val labelPaint = Paint().apply {
        color = Color.WHITE
        textSize = 24f
        isAntiAlias = true
        isFakeBoldText = true
    }

    private val labelBgPaint = Paint().apply {
        color = Color.argb(140, 0, 0, 0)
        style = Paint.Style.FILL
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

    fun setResults(detections: List<AcneDetection>, imageWidth: Int, imageHeight: Int) {
        this.detections = detections
        this.faceRegions = emptyList()
        this.imageWidth = imageWidth
        this.imageHeight = imageHeight
        invalidate()
    }

    fun setFaceRegions(regions: List<Pair<String, RectF>>, imageWidth: Int, imageHeight: Int) {
        this.faceRegions = regions
        this.detections = emptyList()
        this.imageWidth = imageWidth
        this.imageHeight = imageHeight
        invalidate()
    }

    fun clear() {
        detections = emptyList()
        faceRegions = emptyList()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (detections.isEmpty() && faceRegions.isEmpty()) return

        val scale = minOf(width.toFloat() / imageWidth, height.toFloat() / imageHeight)
        val offsetX = (width  - imageWidth  * scale) / 2f
        val offsetY = (height - imageHeight * scale) / 2f

        // 얼굴 부위 점선
        for ((name, rect) in faceRegions) {
            val scaled = RectF(
                rect.left   * scale + offsetX,
                rect.top    * scale + offsetY,
                rect.right  * scale + offsetX,
                rect.bottom * scale + offsetY
            )
            canvas.drawRect(scaled, regionPaint)

            // 부위 이름 라벨
            val label = partKorean(name)
            val textX = scaled.left + 4f
            val textY = scaled.top + labelPaint.textSize + 2f
            canvas.drawRect(textX - 2f, textY - labelPaint.textSize - 2f,
                textX + labelPaint.measureText(label) + 6f, textY + 4f, labelBgPaint)
            canvas.drawText(label, textX, textY, labelPaint)
        }

        // 여드름 박스
        for (det in detections) {
            val scaled = RectF(
                det.bbox.left   * scale + offsetX,
                det.bbox.top    * scale + offsetY,
                det.bbox.right  * scale + offsetX,
                det.bbox.bottom * scale + offsetY
            )
            canvas.drawRect(scaled, acnePaint)

            val label = "${(det.confidence * 100).toInt()}%"
            val textX = scaled.left
            val textY = (scaled.top - 5f).coerceAtLeast(acneTextPaint.textSize)
            canvas.drawRect(textX, textY - acneTextPaint.textSize,
                textX + acneTextPaint.measureText(label) + 8f, textY + 4f, acneBgPaint)
            canvas.drawText(label, textX + 4f, textY, acneTextPaint)
        }
    }

    private fun partKorean(name: String) = when {
        name.contains("forehead") -> "이마"
        name.contains("nose")     -> "코"
        name.contains("left_cheek")  -> "왼볼"
        name.contains("right_cheek") -> "오른볼"
        name.contains("left_jaw")    -> "왼턱"
        name.contains("right_jaw")   -> "오른턱"
        name.contains("mouth")    -> "입가"
        name.contains("chin")     -> "턱"
        name.contains("eye")      -> "눈가"
        name == "face"            -> "얼굴"
        else -> name
    }
}
