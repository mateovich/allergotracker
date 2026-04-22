package io.github.mateovich.allergotracker.features.records.domain

import io.github.mateovich.allergotracker.core.common.result.AppResult
import kotlinx.coroutines.flow.Flow

class ObserveRecordDetailUseCase(
    private val repository: RecordsRepository
) {
    operator fun invoke(recordId: Long, eventType: RecordEventType): Flow<AppResult<RecordDetail>> {
        return repository.observeRecordDetail(recordId, eventType)
    }
}
