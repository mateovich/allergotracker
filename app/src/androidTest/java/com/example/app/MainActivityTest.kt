package com.example.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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

        composeRule.onNodeWithText("Records").assertIsDisplayed()
        composeRule.onNodeWithTag("records-list").assertIsDisplayed()
        composeRule.onNodeWithText("Exposure").assertIsDisplayed()
        composeRule.onNodeWithText("Pollen").assertIsDisplayed()
        composeRule.onNodeWithText("Exposure recorded").assertIsDisplayed()
    }

    @Test
    fun exposure_flow_allows_manual_datetime_correction_with_inline_feedback() {
        composeRule.onNodeWithContentDescription("Add record").performClick()
        composeRule.onNodeWithText("Exposure").performClick()

        composeRule.onNodeWithTag("exposure-type").performTextInput("Pollen")
        composeRule.onNodeWithTag("exposure-occurred-at-input").performTextInput("invalid")
        composeRule.onNodeWithTag("exposure-save").performClick()

        composeRule.onNodeWithText("Enter date and time as yyyy-MM-dd HH:mm.").assertIsDisplayed()
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

        composeRule.onNodeWithText("Records").assertIsDisplayed()
        composeRule.onNodeWithTag("records-list").assertIsDisplayed()
        composeRule.onNodeWithText("Outbreak").assertIsDisplayed()
        composeRule.onNodeWithText("Skin flare").assertIsDisplayed()
        composeRule.onNodeWithText("Left arm").assertIsDisplayed()
    }

    @Test
    fun outbreak_flow_allows_manual_datetime_correction_with_inline_feedback() {
        composeRule.onNodeWithContentDescription("Add record").performClick()
        composeRule.onNodeWithText("Outbreak").performClick()

        composeRule.onNodeWithTag("outbreak-descriptor").performTextInput("Skin flare")
        composeRule.onNodeWithTag("outbreak-body-location").performTextInput("Left arm")
        composeRule.onNodeWithTag("outbreak-occurred-at-input").performTextInput("invalid")
        composeRule.onNodeWithTag("outbreak-save").performClick()

        composeRule.onNodeWithText("Enter date and time as yyyy-MM-dd HH:mm.").assertIsDisplayed()
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
}
