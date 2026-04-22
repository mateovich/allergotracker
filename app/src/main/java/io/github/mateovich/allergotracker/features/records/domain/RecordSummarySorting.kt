package io.github.mateovich.allergotracker.features.records.domain

val RecordSummaryComparator: Comparator<RecordSummary> =
    compareByDescending<RecordSummary> { it.occurredAtEpochMs }
        .thenByDescending { it.createdAtEpochMs }
        .thenByDescending { it.sourceId }
        .thenBy { it.eventType.ordinal }
