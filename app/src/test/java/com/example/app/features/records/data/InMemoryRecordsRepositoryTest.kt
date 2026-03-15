package com.example.app.features.records.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class InMemoryRecordsRepositoryTest {

    @Test
    fun starts_with_local_empty_record_set() = runBlocking {
        val repository = InMemoryRecordsRepository()

        assertEquals(0, repository.observeRecordCount().first())
    }
}
