package com.example.app.features.exposure.domain

data class CreateExposureEventCommand(
    val exposureType: String,
    val occurredAtEpochMs: Long,
    val notes: String?,
    val exposureTypeCatalogId: Long? = null
)

data class UpdateExposureEventCommand(
    val id: Long,
    val exposureType: String,
    val occurredAtEpochMs: Long,
    val notes: String?,
    val exposureTypeCatalogId: Long? = null
)

data class ExposureEvent(
    val id: Long,
    val exposureType: String,
    val occurredAtEpochMs: Long,
    val notes: String?,
    val exposureTypeCatalogId: Long? = null
)

data class CreateExposureDefaults(
    val occurredAtEpochMs: Long
)
