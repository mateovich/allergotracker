package com.example.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class AppRoutesTest {

    @Test
    fun edit_routes_build_stable_paths_from_entity_ids() {
        assertEquals("edit/ingestion/4", AppRoutes.editIngestion(4L))
        assertEquals("edit/exposure/5", AppRoutes.editExposure(5L))
        assertEquals("edit/outbreak/6", AppRoutes.editOutbreak(6L))
    }
}
