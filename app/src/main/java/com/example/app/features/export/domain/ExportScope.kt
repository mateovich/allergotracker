package com.example.app.features.export.domain

import com.example.app.features.records.domain.RecordEventType

sealed interface ExportScope {
    data object AllRecords : ExportScope

    data class EventTypeRecords(val eventType: RecordEventType) : ExportScope
}
