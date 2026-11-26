package app.signsetu

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.signsetu.data.content.ContentBundle
import app.signsetu.data.content.or
import app.signsetu.ui.components.LocalContentRepository
import app.signsetu.ui.session.FAMILY_ROLES
import app.signsetu.ui.session.FamilyCallCard
import app.signsetu.ui.theme.SignSetuTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The family card as a person uses it: every person is a picture with its word, and a tap picks that person. */
@RunWith(AndroidJUnit4::class)
class FamilyCallDeviceTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var content: ContentBundle
    private val called = mutableListOf<String>()

    @Before
    fun load() {
        content = runBlocking { (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as SignSetuApp).container.content.load() }
    }

    private fun label(id: String) = content.cards[id]?.label?.or("en") ?: id

    @Test
    fun everyPersonToCallIsAPictureWithItsWord() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as SignSetuApp
        rule.setContent {
            CompositionLocalProvider(LocalContentRepository provides app.container.content) {
                SignSetuTheme { FamilyCallCard(content, "en", { called += it }, Modifier.size(360.dp, 320.dp)) }
            }
        }
        for (role in FAMILY_ROLES) {
            rule.onNodeWithContentDescription(label(role)).assertExists()
        }
        rule.onNodeWithContentDescription(label("father")).performClick()
        rule.waitForIdle()
        assertEquals(listOf("father"), called)
    }
}
