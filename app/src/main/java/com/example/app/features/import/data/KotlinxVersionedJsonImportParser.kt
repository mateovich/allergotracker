package com.example.app.features.import.data

import com.example.app.core.common.error.DomainError
import com.example.app.core.common.result.AppResult
import com.example.app.core.export.schema.VersionedExportEnvelopeDto
import com.example.app.features.import.domain.VersionedJsonImportParser
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class KotlinxVersionedJsonImportParser(
    private val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }
) : VersionedJsonImportParser {
    override fun parse(rawJson: String): AppResult<VersionedExportEnvelopeDto> {
        return try {
            val root = json.parseToJsonElement(rawJson).jsonObject
            validateRequiredSections(root)
            AppResult.Success(json.decodeFromJsonElement(VersionedExportEnvelopeDto.serializer(), root))
        } catch (error: MissingImportSectionException) {
            AppResult.Error(DomainError.ImportError(error.message ?: "Import file is missing required sections."))
        } catch (_: SerializationException) {
            AppResult.Error(DomainError.ImportError("Import file is not valid JSON for the supported export contract."))
        } catch (_: IllegalArgumentException) {
            AppResult.Error(DomainError.ImportError("Import file is not valid JSON for the supported export contract."))
        }
    }

    private fun validateRequiredSections(root: JsonObject) {
        if (!root.containsKey("schema_version")) {
            throw MissingImportSectionException("Import file is missing schema_version.")
        }
        if (!root.containsKey("exported_at")) {
            throw MissingImportSectionException("Import file is missing exported_at.")
        }
        val data = root["data"]?.jsonObject
            ?: throw MissingImportSectionException("Import file is missing data.")
        requiredDataSections.forEach { section ->
            if (!data.containsKey(section)) {
                throw MissingImportSectionException("Import file is missing data.$section.")
            }
        }
        root["schema_version"]?.jsonPrimitive?.content
            ?: throw MissingImportSectionException("Import file is missing schema_version.")
    }

    private companion object {
        val requiredDataSections = listOf(
            "ingestions",
            "exposures",
            "outbreaks",
            "reusable_ingredients",
            "reusable_compounds",
            "reusable_event_catalog",
            "normalized_base_ingredients"
        )
    }
}

private class MissingImportSectionException(message: String) : IllegalStateException(message)
