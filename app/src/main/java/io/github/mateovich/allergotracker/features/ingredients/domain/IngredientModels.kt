package io.github.mateovich.allergotracker.features.ingredients.domain

data class CreateReusableIngredientCommand(
    val name: String,
    val notes: String?
)

data class UpdateReusableIngredientCommand(
    val id: Long,
    val name: String,
    val notes: String?
)

data class ReusableIngredient(
    val id: Long,
    val name: String,
    val notes: String?,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long
)
