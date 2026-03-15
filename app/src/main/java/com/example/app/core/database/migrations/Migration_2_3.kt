package com.example.app.core.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val Migration_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS ingestion_events (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "item_name TEXT NOT NULL, " +
                "quantity_level INTEGER NOT NULL, " +
                "occurred_at_epoch_ms INTEGER NOT NULL, " +
                "notes TEXT, " +
                "created_at_epoch_ms INTEGER NOT NULL)"
        )
    }
}
