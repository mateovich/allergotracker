package com.example.app.core.database

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.app.core.database.migrations.Migration_1_2
import com.example.app.core.database.migrations.Migration_2_3
import com.example.app.core.database.migrations.Migration_3_4
import com.example.app.core.database.migrations.Migration_4_5
import com.example.app.core.database.migrations.Migration_5_6
import com.example.app.core.database.migrations.Migration_6_7
import com.example.app.core.database.migrations.Migration_7_8
import com.example.app.core.database.migrations.Migration_8_9
import com.example.app.core.database.migrations.Migration_9_10
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

    @Test
    fun migration_1_2_adds_updated_at_column_and_keeps_existing_data() {
        val appContext = ApplicationProvider.getApplicationContext<android.content.Context>()
        val dbName = "migration-1-2-test"
        val dbFile = appContext.getDatabasePath(dbName)

        resetDatabaseFile(dbFile)
        SQLiteDatabase.openOrCreateDatabase(dbFile, null).use { database ->
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS home_messages (" +
                    "id INTEGER NOT NULL, " +
                    "message_text TEXT NOT NULL, " +
                    "PRIMARY KEY(id))"
            )
            database.execSQL("INSERT INTO home_messages (id, message_text) VALUES (1, 'hello')")
            database.version = 1
        }

        Room.databaseBuilder(appContext, AppDatabase::class.java, dbName)
            .addMigrations(Migration_1_2, Migration_2_3, Migration_3_4, Migration_4_5, Migration_5_6, Migration_6_7, Migration_7_8, Migration_8_9, Migration_9_10)
            .build()
            .openHelper
            .writableDatabase
            .use { migratedDb ->
                migratedDb.query("PRAGMA table_info(`home_messages`)").use { cursor ->
                    var foundUpdatedAtColumn = false
                    val nameIndex = cursor.getColumnIndex("name")
                    val notNullIndex = cursor.getColumnIndex("notnull")

                    while (cursor.moveToNext()) {
                        if (cursor.getString(nameIndex) == "updated_at_epoch_ms") {
                            foundUpdatedAtColumn = true
                            assertEquals(1, cursor.getInt(notNullIndex))
                        }
                    }

                    assertTrue(foundUpdatedAtColumn)
                }

                migratedDb.query("SELECT updated_at_epoch_ms FROM home_messages WHERE id = 1").use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals(0L, cursor.getLong(0))
                }
            }
    }

    @Test
    fun migration_2_3_creates_ingestion_events_table() {
        val appContext = ApplicationProvider.getApplicationContext<android.content.Context>()
        val dbName = "migration-2-3-test"
        val dbFile = appContext.getDatabasePath(dbName)

        resetDatabaseFile(dbFile)
        SQLiteDatabase.openOrCreateDatabase(dbFile, null).use { database ->
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS home_messages (" +
                    "id INTEGER NOT NULL, " +
                    "message_text TEXT NOT NULL, " +
                    "updated_at_epoch_ms INTEGER NOT NULL DEFAULT 0, " +
                    "PRIMARY KEY(id))"
            )
            database.version = 2
        }

        Room.databaseBuilder(appContext, AppDatabase::class.java, dbName)
            .addMigrations(Migration_2_3, Migration_3_4, Migration_4_5, Migration_5_6, Migration_6_7, Migration_7_8, Migration_8_9, Migration_9_10)
            .build()
            .openHelper
            .writableDatabase
            .use { migratedDb ->
                migratedDb.query("PRAGMA table_info(`ingestion_events`)").use { cursor ->
                    var foundItemName = false
                    var foundQuantityLevel = false

                    val nameIndex = cursor.getColumnIndex("name")
                    while (cursor.moveToNext()) {
                        when (cursor.getString(nameIndex)) {
                            "item_name" -> foundItemName = true
                            "quantity_level" -> foundQuantityLevel = true
                        }
                    }

                    assertTrue(foundItemName)
                    assertTrue(foundQuantityLevel)
                }
            }
    }

    @Test
    fun migration_3_4_creates_exposure_events_table() {
        val appContext = ApplicationProvider.getApplicationContext<android.content.Context>()
        val dbName = "migration-3-4-test"
        val dbFile = appContext.getDatabasePath(dbName)

        resetDatabaseFile(dbFile)
        SQLiteDatabase.openOrCreateDatabase(dbFile, null).use { database ->
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS home_messages (" +
                    "id INTEGER NOT NULL, " +
                    "message_text TEXT NOT NULL, " +
                    "updated_at_epoch_ms INTEGER NOT NULL DEFAULT 0, " +
                    "PRIMARY KEY(id))"
            )
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS ingestion_events (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "item_name TEXT NOT NULL, " +
                    "quantity_level INTEGER NOT NULL, " +
                    "occurred_at_epoch_ms INTEGER NOT NULL, " +
                    "notes TEXT, " +
                    "created_at_epoch_ms INTEGER NOT NULL)"
            )
            database.version = 3
        }

        Room.databaseBuilder(appContext, AppDatabase::class.java, dbName)
            .addMigrations(Migration_3_4, Migration_4_5, Migration_5_6, Migration_6_7, Migration_7_8, Migration_8_9, Migration_9_10)
            .build()
            .openHelper
            .writableDatabase
            .use { migratedDb ->
                migratedDb.query("PRAGMA table_info(`exposure_events`)").use { cursor ->
                    var foundExposureType = false
                    var foundOccurredAt = false

                    val nameIndex = cursor.getColumnIndex("name")
                    while (cursor.moveToNext()) {
                        when (cursor.getString(nameIndex)) {
                            "exposure_type" -> foundExposureType = true
                            "occurred_at_epoch_ms" -> foundOccurredAt = true
                        }
                    }

                    assertTrue(foundExposureType)
                    assertTrue(foundOccurredAt)
                }
            }
    }

    @Test
    fun migration_4_5_creates_outbreak_events_table() {
        val appContext = ApplicationProvider.getApplicationContext<android.content.Context>()
        val dbName = "migration-4-5-test"
        val dbFile = appContext.getDatabasePath(dbName)

        resetDatabaseFile(dbFile)
        SQLiteDatabase.openOrCreateDatabase(dbFile, null).use { database ->
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS home_messages (" +
                    "id INTEGER NOT NULL, " +
                    "message_text TEXT NOT NULL, " +
                    "updated_at_epoch_ms INTEGER NOT NULL DEFAULT 0, " +
                    "PRIMARY KEY(id))"
            )
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS ingestion_events (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "item_name TEXT NOT NULL, " +
                    "quantity_level INTEGER NOT NULL, " +
                    "occurred_at_epoch_ms INTEGER NOT NULL, " +
                    "notes TEXT, " +
                    "created_at_epoch_ms INTEGER NOT NULL)"
            )
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS exposure_events (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "exposure_type TEXT NOT NULL, " +
                    "occurred_at_epoch_ms INTEGER NOT NULL, " +
                    "notes TEXT, " +
                    "created_at_epoch_ms INTEGER NOT NULL)"
            )
            database.version = 4
        }

        Room.databaseBuilder(appContext, AppDatabase::class.java, dbName)
            .addMigrations(Migration_4_5, Migration_5_6, Migration_6_7, Migration_7_8, Migration_8_9, Migration_9_10)
            .build()
            .openHelper
            .writableDatabase
            .use { migratedDb ->
                migratedDb.query("PRAGMA table_info(`outbreak_events`)").use { cursor ->
                    var foundDescriptor = false
                    var foundBodyLocation = false

                    val nameIndex = cursor.getColumnIndex("name")
                    while (cursor.moveToNext()) {
                        when (cursor.getString(nameIndex)) {
                            "outbreak_descriptor" -> foundDescriptor = true
                            "body_location" -> foundBodyLocation = true
                        }
                    }

                    assertTrue(foundDescriptor)
                    assertTrue(foundBodyLocation)
                }
            }
    }

    @Test
    fun migration_5_6_creates_reusable_catalog_tables() {
        val appContext = ApplicationProvider.getApplicationContext<android.content.Context>()
        val dbName = "migration-5-6-test"
        val dbFile = appContext.getDatabasePath(dbName)

        resetDatabaseFile(dbFile)
        SQLiteDatabase.openOrCreateDatabase(dbFile, null).use { database ->
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS home_messages (" +
                    "id INTEGER NOT NULL, " +
                    "message_text TEXT NOT NULL, " +
                    "updated_at_epoch_ms INTEGER NOT NULL DEFAULT 0, " +
                    "PRIMARY KEY(id))"
            )
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS ingestion_events (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "item_name TEXT NOT NULL, " +
                    "quantity_level INTEGER NOT NULL, " +
                    "occurred_at_epoch_ms INTEGER NOT NULL, " +
                    "notes TEXT, " +
                    "created_at_epoch_ms INTEGER NOT NULL)"
            )
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS exposure_events (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "exposure_type TEXT NOT NULL, " +
                    "occurred_at_epoch_ms INTEGER NOT NULL, " +
                    "notes TEXT, " +
                    "created_at_epoch_ms INTEGER NOT NULL)"
            )
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS outbreak_events (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "outbreak_descriptor TEXT NOT NULL, " +
                    "body_location TEXT NOT NULL, " +
                    "occurred_at_epoch_ms INTEGER NOT NULL, " +
                    "notes TEXT, " +
                    "created_at_epoch_ms INTEGER NOT NULL)"
            )
            database.version = 5
        }

        Room.databaseBuilder(appContext, AppDatabase::class.java, dbName)
            .addMigrations(Migration_5_6, Migration_6_7, Migration_7_8, Migration_8_9, Migration_9_10)
            .build()
            .openHelper
            .writableDatabase
            .use { migratedDb ->
                migratedDb.query("PRAGMA table_info(`reusable_ingredients`)").use { cursor ->
                    var foundName = false
                    var foundUpdatedAt = false
                    val nameIndex = cursor.getColumnIndex("name")

                    while (cursor.moveToNext()) {
                        when (cursor.getString(nameIndex)) {
                            "name" -> foundName = true
                            "updated_at_epoch_ms" -> foundUpdatedAt = true
                        }
                    }

                    assertTrue(foundName)
                    assertTrue(foundUpdatedAt)
                }

                migratedDb.query("PRAGMA table_info(`reusable_compound_components`)").use { cursor ->
                    var foundCompoundId = false
                    var foundProportion = false
                    val nameIndex = cursor.getColumnIndex("name")

                    while (cursor.moveToNext()) {
                        when (cursor.getString(nameIndex)) {
                            "compound_id" -> foundCompoundId = true
                            "proportion_basis_points" -> foundProportion = true
                        }
                    }

                    assertTrue(foundCompoundId)
                    assertTrue(foundProportion)
                }
            }
    }

    @Test
    fun migration_6_7_adds_reusable_reference_columns_to_ingestion_events() {
        val appContext = ApplicationProvider.getApplicationContext<android.content.Context>()
        val dbName = "migration-6-7-test"
        val dbFile = appContext.getDatabasePath(dbName)

        resetDatabaseFile(dbFile)
        SQLiteDatabase.openOrCreateDatabase(dbFile, null).use { database ->
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS home_messages (" +
                    "id INTEGER NOT NULL, " +
                    "message_text TEXT NOT NULL, " +
                    "updated_at_epoch_ms INTEGER NOT NULL DEFAULT 0, " +
                    "PRIMARY KEY(id))"
            )
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS ingestion_events (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "item_name TEXT NOT NULL, " +
                    "quantity_level INTEGER NOT NULL, " +
                    "occurred_at_epoch_ms INTEGER NOT NULL, " +
                    "notes TEXT, " +
                    "created_at_epoch_ms INTEGER NOT NULL)"
            )
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS exposure_events (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "exposure_type TEXT NOT NULL, " +
                    "occurred_at_epoch_ms INTEGER NOT NULL, " +
                    "notes TEXT, " +
                    "created_at_epoch_ms INTEGER NOT NULL)"
            )
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS outbreak_events (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "outbreak_descriptor TEXT NOT NULL, " +
                    "body_location TEXT NOT NULL, " +
                    "occurred_at_epoch_ms INTEGER NOT NULL, " +
                    "notes TEXT, " +
                    "created_at_epoch_ms INTEGER NOT NULL)"
            )
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS reusable_ingredients (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "name TEXT NOT NULL, " +
                    "notes TEXT, " +
                    "created_at_epoch_ms INTEGER NOT NULL, " +
                    "updated_at_epoch_ms INTEGER NOT NULL)"
            )
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS reusable_compounds (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "name TEXT NOT NULL, " +
                    "notes TEXT, " +
                    "composition_status TEXT NOT NULL, " +
                    "created_at_epoch_ms INTEGER NOT NULL, " +
                    "updated_at_epoch_ms INTEGER NOT NULL)"
            )
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS reusable_compound_components (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "compound_id INTEGER NOT NULL, " +
                    "position INTEGER NOT NULL, " +
                    "component_kind TEXT NOT NULL, " +
                    "ingredient_id INTEGER, " +
                    "child_compound_id INTEGER, " +
                    "proportion_basis_points INTEGER, " +
                    "FOREIGN KEY(compound_id) REFERENCES reusable_compounds(id) ON UPDATE NO ACTION ON DELETE CASCADE, " +
                    "FOREIGN KEY(ingredient_id) REFERENCES reusable_ingredients(id) ON UPDATE NO ACTION ON DELETE RESTRICT, " +
                    "FOREIGN KEY(child_compound_id) REFERENCES reusable_compounds(id) ON UPDATE NO ACTION ON DELETE RESTRICT)"
            )
            database.version = 6
        }

        Room.databaseBuilder(appContext, AppDatabase::class.java, dbName)
            .addMigrations(Migration_6_7, Migration_7_8, Migration_8_9, Migration_9_10)
            .build()
            .openHelper
            .writableDatabase
            .use { migratedDb ->
                migratedDb.query("PRAGMA table_info(`ingestion_events`)").use { cursor ->
                    var foundReusableKind = false
                    var foundReusableId = false
                    val nameIndex = cursor.getColumnIndex("name")

                    while (cursor.moveToNext()) {
                        when (cursor.getString(nameIndex)) {
                            "reusable_entity_kind" -> foundReusableKind = true
                            "reusable_entity_id" -> foundReusableId = true
                        }
                    }

                    assertTrue(foundReusableKind)
                    assertTrue(foundReusableId)
                }
            }
    }

    @Test
    fun migration_7_8_adds_component_invariant_index() {
        val appContext = ApplicationProvider.getApplicationContext<android.content.Context>()
        val dbName = "migration-7-8-test"
        val dbFile = appContext.getDatabasePath(dbName)

        resetDatabaseFile(dbFile)
        SQLiteDatabase.openOrCreateDatabase(dbFile, null).use { database ->
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS home_messages (" +
                    "id INTEGER NOT NULL, " +
                    "message_text TEXT NOT NULL, " +
                    "updated_at_epoch_ms INTEGER NOT NULL DEFAULT 0, " +
                    "PRIMARY KEY(id))"
            )
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS ingestion_events (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "item_name TEXT NOT NULL, " +
                    "quantity_level INTEGER NOT NULL, " +
                    "occurred_at_epoch_ms INTEGER NOT NULL, " +
                    "notes TEXT, " +
                    "reusable_entity_kind TEXT, " +
                    "reusable_entity_id INTEGER, " +
                    "created_at_epoch_ms INTEGER NOT NULL)"
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS index_ingestion_events_reusable_entity_kind ON ingestion_events(reusable_entity_kind)"
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS index_ingestion_events_reusable_entity_id ON ingestion_events(reusable_entity_id)"
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS index_ingestion_events_reusable_entity_kind ON ingestion_events(reusable_entity_kind)"
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS index_ingestion_events_reusable_entity_id ON ingestion_events(reusable_entity_id)"
            )
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS exposure_events (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "exposure_type TEXT NOT NULL, " +
                    "occurred_at_epoch_ms INTEGER NOT NULL, " +
                    "notes TEXT, " +
                    "created_at_epoch_ms INTEGER NOT NULL)"
            )
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS outbreak_events (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "outbreak_descriptor TEXT NOT NULL, " +
                    "body_location TEXT NOT NULL, " +
                    "occurred_at_epoch_ms INTEGER NOT NULL, " +
                    "notes TEXT, " +
                    "created_at_epoch_ms INTEGER NOT NULL)"
            )
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS reusable_ingredients (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "name TEXT NOT NULL, " +
                    "notes TEXT, " +
                    "created_at_epoch_ms INTEGER NOT NULL, " +
                    "updated_at_epoch_ms INTEGER NOT NULL)"
            )
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS reusable_compounds (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "name TEXT NOT NULL, " +
                    "notes TEXT, " +
                    "composition_status TEXT NOT NULL, " +
                    "created_at_epoch_ms INTEGER NOT NULL, " +
                    "updated_at_epoch_ms INTEGER NOT NULL)"
            )
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS reusable_compound_components (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "compound_id INTEGER NOT NULL, " +
                    "position INTEGER NOT NULL, " +
                    "component_kind TEXT NOT NULL, " +
                    "ingredient_id INTEGER, " +
                    "child_compound_id INTEGER, " +
                    "proportion_basis_points INTEGER, " +
                    "FOREIGN KEY(compound_id) REFERENCES reusable_compounds(id) ON UPDATE NO ACTION ON DELETE CASCADE, " +
                    "FOREIGN KEY(ingredient_id) REFERENCES reusable_ingredients(id) ON UPDATE NO ACTION ON DELETE RESTRICT, " +
                    "FOREIGN KEY(child_compound_id) REFERENCES reusable_compounds(id) ON UPDATE NO ACTION ON DELETE RESTRICT)"
            )
            database.version = 7
        }

        Room.databaseBuilder(appContext, AppDatabase::class.java, dbName)
            .addMigrations(Migration_7_8, Migration_8_9, Migration_9_10)
            .build()
            .openHelper
            .writableDatabase
            .use { migratedDb ->
                migratedDb.query("PRAGMA index_list(`reusable_compound_components`)").use { cursor ->
                    var foundUniquePositionIndex = false
                    val nameIndex = cursor.getColumnIndex("name")

                    while (cursor.moveToNext()) {
                        if (cursor.getString(nameIndex) == "index_reusable_compound_components_compound_id_position") {
                            foundUniquePositionIndex = true
                        }
                    }

                    assertTrue(foundUniquePositionIndex)
                }
            }
    }

    @Test
    fun migration_8_9_adds_reusable_indexes_and_normalized_names() {
        val appContext = ApplicationProvider.getApplicationContext<android.content.Context>()
        val dbName = "migration-8-9-test"
        val dbFile = appContext.getDatabasePath(dbName)

        resetDatabaseFile(dbFile)
        SQLiteDatabase.openOrCreateDatabase(dbFile, null).use { database ->
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS home_messages (" +
                    "id INTEGER NOT NULL, " +
                    "message_text TEXT NOT NULL, " +
                    "updated_at_epoch_ms INTEGER NOT NULL DEFAULT 0, " +
                    "PRIMARY KEY(id))"
            )
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS ingestion_events (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "item_name TEXT NOT NULL, " +
                    "quantity_level INTEGER NOT NULL, " +
                    "occurred_at_epoch_ms INTEGER NOT NULL, " +
                    "notes TEXT, " +
                    "reusable_entity_kind TEXT, " +
                    "reusable_entity_id INTEGER, " +
                    "created_at_epoch_ms INTEGER NOT NULL)"
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS index_ingestion_events_reusable_entity_kind ON ingestion_events(reusable_entity_kind)"
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS index_ingestion_events_reusable_entity_id ON ingestion_events(reusable_entity_id)"
            )
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS exposure_events (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "exposure_type TEXT NOT NULL, " +
                    "occurred_at_epoch_ms INTEGER NOT NULL, " +
                    "notes TEXT, " +
                    "created_at_epoch_ms INTEGER NOT NULL)"
            )
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS outbreak_events (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "outbreak_descriptor TEXT NOT NULL, " +
                    "body_location TEXT NOT NULL, " +
                    "occurred_at_epoch_ms INTEGER NOT NULL, " +
                    "notes TEXT, " +
                    "created_at_epoch_ms INTEGER NOT NULL)"
            )
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS reusable_ingredients (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "name TEXT NOT NULL, " +
                    "notes TEXT, " +
                    "created_at_epoch_ms INTEGER NOT NULL, " +
                    "updated_at_epoch_ms INTEGER NOT NULL)"
            )
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS reusable_compounds (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "name TEXT NOT NULL, " +
                    "notes TEXT, " +
                    "composition_status TEXT NOT NULL, " +
                    "created_at_epoch_ms INTEGER NOT NULL, " +
                    "updated_at_epoch_ms INTEGER NOT NULL)"
            )
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS reusable_compound_components (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "compound_id INTEGER NOT NULL, " +
                    "position INTEGER NOT NULL, " +
                    "component_kind TEXT NOT NULL, " +
                    "ingredient_id INTEGER, " +
                    "child_compound_id INTEGER, " +
                    "proportion_basis_points INTEGER, " +
                    "FOREIGN KEY(compound_id) REFERENCES reusable_compounds(id) ON UPDATE NO ACTION ON DELETE CASCADE, " +
                    "FOREIGN KEY(ingredient_id) REFERENCES reusable_ingredients(id) ON UPDATE NO ACTION ON DELETE RESTRICT, " +
                    "FOREIGN KEY(child_compound_id) REFERENCES reusable_compounds(id) ON UPDATE NO ACTION ON DELETE RESTRICT)"
            )
            database.execSQL("CREATE INDEX IF NOT EXISTS index_reusable_compounds_name ON reusable_compounds(name)")
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS index_reusable_compound_components_compound_id ON reusable_compound_components(compound_id)"
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS index_reusable_compound_components_ingredient_id ON reusable_compound_components(ingredient_id)"
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS index_reusable_compound_components_child_compound_id ON reusable_compound_components(child_compound_id)"
            )
            database.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_reusable_compound_components_compound_id_position ON reusable_compound_components(compound_id, position)"
            )
            database.version = 8
        }

        Room.databaseBuilder(appContext, AppDatabase::class.java, dbName)
            .addMigrations(Migration_8_9, Migration_9_10)
            .build()
            .openHelper
            .writableDatabase
            .use { migratedDb ->
                migratedDb.query("PRAGMA table_info(`reusable_ingredients`)").use { cursor ->
                    var foundNormalizedName = false
                    val nameIndex = cursor.getColumnIndex("name")

                    while (cursor.moveToNext()) {
                        if (cursor.getString(nameIndex) == "normalized_name") {
                            foundNormalizedName = true
                        }
                    }

                    assertTrue(foundNormalizedName)
                }

                migratedDb.query("PRAGMA index_list(`ingestion_events`)").use { cursor ->
                    var foundKindIndex = false
                    var foundIdIndex = false
                    val nameIndex = cursor.getColumnIndex("name")

                    while (cursor.moveToNext()) {
                        when (cursor.getString(nameIndex)) {
                            "index_ingestion_events_reusable_entity_kind" -> foundKindIndex = true
                            "index_ingestion_events_reusable_entity_id" -> foundIdIndex = true
                        }
                    }

                    assertTrue(foundKindIndex)
                    assertTrue(foundIdIndex)
                }

                migratedDb.query("PRAGMA index_list(`reusable_ingredients`)").use { cursor ->
                    var foundUniqueNormalizedIndex = false
                    val nameIndex = cursor.getColumnIndex("name")
                    val uniqueIndex = cursor.getColumnIndex("unique")

                    while (cursor.moveToNext()) {
                        if (cursor.getString(nameIndex) == "index_reusable_ingredients_normalized_name") {
                            foundUniqueNormalizedIndex = cursor.getInt(uniqueIndex) == 1
                        }
                    }

                    assertTrue(foundUniqueNormalizedIndex)
                }
            }
    }

    @Test
    fun migration_9_10_creates_reusable_event_catalog_and_backfills_references() {
        val appContext = ApplicationProvider.getApplicationContext<android.content.Context>()
        val dbName = "migration-9-10-test"
        val dbFile = appContext.getDatabasePath(dbName)

        resetDatabaseFile(dbFile)
        SQLiteDatabase.openOrCreateDatabase(dbFile, null).use { database ->
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS home_messages (" +
                    "id INTEGER NOT NULL, " +
                    "message_text TEXT NOT NULL, " +
                    "updated_at_epoch_ms INTEGER NOT NULL DEFAULT 0, " +
                    "PRIMARY KEY(id))"
            )
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS ingestion_events (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "item_name TEXT NOT NULL, " +
                    "quantity_level INTEGER NOT NULL, " +
                    "occurred_at_epoch_ms INTEGER NOT NULL, " +
                    "notes TEXT, " +
                    "reusable_entity_kind TEXT, " +
                    "reusable_entity_id INTEGER, " +
                    "created_at_epoch_ms INTEGER NOT NULL)"
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS index_ingestion_events_reusable_entity_kind ON ingestion_events(reusable_entity_kind)"
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS index_ingestion_events_reusable_entity_id ON ingestion_events(reusable_entity_id)"
            )
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS exposure_events (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "exposure_type TEXT NOT NULL, " +
                    "occurred_at_epoch_ms INTEGER NOT NULL, " +
                    "notes TEXT, " +
                    "created_at_epoch_ms INTEGER NOT NULL)"
            )
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS outbreak_events (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "outbreak_descriptor TEXT NOT NULL, " +
                    "body_location TEXT NOT NULL, " +
                    "occurred_at_epoch_ms INTEGER NOT NULL, " +
                    "notes TEXT, " +
                    "created_at_epoch_ms INTEGER NOT NULL)"
            )
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS reusable_ingredients (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "name TEXT NOT NULL, " +
                    "normalized_name TEXT NOT NULL, " +
                    "notes TEXT, " +
                    "created_at_epoch_ms INTEGER NOT NULL, " +
                    "updated_at_epoch_ms INTEGER NOT NULL)"
            )
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS reusable_compounds (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "name TEXT NOT NULL, " +
                    "normalized_name TEXT NOT NULL, " +
                    "notes TEXT, " +
                    "composition_status TEXT NOT NULL, " +
                    "created_at_epoch_ms INTEGER NOT NULL, " +
                    "updated_at_epoch_ms INTEGER NOT NULL)"
            )
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS reusable_compound_components (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "compound_id INTEGER NOT NULL, " +
                    "position INTEGER NOT NULL, " +
                    "component_kind TEXT NOT NULL, " +
                    "ingredient_id INTEGER, " +
                    "child_compound_id INTEGER, " +
                    "proportion_basis_points INTEGER, " +
                    "FOREIGN KEY(compound_id) REFERENCES reusable_compounds(id) ON UPDATE NO ACTION ON DELETE CASCADE, " +
                    "FOREIGN KEY(ingredient_id) REFERENCES reusable_ingredients(id) ON UPDATE NO ACTION ON DELETE RESTRICT, " +
                    "FOREIGN KEY(child_compound_id) REFERENCES reusable_compounds(id) ON UPDATE NO ACTION ON DELETE RESTRICT)"
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS index_reusable_compound_components_compound_id ON reusable_compound_components(compound_id)"
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS index_reusable_compound_components_ingredient_id ON reusable_compound_components(ingredient_id)"
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS index_reusable_compound_components_child_compound_id ON reusable_compound_components(child_compound_id)"
            )
            database.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_reusable_compound_components_compound_id_position ON reusable_compound_components(compound_id, position)"
            )
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_reusable_ingredients_normalized_name ON reusable_ingredients(normalized_name)")
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_reusable_compounds_normalized_name ON reusable_compounds(normalized_name)")
            database.execSQL("INSERT INTO exposure_events(id, exposure_type, occurred_at_epoch_ms, notes, created_at_epoch_ms) VALUES (1, 'Pollen', 1000, NULL, 1000)")
            database.execSQL("INSERT INTO outbreak_events(id, outbreak_descriptor, body_location, occurred_at_epoch_ms, notes, created_at_epoch_ms) VALUES (1, 'Skin flare', 'Left arm', 2000, NULL, 2000)")
            database.version = 9
        }

        Room.databaseBuilder(appContext, AppDatabase::class.java, dbName)
            .addMigrations(Migration_9_10)
            .build()
            .openHelper
            .writableDatabase
            .use { migratedDb ->
                migratedDb.query("PRAGMA table_info(`reusable_event_catalog`)").use { cursor ->
                    var foundUsageCount = false
                    var foundLastUsed = false
                    val nameIndex = cursor.getColumnIndex("name")

                    while (cursor.moveToNext()) {
                        when (cursor.getString(nameIndex)) {
                            "usage_count" -> foundUsageCount = true
                            "last_used_at_epoch_ms" -> foundLastUsed = true
                        }
                    }

                    assertTrue(foundUsageCount)
                    assertTrue(foundLastUsed)
                }

                migratedDb.query("SELECT exposure_type_catalog_id FROM exposure_events WHERE id = 1").use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertTrue(cursor.getLong(0) > 0L)
                }

                migratedDb.query("SELECT outbreak_descriptor_catalog_id, body_location_catalog_id FROM outbreak_events WHERE id = 1").use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertTrue(cursor.getLong(0) > 0L)
                    assertTrue(cursor.getLong(1) > 0L)
                }
            }
    }

    @Test
    fun database_open_fails_without_required_migration_when_fallback_not_enabled() {
        val appContext = ApplicationProvider.getApplicationContext<android.content.Context>()
        val dbName = "migration-missing-test"
        val dbFile = appContext.getDatabasePath(dbName)

        resetDatabaseFile(dbFile)
        SQLiteDatabase.openOrCreateDatabase(dbFile, null).use { database ->
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS home_messages (" +
                    "id INTEGER NOT NULL, " +
                    "message_text TEXT NOT NULL, " +
                    "PRIMARY KEY(id))"
            )
            database.version = 1
        }

        try {
            Room.databaseBuilder(appContext, AppDatabase::class.java, dbName)
                .build()
                .openHelper
                .writableDatabase
            throw AssertionError("Expected migration failure when no migration is registered")
        } catch (error: IllegalStateException) {
            assertTrue(error.message?.contains("A migration from 1 to 10 was required") == true)
        }
    }

    private fun resetDatabaseFile(dbFile: File) {
        dbFile.parentFile?.mkdirs()
        if (dbFile.exists()) {
            dbFile.delete()
        }
        File("${dbFile.path}-journal").delete()
        File("${dbFile.path}-shm").delete()
        File("${dbFile.path}-wal").delete()
    }
}
