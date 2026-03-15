package com.example.app.features.records.data

import com.example.app.features.records.domain.RecordsRepository
import com.example.app.features.records.domain.RecordSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class InMemoryRecordsRepository : RecordsRepository {
    private val recordCount = MutableStateFlow(0)
    private val records = MutableStateFlow(emptyList<RecordSummary>())

    override fun observeRecordCount(): Flow<Int> = recordCount.asStateFlow()

    override fun observeRecords(): Flow<List<RecordSummary>> = records.asStateFlow()
}
