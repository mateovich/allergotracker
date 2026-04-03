package com.example.app.features.exposure.domain

data class CreateExposureEventCommand(
    val exposureType: String,
    val occurredAtEpochMs: Long,
    val notes: String?,
    val intensityLevel: Int = DEFAULT_INTENSITY_LEVEL,
    val exposureTypeCatalogId: Long? = null
)

data class UpdateExposureEventCommand(
    val id: Long,
    val exposureType: String,
    val occurredAtEpochMs: Long,
    val notes: String?,
    val intensityLevel: Int = DEFAULT_INTENSITY_LEVEL,
    val exposureTypeCatalogId: Long? = null
)

data class ExposureEvent(
    val id: Long,
    val exposureType: String,
    val occurredAtEpochMs: Long,
    val notes: String?,
    val intensityLevel: Int = DEFAULT_INTENSITY_LEVEL,
    val exposureTypeCatalogId: Long? = null
)

data class CreateExposureDefaults(
    val occurredAtEpochMs: Long,
    val intensityLevel: Int = DEFAULT_INTENSITY_LEVEL
)

internal const val DEFAULT_INTENSITY_LEVEL = 3
