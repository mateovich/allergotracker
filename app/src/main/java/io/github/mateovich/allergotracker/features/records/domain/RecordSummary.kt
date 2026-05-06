package io.github.mateovich.allergotracker.features.records.domain

import io.github.mateovich.allergotracker.features.ingestion.domain.ReusableEntityKind

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
    val intensityLevel: Int? = null,
    val detailSnapshot: RecordDetailSnapshot? = null
)

sealed interface RecordDetailSnapshot {
    data class Ingestion(
        val quantityLabel: String,
        val reusableEntityKind: ReusableEntityKind?,
        val reusableEntityId: Long?,
        val notes: String?
    ) : RecordDetailSnapshot

    data class Exposure(
        val intensityLabel: String,
        val durationMinutes: Int? = null,
        val place: String?,
        val notes: String?
    ) : RecordDetailSnapshot

    data class Outbreak(
        val bodyLocation: String,
        val intensityLabel: String,
        val durationMinutes: Int? = null,
        val notes: String?
    ) : RecordDetailSnapshot
}

fun recordIntensityLabel(level: Int?): String {
    return level?.let { "Intensity $it" }.orEmpty()
}
