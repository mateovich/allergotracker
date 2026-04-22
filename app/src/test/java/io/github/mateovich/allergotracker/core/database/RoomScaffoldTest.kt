package io.github.mateovich.allergotracker.core.database

import io.github.mateovich.allergotracker.core.database.migrations.Migration_1_2
import io.github.mateovich.allergotracker.core.database.migrations.Migration_10_11
import io.github.mateovich.allergotracker.core.database.migrations.Migration_11_12
import io.github.mateovich.allergotracker.core.database.migrations.Migration_12_13
import io.github.mateovich.allergotracker.core.database.migrations.Migration_2_3
import io.github.mateovich.allergotracker.core.database.migrations.Migration_3_4
import io.github.mateovich.allergotracker.core.database.migrations.Migration_4_5
import io.github.mateovich.allergotracker.core.database.migrations.Migration_5_6
import io.github.mateovich.allergotracker.core.database.migrations.Migration_6_7
import io.github.mateovich.allergotracker.core.database.migrations.Migration_7_8
import io.github.mateovich.allergotracker.core.database.migrations.Migration_8_9
import io.github.mateovich.allergotracker.core.database.migrations.Migration_9_10
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomScaffoldTest {

    @Test
    fun app_database_source_declares_expected_room_metadata() {
        val databaseFile = resolveProjectFile("src/main/java/io/github/mateovich/allergotracker/core/database/AppDatabase.kt")

        assertTrue(databaseFile.exists())
        val source = databaseFile.readText()

        assertTrue(source.contains("@Database("))
        assertTrue(source.contains("ReusableIngredientEntity::class"))
        assertTrue(source.contains("ReusableCompoundEntity::class"))
        assertTrue(source.contains("ReusableCompoundComponentEntity::class"))
        assertTrue(source.contains("ReusableEventCatalogEntity::class"))
        assertTrue(source.contains("version = 13"))
        assertTrue(source.contains("exportSchema = true"))
        assertTrue(source.contains("abstract fun homeDao(): HomeDao"))
        assertTrue(source.contains("abstract fun ingestionEventDao(): IngestionEventDao"))
        assertTrue(source.contains("abstract fun exposureEventDao(): ExposureEventDao"))
        assertTrue(source.contains("abstract fun outbreakEventDao(): OutbreakEventDao"))
        assertTrue(source.contains("abstract fun reusableEventCatalogDao(): ReusableEventCatalogDao"))
        assertTrue(source.contains("abstract fun reusableIngredientDao(): ReusableIngredientDao"))
        assertTrue(source.contains("abstract fun reusableCompoundDao(): ReusableCompoundDao"))
    }

    @Test
    fun home_message_entity_uses_snake_case_table_and_columns() {
        val entityFile = resolveProjectFile("src/main/java/io/github/mateovich/allergotracker/core/database/HomeMessageEntity.kt")

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

    @Test
    fun reusable_catalog_migration_5_6_is_declared() {
        assertEquals(5, Migration_5_6.startVersion)
        assertEquals(6, Migration_5_6.endVersion)
    }

    @Test
    fun ingestion_reusable_reference_migration_6_7_is_declared() {
        assertEquals(6, Migration_6_7.startVersion)
        assertEquals(7, Migration_6_7.endVersion)
    }

    @Test
    fun compound_component_invariants_migration_7_8_is_declared() {
        assertEquals(7, Migration_7_8.startVersion)
        assertEquals(8, Migration_7_8.endVersion)
    }

    @Test
    fun reusable_catalog_hardening_migration_8_9_is_declared() {
        assertEquals(8, Migration_8_9.startVersion)
        assertEquals(9, Migration_8_9.endVersion)
    }

    @Test
    fun reusable_event_catalog_migration_9_10_is_declared() {
        assertEquals(9, Migration_9_10.startVersion)
        assertEquals(10, Migration_9_10.endVersion)
    }

    @Test
    fun reusable_event_catalog_reference_constraints_migration_10_11_is_declared() {
        assertEquals(10, Migration_10_11.startVersion)
        assertEquals(11, Migration_10_11.endVersion)
    }

    @Test
    fun reusable_event_catalog_kind_guards_migration_11_12_is_declared() {
        assertEquals(11, Migration_11_12.startVersion)
        assertEquals(12, Migration_11_12.endVersion)
    }

    @Test
    fun event_intensity_backfill_migration_12_13_is_declared() {
        assertEquals(12, Migration_12_13.startVersion)
        assertEquals(13, Migration_12_13.endVersion)
    }

    private fun resolveProjectFile(relativeToAppModule: String): File {
        val workingDirectory = File(System.getProperty("user.dir") ?: ".")
        val direct = File(workingDirectory, relativeToAppModule)
        if (direct.exists()) return direct

        val nested = File(workingDirectory, "app/$relativeToAppModule")
        if (nested.exists()) return nested

        return direct
    }
}
