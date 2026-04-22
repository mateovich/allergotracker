package io.github.mateovich.allergotracker.features.ingestion.domain

data class CreateIngestionEventCommand(
    val itemName: String,
    val quantityLevel: Int?,
    val occurredAtEpochMs: Long,
    val notes: String?,
    val reusableEntityKind: ReusableEntityKind? = null,
    val reusableEntityId: Long? = null
)

data class UpdateIngestionEventCommand(
    val id: Long,
    val itemName: String,
    val quantityLevel: Int?,
    val occurredAtEpochMs: Long,
    val notes: String?,
    val reusableEntityKind: ReusableEntityKind? = null,
    val reusableEntityId: Long? = null
)

data class IngestionEvent(
    val id: Long,
    val itemName: String,
    val quantityLevel: Int,
    val occurredAtEpochMs: Long,
    val notes: String?,
    val reusableEntityKind: ReusableEntityKind? = null,
    val reusableEntityId: Long? = null
)

data class CreateIngestionDefaults(
    val occurredAtEpochMs: Long
)

enum class IngestionQuantityOption(
    val level: Int,
    val label: String
) {
    SMALL_BITE(level = 1, label = "1 Small bite"),
    LIGHT_SNACK(level = 2, label = "2 Light snack"),
    STANDARD(level = 3, label = "3 Standard"),
    LARGE(level = 4, label = "4 Large"),
    FULL_MEAL(level = 5, label = "5 Full meal");

    companion object {
        fun fromLevel(level: Int): IngestionQuantityOption? = entries.firstOrNull { it.level == level }
    }
}
