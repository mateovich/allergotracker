package io.github.mateovich.allergotracker.features.outbreak.domain

import io.github.mateovich.allergotracker.core.common.result.AppResult

interface OutbreakRepository {
    suspend fun createOutbreak(command: CreateOutbreakEventCommand): AppResult<OutbreakEvent>
    suspend fun getOutbreak(id: Long): AppResult<OutbreakEvent>
    suspend fun updateOutbreak(command: UpdateOutbreakEventCommand): AppResult<OutbreakEvent>
    suspend fun deleteOutbreak(id: Long): AppResult<Unit>
}
