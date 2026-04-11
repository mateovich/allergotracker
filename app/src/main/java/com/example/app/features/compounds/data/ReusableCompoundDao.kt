package com.example.app.features.compounds.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ReusableCompoundDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCompound(entity: ReusableCompoundEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertComponents(entities: List<ReusableCompoundComponentEntity>)

    @Query("SELECT * FROM reusable_compounds WHERE normalized_name = :normalizedName LIMIT 1")
    suspend fun findByNormalizedName(normalizedName: String): ReusableCompoundEntity?

    @Query("SELECT * FROM reusable_compounds WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): ReusableCompoundEntity?

    @Transaction
    @Query("SELECT * FROM reusable_compounds WHERE id = :id LIMIT 1")
    suspend fun findCompoundWithComponentsById(id: Long): ReusableCompoundWithComponents?

    @Transaction
    @Query("SELECT * FROM reusable_compounds WHERE id IN (:ids)")
    suspend fun findCompoundsWithComponentsByIds(ids: List<Long>): List<ReusableCompoundWithComponents>

    @Update
    suspend fun updateCompound(entity: ReusableCompoundEntity): Int

    @Query("DELETE FROM reusable_compound_components WHERE compound_id = :compoundId")
    suspend fun deleteComponentsByCompoundId(compoundId: Long): Int

    @Query("DELETE FROM reusable_compounds WHERE id = :id")
    suspend fun deleteCompoundById(id: Long): Int

    @Query("SELECT EXISTS(SELECT 1 FROM reusable_compounds WHERE id = :id)")
    suspend fun existsById(id: Long): Boolean

    @Query(
        "SELECT COUNT(*) FROM reusable_compound_components WHERE component_kind = 'ingredient' AND ingredient_id = :ingredientId"
    )
    suspend fun countIngredientComponentReferences(ingredientId: Long): Int

    @Query(
        "SELECT DISTINCT compound_id FROM reusable_compound_components WHERE component_kind = 'ingredient' AND ingredient_id = :ingredientId"
    )
    suspend fun findCompoundIdsReferencingIngredient(ingredientId: Long): List<Long>

    @Query(
        """
        SELECT COUNT(*)
        FROM (
            SELECT compound_id
            FROM reusable_compound_components
            WHERE component_kind = 'ingredient'
                AND ingredient_id IN (:sourceIngredientId, :destinationIngredientId)
            GROUP BY compound_id
            HAVING SUM(CASE WHEN ingredient_id = :sourceIngredientId THEN 1 ELSE 0 END) > 0
                AND COUNT(*) > 1
        )
        """
    )
    suspend fun countIngredientMergeConflictingCompounds(sourceIngredientId: Long, destinationIngredientId: Long): Int

    @Query(
        "UPDATE reusable_compound_components SET ingredient_id = :destinationIngredientId WHERE ingredient_id = :sourceIngredientId AND component_kind = 'ingredient'"
    )
    suspend fun replaceIngredientComponentReferences(sourceIngredientId: Long, destinationIngredientId: Long): Int

    @Query(
        "SELECT COUNT(*) FROM reusable_compound_components WHERE component_kind = 'compound' AND child_compound_id = :compoundId"
    )
    suspend fun countChildCompoundReferences(compoundId: Long): Int

    @Query(
        """
        SELECT COUNT(*)
        FROM (
            SELECT compound_id
            FROM reusable_compound_components
            WHERE component_kind = 'compound'
                AND child_compound_id IN (:sourceCompoundId, :destinationCompoundId)
            GROUP BY compound_id
            HAVING SUM(CASE WHEN child_compound_id = :sourceCompoundId THEN 1 ELSE 0 END) > 0
                AND COUNT(*) > 1
        )
        """
    )
    suspend fun countCompoundMergeConflictingParents(sourceCompoundId: Long, destinationCompoundId: Long): Int

    @Query(
        "SELECT DISTINCT compound_id FROM reusable_compound_components WHERE component_kind = 'compound' AND child_compound_id = :sourceCompoundId"
    )
    suspend fun findParentCompoundIdsReferencingChild(sourceCompoundId: Long): List<Long>

    @Query(
        "UPDATE reusable_compound_components SET child_compound_id = :destinationCompoundId WHERE component_kind = 'compound' AND child_compound_id = :sourceCompoundId"
    )
    suspend fun replaceChildCompoundReferences(sourceCompoundId: Long, destinationCompoundId: Long): Int

    @Query(
        """
        SELECT compound_id AS parentCompoundId, child_compound_id AS childCompoundId
        FROM reusable_compound_components
        WHERE child_compound_id IS NOT NULL
        """
    )
    suspend fun findAllChildCompoundEdges(): List<ReusableCompoundChildEdge>

    @Query("UPDATE reusable_compounds SET updated_at_epoch_ms = :updatedAtEpochMs WHERE id = :id")
    suspend fun touchById(id: Long, updatedAtEpochMs: Long): Int

    @Transaction
    @Query("SELECT * FROM reusable_compounds ORDER BY name COLLATE NOCASE ASC, id ASC")
    fun observeAll(): Flow<List<ReusableCompoundWithComponents>>

    @Transaction
    suspend fun insertCompoundWithComponents(
        entity: ReusableCompoundEntity,
        components: List<ReusableCompoundComponentEntity>
    ): Long {
        val compoundId = insertCompound(entity)
        if (components.isNotEmpty()) {
            insertComponents(components.map { it.copy(compoundId = compoundId) })
        }
        return compoundId
    }

    @Transaction
    suspend fun replaceCompoundComponents(
        compoundId: Long,
        components: List<ReusableCompoundComponentEntity>
    ) {
        deleteComponentsByCompoundId(compoundId)
        if (components.isNotEmpty()) {
            insertComponents(components.map { it.copy(compoundId = compoundId) })
        }
    }
}

data class ReusableCompoundChildEdge(
    val parentCompoundId: Long,
    val childCompoundId: Long
)
