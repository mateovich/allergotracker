package com.example.app.features.compounds.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.app.features.ingredients.data.ReusableIngredientEntity

@Entity(
    tableName = "reusable_compound_components",
    foreignKeys = [
        ForeignKey(
            entity = ReusableCompoundEntity::class,
            parentColumns = ["id"],
            childColumns = ["compound_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ReusableIngredientEntity::class,
            parentColumns = ["id"],
            childColumns = ["ingredient_id"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = ReusableCompoundEntity::class,
            parentColumns = ["id"],
            childColumns = ["child_compound_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["compound_id"]),
        Index(value = ["ingredient_id"]),
        Index(value = ["child_compound_id"]),
        Index(value = ["compound_id", "position"], unique = true)
    ]
)
data class ReusableCompoundComponentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "compound_id")
    val compoundId: Long,
    @ColumnInfo(name = "position")
    val position: Int,
    @ColumnInfo(name = "component_kind")
    val componentKind: String,
    @ColumnInfo(name = "ingredient_id")
    val ingredientId: Long?,
    @ColumnInfo(name = "child_compound_id")
    val childCompoundId: Long?,
    @ColumnInfo(name = "proportion_basis_points")
    val proportionBasisPoints: Int?
)
