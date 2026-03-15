package com.example.app.features.records.domain

import kotlinx.coroutines.flow.Flow

interface RecordsRepository {
    fun observeRecordCount(): Flow<Int>
    fun observeRecords(): Flow<List<RecordSummary>>
}
