package io.github.mateovich.allergotracker.features.outbreak.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import io.github.mateovich.allergotracker.features.eventcatalog.data.ReusableEventCatalogEntity

@Entity(
    tableName = "outbreak_events",
    foreignKeys = [
        ForeignKey(
            entity = ReusableEventCatalogEntity::class,
            parentColumns = ["id"],
            childColumns = ["outbreak_descriptor_catalog_id"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.NO_ACTION
        ),
        ForeignKey(
            entity = ReusableEventCatalogEntity::class,
            parentColumns = ["id"],
            childColumns = ["body_location_catalog_id"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.NO_ACTION
        )
    ],
    indices = [Index("outbreak_descriptor_catalog_id"), Index("body_location_catalog_id")]
)
data class OutbreakEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "outbreak_descriptor")
    val outbreakDescriptor: String,
    @ColumnInfo(name = "body_location")
    val bodyLocation: String,
    @ColumnInfo(name = "occurred_at_epoch_ms")
    val occurredAtEpochMs: Long,
    @ColumnInfo(name = "notes")
    val notes: String?,
    @ColumnInfo(name = "created_at_epoch_ms")
    val createdAtEpochMs: Long,
    @ColumnInfo(name = "intensity_level")
    val intensityLevel: Int = 3,
    @ColumnInfo(name = "outbreak_descriptor_catalog_id")
    val outbreakDescriptorCatalogId: Long? = null,
    @ColumnInfo(name = "body_location_catalog_id")
    val bodyLocationCatalogId: Long? = null,
    @ColumnInfo(name = "duration_minutes")
    val durationMinutes: Int? = null
)
