package io.github.mateovich.allergotracker.features.ingestion.domain

enum class ReusableEntityKind(val storageValue: String, val label: String) {
    INGREDIENT(storageValue = "ingredient", label = "Reusable ingredient"),
    COMPOUND(storageValue = "compound", label = "Reusable compound");

    companion object {
        fun fromStorageValue(rawValue: String?): ReusableEntityKind? {
            return entries.firstOrNull { it.storageValue == rawValue }
        }
    }
}
