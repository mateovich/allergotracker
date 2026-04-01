package com.example.app.features.eventcatalog.ui

import com.example.app.MainDispatcherRule
import com.example.app.core.common.error.DomainError
import com.example.app.core.common.result.AppResult
import com.example.app.features.eventcatalog.domain.GetReusableEventCatalogEntryUseCase
import com.example.app.features.eventcatalog.domain.ReusableEventCatalog
import com.example.app.features.eventcatalog.domain.ReusableEventCatalogKind
import com.example.app.features.eventcatalog.domain.ReusableEventCatalogRepository
import com.example.app.features.eventcatalog.domain.UpdateReusableEventCatalogUseCase
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
        val viewModel = EditReusableEventCatalogViewModel(
            catalogId = 7L,
            kind = ReusableEventCatalogKind.EXPOSURE_TYPE,
            getReusableEventCatalogEntryUseCase = GetReusableEventCatalogEntryUseCase(repository),
            updateReusableEventCatalogUseCase = UpdateReusableEventCatalogUseCase(repository)
        )
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
        val viewModel = EditReusableEventCatalogViewModel(
            catalogId = 7L,
            kind = ReusableEventCatalogKind.EXPOSURE_TYPE,
            getReusableEventCatalogEntryUseCase = GetReusableEventCatalogEntryUseCase(repository),
            updateReusableEventCatalogUseCase = UpdateReusableEventCatalogUseCase(repository)
        )

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
        val viewModel = EditReusableEventCatalogViewModel(
            catalogId = 7L,
            kind = ReusableEventCatalogKind.EXPOSURE_TYPE,
            getReusableEventCatalogEntryUseCase = GetReusableEventCatalogEntryUseCase(repository),
            updateReusableEventCatalogUseCase = UpdateReusableEventCatalogUseCase(repository)
        )

        viewModel.onIntent(EditReusableEventCatalogUiIntent.Load)
        viewModel.onIntent(EditReusableEventCatalogUiIntent.Save)

        assertEquals("A saved exposure type with this name already exists.", viewModel.state.value.nameError)
        assertTrue(viewModel.state.value.formMessage.isNotBlank())
    }
}

private class FakeEditReusableEventCatalogRepository(
    private val getFailure: DomainError? = null,
    private val updateResult: AppResult<ReusableEventCatalog>? = null
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
}
