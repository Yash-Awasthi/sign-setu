package app.signsetu.ui.session

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.signsetu.R
import app.signsetu.ui.theme.LocalCall

/** The height of one header tile; the stage keeps the Stop octagon clear below it. */
internal val CALL_TILE_HEIGHT = 48.dp

/** One participant on the call: their view, a name chip at the bottom-left, an accent edge while they sign. */
@Composable
internal fun CallTile(
    name: String,
    sub: String?,
    speaking: Boolean,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    val call = LocalCall.current
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = call.surface.copy(alpha = 0.92f),
        border = BorderStroke(if (speaking) 3.dp else 1.dp, if (speaking) call.accent else call.line),
        modifier = modifier.semantics { contentDescription = name },
    ) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = call.bg.copy(alpha = 0.72f),
                modifier = Modifier.align(Alignment.BottomStart).padding(4.dp),
            ) {
                Column(Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) {
                    Text(
                        name,
                        color = call.text,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    sub?.let {
                        Text(it, color = call.muted, fontSize = 8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

/** The two people on the line, side by side: the signing character and the person watching. */
@Composable
internal fun CallHeader(
    avatarName: String,
    personName: String,
    signing: Boolean,
    modifier: Modifier,
) {
    Row(
        modifier.fillMaxWidth().testTag("call.header"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CallTile(
            name = avatarName,
            sub = null,
            speaking = false,
            modifier = Modifier.weight(1f).height(CALL_TILE_HEIGHT).testTag("call.tile.avatar"),
        ) {
            Text("\uD83D\uDC64", fontSize = 20.sp)
        }
        CallTile(
            name = personName,
            sub = null,
            speaking = signing,
            modifier = Modifier.weight(1f).height(CALL_TILE_HEIGHT).testTag("call.tile.person"),
        ) {
            Text("\uD83D\uDCF9", fontSize = 18.sp)
        }
    }
}

/** The pill at the bottom of the call that says a pull up opens the whole toolset; it thickens as it is pulled. */
@Composable
internal fun SlidePanelHandle(
    onOpen: () -> Unit,
    dragFraction: Float,
    modifier: Modifier = Modifier,
) {
    val call = LocalCall.current
    val label = stringResource(R.string.call_slide_tools)
    val pull = dragFraction.coerceIn(0f, 1f)
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = call.surface.copy(alpha = 0.45f + 0.35f * pull),
        modifier = modifier
            .width(120.dp)
            .height(44.dp)
            .clickable(onClickLabel = label, role = Role.Button) { onOpen() }
            .testTag("call.slide")
            .semantics { contentDescription = label },
    ) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Canvas(Modifier.width(56.dp).height(10.dp)) {
                val stroke = 2.dp.toPx() + 2.dp.toPx() * pull
                drawLine(call.muted, Offset(size.width * 0.15f, size.height * 0.75f), Offset(size.width * 0.5f, size.height * 0.25f), stroke, StrokeCap.Round)
                drawLine(call.muted, Offset(size.width * 0.5f, size.height * 0.25f), Offset(size.width * 0.85f, size.height * 0.75f), stroke, StrokeCap.Round)
            }
            Text(stringResource(R.string.stage_sheet_title), color = call.text, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** Hold and messages, side by side at one level: two equal controls, neither hidden behind the other. */
@Composable
internal fun CallLevelRow(
    onHold: (() -> Unit)?,
    onMessages: (() -> Unit)?,
    holdSelected: Boolean,
    holdLabel: String,
    messagesLabel: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().testTag("call.levels"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LevelButton(holdLabel, "call.hold", onHold, holdSelected, Modifier.weight(1f))
        LevelButton(messagesLabel, "call.messages", onMessages, false, Modifier.weight(1f))
    }
}

/** One of the two call levels: same height, same padding, and a fill while it is the one in force. */
@Composable
private fun LevelButton(
    label: String,
    tag: String,
    onClick: (() -> Unit)?,
    selectedNow: Boolean,
    modifier: Modifier,
) {
    val call = LocalCall.current
    Surface(
        onClick = { onClick?.invoke() },
        enabled = onClick != null,
        shape = RoundedCornerShape(16.dp),
        color = if (selectedNow) call.accent else call.surface.copy(alpha = 0.9f),
        contentColor = if (selectedNow) call.bg else call.text,
        border = BorderStroke(1.dp, if (selectedNow) call.accent else call.line),
        modifier = modifier
            .height(56.dp)
            .testTag(tag)
            .semantics {
                role = Role.Button
                contentDescription = label
                selected = selectedNow
            },
    ) {
        Box(Modifier.fillMaxSize().padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
            Text(
                label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}
