package com.example.soboroskin

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.RectF
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker.FaceLandmarkerOptions
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult

data class FaceContour(val name: String, val points: List<PointF>)

data class FacePart(
    val name: String,
    val bitmap: Bitmap,
    val offsetX: Int,   // 원본 이미지에서의 x 오프셋
    val offsetY: Int,   // 원본 이미지에서의 y 오프셋
)

class FacePartCropper(private val context: Context) {

    private var faceLandmarker: FaceLandmarker? = null

    // 파트별 랜드마크 인덱스
    private val PARTS_0 = mapOf(
        "face" to listOf(10,109,67,103,54,21,162,127,234,93,132,58,172,136,150,149,176,148,152,377,400,378,379,365,397,288,361,323,454,356,389,251,284,332,297,338)
    )

    private val PARTS_8 = mapOf(
        "forehead"    to listOf(10,109,67,103,54,21,162,127,234,93,389,251,284,332,297,338),
        "nose"        to listOf(168,6,197,195,5,4,1,19,94,2,98,327,294,278,344,440,275,45,51,3),
        "left_cheek"  to listOf(234,93,132,58,172,136,150,149,176,148,152,377,400,378,379,365,397,288),
        "right_cheek" to listOf(454,323,361,288,397,365,379,378,400,377,152,148,176,149,150,136,172,58),
        "left_jaw"    to listOf(58,172,136,150,149,176,148,152,175,199,200,18,17,314,405,321,375),
        "right_jaw"   to listOf(288,397,365,379,378,400,377,152,175,199,200,18,17,84,181,91,146),
        "mouth"       to listOf(61,185,40,39,37,0,267,269,270,409,291,375,321,405,314,17,84,181,91,146),
        "chin"        to listOf(175,199,200,18,152,377,378,379,365,148,176,149,150,136,172)
    )

    private val PARTS_12 = mapOf(
        "forehead_left"     to listOf(10,109,67,103,54,21,162,127,234),
        "forehead_right"    to listOf(10,338,297,332,284,251,389,356,454),
        "left_eye_area"     to listOf(226,247,30,29,27,28,56,190,243,112,26,22,23,24,110,25),
        "right_eye_area"    to listOf(446,467,260,259,257,258,286,414,463,341,256,252,253,254,339,255),
        "nose_upper"        to listOf(168,6,197,195,5,4,1,19,94,2),
        "nose_lower"        to listOf(98,327,294,278,344,440,275,45,51,3,240,99,97,2),
        "left_cheek_upper"  to listOf(234,93,132,58,172,136,150,149),
        "left_cheek_lower"  to listOf(149,176,148,152,377,400,378,379,365,397,288),
        "right_cheek_upper" to listOf(454,323,361,288,397,365,379,378),
        "right_cheek_lower" to listOf(378,400,377,152,148,176,149,150,136,172,58),
        "mouth_chin"        to listOf(61,185,40,39,37,0,267,269,270,409,291,375,321,405,314,17,84,181,91,146),
        "chin"              to listOf(175,199,200,18,152,377,378,379,365,148,176,149,150,136,172)
    )

    private val PARTS_16 = mapOf(
        "forehead_left"     to listOf(10,109,67,103,54,21,162,127,234),
        "forehead_right"    to listOf(10,338,297,332,284,251,389,356,454),
        "left_eye_area"     to listOf(226,247,30,29,27,28,56,190,243,112,26,22,23,24,110,25),
        "right_eye_area"    to listOf(446,467,260,259,257,258,286,414,463,341,256,252,253,254,339,255),
        "nose_bridge"       to listOf(168,6,197,195,5,4),
        "nose_tip"          to listOf(1,19,94,2,98,327,294,278,344,440,275,45,51,3),
        "left_cheek_upper"  to listOf(234,93,132,58,172,136),
        "left_cheek_lower"  to listOf(136,150,149,176,148,152,377,400,378,379),
        "right_cheek_upper" to listOf(454,323,361,288,397,365),
        "right_cheek_lower" to listOf(365,379,378,400,377,152,148,176,149,150),
        "left_jaw"          to listOf(58,172,136,150,149,176,148,152,175,199),
        "right_jaw"         to listOf(288,397,365,379,378,400,377,152,175,199),
        "mouth_upper"       to listOf(61,185,40,39,37,0,267,269,270,409,291),
        "mouth_lower"       to listOf(375,321,405,314,17,84,181,91,146),
        "chin_left"         to listOf(175,199,200,18,152,377,378),
        "chin_right"        to listOf(175,199,200,18,148,176,149)
    )

    init {
        loadModel()
    }

    private fun loadModel() {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath("face_landmarker.task")
            .build()
        val options = FaceLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setNumFaces(1)
            .setMinFaceDetectionConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .setOutputFaceBlendshapes(false)
            .setRunningMode(com.google.mediapipe.tasks.vision.core.RunningMode.IMAGE)
            .build()
        faceLandmarker = FaceLandmarker.createFromOptions(context, options)
    }

    fun cropParts(bitmap: Bitmap): List<FacePart>? {
        val mpImage = BitmapImageBuilder(bitmap).build()
        val result: FaceLandmarkerResult = faceLandmarker?.detect(mpImage) ?: return null

        if (result.faceLandmarks().isEmpty()) return null

        val landmarks = result.faceLandmarks()[0]
        val imgW = bitmap.width
        val imgH = bitmap.height
        val parts = mutableListOf<FacePart>()

        // 4개 파트 세트 모두 크롭
        for (partsMap in listOf(PARTS_0, PARTS_8, PARTS_12, PARTS_16)) {
            for ((name, indices) in partsMap) {
                val bbox = getBBox(landmarks, indices, imgW, imgH) ?: continue
                val crop = Bitmap.createBitmap(
                    bitmap,
                    bbox.left.toInt(),
                    bbox.top.toInt(),
                    (bbox.width()).toInt(),
                    (bbox.height()).toInt()
                )
                parts.add(FacePart(name, crop, bbox.left.toInt(), bbox.top.toInt()))
            }
        }
        return parts
    }

    private fun getBBox(
        landmarks: List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>,
        indices: List<Int>,
        imgW: Int,
        imgH: Int,
        pad: Float = 0.15f
    ): RectF? {
        if (indices.any { it >= landmarks.size }) return null
        val xs = indices.map { landmarks[it].x() * imgW }
        val ys = indices.map { landmarks[it].y() * imgH }
        var x1 = xs.min()
        var y1 = ys.min()
        var x2 = xs.max()
        var y2 = ys.max()
        val pw = (x2 - x1) * pad
        val ph = (y2 - y1) * pad
        x1 = maxOf(0f, x1 - pw)
        y1 = maxOf(0f, y1 - ph)
        x2 = minOf(imgW.toFloat(), x2 + pw)
        y2 = minOf(imgH.toFloat(), y2 + ph)
        if (x2 - x1 < 10 || y2 - y1 < 10) return null
        return RectF(x1, y1, x2, y2)
    }

    // PARTS_8 기준 얼굴 부위별 윤곽 포인트 반환
    fun getContours(bitmap: Bitmap): List<FaceContour>? {
        val mpImage = BitmapImageBuilder(bitmap).build()
        val result: FaceLandmarkerResult = faceLandmarker?.detect(mpImage) ?: return null
        if (result.faceLandmarks().isEmpty()) return null

        val landmarks = result.faceLandmarks()[0]
        val imgW = bitmap.width.toFloat()
        val imgH = bitmap.height.toFloat()

        return PARTS_8.mapNotNull { (name, indices) ->
            val pts = indices.mapNotNull { idx ->
                if (idx < landmarks.size)
                    PointF(landmarks[idx].x() * imgW, landmarks[idx].y() * imgH)
                else null
            }
            if (pts.size >= 3) FaceContour(name, pts) else null
        }
    }

    fun close() {
        faceLandmarker?.close()
    }
}