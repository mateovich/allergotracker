package io.github.mateovich.allergotracker.features.outbreak.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Update
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface OutbreakEventDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: OutbreakEventEntity): Long

    @Query("SELECT COUNT(*) FROM outbreak_events")
    fun observeRecordCount(): Flow<Int>

    @Query("SELECT * FROM outbreak_events WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): OutbreakEventEntity?

    @Query("SELECT * FROM outbreak_events WHERE id = :id LIMIT 1")
    fun observeById(id: Long): Flow<OutbreakEventEntity?>

    @Update
    suspend fun update(entity: OutbreakEventEntity): Int

    @Query("DELETE FROM outbreak_events WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("UPDATE outbreak_events SET outbreak_descriptor = :name WHERE outbreak_descriptor_catalog_id = :catalogId")
    suspend fun updateOutbreakDescriptorCatalogName(catalogId: Long, name: String): Int

    @Query("SELECT COUNT(*) FROM outbreak_events WHERE outbreak_descriptor_catalog_id = :catalogId")
    suspend fun countOutbreakDescriptorCatalogReferences(catalogId: Long): Int

    @Query("UPDATE outbreak_events SET body_location = :name WHERE body_location_catalog_id = :catalogId")
    suspend fun updateBodyLocationCatalogName(catalogId: Long, name: String): Int

    @Query("SELECT COUNT(*) FROM outbreak_events WHERE body_location_catalog_id = :catalogId")
    suspend fun countBodyLocationCatalogReferences(catalogId: Long): Int

    @Query("SELECT * FROM outbreak_events ORDER BY occurred_at_epoch_ms DESC, id DESC")
    fun observeOutbreakEvents(): Flow<List<OutbreakEventEntity>>
}
