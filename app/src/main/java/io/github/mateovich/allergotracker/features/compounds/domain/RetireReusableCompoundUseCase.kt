package io.github.mateovich.allergotracker.features.compounds.domain

import io.github.mateovich.allergotracker.core.common.error.DomainError
import io.github.mateovich.allergotracker.core.common.result.AppResult
import kotlinx.coroutines.CancellationException

data class ReusableCompoundRetirementImpact(
    val compound: ReusableCompound,
    val ingestionReferenceCount: Int,
    val childCompoundReferenceCount: Int
) {
    val isBlocked: Boolean = childCompoundReferenceCount > 0
    val requiresDetachment: Boolean = !isBlocked && ingestionReferenceCount > 0
}

class GetReusableCompoundRetirementImpactUseCase(
    private val repository: ReusableCompoundRepository,
    private val countIngestionReferences: suspend (Long) -> Int = { 0 },
    private val countChildCompoundReferences: suspend (Long) -> Int = { 0 }
) {
    suspend operator fun invoke(id: Long): AppResult<ReusableCompoundRetirementImpact> {
        if (id <= 0L) {
            return AppResult.Error(DomainError.IntegrityError("Saved compound is no longer available."))
        }

        return try {
            when (val compound = repository.getCompound(id)) {
                is AppResult.Success -> AppResult.Success(
                    ReusableCompoundRetirementImpact(
                        compound = compound.value,
                        ingestionReferenceCount = countIngestionReferences(id),
                        childCompoundReferenceCount = countChildCompoundReferences(id)
                    )
                )

                is AppResult.Error -> compound
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            AppResult.Error(DomainError.PersistenceError("Couldn't inspect compound references locally. Try again."))
        }
    }
}

class RetireReusableCompoundUseCase(
    private val repository: ReusableCompoundRepository,
    private val getImpact: GetReusableCompoundRetirementImpactUseCase,
    private val detachIngestionReferences: suspend (Long) -> Int = { 0 },
    private val runAtomically: suspend (suspend () -> AppResult<ReusableCompoundRetirementImpact>) -> AppResult<ReusableCompoundRetirementImpact> = { block -> block() }
) {
    suspend operator fun invoke(id: Long): AppResult<ReusableCompoundRetirementImpact> {
        val impact = when (val result = getImpact(id)) {
            is AppResult.Success -> result.value
            is AppResult.Error -> return result
        }

        if (impact.isBlocked) {
            return AppResult.Error(
                DomainError.IntegrityError(
                    "This compound is still nested inside ${impact.childCompoundReferenceCount} other compounds. Replace those references before retiring it."
                )
            )
        }

        return try {
            runAtomically {
                if (impact.ingestionReferenceCount > 0) {
                    val detached = detachIngestionReferences(id)
                    if (detached != impact.ingestionReferenceCount) {
                        throw ReusableCompoundRetirementRollback(
                            DomainError.IntegrityError("Couldn't safely detach linked ingestion records. Try again.")
                        )
                    }
                }
                when (val deleteResult = repository.deleteCompound(id)) {
                    is AppResult.Success -> AppResult.Success(impact)
                    is AppResult.Error -> throw ReusableCompoundRetirementRollback(deleteResult.error)
                }
            }
        } catch (rollback: ReusableCompoundRetirementRollback) {
            AppResult.Error(rollback.error)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            AppResult.Error(DomainError.PersistenceError("Couldn't retire compound locally. Try again."))
        }
    }
}

private class ReusableCompoundRetirementRollback(
    val error: DomainError
) : RuntimeException()
