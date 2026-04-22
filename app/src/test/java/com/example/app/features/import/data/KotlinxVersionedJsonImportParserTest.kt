package com.example.app.features.import.data

import com.example.app.core.common.result.AppResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KotlinxVersionedJsonImportParserTest {

    private val parser = KotlinxVersionedJsonImportParser()

    @Test
    fun parse_accepts_versioned_export_contract_with_required_sections() {
        val result = parser.parse(validJson)

        assertTrue(result is AppResult.Success)
        result as AppResult.Success
        assertEquals("2.0.0", result.value.schemaVersion)
    }

    @Test
    fun parse_rejects_missing_required_section() {
        val result = parser.parse(
            """
            {"schema_version":"2.0.0","exported_at":"2023-11-14T22:13:20Z","data":{"ingestions":[],"exposures":[],"outbreaks":[],"reusable_ingredients":[],"reusable_compounds":[],"reusable_event_catalog":[]}}
            """.trimIndent()
        )

        assertTrue(result is AppResult.Error)
        result as AppResult.Error
        assertEquals("Import file is missing data.normalized_base_ingredients.", result.error.message)
    }

    @Test
    fun parse_rejects_malformed_json() {
        val result = parser.parse("{" )

        assertTrue(result is AppResult.Error)
        result as AppResult.Error
        assertEquals("Import file is not valid JSON for the supported export contract.", result.error.message)
    }

    private companion object {
        val validJson = """
            {"schema_version":"2.0.0","exported_at":"2023-11-14T22:13:20Z","data":{"ingestions":[],"exposures":[],"outbreaks":[],"reusable_ingredients":[],"reusable_compounds":[],"reusable_event_catalog":[],"normalized_base_ingredients":[]}}
        """.trimIndent()
    }
}
