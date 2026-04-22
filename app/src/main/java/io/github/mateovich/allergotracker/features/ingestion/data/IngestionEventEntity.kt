package io.github.mateovich.allergotracker.features.ingestion.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ingestion_events",
    indices = [
        Index(value = ["reusable_entity_kind"]),
        Index(value = ["reusable_entity_id"])
    ]
)
data class IngestionEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "item_name")
    val itemName: String,
    @ColumnInfo(name = "quantity_level")
    val quantityLevel: Int,
    @ColumnInfo(name = "occurred_at_epoch_ms")
    val occurredAtEpochMs: Long,
    @ColumnInfo(name = "notes")
    val notes: String?,
    @ColumnInfo(name = "reusable_entity_kind")
    val reusableEntityKind: String?,
    @ColumnInfo(name = "reusable_entity_id")
    val reusableEntityId: Long?,
    @ColumnInfo(name = "created_at_epoch_ms")
    val createdAtEpochMs: Long
)
