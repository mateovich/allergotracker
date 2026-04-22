package io.github.mateovich.allergotracker.features.export.data

import io.github.mateovich.allergotracker.core.common.result.AppResult
import io.github.mateovich.allergotracker.core.export.schema.VersionedExportEnvelopeDto
import io.github.mateovich.allergotracker.features.export.domain.VersionedJsonExportSerializer
import io.github.mateovich.allergotracker.features.export.domain.toExportError
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
