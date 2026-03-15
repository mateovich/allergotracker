package com.example.app.features.home.data

import com.example.app.core.common.result.AppResult
import com.example.app.core.database.HomeDao
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeRepositoryImplTest {

    @Test
    fun load_welcome_message_sanitizes_persistence_error_message() {
        val repository = HomeRepositoryImpl(
            homeDao = object : HomeDao {
                override fun getWelcomeMessage(): String {
                    throw IllegalStateException("db path leaked")
                }
            }
        )

        val result = repository.loadWelcomeMessage()

        assertTrue(result is AppResult.Error)
        result as AppResult.Error
        assertEquals("Local persistence operation failed", result.error.message)
    }
}
