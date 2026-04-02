package com.example.app.features.ingredients.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ReusableIngredientDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: ReusableIngredientEntity): Long

    @Query("SELECT * FROM reusable_ingredients WHERE normalized_name = :normalizedName LIMIT 1")
    suspend fun findByNormalizedName(normalizedName: String): ReusableIngredientEntity?

    @Query("SELECT * FROM reusable_ingredients WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): ReusableIngredientEntity?

    @Update
    suspend fun update(entity: ReusableIngredientEntity): Int

    @Query("DELETE FROM reusable_ingredients WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("SELECT EXISTS(SELECT 1 FROM reusable_ingredients WHERE id = :id)")
    suspend fun existsById(id: Long): Boolean

    @Query("UPDATE reusable_ingredients SET updated_at_epoch_ms = :updatedAtEpochMs WHERE id = :id")
    suspend fun touchById(id: Long, updatedAtEpochMs: Long): Int

    @Query("SELECT * FROM reusable_ingredients ORDER BY name COLLATE NOCASE ASC, id ASC")
    fun observeAll(): Flow<List<ReusableIngredientEntity>>
}
