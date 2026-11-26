package app.signsetu.ui.session

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
