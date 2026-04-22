package com.example.app.features.import.domain

import com.example.app.core.common.result.AppResult
import com.example.app.core.export.schema.VersionedExportEnvelopeDto

interface VersionedJsonImportParser {
    fun parse(rawJson: String): AppResult<VersionedExportEnvelopeDto>
}
