package io.github.mateovich.allergotracker.features.eventcatalog.domain

enum class ReusableEventCatalogKind(
    val storageValue: String,
    val singularLabel: String,
    val pluralLabel: String
) {
    EXPOSURE_TYPE(
        storageValue = "exposure_type",
        singularLabel = "Exposure type",
        pluralLabel = "Exposure types"
    ),
    OUTBREAK_DESCRIPTOR(
        storageValue = "outbreak_descriptor",
        singularLabel = "Outbreak descriptor",
        pluralLabel = "Outbreak descriptors"
    ),
    BODY_LOCATION(
        storageValue = "body_location",
        singularLabel = "Body location",
        pluralLabel = "Body locations"
    );

    companion object {
        fun fromStorageValue(value: String): ReusableEventCatalogKind? {
            return entries.firstOrNull { it.storageValue == value }
        }
    }
}
