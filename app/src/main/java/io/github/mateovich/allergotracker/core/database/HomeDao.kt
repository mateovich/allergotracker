package io.github.mateovich.allergotracker.core.database

import androidx.room.Dao
import androidx.room.Query

@Dao
interface HomeDao {
    @Query("SELECT COALESCE((SELECT message_text FROM home_messages WHERE id = 1 LIMIT 1), 'Architecture Skeleton Ready')")
    fun getWelcomeMessage(): String
}
