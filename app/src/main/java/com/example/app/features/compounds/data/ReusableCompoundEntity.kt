package com.example.app.features.compounds.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "reusable_compounds",
    indices = [Index(value = ["normalized_name"], unique = true)]
)
data class ReusableCompoundEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "normalized_name")
    val normalizedName: String,
    @ColumnInfo(name = "notes")
    val notes: String?,
    @ColumnInfo(name = "composition_status")
    val compositionStatus: String,
    @ColumnInfo(name = "created_at_epoch_ms")
    val createdAtEpochMs: Long,
    @ColumnInfo(name = "updated_at_epoch_ms")
    val updatedAtEpochMs: Long
)
