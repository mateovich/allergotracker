package com.example.app.features.eventcatalog.ui

import com.example.app.features.eventcatalog.domain.ReusableEventCatalogKind

data class ReusableEventCatalogOptionUiModel(
    val id: Long,
    val name: String,
    val kind: ReusableEventCatalogKind,
    val usageCount: Int,
    val lastUsedAtEpochMs: Long,
    val updatedAtEpochMs: Long
)

internal fun List<ReusableEventCatalogOptionUiModel>.filterForQuery(query: String): List<ReusableEventCatalogOptionUiModel> {
    val normalizedQuery = query.trim().lowercase()
    return asSequence()
        .filter { option -> normalizedQuery.isBlank() || option.name.lowercase().contains(normalizedQuery) }
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
    val normalizedQuery = query.trim().lowercase()
    if (normalizedQuery.isBlank()) return false
    return any { option -> option.name.lowercase() == normalizedQuery }
}

private fun ReusableEventCatalogOptionUiModel.relevanceBucket(normalizedQuery: String): Int {
    val normalizedName = name.lowercase()
    return when {
        normalizedQuery.isBlank() -> 0
        normalizedName == normalizedQuery -> 0
        normalizedName.startsWith(normalizedQuery) -> 1
        normalizedName.contains(normalizedQuery) -> 2
        else -> 3
    }
}

private const val MAX_RECENT_EVENT_CATALOG_OPTIONS = 6
