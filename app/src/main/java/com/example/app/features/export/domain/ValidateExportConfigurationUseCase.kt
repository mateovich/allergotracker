package com.example.app.features.export.domain

import com.example.app.core.common.error.DomainError
import com.example.app.core.common.result.AppResult

class ValidateExportConfigurationUseCase {
    operator fun invoke(
        selectedFormat: ExportFormat?,
        selectedScope: ExportScope?
    ): AppResult<ExportConfiguration> {
        val fieldErrors = buildMap {
            if (selectedFormat == null) {
                put(FORMAT_FIELD, "Choose an export format.")
            }
            if (selectedScope == null) {
                put(SCOPE_FIELD, "Choose which records to include.")
            }
        }

        if (fieldErrors.isNotEmpty()) {
            return AppResult.Error(
                DomainError.ValidationError(
                    message = "Choose a format and scope before continuing.",
                    fieldErrors = fieldErrors
                )
            )
        }

        return AppResult.Success(
            ExportConfiguration(
                format = requireNotNull(selectedFormat),
                scope = requireNotNull(selectedScope)
            )
        )
    }

    companion object {
        const val FORMAT_FIELD = "format"
        const val SCOPE_FIELD = "scope"
    }
}
