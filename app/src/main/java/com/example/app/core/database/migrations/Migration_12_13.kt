package com.example.app.core.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val Migration_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE exposure_events ADD COLUMN intensity_level INTEGER NOT NULL DEFAULT 3"
        )
        db.execSQL(
            "ALTER TABLE outbreak_events ADD COLUMN intensity_level INTEGER NOT NULL DEFAULT 3"
        )
    }
}
