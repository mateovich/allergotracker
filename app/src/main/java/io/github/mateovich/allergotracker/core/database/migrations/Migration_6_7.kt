package io.github.mateovich.allergotracker.core.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val Migration_6_7 = object : Migration(6, 7) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE ingestion_events ADD COLUMN reusable_entity_kind TEXT")
        database.execSQL("ALTER TABLE ingestion_events ADD COLUMN reusable_entity_id INTEGER")
    }
}
