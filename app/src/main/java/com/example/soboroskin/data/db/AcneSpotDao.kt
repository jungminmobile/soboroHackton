package com.example.soboroskin.data.db

import androidx.room.*
import com.example.soboroskin.data.model.AcneSpotEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AcneSpotDao {

    @Query("SELECT * FROM acne_spots WHERE isHealed = 0 ORDER BY firstSeenDate DESC")
    suspend fun getActiveSpots(): List<AcneSpotEntity>

    @Query("SELECT * FROM acne_spots ORDER BY firstSeenDate DESC")
    fun getAllSpotsFlow(): Flow<List<AcneSpotEntity>>

    @Query("SELECT * FROM acne_spots ORDER BY firstSeenDate DESC")
    suspend fun getAllSpots(): List<AcneSpotEntity>

    @Query("SELECT * FROM acne_spots WHERE partName = :part AND isHealed = 0")
    suspend fun getActiveSpotsByPart(part: String): List<AcneSpotEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(spot: AcneSpotEntity): Long

    @Update
    suspend fun update(spot: AcneSpotEntity)
}
