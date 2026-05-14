package com.example.soboroskin

import android.util.Log
import com.example.soboroskin.data.db.AppDatabase
import com.example.soboroskin.data.model.AcneSpotEntity
import com.example.soboroskin.data.model.AcneSpotRecordEntity
import kotlin.math.sqrt

/**
 * 트러블 개체 추적기
 * - 새 감지 결과를 기존 TroubleSpot DB와 매칭
 * - 같은 트러블이면 변화 타입(worsened/improved/existing) 기록
 * - 새 트러블이면 INSERT, 사라진 트러블이면 healed 처리
 */
class AcneTracker(private val db: AppDatabase) {

    companion object {
        // 같은 부위 내에서 같은 트러블로 판단하는 거리 임계값 (이미지 대각선의 15%)
        private const val MATCH_THRESHOLD = 0.15f
        // 부위 불일치 시 폴백 매칭 임계값 (8%) — AI가 경계 부위를 다르게 라벨링할 때 커버
        private const val MATCH_THRESHOLD_FALLBACK = 0.08f
        // 이 기간 이상 감지 안 되면 완치로 판단 (7일)
        private const val HEAL_THRESHOLD_MS = 7 * 24 * 60 * 60 * 1000L
        // 크기 변화 판단 기준 (10%)
        private const val CHANGE_RATIO = 0.10f

        /**
         * 볼 upper/lower 같은 세부 구역을 대표 부위명으로 정규화.
         * 매칭 및 저장에 동일하게 사용.
         */
        fun normalizePartName(raw: String): String = when {
            raw.contains("left_cheek")  -> "left_cheek"
            raw.contains("right_cheek") -> "right_cheek"
            raw.contains("left_jaw")    -> "left_jaw"
            raw.contains("right_jaw")   -> "right_jaw"
            raw.contains("forehead")    -> "forehead"
            raw.contains("nose")        -> "nose"
            raw.contains("chin")        -> "chin"
            raw.contains("mouth")       -> "mouth"
            raw.contains("eye")         -> "eye"
            else                        -> raw
        }
    }

    suspend fun track(
        detections: List<AcneDetection>,
        imageWidth: Int,
        imageHeight: Int,
        diagnosisId: Long,
        date: Long
    ) {
        if (imageWidth == 0 || imageHeight == 0) return

        val iw = imageWidth.toFloat()
        val ih = imageHeight.toFloat()

        // 1. 감지 결과를 정규화 좌표로 변환 (부위명도 정규화)
        val normalized = detections.map { det ->
            NormalizedDetection(
                partName = normalizePartName(det.part),
                cx = (det.left + det.right) / 2f / iw,
                cy = (det.top + det.bottom) / 2f / ih,
                w  = (det.right - det.left) / iw,
                h  = (det.bottom - det.top) / ih,
                confidence = det.confidence
            )
        }

        // 2. DB에서 현재 활성 스팟 조회
        val activeSpots = db.acneSpotDao().getActiveSpots().toMutableList()
        val matchedSpotIds = mutableSetOf<Long>()

        // 3. 각 감지 → 기존 스팟과 매칭 (신뢰도 높은 순으로)
        val sortedDetections = normalized.sortedByDescending { it.confidence }

        for (det in sortedDetections) {
            val (bestSpot, bestDist) = findBestMatch(det, activeSpots, matchedSpotIds)

            if (bestSpot != null) {
                // ── 기존 트러블 업데이트 ──
                matchedSpotIds.add(bestSpot.id)

                val lastRecord = db.acneSpotRecordDao().getLatestRecord(bestSpot.id)
                val changeType = classifyChange(det, lastRecord)

                Log.d("AcneTracker", "MATCH spotId=${bestSpot.id} part=${det.partName} dist=${"%.3f".format(bestDist)} → $changeType")

                db.acneSpotDao().update(
                    bestSpot.copy(
                        normalizedCx = det.cx,
                        normalizedCy = det.cy,
                        lastSeenDate = date
                    )
                )
                db.acneSpotRecordDao().insert(
                    AcneSpotRecordEntity(
                        spotId          = bestSpot.id,
                        diagnosisId     = diagnosisId,
                        date            = date,
                        confidence      = det.confidence,
                        normalizedCx    = det.cx,
                        normalizedCy    = det.cy,
                        normalizedWidth = det.w,
                        normalizedHeight= det.h,
                        changeType      = changeType
                    )
                )
            } else {
                // ── 새 트러블 ──
                Log.d("AcneTracker", "NEW spot part=${det.partName} cx=${"%.3f".format(det.cx)} cy=${"%.3f".format(det.cy)}")

                val newSpot = AcneSpotEntity(
                    partName     = det.partName,
                    normalizedCx = det.cx,
                    normalizedCy = det.cy,
                    firstSeenDate= date,
                    lastSeenDate = date,
                    isHealed     = false
                )
                val newId = db.acneSpotDao().insert(newSpot)
                db.acneSpotRecordDao().insert(
                    AcneSpotRecordEntity(
                        spotId          = newId,
                        diagnosisId     = diagnosisId,
                        date            = date,
                        confidence      = det.confidence,
                        normalizedCx    = det.cx,
                        normalizedCy    = det.cy,
                        normalizedWidth = det.w,
                        normalizedHeight= det.h,
                        changeType      = "new"
                    )
                )
            }
        }

        // 4. 이번 감지에서 매칭 안 된 기존 스팟 → 완치 판단
        val now = System.currentTimeMillis()
        val unmatchedSpots = activeSpots.filter { it.id !in matchedSpotIds }
        for (spot in unmatchedSpots) {
            if (now - spot.lastSeenDate >= HEAL_THRESHOLD_MS) {
                Log.d("AcneTracker", "HEALED spotId=${spot.id} part=${spot.partName}")
                db.acneSpotDao().update(spot.copy(isHealed = true))
                db.acneSpotRecordDao().insert(
                    AcneSpotRecordEntity(
                        spotId          = spot.id,
                        diagnosisId     = diagnosisId,
                        date            = date,
                        confidence      = 0f,
                        normalizedCx    = spot.normalizedCx,
                        normalizedCy    = spot.normalizedCy,
                        normalizedWidth = 0f,
                        normalizedHeight= 0f,
                        changeType      = "healed"
                    )
                )
            }
            // 7일 미만이면 그냥 이번 스캔에서 감지 못한 것으로 두고 패스
        }
    }

    /**
     * 두 단계 매칭:
     * 1단계: 같은 부위 내에서 MATCH_THRESHOLD(15%) 이내 → 우선 매칭
     * 2단계: 같은 부위 매칭 실패 시, 부위 무관하게 MATCH_THRESHOLD_FALLBACK(8%) 이내 폴백
     *        (AI가 경계 부위를 다르게 라벨링하는 경우 커버)
     */
    private fun findBestMatch(
        det: NormalizedDetection,
        activeSpots: List<AcneSpotEntity>,
        alreadyMatched: Set<Long>
    ): Pair<AcneSpotEntity?, Float> {

        // 1단계: 같은 부위 매칭
        var bestSpot: AcneSpotEntity? = null
        var bestDist = Float.MAX_VALUE

        for (spot in activeSpots) {
            if (spot.id in alreadyMatched) continue
            if (normalizePartName(spot.partName) != det.partName) continue

            val dist = euclidean(spot.normalizedCx, spot.normalizedCy, det.cx, det.cy)
            if (dist < bestDist) {
                bestDist = dist
                bestSpot = spot
            }
        }
        if (bestSpot != null && bestDist < MATCH_THRESHOLD) return Pair(bestSpot, bestDist)

        // 2단계: 부위 무관 폴백 매칭 (매우 가까운 경우만)
        bestSpot = null
        bestDist = Float.MAX_VALUE

        for (spot in activeSpots) {
            if (spot.id in alreadyMatched) continue
            val dist = euclidean(spot.normalizedCx, spot.normalizedCy, det.cx, det.cy)
            if (dist < bestDist) {
                bestDist = dist
                bestSpot = spot
            }
        }

        return if (bestSpot != null && bestDist < MATCH_THRESHOLD_FALLBACK)
            Pair(bestSpot, bestDist)
        else
            Pair(null, Float.MAX_VALUE)
    }

    private fun euclidean(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x1 - x2
        val dy = y1 - y2
        return sqrt((dx * dx + dy * dy).toDouble()).toFloat()
    }

    private fun classifyChange(
        det: NormalizedDetection,
        lastRecord: AcneSpotRecordEntity?
    ): String {
        if (lastRecord == null || lastRecord.changeType == "healed") return "existing"

        val prevArea = lastRecord.normalizedWidth * lastRecord.normalizedHeight
        val curArea  = det.w * det.h

        return when {
            prevArea <= 0f -> "existing"
            curArea > prevArea * (1f + CHANGE_RATIO) -> "worsened"
            curArea < prevArea * (1f - CHANGE_RATIO) -> "improved"
            else -> "existing"
        }
    }

    private data class NormalizedDetection(
        val partName: String,
        val cx: Float,
        val cy: Float,
        val w: Float,
        val h: Float,
        val confidence: Float
    )
}
