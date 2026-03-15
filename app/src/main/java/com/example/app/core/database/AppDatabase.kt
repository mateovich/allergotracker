package com.example.app.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.app.features.exposure.data.ExposureEventDao
import com.example.app.features.exposure.data.ExposureEventEntity
import com.example.app.features.ingestion.data.IngestionEventDao
import com.example.app.features.ingestion.data.IngestionEventEntity
import com.example.app.features.outbreak.data.OutbreakEventDao
import com.example.app.features.outbreak.data.OutbreakEventEntity

@Database(
    entities = [HomeMessageEntity::class, IngestionEventEntity::class, ExposureEventEntity::class, OutbreakEventEntity::class],
    version = 5,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun homeDao(): HomeDao
    abstract fun ingestionEventDao(): IngestionEventDao
    abstract fun exposureEventDao(): ExposureEventDao
    abstract fun outbreakEventDao(): OutbreakEventDao
}
