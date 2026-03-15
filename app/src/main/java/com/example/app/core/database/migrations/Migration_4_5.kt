package com.example.app.core.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val Migration_4_5 = object : Migration(4, 5) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS outbreak_events (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "outbreak_descriptor TEXT NOT NULL, " +
                "body_location TEXT NOT NULL, " +
                "occurred_at_epoch_ms INTEGER NOT NULL, " +
                "notes TEXT, " +
                "created_at_epoch_ms INTEGER NOT NULL)"
        )
    }
}
