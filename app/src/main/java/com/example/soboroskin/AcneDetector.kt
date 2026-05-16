package com.example.soboroskin

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
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

private data class LbParams(val scale: Float, val padLeft: Int, val padTop: Int)

class AcneDetector(private val context: Context) {

    private var interpreter: Interpreter? = null
    var confThreshold = 0.04f
    private val iouThreshold = 0.3f  // 겹침 기준 강화 (기존 0.5)

    private var inputW = 640
    private var inputH = 640
    // 출력 포맷
    //  - NMS 내장 (ultralytics export with nms=True): [1, N, 6] = [x1,y1,x2,y2,score,class]
    //  - Raw YOLOv8:                                   [1, 4+C, num_boxes]
    private var isNmsIncluded = false
    private var numBoxes = 8400
    private var numAttrs = 5
    // 첫 inference에서 좌표 단위(0~1 vs 0~640) 자동 감지
    private var coordsAreNormalized: Boolean? = null

    init { loadModel() }

    private fun loadModel() {
        val afd = context.assets.openFd("acne_clean_best_float32.tflite")
        val model: MappedByteBuffer = FileInputStream(afd.fileDescriptor).channel
            .map(FileChannel.MapMode.READ_ONLY, afd.startOffset, afd.declaredLength)

        interpreter = Interpreter(model, Interpreter.Options().apply { setNumThreads(4) })

        val inp = interpreter!!.getInputTensor(0).shape()
        val out = interpreter!!.getOutputTensor(0).shape()

        inputH = inp[1]; inputW = inp[2]

        // NMS 내장 export: [1, N, 6]
        if (out.size == 3 && out[2] == 6) {
            isNmsIncluded = true
            numBoxes = out[1]   // e.g. 300
            numAttrs = out[2]   // 6
        } else {
            // Raw YOLOv8: [1, attrs, boxes]
            isNmsIncluded = false
            numAttrs = out[1]
            numBoxes = out[2]
        }

        Log.d("AcneDetector",
            "input=${inp.contentToString()} output=${out.contentToString()} nmsIncluded=$isNmsIncluded")
    }

    fun detect(bitmap: Bitmap, partName: String): List<AcneDetection> {
        val interp = interpreter ?: return emptyList()
        val (inputBuf, lb) = letterboxPreprocess(bitmap)
        val outputBuf = allocateOutput()
        interp.run(inputBuf, outputBuf)
        return parseOutput(outputBuf, bitmap.width.toFloat(), bitmap.height.toFloat(), partName, lb)
    }

    /**
     * Python ultralytics와 동일한 letterbox 전처리:
     * 종횡비 유지, 빈 공간은 YOLOv8 기본 패딩색(114, 114, 114)으로 채움
     */
    private fun letterboxPreprocess(bitmap: Bitmap): Pair<ByteBuffer, LbParams> {
        val scale = minOf(inputW.toFloat() / bitmap.width, inputH.toFloat() / bitmap.height)
        val scaledW = (bitmap.width  * scale).toInt().coerceIn(1, inputW)
        val scaledH = (bitmap.height * scale).toInt().coerceIn(1, inputH)
        val padLeft = (inputW - scaledW) / 2
        val padTop  = (inputH - scaledH) / 2

        val scaled = Bitmap.createScaledBitmap(bitmap, scaledW, scaledH, true)
        val buf = ByteBuffer.allocateDirect(inputH * inputW * 3 * 4).order(ByteOrder.nativeOrder())
        val grayF = 114f / 255f

        for (y in 0 until inputH) {
            for (x in 0 until inputW) {
                val imgX = x - padLeft
                val imgY = y - padTop
                if (imgX < 0 || imgX >= scaledW || imgY < 0 || imgY >= scaledH) {
                    buf.putFloat(grayF); buf.putFloat(grayF); buf.putFloat(grayF)
                } else {
                    val px = scaled.getPixel(imgX, imgY)
                    buf.putFloat(Color.red(px)   / 255f)
                    buf.putFloat(Color.green(px) / 255f)
                    buf.putFloat(Color.blue(px)  / 255f)
                }
            }
        }
        buf.rewind()
        return Pair(buf, LbParams(scale, padLeft, padTop))
    }

    private fun allocateOutput(): Array<Array<FloatArray>> =
        if (isNmsIncluded) Array(1) { Array(numBoxes) { FloatArray(numAttrs) } }
        else               Array(1) { Array(numAttrs) { FloatArray(numBoxes) } }

    private fun parseOutput(
        raw: Array<Array<FloatArray>>,
        origW: Float, origH: Float,
        partName: String,
        lb: LbParams
    ): List<AcneDetection> {
        return if (isNmsIncluded) parseNmsIncluded(raw, origW, origH, partName, lb)
               else               parseRawYolov8(raw, origW, origH, partName, lb)
    }

    /**
     * NMS 내장 export 출력: [1, N, 6] = [x1, y1, x2, y2, score, class]
     * 좌표는 letterboxed 640 기준 (normalized 0~1 또는 픽셀 0~640) — 첫 호출에서 자동 감지
     */
    private fun parseNmsIncluded(
        raw: Array<Array<FloatArray>>,
        origW: Float, origH: Float,
        partName: String,
        lb: LbParams
    ): List<AcneDetection> {
        // 좌표 단위 자동 감지 (한 번만)
        if (coordsAreNormalized == null) {
            var maxCoord = 0f
            for (i in 0 until numBoxes) {
                for (k in 0..3) {
                    val v = raw[0][i][k]
                    if (v > maxCoord) maxCoord = v
                }
            }
            coordsAreNormalized = maxCoord <= 1.5f
            Log.d("AcneDetector", "auto-detect maxCoord=$maxCoord normalized=$coordsAreNormalized")
        }
        val normalized = coordsAreNormalized == true
        val results = mutableListOf<AcneDetection>()
        var validCount = 0

        for (i in 0 until numBoxes) {
            val det = raw[0][i]
            val x1n = det[0]
            val y1n = det[1]
            val x2n = det[2]
            val y2n = det[3]
            val score = det[4]
            // val cls = det[5]

            if (score < confThreshold) continue
            if (x2n <= x1n || y2n <= y1n) continue  // 0-padded 빈 슬롯

            // letterboxed 640 좌표 (픽셀)
            val lx1 = if (normalized) x1n * inputW else x1n
            val ly1 = if (normalized) y1n * inputH else y1n
            val lx2 = if (normalized) x2n * inputW else x2n
            val ly2 = if (normalized) y2n * inputH else y2n

            // letterbox 역변환 → 크롭 픽셀 좌표
            val rx1 = (lx1 - lb.padLeft) / lb.scale
            val ry1 = (ly1 - lb.padTop)  / lb.scale
            val rx2 = (lx2 - lb.padLeft) / lb.scale
            val ry2 = (ly2 - lb.padTop)  / lb.scale

            if (validCount < 3) {
                Log.d("AcneDetector",
                    "[$partName #$i] xyxy(n)=${"%.3f".format(x1n)},${"%.3f".format(y1n)}," +
                    "${"%.3f".format(x2n)},${"%.3f".format(y2n)} score=${"%.3f".format(score)} " +
                    "→ crop=(${rx1.toInt()},${ry1.toInt()})-(${rx2.toInt()},${ry2.toInt()}) " +
                    "of ${origW.toInt()}x${origH.toInt()}")
                validCount++
            }

            if (rx2 <= 0f || ry2 <= 0f || rx1 >= origW || ry1 >= origH) continue

            results.add(
                AcneDetection(
                    left       = rx1.coerceAtLeast(0f),
                    top        = ry1.coerceAtLeast(0f),
                    right      = rx2.coerceAtMost(origW),
                    bottom     = ry2.coerceAtMost(origH),
                    confidence = score,
                    part       = partName
                )
            )
        }
        // NMS는 모델 안에서 이미 적용됨 — 추가 NMS 불필요
        return results
    }

    /** Raw YOLOv8 출력: [1, 4+C, num_boxes] = [cx, cy, w, h, ...class_scores] */
    private fun parseRawYolov8(
        raw: Array<Array<FloatArray>>,
        origW: Float, origH: Float,
        partName: String,
        lb: LbParams
    ): List<AcneDetection> {
        val candidates = mutableListOf<AcneDetection>()
        for (i in 0 until numBoxes) {
            val cx = raw[0][0][i]
            val cy = raw[0][1][i]
            val w  = raw[0][2][i]
            val h  = raw[0][3][i]
            val score = if (numAttrs == 5) raw[0][4][i]
                        else (4 until numAttrs).maxOf { raw[0][it][i] }
            if (score < confThreshold) continue

            val rx1 = ((cx - w / 2f) * inputW - lb.padLeft) / lb.scale
            val ry1 = ((cy - h / 2f) * inputH - lb.padTop)  / lb.scale
            val rx2 = ((cx + w / 2f) * inputW - lb.padLeft) / lb.scale
            val ry2 = ((cy + h / 2f) * inputH - lb.padTop)  / lb.scale

            if (rx2 <= 0f || ry2 <= 0f || rx1 >= origW || ry1 >= origH) continue

            candidates.add(
                AcneDetection(
                    left       = rx1.coerceAtLeast(0f),
                    top        = ry1.coerceAtLeast(0f),
                    right      = rx2.coerceAtMost(origW),
                    bottom     = ry2.coerceAtMost(origH),
                    confidence = score,
                    part       = partName
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
        return suppressContained(kept)
    }

    /**
     * 두 박스의 교차 면적이 더 작은 박스 면적의 80% 이상이면
     * 더 작은 박스를 제거 (거의 포함된 중복 박스 제거).
     */
    private fun suppressContained(
        dets: List<AcneDetection>,
        overlapRatio: Float = 0.50f
    ): List<AcneDetection> {
        val sorted = dets.sortedByDescending { it.bbox.width() * it.bbox.height() }
        val kept = mutableListOf<AcneDetection>()
        for (candidate in sorted) {
            val areaC = candidate.bbox.width() * candidate.bbox.height()
            val dominated = kept.any { big ->
                val inter = intersection(big.bbox, candidate.bbox)
                areaC > 0f && inter / areaC >= overlapRatio
            }
            if (!dominated) kept.add(candidate)
        }
        return kept
    }

    private fun intersection(a: RectF, b: RectF): Float {
        val ix1 = maxOf(a.left, b.left); val iy1 = maxOf(a.top, b.top)
        val ix2 = minOf(a.right, b.right); val iy2 = minOf(a.bottom, b.bottom)
        return maxOf(0f, ix2 - ix1) * maxOf(0f, iy2 - iy1)
    }

    private fun iou(a: RectF, b: RectF): Float {
        val inter = intersection(a, b)
        val union = a.width() * a.height() + b.width() * b.height() - inter
        return if (union > 0f) inter / union else 0f
    }

    fun close() { interpreter?.close() }
}
