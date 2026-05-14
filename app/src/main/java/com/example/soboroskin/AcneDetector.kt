package com.example.soboroskin

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

data class AcneDetection(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val confidence: Float,
    val part: String
) : java.io.Serializable {
    val bbox: RectF get() = RectF(left, top, right, bottom)
}

class AcneDetector(private val context: Context) {

    private var interpreter: Interpreter? = null
    private val confThreshold = 0.04f
    private val iouThreshold  = 0.45f
    private val maxBoxRatio   = 0.40f

    private var inputW = 640
    private var inputH = 640

    // 출력 텐서 포맷
    private var isYolov8 = false   // true: [1, 5+, N]  false: [1, N, 5+]
    private var numBoxes = 8400
    private var numAttrs = 5       // cx cy w h score (단일 클래스)

    init {
        loadModel()
    }

    private fun loadModel() {
        val afd = context.assets.openFd("acne_clean_best_float32.tflite")
        val model: MappedByteBuffer = FileInputStream(afd.fileDescriptor).channel
            .map(FileChannel.MapMode.READ_ONLY, afd.startOffset, afd.declaredLength)

        interpreter = Interpreter(model, Interpreter.Options().apply { setNumThreads(4) })

        val inp = interpreter!!.getInputTensor(0).shape()   // e.g. [1, 640, 640, 3]
        val out = interpreter!!.getOutputTensor(0).shape()  // e.g. [1, 5, 8400] or [1, 8400, 6]

        inputH = inp[1]
        inputW = inp[2]

        // YOLOv8 고정: [1, 4+nc, num_boxes]
        isYolov8 = true
        numAttrs = out[1]
        numBoxes = out[2]

        Log.d("AcneDetector", "input=${inp.contentToString()} output=${out.contentToString()} attrs=$numAttrs boxes=$numBoxes")
    }

    fun detect(bitmap: Bitmap, partName: String): List<AcneDetection> {
        val interp = interpreter ?: return emptyList()
        val origW = bitmap.width.toFloat()
        val origH = bitmap.height.toFloat()

        val inputBuf = preprocess(bitmap)
        val outputBuf = allocateOutput()

        interp.run(inputBuf, outputBuf)

        return parseOutput(outputBuf, origW, origH, partName)
    }

    private fun preprocess(bitmap: Bitmap): ByteBuffer {
        val scaled = Bitmap.createScaledBitmap(bitmap, inputW, inputH, true)
        val buf = ByteBuffer.allocateDirect(1 * inputH * inputW * 3 * 4)
            .order(ByteOrder.nativeOrder())
        for (y in 0 until inputH) {
            for (x in 0 until inputW) {
                val px = scaled.getPixel(x, y)
                buf.putFloat(((px shr 16) and 0xFF) / 255f)
                buf.putFloat(((px shr 8)  and 0xFF) / 255f)
                buf.putFloat(( px         and 0xFF) / 255f)
            }
        }
        buf.rewind()
        return buf
    }

    private fun allocateOutput(): Array<Array<FloatArray>> {
        return if (isYolov8)
            Array(1) { Array(numAttrs) { FloatArray(numBoxes) } }
        else
            Array(1) { Array(numBoxes) { FloatArray(numAttrs) } }
    }

    private fun parseOutput(
        raw: Array<Array<FloatArray>>,
        origW: Float,
        origH: Float,
        partName: String
    ): List<AcneDetection> {
        val candidates = mutableListOf<AcneDetection>()

        for (i in 0 until numBoxes) {
            val cx: Float; val cy: Float; val w: Float; val h: Float; val score: Float

            if (isYolov8) {
                // raw[0][attr][box]
                cx    = raw[0][0][i]
                cy    = raw[0][1][i]
                w     = raw[0][2][i]
                h     = raw[0][3][i]
                // 단일 클래스 → index 4; 다중 클래스 → max of [4..]
                score = if (numAttrs == 5) raw[0][4][i]
                        else (4 until numAttrs).maxOf { raw[0][it][i] }
            } else {
                // YOLOv5: raw[0][box][attr], attr[4]=objectness, attr[5]=class
                cx    = raw[0][i][0]
                cy    = raw[0][i][1]
                w     = raw[0][i][2]
                h     = raw[0][i][3]
                val obj = raw[0][i][4]
                val cls = if (numAttrs > 5) raw[0][i][5] else 1f
                score = obj * cls
            }

            if (score < confThreshold) continue

            // 정규화 좌표(0~1) → 원본 픽셀 좌표
            val x1 = (cx - w / 2f) * origW
            val y1 = (cy - h / 2f) * origH
            val x2 = (cx + w / 2f) * origW
            val y2 = (cy + h / 2f) * origH

            // 너무 큰 박스 제거
            if (w > maxBoxRatio || h > maxBoxRatio) continue

            candidates.add(
                AcneDetection(
                    left = x1.coerceAtLeast(0f),
                    top = y1.coerceAtLeast(0f),
                    right = x2.coerceAtMost(origW),
                    bottom = y2.coerceAtMost(origH),
                    confidence = score,
                    part = partName
                )
            )
        }

        return nms(candidates)
    }

    private fun nms(dets: List<AcneDetection>): List<AcneDetection> {
        val sorted = dets.sortedByDescending { it.confidence }.toMutableList()
        val kept = mutableListOf<AcneDetection>()
        while (sorted.isNotEmpty()) {
            val best = sorted.removeAt(0)
            kept.add(best)
            sorted.removeAll { iou(best.bbox, it.bbox) >= iouThreshold }
        }
        return kept
    }

    private fun iou(a: RectF, b: RectF): Float {
        val ix1 = maxOf(a.left, b.left); val iy1 = maxOf(a.top, b.top)
        val ix2 = minOf(a.right, b.right); val iy2 = minOf(a.bottom, b.bottom)
        val inter = maxOf(0f, ix2 - ix1) * maxOf(0f, iy2 - iy1)
        val union = a.width() * a.height() + b.width() * b.height() - inter
        return if (union > 0f) inter / union else 0f
    }

    fun close() {
        interpreter?.close()
    }
}
