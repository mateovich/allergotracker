package com.example.app.features.compounds.data

import com.example.app.core.common.error.DomainError
import com.example.app.core.common.result.AppResult
import com.example.app.features.compounds.domain.CompoundComponentKind
import com.example.app.features.compounds.domain.CompoundCompositionStatus
import com.example.app.features.compounds.domain.CreateReusableCompoundCommand
import com.example.app.features.compounds.domain.ReusableCompound
import com.example.app.features.compounds.domain.ReusableCompoundComponent
import com.example.app.features.compounds.domain.ReusableCompoundRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomReusableCompoundRepository(
    private val compoundDao: ReusableCompoundDao,
    private val nowEpochMillis: () -> Long
) : ReusableCompoundRepository {
    override suspend fun createCompound(command: CreateReusableCompoundCommand): AppResult<ReusableCompound> {
        return try {
            val normalizedName = command.name.normalizedKey()
            compoundDao.findByNormalizedName(normalizedName)?.let { existing ->
                return AppResult.Success(
                    ReusableCompound(
                        id = existing.id,
                        name = existing.name,
                        notes = existing.notes,
                        compositionStatus = CompoundCompositionStatus.fromStorageValue(existing.compositionStatus),
                        components = emptyList(),
                        createdAtEpochMs = existing.createdAtEpochMs,
                        updatedAtEpochMs = existing.updatedAtEpochMs
                    )
                )
            }
            val now = nowEpochMillis()
            val entity = ReusableCompoundEntity(
                name = command.name,
                normalizedName = normalizedName,
                notes = command.notes,
                compositionStatus = CompoundCompositionStatus.DRAFT.storageValue,
                createdAtEpochMs = now,
                updatedAtEpochMs = now
            )
            val id = compoundDao.insertCompound(entity)
            AppResult.Success(
                ReusableCompound(
                    id = id,
                    name = entity.name,
                    notes = entity.notes,
                    compositionStatus = CompoundCompositionStatus.DRAFT,
                    components = emptyList(),
                    createdAtEpochMs = entity.createdAtEpochMs,
                    updatedAtEpochMs = entity.updatedAtEpochMs
                )
            )
        } catch (_: Exception) {
            AppResult.Error(DomainError.PersistenceError("Couldn't save compound locally. Try again."))
        }
    }

    override fun observeCompounds(): Flow<List<ReusableCompound>> {
        return compoundDao.observeAll().map { rows -> rows.map(ReusableCompoundWithComponents::toDomain) }
    }
}

private fun ReusableCompoundWithComponents.toDomain(): ReusableCompound {
    return ReusableCompound(
        id = compound.id,
        name = compound.name,
        notes = compound.notes,
        compositionStatus = CompoundCompositionStatus.fromStorageValue(compound.compositionStatus),
        components = components.sortedBy { it.position }.map { component ->
            ReusableCompoundComponent(
                id = component.id,
                position = component.position,
                kind = CompoundComponentKind.fromStorageValue(component.componentKind),
                ingredientId = component.ingredientId,
                childCompoundId = component.childCompoundId,
                proportionBasisPoints = component.proportionBasisPoints
            )
        },
        createdAtEpochMs = compound.createdAtEpochMs,
        updatedAtEpochMs = compound.updatedAtEpochMs
    )
}

private fun String.normalizedKey(): String = trim().lowercase()
