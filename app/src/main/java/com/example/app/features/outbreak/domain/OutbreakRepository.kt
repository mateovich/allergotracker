package com.example.app.features.outbreak.domain

import com.example.app.core.common.result.AppResult

interface OutbreakRepository {
    suspend fun createOutbreak(command: CreateOutbreakEventCommand): AppResult<OutbreakEvent>
    suspend fun getOutbreak(id: Long): AppResult<OutbreakEvent>
    suspend fun updateOutbreak(command: UpdateOutbreakEventCommand): AppResult<OutbreakEvent>
}
