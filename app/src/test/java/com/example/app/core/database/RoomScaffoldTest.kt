package com.example.app.core.database

import com.example.app.core.database.migrations.Migration_1_2
import com.example.app.core.database.migrations.Migration_2_3
import com.example.app.core.database.migrations.Migration_3_4
import com.example.app.core.database.migrations.Migration_4_5
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomScaffoldTest {

    @Test
    fun app_database_source_declares_expected_room_metadata() {
        val databaseFile = File(
            System.getProperty("user.dir"),
            "src/main/java/com/example/app/core/database/AppDatabase.kt"
        )

        assertTrue(databaseFile.exists())
        val source = databaseFile.readText()

        assertTrue(source.contains("@Database("))
        assertTrue(
            source.contains(
                "entities = [HomeMessageEntity::class, IngestionEventEntity::class, ExposureEventEntity::class, OutbreakEventEntity::class]"
            )
        )
        assertTrue(source.contains("version = 5"))
        assertTrue(source.contains("exportSchema = true"))
        assertTrue(source.contains("abstract fun homeDao(): HomeDao"))
        assertTrue(source.contains("abstract fun ingestionEventDao(): IngestionEventDao"))
        assertTrue(source.contains("abstract fun exposureEventDao(): ExposureEventDao"))
        assertTrue(source.contains("abstract fun outbreakEventDao(): OutbreakEventDao"))
    }

    @Test
    fun home_message_entity_uses_snake_case_table_and_columns() {
        val entityFile = File(
            System.getProperty("user.dir"),
            "src/main/java/com/example/app/core/database/HomeMessageEntity.kt"
        )

        assertTrue(entityFile.exists())
        val source = entityFile.readText()

        assertTrue(source.contains("@Entity(tableName = \"home_messages\")"))
        assertTrue(source.contains("@ColumnInfo(name = \"message_text\")"))
        assertTrue(source.contains("@ColumnInfo(name = \"updated_at_epoch_ms\")"))
    }

    @Test
    fun baseline_migration_1_2_is_declared() {
        assertEquals(1, Migration_1_2.startVersion)
        assertEquals(2, Migration_1_2.endVersion)
    }

    @Test
    fun ingestion_table_migration_2_3_is_declared() {
        assertEquals(2, Migration_2_3.startVersion)
        assertEquals(3, Migration_2_3.endVersion)
    }

    @Test
    fun exposure_table_migration_3_4_is_declared() {
        assertEquals(3, Migration_3_4.startVersion)
        assertEquals(4, Migration_3_4.endVersion)
    }

    @Test
    fun outbreak_table_migration_4_5_is_declared() {
        assertEquals(4, Migration_4_5.startVersion)
        assertEquals(5, Migration_4_5.endVersion)
    }
}
