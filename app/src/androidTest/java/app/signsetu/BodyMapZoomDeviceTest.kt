package app.signsetu

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.signsetu.ui.session.BodyPart
import app.signsetu.ui.session.BodyTouch
import app.signsetu.ui.session.HurtMap
import app.signsetu.ui.session.bodyBox
import app.signsetu.ui.theme.SignSetuTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** A tap on the hand opens its close-up, and the second tap records the exact point rather than the coarse one. */
@RunWith(AndroidJUnit4::class)
class BodyMapZoomDeviceTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val touches = mutableListOf<BodyTouch>()

    @Test
    fun theHandCloseUpRecordsAnExactPointInsideTheHand() {
        rule.setContent { SignSetuTheme { HurtMap({ touches += it }, Modifier.size(300.dp, 600.dp)) } }
        val map = rule.onNodeWithContentDescription(rule.activity.getString(R.string.hurt_map))
        // The viewer's left hand, middle of the palm: a coarse touch on a small part.
        map.performTouchInput { click(Offset(width * 0.15f, height * 0.50f)) }
        rule.waitForIdle()
        assertEquals(1, touches.size)
        val coarse = touches[0]
        assertEquals(BodyPart.HAND, coarse.part)

        val closeUp = rule.onNodeWithContentDescription(rule.activity.getString(R.string.hurt_zoom, rule.activity.getString(R.string.part_hand)))
        closeUp.assertExists()
        closeUp.performTouchInput { click(Offset(width * 0.30f, height * 0.80f)) }
        rule.waitForIdle()
        assertEquals("the close-up corrects the touch instead of adding a second place", 2, touches.size)
        val refined = touches[1]
        assertEquals(BodyPart.HAND, refined.part)
        assertTrue("the refined point moved to where the finger went", refined.x != coarse.x || refined.y != coarse.y)
        val box = bodyBox(BodyPart.HAND, refined.x)!!
        assertTrue("the point kept is inside the hand, at ${refined.x},${refined.y}", box.contains(Offset(refined.x, refined.y)))
        assertEquals("the same side of the body as the first touch", coarse.personsLeft, refined.personsLeft)
    }
}
