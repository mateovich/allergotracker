package com.example.app.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "home_messages")
data class HomeMessageEntity(
    @PrimaryKey
    val id: Int,
    @ColumnInfo(name = "message_text")
    val messageText: String,
    @ColumnInfo(name = "updated_at_epoch_ms")
    val updatedAtEpochMs: Long
)
