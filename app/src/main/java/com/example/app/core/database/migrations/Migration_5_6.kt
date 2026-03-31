package com.example.app.core.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val Migration_5_6 = object : Migration(5, 6) {
    override fun migrate(database: SupportSQLiteDatabase) {
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
            "CREATE INDEX IF NOT EXISTS index_reusable_compounds_name ON reusable_compounds(name)"
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
    }
}
