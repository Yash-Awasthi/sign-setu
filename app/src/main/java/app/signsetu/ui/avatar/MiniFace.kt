package app.signsetu.ui.avatar

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import app.signsetu.R

/** The character's head in a circle, for profile tiles and headers: the drawn character's head while the drawn one is
 * in use (chosen, or 3D not running), otherwise a still of assets/avatar's head view. */
@Composable
fun MiniFace(size: Dp, background: Color, modifier: Modifier = Modifier) {
    val drawn = AvatarRenderer.style.value == AvatarStyle.FLAT || AvatarRenderer.vrmFailed.value || !AvatarRenderer.webViewAvailable()
    if (drawn) {
        Box(modifier.size(size).clip(CircleShape).background(background)) {
            FlatAvatar(gesture = null, playKey = Unit, idle = false, view = AvatarView.Head, modifier = Modifier.fillMaxSize())
        }
    } else {
        Image(
            painterResource(R.drawable.avatar_face),
            contentDescription = null,
            modifier = modifier.size(size).clip(CircleShape).background(background),
        )
    }
}
