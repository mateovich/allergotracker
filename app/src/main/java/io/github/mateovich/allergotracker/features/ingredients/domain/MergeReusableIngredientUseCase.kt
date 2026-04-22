package io.github.mateovich.allergotracker.features.ingredients.domain

import io.github.mateovich.allergotracker.core.common.error.DomainError
import io.github.mateovich.allergotracker.core.common.result.AppResult
import io.github.mateovich.allergotracker.core.validation.PostMergeIntegrityCheck
import io.github.mateovich.allergotracker.core.validation.ValidatePostMergeIntegrityUseCase
import kotlinx.coroutines.CancellationException

data class ReusableIngredientMergePreview(
    val source: ReusableIngredient,
    val destination: ReusableIngredient,
    val ingestionReferenceCount: Int,
    val compoundReferenceCount: Int,
    val conflictingCompoundCount: Int,
    val isBlocked: Boolean,
    val blockMessage: String?
)

data class ReusableIngredientMergeResult(
    val sourceIngredientId: Long,
    val destination: ReusableIngredient,
    val migratedIngestionReferenceCount: Int,
    val migratedCompoundReferenceCount: Int
)

class GetReusableIngredientMergePreviewUseCase(
    private val repository: ReusableIngredientRepository,
    private val countIngestionReferences: suspend (Long) -> Int = { 0 },
    private val countCompoundReferences: suspend (Long) -> Int = { 0 },
    private val countConflictingCompounds: suspend (Long, Long) -> Int = { _, _ -> 0 }
) {
    suspend operator fun invoke(sourceIngredientId: Long, destinationIngredientId: Long): AppResult<ReusableIngredientMergePreview> {
        if (sourceIngredientId <= 0L || destinationIngredientId <= 0L) {
            return AppResult.Error(DomainError.IntegrityError("Saved ingredient is no longer available."))
        }

        return try {
            val source = when (val result = repository.getIngredient(sourceIngredientId)) {
                is AppResult.Success -> result.value
                is AppResult.Error -> return result
            }
            val destination = when (val result = repository.getIngredient(destinationIngredientId)) {
                is AppResult.Success -> result.value
                is AppResult.Error -> return result
            }
            if (source.id == destination.id) {
                return AppResult.Success(
                    ReusableIngredientMergePreview(
                        source = source,
                        destination = destination,
                        ingestionReferenceCount = 0,
                        compoundReferenceCount = 0,
                        conflictingCompoundCount = 0,
                        isBlocked = true,
                        blockMessage = "Choose a different canonical ingredient before merging."
                    )
                )
            }

            val ingestionReferenceCount = countIngestionReferences(source.id)
            val compoundReferenceCount = countCompoundReferences(source.id)
            val conflictingCompoundCount = countConflictingCompounds(source.id, destination.id)
            val blockMessage = when {
                conflictingCompoundCount > 0 -> {
                    "Merge blocked: $conflictingCompoundCount compound${if (conflictingCompoundCount == 1) "" else "s"} would end up with duplicate ingredient components. Resolve those compounds first."
                }
                else -> null
            }

            AppResult.Success(
                ReusableIngredientMergePreview(
                    source = source,
                    destination = destination,
                    ingestionReferenceCount = ingestionReferenceCount,
                    compoundReferenceCount = compoundReferenceCount,
                    conflictingCompoundCount = conflictingCompoundCount,
                    isBlocked = blockMessage != null,
                    blockMessage = blockMessage
                )
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            AppResult.Error(DomainError.PersistenceError("Couldn't inspect merge impact locally. Try again."))
        }
    }
}

class MergeReusableIngredientUseCase(
    private val getPreview: GetReusableIngredientMergePreviewUseCase,
    private val getIngredient: suspend (Long) -> AppResult<ReusableIngredient>,
    private val deleteIngredient: suspend (Long) -> AppResult<Unit>,
    private val reassignIngestionReferences: suspend (Long, Long, String) -> Int = { _, _, _ -> 0 },
    private val replaceCompoundReferences: suspend (Long, Long) -> Int = { _, _ -> 0 },
    private val countIngestionReferences: suspend (Long) -> Int = { 0 },
    private val countCompoundReferences: suspend (Long) -> Int = { 0 },
    private val findAffectedCompoundIds: suspend (Long) -> List<Long> = { emptyList() },
    private val hasInvalidCompoundState: suspend (List<Long>) -> Boolean = { false },
    private val validatePostMergeIntegrity: ValidatePostMergeIntegrityUseCase = ValidatePostMergeIntegrityUseCase(),
    private val runAtomically: suspend (suspend () -> AppResult<ReusableIngredientMergeResult>) -> AppResult<ReusableIngredientMergeResult> = { block -> block() }
) {
    suspend operator fun invoke(sourceIngredientId: Long, destinationIngredientId: Long): AppResult<ReusableIngredientMergeResult> {
        val preview = when (val result = getPreview(sourceIngredientId, destinationIngredientId)) {
            is AppResult.Success -> result.value
            is AppResult.Error -> return result
        }

        if (preview.isBlocked) {
            return AppResult.Error(
                DomainError.IntegrityError(preview.blockMessage ?: "This ingredient merge is currently blocked.")
            )
        }

        return try {
            runAtomically {
                val confirmedPreview = when (val result = getPreview(sourceIngredientId, destinationIngredientId)) {
                    is AppResult.Success -> result.value
                    is AppResult.Error -> throw ReusableIngredientMergeRollback(result.error)
                }
                confirmedPreview.blockMessage?.let { message ->
                    throw ReusableIngredientMergeRollback(DomainError.IntegrityError(message))
                }

                val destination = when (val result = getIngredient(confirmedPreview.destination.id)) {
                    is AppResult.Success -> result.value
                    is AppResult.Error -> throw ReusableIngredientMergeRollback(
                        DomainError.IntegrityError(
                            "Merge stopped because the canonical ingredient is no longer available. Reload the preview and choose a saved ingredient again."
                        )
                    )
                }
                val destinationIngestionReferenceCountBeforeMerge = countIngestionReferences(destination.id)
                val destinationCompoundReferenceCountBeforeMerge = countCompoundReferences(destination.id)
                val affectedCompoundIds = findAffectedCompoundIds(confirmedPreview.source.id)
                val migratedIngestionReferenceCount = reassignIngestionReferences(
                    confirmedPreview.source.id,
                    destination.id,
                    destination.name
                )
                if (migratedIngestionReferenceCount != confirmedPreview.ingestionReferenceCount) {
                    throw ReusableIngredientMergeRollback(
                        DomainError.IntegrityError(
                            "Merge stopped because linked ingestion records changed during confirmation. Reload the preview and try again."
                        )
                    )
                }

                val migratedCompoundReferenceCount = replaceCompoundReferences(confirmedPreview.source.id, destination.id)
                if (migratedCompoundReferenceCount != confirmedPreview.compoundReferenceCount) {
                    throw ReusableIngredientMergeRollback(
                        DomainError.IntegrityError(
                            "Merge stopped because affected compound components changed during confirmation. Reload the preview and resolve any new conflicts before retrying."
                        )
                    )
                }

                when (
                    val validationResult = validatePostMergeIntegrity(
                        listOf(
                            PostMergeIntegrityCheck(
                                isValid = countIngestionReferences(confirmedPreview.source.id) == 0,
                                failureMessage = "Merge stopped because linked ingestion references remained inconsistent after reassignment. Reload the preview and try again."
                            ),
                            PostMergeIntegrityCheck(
                                isValid = countIngestionReferences(destination.id) ==
                                    destinationIngestionReferenceCountBeforeMerge + migratedIngestionReferenceCount,
                                failureMessage = "Merge stopped because linked ingestion references were not fully reassigned to the canonical ingredient. Reload the preview and try again."
                            ),
                            PostMergeIntegrityCheck(
                                isValid = countCompoundReferences(confirmedPreview.source.id) == 0,
                                failureMessage = "Merge stopped because affected compound references remained inconsistent after reassignment. Reload the preview and resolve those compounds before retrying."
                            ),
                            PostMergeIntegrityCheck(
                                isValid = countCompoundReferences(destination.id) ==
                                    destinationCompoundReferenceCountBeforeMerge + migratedCompoundReferenceCount,
                                failureMessage = "Merge stopped because affected compound references were not fully reassigned to the canonical ingredient. Reload the preview and resolve those compounds before retrying."
                            ),
                            PostMergeIntegrityCheck(
                                isValid = !hasInvalidCompoundState(affectedCompoundIds),
                                failureMessage = "Merge stopped because affected compound components became inconsistent after reassignment. Reload the preview and resolve those compounds before retrying."
                            )
                        )
                    )
                ) {
                    is AppResult.Success -> Unit
                    is AppResult.Error -> throw ReusableIngredientMergeRollback(validationResult.error)
                }

                when (val deleteResult = deleteIngredient(confirmedPreview.source.id)) {
                    is AppResult.Success -> {
                        AppResult.Success(
                            ReusableIngredientMergeResult(
                                sourceIngredientId = confirmedPreview.source.id,
                                destination = destination,
                                migratedIngestionReferenceCount = migratedIngestionReferenceCount,
                                migratedCompoundReferenceCount = migratedCompoundReferenceCount
                            )
                        )
                    }

                    is AppResult.Error -> throw ReusableIngredientMergeRollback(deleteResult.error)
                }
            }
        } catch (rollback: ReusableIngredientMergeRollback) {
            AppResult.Error(rollback.error)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            AppResult.Error(
                DomainError.PersistenceError(
                    "Merge couldn't be completed locally because stored data changed unexpectedly. Reload the preview and try again."
                )
            )
        }
    }
}

private class ReusableIngredientMergeRollback(
    val error: DomainError
) : RuntimeException()
