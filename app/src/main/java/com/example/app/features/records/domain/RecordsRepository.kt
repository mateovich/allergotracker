package com.example.app.features.records.domain

import com.example.app.core.common.error.DomainError
import com.example.app.core.common.result.AppResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

interface RecordsRepository {
    fun observeRecordCount(): Flow<Int>
    fun observeRecords(filters: Flow<RecordsFilter> = flowOf(RecordsFilter())): Flow<List<RecordSummary>>
    fun observeRecordDetail(recordId: Long, eventType: RecordEventType): Flow<AppResult<RecordDetail>> {
        return flowOf(AppResult.Error(DomainError.PersistenceError("This record is no longer available.")))
    }
}
