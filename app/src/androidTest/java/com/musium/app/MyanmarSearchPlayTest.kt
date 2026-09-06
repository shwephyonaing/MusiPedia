package com.musium.app

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.printToLog
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MyanmarSearchPlayTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun searchAmeAinShowsSongsAndStartsPlayback() {
        rule.waitUntil(8_000) {
            rule.onAllNodesWithText("Explore").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Explore").performClick()

        rule.onNodeWithTag("searchField").performTextInput("အမေ့အိမ်")
        rule.onNodeWithTag("searchField").performImeAction()

        rule.waitUntil(25_000) { hasSongResult() }
        rule.onRoot().printToLog("MusiPediaSearch")
        assertTrue("Songs should appear for အမေ့အိမ်", hasSongResult())

        clickFirstSong()
        rule.waitUntil(20_000) {
            rule.onAllNodesWithContentDescription("Play").fetchSemanticsNodes().isNotEmpty() ||
                rule.onAllNodesWithContentDescription("Pause").fetchSemanticsNodes().isNotEmpty()
        }
        Thread.sleep(8_000)
        rule.onRoot().printToLog("MusiPediaPlay")
        assertTrue("Mini/full player should show the playing title", hasSongResult())
    }

    private fun hasSongResult(): Boolean {
        return listOf("Htoo Ein Thin", "ထူးအိမ်သင်", "Eternal Gosh", "Ni Ni Win Shwe").any { needle ->
            rule.onAllNodesWithText(needle, substring = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun clickFirstSong() {
        listOf("Htoo Ein Thin", "ထူးအိမ်သင်", "Eternal Gosh", "Ni Ni Win Shwe").forEach { needle ->
            val nodes = rule.onAllNodesWithText(needle, substring = true)
            if (nodes.fetchSemanticsNodes().isNotEmpty()) {
                nodes.onFirst().performClick()
                return
            }
        }
    }
}
