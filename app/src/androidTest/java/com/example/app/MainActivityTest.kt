package com.example.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.espresso.Espresso.pressBack
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun resetAppState() {
        composeRule.activity.resetAppStateForTesting()
        composeRule.waitForIdle()
    }

    @Test
    fun app_launches_into_records_with_enabled_primary_add_action() {
        composeRule.onNodeWithText("Records").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Add record").assertIsDisplayed().assertIsEnabled()
        composeRule.onNodeWithTag("records-add-fab")
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun add_action_opens_compact_picker_with_exact_three_options() {
        composeRule.onNodeWithContentDescription("Add record").performClick()

        composeRule.onNodeWithContentDescription("Choose event type").assertIsDisplayed()
        composeRule.onNodeWithText("Ingestion").assertIsDisplayed()
        composeRule.onNodeWithText("Exposure").assertIsDisplayed()
        composeRule.onNodeWithText("Outbreak").assertIsDisplayed()
        composeRule.onAllNodesWithText("Ingestion").assertCountEquals(1)
        composeRule.onAllNodesWithText("Exposure").assertCountEquals(1)
        composeRule.onAllNodesWithText("Outbreak").assertCountEquals(1)
    }

    @Test
    fun records_empty_state_directs_user_to_plus_action() {
        composeRule.onNodeWithTag("records-empty-state").assertIsDisplayed()
        composeRule.onNodeWithText("No records yet").assertIsDisplayed()
        composeRule.onNodeWithText("Use + to create your first record.").assertIsDisplayed()
    }

    @Test
    fun selecting_each_picker_option_opens_matching_form() {
        assertNavigatesToForm("Ingestion", "Create Ingestion")
        assertNavigatesToForm("Exposure", "Create Exposure")
        assertNavigatesToForm("Outbreak", "Create Outbreak")
    }

    @Test
    fun create_forms_show_calm_offline_notice() {
        assertCreateFormShowsOfflineNotice("Ingestion", "create-ingestion-form-screen")
        assertCreateFormShowsOfflineNotice("Exposure", "create-exposure-form-screen")
        assertCreateFormShowsOfflineNotice("Outbreak", "create-outbreak-form-screen")
    }

    @Test
    fun ingestion_flow_shows_inline_validation_and_returns_saved_record_to_records() {
        composeRule.onNodeWithContentDescription("Add record").performClick()
        composeRule.onNodeWithText("Ingestion").performClick()

        composeRule.onNodeWithTag("create-ingestion-form-screen").assertIsDisplayed()
        composeRule.onNodeWithTag("ingestion-occurred-at-label").assertIsDisplayed()

        composeRule.onNodeWithTag("ingestion-item-name").performTextInput("Tea")
        composeRule.onNodeWithTag("ingestion-save").performClick()

        composeRule.onNodeWithText("Choose a quantity.").assertIsDisplayed()
        composeRule.onNodeWithTag("ingestion-item-name").assertTextContains("Tea")

        composeRule.onNodeWithTag("ingestion-quantity-2").performClick()
        composeRule.onNodeWithTag("ingestion-save").performClick()

        composeRule.onNodeWithText("Records").assertIsDisplayed()
        composeRule.onNodeWithTag("records-list").assertIsDisplayed()
        composeRule.onNodeWithText("Tea").assertIsDisplayed()
        composeRule.onNodeWithText("2 Light snack").assertIsDisplayed()
    }

    @Test
    fun ingestion_flow_can_create_reusable_ingredient_inline_and_return_with_selection() {
        composeRule.onNodeWithContentDescription("Add record").performClick()
        composeRule.onNodeWithText("Ingestion").performClick()

        composeRule.onNodeWithTag("ingestion-create-ingredient").performClick()
        composeRule.onNodeWithTag("create-ingredient-form-screen").assertIsDisplayed()
        composeRule.onNodeWithTag("ingredient-name").performTextInput("Oat milk")
        composeRule.onNodeWithTag("ingredient-save").performClick()

        composeRule.onNodeWithTag("create-ingestion-form-screen").assertIsDisplayed()
        composeRule.onNodeWithTag("ingestion-item-name").assertTextContains("Oat milk")
        composeRule.onNodeWithTag("ingestion-reusable-summary").assertTextContains("Reusable ingredient selected: Oat milk")
    }

    @Test
    fun ingestion_flow_can_create_reusable_compound_inline_and_return_with_selection() {
        composeRule.onNodeWithContentDescription("Add record").performClick()
        composeRule.onNodeWithText("Ingestion").performClick()

        composeRule.onNodeWithTag("ingestion-create-compound").performClick()
        composeRule.onNodeWithTag("create-compound-form-screen").assertIsDisplayed()
        composeRule.onNodeWithTag("compound-name").performTextInput("Breakfast mix")
        composeRule.onNodeWithTag("compound-save").performClick()

        composeRule.onNodeWithTag("create-ingestion-form-screen").assertIsDisplayed()
        composeRule.onNodeWithTag("ingestion-item-name").assertTextContains("Breakfast mix")
        composeRule.onNodeWithTag("ingestion-reusable-summary").assertTextContains("Reusable compound selected: Breakfast mix")
    }

    @Test
    fun ingestion_flow_can_create_reusable_compound_inline_save_and_render_reused_record() {
        composeRule.onNodeWithContentDescription("Add record").performClick()
        composeRule.onNodeWithText("Ingestion").performClick()

        composeRule.onNodeWithTag("ingestion-notes").performTextInput("Post workout")
        composeRule.onNodeWithTag("ingestion-quantity-2").performClick()
        composeRule.onNodeWithTag("ingestion-create-compound").performClick()
        composeRule.onNodeWithTag("compound-name").performTextInput("Breakfast mix")
        composeRule.onNodeWithTag("compound-save").performClick()

        composeRule.onNodeWithTag("create-ingestion-form-screen").assertIsDisplayed()
        composeRule.onNodeWithTag("ingestion-item-name").assertTextContains("Breakfast mix")
        composeRule.onNodeWithTag("ingestion-notes").assertTextContains("Post workout")
        composeRule.onNodeWithTag("ingestion-reusable-summary").assertTextContains("Reusable compound selected: Breakfast mix")
        composeRule.onNodeWithTag("ingestion-save").performClick()

        composeRule.onNodeWithTag("records-list").assertIsDisplayed()
        composeRule.onNodeWithText("Breakfast mix").assertIsDisplayed()
        composeRule.onNodeWithText("2 Light snack - Reusable compound").assertIsDisplayed()
    }

    @Test
    fun ingestion_flow_can_reuse_saved_ingredient_in_a_later_capture() {
        composeRule.onNodeWithContentDescription("Add record").performClick()
        composeRule.onNodeWithText("Ingestion").performClick()
        composeRule.onNodeWithTag("ingestion-create-ingredient").performClick()
        composeRule.onNodeWithTag("ingredient-name").performTextInput("Oat milk")
        composeRule.onNodeWithTag("ingredient-save").performClick()
        pressBack()

        composeRule.onNodeWithContentDescription("Add record").performClick()
        composeRule.onNodeWithText("Ingestion").performClick()
        composeRule.onNodeWithText("Oat milk - Ingredient").performClick()

        composeRule.onNodeWithTag("ingestion-item-name").assertTextContains("Oat milk")
        composeRule.onNodeWithTag("ingestion-reusable-summary").assertTextContains("Reusable ingredient selected: Oat milk")
    }

    @Test
    fun ingestion_flow_filters_saved_reusable_items_before_selection() {
        composeRule.onNodeWithContentDescription("Add record").performClick()
        composeRule.onNodeWithText("Ingestion").performClick()
        composeRule.onNodeWithTag("ingestion-create-ingredient").performClick()
        composeRule.onNodeWithTag("ingredient-name").performTextInput("Oat milk")
        composeRule.onNodeWithTag("ingredient-save").performClick()
        pressBack()

        composeRule.onNodeWithContentDescription("Add record").performClick()
        composeRule.onNodeWithText("Ingestion").performClick()
        composeRule.onNodeWithTag("ingestion-reusable-search").performTextInput("oat")
        composeRule.onNodeWithText("Oat milk - Ingredient").assertIsDisplayed().performClick()

        composeRule.onNodeWithTag("ingestion-reusable-summary").assertTextContains("Reusable ingredient selected: Oat milk")
    }

    @Test
    fun ingestion_flow_preserves_context_when_creating_reusable_inline() {
        composeRule.onNodeWithContentDescription("Add record").performClick()
        composeRule.onNodeWithText("Ingestion").performClick()

        composeRule.onNodeWithTag("ingestion-notes").performTextInput("Morning routine")
        composeRule.onNodeWithTag("ingestion-quantity-2").performClick()
        composeRule.onNodeWithTag("ingestion-time-one_hour_ago").performClick()
        composeRule.onNodeWithTag("ingestion-create-ingredient").performClick()
        composeRule.onNodeWithTag("ingredient-name").performTextInput("Oat milk")
        composeRule.onNodeWithTag("ingredient-save").performClick()

        composeRule.onNodeWithTag("create-ingestion-form-screen").assertIsDisplayed()
        composeRule.onNodeWithTag("ingestion-item-name").assertTextContains("Oat milk")
        composeRule.onNodeWithTag("ingestion-notes").assertTextContains("Morning routine")
        composeRule.onNodeWithTag("ingestion-reusable-summary").assertTextContains("Reusable ingredient selected: Oat milk")
        composeRule.onNodeWithTag("ingestion-save").performClick()

        composeRule.onNodeWithTag("records-list").assertIsDisplayed()
        composeRule.onNodeWithText("Oat milk").assertIsDisplayed()
        composeRule.onNodeWithText("2 Light snack - Reusable ingredient").assertIsDisplayed()
    }

    @Test
    fun exposure_flow_shows_inline_validation_and_returns_saved_record_to_records() {
        composeRule.onNodeWithContentDescription("Add record").performClick()
        composeRule.onNodeWithText("Exposure").performClick()

        composeRule.onNodeWithTag("create-exposure-form-screen").assertIsDisplayed()
        composeRule.onNodeWithTag("exposure-occurred-at-label").assertIsDisplayed()
        composeRule.onNodeWithTag("exposure-occurred-at-input").assertIsDisplayed()

        composeRule.onNodeWithTag("exposure-save").performClick()

        composeRule.onNodeWithText("Enter what you were exposed to.").assertIsDisplayed()

        composeRule.onNodeWithTag("exposure-type").performTextInput("Pollen")
        composeRule.onNodeWithTag("exposure-save").performClick()

        waitForTag("records-list")
        composeRule.onNodeWithText("Records").assertExists()
        composeRule.onNodeWithText("Exposure").assertIsDisplayed()
        composeRule.onNodeWithText("Pollen").assertIsDisplayed()
        composeRule.onNodeWithText("Intensity 3").assertIsDisplayed()
    }

    @Test
    fun exposure_flow_allows_manual_datetime_correction_with_inline_feedback() {
        composeRule.onNodeWithContentDescription("Add record").performClick()
        composeRule.onNodeWithText("Exposure").performClick()

        composeRule.onNodeWithTag("exposure-type").performTextInput("Pollen")
        composeRule.onNodeWithTag("exposure-occurred-at-input").performTextReplacement("invalid")
        composeRule.onNodeWithTag("exposure-save").performClick()

        waitForText("Enter date and time as yyyy-MM-dd HH:mm.")
    }

    @Test
    fun outbreak_flow_shows_inline_validation_and_returns_saved_record_to_records() {
        composeRule.onNodeWithContentDescription("Add record").performClick()
        composeRule.onNodeWithText("Outbreak").performClick()

        composeRule.onNodeWithTag("create-outbreak-form-screen").assertIsDisplayed()
        composeRule.onNodeWithTag("outbreak-occurred-at-label").assertIsDisplayed()
        composeRule.onNodeWithTag("outbreak-occurred-at-input").assertIsDisplayed()

        composeRule.onNodeWithTag("outbreak-save").performClick()

        composeRule.onNodeWithText("Enter what happened.").assertIsDisplayed()
        composeRule.onNodeWithText("Enter where on the body it happened.").assertIsDisplayed()

        composeRule.onNodeWithTag("outbreak-descriptor").performTextInput("Skin flare")
        composeRule.onNodeWithTag("outbreak-body-location").performTextInput("Left arm")
        composeRule.onNodeWithTag("outbreak-save").performClick()

        waitForTag("records-list")
        composeRule.onNodeWithText("Outbreak").assertIsDisplayed()
        composeRule.onNodeWithText("Skin flare").assertIsDisplayed()
        composeRule.onNodeWithText("Intensity 3 - Left arm").assertIsDisplayed()
    }

    @Test
    fun outbreak_flow_allows_manual_datetime_correction_with_inline_feedback() {
        composeRule.onNodeWithContentDescription("Add record").performClick()
        composeRule.onNodeWithText("Outbreak").performClick()

        composeRule.onNodeWithTag("outbreak-descriptor").performTextInput("Skin flare")
        composeRule.onNodeWithTag("outbreak-body-location").performTextInput("Left arm")
        composeRule.onNodeWithTag("outbreak-occurred-at-input").performTextReplacement("invalid")
        composeRule.onNodeWithTag("outbreak-save").performClick()

        waitForText("Enter date and time as yyyy-MM-dd HH:mm.")
    }

    @Test
    fun saved_record_remains_visible_after_normal_activity_recreation() {
        composeRule.onNodeWithContentDescription("Add record").performClick()
        composeRule.onNodeWithText("Ingestion").performClick()

        composeRule.onNodeWithTag("ingestion-item-name").performTextInput("Tea")
        composeRule.onNodeWithTag("ingestion-quantity-2").performClick()
        composeRule.onNodeWithTag("ingestion-save").performClick()

        composeRule.onNodeWithText("Tea").assertIsDisplayed()

        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Records").assertIsDisplayed()
        composeRule.onNodeWithText("Tea").assertIsDisplayed()
        composeRule.onNodeWithTag("records-list").assertIsDisplayed()
    }

    @Test
    fun ingestion_record_can_be_edited_and_deleted_offline_end_to_end() {
        composeRule.onNodeWithContentDescription("Add record").performClick()
        composeRule.onNodeWithText("Ingestion").performClick()

        composeRule.onNodeWithTag("ingestion-item-name").performTextInput("Tea")
        composeRule.onNodeWithTag("ingestion-quantity-2").performClick()
        composeRule.onNodeWithTag("ingestion-save").performClick()

        composeRule.onNodeWithText("Tea").performClick()
        composeRule.onNodeWithTag("record-detail-edit").performClick()
        assertOfflineFormHasNoDependencyWarnings("edit-ingestion-form-screen")

        composeRule.onNodeWithTag("edit-ingestion-item-name").performTextReplacement("Green tea")
        composeRule.onNodeWithTag("edit-ingestion-quantity-3").performClick()
        composeRule.onNodeWithTag("edit-ingestion-save").performClick()

        composeRule.onNodeWithTag("records-list").assertIsDisplayed()
        composeRule.onNodeWithText("Green tea").assertIsDisplayed()
        composeRule.onAllNodesWithText("Tea").assertCountEquals(0)

        composeRule.onNodeWithText("Green tea").performClick()
        composeRule.onNodeWithTag("record-detail-delete").performClick()
        composeRule.onNodeWithTag("edit-ingestion-delete").performClick()
        composeRule.onNodeWithTag("edit-ingestion-delete-confirm").performClick()

        composeRule.onNodeWithTag("records-empty-state").assertIsDisplayed()
        composeRule.onAllNodesWithText("Green tea").assertCountEquals(0)
    }

    @Test
    fun exposure_record_can_be_edited_and_deleted_offline_end_to_end() {
        composeRule.onNodeWithContentDescription("Add record").performClick()
        composeRule.onNodeWithText("Exposure").performClick()

        composeRule.onNodeWithTag("exposure-type").performTextInput("Pollen")
        composeRule.onNodeWithTag("exposure-save").performClick()

        waitForText("Pollen")
        composeRule.onNodeWithText("Pollen").performClick()
        waitForTag("record-detail-edit")
        composeRule.onNodeWithTag("record-detail-edit").performClick()
        assertOfflineFormHasNoDependencyWarnings("edit-exposure-form-screen")

        composeRule.onNodeWithTag("edit-exposure-type").performTextReplacement("Dust")
        composeRule.onNodeWithTag("edit-exposure-save").performClick()

        waitForTag("records-list")
        composeRule.onNodeWithText("Dust").assertIsDisplayed()
        composeRule.onAllNodesWithText("Pollen").assertCountEquals(0)

        composeRule.onNodeWithText("Dust").performClick()
        composeRule.onNodeWithTag("record-detail-delete").performClick()
        composeRule.onNodeWithTag("edit-exposure-delete").performClick()
        composeRule.onNodeWithTag("edit-exposure-delete-confirm").performClick()

        composeRule.onNodeWithTag("records-empty-state").assertIsDisplayed()
        composeRule.onAllNodesWithText("Dust").assertCountEquals(0)
    }

    @Test
    fun exposure_reusable_edit_flow_updates_runtime_navigation_and_post_save_detail_labels() {
        composeRule.onNodeWithContentDescription("Add record").performClick()
        composeRule.onNodeWithText("Exposure").performClick()

        composeRule.onNodeWithTag("exposure-type").performTextInput("Pollen")
        composeRule.onNodeWithTag("exposure-type-create-inline").performClick()
        composeRule.onNodeWithTag("exposure-save").performClick()

        waitForText("Pollen")
        composeRule.onNodeWithText("Pollen").performClick()
        waitForTag("record-detail-edit")
        composeRule.onNodeWithTag("record-detail-edit").performClick()
        composeRule.onNodeWithTag("edit-exposure-form-screen").assertIsDisplayed()
        composeRule.onNodeWithTag("edit-exposure-type-summary").assertIsDisplayed()
        composeRule.onNodeWithTag("edit-exposure-type-edit").performClick()

        composeRule.onNodeWithTag("edit-event-catalog-form-screen").assertIsDisplayed()
        composeRule.onNodeWithTag("edit-event-catalog-name").performTextReplacement("Dust")
        composeRule.onNodeWithTag("edit-event-catalog-save").performClick()

        composeRule.onNodeWithTag("edit-exposure-form-screen").assertIsDisplayed()
        composeRule.onNodeWithTag("edit-exposure-type").assertTextContains("Dust")
        composeRule.onNodeWithText("Saved exposure type selected: Dust").assertIsDisplayed()
        composeRule.onNodeWithTag("edit-exposure-save").performClick()

        waitForTag("records-list")
        composeRule.onNodeWithText("Dust").assertIsDisplayed()
        composeRule.onAllNodesWithText("Pollen").assertCountEquals(0)

        composeRule.onNodeWithText("Dust").performClick()
        composeRule.onNodeWithTag("record-detail-screen").assertIsDisplayed()
        composeRule.onAllNodesWithText("Dust").assertCountEquals(2)
    }

    @Test
    fun ingestion_reusable_ingredient_edit_flow_updates_runtime_navigation_and_saved_labels() {
        composeRule.onNodeWithContentDescription("Add record").performClick()
        composeRule.onNodeWithText("Ingestion").performClick()

        composeRule.onNodeWithTag("ingestion-create-ingredient").performClick()
        composeRule.onNodeWithTag("ingredient-name").performTextInput("Oat milk")
        composeRule.onNodeWithTag("ingredient-save").performClick()

        composeRule.onNodeWithTag("ingestion-reusable-edit").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("edit-ingredient-form-screen").assertIsDisplayed()
        composeRule.onNodeWithTag("edit-ingredient-name").performTextReplacement("Oat milk plus")
        composeRule.onNodeWithTag("edit-ingredient-save").performClick()

        composeRule.onNodeWithTag("create-ingestion-form-screen").assertIsDisplayed()
        composeRule.onNodeWithTag("ingestion-item-name").assertTextContains("Oat milk plus")
        composeRule.onNodeWithTag("ingestion-reusable-summary").assertTextContains("Reusable ingredient selected: Oat milk plus")
    }

    @Test
    fun ingestion_reusable_compound_edit_flow_updates_runtime_navigation_and_saved_labels() {
        composeRule.onNodeWithContentDescription("Add record").performClick()
        composeRule.onNodeWithText("Ingestion").performClick()

        composeRule.onNodeWithTag("ingestion-create-compound").performClick()
        composeRule.onNodeWithTag("compound-name").performTextInput("Breakfast mix")
        composeRule.onNodeWithTag("compound-save").performClick()

        composeRule.onNodeWithTag("ingestion-reusable-edit").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("edit-compound-form-screen").assertIsDisplayed()
        composeRule.onNodeWithTag("edit-compound-name").performTextReplacement("Breakfast mix plus")
        composeRule.onNodeWithTag("edit-compound-save").performClick()

        composeRule.onNodeWithTag("create-ingestion-form-screen").assertIsDisplayed()
        composeRule.onNodeWithTag("ingestion-item-name").assertTextContains("Breakfast mix plus")
        composeRule.onNodeWithTag("ingestion-reusable-summary").assertTextContains("Reusable compound selected: Breakfast mix plus")
    }

    @Test
    fun reusable_edit_flows_keep_talkback_visible_labels_and_inline_errors() {
        composeRule.onNodeWithContentDescription("Add record").performClick()
        composeRule.onNodeWithText("Ingestion").performClick()
        composeRule.onNodeWithTag("ingestion-create-ingredient").performClick()
        composeRule.onNodeWithTag("ingredient-name").performTextInput("Oat milk")
        composeRule.onNodeWithTag("ingredient-save").performClick()
        composeRule.onNodeWithTag("ingestion-reusable-edit").performClick()

        composeRule.onNodeWithText("Edit Ingredient").assertIsDisplayed()
        composeRule.onNodeWithText("Ingredient name").assertIsDisplayed()
        composeRule.onNodeWithText("Notes (optional)").assertIsDisplayed()
        composeRule.onNodeWithText("Save changes").assertIsDisplayed()
        composeRule.onNodeWithText("Works offline. Saved only on this device.").assertIsDisplayed()

        composeRule.onNodeWithTag("edit-ingredient-name").performTextReplacement("   ")
        composeRule.onNodeWithText("Enter an ingredient name.").assertIsDisplayed()

        pressBack()
        pressBack()
        composeRule.onNodeWithTag("create-ingestion-form-screen").assertIsDisplayed()
        composeRule.onNodeWithTag("ingestion-reusable-clear").performClick()
        composeRule.onNodeWithTag("ingestion-create-compound").performClick()
        composeRule.onNodeWithTag("compound-name").performTextInput("Breakfast mix")
        composeRule.onNodeWithTag("compound-save").performClick()
        composeRule.onNodeWithTag("ingestion-reusable-edit").performClick()

        composeRule.onNodeWithText("Edit Compound").assertIsDisplayed()
        composeRule.onNodeWithText("Compound name").assertIsDisplayed()
        composeRule.onNodeWithText("Save changes").assertIsDisplayed()
        composeRule.onNodeWithText("Works offline. Saved only on this device.").assertIsDisplayed()

        pressBack()
        composeRule.onNodeWithTag("create-ingestion-form-screen").assertIsDisplayed()
        pressBack()
        composeRule.onNodeWithText("Records").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Add record").performClick()
        composeRule.onNodeWithText("Exposure").performClick()
        composeRule.onNodeWithTag("exposure-type").performTextInput("Pollen")
        composeRule.onNodeWithTag("exposure-type-create-inline").performClick()
        composeRule.onNodeWithTag("exposure-save").performClick()
        composeRule.onNodeWithText("Pollen").performClick()
        composeRule.onNodeWithTag("record-detail-edit").performClick()
        composeRule.onNodeWithTag("edit-exposure-type-edit").performClick()

        composeRule.onNodeWithText("Edit Exposure Type").assertIsDisplayed()
        composeRule.onNodeWithText("Exposure type").assertIsDisplayed()
        composeRule.onNodeWithText("Save changes").assertIsDisplayed()
        composeRule.onNodeWithText("Works offline. Saved only on this device.").assertIsDisplayed()
    }

    @Test
    fun ingredient_retirement_confirm_flow_clears_reusable_selection_after_return() {
        composeRule.onNodeWithContentDescription("Add record").performClick()
        composeRule.onNodeWithText("Ingestion").performClick()
        composeRule.onNodeWithTag("ingestion-create-ingredient").performClick()
        composeRule.onNodeWithTag("ingredient-name").performTextInput("Oat milk")
        composeRule.onNodeWithTag("ingredient-save").performClick()
        composeRule.onNodeWithTag("ingestion-reusable-edit").performClick()

        composeRule.onNodeWithTag("edit-ingredient-retire").performClick()
        composeRule.onNodeWithTag("edit-ingredient-retire-message").assertIsDisplayed()
        composeRule.onNodeWithTag("edit-ingredient-retire-confirm").assertIsDisplayed()
        composeRule.onNodeWithTag("edit-ingredient-retire-confirm").performClick()

        composeRule.onNodeWithTag("create-ingestion-form-screen").assertIsDisplayed()
        composeRule.onAllNodesWithTag("ingestion-reusable-summary").assertCountEquals(0)
        composeRule.onAllNodesWithTag("ingestion-reusable-edit").assertCountEquals(0)
        composeRule.onNodeWithTag("ingestion-form-message")
            .assertTextContains("Saved reusable item was retired. Review the text value before saving.")
        composeRule.onNodeWithTag("ingestion-item-name").assertTextContains("Oat milk")
    }

    @Test
    fun compound_retirement_confirm_flow_clears_reusable_selection_after_return() {
        composeRule.onNodeWithContentDescription("Add record").performClick()
        composeRule.onNodeWithText("Ingestion").performClick()
        composeRule.onNodeWithTag("ingestion-create-compound").performClick()
        composeRule.onNodeWithTag("compound-name").performTextInput("Breakfast mix")
        composeRule.onNodeWithTag("compound-save").performClick()
        composeRule.onNodeWithTag("ingestion-reusable-edit").performClick()

        composeRule.onNodeWithTag("edit-compound-retire").performClick()
        composeRule.onNodeWithTag("edit-compound-retire-message").assertIsDisplayed()
        composeRule.onNodeWithTag("edit-compound-retire-confirm").assertIsDisplayed()
        composeRule.onNodeWithTag("edit-compound-retire-confirm").performClick()

        composeRule.onNodeWithTag("create-ingestion-form-screen").assertIsDisplayed()
        composeRule.onAllNodesWithTag("ingestion-reusable-summary").assertCountEquals(0)
        composeRule.onAllNodesWithTag("ingestion-reusable-edit").assertCountEquals(0)
        composeRule.onNodeWithTag("ingestion-form-message")
            .assertTextContains("Saved reusable item was retired. Review the text value before saving.")
        composeRule.onNodeWithTag("ingestion-item-name").assertTextContains("Breakfast mix")
    }

    @Test
    fun exposure_event_catalog_retirement_confirm_flow_recovers_create_screen_and_saves_record() {
        composeRule.onNodeWithContentDescription("Add record").performClick()
        composeRule.onNodeWithText("Exposure").performClick()
        composeRule.onNodeWithTag("exposure-type").performTextInput("Pollen")
        composeRule.onNodeWithTag("exposure-type-create-inline").performClick()
        composeRule.onNodeWithTag("exposure-save").performClick()

        waitForText("Pollen")
        composeRule.onNodeWithText("Pollen").performClick()
        waitForTag("record-detail-delete")
        composeRule.onNodeWithTag("record-detail-delete").performClick()
        composeRule.onNodeWithTag("edit-exposure-delete").performClick()
        composeRule.onNodeWithTag("edit-exposure-delete-confirm").performClick()

        composeRule.onNodeWithContentDescription("Add record").performClick()
        composeRule.onNodeWithText("Exposure").performClick()
        composeRule.onNodeWithText("Pollen").performClick()
        composeRule.onNodeWithTag("exposure-type-edit").performClick()
        composeRule.onNodeWithTag("edit-event-catalog-retire").performClick()
        composeRule.onNodeWithTag("edit-event-catalog-retire-message").assertIsDisplayed()
        composeRule.onNodeWithTag("edit-event-catalog-retire-confirm").assertIsDisplayed().performClick()

        composeRule.onNodeWithTag("create-exposure-form-screen").assertIsDisplayed()
        composeRule.onAllNodesWithTag("exposure-type-summary").assertCountEquals(0)
        composeRule.onAllNodesWithTag("exposure-type-edit").assertCountEquals(0)
        composeRule.onNodeWithTag("exposure-form-message")
            .assertTextContains("Saved exposure type was retired. Review the text value before saving.")
        composeRule.onNodeWithTag("exposure-type").assertTextContains("Pollen")

        composeRule.onNodeWithTag("exposure-save").performClick()

        waitForTag("records-list")
        composeRule.onNodeWithText("Exposure").assertIsDisplayed()
        composeRule.onNodeWithText("Pollen").assertIsDisplayed()
    }

    @Test
    fun outbreak_event_catalog_retirement_confirm_flow_recovers_create_screen_and_saves_record() {
        composeRule.onNodeWithContentDescription("Add record").performClick()
        composeRule.onNodeWithText("Outbreak").performClick()
        composeRule.onNodeWithTag("outbreak-descriptor").performTextInput("Skin flare")
        composeRule.onNodeWithTag("outbreak-body-location").performTextInput("Arm")
        composeRule.onNodeWithTag("outbreak-body-location-create-inline").performClick()
        composeRule.onNodeWithTag("outbreak-save").performClick()

        waitForText("Skin flare")
        composeRule.onNodeWithText("Skin flare").performClick()
        waitForTag("record-detail-delete")
        composeRule.onNodeWithTag("record-detail-delete").performClick()
        composeRule.onNodeWithTag("edit-outbreak-delete").performClick()
        composeRule.onNodeWithTag("edit-outbreak-delete-confirm").performClick()

        composeRule.onNodeWithContentDescription("Add record").performClick()
        composeRule.onNodeWithText("Outbreak").performClick()
        composeRule.onNodeWithTag("outbreak-descriptor").performTextInput("Skin flare")
        composeRule.onNodeWithText("Arm").performClick()
        composeRule.onNodeWithTag("outbreak-body-location-clear-edit").performClick()
        composeRule.onNodeWithTag("edit-event-catalog-retire").performClick()
        composeRule.onNodeWithTag("edit-event-catalog-retire-message").assertIsDisplayed()
        composeRule.onNodeWithTag("edit-event-catalog-retire-confirm").assertIsDisplayed().performClick()

        composeRule.onNodeWithTag("create-outbreak-form-screen").assertIsDisplayed()
        composeRule.onAllNodesWithText("Saved body location selected: Arm").assertCountEquals(0)
        composeRule.onAllNodesWithTag("outbreak-body-location-clear-edit").assertCountEquals(0)
        composeRule.onNodeWithTag("outbreak-form-message")
            .assertTextContains("Saved body location was retired. Review the text value before saving.")
        composeRule.onNodeWithTag("outbreak-body-location").assertTextContains("Arm")

        composeRule.onNodeWithTag("outbreak-save").performClick()

        waitForTag("records-list")
        composeRule.onNodeWithText("Outbreak").assertIsDisplayed()
        composeRule.onNodeWithText("Skin flare").assertIsDisplayed()
        composeRule.onNodeWithText("Intensity 3 - Arm").assertIsDisplayed()
    }

    @Test
    fun event_catalog_retirement_shows_blocked_message_when_record_still_references_it() {
        composeRule.onNodeWithContentDescription("Add record").performClick()
        composeRule.onNodeWithText("Exposure").performClick()
        composeRule.onNodeWithTag("exposure-type").performTextInput("Pollen")
        composeRule.onNodeWithTag("exposure-type-create-inline").performClick()
        composeRule.onNodeWithTag("exposure-save").performClick()
        composeRule.onNodeWithText("Pollen").performClick()
        composeRule.onNodeWithTag("record-detail-edit").performClick()
        composeRule.onNodeWithTag("edit-exposure-type-edit").performClick()

        composeRule.onNodeWithTag("edit-event-catalog-retire").performClick()
        composeRule.onNodeWithTag("edit-event-catalog-retire-message").assertIsDisplayed()
        composeRule.onAllNodesWithTag("edit-event-catalog-retire-confirm").assertCountEquals(0)
        composeRule.onNodeWithTag("edit-event-catalog-retire-dismiss").performClick()
        composeRule.onNodeWithTag("edit-event-catalog-form-screen").assertIsDisplayed()
    }

    @Test
    fun outbreak_record_can_be_edited_and_deleted_offline_end_to_end() {
        composeRule.onNodeWithContentDescription("Add record").performClick()
        composeRule.onNodeWithText("Outbreak").performClick()

        composeRule.onNodeWithTag("outbreak-descriptor").performTextInput("Skin flare")
        composeRule.onNodeWithTag("outbreak-body-location").performTextInput("Arm")
        composeRule.onNodeWithTag("outbreak-save").performClick()

        waitForText("Skin flare")
        composeRule.onNodeWithText("Skin flare").performClick()
        waitForTag("record-detail-edit")
        composeRule.onNodeWithTag("record-detail-edit").performClick()
        assertOfflineFormHasNoDependencyWarnings("edit-outbreak-form-screen")

        composeRule.onNodeWithTag("edit-outbreak-descriptor").performTextReplacement("Skin rash")
        composeRule.onNodeWithTag("edit-outbreak-body-location").performTextReplacement("Left arm")
        composeRule.onNodeWithTag("edit-outbreak-save").performClick()

        waitForTag("records-list")
        composeRule.onNodeWithText("Skin rash").assertIsDisplayed()
        composeRule.onAllNodesWithText("Skin flare").assertCountEquals(0)

        composeRule.onNodeWithText("Skin rash").performClick()
        composeRule.onNodeWithTag("record-detail-delete").performClick()
        composeRule.onNodeWithTag("edit-outbreak-delete").performClick()
        composeRule.onNodeWithTag("edit-outbreak-delete-confirm").performClick()

        composeRule.onNodeWithTag("records-empty-state").assertIsDisplayed()
        composeRule.onAllNodesWithText("Skin rash").assertCountEquals(0)
    }

    @Test
    fun detail_back_navigation_preserves_active_filters_in_real_nav_graph() {
        composeRule.onNodeWithContentDescription("Add record").performClick()
        composeRule.onNodeWithText("Ingestion").performClick()
        composeRule.onNodeWithTag("ingestion-item-name").performTextInput("Tea")
        composeRule.onNodeWithTag("ingestion-quantity-2").performClick()
        composeRule.onNodeWithTag("ingestion-save").performClick()

        composeRule.onNodeWithContentDescription("Add record").performClick()
        composeRule.onNodeWithText("Exposure").performClick()
        composeRule.onNodeWithTag("exposure-type").performTextInput("Pollen")
        composeRule.onNodeWithTag("exposure-save").performClick()

        composeRule.onNodeWithTag("records-filter-action").performClick()
        composeRule.onNodeWithTag("records-filter-type-ingestion").performClick()
        composeRule.onNodeWithTag("records-filter-apply").performClick()

        composeRule.onNodeWithText("Tea").performClick()
        composeRule.onNodeWithTag("record-detail-screen").assertIsDisplayed()
        composeRule.onNodeWithTag("record-detail-back").performClick()

        composeRule.onNodeWithTag("records-list").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Active filter: Types: Ingestion").assertIsDisplayed()
        composeRule.onNodeWithText("Tea").assertIsDisplayed()
        composeRule.onAllNodesWithText("Pollen").assertCountEquals(0)
    }

    private fun assertNavigatesToForm(optionLabel: String, destinationTitle: String) {
        composeRule.onNodeWithContentDescription("Add record").performClick()
        composeRule.onNodeWithText(optionLabel).performClick()

        val destinationTag = when (optionLabel) {
            "Ingestion" -> "create-ingestion-form-screen"
            "Exposure" -> "create-exposure-form-screen"
            else -> "create-outbreak-form-screen"
        }

        composeRule.onNodeWithText(destinationTitle).assertIsDisplayed()
        composeRule.onNodeWithTag(destinationTag).assertIsDisplayed()
        pressBack()
    }

    private fun assertCreateFormShowsOfflineNotice(optionLabel: String, destinationTag: String) {
        composeRule.onNodeWithContentDescription("Add record").performClick()
        composeRule.onNodeWithText(optionLabel).performClick()

        composeRule.onNodeWithTag(destinationTag).assertIsDisplayed()
        assertOfflineFormHasNoDependencyWarnings(destinationTag)

        pressBack()
    }

    private fun assertOfflineFormHasNoDependencyWarnings(destinationTag: String) {
        composeRule.onNodeWithTag(destinationTag).assertIsDisplayed()
        composeRule.onNodeWithTag("local-only-notice").assertIsDisplayed()
        composeRule.onNodeWithText("Works offline. Saved only on this device.").assertIsDisplayed()
        when {
            destinationTag.startsWith("create-ingestion") -> composeRule.onAllNodesWithText("Couldn't save ingestion locally. Try again.").assertCountEquals(0)
            destinationTag.startsWith("create-exposure") -> composeRule.onAllNodesWithText("Couldn't save exposure locally. Try again.").assertCountEquals(0)
            destinationTag.startsWith("create-outbreak") -> composeRule.onAllNodesWithText("Couldn't save outbreak locally. Try again.").assertCountEquals(0)
            destinationTag.startsWith("edit-ingestion") -> composeRule.onAllNodesWithText("Couldn't update ingestion locally. Try again.").assertCountEquals(0)
            destinationTag.startsWith("edit-exposure") -> composeRule.onAllNodesWithText("Couldn't update exposure locally. Try again.").assertCountEquals(0)
            destinationTag.startsWith("edit-outbreak") -> composeRule.onAllNodesWithText("Couldn't update outbreak locally. Try again.").assertCountEquals(0)
        }
    }

    private fun waitForTag(tag: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
