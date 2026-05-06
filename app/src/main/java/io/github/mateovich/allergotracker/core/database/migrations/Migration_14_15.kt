package io.github.mateovich.allergotracker.core.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val Migration_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE exposure_events ADD COLUMN duration_minutes INTEGER")
        db.execSQL("ALTER TABLE outbreak_events ADD COLUMN duration_minutes INTEGER")
    }
}
