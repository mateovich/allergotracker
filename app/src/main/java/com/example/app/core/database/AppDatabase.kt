package com.example.app.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.app.features.compounds.data.ReusableCompoundComponentEntity
import com.example.app.features.compounds.data.ReusableCompoundDao
import com.example.app.features.compounds.data.ReusableCompoundEntity
import com.example.app.features.exposure.data.ExposureEventDao
import com.example.app.features.exposure.data.ExposureEventEntity
import com.example.app.features.eventcatalog.data.ReusableEventCatalogDao
import com.example.app.features.eventcatalog.data.ReusableEventCatalogEntity
import com.example.app.features.ingredients.data.ReusableIngredientDao
import com.example.app.features.ingredients.data.ReusableIngredientEntity
import com.example.app.features.ingestion.data.IngestionEventDao
import com.example.app.features.ingestion.data.IngestionEventEntity
import com.example.app.features.outbreak.data.OutbreakEventDao
import com.example.app.features.outbreak.data.OutbreakEventEntity

@Database(
    entities = [
        HomeMessageEntity::class,
        IngestionEventEntity::class,
        ExposureEventEntity::class,
        OutbreakEventEntity::class,
        ReusableEventCatalogEntity::class,
        ReusableIngredientEntity::class,
        ReusableCompoundEntity::class,
        ReusableCompoundComponentEntity::class
    ],
    version = 13,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun homeDao(): HomeDao
    abstract fun ingestionEventDao(): IngestionEventDao
    abstract fun exposureEventDao(): ExposureEventDao
    abstract fun outbreakEventDao(): OutbreakEventDao
    abstract fun reusableEventCatalogDao(): ReusableEventCatalogDao
    abstract fun reusableIngredientDao(): ReusableIngredientDao
    abstract fun reusableCompoundDao(): ReusableCompoundDao
}
