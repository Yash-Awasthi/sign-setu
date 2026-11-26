package app.signsetu.ui.session

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.signsetu.ui.theme.Space
import app.signsetu.ui.theme.Radius

/** One phone arrangement for every session, except a paired tablet held in landscape, which docks the pair. */
internal enum class SessionShape { PHONE, TABLET, DOCK }

/**
 * A dock is a paired phone in landscape: the second screen carries the stage, so this screen holds both sides at
 * once. An unpaired landscape phone is a tablet, not a dock.
 */
internal fun sessionShape(widthDp: Int, heightDp: Int, paired: Boolean): SessionShape = when {
    paired && widthDp > heightDp -> SessionShape.DOCK
    widthDp >= 600 -> SessionShape.TABLET
    else -> SessionShape.PHONE
}

/** The stage and the officer side by side: 0.62 of the width for the stage, the rest for the officer pane. */
@Composable
internal fun SessionDock(
    stage: @Composable () -> Unit,
    officer: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(Space.sm),
    ) {
        Surface(
            modifier = Modifier
                .weight(0.62f)
                .fillMaxHeight()
                .testTag("session.dock"),
            shape = RoundedCornerShape(Radius.r24),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Box(Modifier.fillMaxSize().testTag("session.dock.stage")) { stage() }
        }
        Box(
            Modifier
                .fillMaxHeight()
                .width(Space.hair)
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
        Box(
            Modifier
                .weight(0.38f)
                .fillMaxHeight()
                .testTag("session.dock.officer"),
        ) { officer() }
    }
}
