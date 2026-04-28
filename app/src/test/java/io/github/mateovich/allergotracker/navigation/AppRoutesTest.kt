package io.github.mateovich.allergotracker.navigation

import androidx.lifecycle.SavedStateHandle
import io.github.mateovich.allergotracker.features.eventcatalog.domain.ReusableEventCatalogKind
import io.github.mateovich.allergotracker.features.ingestion.domain.ReusableEntityKind
import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Test

class AppRoutesTest {

    @Test
    fun edit_routes_build_stable_paths_from_entity_ids() {
        assertEquals("edit/ingestion/4", AppRoutes.editIngestion(4L))
        assertEquals("edit/exposure/5?openDelete=false", AppRoutes.editExposure(5L))
        assertEquals("edit/exposure/5?openDelete=true", AppRoutes.editExposure(5L, openDelete = true))
        assertEquals("edit/outbreak/6?openDelete=false", AppRoutes.editOutbreak(6L))
        assertEquals("edit/outbreak/6?openDelete=true", AppRoutes.editOutbreak(6L, openDelete = true))
    }

    @Test
    fun create_routes_cover_reusable_catalog_flows() {
        assertEquals("create/ingredient", AppRoutes.CREATE_INGREDIENT)
        assertEquals(
            "create/compound?prefillName={prefillName}&seedIngredientId={seedIngredientId}&seedIngredientName={seedIngredientName}",
            AppRoutes.CREATE_COMPOUND
        )
        assertEquals(
            "create/compound?prefillName=Banana%20shake&seedIngredientId=7&seedIngredientName=Oat%20milk",
            AppRoutes.createCompound(
                prefillName = "Banana shake",
                seedIngredientId = 7L,
                seedIngredientName = "Oat milk"
            )
        )
    }

    @Test
    fun restore_helpers_fail_closed_when_saved_state_is_incomplete() {
        val ingestionState = SavedStateHandle(
            mapOf(
                AppRoutes.REUSABLE_ENTITY_NAME_RESULT to "Oat milk",
                AppRoutes.REUSABLE_ENTITY_KIND_RESULT to ReusableEntityKind.INGREDIENT.storageValue
            )
        )
        val catalogState = SavedStateHandle(
            mapOf(
                AppRoutes.EVENT_CATALOG_ID_RESULT to 7L,
                AppRoutes.EVENT_CATALOG_NAME_RESULT to "Dust"
            )
        )

        assertNull(ingestionState.toIngestionReusableSelectionResult())
        assertNull(catalogState.toEditedEventCatalogSelectionResult())
    }

    @Test
    fun restore_helpers_rebuild_valid_saved_state_payloads() {
        val ingestionState = SavedStateHandle(
            mapOf(
                AppRoutes.REUSABLE_ENTITY_ID_RESULT to 11L,
                AppRoutes.REUSABLE_ENTITY_NAME_RESULT to "Oat milk",
                AppRoutes.REUSABLE_ENTITY_KIND_RESULT to ReusableEntityKind.INGREDIENT.storageValue
            )
        )
        val catalogState = SavedStateHandle(
            mapOf(
                AppRoutes.EVENT_CATALOG_ID_RESULT to 7L,
                AppRoutes.EVENT_CATALOG_NAME_RESULT to "Dust",
                AppRoutes.EVENT_CATALOG_KIND_RESULT to ReusableEventCatalogKind.EXPOSURE_TYPE.storageValue
            )
        )

        assertEquals(11L, ingestionState.toIngestionReusableSelectionResult()?.id)
        assertEquals(ReusableEventCatalogKind.EXPOSURE_TYPE, catalogState.toEditedEventCatalogSelectionResult()?.kind)
    }
}
