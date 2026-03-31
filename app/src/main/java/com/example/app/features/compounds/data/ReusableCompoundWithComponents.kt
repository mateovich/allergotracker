package com.example.app.features.compounds.data

import androidx.room.Embedded
import androidx.room.Relation

data class ReusableCompoundWithComponents(
    @Embedded
    val compound: ReusableCompoundEntity,
    @Relation(parentColumn = "id", entityColumn = "compound_id")
    val components: List<ReusableCompoundComponentEntity>
)
