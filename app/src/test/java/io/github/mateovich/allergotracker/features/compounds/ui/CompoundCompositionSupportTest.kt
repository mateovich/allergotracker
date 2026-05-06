package io.github.mateovich.allergotracker.features.compounds.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class CompoundCompositionSupportTest {

    @Test
    fun component_search_key_collapses_whitespace_and_ignores_case() {
        assertEquals("oat milk", "  OAT   milk  ".toComponentSearchKey())
    }

    @Test
    fun component_search_key_strips_diacritics_from_saved_names() {
        assertEquals("cafe", "Café".toComponentSearchKey())
    }

    @Test
    fun component_search_key_strips_diacritics_from_queries() {
        assertEquals("limon", "Limón".toComponentSearchKey())
    }

    @Test
    fun filter_by_query_waits_for_two_normalized_characters() {
        val options = listOf(catalogOption(name = "Café"))

        assertEquals(emptyList<CompoundComponentCatalogOptionUiModel>(), options.filterByQuery("c"))
        assertEquals(listOf("Café"), options.filterByQuery("ca").map { it.name })
    }

    @Test
    fun filter_by_query_hides_nonblank_query_that_normalizes_to_empty() {
        val options = listOf(catalogOption(name = "Café"))

        assertEquals(emptyList<CompoundComponentCatalogOptionUiModel>(), options.filterByQuery("\u0301"))
    }

    @Test
    fun filter_by_query_matches_diacritic_equivalent_names_and_queries() {
        val options = listOf(catalogOption(name = "Cafe"), catalogOption(id = 2L, name = "Limón"))

        assertEquals(listOf("Limón"), options.filterByQuery("limon").map { it.name })
        assertEquals(listOf("Cafe"), options.filterByQuery("café").map { it.name })
    }

    @Test
    fun exact_saved_ingredient_match_uses_full_available_options() {
        val options = (1L..8L).map { id -> catalogOption(id = id, name = "Cafe $id") } +
            catalogOption(id = 9L, name = "Cafe")

        assertEquals(false, options.filterByQuery("cafe").take(MAX_VISIBLE_COMPONENT_OPTIONS).any { it.name == "Cafe" })
        assertEquals(true, options.hasExactSavedIngredientMatch(" café "))
    }

    @Test
    fun exact_saved_ingredient_match_still_detects_hidden_one_character_name() {
        val options = listOf(catalogOption(name = "Á"))

        assertEquals(emptyList<CompoundComponentCatalogOptionUiModel>(), options.filterByQuery("a"))
        assertEquals(true, options.hasExactSavedIngredientMatch("a"))
    }

    private fun catalogOption(
        id: Long = 1L,
        name: String
    ): CompoundComponentCatalogOptionUiModel {
        return CompoundComponentCatalogOptionUiModel(
            id = id,
            name = name,
            kind = "ingredient",
            supportingLabel = "Ingredient",
            updatedAtEpochMs = id
        )
    }
}
