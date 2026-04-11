package com.example.app.features.compounds.domain

import com.example.app.core.common.error.DomainError
import com.example.app.core.common.result.AppResult
import com.example.app.core.validation.PostMergeIntegrityCheck
import com.example.app.core.validation.ValidatePostMergeIntegrityUseCase
import kotlinx.coroutines.CancellationException

data class ReusableCompoundMergePreview(
    val source: ReusableCompound,
    val destination: ReusableCompound,
    val ingestionReferenceCount: Int,
    val parentCompoundReferenceCount: Int,
    val conflictingParentCompoundCount: Int,
    val recursiveParentCompoundCount: Int,
    val isBlocked: Boolean,
    val blockMessage: String?
)

data class ReusableCompoundMergeResult(
    val sourceCompoundId: Long,
    val destination: ReusableCompound,
    val migratedIngestionReferenceCount: Int,
    val migratedParentCompoundReferenceCount: Int
)

class GetReusableCompoundMergePreviewUseCase(
    private val repository: ReusableCompoundRepository,
    private val countIngestionReferences: suspend (Long) -> Int = { 0 },
    private val countParentCompoundReferences: suspend (Long) -> Int = { 0 },
    private val countConflictingParentCompounds: suspend (Long, Long) -> Int = { _, _ -> 0 },
    private val countRecursiveParentCompounds: suspend (Long, Long) -> Int = { _, _ -> 0 }
) {
    suspend operator fun invoke(sourceCompoundId: Long, destinationCompoundId: Long): AppResult<ReusableCompoundMergePreview> {
        if (sourceCompoundId <= 0L || destinationCompoundId <= 0L) {
            return AppResult.Error(DomainError.IntegrityError("Saved compound is no longer available."))
        }

        return try {
            val source = when (val result = repository.getCompound(sourceCompoundId)) {
                is AppResult.Success -> result.value
                is AppResult.Error -> return result
            }
            val destination = when (val result = repository.getCompound(destinationCompoundId)) {
                is AppResult.Success -> result.value
                is AppResult.Error -> return result
            }
            if (source.id == destination.id) {
                return AppResult.Success(
                    ReusableCompoundMergePreview(
                        source = source,
                        destination = destination,
                        ingestionReferenceCount = 0,
                        parentCompoundReferenceCount = 0,
                        conflictingParentCompoundCount = 0,
                        recursiveParentCompoundCount = 0,
                        isBlocked = true,
                        blockMessage = "Choose a different canonical compound before merging."
                    )
                )
            }

            val ingestionReferenceCount = countIngestionReferences(source.id)
            val parentCompoundReferenceCount = countParentCompoundReferences(source.id)
            val conflictingParentCompoundCount = countConflictingParentCompounds(source.id, destination.id)
            val recursiveParentCompoundCount = countRecursiveParentCompounds(source.id, destination.id)
            val blockMessage = when {
                conflictingParentCompoundCount > 0 -> {
                    "Merge blocked: $conflictingParentCompoundCount parent compound${if (conflictingParentCompoundCount == 1) "" else "s"} would end up with duplicate child compounds. Resolve those compositions first."
                }

                recursiveParentCompoundCount > 0 -> {
                    "Merge blocked: $recursiveParentCompoundCount parent compound${if (recursiveParentCompoundCount == 1) "" else "s"} would become recursive after reassignment. Remove the recursive path first."
                }

                else -> null
            }

            AppResult.Success(
                ReusableCompoundMergePreview(
                    source = source,
                    destination = destination,
                    ingestionReferenceCount = ingestionReferenceCount,
                    parentCompoundReferenceCount = parentCompoundReferenceCount,
                    conflictingParentCompoundCount = conflictingParentCompoundCount,
                    recursiveParentCompoundCount = recursiveParentCompoundCount,
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

class MergeReusableCompoundUseCase(
    private val getPreview: GetReusableCompoundMergePreviewUseCase,
    private val getCompound: suspend (Long) -> AppResult<ReusableCompound>,
    private val deleteCompound: suspend (Long) -> AppResult<Unit>,
    private val reassignIngestionReferences: suspend (Long, Long, String) -> Int = { _, _, _ -> 0 },
    private val replaceParentCompoundReferences: suspend (Long, Long) -> Int = { _, _ -> 0 },
    private val countIngestionReferences: suspend (Long) -> Int = { 0 },
    private val countParentCompoundReferences: suspend (Long) -> Int = { 0 },
    private val findAffectedParentCompoundIds: suspend (Long) -> List<Long> = { emptyList() },
    private val hasInvalidParentCompoundState: suspend (List<Long>) -> Boolean = { false },
    private val validatePostMergeIntegrity: ValidatePostMergeIntegrityUseCase = ValidatePostMergeIntegrityUseCase(),
    private val runAtomically: suspend (suspend () -> AppResult<ReusableCompoundMergeResult>) -> AppResult<ReusableCompoundMergeResult> = { block -> block() }
) {
    suspend operator fun invoke(sourceCompoundId: Long, destinationCompoundId: Long): AppResult<ReusableCompoundMergeResult> {
        val preview = when (val result = getPreview(sourceCompoundId, destinationCompoundId)) {
            is AppResult.Success -> result.value
            is AppResult.Error -> return result
        }

        val initialBlock = preview.blockMessage?.let(::mergeBlockedError)
        if (initialBlock != null) {
            return AppResult.Error(initialBlock)
        }

        return try {
            runAtomically {
                val confirmedPreview = when (val result = getPreview(sourceCompoundId, destinationCompoundId)) {
                    is AppResult.Success -> result.value
                    is AppResult.Error -> throw ReusableCompoundMergeRollback(result.error)
                }
                confirmedPreview.blockMessage?.let { message ->
                    throw ReusableCompoundMergeRollback(mergeBlockedError(message))
                }

                val destination = when (val result = getCompound(confirmedPreview.destination.id)) {
                    is AppResult.Success -> result.value
                    is AppResult.Error -> throw ReusableCompoundMergeRollback(
                        DomainError.IntegrityError(
                            "Merge stopped because the canonical compound is no longer available. Reload the preview and choose a saved compound again."
                        )
                    )
                }
                val destinationIngestionReferenceCountBeforeMerge = countIngestionReferences(destination.id)
                val destinationParentReferenceCountBeforeMerge = countParentCompoundReferences(destination.id)
                val affectedParentCompoundIds = findAffectedParentCompoundIds(confirmedPreview.source.id)
                val migratedIngestionReferenceCount = reassignIngestionReferences(
                    confirmedPreview.source.id,
                    destination.id,
                    destination.name
                )
                if (migratedIngestionReferenceCount != confirmedPreview.ingestionReferenceCount) {
                    throw ReusableCompoundMergeRollback(
                        DomainError.IntegrityError(
                            "Merge stopped because linked ingestion records changed during confirmation. Reload the preview and try again."
                        )
                    )
                }

                val migratedParentCompoundReferenceCount = replaceParentCompoundReferences(
                    confirmedPreview.source.id,
                    destination.id
                )
                if (migratedParentCompoundReferenceCount != confirmedPreview.parentCompoundReferenceCount) {
                    throw ReusableCompoundMergeRollback(
                        DomainError.IntegrityError(
                            "Merge stopped because affected parent compounds changed during confirmation. Reload the preview and resolve any new conflicts before retrying."
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
                                failureMessage = "Merge stopped because linked ingestion references were not fully reassigned to the canonical compound. Reload the preview and try again."
                            ),
                            PostMergeIntegrityCheck(
                                isValid = countParentCompoundReferences(confirmedPreview.source.id) == 0,
                                failureMessage = "Merge stopped because affected parent compound references remained inconsistent after reassignment. Reload the preview and resolve those compositions before retrying."
                            ),
                            PostMergeIntegrityCheck(
                                isValid = countParentCompoundReferences(destination.id) ==
                                    destinationParentReferenceCountBeforeMerge + migratedParentCompoundReferenceCount,
                                failureMessage = "Merge stopped because affected parent compound references were not fully reassigned to the canonical compound. Reload the preview and resolve those compositions before retrying."
                            ),
                            PostMergeIntegrityCheck(
                                isValid = !hasInvalidParentCompoundState(affectedParentCompoundIds),
                                failureMessage = "Merge stopped because affected parent compounds became inconsistent after reassignment. Reload the preview and resolve those compositions before retrying."
                            )
                        )
                    )
                ) {
                    is AppResult.Success -> Unit
                    is AppResult.Error -> throw ReusableCompoundMergeRollback(validationResult.error)
                }

                when (val deleteResult = deleteCompound(confirmedPreview.source.id)) {
                    is AppResult.Success -> {
                        AppResult.Success(
                            ReusableCompoundMergeResult(
                                sourceCompoundId = confirmedPreview.source.id,
                                destination = destination,
                                migratedIngestionReferenceCount = migratedIngestionReferenceCount,
                                migratedParentCompoundReferenceCount = migratedParentCompoundReferenceCount
                            )
                        )
                    }

                    is AppResult.Error -> throw ReusableCompoundMergeRollback(deleteResult.error)
                }
            }
        } catch (rollback: ReusableCompoundMergeRollback) {
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

private fun mergeBlockedError(message: String): DomainError.IntegrityError {
    return DomainError.IntegrityError(message)
}

private class ReusableCompoundMergeRollback(
    val error: DomainError
) : RuntimeException()
