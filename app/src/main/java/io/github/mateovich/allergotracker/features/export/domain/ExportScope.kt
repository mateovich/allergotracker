package io.github.mateovich.allergotracker.features.export.domain

import io.github.mateovich.allergotracker.features.records.domain.RecordEventType

sealed interface ExportScope {
    data object AllRecords : ExportScope

    data class EventTypeRecords(val eventType: RecordEventType) : ExportScope
}
