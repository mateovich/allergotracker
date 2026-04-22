package io.github.mateovich.allergotracker.core.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val Migration_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.query(
            """
            SELECT COUNT(*)
            FROM reusable_compound_components
            WHERE component_kind NOT IN ('ingredient', 'compound')
               OR position < 0
               OR (proportion_basis_points IS NOT NULL AND (proportion_basis_points < 1 OR proportion_basis_points > 10000))
               OR NOT (
                    (component_kind = 'ingredient' AND ingredient_id IS NOT NULL AND child_compound_id IS NULL)
                    OR
                    (component_kind = 'compound' AND ingredient_id IS NULL AND child_compound_id IS NOT NULL)
               )
               OR (child_compound_id IS NOT NULL AND child_compound_id = compound_id)
            """.trimIndent()
        ).use { cursor ->
            cursor.moveToFirst()
            if (cursor.getInt(0) > 0) {
                throw IllegalStateException(
                    "Migration 7->8 aborted: reusable_compound_components contains rows that violate required invariants."
                )
            }
        }

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS reusable_compound_components_new (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                compound_id INTEGER NOT NULL,
                position INTEGER NOT NULL,
                component_kind TEXT NOT NULL,
                ingredient_id INTEGER,
                child_compound_id INTEGER,
                proportion_basis_points INTEGER,
                FOREIGN KEY(compound_id) REFERENCES reusable_compounds(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(ingredient_id) REFERENCES reusable_ingredients(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(child_compound_id) REFERENCES reusable_compounds(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                CHECK (component_kind IN ('ingredient', 'compound')),
                CHECK (position >= 0),
                CHECK (proportion_basis_points IS NULL OR (proportion_basis_points >= 1 AND proportion_basis_points <= 10000)),
                CHECK (
                    (component_kind = 'ingredient' AND ingredient_id IS NOT NULL AND child_compound_id IS NULL)
                    OR
                    (component_kind = 'compound' AND ingredient_id IS NULL AND child_compound_id IS NOT NULL)
                ),
                CHECK (child_compound_id IS NULL OR child_compound_id != compound_id)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO reusable_compound_components_new (
                id,
                compound_id,
                position,
                component_kind,
                ingredient_id,
                child_compound_id,
                proportion_basis_points
            )
            SELECT
                id,
                compound_id,
                position,
                component_kind,
                ingredient_id,
                child_compound_id,
                proportion_basis_points
            FROM reusable_compound_components
            """.trimIndent()
        )
        db.execSQL("DROP TABLE reusable_compound_components")
        db.execSQL("ALTER TABLE reusable_compound_components_new RENAME TO reusable_compound_components")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_reusable_compound_components_compound_id ON reusable_compound_components(compound_id)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_reusable_compound_components_ingredient_id ON reusable_compound_components(ingredient_id)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_reusable_compound_components_child_compound_id ON reusable_compound_components(child_compound_id)"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_reusable_compound_components_compound_id_position ON reusable_compound_components(compound_id, position)"
        )
    }
}
