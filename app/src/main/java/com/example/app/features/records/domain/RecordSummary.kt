package com.example.app.features.records.domain

enum class RecordEventType {
    INGESTION,
    EXPOSURE,
    OUTBREAK
}

data class RecordSummary(
    val id: String,
    val sortKey: Long,
    val typeLabel: String,
    val title: String,
    val detailLabel: String,
    val occurredAtEpochMs: Long,
    val createdAtEpochMs: Long,
    val sourceId: Long,
    val eventType: RecordEventType,
    val ingestionQuantityLevel: Int? = null
)
