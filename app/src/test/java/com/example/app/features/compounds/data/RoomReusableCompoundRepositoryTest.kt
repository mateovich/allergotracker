package com.example.app.features.compounds.data

import com.example.app.core.common.result.AppResult
import com.example.app.features.compounds.domain.CompoundComponentKind
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
            CreateReusableCompoundCommand(name = "  Breakfast   mix  ", notes = "Base")
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

    @Test
    fun update_persists_trimmed_name_and_notes() = runBlocking {
        val dao = FakeReusableCompoundDao(
            existing = ReusableCompoundEntity(
                id = 4L,
                name = "Breakfast mix",
                normalizedName = "breakfast mix",
                notes = "Draft",
                compositionStatus = CompoundCompositionStatus.DRAFT.storageValue,
                createdAtEpochMs = 1L,
                updatedAtEpochMs = 1L
            )
        )
        val repository = RoomReusableCompoundRepository(dao) { 2_000L }

        val result = repository.updateCompound(
            com.example.app.features.compounds.domain.UpdateReusableCompoundCommand(
                id = 4L,
                name = "  Breakfast   mix plus  ",
                notes = "Updated"
            )
        )

        val success = result as AppResult.Success<ReusableCompound>
        assertEquals("Breakfast mix plus", success.value.name)
        assertEquals("breakfast mix plus", dao.updated.single().normalizedName)
        assertEquals("Updated", dao.updated.single().notes)
    }

    @Test
    fun get_returns_existing_compound_with_full_component_structure() = runBlocking {
        val dao = FakeReusableCompoundDao(
            existing = ReusableCompoundEntity(
                id = 4L,
                name = "Breakfast mix",
                normalizedName = "breakfast mix",
                notes = "Draft",
                compositionStatus = CompoundCompositionStatus.COMPLETE.storageValue,
                createdAtEpochMs = 1L,
                updatedAtEpochMs = 2L
            ),
            existingComponents = listOf(
                ReusableCompoundComponentEntity(
                    id = 40L,
                    compoundId = 4L,
                    position = 0,
                    componentKind = CompoundComponentKind.INGREDIENT.storageValue,
                    ingredientId = 7L,
                    childCompoundId = null,
                    proportionBasisPoints = 6_000
                )
            )
        )
        val repository = RoomReusableCompoundRepository(dao) { 2_000L }

        val result = repository.getCompound(4L)

        val success = result as AppResult.Success<ReusableCompound>
        assertEquals(CompoundCompositionStatus.COMPLETE, success.value.compositionStatus)
        assertEquals(1, success.value.components.size)
        assertEquals(7L, success.value.components.single().ingredientId)
    }
}

private class FakeReusableCompoundDao(
    private val existing: ReusableCompoundEntity? = null,
    private val existingComponents: List<ReusableCompoundComponentEntity> = emptyList()
) : ReusableCompoundDao {
    val inserted = mutableListOf<ReusableCompoundEntity>()
    val updated = mutableListOf<ReusableCompoundEntity>()

    override suspend fun insertCompound(entity: ReusableCompoundEntity): Long {
        inserted += entity
        return 1L
    }

    override suspend fun findByNormalizedName(normalizedName: String): ReusableCompoundEntity? = existing

    override suspend fun findById(id: Long): ReusableCompoundEntity? = existing?.takeIf { it.id == id }

    override suspend fun findCompoundWithComponentsById(id: Long): ReusableCompoundWithComponents? {
        val compound = existing?.takeIf { it.id == id } ?: return null
        return ReusableCompoundWithComponents(compound = compound, components = existingComponents)
    }

    override suspend fun updateCompound(entity: ReusableCompoundEntity): Int {
        if (existing?.id == entity.id) {
            updated += entity
            return 1
        }
        return 0
    }

    override suspend fun existsById(id: Long): Boolean = existing?.id == id

    override suspend fun touchById(id: Long, updatedAtEpochMs: Long): Int = if (existing?.id == id) 1 else 0

    override fun observeAll(): Flow<List<ReusableCompoundWithComponents>> = emptyFlow()
}
