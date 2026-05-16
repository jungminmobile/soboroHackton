package com.example.soboroskin.ui.diary

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * 피부 5항목 오각형 레이더 차트
 * 꼭짓점 순서 (위에서 시계 방향):
 *   0: 수분, 1: 건조함, 2: 트러블, 3: 모공, 4: 탄력
 */
class PentagonChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val N = 5
        private const val LEVELS = 4
    }

    // scores[0..4] : 0~100 범위
    var scores = FloatArray(N) { 0f }
        set(value) {
            field = value
            invalidate()
        }

    val labels = arrayOf("수분", "건조함", "트러블", "모공", "탄력")

    // ─── Paints ─────────────────────────────────────────────────────
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#E0E0E0")
        strokeWidth = 2f
    }

    private val axisLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#EEEEEE")
        strokeWidth = 1.5f
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#556EBA93")   // primary 34% 투명
    }

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#6EBA93")
        strokeWidth = 2.5f
    }

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#6EBA93")
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = Color.parseColor("#616161")
        isFakeBoldText = true
    }

    // ─── Draw ────────────────────────────────────────────────────────
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cx = width / 2f
        val cy = height / 2f
        val maxRadius = min(width, height) / 2f * 0.52f
        val labelOffset = maxRadius * 1.38f

        labelPaint.textSize = maxRadius * 0.21f

        // 격자 (4단계 오각형)
        for (level in 1..LEVELS) {
            val r = maxRadius * level / LEVELS
            canvas.drawPath(pentagonPath(cx, cy, r), gridPaint)
        }

        // 축선
        for (i in 0 until N) {
            val a = angle(i)
            canvas.drawLine(
                cx, cy,
                cx + (maxRadius * cos(a)).toFloat(),
                cy + (maxRadius * sin(a)).toFloat(),
                axisLinePaint
            )
        }

        // 데이터 폴리곤
        val dataPath = Path()
        for (i in 0 until N) {
            val a = angle(i)
            val r = maxRadius * (scores[i] / 100f).coerceIn(0.03f, 1f)
            val x = cx + (r * cos(a)).toFloat()
            val y = cy + (r * sin(a)).toFloat()
            if (i == 0) dataPath.moveTo(x, y) else dataPath.lineTo(x, y)
        }
        dataPath.close()
        canvas.drawPath(dataPath, fillPaint)
        canvas.drawPath(dataPath, strokePaint)

        // 꼭짓점 점
        for (i in 0 until N) {
            val a = angle(i)
            val r = maxRadius * (scores[i] / 100f).coerceIn(0.03f, 1f)
            canvas.drawCircle(
                cx + (r * cos(a)).toFloat(),
                cy + (r * sin(a)).toFloat(),
                6f, dotPaint
            )
        }

        // 라벨
        for (i in 0 until N) {
            val a = angle(i)
            val lx = cx + (labelOffset * cos(a)).toFloat()
            val ly = cy + (labelOffset * sin(a)).toFloat()
            canvas.drawText(labels[i], lx, ly + labelPaint.textSize / 3f, labelPaint)
        }
    }

    // 위에서 시작, 시계 방향 72° 간격
    private fun angle(i: Int): Double = Math.toRadians(-90.0 + 72.0 * i)

    private fun pentagonPath(cx: Float, cy: Float, r: Float): Path {
        val path = Path()
        for (i in 0 until N) {
            val a = angle(i)
            val x = cx + (r * cos(a)).toFloat()
            val y = cy + (r * sin(a)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return path
    }
}
