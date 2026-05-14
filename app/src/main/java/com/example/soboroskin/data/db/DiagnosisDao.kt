package com.example.soboroskin.data.db

import androidx.room.*
import com.example.soboroskin.data.model.DiagnosisEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DiagnosisDao {

    @Query("SELECT * FROM diagnoses ORDER BY date DESC")
    fun getAllEntries(): Flow<List<DiagnosisEntity>>

    @Query("SELECT * FROM diagnoses ORDER BY date DESC LIMIT 1")
    suspend fun getLatestDiagnosis(): DiagnosisEntity?

    @Query("SELECT * FROM diagnoses WHERE date >= :fromDate ORDER BY date ASC")
    fun getEntriesSince(fromDate: Long): Flow<List<DiagnosisEntity>>

    @Query("SELECT * FROM diagnoses WHERE isManual = 0 ORDER BY date DESC")
    fun getAiDiagnoses(): Flow<List<DiagnosisEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: DiagnosisEntity): Long

    @Update
    suspend fun update(entity: DiagnosisEntity)

    @Delete
    suspend fun delete(entity: DiagnosisEntity)

    @Query("DELETE FROM diagnoses WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM diagnoses")
    suspend fun getCount(): Int

    @Query("SELECT * FROM diagnoses WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): DiagnosisEntity?

    @Query("DELETE FROM diagnoses")
    suspend fun deleteAll()

    @Query("SELECT * FROM diagnoses")
    suspend fun getAllNow(): List<DiagnosisEntity>

    @Query("DELETE FROM diagnoses WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)
}
