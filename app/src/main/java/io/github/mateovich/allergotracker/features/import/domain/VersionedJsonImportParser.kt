package io.github.mateovich.allergotracker.features.import.domain

import io.github.mateovich.allergotracker.core.common.result.AppResult
import io.github.mateovich.allergotracker.core.export.schema.VersionedExportEnvelopeDto

interface VersionedJsonImportParser {
    fun parse(rawJson: String): AppResult<VersionedExportEnvelopeDto>
}
