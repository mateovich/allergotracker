package com.example.app.core.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val Migration_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS reusable_event_catalog (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "kind TEXT NOT NULL, " +
                "name TEXT NOT NULL, " +
                "normalized_name TEXT NOT NULL, " +
                "usage_count INTEGER NOT NULL, " +
                "created_at_epoch_ms INTEGER NOT NULL, " +
                "updated_at_epoch_ms INTEGER NOT NULL, " +
                "last_used_at_epoch_ms INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_reusable_event_catalog_kind_normalized_name ON reusable_event_catalog(kind, normalized_name)"
        )

        db.execSQL("ALTER TABLE exposure_events ADD COLUMN exposure_type_catalog_id INTEGER")
        db.execSQL("ALTER TABLE outbreak_events ADD COLUMN outbreak_descriptor_catalog_id INTEGER")
        db.execSQL("ALTER TABLE outbreak_events ADD COLUMN body_location_catalog_id INTEGER")

        backfillCatalogForColumn(
            db = db,
            sourceTable = "exposure_events",
            nameColumn = "exposure_type",
            targetColumn = "exposure_type_catalog_id",
            kind = "exposure_type"
        )
        backfillCatalogForColumn(
            db = db,
            sourceTable = "outbreak_events",
            nameColumn = "outbreak_descriptor",
            targetColumn = "outbreak_descriptor_catalog_id",
            kind = "outbreak_descriptor"
        )
        backfillCatalogForColumn(
            db = db,
            sourceTable = "outbreak_events",
            nameColumn = "body_location",
            targetColumn = "body_location_catalog_id",
            kind = "body_location"
        )
    }
}

private fun backfillCatalogForColumn(
    db: SupportSQLiteDatabase,
    sourceTable: String,
    nameColumn: String,
    targetColumn: String,
    kind: String
) {
    db.query(
        "SELECT DISTINCT TRIM($nameColumn) AS canonical_name, LOWER(TRIM($nameColumn)) AS normalized_name FROM $sourceTable WHERE TRIM($nameColumn) != ''"
    ).use { cursor ->
        val canonicalNameIndex = cursor.getColumnIndexOrThrow("canonical_name")
        val normalizedNameIndex = cursor.getColumnIndexOrThrow("normalized_name")
        while (cursor.moveToNext()) {
            val canonicalName = cursor.getString(canonicalNameIndex)
            val normalizedName = cursor.getString(normalizedNameIndex)
            val usageCount = countOccurrences(db, sourceTable, nameColumn, normalizedName)
            val lastUsedAt = latestOccurredAt(db, sourceTable, nameColumn, normalizedName)
            db.execSQL(
                "INSERT INTO reusable_event_catalog(kind, name, normalized_name, usage_count, created_at_epoch_ms, updated_at_epoch_ms, last_used_at_epoch_ms) VALUES(?, ?, ?, ?, ?, ?, ?)",
                arrayOf(kind, canonicalName, normalizedName, usageCount, lastUsedAt, lastUsedAt, lastUsedAt)
            )
        }
    }

    db.execSQL(
        "UPDATE $sourceTable SET $targetColumn = (SELECT catalog.id FROM reusable_event_catalog AS catalog WHERE catalog.kind = ? AND catalog.normalized_name = LOWER(TRIM($nameColumn))) WHERE TRIM($nameColumn) != ''",
        arrayOf(kind)
    )
}

private fun countOccurrences(
    db: SupportSQLiteDatabase,
    sourceTable: String,
    nameColumn: String,
    normalizedName: String
): Int {
    db.query(
        "SELECT COUNT(*) AS usage_count FROM $sourceTable WHERE LOWER(TRIM($nameColumn)) = ?",
        arrayOf(normalizedName)
    ).use { cursor ->
        cursor.moveToFirst()
        return cursor.getInt(cursor.getColumnIndexOrThrow("usage_count"))
    }
}

private fun latestOccurredAt(
    db: SupportSQLiteDatabase,
    sourceTable: String,
    nameColumn: String,
    normalizedName: String
): Long {
    db.query(
        "SELECT MAX(occurred_at_epoch_ms) AS last_used_at_epoch_ms FROM $sourceTable WHERE LOWER(TRIM($nameColumn)) = ?",
        arrayOf(normalizedName)
    ).use { cursor ->
        cursor.moveToFirst()
        return cursor.getLong(cursor.getColumnIndexOrThrow("last_used_at_epoch_ms"))
    }
}
