package io.github.mateovich.allergotracker.features.eventcatalog.ui

import io.github.mateovich.allergotracker.MainDispatcherRule
import io.github.mateovich.allergotracker.core.common.error.DomainError
import io.github.mateovich.allergotracker.core.common.result.AppResult
import io.github.mateovich.allergotracker.features.eventcatalog.domain.GetReusableEventCatalogEntryUseCase
import io.github.mateovich.allergotracker.features.eventcatalog.domain.GetReusableEventCatalogRetirementImpactUseCase
import io.github.mateovich.allergotracker.features.eventcatalog.domain.ReusableEventCatalog
import io.github.mateovich.allergotracker.features.eventcatalog.domain.ReusableEventCatalogKind
import io.github.mateovich.allergotracker.features.eventcatalog.domain.ReusableEventCatalogRepository
import io.github.mateovich.allergotracker.features.eventcatalog.domain.RetireReusableEventCatalogUseCase
import io.github.mateovich.allergotracker.features.eventcatalog.domain.UpdateReusableEventCatalogUseCase
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class EditReusableEventCatalogViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun load_populates_existing_value_and_save_returns_updated_selection() = runBlocking {
        val repository = FakeEditReusableEventCatalogRepository()
        val viewModel = buildViewModel(repository)
        val effect = async { viewModel.effects.first() }

        viewModel.onIntent(EditReusableEventCatalogUiIntent.Load)
        viewModel.onIntent(EditReusableEventCatalogUiIntent.UpdateName("Pollen plus"))
        viewModel.onIntent(EditReusableEventCatalogUiIntent.Save)

        val navigation = effect.await() as EditReusableEventCatalogUiEffect.NavigateBackWithSelection

        assertEquals(7L, repository.updatedId)
        assertEquals("Pollen plus", repository.updatedName)
        assertEquals("Pollen plus", navigation.result.name)
    }

    @Test
    fun load_failure_surfaces_retryable_message_without_enabling_save() = runBlocking {
        val repository = FakeEditReusableEventCatalogRepository(
            getFailure = DomainError.PersistenceError("Couldn't load saved value locally. Try again.")
        )
        val viewModel = buildViewModel(repository)

        viewModel.onIntent(EditReusableEventCatalogUiIntent.Load)

        assertEquals("Couldn't load saved value locally. Try again.", viewModel.state.value.formMessage)
        assertTrue(viewModel.state.value.name.isBlank())
        assertFalse(viewModel.state.value.canSave)
    }

    @Test
    fun validation_error_surfaces_inline_message() = runBlocking {
        val repository = FakeEditReusableEventCatalogRepository(
            updateResult = AppResult.Error(
                DomainError.ValidationError(
                    "Please fix the highlighted fields.",
                    mapOf("name" to "A saved exposure type with this name already exists.")
                )
            )
        )
        val viewModel = buildViewModel(repository)

        viewModel.onIntent(EditReusableEventCatalogUiIntent.Load)
        viewModel.onIntent(EditReusableEventCatalogUiIntent.Save)

        assertEquals("A saved exposure type with this name already exists.", viewModel.state.value.nameError)
        assertTrue(viewModel.state.value.formMessage.isNotBlank())
    }

    @Test
    fun retirement_preview_shows_blocked_state_for_active_catalog_references() = runBlocking {
        val repository = FakeEditReusableEventCatalogRepository()
        val viewModel = buildViewModel(repository, countReferences = { _, _ -> 3 })

        viewModel.onIntent(EditReusableEventCatalogUiIntent.Load)
        viewModel.onIntent(EditReusableEventCatalogUiIntent.RequestRetirementPreview)

        assertTrue(viewModel.state.value.isRetirementDialogVisible)
        assertEquals(3, viewModel.state.value.retirementImpact?.activeReferenceCount)
        assertTrue(viewModel.state.value.retirementImpact?.isBlocked == true)
    }

    @Test
    fun confirm_retirement_navigates_back_after_successful_retire() = runBlocking {
        val repository = FakeEditReusableEventCatalogRepository()
        val viewModel = buildViewModel(repository)
        val effect = async { viewModel.effects.first() }

        viewModel.onIntent(EditReusableEventCatalogUiIntent.Load)
        viewModel.onIntent(EditReusableEventCatalogUiIntent.RequestRetirementPreview)
        viewModel.onIntent(EditReusableEventCatalogUiIntent.ConfirmRetirement)

        val navigation = effect.await() as EditReusableEventCatalogUiEffect.NavigateBackAfterRetirement

        assertEquals(EditReusableEventCatalogUiEffect.NavigateBackAfterRetirement, navigation)
        assertFalse(viewModel.state.value.isRetirementDialogVisible)
    }

    @Test
    fun blocked_retirement_keeps_dialog_visible_without_navigation() = runBlocking {
        val repository = FakeEditReusableEventCatalogRepository()
        val viewModel = buildViewModel(repository, countReferences = { _, _ -> 3 })

        viewModel.onIntent(EditReusableEventCatalogUiIntent.Load)
        viewModel.onIntent(EditReusableEventCatalogUiIntent.RequestRetirementPreview)
        viewModel.onIntent(EditReusableEventCatalogUiIntent.ConfirmRetirement)

        assertTrue(viewModel.state.value.isRetirementDialogVisible)
        assertTrue(viewModel.state.value.retirementImpact?.isBlocked == true)
    }

    @Test
    fun confirm_retirement_failure_keeps_dialog_open_and_surfaces_actionable_error() = runBlocking {
        val repository = FakeEditReusableEventCatalogRepository(
            deleteResult = AppResult.Error(
                DomainError.IntegrityError("Replace or clear the linked records before retiring this saved value.")
            )
        )
        val viewModel = buildViewModel(repository)

        viewModel.onIntent(EditReusableEventCatalogUiIntent.Load)
        viewModel.onIntent(EditReusableEventCatalogUiIntent.RequestRetirementPreview)
        viewModel.onIntent(EditReusableEventCatalogUiIntent.ConfirmRetirement)

        assertTrue(viewModel.state.value.isRetirementDialogVisible)
        assertEquals(
            "Replace or clear the linked records before retiring this saved value.",
            viewModel.state.value.formMessage
        )
        assertFalse(viewModel.state.value.isRetiring)
    }

    private fun buildViewModel(
        repository: FakeEditReusableEventCatalogRepository,
        countReferences: suspend (ReusableEventCatalogKind, Long) -> Int = { _, _ -> 0 }
    ): EditReusableEventCatalogViewModel {
        val impactUseCase = GetReusableEventCatalogRetirementImpactUseCase(repository, countReferences)
        return EditReusableEventCatalogViewModel(
            catalogId = 7L,
            kind = ReusableEventCatalogKind.EXPOSURE_TYPE,
            getReusableEventCatalogEntryUseCase = GetReusableEventCatalogEntryUseCase(repository),
            updateReusableEventCatalogUseCase = UpdateReusableEventCatalogUseCase(repository),
            getReusableEventCatalogRetirementImpactUseCase = impactUseCase,
            retireReusableEventCatalogUseCase = RetireReusableEventCatalogUseCase(repository, impactUseCase)
        )
    }
}

private class FakeEditReusableEventCatalogRepository(
    private val getFailure: DomainError? = null,
    private val updateResult: AppResult<ReusableEventCatalog>? = null,
    private val deleteResult: AppResult<Unit> = AppResult.Success(Unit)
) : ReusableEventCatalogRepository {
    var updatedId: Long? = null
    var updatedName: String? = null

    override fun observeCatalog(kind: ReusableEventCatalogKind): Flow<List<ReusableEventCatalog>> = emptyFlow()

    override suspend fun getCatalogEntry(kind: ReusableEventCatalogKind, id: Long): ReusableEventCatalog? {
        getFailure?.let { throw IllegalStateException(it.message) }
        return ReusableEventCatalog(
            id = id,
            kind = kind,
            name = "Pollen",
            usageCount = 1,
            createdAtEpochMs = 1L,
            updatedAtEpochMs = 1L,
            lastUsedAtEpochMs = 1L
        )
    }

    override suspend fun existsCatalogEntry(kind: ReusableEventCatalogKind, id: Long): Boolean = true

    override suspend fun ensureCatalogEntry(kind: ReusableEventCatalogKind, name: String): ReusableEventCatalog {
        error("unused")
    }

    override suspend fun touchCatalogEntry(kind: ReusableEventCatalogKind, id: Long): ReusableEventCatalog? = null

    override suspend fun updateCatalogEntry(
        kind: ReusableEventCatalogKind,
        id: Long,
        name: String
    ): AppResult<ReusableEventCatalog> {
        updatedId = id
        updatedName = name
        return updateResult ?: AppResult.Success(
            ReusableEventCatalog(
                id = id,
                kind = kind,
                name = name,
                usageCount = 1,
                createdAtEpochMs = 1L,
                updatedAtEpochMs = 2L,
                lastUsedAtEpochMs = 1L
            )
        )
    }

    override suspend fun deleteCatalogEntry(kind: ReusableEventCatalogKind, id: Long): AppResult<Unit> = deleteResult
}
