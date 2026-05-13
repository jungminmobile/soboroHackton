package com.example.soboroskin.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "diagnoses")
data class DiagnosisEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val date: Long = System.currentTimeMillis(),

    // 피부 타입: "건성", "지성", "복합성", "민감성", "중성"
    val skinType: String = "",

    // 점수 (0~100)
    val moistureScore: Int = 0,
    val oilScore: Int = 0,
    val troubleScore: Int = 0,
    val elasticityScore: Int = 0,

    // AI 코멘트
    val aiComment: String = "",

    // 촬영 이미지 파일 경로 (없으면 빈 문자열)
    val photoPath: String = "",

    // 수동 입력 여부 (false = AI 진단, true = 직접 기록)
    val isManual: Boolean = false,

    // 사용자 메모
    val notes: String = ""
)
