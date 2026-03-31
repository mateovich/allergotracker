package com.example.app.core.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val Migration_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE reusable_ingredients ADD COLUMN normalized_name TEXT NOT NULL DEFAULT ''"
        )
        db.execSQL(
            "ALTER TABLE reusable_compounds ADD COLUMN normalized_name TEXT NOT NULL DEFAULT ''"
        )

        db.execSQL("UPDATE reusable_ingredients SET normalized_name = LOWER(TRIM(name))")
        db.execSQL("UPDATE reusable_compounds SET normalized_name = LOWER(TRIM(name))")

        mergeIngredientDuplicates(db)
        mergeCompoundDuplicates(db)

        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_ingestion_events_reusable_entity_kind ON ingestion_events(reusable_entity_kind)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_ingestion_events_reusable_entity_id ON ingestion_events(reusable_entity_id)"
        )
        db.execSQL("DROP INDEX IF EXISTS index_reusable_compounds_name")
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_reusable_ingredients_normalized_name ON reusable_ingredients(normalized_name)"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_reusable_compounds_normalized_name ON reusable_compounds(normalized_name)"
        )
    }
}

private fun mergeIngredientDuplicates(db: SupportSQLiteDatabase) {
    db.query(
        "SELECT normalized_name FROM reusable_ingredients GROUP BY normalized_name HAVING COUNT(*) > 1"
    ).use { duplicates ->
        val normalizedNameIndex = duplicates.getColumnIndexOrThrow("normalized_name")
        while (duplicates.moveToNext()) {
            val normalizedName = duplicates.getString(normalizedNameIndex)
            val canonicalId = canonicalIdForName(db, "reusable_ingredients", normalizedName)

            db.execSQL(
                "UPDATE reusable_compound_components SET ingredient_id = ? WHERE ingredient_id IN (SELECT id FROM reusable_ingredients WHERE normalized_name = ? AND id != ?)",
                arrayOf(canonicalId, normalizedName, canonicalId)
            )
            db.execSQL(
                "UPDATE ingestion_events SET reusable_entity_id = ? WHERE reusable_entity_kind = 'ingredient' AND reusable_entity_id IN (SELECT id FROM reusable_ingredients WHERE normalized_name = ? AND id != ?)",
                arrayOf(canonicalId, normalizedName, canonicalId)
            )
            db.execSQL(
                "DELETE FROM reusable_ingredients WHERE normalized_name = ? AND id != ?",
                arrayOf(normalizedName, canonicalId)
            )
        }
    }
}

private fun mergeCompoundDuplicates(db: SupportSQLiteDatabase) {
    db.query(
        "SELECT normalized_name FROM reusable_compounds GROUP BY normalized_name HAVING COUNT(*) > 1"
    ).use { duplicates ->
        val normalizedNameIndex = duplicates.getColumnIndexOrThrow("normalized_name")
        while (duplicates.moveToNext()) {
            val normalizedName = duplicates.getString(normalizedNameIndex)
            val canonicalId = canonicalIdForName(db, "reusable_compounds", normalizedName)

            db.execSQL(
                "UPDATE reusable_compound_components SET child_compound_id = ? WHERE child_compound_id IN (SELECT id FROM reusable_compounds WHERE normalized_name = ? AND id != ?)",
                arrayOf(canonicalId, normalizedName, canonicalId)
            )
            db.execSQL(
                "UPDATE ingestion_events SET reusable_entity_id = ? WHERE reusable_entity_kind = 'compound' AND reusable_entity_id IN (SELECT id FROM reusable_compounds WHERE normalized_name = ? AND id != ?)",
                arrayOf(canonicalId, normalizedName, canonicalId)
            )
            db.execSQL(
                "DELETE FROM reusable_compounds WHERE normalized_name = ? AND id != ?",
                arrayOf(normalizedName, canonicalId)
            )
        }
    }
}

private fun canonicalIdForName(
    db: SupportSQLiteDatabase,
    tableName: String,
    normalizedName: String
): Long {
    db.query(
        "SELECT MIN(id) AS canonical_id FROM $tableName WHERE normalized_name = ?",
        arrayOf(normalizedName)
    ).use { cursor ->
        cursor.moveToFirst()
        return cursor.getLong(cursor.getColumnIndexOrThrow("canonical_id"))
    }
}
