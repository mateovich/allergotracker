package com.example.app.features.eventcatalog.domain

import com.example.app.core.common.error.DomainError
import com.example.app.core.common.result.AppResult
import kotlinx.coroutines.CancellationException

class UpdateReusableEventCatalogUseCase(
    private val repository: ReusableEventCatalogRepository,
    private val syncReferences: suspend (ReusableEventCatalogKind, Long, String) -> Int = { _, _, _ -> 0 },
    private val countReferences: suspend (ReusableEventCatalogKind, Long) -> Int = { _, _ -> 0 },
    private val runAtomically: suspend (suspend () -> AppResult<ReusableEventCatalog>) -> AppResult<ReusableEventCatalog> = { block -> block() }
) {
    suspend operator fun invoke(
        kind: ReusableEventCatalogKind,
        id: Long,
        name: String
    ): AppResult<ReusableEventCatalog> {
        val normalizedName = canonicalizeReusableEventCatalogName(name)
        if (id <= 0L) {
            return AppResult.Error(DomainError.IntegrityError("Saved value is no longer available."))
        }
        if (normalizedName.isBlank()) {
            return AppResult.Error(
                DomainError.ValidationError(
                    message = "Please fix the highlighted fields.",
                    fieldErrors = mapOf("name" to fieldError(kind))
                )
            )
        }

        return try {
            runAtomically {
                when (val result = repository.updateCatalogEntry(kind, id, normalizedName)) {
                    is AppResult.Success -> {
                        val expectedReferences = countReferences(kind, result.value.id)
                        val syncedReferences = syncReferences(kind, result.value.id, result.value.name)
                        if (syncedReferences != expectedReferences) {
                            throw ReusableEventCatalogUpdateRollback(
                                DomainError.IntegrityError("Couldn't keep linked saved values in sync. Try again.")
                            )
                        } else {
                            result
                        }
                    }

                    is AppResult.Error -> result
                }
            }
        } catch (rollback: ReusableEventCatalogUpdateRollback) {
            AppResult.Error(rollback.error)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            AppResult.Error(DomainError.PersistenceError("Couldn't update saved value locally. Try again."))
        }
    }

    private fun fieldError(kind: ReusableEventCatalogKind): String {
        return when (kind) {
            ReusableEventCatalogKind.EXPOSURE_TYPE -> "Enter an exposure type."
            ReusableEventCatalogKind.OUTBREAK_DESCRIPTOR -> "Enter what happened."
            ReusableEventCatalogKind.BODY_LOCATION -> "Enter where on the body it happened."
        }
    }
}

private class ReusableEventCatalogUpdateRollback(
    val error: DomainError
) : RuntimeException()
