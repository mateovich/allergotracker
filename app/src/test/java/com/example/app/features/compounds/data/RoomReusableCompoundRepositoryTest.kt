package com.example.app.features.compounds.data

import com.example.app.core.common.result.AppResult
import com.example.app.features.compounds.domain.CompoundCompositionStatus
import com.example.app.features.compounds.domain.CreateReusableCompoundCommand
import com.example.app.features.compounds.domain.ReusableCompound
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomReusableCompoundRepositoryTest {

    @Test
    fun create_persists_draft_compound_locally() = runBlocking {
        val dao = FakeReusableCompoundDao()
        val repository = RoomReusableCompoundRepository(dao) { 1_700_000_123_000L }

        val result = repository.createCompound(
            CreateReusableCompoundCommand(name = "Breakfast mix", notes = "Base")
        )

        val saved = dao.inserted.single()
        assertTrue(result is AppResult.Success<ReusableCompound>)
        assertEquals("Breakfast mix", saved.name)
        assertEquals(CompoundCompositionStatus.DRAFT.storageValue, saved.compositionStatus)
    }

    @Test
    fun create_reuses_existing_compound_when_name_matches_ignoring_case() = runBlocking {
        val dao = FakeReusableCompoundDao(
            existing = ReusableCompoundEntity(
                id = 4L,
                name = "Breakfast mix",
                normalizedName = "breakfast mix",
                notes = null,
                compositionStatus = CompoundCompositionStatus.DRAFT.storageValue,
                createdAtEpochMs = 1L,
                updatedAtEpochMs = 1L
            )
        )
        val repository = RoomReusableCompoundRepository(dao) { 1_700_000_123_000L }

        val result = repository.createCompound(CreateReusableCompoundCommand(name = "breakfast mix", notes = null))

        val success = result as AppResult.Success<ReusableCompound>
        assertEquals(4L, success.value.id)
        assertTrue(dao.inserted.isEmpty())
    }

    @Test
    fun create_stores_normalized_name_for_db_uniqueness() = runBlocking {
        val dao = FakeReusableCompoundDao()
        val repository = RoomReusableCompoundRepository(dao) { 1_700_000_123_000L }

        repository.createCompound(CreateReusableCompoundCommand(name = "Breakfast mix", notes = null))

        assertEquals("breakfast mix", dao.inserted.single().normalizedName)
    }
}

private class FakeReusableCompoundDao(
    private val existing: ReusableCompoundEntity? = null
) : ReusableCompoundDao {
    val inserted = mutableListOf<ReusableCompoundEntity>()

    override suspend fun insertCompound(entity: ReusableCompoundEntity): Long {
        inserted += entity
        return 1L
    }

    override suspend fun findByNormalizedName(normalizedName: String): ReusableCompoundEntity? = existing

    override suspend fun existsById(id: Long): Boolean = existing?.id == id

    override fun observeAll(): Flow<List<ReusableCompoundWithComponents>> = emptyFlow()
}
