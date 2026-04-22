package io.github.mateovich.allergotracker.features.records.domain

import io.github.mateovich.allergotracker.features.ingestion.domain.IngestionQuantityOption
import java.time.LocalDate

data class RecordsFilter(
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val eventTypes: Set<RecordEventType> = emptySet(),
    val intensityLevels: Set<IngestionQuantityOption> = emptySet()
) {
    val hasActiveFilters: Boolean =
        startDate != null || endDate != null || eventTypes.isNotEmpty() || intensityLevels.isNotEmpty()
}
