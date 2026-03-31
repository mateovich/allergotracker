package com.example.app.features.eventcatalog.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ReusableEventCatalogDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: ReusableEventCatalogEntity): Long

    @Update
    suspend fun update(entity: ReusableEventCatalogEntity): Int

    @Query("SELECT * FROM reusable_event_catalog WHERE kind = :kind ORDER BY usage_count DESC, last_used_at_epoch_ms DESC, updated_at_epoch_ms DESC, name COLLATE NOCASE ASC, id ASC")
    fun observeByKind(kind: String): Flow<List<ReusableEventCatalogEntity>>

    @Query("SELECT * FROM reusable_event_catalog WHERE kind = :kind AND normalized_name = :normalizedName LIMIT 1")
    suspend fun findByNormalizedName(kind: String, normalizedName: String): ReusableEventCatalogEntity?

    @Query("SELECT * FROM reusable_event_catalog WHERE kind = :kind AND id = :id LIMIT 1")
    suspend fun findById(kind: String, id: Long): ReusableEventCatalogEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM reusable_event_catalog WHERE kind = :kind AND id = :id)")
    suspend fun existsById(kind: String, id: Long): Boolean
}
