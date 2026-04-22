package io.github.mateovich.allergotracker.features.eventcatalog.domain

data class ReusableEventCatalog(
    val id: Long,
    val kind: ReusableEventCatalogKind,
    val name: String,
    val usageCount: Int,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val lastUsedAtEpochMs: Long
)
