package io.github.mateovich.allergotracker.features.compounds.domain

import io.github.mateovich.allergotracker.core.common.error.DomainError
import io.github.mateovich.allergotracker.core.common.result.AppResult
import kotlinx.coroutines.CancellationException

data class ReplaceIngredientWithCompoundImpact(
    val ingredientName: String,
    val ingestionReferenceCount: Int,
    val compoundReferenceCount: Int
)

data class ReplaceIngredientWithCompoundResult(
    val compound: ReusableCompound,
    val reassignedIngestionCount: Int,
    val replacedComponentCount: Int
)

class ReplaceIngredientWithCompoundUseCase(
    private val createCompound: suspend (CreateReusableCompoundCommand) -> AppResult<ReusableCompound>,
    private val getIngredientName: suspend (Long) -> AppResult<String>,
    private val deleteIngredient: suspend (Long) -> AppResult<Unit>,
    private val countIngestionReferences: suspend (Long) -> Int,
    private val countComponentReferences: suspend (Long) -> Int,
    private val findParentCompoundIds: suspend (Long) -> List<Long>,
    private val findChildCompoundEdges: suspend () -> List<CompoundChildEdge>,
    private val reassignIngestionReferences: suspend (ingredientId: Long, compoundId: Long, compoundName: String) -> Int,
    private val replaceComponentReferences: suspend (ingredientId: Long, compoundId: Long) -> Int,
    private val runAtomically: suspend (suspend () -> AppResult<ReplaceIngredientWithCompoundResult>) -> AppResult<ReplaceIngredientWithCompoundResult> = { block -> block() }
) {
    suspend fun impact(ingredientId: Long): AppResult<ReplaceIngredientWithCompoundImpact> {
        return try {
            when (val name = getIngredientName(ingredientId)) {
                is AppResult.Success -> AppResult.Success(
                    ReplaceIngredientWithCompoundImpact(
                        ingredientName = name.value,
                        ingestionReferenceCount = countIngestionReferences(ingredientId),
                        compoundReferenceCount = countComponentReferences(ingredientId)
                    )
                )

                is AppResult.Error -> name
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            AppResult.Error(DomainError.PersistenceError("Couldn't inspect ingredient references locally. Try again."))
        }
    }

    suspend operator fun invoke(
        ingredientId: Long,
        command: CreateReusableCompoundCommand
    ): AppResult<ReplaceIngredientWithCompoundResult> {
        if (command.components.any { it.kind == CompoundComponentKind.INGREDIENT && it.reusableEntityId == ingredientId }) {
            return AppResult.Error(
                DomainError.ValidationError(
                    message = "Please fix the highlighted fields.",
                    fieldErrors = mapOf("components" to "Remove the ingredient being replaced from the composition.")
                )
            )
        }

        return try {
            runAtomically {
                val ingredientName = when (val name = getIngredientName(ingredientId)) {
                    is AppResult.Success -> name.value
                    is AppResult.Error -> throw ReplaceIngredientRollback(name.error)
                }
                val expectedIngestionCount = countIngestionReferences(ingredientId)
                val expectedComponentCount = countComponentReferences(ingredientId)
                val parentCompoundIds = findParentCompoundIds(ingredientId)

                val compound = when (val created = createCompound(command)) {
                    is AppResult.Success -> created.value
                    is AppResult.Error -> throw ReplaceIngredientRollback(created.error)
                }

                val edges = findChildCompoundEdges()
                if (parentCompoundIds.any { CompoundCompositionIntegrity.wouldCreateCycle(it, compound.id, edges) }) {
                    throw ReplaceIngredientRollback(
                        DomainError.IntegrityError(
                            "Replacing \"$ingredientName\" would make a compound contain itself. Remove the loop from the new composition first."
                        )
                    )
                }

                val reassigned = reassignIngestionReferences(ingredientId, compound.id, compound.name)
                if (reassigned != expectedIngestionCount) {
                    throw ReplaceIngredientRollback(
                        DomainError.IntegrityError("Couldn't safely relink saved records. Try again.")
                    )
                }
                val replaced = replaceComponentReferences(ingredientId, compound.id)
                if (replaced != expectedComponentCount) {
                    throw ReplaceIngredientRollback(
                        DomainError.IntegrityError("Couldn't safely update compounds that use \"$ingredientName\". Try again.")
                    )
                }
                when (val deleted = deleteIngredient(ingredientId)) {
                    is AppResult.Success -> Unit
                    is AppResult.Error -> throw ReplaceIngredientRollback(deleted.error)
                }

                AppResult.Success(
                    ReplaceIngredientWithCompoundResult(
                        compound = compound,
                        reassignedIngestionCount = reassigned,
                        replacedComponentCount = replaced
                    )
                )
            }
        } catch (rollback: ReplaceIngredientRollback) {
            AppResult.Error(rollback.error)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            AppResult.Error(DomainError.PersistenceError("Couldn't replace ingredient locally. Try again."))
        }
    }
}

private class ReplaceIngredientRollback(
    val error: DomainError
) : RuntimeException()
