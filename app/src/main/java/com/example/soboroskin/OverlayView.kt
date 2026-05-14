package com.example.soboroskin

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class OverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var detections: List<AcneDetection> = emptyList()
    private var imageWidth: Int = 1
    private var imageHeight: Int = 1

    private val boxPaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 3f
        isAntiAlias = true
    }

    private val textPaint = Paint().apply {
        color = Color.RED
        textSize = 28f
        isAntiAlias = true
    }

    private val bgPaint = Paint().apply {
        color = Color.argb(120, 0, 0, 0)
        style = Paint.Style.FILL
    }

    fun setResults(
        detections: List<AcneDetection>,
        imageWidth: Int,
        imageHeight: Int
    ) {
        this.detections = detections
        this.imageWidth = imageWidth
        this.imageHeight = imageHeight
        invalidate()
    }

    fun clear() {
        detections = emptyList()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (detections.isEmpty()) return

        // fitCenter 기준 스케일/오프셋 계산
        val scale = minOf(width.toFloat() / imageWidth, height.toFloat() / imageHeight)
        val offsetX = (width  - imageWidth  * scale) / 2f
        val offsetY = (height - imageHeight * scale) / 2f

        for (det in detections) {
            val scaledBox = RectF(
                det.bbox.left   * scale + offsetX,
                det.bbox.top    * scale + offsetY,
                det.bbox.right  * scale + offsetX,
                det.bbox.bottom * scale + offsetY
            )

            canvas.drawRect(scaledBox, boxPaint)

            val label = "${(det.confidence * 100).toInt()}%"
            val textX = scaledBox.left
            val textY = (scaledBox.top - 5f).coerceAtLeast(textPaint.textSize)

            canvas.drawRect(
                textX, textY - textPaint.textSize,
                textX + textPaint.measureText(label) + 8f, textY + 4f,
                bgPaint
            )
            canvas.drawText(label, textX + 4f, textY, textPaint)
        }
    }
}