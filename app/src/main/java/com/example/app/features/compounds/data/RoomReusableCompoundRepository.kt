package com.example.app.features.compounds.data

import android.database.sqlite.SQLiteConstraintException
import com.example.app.core.common.error.DomainError
import com.example.app.core.common.result.AppResult
import com.example.app.features.compounds.domain.CompoundComponentKind
import com.example.app.features.compounds.domain.CompoundCompositionStatus
import com.example.app.features.compounds.domain.CreateReusableCompoundCommand
import com.example.app.features.compounds.domain.ReusableCompound
import com.example.app.features.compounds.domain.ReusableCompoundComponent
import com.example.app.features.compounds.domain.CompoundChildEdge
import com.example.app.features.compounds.domain.CompoundCompositionIntegrity
import com.example.app.features.compounds.domain.ReusableCompoundRepository
import com.example.app.features.compounds.domain.UpdateReusableCompoundCommand
import com.example.app.features.compounds.domain.normalizeReusableName
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomReusableCompoundRepository(
    private val compoundDao: ReusableCompoundDao,
    private val ingredientExists: suspend (Long) -> Boolean,
    private val compoundExists: suspend (Long) -> Boolean,
    private val nowEpochMillis: () -> Long
) : ReusableCompoundRepository {
    override suspend fun createCompound(command: CreateReusableCompoundCommand): AppResult<ReusableCompound> {
        return try {
            val canonicalName = command.name.normalizeReusableName()
            val normalizedName = canonicalName.normalizedKey()
            compoundDao.findByNormalizedName(normalizedName)?.let {
                return AppResult.Error(
                    DomainError.ValidationError(
                        message = "Please fix the highlighted fields.",
                        fieldErrors = mapOf("name" to "A compound with this name already exists.")
                    )
                )
            }
            val validationError = validateComponents(command)
            if (validationError != null) {
                return AppResult.Error(validationError)
            }
            val now = nowEpochMillis()
            val entity = ReusableCompoundEntity(
                name = canonicalName,
                normalizedName = normalizedName,
                notes = command.notes,
                compositionStatus = CompoundCompositionStatus.COMPLETE.storageValue,
                createdAtEpochMs = now,
                updatedAtEpochMs = now
            )
            val components = command.components.mapIndexed { index, component ->
                ReusableCompoundComponentEntity(
                    compoundId = 0L,
                    position = index,
                    componentKind = component.kind.storageValue,
                    ingredientId = component.reusableEntityId.takeIf { component.kind == CompoundComponentKind.INGREDIENT },
                    childCompoundId = component.reusableEntityId.takeIf { component.kind == CompoundComponentKind.COMPOUND },
                    proportionBasisPoints = component.proportionBasisPoints
                )
            }
            val id = compoundDao.insertCompoundWithComponents(entity, components)
            val savedCompound = compoundDao.findCompoundWithComponentsById(id)
                ?: return AppResult.Error(DomainError.IntegrityError("Saved compound is no longer available."))
            AppResult.Success(savedCompound.toDomain())
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            AppResult.Error(DomainError.PersistenceError("Couldn't save compound locally. Try again."))
        }
    }

    override suspend fun getCompound(id: Long): AppResult<ReusableCompound> {
        return try {
            val compound = compoundDao.findCompoundWithComponentsById(id)
                ?: return AppResult.Error(DomainError.IntegrityError("Saved compound is no longer available."))
            AppResult.Success(compound.toDomain())
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            AppResult.Error(DomainError.PersistenceError("Couldn't load compound locally. Try again."))
        }
    }

    override suspend fun updateCompound(command: UpdateReusableCompoundCommand): AppResult<ReusableCompound> {
        return try {
            val current = compoundDao.findById(command.id)
                ?: return AppResult.Error(DomainError.IntegrityError("Saved compound is no longer available."))
            val canonicalName = command.name.normalizeReusableName()
            val normalizedName = canonicalName.normalizedKey()
            compoundDao.findByNormalizedName(normalizedName)?.let { existing ->
                if (existing.id != command.id) {
                    return AppResult.Error(
                        DomainError.ValidationError(
                            message = "Please fix the highlighted fields.",
                            fieldErrors = mapOf("name" to "A compound with this name already exists.")
                        )
                    )
                }
            }
            val validationError = validateComponents(
                components = command.components.map { component ->
                    ComponentValidationInput(
                        kind = component.kind,
                        reusableEntityId = component.reusableEntityId,
                        proportionBasisPoints = component.proportionBasisPoints
                    )
                },
                compoundId = command.id
            )
            if (validationError != null) {
                return AppResult.Error(validationError)
            }
            val updated = current.copy(
                name = canonicalName,
                normalizedName = normalizedName,
                notes = command.notes,
                compositionStatus = CompoundCompositionStatus.COMPLETE.storageValue,
                updatedAtEpochMs = nowEpochMillis()
            )
            if (compoundDao.updateCompound(updated) == 0) {
                return AppResult.Error(DomainError.IntegrityError("Saved compound is no longer available."))
            }
            compoundDao.replaceCompoundComponents(
                compoundId = command.id,
                components = command.components.mapIndexed { index, component ->
                    ReusableCompoundComponentEntity(
                        compoundId = command.id,
                        position = index,
                        componentKind = component.kind.storageValue,
                        ingredientId = component.reusableEntityId.takeIf { component.kind == CompoundComponentKind.INGREDIENT },
                        childCompoundId = component.reusableEntityId.takeIf { component.kind == CompoundComponentKind.COMPOUND },
                        proportionBasisPoints = component.proportionBasisPoints
                    )
                }
            )
            val savedCompound = compoundDao.findCompoundWithComponentsById(command.id)
                ?: return AppResult.Error(DomainError.IntegrityError("Saved compound is no longer available."))
            AppResult.Success(savedCompound.toDomain())
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            AppResult.Error(DomainError.PersistenceError("Couldn't update compound locally. Try again."))
        }
    }

    override suspend fun deleteCompound(id: Long): AppResult<Unit> {
        return try {
            if (compoundDao.deleteCompoundById(id) == 0) {
                AppResult.Error(DomainError.IntegrityError("Saved compound is no longer available."))
            } else {
                AppResult.Success(Unit)
            }
        } catch (_: SQLiteConstraintException) {
            AppResult.Error(
                DomainError.IntegrityError(
                    "This compound is still linked from other records. Replace those references before retiring it."
                )
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            AppResult.Error(DomainError.PersistenceError("Couldn't retire compound locally. Try again."))
        }
    }

    override fun observeCompounds(): Flow<List<ReusableCompound>> {
        return compoundDao.observeAll().map { rows -> rows.map(ReusableCompoundWithComponents::toDomain) }
    }

    private suspend fun validateComponents(command: CreateReusableCompoundCommand): DomainError? {
        return validateComponents(
            components = command.components.map { component ->
                ComponentValidationInput(
                    kind = component.kind,
                    reusableEntityId = component.reusableEntityId,
                    proportionBasisPoints = component.proportionBasisPoints
                )
            }
        )
    }

    private suspend fun validateComponents(
        components: List<ComponentValidationInput>,
        compoundId: Long? = null
    ): DomainError? {
        val fieldErrors = linkedMapOf<String, String>()
        val duplicateIndexes = CompoundCompositionIntegrity.findDuplicateComponentIndexes(
            components.map { it.kind to it.reusableEntityId }
        )
        val cycleChildCompoundIds = CompoundCompositionIntegrity.invalidChildCompoundIds(
            parentCompoundId = compoundId,
            candidateIds = components.mapNotNull { component ->
                component.reusableEntityId?.takeIf { component.kind == CompoundComponentKind.COMPOUND }
            },
            childEdges = compoundDao.findAllChildCompoundEdges().map { edge ->
                CompoundChildEdge(
                    parentCompoundId = edge.parentCompoundId,
                    childCompoundId = edge.childCompoundId
                )
            }
        )
        if (components.isEmpty()) {
            fieldErrors["components"] = "Add at least one component."
        }
        components.forEachIndexed { index, component ->
            val reusableId = component.reusableEntityId
            val targetKey = "components[$index].target"
            val proportionKey = "components[$index].proportion"
            when {
                reusableId == null || reusableId <= 0L -> fieldErrors[targetKey] = "Choose a saved ingredient or compound."
                index in duplicateIndexes && component.kind == CompoundComponentKind.INGREDIENT -> {
                    fieldErrors[targetKey] = "This ingredient is already in the composition. Edit the existing row instead."
                }
                index in duplicateIndexes && component.kind == CompoundComponentKind.COMPOUND -> {
                    fieldErrors[targetKey] = "This compound is already in the composition. Edit the existing row instead."
                }
                component.kind == CompoundComponentKind.INGREDIENT && !ingredientExists(reusableId) -> {
                    fieldErrors[targetKey] = "Selected ingredient is no longer available."
                }
                component.kind == CompoundComponentKind.COMPOUND && reusableId == compoundId -> {
                    fieldErrors[targetKey] = "A compound cannot include itself."
                }
                component.kind == CompoundComponentKind.COMPOUND && reusableId in cycleChildCompoundIds -> {
                    fieldErrors[targetKey] = "This compound would create a recursive composition."
                }
                component.kind == CompoundComponentKind.COMPOUND && !compoundExists(reusableId) -> {
                    fieldErrors[targetKey] = "Selected compound is no longer available."
                }
            }
            if (component.proportionBasisPoints == null || component.proportionBasisPoints !in 1..10_000) {
                fieldErrors[proportionKey] = "Enter a proportion greater than 0%."
            }
        }
        val totalProportion = components.sumOf { it.proportionBasisPoints ?: 0 }
        if (components.isNotEmpty() && totalProportion != 10_000) {
            fieldErrors["componentsTotal"] = "Total proportion must equal 100%."
        }
        if (fieldErrors.isNotEmpty()) {
            return DomainError.ValidationError(
                message = "Please fix the highlighted fields.",
                fieldErrors = fieldErrors
            )
        }
        return null
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

private fun String.normalizedKey(): String = normalizeReusableName().lowercase()

private data class ComponentValidationInput(
    val kind: CompoundComponentKind,
    val reusableEntityId: Long?,
    val proportionBasisPoints: Int?
)
