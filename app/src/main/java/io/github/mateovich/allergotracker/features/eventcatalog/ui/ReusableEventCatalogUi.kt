package io.github.mateovich.allergotracker.features.eventcatalog.ui

import io.github.mateovich.allergotracker.features.eventcatalog.domain.ReusableEventCatalogKind
import io.github.mateovich.allergotracker.features.eventcatalog.domain.normalizeReusableEventCatalogName

data class ReusableEventCatalogOptionUiModel(
    val id: Long,
    val name: String,
    val kind: ReusableEventCatalogKind,
    val usageCount: Int,
    val lastUsedAtEpochMs: Long,
    val updatedAtEpochMs: Long
)

internal fun List<ReusableEventCatalogOptionUiModel>.filterForQuery(query: String): List<ReusableEventCatalogOptionUiModel> {
    val normalizedQuery = normalizeReusableEventCatalogName(query)
    return asSequence()
        .filter { option ->
            normalizedQuery.isBlank() || normalizeReusableEventCatalogName(option.name).contains(normalizedQuery)
        }
        .sortedWith(
            compareBy<ReusableEventCatalogOptionUiModel> { option ->
                option.relevanceBucket(normalizedQuery)
            }.thenByDescending { it.usageCount }
                .thenByDescending { it.lastUsedAtEpochMs }
                .thenByDescending { it.updatedAtEpochMs }
                .thenBy { it.name.lowercase() }
                .thenBy { it.id }
        )
        .let { sequence ->
            if (normalizedQuery.isBlank()) sequence.take(MAX_RECENT_EVENT_CATALOG_OPTIONS) else sequence
        }
        .toList()
}

internal fun List<ReusableEventCatalogOptionUiModel>.hasExactName(query: String): Boolean {
    val normalizedQuery = normalizeReusableEventCatalogName(query)
    if (normalizedQuery.isBlank()) return false
    return any { option -> normalizeReusableEventCatalogName(option.name) == normalizedQuery }
}

private fun ReusableEventCatalogOptionUiModel.relevanceBucket(normalizedQuery: String): Int {
    val normalizedName = normalizeReusableEventCatalogName(name)
    return when {
        normalizedQuery.isBlank() -> 0
        normalizedName == normalizedQuery -> 0
        normalizedName.startsWith(normalizedQuery) -> 1
        normalizedName.contains(normalizedQuery) -> 2
        else -> 3
    }
}

private const val MAX_RECENT_EVENT_CATALOG_OPTIONS = 6
