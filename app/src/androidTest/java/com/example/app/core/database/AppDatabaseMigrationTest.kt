package com.example.app.core.database

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.app.core.database.migrations.Migration_1_2
import com.example.app.core.database.migrations.Migration_2_3
import com.example.app.core.database.migrations.Migration_3_4
import com.example.app.core.database.migrations.Migration_4_5
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
            .addMigrations(Migration_1_2, Migration_2_3, Migration_3_4, Migration_4_5)
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
            .addMigrations(Migration_2_3, Migration_3_4, Migration_4_5)
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
            .addMigrations(Migration_3_4, Migration_4_5)
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
            .addMigrations(Migration_4_5)
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
            assertTrue(error.message?.contains("A migration from 1 to 5 was required") == true)
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
