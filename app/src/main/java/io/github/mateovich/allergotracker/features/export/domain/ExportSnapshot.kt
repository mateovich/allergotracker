package io.github.mateovich.allergotracker.features.export.domain

data class ExportSnapshot(
    val ingestions: List<ExportIngestionRecord>,
    val exposures: List<ExportExposureRecord>,
    val outbreaks: List<ExportOutbreakRecord>,
    val reusableIngredients: List<ExportReusableIngredientRecord>,
    val reusableCompounds: List<ExportReusableCompoundRecord>,
    val reusableEventCatalogEntries: List<ExportReusableEventCatalogRecord>
)

data class ExportReusableEntityReference(
    val kind: String,
    val id: Long
)

data class ExportEventCatalogReference(
    val kind: String,
    val id: Long
)

data class ExportIngestionRecord(
    val id: Long,
    val itemName: String,
    val quantityLevel: Int,
    val occurredAtEpochMs: Long,
    val notes: String?,
    val reusableEntityRef: ExportReusableEntityReference?,
    val createdAtEpochMs: Long
)

data class ExportExposureRecord(
    val id: Long,
    val exposureType: String,
    val occurredAtEpochMs: Long,
    val notes: String?,
    val intensityLevel: Int,
    val exposureTypeCatalogRef: ExportEventCatalogReference?,
    val createdAtEpochMs: Long
)

data class ExportOutbreakRecord(
    val id: Long,
    val outbreakDescriptor: String,
    val bodyLocation: String,
    val occurredAtEpochMs: Long,
    val notes: String?,
    val intensityLevel: Int,
    val outbreakDescriptorCatalogRef: ExportEventCatalogReference?,
    val bodyLocationCatalogRef: ExportEventCatalogReference?,
    val createdAtEpochMs: Long
)

data class ExportReusableIngredientRecord(
    val id: Long,
    val name: String,
    val notes: String?,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long
)

data class ExportReusableCompoundRecord(
    val id: Long,
    val name: String,
    val notes: String?,
    val compositionStatus: String,
    val components: List<ExportReusableCompoundComponentRecord>,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long
)

data class ExportReusableCompoundComponentRecord(
    val id: Long,
    val position: Int,
    val componentRef: ExportReusableEntityReference,
    val proportionBasisPoints: Int?
)

data class ExportReusableEventCatalogRecord(
    val id: Long,
    val kind: String,
    val name: String,
    val usageCount: Int,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val lastUsedAtEpochMs: Long
)
