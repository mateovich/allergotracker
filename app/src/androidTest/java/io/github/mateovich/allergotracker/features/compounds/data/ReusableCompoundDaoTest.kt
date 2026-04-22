package io.github.mateovich.allergotracker.features.compounds.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.mateovich.allergotracker.core.database.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReusableCompoundDaoTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var databaseName: String
    private lateinit var dao: ReusableCompoundDao

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        databaseName = "compound-dao-test-${System.nanoTime()}"
        context.deleteDatabase(databaseName)
        database = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
            .allowMainThreadQueries()
            .build()
        dao = database.reusableCompoundDao()
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun count_compound_merge_conflicting_parents_counts_source_destination_and_duplicate_source_rows() = runBlocking {
        val sourceId = insertCompound("Source")
        val destinationId = insertCompound("Destination")
        val parentWithDestinationId = insertCompound("Parent with destination")
        val parentWithDuplicateSourceId = insertCompound("Parent with duplicate source")
        val parentWithoutConflictId = insertCompound("Parent without conflict")

        dao.insertComponents(
            listOf(
                compoundComponent(parentWithDestinationId, 0, sourceId),
                compoundComponent(parentWithDestinationId, 1, destinationId),
                compoundComponent(parentWithDuplicateSourceId, 0, sourceId),
                compoundComponent(parentWithDuplicateSourceId, 1, sourceId),
                compoundComponent(parentWithoutConflictId, 0, sourceId)
            )
        )

        assertEquals(2, dao.countCompoundMergeConflictingParents(sourceId, destinationId))
    }

    @Test
    fun find_and_replace_child_compound_references_only_touch_source_rows() = runBlocking {
        val sourceId = insertCompound("Source")
        val destinationId = insertCompound("Destination")
        val parentOneId = insertCompound("Parent one")
        val parentTwoId = insertCompound("Parent two")
        val untouchedParentId = insertCompound("Untouched parent")

        dao.insertComponents(
            listOf(
                compoundComponent(parentOneId, 0, sourceId),
                compoundComponent(parentOneId, 1, destinationId),
                compoundComponent(parentTwoId, 0, sourceId),
                compoundComponent(untouchedParentId, 0, destinationId)
            )
        )

        assertEquals(listOf(parentOneId, parentTwoId), dao.findParentCompoundIdsReferencingChild(sourceId).sorted())
        assertEquals(2, dao.replaceChildCompoundReferences(sourceId, destinationId))
        assertEquals(emptyList<Long>(), dao.findParentCompoundIdsReferencingChild(sourceId))
        assertEquals(4, dao.countChildCompoundReferences(destinationId))
    }

    private suspend fun insertCompound(name: String): Long {
        return dao.insertCompound(
            ReusableCompoundEntity(
                name = name,
                normalizedName = name.lowercase(),
                notes = null,
                compositionStatus = "complete",
                createdAtEpochMs = 1L,
                updatedAtEpochMs = 1L
            )
        )
    }

    private fun compoundComponent(compoundId: Long, position: Int, childCompoundId: Long): ReusableCompoundComponentEntity {
        return ReusableCompoundComponentEntity(
            compoundId = compoundId,
            position = position,
            componentKind = "compound",
            ingredientId = null,
            childCompoundId = childCompoundId,
            proportionBasisPoints = 5_000
        )
    }
}
