package io.github.mateovich.allergotracker.features.export.domain

data class ExportConfiguration(
    val format: ExportFormat,
    val scope: ExportScope
)
