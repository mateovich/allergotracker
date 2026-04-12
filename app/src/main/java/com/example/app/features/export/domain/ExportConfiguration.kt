package com.example.app.features.export.domain

data class ExportConfiguration(
    val format: ExportFormat,
    val scope: ExportScope
)
