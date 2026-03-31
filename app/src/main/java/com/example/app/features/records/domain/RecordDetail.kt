package com.example.app.features.records.domain

import com.example.app.features.ingestion.domain.ReusableEntityKind

sealed interface RecordDetail {
    val recordId: Long
    val eventType: RecordEventType
    val typeLabel: String
    val title: String
    val occurredAtEpochMs: Long
    val notes: String?

    data class Ingestion(
        override val recordId: Long,
        override val occurredAtEpochMs: Long,
        val itemName: String,
        val quantityLabel: String,
        val reusableEntityKind: ReusableEntityKind?,
        val reusableEntityId: Long?,
        override val notes: String?
    ) : RecordDetail {
        override val eventType: RecordEventType = RecordEventType.INGESTION
        override val typeLabel: String = "Ingestion"
        override val title: String = itemName
    }

    data class Exposure(
        override val recordId: Long,
        override val occurredAtEpochMs: Long,
        val exposureType: String,
        override val notes: String?
    ) : RecordDetail {
        override val eventType: RecordEventType = RecordEventType.EXPOSURE
        override val typeLabel: String = "Exposure"
        override val title: String = exposureType
    }

    data class Outbreak(
        override val recordId: Long,
        override val occurredAtEpochMs: Long,
        val outbreakDescriptor: String,
        val bodyLocation: String,
        override val notes: String?
    ) : RecordDetail {
        override val eventType: RecordEventType = RecordEventType.OUTBREAK
        override val typeLabel: String = "Outbreak"
        override val title: String = outbreakDescriptor
    }
}
