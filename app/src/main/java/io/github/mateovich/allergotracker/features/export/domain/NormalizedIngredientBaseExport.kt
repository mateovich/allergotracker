package io.github.mateovich.allergotracker.features.export.domain

import io.github.mateovich.allergotracker.core.common.error.DomainError
import io.github.mateovich.allergotracker.core.common.result.AppResult
import io.github.mateovich.allergotracker.features.ingestion.domain.IngestionQuantityOption
import io.github.mateovich.allergotracker.features.ingestion.domain.ReusableEntityKind

private const val BASIS_POINTS_SCALE = 10_000
private const val INGESTION_EVENT_KIND = "ingestion"
internal const val NORMALIZED_DERIVATION_KIND = "final_base_ingredients"

data class NormalizedBaseIngredientsRecord(
    val sourceEvent: NormalizedSourceIngestionRecord,
    val derivationKind: String,
    val baseIngredients: List<NormalizedBaseIngredientContributionRecord>
)

data class NormalizedSourceIngestionRecord(
    val kind: String,
    val id: Long,
    val itemName: String,
    val quantityLevel: Int,
    val occurredAtEpochMs: Long,
    val reusableEntityRef: ExportReusableEntityReference
)

data class NormalizedBaseIngredientContributionRecord(
    val reusableIngredientRef: ExportReusableEntityReference,
    val ingredientName: String,
    val contributionBasisPoints: Int,
    val derivedQuantityLevelBasisPoints: Int
)

internal class NormalizedIngredientBaseExportBuilder {
    fun build(snapshot: ExportSnapshot): AppResult<List<NormalizedBaseIngredientsRecord>> {
        val ingredientsById = snapshot.reusableIngredients.associateBy { it.id }
        val compoundsById = snapshot.reusableCompounds.associateBy { it.id }
        val normalizedRecords = mutableListOf<NormalizedBaseIngredientsRecord>()
        for (ingestion in snapshot.ingestions) {
            val reusableEntityRef = ingestion.reusableEntityRef
                ?: return AppResult.Error(
                    DomainError.ExportError(
                        "Export blocked: ingestion record ${ingestion.id} is missing reusable_entity_ref required for normalized export. Resolve the inconsistency before exporting."
                    )
                )
            val quantityLevel = ingestion.quantityLevel
                .takeIf { IngestionQuantityOption.fromLevel(it) != null }
                ?: return AppResult.Error(
                    DomainError.ExportError(
                        "Export blocked: ingestion record ${ingestion.id} has invalid quantity_level ${ingestion.quantityLevel}. Expected a value between 1 and 5."
                    )
                )
            val baseIngredientBasisPoints = linkedMapOf<Long, Int>()
            when (reusableEntityRef.kind) {
                ReusableEntityKind.INGREDIENT.storageValue -> {
                    val ingredient = ingredientsById[reusableEntityRef.id]
                        ?: return missingIngredientResult(ingestion.id, reusableEntityRef.id)
                    baseIngredientBasisPoints[ingredient.id] = BASIS_POINTS_SCALE
                }

                ReusableEntityKind.COMPOUND.storageValue -> {
                    when (
                        val expansionResult = expandCompound(
                            rootIngestionId = ingestion.id,
                            compoundId = reusableEntityRef.id,
                            compoundShareBasisPoints = BASIS_POINTS_SCALE,
                            ingredientsById = ingredientsById,
                            compoundsById = compoundsById,
                            aggregateBasisPoints = baseIngredientBasisPoints,
                            traversalPath = linkedSetOf(reusableEntityRef.id)
                        )
                    ) {
                        is AppResult.Success -> Unit
                        is AppResult.Error -> return expansionResult
                    }
                }

                else -> {
                    return AppResult.Error(
                        DomainError.ExportError(
                            "Export blocked: ingestion record ${ingestion.id} references unsupported reusable entity kind '${reusableEntityRef.kind}'."
                        )
                    )
                }
            }
            val baseIngredients = baseIngredientBasisPoints.entries
                .sortedBy { it.key }
                .map { (ingredientId, contributionBasisPoints) ->
                    val ingredient = ingredientsById[ingredientId]
                        ?: return missingIngredientResult(ingestion.id, ingredientId)
                    NormalizedBaseIngredientContributionRecord(
                        reusableIngredientRef = ExportReusableEntityReference(
                            kind = ReusableEntityKind.INGREDIENT.storageValue,
                            id = ingredientId
                        ),
                        ingredientName = ingredient.name,
                        contributionBasisPoints = contributionBasisPoints,
                        derivedQuantityLevelBasisPoints = quantityLevel * contributionBasisPoints
                    )
                }
            normalizedRecords += NormalizedBaseIngredientsRecord(
                sourceEvent = NormalizedSourceIngestionRecord(
                    kind = INGESTION_EVENT_KIND,
                    id = ingestion.id,
                    itemName = ingestion.itemName,
                    quantityLevel = ingestion.quantityLevel,
                    occurredAtEpochMs = ingestion.occurredAtEpochMs,
                    reusableEntityRef = reusableEntityRef
                ),
                derivationKind = NORMALIZED_DERIVATION_KIND,
                baseIngredients = baseIngredients
            )
        }
        return AppResult.Success(normalizedRecords.sortedBy { it.sourceEvent.id })
    }

    private fun expandCompound(
        rootIngestionId: Long,
        compoundId: Long,
        compoundShareBasisPoints: Int,
        ingredientsById: Map<Long, ExportReusableIngredientRecord>,
        compoundsById: Map<Long, ExportReusableCompoundRecord>,
        aggregateBasisPoints: MutableMap<Long, Int>,
        traversalPath: LinkedHashSet<Long>
    ): AppResult<Unit> {
        val compound = compoundsById[compoundId]
            ?: return AppResult.Error(
                DomainError.ExportError(
                    "Export blocked: ingestion record $rootIngestionId references missing reusable compound $compoundId. Resolve the inconsistency before exporting."
                )
            )
        if (compound.compositionStatus != "complete") {
            return AppResult.Error(
                DomainError.ExportError(
                    "Export blocked: compound $compoundId is not complete and cannot be normalized."
                )
            )
        }
        if (compound.components.isEmpty()) {
            return AppResult.Error(
                DomainError.ExportError(
                    "Export blocked: compound $compoundId has no components to normalize."
                )
            )
        }
        val allocations = allocateDeterministicShares(
            ownerCompoundId = compoundId,
            parentShareBasisPoints = compoundShareBasisPoints,
            components = compound.components.sortedWith(compareBy({ it.position }, { it.id }))
        )
        when (allocations) {
            is AppResult.Error -> return allocations
            is AppResult.Success -> {
                allocations.value.forEach { (component, componentShareBasisPoints) ->
                    when (component.componentRef.kind) {
                        ReusableEntityKind.INGREDIENT.storageValue -> {
                            val ingredientId = component.componentRef.id
                            if (ingredientId !in ingredientsById) {
                                return missingIngredientResult(rootIngestionId, ingredientId)
                            }
                            aggregateBasisPoints[ingredientId] =
                                aggregateBasisPoints.getOrDefault(ingredientId, 0) + componentShareBasisPoints
                        }

                        ReusableEntityKind.COMPOUND.storageValue -> {
                            val childCompoundId = component.componentRef.id
                            if (!traversalPath.add(childCompoundId)) {
                                return AppResult.Error(
                                    DomainError.ExportError(
                                        "Export blocked: recursive compound loop detected while normalizing ingestion record $rootIngestionId at compound $childCompoundId."
                                    )
                                )
                            }
                            when (
                                val childResult = expandCompound(
                                    rootIngestionId = rootIngestionId,
                                    compoundId = childCompoundId,
                                    compoundShareBasisPoints = componentShareBasisPoints,
                                    ingredientsById = ingredientsById,
                                    compoundsById = compoundsById,
                                    aggregateBasisPoints = aggregateBasisPoints,
                                    traversalPath = traversalPath
                                )
                            ) {
                                is AppResult.Success -> Unit
                                is AppResult.Error -> return childResult
                            }
                            traversalPath.remove(childCompoundId)
                        }

                        else -> {
                            return AppResult.Error(
                                DomainError.ExportError(
                                    "Export blocked: compound $compoundId component ${component.id} uses unsupported component kind '${component.componentRef.kind}'."
                                )
                            )
                        }
                    }
                }
            }
        }
        return AppResult.Success(Unit)
    }

    private fun allocateDeterministicShares(
        ownerCompoundId: Long,
        parentShareBasisPoints: Int,
        components: List<ExportReusableCompoundComponentRecord>
    ): AppResult<List<Pair<ExportReusableCompoundComponentRecord, Int>>> {
        data class AllocationCandidate(
            val component: ExportReusableCompoundComponentRecord,
            val floorShare: Int,
            val remainder: Int
        )

        val candidates = components.map { component ->
            val proportion = component.proportionBasisPoints
                ?: return AppResult.Error(
                    DomainError.ExportError(
                        "Export blocked: compound $ownerCompoundId component ${component.id} is missing proportion_basis_points."
                    )
                )
            val exactShare = parentShareBasisPoints.toLong() * proportion.toLong()
            AllocationCandidate(
                component = component,
                floorShare = (exactShare / BASIS_POINTS_SCALE).toInt(),
                remainder = (exactShare % BASIS_POINTS_SCALE).toInt()
            )
        }
        val totalProportionBasisPoints = components.sumOf { it.proportionBasisPoints ?: 0 }
        if (totalProportionBasisPoints != BASIS_POINTS_SCALE) {
            return AppResult.Error(
                DomainError.ExportError(
                    "Export blocked: compound $ownerCompoundId has invalid total proportion_basis_points $totalProportionBasisPoints. Expected $BASIS_POINTS_SCALE."
                )
            )
        }
        val distributedFloor = candidates.sumOf { it.floorShare }
        val remainderUnits = parentShareBasisPoints - distributedFloor
        if (remainderUnits < 0 || remainderUnits > candidates.size) {
            return AppResult.Error(
                DomainError.ExportError(
                    "Export blocked: compound $ownerCompoundId produced an invalid normalization remainder."
                )
            )
        }
        val prioritizedRemainders = candidates
            .sortedWith(compareByDescending<AllocationCandidate> { it.remainder }.thenBy { it.component.position }.thenBy { it.component.id })
            .take(remainderUnits)
            .map { it.component.id }
            .toSet()
        return AppResult.Success(
            candidates.map { candidate ->
                val extraUnit = if (candidate.component.id in prioritizedRemainders) 1 else 0
                candidate.component to (candidate.floorShare + extraUnit)
            }
        )
    }

    private fun missingIngredientResult(rootIngestionId: Long, ingredientId: Long): AppResult.Error {
        return AppResult.Error(
            DomainError.ExportError(
                "Export blocked: ingestion record $rootIngestionId references missing reusable ingredient $ingredientId. Resolve the inconsistency before exporting."
            )
        )
    }
}
