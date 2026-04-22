package io.github.mateovich.allergotracker.core.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import io.github.mateovich.allergotracker.features.eventcatalog.domain.canonicalizeReusableEventCatalogName
import io.github.mateovich.allergotracker.features.eventcatalog.domain.normalizeReusableEventCatalogName

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
    val aggregatedEntries = linkedMapOf<String, AggregatedCatalogBackfill>()
    db.query(
        "SELECT $nameColumn, occurred_at_epoch_ms FROM $sourceTable WHERE TRIM($nameColumn) != '' ORDER BY id ASC"
    ).use { cursor ->
        val nameIndex = cursor.getColumnIndexOrThrow(nameColumn)
        val occurredAtIndex = cursor.getColumnIndexOrThrow("occurred_at_epoch_ms")
        while (cursor.moveToNext()) {
            val rawName = cursor.getString(nameIndex)
            val canonicalName = canonicalizeReusableEventCatalogName(rawName)
            val normalizedName = normalizeReusableEventCatalogName(rawName)
            val occurredAt = cursor.getLong(occurredAtIndex)
            val current = aggregatedEntries[normalizedName]
            if (current == null) {
                aggregatedEntries[normalizedName] = AggregatedCatalogBackfill(
                    canonicalName = canonicalName,
                    usageCount = 1,
                    lastUsedAtEpochMs = occurredAt
                )
            } else {
                aggregatedEntries[normalizedName] = current.copy(
                    usageCount = current.usageCount + 1,
                    lastUsedAtEpochMs = maxOf(current.lastUsedAtEpochMs, occurredAt)
                )
            }
        }
    }

    aggregatedEntries.forEach { (normalizedName, entry) ->
        db.execSQL(
            "INSERT INTO reusable_event_catalog(kind, name, normalized_name, usage_count, created_at_epoch_ms, updated_at_epoch_ms, last_used_at_epoch_ms) VALUES(?, ?, ?, ?, ?, ?, ?)",
            arrayOf<Any>(kind, entry.canonicalName, normalizedName, entry.usageCount, entry.lastUsedAtEpochMs, entry.lastUsedAtEpochMs, entry.lastUsedAtEpochMs)
        )
    }

    db.query(
        "SELECT id, $nameColumn FROM $sourceTable WHERE TRIM($nameColumn) != ''"
    ).use { cursor ->
        val idIndex = cursor.getColumnIndexOrThrow("id")
        val nameIndex = cursor.getColumnIndexOrThrow(nameColumn)
        while (cursor.moveToNext()) {
            val rowId = cursor.getLong(idIndex)
            val normalizedName = normalizeReusableEventCatalogName(cursor.getString(nameIndex))
            db.execSQL(
                "UPDATE $sourceTable SET $targetColumn = (SELECT catalog.id FROM reusable_event_catalog AS catalog WHERE catalog.kind = ? AND catalog.normalized_name = ?) WHERE id = ?",
                arrayOf<Any>(kind, normalizedName, rowId)
            )
        }
    }
}

private data class AggregatedCatalogBackfill(
    val canonicalName: String,
    val usageCount: Int,
    val lastUsedAtEpochMs: Long
)
