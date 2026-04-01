package com.example.app.features.eventcatalog.domain

import com.example.app.core.common.error.DomainError
import com.example.app.core.common.result.AppResult
import kotlinx.coroutines.CancellationException

class GetReusableEventCatalogEntryUseCase(
    private val repository: ReusableEventCatalogRepository
) {
    suspend operator fun invoke(
        kind: ReusableEventCatalogKind,
        id: Long
    ): AppResult<ReusableEventCatalog> {
        return try {
            repository.getCatalogEntry(kind, id)?.let { AppResult.Success(it) }
                ?: AppResult.Error(DomainError.IntegrityError("Saved value is no longer available."))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            AppResult.Error(DomainError.PersistenceError("Couldn't load saved value locally. Try again."))
        }
    }
}
