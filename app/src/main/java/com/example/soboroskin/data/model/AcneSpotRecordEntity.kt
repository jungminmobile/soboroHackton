package com.example.soboroskin.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "acne_spot_records")
data class AcneSpotRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val spotId: Long,
    val diagnosisId: Long,
    val date: Long,
    val confidence: Float,
    val normalizedCx: Float,
    val normalizedCy: Float,
    val normalizedWidth: Float,
    val normalizedHeight: Float,
    // "new" / "existing" / "worsened" / "improved" / "healed"
    val changeType: String
)
