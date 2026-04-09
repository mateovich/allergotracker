package com.example.app.features.ingredients.domain

import com.example.app.core.common.error.DomainError
import com.example.app.core.common.result.AppResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MergeReusableIngredientUseCaseTest {

    @Test
    fun preview_blocks_when_source_and_destination_are_identical() = runBlocking {
        val repository = FakeMergeReusableIngredientRepository()
        val useCase = GetReusableIngredientMergePreviewUseCase(repository)

        val result = useCase(7L, 7L) as AppResult.Success

        assertTrue(result.value.isBlocked)
        assertEquals("Choose a different canonical ingredient before merging.", result.value.blockMessage)
    }

    @Test
    fun preview_reports_affected_ingestion_and_compound_counts() = runBlocking {
        val repository = FakeMergeReusableIngredientRepository()
        val useCase = GetReusableIngredientMergePreviewUseCase(
            repository = repository,
            countIngestionReferences = { 4 },
            countCompoundReferences = { 2 },
            countConflictingCompounds = { _, _ -> 0 }
        )

        val result = useCase(7L, 8L) as AppResult.Success

        assertEquals(4, result.value.ingestionReferenceCount)
        assertEquals(2, result.value.compoundReferenceCount)
        assertTrue(!result.value.isBlocked)
    }

    @Test
    fun preview_blocks_when_compound_conflicts_would_be_created() = runBlocking {
        val repository = FakeMergeReusableIngredientRepository()
        val useCase = GetReusableIngredientMergePreviewUseCase(
            repository = repository,
            countConflictingCompounds = { _, _ -> 2 }
        )

        val result = useCase(7L, 8L) as AppResult.Success

        assertTrue(result.value.isBlocked)
        assertEquals(2, result.value.conflictingCompoundCount)
    }

    @Test
    fun preview_returns_error_when_destination_is_missing() = runBlocking {
        val repository = FakeMergeReusableIngredientRepository(missingIds = mutableSetOf(8L))
        val useCase = GetReusableIngredientMergePreviewUseCase(repository)

        val result = useCase(7L, 8L) as AppResult.Error

        assertEquals("Saved ingredient is no longer available.", result.error.message)
    }

    @Test
    fun merge_reassigns_ingestion_and_compound_references_before_deleting_source() = runBlocking {
        val repository = FakeMergeReusableIngredientRepository()
        val getPreview = GetReusableIngredientMergePreviewUseCase(
            repository = repository,
            countIngestionReferences = { 3 },
            countCompoundReferences = { 1 },
            countConflictingCompounds = { _, _ -> 0 }
        )
        var reassignedIngestionArgs: Triple<Long, Long, String>? = null
        var reassignedCompoundArgs: Pair<Long, Long>? = null
        val useCase = MergeReusableIngredientUseCase(
            getPreview = getPreview,
            getIngredient = repository::getIngredient,
            deleteIngredient = repository::deleteIngredient,
            reassignIngestionReferences = { sourceId, destinationId, destinationName ->
                reassignedIngestionArgs = Triple(sourceId, destinationId, destinationName)
                3
            },
            replaceCompoundReferences = { sourceId, destinationId ->
                reassignedCompoundArgs = sourceId to destinationId
                1
            }
        )

        val result = useCase(7L, 8L) as AppResult.Success

        assertEquals(Triple(7L, 8L, "Oat milk plus"), reassignedIngestionArgs)
        assertEquals(7L to 8L, reassignedCompoundArgs)
        assertEquals(listOf(7L), repository.deletedIds)
        assertEquals(3, result.value.migratedIngestionReferenceCount)
        assertEquals(1, result.value.migratedCompoundReferenceCount)
    }

    @Test
    fun merge_returns_integrity_error_when_partial_failure_requires_rollback() = runBlocking {
        val repository = FakeMergeReusableIngredientRepository()
        val getPreview = GetReusableIngredientMergePreviewUseCase(
            repository = repository,
            countIngestionReferences = { 2 },
            countCompoundReferences = { 1 }
        )
        val useCase = MergeReusableIngredientUseCase(
            getPreview = getPreview,
            getIngredient = repository::getIngredient,
            deleteIngredient = repository::deleteIngredient,
            reassignIngestionReferences = { _, _, _ -> 2 },
            replaceCompoundReferences = { _, _ -> 0 }
        )

        val result = useCase(7L, 8L) as AppResult.Error

        assertEquals(
            "Merge stopped because affected compound components changed during confirmation. Reload the preview and resolve any new conflicts before retrying.",
            result.error.message
        )
        assertTrue(repository.deletedIds.isEmpty())
    }

    @Test
    fun merge_uses_latest_destination_name_during_confirmation() = runBlocking {
        val repository = FakeMergeReusableIngredientRepository(
            ingredients = mutableMapOf(
                7L to ReusableIngredient(7L, "Oat milk", null, 1L, 1L),
                8L to ReusableIngredient(8L, "Oat milk plus renamed", null, 1L, 2L)
            )
        )
        val getPreview = GetReusableIngredientMergePreviewUseCase(
            repository = object : ReusableIngredientRepository by repository {
                override suspend fun getIngredient(id: Long): AppResult<ReusableIngredient> {
                    return when (id) {
                        7L -> AppResult.Success(ReusableIngredient(7L, "Oat milk", null, 1L, 1L))
                        8L -> AppResult.Success(ReusableIngredient(8L, "Oat milk plus", null, 1L, 2L))
                        else -> AppResult.Error(DomainError.IntegrityError("Saved ingredient is no longer available."))
                    }
                }
            },
            countIngestionReferences = { 1 },
            countCompoundReferences = { 0 }
        )
        var reassignedIngestionArgs: Triple<Long, Long, String>? = null
        val useCase = MergeReusableIngredientUseCase(
            getPreview = getPreview,
            getIngredient = repository::getIngredient,
            deleteIngredient = repository::deleteIngredient,
            reassignIngestionReferences = { sourceId, destinationId, destinationName ->
                reassignedIngestionArgs = Triple(sourceId, destinationId, destinationName)
                1
            }
        )

        val result = useCase(7L, 8L) as AppResult.Success

        assertEquals(Triple(7L, 8L, "Oat milk plus renamed"), reassignedIngestionArgs)
        assertEquals("Oat milk plus renamed", result.value.destination.name)
    }

    @Test
    fun merge_stops_when_destination_disappears_after_preview() = runBlocking {
        val repository = FakeMergeReusableIngredientRepository()
        val getPreview = GetReusableIngredientMergePreviewUseCase(
            repository = repository,
            countIngestionReferences = { 1 },
            countCompoundReferences = { 0 }
        )
        val useCase = MergeReusableIngredientUseCase(
            getPreview = getPreview,
            getIngredient = { id ->
                if (id == 8L) {
                    AppResult.Error(DomainError.IntegrityError("Saved ingredient is no longer available."))
                } else {
                    repository.getIngredient(id)
                }
            },
            deleteIngredient = repository::deleteIngredient,
            reassignIngestionReferences = { _, _, _ -> 1 }
        )

        val result = useCase(7L, 8L) as AppResult.Error

        assertEquals(
            "Merge stopped because the canonical ingredient is no longer available. Reload the preview and choose a saved ingredient again.",
            result.error.message
        )
        assertFalse(repository.deletedIds.contains(7L))
    }
}

private class FakeMergeReusableIngredientRepository(
    val missingIds: MutableSet<Long> = mutableSetOf(),
    private val ingredients: MutableMap<Long, ReusableIngredient> = mutableMapOf(
        7L to ReusableIngredient(7L, "Oat milk", null, 1L, 1L),
        8L to ReusableIngredient(8L, "Oat milk plus", null, 1L, 2L)
    )
) : ReusableIngredientRepository {
    val deletedIds = mutableListOf<Long>()

    override suspend fun createIngredient(command: CreateReusableIngredientCommand): AppResult<ReusableIngredient> = error("unused")

    override suspend fun getIngredient(id: Long): AppResult<ReusableIngredient> {
        return when {
            missingIds.contains(id) -> AppResult.Error(DomainError.IntegrityError("Saved ingredient is no longer available."))
            else -> ingredients[id]?.let { AppResult.Success(it) }
                ?: AppResult.Error(DomainError.IntegrityError("Saved ingredient is no longer available."))
        }
    }

    override suspend fun deleteIngredient(id: Long): AppResult<Unit> {
        deletedIds += id
        return AppResult.Success(Unit)
    }

    override fun observeIngredients(): Flow<List<ReusableIngredient>> = emptyFlow()
}
