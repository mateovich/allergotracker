package com.example.app.features.compounds.domain

data class CreateReusableCompoundCommand(
    val name: String,
    val notes: String?
)

enum class CompoundCompositionStatus(val storageValue: String) {
    DRAFT("draft"),
    COMPLETE("complete");

    companion object {
        fun fromStorageValue(rawValue: String): CompoundCompositionStatus {
            return entries.firstOrNull { it.storageValue == rawValue } ?: DRAFT
        }
    }
}

enum class CompoundComponentKind(val storageValue: String) {
    INGREDIENT("ingredient"),
    COMPOUND("compound");

    companion object {
        fun fromStorageValue(rawValue: String): CompoundComponentKind {
            return entries.firstOrNull { it.storageValue == rawValue } ?: INGREDIENT
        }
    }
}

data class ReusableCompoundComponent(
    val id: Long,
    val position: Int,
    val kind: CompoundComponentKind,
    val ingredientId: Long?,
    val childCompoundId: Long?,
    val proportionBasisPoints: Int?
)

data class ReusableCompound(
    val id: Long,
    val name: String,
    val notes: String?,
    val compositionStatus: CompoundCompositionStatus,
    val components: List<ReusableCompoundComponent>,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long
)
