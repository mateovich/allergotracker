package com.example.app.core.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val Migration_3_4 = object : Migration(3, 4) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS exposure_events (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "exposure_type TEXT NOT NULL, " +
                "occurred_at_epoch_ms INTEGER NOT NULL, " +
                "notes TEXT, " +
                "created_at_epoch_ms INTEGER NOT NULL)"
        )
    }
}
