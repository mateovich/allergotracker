package io.github.mateovich.allergotracker.features.eventcatalog.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "reusable_event_catalog",
    indices = [Index(value = ["kind", "normalized_name"], unique = true)]
)
data class ReusableEventCatalogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "kind")
    val kind: String,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "normalized_name")
    val normalizedName: String,
    @ColumnInfo(name = "usage_count")
    val usageCount: Int,
    @ColumnInfo(name = "created_at_epoch_ms")
    val createdAtEpochMs: Long,
    @ColumnInfo(name = "updated_at_epoch_ms")
    val updatedAtEpochMs: Long,
    @ColumnInfo(name = "last_used_at_epoch_ms")
    val lastUsedAtEpochMs: Long
)
