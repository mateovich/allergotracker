package com.example.app.features.ingredients.data

import com.example.app.core.common.result.AppResult
import com.example.app.features.ingredients.domain.ReusableIngredient
import com.example.app.features.ingredients.domain.CreateReusableIngredientCommand
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomReusableIngredientRepositoryTest {

    @Test
    fun create_persists_ingredient_locally() = runBlocking {
        val dao = FakeReusableIngredientDao()
        val repository = RoomReusableIngredientRepository(dao) { 1_700_000_123_000L }

        val result = repository.createIngredient(
            CreateReusableIngredientCommand(name = "Oat milk", notes = "Staple")
        )

        val saved = dao.inserted.single()
        assertTrue(result is AppResult.Success<ReusableIngredient>)
        assertEquals("Oat milk", saved.name)
        assertEquals(1_700_000_123_000L, saved.createdAtEpochMs)
        assertEquals(1_700_000_123_000L, saved.updatedAtEpochMs)
    }

    @Test
    fun create_reuses_existing_ingredient_when_name_matches_ignoring_case() = runBlocking {
        val dao = FakeReusableIngredientDao(
            existing = ReusableIngredientEntity(
                id = 9L,
                name = "Oat milk",
                normalizedName = "oat milk",
                notes = null,
                createdAtEpochMs = 1L,
                updatedAtEpochMs = 1L
            )
        )
        val repository = RoomReusableIngredientRepository(dao) { 1_700_000_123_000L }

        val result = repository.createIngredient(CreateReusableIngredientCommand(name = "oat milk", notes = null))

        val success = result as AppResult.Success<ReusableIngredient>
        assertEquals(9L, success.value.id)
        assertTrue(dao.inserted.isEmpty())
    }

    @Test
    fun create_stores_normalized_name_for_db_uniqueness() = runBlocking {
        val dao = FakeReusableIngredientDao()
        val repository = RoomReusableIngredientRepository(dao) { 1_700_000_123_000L }

        repository.createIngredient(CreateReusableIngredientCommand(name = "Oat milk", notes = null))

        assertEquals("oat milk", dao.inserted.single().normalizedName)
    }
}

private class FakeReusableIngredientDao(
    private val existing: ReusableIngredientEntity? = null
) : ReusableIngredientDao {
    val inserted = mutableListOf<ReusableIngredientEntity>()

    override suspend fun insert(entity: ReusableIngredientEntity): Long {
        inserted += entity
        return 1L
    }

    override suspend fun findByNormalizedName(normalizedName: String): ReusableIngredientEntity? = existing

    override suspend fun existsById(id: Long): Boolean = existing?.id == id

    override fun observeAll(): Flow<List<ReusableIngredientEntity>> = emptyFlow()
}
