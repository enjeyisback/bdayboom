package app.bdayboom

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real screens on a real phone, driven through the semantics tree (what TalkBack reads).
 * Uses the debug app's own database, which is cleared before each test. Never the user's release app.
 */
@RunWith(AndroidJUnit4::class)
class UiFlowsTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val graph get() = InstrumentationRegistry.getInstrumentation().targetContext.graph

    @Before fun clean() = runBlocking {
        graph.repo.deleteAllNow()
        graph.settings.setReminderTime(23, 59) // tests add people "today": don't notify the phone's owner
        graph.settings.setCheckupSeen()
        graph.settings.setDailySummary(false) // no morning summary popping up mid-test
        rule.waitForIdle()
    }

    private fun waitForText(text: String, substring: Boolean = false) =
        rule.waitUntil(5_000) { rule.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty() }

    private fun waitForPerson(name: String) =
        rule.waitUntil(5_000) {
            rule.onAllNodes(hasContentDescription(name, substring = true) or hasText(name, substring = true)).fetchSemanticsNodes().isNotEmpty()
        }

    private fun openAdd() {
        rule.onNodeWithContentDescription("Add birthday").performClick()
        waitForText("New celebration")
    }

    private fun nameField(): SemanticsNodeInteraction = rule.onAllNodes(hasSetTextAction())[0]

    private fun save() {
        rule.onNodeWithText("Save and remind me").performScrollTo().performClick()
    }

    // F01-H1
    @Test fun addSomeoneShowsThemOnHome() {
        openAdd()
        nameField().performTextInput("Asha Rao")
        save()
        waitForPerson("Asha Rao")
        assertEquals(1, graph.repo.peopleNow().size)
    }

    // F01-E1
    @Test fun blankNameIsRefused() {
        openAdd()
        save()
        waitForText("Add a name")
        assertEquals(0, graph.repo.peopleNow().size)
    }

    // F01-E2 + F01-E3
    @Test fun longNameWithEmojiAndAccentsIsKeptExactly() {
        val name = "Zoë 🎂 Ñandú " + "x".repeat(48)
        openAdd()
        nameField().performTextInput(name)
        save()
        rule.waitUntil(5_000) { graph.repo.peopleNow().isNotEmpty() }
        assertEquals(name, graph.repo.peopleNow().single().name)
    }

    // F01-E7
    @Test fun doubleTapOnSaveSavesOnce() {
        openAdd()
        nameField().performTextInput("Twice")
        rule.onNodeWithText("Save and remind me").performScrollTo()
        val save = rule.onNodeWithText("Save and remind me")
        save.performClick()
        runCatching { save.performClick() } // the screen may already be gone; a second tap must not add a copy
        rule.waitUntil(5_000) { graph.repo.peopleNow().isNotEmpty() }
        Thread.sleep(800)
        assertEquals(1, graph.repo.peopleNow().size)
    }

    // F06-E1
    @Test fun searchWithNoMatchSaysSoAndCanReset() {
        runBlocking { graph.repo.save(app.bdayboom.data.Person("", app.bdayboom.data.EntryType.Birthday, "Meera", 1, 1, null, null, "", true)) }
        waitForPerson("Meera")
        rule.onAllNodes(hasSetTextAction())[0].performTextInput("zzzz")
        waitForText("No one matches")
        rule.onNodeWithText("Show everyone").performClick()
        waitForPerson("Meera")
    }

    // F04-E1
    @Test fun emptyCalendarDayOffersToAddOnThatDay() {
        rule.onAllNodesWithText("Calendar")[0].performClick()
        waitForText("Month view".uppercase())
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Add on this day"))
        rule.onNodeWithText("Nothing on this day").assertIsDisplayed()
        rule.onNodeWithText("Add on this day").performClick()
        waitForText("New celebration")
    }

    // F07-H1
    @Test fun deleteFromDetailCanBeUndone() {
        runBlocking { graph.repo.save(app.bdayboom.data.Person("", app.bdayboom.data.EntryType.Birthday, "Kabir", 3, 3, 1990, null, "", true)) }
        waitForPerson("Kabir")
        rule.onAllNodes(hasContentDescription("Kabir", substring = true) and hasClickAction())[0].performClick()
        waitForText("Details")
        rule.onNodeWithContentDescription("Delete").performClick()
        rule.onNodeWithText("Remove").performClick()
        waitForText("Undo")
        rule.onNodeWithText("Undo").performClick()
        rule.waitUntil(5_000) { graph.repo.peopleNow().any { it.name == "Kabir" } }
    }

    // A11Y-1: every tappable thing on Home, Add and Settings says what it is
    @Test fun everyTappableElementHasALabel() {
        runBlocking { graph.repo.save(app.bdayboom.data.Person("", app.bdayboom.data.EntryType.Birthday, "Label Check", 4, 4, null, null, "", true)) }
        waitForPerson("Label Check")
        val unlabeled = mutableListOf<String>()
        fun check(screen: String) {
            rule.waitForIdle()
            rule.onAllNodes(hasClickAction() or hasSetTextAction()).fetchSemanticsNodes().forEach { n ->
                val c = n.config
                val label = c.getOrNull(SemanticsProperties.ContentDescription)?.joinToString("").orEmpty() +
                    c.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }.orEmpty() +
                    c.getOrNull(SemanticsProperties.EditableText)?.text.orEmpty()
                val childText = n.children.joinToString("") { ch ->
                    ch.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }.orEmpty() +
                        ch.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString("").orEmpty()
                }
                if ((label + childText).isBlank()) unlabeled += "$screen: node ${n.id} at ${n.boundsInRoot}"
            }
        }
        check("Home")
        openAdd(); check("Add")
        assertTrue("Unlabeled controls:\n" + unlabeled.joinToString("\n"), unlabeled.isEmpty())
    }

    // Wishes: pick a ready wish for someone, then save it as your own (name turned back into {name})
    @Test fun pickAReadyWishAndSaveItAsYourOwn() {
        runBlocking { graph.repo.save(app.bdayboom.data.Person("", app.bdayboom.data.EntryType.Birthday, "Kabir Mehta", 1, 1, null, null, "", true)) }
        waitForPerson("Kabir")
        rule.onAllNodes(hasContentDescription("Kabir", substring = true) or hasText("Kabir Mehta"))[0].performClick()
        waitForText("Send wish")
        rule.onAllNodesWithText("Send wish")[0].performClick()
        waitForText("Send a wish")

        val ready = "Happy birthday, Kabir! 🎉 Wishing you a day as fun as you are."
        rule.onNodeWithText(ready).performScrollTo().performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText(ready).fetchSemanticsNodes().size == 2 } // card + message box
        rule.onNodeWithText("Save to my wishes").performScrollTo().performClick()
        val template = "Happy birthday, {name}! 🎉 Wishing you a day as fun as you are."
        rule.waitUntil(5_000) { graph.settings.now().myWishes.contains(template) }
        runBlocking { graph.settings.removeWish(template) }
    }

    // Today's card: "Send wish" turns into "Sent" once they've been wished today
    @Test fun spotlightShowsSentAfterWishing() {
        val today = java.time.LocalDate.now()
        runBlocking { graph.repo.save(app.bdayboom.data.Person("", app.bdayboom.data.EntryType.Birthday, "Riya Sen", today.monthValue, today.dayOfMonth, null, null, "", true)) }
        waitForPerson("Riya")
        waitForText("Send wish")
        val id = runBlocking { graph.repo.peopleNow().single().id }
        runBlocking { graph.settings.markWished(id, today) }
        waitForText("Sent")
        waitForText("Wish sent. Nice one.")
    }

    // Store reviews: a sideways flick while scrolling deleted people. Rows don't delete on swipe any more.
    @Test fun swipingARowDoesNotDeleteAnyone() {
        runBlocking { graph.repo.save(app.bdayboom.data.Person("", app.bdayboom.data.EntryType.Birthday, "Dev Patel", 6, 15, null, null, "", true)) }
        waitForPerson("Dev Patel")
        rule.onAllNodes(hasContentDescription("Dev Patel", substring = true))[0].performTouchInput { swipeLeft() }
        rule.waitForIdle()
        Thread.sleep(500)
        assertEquals(1, graph.repo.peopleNow().size)
    }
}
