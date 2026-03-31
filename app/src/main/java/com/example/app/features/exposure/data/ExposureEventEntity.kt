package com.example.app.features.exposure.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "exposure_events")
data class ExposureEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "exposure_type")
    val exposureType: String,
    @ColumnInfo(name = "occurred_at_epoch_ms")
    val occurredAtEpochMs: Long,
    @ColumnInfo(name = "notes")
    val notes: String?,
    @ColumnInfo(name = "created_at_epoch_ms")
    val createdAtEpochMs: Long,
    @ColumnInfo(name = "exposure_type_catalog_id")
    val exposureTypeCatalogId: Long? = null
)
