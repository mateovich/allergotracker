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

    @Query("SELECT * FROM reusable_compounds WHERE normalized_name = :normalizedName LIMIT 1")
    suspend fun findByNormalizedName(normalizedName: String): ReusableCompoundEntity?

    @Query("SELECT * FROM reusable_compounds WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): ReusableCompoundEntity?

    @Transaction
    @Query("SELECT * FROM reusable_compounds WHERE id = :id LIMIT 1")
    suspend fun findCompoundWithComponentsById(id: Long): ReusableCompoundWithComponents?

    @Update
    suspend fun updateCompound(entity: ReusableCompoundEntity): Int

    @Query("DELETE FROM reusable_compounds WHERE id = :id")
    suspend fun deleteCompoundById(id: Long): Int

    @Query("SELECT EXISTS(SELECT 1 FROM reusable_compounds WHERE id = :id)")
    suspend fun existsById(id: Long): Boolean

    @Query("SELECT COUNT(*) FROM reusable_compound_components WHERE ingredient_id = :ingredientId")
    suspend fun countIngredientComponentReferences(ingredientId: Long): Int

    @Query("SELECT COUNT(*) FROM reusable_compound_components WHERE child_compound_id = :compoundId")
    suspend fun countChildCompoundReferences(compoundId: Long): Int

    @Query("UPDATE reusable_compounds SET updated_at_epoch_ms = :updatedAtEpochMs WHERE id = :id")
    suspend fun touchById(id: Long, updatedAtEpochMs: Long): Int

    @Transaction
    @Query("SELECT * FROM reusable_compounds ORDER BY name COLLATE NOCASE ASC, id ASC")
    fun observeAll(): Flow<List<ReusableCompoundWithComponents>>
}
