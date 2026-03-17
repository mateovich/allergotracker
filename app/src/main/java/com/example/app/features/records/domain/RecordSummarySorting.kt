package com.example.app.features.records.domain

val RecordSummaryComparator: Comparator<RecordSummary> =
    compareByDescending<RecordSummary> { it.occurredAtEpochMs }
        .thenByDescending { it.createdAtEpochMs }
        .thenByDescending { it.sourceId }
        .thenBy { it.eventType.ordinal }
