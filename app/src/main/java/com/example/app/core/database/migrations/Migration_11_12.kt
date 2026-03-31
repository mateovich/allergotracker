package com.example.app.core.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val Migration_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        createCatalogKindTrigger(
            db = db,
            triggerName = "validate_exposure_catalog_kind_insert",
            tableName = "exposure_events",
            columnName = "exposure_type_catalog_id",
            expectedKind = "exposure_type"
        )
        createCatalogKindTrigger(
            db = db,
            triggerName = "validate_exposure_catalog_kind_update",
            tableName = "exposure_events",
            columnName = "exposure_type_catalog_id",
            expectedKind = "exposure_type",
            beforeUpdate = true
        )
        createCatalogKindTrigger(
            db = db,
            triggerName = "validate_outbreak_descriptor_catalog_kind_insert",
            tableName = "outbreak_events",
            columnName = "outbreak_descriptor_catalog_id",
            expectedKind = "outbreak_descriptor"
        )
        createCatalogKindTrigger(
            db = db,
            triggerName = "validate_outbreak_descriptor_catalog_kind_update",
            tableName = "outbreak_events",
            columnName = "outbreak_descriptor_catalog_id",
            expectedKind = "outbreak_descriptor",
            beforeUpdate = true
        )
        createCatalogKindTrigger(
            db = db,
            triggerName = "validate_body_location_catalog_kind_insert",
            tableName = "outbreak_events",
            columnName = "body_location_catalog_id",
            expectedKind = "body_location"
        )
        createCatalogKindTrigger(
            db = db,
            triggerName = "validate_body_location_catalog_kind_update",
            tableName = "outbreak_events",
            columnName = "body_location_catalog_id",
            expectedKind = "body_location",
            beforeUpdate = true
        )
    }
}

private fun createCatalogKindTrigger(
    db: SupportSQLiteDatabase,
    triggerName: String,
    tableName: String,
    columnName: String,
    expectedKind: String,
    beforeUpdate: Boolean = false
) {
    val operation = if (beforeUpdate) "UPDATE" else "INSERT"
    db.execSQL(
        """
        CREATE TRIGGER IF NOT EXISTS $triggerName
        BEFORE $operation ON $tableName
        WHEN NEW.$columnName IS NOT NULL
          AND NOT EXISTS (
            SELECT 1
            FROM reusable_event_catalog
            WHERE id = NEW.$columnName
              AND kind = '$expectedKind'
          )
        BEGIN
          SELECT RAISE(ABORT, 'Invalid reusable event catalog kind for $tableName.$columnName');
        END
        """.trimIndent()
    )
}
