package com.example.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class AppRoutesTest {

    @Test
    fun edit_routes_build_stable_paths_from_entity_ids() {
        assertEquals("edit/ingestion/4", AppRoutes.editIngestion(4L))
        assertEquals("edit/exposure/5", AppRoutes.editExposure(5L))
        assertEquals("edit/outbreak/6?openDelete=false", AppRoutes.editOutbreak(6L))
        assertEquals("edit/outbreak/6?openDelete=true", AppRoutes.editOutbreak(6L, openDelete = true))
    }

    @Test
    fun create_routes_cover_reusable_catalog_flows() {
        assertEquals("create/ingredient", AppRoutes.CREATE_INGREDIENT)
        assertEquals("create/compound", AppRoutes.CREATE_COMPOUND)
    }
}
