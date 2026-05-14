package com.example.soboroskin.data.db

import androidx.room.*
import com.example.soboroskin.data.model.AcneSpotRecordEntity

@Dao
interface AcneSpotRecordDao {

    @Insert
    suspend fun insert(record: AcneSpotRecordEntity): Long

    @Query("SELECT * FROM acne_spot_records WHERE spotId = :spotId ORDER BY date ASC")
    suspend fun getRecordsForSpot(spotId: Long): List<AcneSpotRecordEntity>

    @Query("SELECT * FROM acne_spot_records WHERE spotId = :spotId ORDER BY date DESC LIMIT 1")
    suspend fun getLatestRecord(spotId: Long): AcneSpotRecordEntity?

    @Query("SELECT * FROM acne_spot_records WHERE diagnosisId = :diagnosisId")
    suspend fun getRecordsForDiagnosis(diagnosisId: Long): List<AcneSpotRecordEntity>
}
