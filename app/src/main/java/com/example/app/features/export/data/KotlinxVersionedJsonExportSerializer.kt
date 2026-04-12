package com.example.app.features.export.data

import com.example.app.core.common.result.AppResult
import com.example.app.core.export.schema.VersionedExportEnvelopeDto
import com.example.app.features.export.domain.VersionedJsonExportSerializer
import com.example.app.features.export.domain.toExportError
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class KotlinxVersionedJsonExportSerializer(
    private val json: Json = Json {
        prettyPrint = false
        encodeDefaults = true
        explicitNulls = false
    }
) : VersionedJsonExportSerializer {
    override fun serialize(envelope: VersionedExportEnvelopeDto): AppResult<String> {
        return try {
            AppResult.Success(json.encodeToString(envelope))
        } catch (error: Exception) {
            AppResult.Error(error.toExportError("Could not serialize the export to JSON."))
        }
    }
}
