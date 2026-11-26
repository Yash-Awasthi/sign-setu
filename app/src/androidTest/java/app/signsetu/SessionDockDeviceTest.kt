package app.signsetu

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.signsetu.ui.session.SessionDock
import app.signsetu.ui.session.SessionShape
import app.signsetu.ui.session.sessionShape
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/** The dock and the selector that chooses it, rendered at each width class without a second device. */
@RunWith(AndroidJUnit4::class)
class SessionDockDeviceTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun config(widthDp: Int, heightDp: Int, landscape: Boolean) = Configuration().apply {
        screenWidthDp = widthDp
        screenHeightDp = heightDp
        orientation = if (landscape) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT
    }

    /** The same one-line decision SessionRoute makes: dock only when the shape says so. */
    @Composable
    private fun Arrangement(paired: Boolean, stage: @Composable () -> Unit, officer: @Composable () -> Unit) {
        val current = LocalConfiguration.current
        when (sessionShape(current.screenWidthDp, current.screenHeightDp, paired)) {
            SessionShape.DOCK -> SessionDock(stage = stage, officer = officer)
            SessionShape.PHONE, SessionShape.TABLET -> Box(Modifier.fillMaxSize().testTag("session.single")) { officer() }
        }
    }

    @Test
    fun landscapeDockPutsTheStageLeftOfTheOfficer() {
        rule.setContent {
            CompositionLocalProvider(LocalConfiguration provides config(1280, 800, landscape = true)) {
                SessionDock(
                    stage = { Box(Modifier.fillMaxSize().testTag("session.dock.stage.content")) },
                    officer = { Box(Modifier.fillMaxSize().testTag("session.dock.officer.content")) },
                )
            }
        }
        rule.onNodeWithTag("session.dock").assertExists()
        rule.onNodeWithTag("session.dock.stage").assertExists()
        rule.onNodeWithTag("session.dock.officer").assertExists()
        val stage = rule.onNodeWithTag("session.dock.stage").getUnclippedBoundsInRoot()
        val officer = rule.onNodeWithTag("session.dock.officer").getUnclippedBoundsInRoot()
        assertTrue(
            "stage right ${stage.right} is not left of officer left ${officer.left}",
            stage.right.value <= officer.left.value + 2f,
        )
        assertTrue(
            "stage top ${stage.top} and officer top ${officer.top} differ",
            abs(stage.top.value - officer.top.value) <= 2f,
        )
        assertTrue(
            "stage ${stage.right - stage.left} is not wider than officer ${officer.right - officer.left}",
            (stage.right - stage.left) > (officer.right - officer.left),
        )
    }

    @Test
    fun phonePortraitDoesNotDock() {
        rule.setContent {
            CompositionLocalProvider(LocalConfiguration provides config(360, 800, landscape = false)) {
                Arrangement(
                    paired = false,
                    stage = { Box(Modifier.fillMaxSize().testTag("session.single.stage")) },
                    officer = { Box(Modifier.fillMaxSize().testTag("session.single.officer")) },
                )
            }
        }
        rule.onNodeWithTag("session.single").assertExists()
        rule.onNodeWithTag("session.single.officer").assertExists()
        rule.onAllNodesWithTag("session.dock").assertCountEquals(0)
    }

    @Test
    fun pairedTabletPortraitDoesNotDock() {
        rule.setContent {
            CompositionLocalProvider(LocalConfiguration provides config(800, 1280, landscape = false)) {
                Arrangement(
                    paired = true,
                    stage = { Box(Modifier.fillMaxSize().testTag("session.single.stage")) },
                    officer = { Box(Modifier.fillMaxSize().testTag("session.single.officer")) },
                )
            }
        }
        rule.onNodeWithTag("session.single").assertExists()
        rule.onAllNodesWithTag("session.dock").assertCountEquals(0)
    }
}
