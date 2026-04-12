package com.example.app.core.export.schema

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

const val EXPORT_SCHEMA_VERSION = "2.0.0"

@Serializable
data class VersionedExportEnvelopeDto(
    @SerialName("schema_version")
    val schemaVersion: String,
    @SerialName("exported_at")
    val exportedAt: String,
    val data: VersionedExportDataDto
)

@Serializable
data class VersionedExportDataDto(
    val ingestions: List<IngestionExportDto> = emptyList(),
    val exposures: List<ExposureExportDto> = emptyList(),
    val outbreaks: List<OutbreakExportDto> = emptyList(),
    @SerialName("reusable_ingredients")
    val reusableIngredients: List<ReusableIngredientExportDto> = emptyList(),
    @SerialName("reusable_compounds")
    val reusableCompounds: List<ReusableCompoundExportDto> = emptyList(),
    @SerialName("reusable_event_catalog")
    val reusableEventCatalog: List<ReusableEventCatalogExportDto> = emptyList(),
    @SerialName("normalized_base_ingredients")
    val normalizedBaseIngredients: List<NormalizedBaseIngredientsExportDto> = emptyList()
)

@Serializable
data class NormalizedBaseIngredientsExportDto(
    @SerialName("source_event")
    val sourceEvent: NormalizedSourceIngestionExportDto,
    @SerialName("derivation_kind")
    val derivationKind: String,
    @SerialName("base_ingredients")
    val baseIngredients: List<NormalizedBaseIngredientContributionExportDto>
)

@Serializable
data class NormalizedSourceIngestionExportDto(
    val kind: String,
    val id: Long,
    @SerialName("item_name")
    val itemName: String,
    @SerialName("quantity_level")
    val quantityLevel: Int,
    @SerialName("occurred_at")
    val occurredAt: String,
    @SerialName("reusable_entity_ref")
    val reusableEntityRef: ReusableEntityReferenceDto
)

@Serializable
data class NormalizedBaseIngredientContributionExportDto(
    @SerialName("reusable_ingredient_ref")
    val reusableIngredientRef: ReusableEntityReferenceDto,
    @SerialName("ingredient_name")
    val ingredientName: String,
    @SerialName("contribution_basis_points")
    val contributionBasisPoints: Int,
    @SerialName("derived_quantity_level_basis_points")
    val derivedQuantityLevelBasisPoints: Int
)

@Serializable
data class IngestionExportDto(
    val id: Long,
    @SerialName("item_name")
    val itemName: String,
    @SerialName("quantity_level")
    val quantityLevel: Int,
    @SerialName("occurred_at")
    val occurredAt: String,
    val notes: String? = null,
    @SerialName("reusable_entity_ref")
    val reusableEntityRef: ReusableEntityReferenceDto? = null,
    @SerialName("created_at")
    val createdAt: String
)

@Serializable
data class ExposureExportDto(
    val id: Long,
    @SerialName("exposure_type")
    val exposureType: String,
    @SerialName("occurred_at")
    val occurredAt: String,
    val notes: String? = null,
    @SerialName("intensity_level")
    val intensityLevel: Int,
    @SerialName("exposure_type_catalog_ref")
    val exposureTypeCatalogRef: EventCatalogReferenceDto? = null,
    @SerialName("created_at")
    val createdAt: String
)

@Serializable
data class OutbreakExportDto(
    val id: Long,
    @SerialName("outbreak_descriptor")
    val outbreakDescriptor: String,
    @SerialName("body_location")
    val bodyLocation: String,
    @SerialName("occurred_at")
    val occurredAt: String,
    val notes: String? = null,
    @SerialName("intensity_level")
    val intensityLevel: Int,
    @SerialName("outbreak_descriptor_catalog_ref")
    val outbreakDescriptorCatalogRef: EventCatalogReferenceDto? = null,
    @SerialName("body_location_catalog_ref")
    val bodyLocationCatalogRef: EventCatalogReferenceDto? = null,
    @SerialName("created_at")
    val createdAt: String
)

@Serializable
data class ReusableEntityReferenceDto(
    val kind: String,
    val id: Long
)

@Serializable
data class EventCatalogReferenceDto(
    val kind: String,
    val id: Long
)

@Serializable
data class ReusableIngredientExportDto(
    val id: Long,
    val name: String,
    val notes: String? = null,
    @SerialName("created_at")
    val createdAt: String,
    @SerialName("updated_at")
    val updatedAt: String
)

@Serializable
data class ReusableCompoundExportDto(
    val id: Long,
    val name: String,
    val notes: String? = null,
    @SerialName("composition_status")
    val compositionStatus: String,
    val components: List<ReusableCompoundComponentExportDto>,
    @SerialName("created_at")
    val createdAt: String,
    @SerialName("updated_at")
    val updatedAt: String
)

@Serializable
data class ReusableCompoundComponentExportDto(
    val id: Long,
    val position: Int,
    @SerialName("component_ref")
    val componentRef: ReusableEntityReferenceDto,
    @SerialName("proportion_basis_points")
    val proportionBasisPoints: Int? = null
)

@Serializable
data class ReusableEventCatalogExportDto(
    val id: Long,
    val kind: String,
    val name: String,
    @SerialName("usage_count")
    val usageCount: Int,
    @SerialName("created_at")
    val createdAt: String,
    @SerialName("updated_at")
    val updatedAt: String,
    @SerialName("last_used_at")
    val lastUsedAt: String
)
