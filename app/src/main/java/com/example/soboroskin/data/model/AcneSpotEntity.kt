package com.example.soboroskin.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "acne_spots")
data class AcneSpotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val partName: String,           // "left_cheek", "nose" 등
    val normalizedCx: Float,        // 이미지 너비 기준 중심 x (0~1)
    val normalizedCy: Float,        // 이미지 높이 기준 중심 y (0~1)
    val firstSeenDate: Long,
    val lastSeenDate: Long,
    val isHealed: Boolean = false
)
