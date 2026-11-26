package app.signsetu

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.signsetu.data.content.ContentBundle
import app.signsetu.data.content.or
import app.signsetu.ui.session.Feeling
import app.signsetu.ui.session.FeelingPanel
import app.signsetu.ui.session.Mood
import app.signsetu.ui.theme.SignSetuTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The stage's feeling scale as a person uses it: point at a face, then a number. */
@RunWith(AndroidJUnit4::class)
class FeelingsDeviceTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var content: ContentBundle
    private val feelings = mutableListOf<Feeling>()

    @Before
    fun load() {
        content = runBlocking { (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as SignSetuApp).container.content.load() }
    }

    private fun label(id: String) = content.cards[id]?.label?.or("en") ?: id

    @Test
    fun aFaceThenAPainNumberIsSentEachTime() {
        rule.setContent {
            SignSetuTheme { FeelingPanel(content, "en", { feelings += it }, Modifier.size(360.dp, 420.dp)) }
        }
        val scared = rule.onNodeWithContentDescription(label("scared"))
        scared.assertExists()
        scared.performClick()
        rule.waitForIdle()
        assertEquals("the face alone is already an answer, at no pain", listOf(Feeling(Mood.SCARED, 0)), feelings)

        // The numbers appear only after a face, so a number always has the face it belongs to.
        val seven = rule.onNodeWithContentDescription(rule.activity.getString(R.string.feel_pain, 7))
        seven.assertExists()
        seven.performClick()
        rule.waitForIdle()
        assertEquals("the second tap adds the pain, not a new face", Feeling(Mood.SCARED, 7), feelings.last())
        assertEquals(2, feelings.size)
    }
}
