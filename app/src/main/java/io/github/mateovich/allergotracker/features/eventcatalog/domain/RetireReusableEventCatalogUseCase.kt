package io.github.mateovich.allergotracker.features.eventcatalog.domain

import io.github.mateovich.allergotracker.core.common.error.DomainError
import io.github.mateovich.allergotracker.core.common.result.AppResult
import kotlinx.coroutines.CancellationException

data class ReusableEventCatalogRetirementImpact(
    val entry: ReusableEventCatalog,
    val activeReferenceCount: Int
) {
    val isBlocked: Boolean = activeReferenceCount > 0
}

class GetReusableEventCatalogRetirementImpactUseCase(
    private val repository: ReusableEventCatalogRepository,
    private val countReferences: suspend (ReusableEventCatalogKind, Long) -> Int = { _, _ -> 0 }
) {
    suspend operator fun invoke(
        kind: ReusableEventCatalogKind,
        id: Long
    ): AppResult<ReusableEventCatalogRetirementImpact> {
        if (id <= 0L) {
            return AppResult.Error(DomainError.IntegrityError("Saved value is no longer available."))
        }

        return try {
            val entry = repository.getCatalogEntry(kind, id)
                ?: return AppResult.Error(DomainError.IntegrityError("Saved value is no longer available."))
            AppResult.Success(
                ReusableEventCatalogRetirementImpact(
                    entry = entry,
                    activeReferenceCount = countReferences(kind, id)
                )
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            AppResult.Error(DomainError.PersistenceError("Couldn't inspect saved value references locally. Try again."))
        }
    }
}

class RetireReusableEventCatalogUseCase(
    private val repository: ReusableEventCatalogRepository,
    private val getImpact: GetReusableEventCatalogRetirementImpactUseCase,
    private val runAtomically: suspend (suspend () -> AppResult<ReusableEventCatalogRetirementImpact>) -> AppResult<ReusableEventCatalogRetirementImpact> = { block -> block() }
) {
    suspend operator fun invoke(
        kind: ReusableEventCatalogKind,
        id: Long
    ): AppResult<ReusableEventCatalogRetirementImpact> {
        val impact = when (val result = getImpact(kind, id)) {
            is AppResult.Success -> result.value
            is AppResult.Error -> return result
        }

        if (impact.isBlocked) {
            return AppResult.Error(
                DomainError.IntegrityError(
                    "This saved value is still linked to ${impact.activeReferenceCount} records. Replace or clear those references before retiring it."
                )
            )
        }

        return try {
            runAtomically {
                when (val deleteResult = repository.deleteCatalogEntry(kind, id)) {
                    is AppResult.Success -> AppResult.Success(impact)
                    is AppResult.Error -> throw ReusableEventCatalogRetirementRollback(deleteResult.error)
                }
            }
        } catch (rollback: ReusableEventCatalogRetirementRollback) {
            AppResult.Error(rollback.error)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            AppResult.Error(DomainError.PersistenceError("Couldn't retire saved value locally. Try again."))
        }
    }
}

private class ReusableEventCatalogRetirementRollback(
    val error: DomainError
) : RuntimeException()
