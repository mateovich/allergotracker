package io.github.mateovich.allergotracker.features.ingestion.data

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

    @Query("SELECT * FROM ingestion_events WHERE id = :id LIMIT 1")
    fun observeById(id: Long): Flow<IngestionEventEntity?>

    @Update
    suspend fun update(entity: IngestionEventEntity): Int

    @Query("DELETE FROM ingestion_events WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("UPDATE ingestion_events SET item_name = :name WHERE reusable_entity_kind = :kind AND reusable_entity_id = :reusableId")
    suspend fun updateReusableEntityName(kind: String, reusableId: Long, name: String): Int

    @Query("SELECT COUNT(*) FROM ingestion_events WHERE reusable_entity_kind = :kind AND reusable_entity_id = :reusableId")
    suspend fun countReusableEntityReferences(kind: String, reusableId: Long): Int

    @Query(
        "UPDATE ingestion_events SET reusable_entity_id = :destinationId, item_name = :destinationName WHERE reusable_entity_kind = :kind AND reusable_entity_id = :sourceId"
    )
    suspend fun reassignReusableEntityReferences(
        kind: String,
        sourceId: Long,
        destinationId: Long,
        destinationName: String
    ): Int

    @Query("UPDATE ingestion_events SET reusable_entity_kind = NULL, reusable_entity_id = NULL WHERE reusable_entity_kind = :kind AND reusable_entity_id = :reusableId")
    suspend fun clearReusableEntityReferences(kind: String, reusableId: Long): Int

    @Query("SELECT * FROM ingestion_events ORDER BY occurred_at_epoch_ms DESC, id DESC")
    fun observeIngestionEvents(): Flow<List<IngestionEventEntity>>
}
