package io.github.mateovich.allergotracker.features.compounds.domain

import io.github.mateovich.allergotracker.core.common.error.DomainError
import io.github.mateovich.allergotracker.core.common.result.AppResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MergeReusableCompoundUseCaseTest {

    @Test
    fun preview_blocks_when_source_and_destination_are_identical() = runBlocking {
        val repository = FakeMergeReusableCompoundRepository()
        val useCase = GetReusableCompoundMergePreviewUseCase(repository)

        val result = useCase(7L, 7L) as AppResult.Success

        assertTrue(result.value.isBlocked)
        assertEquals("Choose a different canonical compound before merging.", result.value.blockMessage)
    }

    @Test
    fun preview_reports_affected_ingestion_and_parent_counts() = runBlocking {
        val repository = FakeMergeReusableCompoundRepository()
        val useCase = GetReusableCompoundMergePreviewUseCase(
            repository = repository,
            countIngestionReferences = { 4 },
            countParentCompoundReferences = { 2 }
        )

        val result = useCase(7L, 8L) as AppResult.Success

        assertEquals(4, result.value.ingestionReferenceCount)
        assertEquals(2, result.value.parentCompoundReferenceCount)
        assertFalse(result.value.isBlocked)
    }

    @Test
    fun preview_blocks_when_parent_conflicts_would_be_created() = runBlocking {
        val repository = FakeMergeReusableCompoundRepository()
        val useCase = GetReusableCompoundMergePreviewUseCase(
            repository = repository,
            countConflictingParentCompounds = { _, _ -> 2 }
        )

        val result = useCase(7L, 8L) as AppResult.Success

        assertTrue(result.value.isBlocked)
        assertEquals(2, result.value.conflictingParentCompoundCount)
    }

    @Test
    fun preview_blocks_when_post_merge_graph_would_be_recursive() = runBlocking {
        val repository = FakeMergeReusableCompoundRepository()
        val useCase = GetReusableCompoundMergePreviewUseCase(
            repository = repository,
            countRecursiveParentCompounds = { _, _ -> 1 }
        )

        val result = useCase(7L, 8L) as AppResult.Success

        assertTrue(result.value.isBlocked)
        assertEquals(1, result.value.recursiveParentCompoundCount)
    }

    @Test
    fun preview_returns_error_when_destination_is_missing() = runBlocking {
        val repository = FakeMergeReusableCompoundRepository(missingIds = mutableSetOf(8L))
        val useCase = GetReusableCompoundMergePreviewUseCase(repository)

        val result = useCase(7L, 8L) as AppResult.Error

        assertEquals("Saved compound is no longer available.", result.error.message)
    }

    @Test
    fun merge_reassigns_ingestion_and_parent_references_before_deleting_source() = runBlocking {
        val repository = FakeMergeReusableCompoundRepository()
        val transactionState = CompoundMergeTransactionState(
            sourceIngestionReferences = 3,
            sourceParentReferences = 1
        )
        val getPreview = GetReusableCompoundMergePreviewUseCase(
            repository = repository,
            countIngestionReferences = { compoundId ->
                when (compoundId) {
                    7L -> transactionState.sourceIngestionReferences
                    8L -> transactionState.destinationIngestionReferences
                    else -> 0
                }
            },
            countParentCompoundReferences = { compoundId ->
                when (compoundId) {
                    7L -> transactionState.sourceParentReferences
                    8L -> transactionState.destinationParentReferences
                    else -> 0
                }
            }
        )
        var reassignedIngestionArgs: Triple<Long, Long, String>? = null
        var reassignedParentArgs: Pair<Long, Long>? = null
        val useCase = MergeReusableCompoundUseCase(
            getPreview = getPreview,
            getCompound = repository::getCompound,
            deleteCompound = repository::deleteCompound,
            reassignIngestionReferences = { sourceId, destinationId, destinationName ->
                reassignedIngestionArgs = Triple(sourceId, destinationId, destinationName)
                transactionState.sourceIngestionReferences = 0
                transactionState.destinationIngestionReferences = 3
                3
            },
            replaceParentCompoundReferences = { sourceId, destinationId ->
                reassignedParentArgs = sourceId to destinationId
                transactionState.sourceParentReferences = 0
                transactionState.destinationParentReferences = 1
                1
            },
            countIngestionReferences = { compoundId ->
                when (compoundId) {
                    7L -> transactionState.sourceIngestionReferences
                    8L -> transactionState.destinationIngestionReferences
                    else -> 0
                }
            },
            countParentCompoundReferences = { compoundId ->
                when (compoundId) {
                    7L -> transactionState.sourceParentReferences
                    8L -> transactionState.destinationParentReferences
                    else -> 0
                }
            }
        )

        val result = useCase(7L, 8L) as AppResult.Success

        assertEquals(Triple(7L, 8L, "Breakfast mix plus"), reassignedIngestionArgs)
        assertEquals(7L to 8L, reassignedParentArgs)
        assertEquals(listOf(7L), repository.deletedIds)
        assertEquals(3, result.value.migratedIngestionReferenceCount)
        assertEquals(1, result.value.migratedParentCompoundReferenceCount)
    }

    @Test
    fun merge_returns_integrity_error_when_partial_failure_requires_rollback() = runBlocking {
        val repository = FakeMergeReusableCompoundRepository()
        val getPreview = GetReusableCompoundMergePreviewUseCase(
            repository = repository,
            countIngestionReferences = { 2 },
            countParentCompoundReferences = { 1 }
        )
        val useCase = MergeReusableCompoundUseCase(
            getPreview = getPreview,
            getCompound = repository::getCompound,
            deleteCompound = repository::deleteCompound,
            reassignIngestionReferences = { _, _, _ -> 2 },
            replaceParentCompoundReferences = { _, _ -> 0 }
        )

        val result = useCase(7L, 8L) as AppResult.Error

        assertEquals(
            "Merge stopped because affected parent compounds changed during confirmation. Reload the preview and resolve any new conflicts before retrying.",
            result.error.message
        )
        assertTrue(repository.deletedIds.isEmpty())
    }

    @Test
    fun merge_rolls_back_partial_mutation_when_post_write_validation_fails() = runBlocking {
        val repository = FakeMergeReusableCompoundRepository()
        val transactionState = CompoundMergeTransactionState(
            sourceIngestionReferences = 2,
            sourceParentReferences = 1
        )
        val getPreview = GetReusableCompoundMergePreviewUseCase(
            repository = repository,
            countIngestionReferences = { compoundId ->
                when (compoundId) {
                    7L -> transactionState.sourceIngestionReferences
                    8L -> transactionState.destinationIngestionReferences
                    else -> 0
                }
            },
            countParentCompoundReferences = { compoundId ->
                when (compoundId) {
                    7L -> transactionState.sourceParentReferences
                    8L -> transactionState.destinationParentReferences
                    else -> 0
                }
            }
        )
        val useCase = MergeReusableCompoundUseCase(
            getPreview = getPreview,
            getCompound = repository::getCompound,
            deleteCompound = repository::deleteCompound,
            reassignIngestionReferences = { _, _, _ ->
                transactionState.sourceIngestionReferences = 0
                transactionState.destinationIngestionReferences = 2
                2
            },
            replaceParentCompoundReferences = { _, _ ->
                transactionState.sourceParentReferences = 0
                transactionState.destinationParentReferences = 1
                1
            },
            countIngestionReferences = { compoundId ->
                when (compoundId) {
                    7L -> transactionState.sourceIngestionReferences
                    8L -> transactionState.destinationIngestionReferences
                    else -> 0
                }
            },
            countParentCompoundReferences = { compoundId ->
                when (compoundId) {
                    7L -> transactionState.sourceParentReferences
                    8L -> transactionState.destinationParentReferences
                    else -> 0
                }
            },
            findAffectedParentCompoundIds = { listOf(31L) },
            hasInvalidParentCompoundState = { true },
            runAtomically = { block ->
                val snapshot = transactionState.copy()
                try {
                    block()
                } catch (error: RuntimeException) {
                    transactionState.restoreFrom(snapshot)
                    throw error
                }
            }
        )

        val result = useCase(7L, 8L) as AppResult.Error

        assertEquals(
            "Merge stopped because affected parent compounds became inconsistent after reassignment. Reload the preview and resolve those compositions before retrying.",
            result.error.message
        )
        assertEquals(2, transactionState.sourceIngestionReferences)
        assertEquals(0, transactionState.destinationIngestionReferences)
        assertEquals(1, transactionState.sourceParentReferences)
        assertEquals(0, transactionState.destinationParentReferences)
        assertTrue(repository.deletedIds.isEmpty())
    }

    @Test
    fun merge_rolls_back_when_ingestion_references_remain_after_reassignment() = runBlocking {
        val repository = FakeMergeReusableCompoundRepository()
        val transactionState = CompoundMergeTransactionState(
            sourceIngestionReferences = 2,
            sourceParentReferences = 1
        )
        val getPreview = GetReusableCompoundMergePreviewUseCase(
            repository = repository,
            countIngestionReferences = { compoundId ->
                when (compoundId) {
                    7L -> transactionState.sourceIngestionReferences
                    8L -> transactionState.destinationIngestionReferences
                    else -> 0
                }
            },
            countParentCompoundReferences = { compoundId ->
                when (compoundId) {
                    7L -> transactionState.sourceParentReferences
                    8L -> transactionState.destinationParentReferences
                    else -> 0
                }
            }
        )
        val useCase = MergeReusableCompoundUseCase(
            getPreview = getPreview,
            getCompound = repository::getCompound,
            deleteCompound = repository::deleteCompound,
            reassignIngestionReferences = { _, _, _ ->
                transactionState.destinationIngestionReferences = 2
                2
            },
            replaceParentCompoundReferences = { _, _ ->
                transactionState.sourceParentReferences = 0
                transactionState.destinationParentReferences = 1
                1
            },
            countIngestionReferences = { compoundId ->
                when (compoundId) {
                    7L -> transactionState.sourceIngestionReferences
                    8L -> transactionState.destinationIngestionReferences
                    else -> 0
                }
            },
            countParentCompoundReferences = { compoundId ->
                when (compoundId) {
                    7L -> transactionState.sourceParentReferences
                    8L -> transactionState.destinationParentReferences
                    else -> 0
                }
            },
            runAtomically = { block ->
                val snapshot = transactionState.copy()
                try {
                    block()
                } catch (error: RuntimeException) {
                    transactionState.restoreFrom(snapshot)
                    throw error
                }
            }
        )

        val result = useCase(7L, 8L) as AppResult.Error

        assertEquals(
            "Merge stopped because linked ingestion references remained inconsistent after reassignment. Reload the preview and try again.",
            result.error.message
        )
        assertEquals(2, transactionState.sourceIngestionReferences)
        assertEquals(0, transactionState.destinationIngestionReferences)
        assertEquals(1, transactionState.sourceParentReferences)
        assertEquals(0, transactionState.destinationParentReferences)
        assertTrue(repository.deletedIds.isEmpty())
    }

    @Test
    fun merge_rechecks_structural_blockers_inside_transaction_before_persisting() = runBlocking {
        val repository = FakeMergeReusableCompoundRepository()
        var previewCalls = 0
        val getPreview = GetReusableCompoundMergePreviewUseCase(
            repository = repository,
            countIngestionReferences = { 1 },
            countParentCompoundReferences = { 1 },
            countConflictingParentCompounds = { _, _ ->
                previewCalls += 1
                if (previewCalls == 1) 0 else 1
            }
        )
        val useCase = MergeReusableCompoundUseCase(
            getPreview = getPreview,
            getCompound = repository::getCompound,
            deleteCompound = repository::deleteCompound,
            reassignIngestionReferences = { _, _, _ -> 1 },
            replaceParentCompoundReferences = { _, _ -> 1 }
        )

        val result = useCase(7L, 8L) as AppResult.Error

        assertEquals(
            "Merge blocked: 1 parent compound would end up with duplicate child compounds. Resolve those compositions first.",
            result.error.message
        )
        assertTrue(repository.deletedIds.isEmpty())
    }

    @Test
    fun merge_uses_latest_destination_name_during_confirmation() = runBlocking {
        val repository = FakeMergeReusableCompoundRepository(
            compounds = mutableMapOf(
                7L to ReusableCompound(7L, "Breakfast mix", null, CompoundCompositionStatus.COMPLETE, emptyList(), 1L, 1L),
                8L to ReusableCompound(8L, "Breakfast mix plus renamed", null, CompoundCompositionStatus.COMPLETE, emptyList(), 1L, 2L)
            )
        )
        val transactionState = CompoundMergeTransactionState(
            sourceIngestionReferences = 1,
            sourceParentReferences = 0
        )
        val getPreview = GetReusableCompoundMergePreviewUseCase(
            repository = object : ReusableCompoundRepository by repository {
                override suspend fun getCompound(id: Long): AppResult<ReusableCompound> {
                    return when (id) {
                        7L -> AppResult.Success(
                            ReusableCompound(7L, "Breakfast mix", null, CompoundCompositionStatus.COMPLETE, emptyList(), 1L, 1L)
                        )

                        8L -> AppResult.Success(
                            ReusableCompound(8L, "Breakfast mix plus", null, CompoundCompositionStatus.COMPLETE, emptyList(), 1L, 2L)
                        )

                        else -> AppResult.Error(DomainError.IntegrityError("Saved compound is no longer available."))
                    }
                }
            },
            countIngestionReferences = { compoundId ->
                when (compoundId) {
                    7L -> transactionState.sourceIngestionReferences
                    8L -> transactionState.destinationIngestionReferences
                    else -> 0
                }
            },
            countParentCompoundReferences = { compoundId ->
                when (compoundId) {
                    7L -> transactionState.sourceParentReferences
                    8L -> transactionState.destinationParentReferences
                    else -> 0
                }
            }
        )
        var reassignedIngestionArgs: Triple<Long, Long, String>? = null
        val useCase = MergeReusableCompoundUseCase(
            getPreview = getPreview,
            getCompound = repository::getCompound,
            deleteCompound = repository::deleteCompound,
            reassignIngestionReferences = { sourceId, destinationId, destinationName ->
                reassignedIngestionArgs = Triple(sourceId, destinationId, destinationName)
                transactionState.sourceIngestionReferences = 0
                transactionState.destinationIngestionReferences = 1
                1
            },
            countIngestionReferences = { compoundId ->
                when (compoundId) {
                    7L -> transactionState.sourceIngestionReferences
                    8L -> transactionState.destinationIngestionReferences
                    else -> 0
                }
            },
            countParentCompoundReferences = { compoundId ->
                when (compoundId) {
                    7L -> transactionState.sourceParentReferences
                    8L -> transactionState.destinationParentReferences
                    else -> 0
                }
            }
        )

        val result = useCase(7L, 8L) as AppResult.Success

        assertEquals(Triple(7L, 8L, "Breakfast mix plus renamed"), reassignedIngestionArgs)
        assertEquals("Breakfast mix plus renamed", result.value.destination.name)
    }

    @Test
    fun merge_stops_when_destination_disappears_after_preview() = runBlocking {
        val repository = FakeMergeReusableCompoundRepository()
        val getPreview = GetReusableCompoundMergePreviewUseCase(
            repository = repository,
            countIngestionReferences = { 1 },
            countParentCompoundReferences = { 0 }
        )
        val useCase = MergeReusableCompoundUseCase(
            getPreview = getPreview,
            getCompound = { id ->
                if (id == 8L) {
                    AppResult.Error(DomainError.IntegrityError("Saved compound is no longer available."))
                } else {
                    repository.getCompound(id)
                }
            },
            deleteCompound = repository::deleteCompound,
            reassignIngestionReferences = { _, _, _ -> 1 }
        )

        val result = useCase(7L, 8L) as AppResult.Error

        assertEquals(
            "Merge stopped because the canonical compound is no longer available. Reload the preview and choose a saved compound again.",
            result.error.message
        )
        assertFalse(repository.deletedIds.contains(7L))
    }
}

private class FakeMergeReusableCompoundRepository(
    val missingIds: MutableSet<Long> = mutableSetOf(),
    private val compounds: MutableMap<Long, ReusableCompound> = mutableMapOf(
        7L to ReusableCompound(7L, "Breakfast mix", null, CompoundCompositionStatus.COMPLETE, emptyList(), 1L, 1L),
        8L to ReusableCompound(8L, "Breakfast mix plus", null, CompoundCompositionStatus.COMPLETE, emptyList(), 1L, 2L)
    )
) : ReusableCompoundRepository {
    val deletedIds = mutableListOf<Long>()

    override suspend fun createCompound(command: CreateReusableCompoundCommand): AppResult<ReusableCompound> = error("unused")

    override suspend fun getCompound(id: Long): AppResult<ReusableCompound> {
        return when {
            missingIds.contains(id) -> AppResult.Error(DomainError.IntegrityError("Saved compound is no longer available."))
            else -> compounds[id]?.let { AppResult.Success(it) }
                ?: AppResult.Error(DomainError.IntegrityError("Saved compound is no longer available."))
        }
    }

    override suspend fun deleteCompound(id: Long): AppResult<Unit> {
        deletedIds += id
        return AppResult.Success(Unit)
    }

    override fun observeCompounds(): Flow<List<ReusableCompound>> = emptyFlow()
}

private data class CompoundMergeTransactionState(
    var sourceIngestionReferences: Int,
    var destinationIngestionReferences: Int = 0,
    var sourceParentReferences: Int,
    var destinationParentReferences: Int = 0
) {
    fun restoreFrom(snapshot: CompoundMergeTransactionState) {
        sourceIngestionReferences = snapshot.sourceIngestionReferences
        destinationIngestionReferences = snapshot.destinationIngestionReferences
        sourceParentReferences = snapshot.sourceParentReferences
        destinationParentReferences = snapshot.destinationParentReferences
    }
}
