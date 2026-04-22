package io.github.mateovich.allergotracker.features.exposure.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Update
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ExposureEventDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: ExposureEventEntity): Long

    @Query("SELECT COUNT(*) FROM exposure_events")
    fun observeRecordCount(): Flow<Int>

    @Query("SELECT * FROM exposure_events WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ExposureEventEntity?

    @Query("SELECT * FROM exposure_events WHERE id = :id LIMIT 1")
    fun observeById(id: Long): Flow<ExposureEventEntity?>

    @Update
    suspend fun update(entity: ExposureEventEntity): Int

    @Query("DELETE FROM exposure_events WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("UPDATE exposure_events SET exposure_type = :name WHERE exposure_type_catalog_id = :catalogId")
    suspend fun updateCatalogName(catalogId: Long, name: String): Int

    @Query("SELECT COUNT(*) FROM exposure_events WHERE exposure_type_catalog_id = :catalogId")
    suspend fun countCatalogReferences(catalogId: Long): Int

    @Query("SELECT * FROM exposure_events ORDER BY occurred_at_epoch_ms DESC, id DESC")
    fun observeExposureEvents(): Flow<List<ExposureEventEntity>>
}
