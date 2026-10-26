package app.signsetu.ui.avatar

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import app.signsetu.R

/** The character's head in a circle, for profile tiles and headers; a still of assets/avatar's head view. */
@Composable
fun MiniFace(size: Dp, background: Color, modifier: Modifier = Modifier) {
    Image(
        painterResource(R.drawable.avatar_face),
        contentDescription = null,
        modifier = modifier.size(size).clip(CircleShape).background(background),
    )
}
