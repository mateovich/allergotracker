package io.github.mateovich.allergotracker.features.outbreak.domain

data class CreateOutbreakEventCommand(
    val outbreakDescriptor: String,
    val bodyLocation: String,
    val occurredAtEpochMs: Long,
    val notes: String?,
    val intensityLevel: Int = DEFAULT_INTENSITY_LEVEL,
    val outbreakDescriptorCatalogId: Long? = null,
    val bodyLocationCatalogId: Long? = null
)

data class UpdateOutbreakEventCommand(
    val id: Long,
    val outbreakDescriptor: String,
    val bodyLocation: String,
    val occurredAtEpochMs: Long,
    val notes: String?,
    val intensityLevel: Int = DEFAULT_INTENSITY_LEVEL,
    val outbreakDescriptorCatalogId: Long? = null,
    val bodyLocationCatalogId: Long? = null
)

data class OutbreakEvent(
    val id: Long,
    val outbreakDescriptor: String,
    val bodyLocation: String,
    val occurredAtEpochMs: Long,
    val notes: String?,
    val intensityLevel: Int = DEFAULT_INTENSITY_LEVEL,
    val outbreakDescriptorCatalogId: Long? = null,
    val bodyLocationCatalogId: Long? = null
)

data class CreateOutbreakDefaults(
    val occurredAtEpochMs: Long,
    val intensityLevel: Int = DEFAULT_INTENSITY_LEVEL
)

internal const val DEFAULT_INTENSITY_LEVEL = 3
