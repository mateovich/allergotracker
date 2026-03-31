package com.example.app.features.outbreak.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "outbreak_events")
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
    @ColumnInfo(name = "outbreak_descriptor_catalog_id")
    val outbreakDescriptorCatalogId: Long? = null,
    @ColumnInfo(name = "body_location_catalog_id")
    val bodyLocationCatalogId: Long? = null
)
