package com.example.app.features.ingestion.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Update
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface IngestionEventDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: IngestionEventEntity): Long

    @Query("SELECT COUNT(*) FROM ingestion_events")
    fun observeRecordCount(): Flow<Int>

    @Query("SELECT * FROM ingestion_events WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): IngestionEventEntity?

    @Update
    suspend fun update(entity: IngestionEventEntity): Int

    @Query("DELETE FROM ingestion_events WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("SELECT * FROM ingestion_events ORDER BY occurred_at_epoch_ms DESC, id DESC")
    fun observeIngestionEvents(): Flow<List<IngestionEventEntity>>
}
