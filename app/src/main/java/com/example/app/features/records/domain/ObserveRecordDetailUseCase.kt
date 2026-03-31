package com.example.app.features.records.domain

import com.example.app.core.common.result.AppResult
import kotlinx.coroutines.flow.Flow

class ObserveRecordDetailUseCase(
    private val repository: RecordsRepository
) {
    operator fun invoke(recordId: Long, eventType: RecordEventType): Flow<AppResult<RecordDetail>> {
        return repository.observeRecordDetail(recordId, eventType)
    }
}
