package com.example.app.features.compounds.data

import android.database.sqlite.SQLiteConstraintException
import com.example.app.core.common.error.DomainError
import com.example.app.core.common.result.AppResult
import com.example.app.features.compounds.domain.CompoundComponentKind
import com.example.app.features.compounds.domain.CompoundCompositionStatus
import com.example.app.features.compounds.domain.CreateReusableCompoundCommand
import com.example.app.features.compounds.domain.CreateReusableCompoundComponentCommand
import com.example.app.features.compounds.domain.ReusableCompound
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomReusableCompoundRepositoryTest {

    @Test
    fun create_persists_complete_compound_with_ordered_components_locally() = runBlocking {
        val dao = FakeReusableCompoundDao()
        val repository = RoomReusableCompoundRepository(
            compoundDao = dao,
            ingredientExists = { it == 7L },
            compoundExists = { it == 9L },
            nowEpochMillis = { 1_700_000_123_000L }
        )

        val result = repository.createCompound(
            CreateReusableCompoundCommand(
                name = "  Breakfast   mix  ",
                notes = "Base",
                components = listOf(
                    CreateReusableCompoundComponentCommand(
                        kind = CompoundComponentKind.INGREDIENT,
                        reusableEntityId = 7L,
                        proportionBasisPoints = 6_000
                    ),
                    CreateReusableCompoundComponentCommand(
                        kind = CompoundComponentKind.COMPOUND,
                        reusableEntityId = 9L,
                        proportionBasisPoints = 4_000
                    )
                )
            )
        )

        val saved = dao.inserted.single()
        assertTrue(result is AppResult.Success<ReusableCompound>)
        assertEquals("Breakfast mix", saved.name)
        assertEquals(CompoundCompositionStatus.COMPLETE.storageValue, saved.compositionStatus)
        assertEquals(2, dao.insertedComponents.size)
        assertEquals(0, dao.insertedComponents[0].position)
        assertEquals(7L, dao.insertedComponents[0].ingredientId)
        assertEquals(1, dao.insertedComponents[1].position)
        assertEquals(9L, dao.insertedComponents[1].childCompoundId)
    }

    @Test
    fun create_rejects_duplicate_name_instead_of_reusing_existing_compound() = runBlocking {
        val dao = FakeReusableCompoundDao(
            existing = ReusableCompoundEntity(
                id = 4L,
                name = "Breakfast mix",
                normalizedName = "breakfast mix",
                notes = null,
                compositionStatus = CompoundCompositionStatus.COMPLETE.storageValue,
                createdAtEpochMs = 1L,
                updatedAtEpochMs = 1L
            )
        )
        val repository = RoomReusableCompoundRepository(
            compoundDao = dao,
            ingredientExists = { true },
            compoundExists = { true },
            nowEpochMillis = { 1_700_000_123_000L }
        )

        val result = repository.createCompound(
            CreateReusableCompoundCommand(
                name = "breakfast mix",
                notes = null,
                components = listOf(validIngredientComponent())
            )
        )

        val error = result as AppResult.Error
        assertEquals("A compound with this name already exists.", (error.error as DomainError.ValidationError).fieldErrors["name"])
        assertTrue(dao.inserted.isEmpty())
    }

    @Test
    fun create_returns_validation_error_when_component_target_is_missing() = runBlocking {
        val dao = FakeReusableCompoundDao()
        val repository = RoomReusableCompoundRepository(
            compoundDao = dao,
            ingredientExists = { false },
            compoundExists = { false },
            nowEpochMillis = { 1_700_000_123_000L }
        )

        val result = repository.createCompound(
            CreateReusableCompoundCommand(
                name = "Breakfast mix",
                notes = null,
                components = listOf(validIngredientComponent())
            )
        )

        val error = result as AppResult.Error
        assertEquals(
            "Selected ingredient is no longer available.",
            (error.error as DomainError.ValidationError).fieldErrors["components[0].target"]
        )
        assertTrue(dao.inserted.isEmpty())
    }

    @Test
    fun create_returns_validation_error_when_component_list_is_empty() = runBlocking {
        val dao = FakeReusableCompoundDao()
        val repository = RoomReusableCompoundRepository(
            compoundDao = dao,
            ingredientExists = { true },
            compoundExists = { true },
            nowEpochMillis = { 1_700_000_123_000L }
        )

        val result = repository.createCompound(
            CreateReusableCompoundCommand(
                name = "Breakfast mix",
                notes = null,
                components = emptyList()
            )
        )

        val error = result as AppResult.Error
        assertEquals(
            "Add at least one component.",
            (error.error as DomainError.ValidationError).fieldErrors["components"]
        )
        assertTrue(dao.inserted.isEmpty())
    }

    @Test
    fun create_returns_validation_error_when_total_proportion_is_not_100_percent() = runBlocking {
        val dao = FakeReusableCompoundDao()
        val repository = RoomReusableCompoundRepository(
            compoundDao = dao,
            ingredientExists = { true },
            compoundExists = { true },
            nowEpochMillis = { 1_700_000_123_000L }
        )

        val result = repository.createCompound(
            CreateReusableCompoundCommand(
                name = "Breakfast mix",
                notes = null,
                components = listOf(
                    validIngredientComponent(proportionBasisPoints = 6_000),
                    CreateReusableCompoundComponentCommand(
                        kind = CompoundComponentKind.COMPOUND,
                        reusableEntityId = 9L,
                        proportionBasisPoints = 2_000
                    )
                )
            )
        )

        val error = result as AppResult.Error
        assertEquals(
            "Total proportion must equal 100%.",
            (error.error as DomainError.ValidationError).fieldErrors["componentsTotal"]
        )
        assertTrue(dao.inserted.isEmpty())
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
        val repository = RoomReusableCompoundRepository(
            compoundDao = dao,
            ingredientExists = { true },
            compoundExists = { true },
            nowEpochMillis = { 2_000L }
        )

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
        val repository = RoomReusableCompoundRepository(
            compoundDao = dao,
            ingredientExists = { true },
            compoundExists = { true },
            nowEpochMillis = { 2_000L }
        )

        val result = repository.getCompound(4L)

        val success = result as AppResult.Success<ReusableCompound>
        assertEquals(CompoundCompositionStatus.COMPLETE, success.value.compositionStatus)
        assertEquals(1, success.value.components.size)
        assertEquals(7L, success.value.components.single().ingredientId)
    }

    @Test
    fun delete_maps_fk_constraint_failures_to_integrity_error() = runBlocking {
        val dao = FakeReusableCompoundDao(
            existing = ReusableCompoundEntity(
                id = 4L,
                name = "Breakfast mix",
                normalizedName = "breakfast mix",
                notes = null,
                compositionStatus = CompoundCompositionStatus.DRAFT.storageValue,
                createdAtEpochMs = 1L,
                updatedAtEpochMs = 1L
            ),
            deleteFailure = SQLiteConstraintException("fk")
        )
        val repository = RoomReusableCompoundRepository(
            compoundDao = dao,
            ingredientExists = { true },
            compoundExists = { true },
            nowEpochMillis = { 2_000L }
        )

        val result = repository.deleteCompound(4L)

        val error = result as AppResult.Error
        assertEquals(
            DomainError.IntegrityError(
                "This compound is still linked from other records. Replace those references before retiring it."
            ),
            error.error
        )
    }
}

private fun validIngredientComponent(
    proportionBasisPoints: Int = 10_000
): CreateReusableCompoundComponentCommand {
    return CreateReusableCompoundComponentCommand(
        kind = CompoundComponentKind.INGREDIENT,
        reusableEntityId = 7L,
        proportionBasisPoints = proportionBasisPoints
    )
}

private class FakeReusableCompoundDao(
    private val existing: ReusableCompoundEntity? = null,
    private val existingComponents: List<ReusableCompoundComponentEntity> = emptyList(),
    private val deleteFailure: Exception? = null
) : ReusableCompoundDao {
    val inserted = mutableListOf<ReusableCompoundEntity>()
    val insertedComponents = mutableListOf<ReusableCompoundComponentEntity>()
    val updated = mutableListOf<ReusableCompoundEntity>()

    override suspend fun insertCompound(entity: ReusableCompoundEntity): Long {
        inserted += entity
        return 1L
    }

    override suspend fun insertComponents(entities: List<ReusableCompoundComponentEntity>) {
        insertedComponents += entities
    }

    override suspend fun findByNormalizedName(normalizedName: String): ReusableCompoundEntity? = existing

    override suspend fun findById(id: Long): ReusableCompoundEntity? = existing?.takeIf { it.id == id }

    override suspend fun findCompoundWithComponentsById(id: Long): ReusableCompoundWithComponents? {
        val compound = when {
            existing?.id == id -> existing
            inserted.isNotEmpty() && id == 1L -> inserted.single().copy(id = 1L)
            else -> null
        } ?: return null
        val components = when {
            existing?.id == id -> existingComponents
            insertedComponents.isNotEmpty() && id == 1L -> insertedComponents.mapIndexed { index, component ->
                component.copy(id = index + 1L, compoundId = id)
            }
            else -> emptyList()
        }
        return ReusableCompoundWithComponents(compound = compound, components = components)
    }

    override suspend fun updateCompound(entity: ReusableCompoundEntity): Int {
        if (existing?.id == entity.id) {
            updated += entity
            return 1
        }
        return 0
    }

    override suspend fun deleteComponentsByCompoundId(compoundId: Long): Int {
        val removed = insertedComponents.count { it.compoundId == compoundId }
        insertedComponents.removeAll { it.compoundId == compoundId }
        return removed
    }

    override suspend fun deleteCompoundById(id: Long): Int {
        deleteFailure?.let { throw it }
        return if (existing?.id == id) 1 else 0
    }

    override suspend fun existsById(id: Long): Boolean = existing?.id == id || id == 1L

    override suspend fun countIngredientComponentReferences(ingredientId: Long): Int {
        return existingComponents.count { it.ingredientId == ingredientId }
    }

    override suspend fun countChildCompoundReferences(compoundId: Long): Int {
        return existingComponents.count { it.childCompoundId == compoundId }
    }

    override suspend fun touchById(id: Long, updatedAtEpochMs: Long): Int = if (existing?.id == id) 1 else 0

    override fun observeAll(): Flow<List<ReusableCompoundWithComponents>> = emptyFlow()
}
