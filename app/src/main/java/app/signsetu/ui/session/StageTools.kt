package app.signsetu.ui.session

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Face
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.signsetu.R
import app.signsetu.ui.theme.Baloo
import app.signsetu.ui.theme.LocalAnswerColors
import app.signsetu.ui.theme.LocalCall
import app.signsetu.ui.theme.Space
import app.signsetu.ui.theme.TypeSize
import app.signsetu.ui.theme.Radius

/** The five things the stage's tool bar offers. The key is also the test tag suffix. */
internal enum class StageTool(val key: String) {
    LANGUAGE("language"),
    PICTURES("pictures"),
    DRAWING("drawing"),
    EMOJI("emoji"),
    TYPING("typing"),
}

/** The three path groups the call offers at most: answers, tools and the way out. */
internal object StagePaths {
    const val ANSWERS = "stage.path.answers"
    const val TOOLS = "stage.path.tools"
    const val EXIT = "stage.path.exit"
    val all = listOf(ANSWERS, TOOLS, EXIT)
}

private fun StageTool.labelRes(): Int = when (this) {
    StageTool.LANGUAGE -> R.string.stage_tool_language
    StageTool.PICTURES -> R.string.stage_tool_pictures
    StageTool.DRAWING -> R.string.stage_tool_drawing
    StageTool.EMOJI -> R.string.stage_tool_emoji
    StageTool.TYPING -> R.string.stage_tool_typing
}

/** One horizontal bar: a single button per tool that is available for the item on screen. */
@Composable
internal fun StageToolBar(
    selected: StageTool?,
    available: Set<StageTool>,
    onSelect: (StageTool) -> Unit,
    modifier: Modifier = Modifier,
) {
    val call = LocalCall.current
    Row(
        modifier = modifier.fillMaxWidth().testTag("stage.toolbar"),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StageTool.entries.forEach { tool ->
            if (tool !in available) return@forEach
            val label = stringResource(tool.labelRes())
            val chosen = tool == selected
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Space.xs),
                modifier = Modifier
                    .testTag("stage.tool." + tool.key)
                    .widthIn(min = Space.s48)
                    .heightIn(min = Space.s48)
                    .clickable(role = Role.Button) { onSelect(tool) }
                    .padding(horizontal = Space.xs, vertical = Space.xxs)
                    .semantics { contentDescription = label },
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (chosen) call.accent else call.surface,
                    border = BorderStroke(Space.hair, if (chosen) call.accentDeep else call.line),
                    shadowElevation = Space.xs,
                    modifier = Modifier.size(Space.s44),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        val tint = if (chosen) call.bg else call.text
                        when (tool) {
                            StageTool.LANGUAGE -> Icon(painterResource(R.drawable.ic_translate), contentDescription = null, tint = tint, modifier = Modifier.size(Space.s22))
                            StageTool.PICTURES -> Icon(painterResource(R.drawable.ic_grid), contentDescription = null, tint = tint, modifier = Modifier.size(Space.s22))
                            StageTool.DRAWING -> Icon(Icons.Filled.Create, contentDescription = null, tint = tint, modifier = Modifier.size(Space.s22))
                            StageTool.EMOJI -> Icon(Icons.Filled.Face, contentDescription = null, tint = tint, modifier = Modifier.size(Space.s22))
                            StageTool.TYPING -> Icon(painterResource(R.drawable.ic_keyboard), contentDescription = null, tint = tint, modifier = Modifier.size(Space.s22))
                        }
                    }
                }
                Text(
                    text = label,
                    fontFamily = Baloo,
                    fontSize = TypeSize.s10,
                    fontWeight = FontWeight.Bold,
                    color = call.text,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** The one filled action of the stage: Yes, Done while signing, or Next on a ground rule. */
@Composable
internal fun StagePrimaryAction(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val call = LocalCall.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(Radius.r18),
        color = call.accent,
        border = BorderStroke(Space.xxs, call.accentDeep),
        shadowElevation = Space.s6,
        modifier = modifier.heightIn(min = Space.s52).testTag("stage.primary"),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().padding(horizontal = Space.md, vertical = Space.md)) {
            Text(
                text = label,
                fontFamily = Baloo,
                fontWeight = FontWeight.Bold,
                fontSize = TypeSize.s15,
                color = call.bg,
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** The one affordance that opens the unsure answers (do not know, do not understand, wrong). */
@Composable
internal fun StageMoreAnswers(expanded: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val answer = LocalAnswerColors.current
    val label = stringResource(R.string.stage_more_answers)
    Surface(
        onClick = onToggle,
        shape = Radius.pill,
        color = answer.unsure,
        border = if (expanded) BorderStroke(Space.xxs, answer.onUnsure) else null,
        modifier = modifier
            .heightIn(min = Space.s48)
            .testTag("stage.more")
            .semantics { contentDescription = label },
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().padding(horizontal = Space.sm, vertical = Space.s6)) {
            Text(label, fontFamily = Baloo, fontWeight = FontWeight.Bold, fontSize = TypeSize.s12, color = answer.onUnsure, maxLines = 2, textAlign = TextAlign.Center)
        }
    }
}
