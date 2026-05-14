package com.example.soboroskin

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF

data class AnalysisResult(
    val detections: List<AcneDetection>,  // 최종 감지 결과
    val faceDetected: Boolean,            // 얼굴 감지 여부
    val partCounts: Map<String, Int>      // 부위별 여드름 개수
)

class SkinAnalyzer(private val context: Context) {

    private val facePartCropper = FacePartCropper(context)
    private val acneDetector = AcneDetector(context)

    var confThreshold: Float
        get() = acneDetector.confThreshold
        set(value) { acneDetector.confThreshold = value }

    fun cropFaceParts(bitmap: Bitmap): List<FacePart>? = facePartCropper.cropParts(bitmap)
    fun getFaceContours(bitmap: Bitmap): List<FaceContour>? = facePartCropper.getContours(bitmap)

    fun analyze(bitmap: Bitmap): AnalysisResult {
        // 1단계: MediaPipe로 얼굴 부위 크롭
        val parts = facePartCropper.cropParts(bitmap)
            ?: return AnalysisResult(
                detections = emptyList(),
                faceDetected = false,
                partCounts = emptyMap()
            )

        // 2단계: 각 크롭에 YOLO 적용
        val allDetections = mutableListOf<AcneDetection>()

        val imgW = bitmap.width.toFloat()
        val imgH = bitmap.height.toFloat()
        // Python 코드와 동일: 전체 이미지 대비 20% 초과 박스 제거
        val maxBoxW = imgW * 0.20f
        val maxBoxH = imgH * 0.20f

        for (part in parts) {
            val detections = acneDetector.detect(part.bitmap, part.name)

            // 크롭 좌표 → 원본 좌표로 역변환
            for (det in detections) {
                val ox1 = det.left  + part.offsetX
                val oy1 = det.top   + part.offsetY
                val ox2 = det.right + part.offsetX
                val oy2 = det.bottom + part.offsetY

                // Python: if (ox2-ox1)/w > 0.20 or (oy2-oy1)/h > 0.20: continue
                if (ox2 - ox1 > maxBoxW || oy2 - oy1 > maxBoxH) continue

                allDetections.add(det.copy(left=ox1, top=oy1, right=ox2, bottom=oy2))
            }
        }

        // 3단계: 전체 NMS (과도한 중복 제거)
        val finalDetections = applyGlobalNMS(allDetections, iouThreshold = 0.5f)

        // 4단계: 부위별 카운트
        val partCounts = finalDetections
            .groupBy { it.part }
            .mapValues { it.value.size }

        return AnalysisResult(
            detections = finalDetections,
            faceDetected = true,
            partCounts = partCounts
        )
    }

    private fun applyGlobalNMS(
        detections: List<AcneDetection>,
        iouThreshold: Float
    ): List<AcneDetection> {
        val sorted = detections.sortedByDescending { it.confidence }.toMutableList()
        val kept = mutableListOf<AcneDetection>()

        while (sorted.isNotEmpty()) {
            val best = sorted.removeAt(0)
            kept.add(best)
            sorted.removeAll { iou(best.bbox, it.bbox) >= iouThreshold }
        }
        return kept
    }

    private fun iou(a: RectF, b: RectF): Float {
        val interX1 = maxOf(a.left, b.left)
        val interY1 = maxOf(a.top, b.top)
        val interX2 = minOf(a.right, b.right)
        val interY2 = minOf(a.bottom, b.bottom)
        val inter = maxOf(0f, interX2 - interX1) * maxOf(0f, interY2 - interY1)
        val union = a.width() * a.height() + b.width() * b.height() - inter
        return if (union > 0) inter / union else 0f
    }

    fun close() {
        facePartCropper.close()
        acneDetector.close()
    }
}