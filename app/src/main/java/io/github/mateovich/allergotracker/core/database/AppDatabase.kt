package io.github.mateovich.allergotracker.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import io.github.mateovich.allergotracker.features.compounds.data.ReusableCompoundComponentEntity
import io.github.mateovich.allergotracker.features.compounds.data.ReusableCompoundDao
import io.github.mateovich.allergotracker.features.compounds.data.ReusableCompoundEntity
import io.github.mateovich.allergotracker.features.exposure.data.ExposureEventDao
import io.github.mateovich.allergotracker.features.exposure.data.ExposureEventEntity
import io.github.mateovich.allergotracker.features.eventcatalog.data.ReusableEventCatalogDao
import io.github.mateovich.allergotracker.features.eventcatalog.data.ReusableEventCatalogEntity
import io.github.mateovich.allergotracker.features.ingredients.data.ReusableIngredientDao
import io.github.mateovich.allergotracker.features.ingredients.data.ReusableIngredientEntity
import io.github.mateovich.allergotracker.features.ingestion.data.IngestionEventDao
import io.github.mateovich.allergotracker.features.ingestion.data.IngestionEventEntity
import io.github.mateovich.allergotracker.features.outbreak.data.OutbreakEventDao
import io.github.mateovich.allergotracker.features.outbreak.data.OutbreakEventEntity

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
