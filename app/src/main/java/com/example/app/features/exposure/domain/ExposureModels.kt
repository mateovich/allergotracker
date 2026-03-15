package com.example.app.features.exposure.domain

data class CreateExposureEventCommand(
    val exposureType: String,
    val occurredAtEpochMs: Long,
    val notes: String?
)

data class UpdateExposureEventCommand(
    val id: Long,
    val exposureType: String,
    val occurredAtEpochMs: Long,
    val notes: String?
)

data class ExposureEvent(
    val id: Long,
    val exposureType: String,
    val occurredAtEpochMs: Long,
    val notes: String?
)

data class CreateExposureDefaults(
    val occurredAtEpochMs: Long
)
