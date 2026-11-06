package com.kindose.app.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.kindose.app.ui.theme.KinDoseTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Instrumented Compose UI & Accessibility verification for [MainScreen]. */
class MainScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun setup() {
        composeTestRule.setContent {
            KinDoseTheme {
                MainScreen()
            }
        }
    }

    @Test
    fun seniorApplianceScreen_displaysPrimaryMedicationAndGiantConfirmAction() {
        composeTestRule.onNodeWithText("Lisinopril (10 mg)").assertExists()
        composeTestRule.onNodeWithText("I TOOK MY 8:00 AM PILL").assertExists()
    }
}
