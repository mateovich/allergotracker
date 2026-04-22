package io.github.mateovich.allergotracker.core.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val Migration_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        recreateExposureEventsWithCatalogForeignKey(db)
        recreateOutbreakEventsWithCatalogForeignKeys(db)
    }
}

private fun recreateExposureEventsWithCatalogForeignKey(db: SupportSQLiteDatabase) {
    db.execSQL(
        "CREATE TABLE IF NOT EXISTS exposure_events_new (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "exposure_type TEXT NOT NULL, " +
            "occurred_at_epoch_ms INTEGER NOT NULL, " +
            "notes TEXT, " +
            "created_at_epoch_ms INTEGER NOT NULL, " +
            "exposure_type_catalog_id INTEGER, " +
            "FOREIGN KEY(exposure_type_catalog_id) REFERENCES reusable_event_catalog(id) ON UPDATE NO ACTION ON DELETE RESTRICT)"
    )
    db.execSQL(
        "INSERT INTO exposure_events_new(id, exposure_type, occurred_at_epoch_ms, notes, created_at_epoch_ms, exposure_type_catalog_id) " +
            "SELECT id, exposure_type, occurred_at_epoch_ms, notes, created_at_epoch_ms, exposure_type_catalog_id FROM exposure_events"
    )
    db.execSQL("DROP TABLE exposure_events")
    db.execSQL("ALTER TABLE exposure_events_new RENAME TO exposure_events")
    db.execSQL("CREATE INDEX IF NOT EXISTS index_exposure_events_exposure_type_catalog_id ON exposure_events(exposure_type_catalog_id)")
}

private fun recreateOutbreakEventsWithCatalogForeignKeys(db: SupportSQLiteDatabase) {
    db.execSQL(
        "CREATE TABLE IF NOT EXISTS outbreak_events_new (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "outbreak_descriptor TEXT NOT NULL, " +
            "body_location TEXT NOT NULL, " +
            "occurred_at_epoch_ms INTEGER NOT NULL, " +
            "notes TEXT, " +
            "created_at_epoch_ms INTEGER NOT NULL, " +
            "outbreak_descriptor_catalog_id INTEGER, " +
            "body_location_catalog_id INTEGER, " +
            "FOREIGN KEY(outbreak_descriptor_catalog_id) REFERENCES reusable_event_catalog(id) ON UPDATE NO ACTION ON DELETE RESTRICT, " +
            "FOREIGN KEY(body_location_catalog_id) REFERENCES reusable_event_catalog(id) ON UPDATE NO ACTION ON DELETE RESTRICT)"
    )
    db.execSQL(
        "INSERT INTO outbreak_events_new(id, outbreak_descriptor, body_location, occurred_at_epoch_ms, notes, created_at_epoch_ms, outbreak_descriptor_catalog_id, body_location_catalog_id) " +
            "SELECT id, outbreak_descriptor, body_location, occurred_at_epoch_ms, notes, created_at_epoch_ms, outbreak_descriptor_catalog_id, body_location_catalog_id FROM outbreak_events"
    )
    db.execSQL("DROP TABLE outbreak_events")
    db.execSQL("ALTER TABLE outbreak_events_new RENAME TO outbreak_events")
    db.execSQL("CREATE INDEX IF NOT EXISTS index_outbreak_events_outbreak_descriptor_catalog_id ON outbreak_events(outbreak_descriptor_catalog_id)")
    db.execSQL("CREATE INDEX IF NOT EXISTS index_outbreak_events_body_location_catalog_id ON outbreak_events(body_location_catalog_id)")
}
