package io.github.mateovich.allergotracker.features.export.domain

data class GeneratedJsonExport(
    val configuration: ExportConfiguration,
    val schemaVersion: String,
    val exportedAt: String,
    val artifact: ExportArtifact
)

data class ExportArtifact(
    val fileName: String,
    val absolutePath: String,
    val mimeType: String = "application/json"
)
