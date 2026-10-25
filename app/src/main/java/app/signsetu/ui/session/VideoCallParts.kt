package app.signsetu.ui.session

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.signsetu.R
import app.signsetu.ui.theme.LocalCall
import app.signsetu.ui.components.CardImage
import app.signsetu.ui.theme.Baloo

@Composable
internal fun CalmingBreathingGuide(onDismiss: () -> Unit) {
    val breathScale by rememberInfiniteTransition(label = "breathCycle").animateFloat(
        initialValue = 0.85f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breathScale",
    )

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = LocalCall.current.bg.copy(alpha = 0.98f),
        border = BorderStroke(2.dp, LocalCall.current.accent),
        shadowElevation = 16.dp,
        modifier = Modifier
            .padding(24.dp)
            .fillMaxWidth(0.92f),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.call_breath_title),
                    fontFamily = Baloo,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color.White,
                )
                Surface(
                    shape = CircleShape,
                    color = LocalCall.current.line,
                    modifier = Modifier
                        .size(32.dp)
                        .clickable(onClick = onDismiss),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close), tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(160.dp)
                    .padding(vertical = 8.dp),
            ) {
                Surface(shape = CircleShape, color = LocalCall.current.ink.copy(alpha = 0.25f), modifier = Modifier.size(150.dp * breathScale)) {}
                Surface(shape = CircleShape, color = LocalCall.current.accent.copy(alpha = 0.45f), modifier = Modifier.size(110.dp * breathScale)) {}
                Surface(
                    shape = CircleShape,
                    color = LocalCall.current.ink,
                    modifier = Modifier.size(72.dp),
                    shadowElevation = 6.dp,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(if (breathScale > 1.05f) R.string.call_exhale else R.string.call_inhale),
                            fontFamily = Baloo,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color.White,
                        )
                    }
                }
            }

            Text(
                text = stringResource(R.string.call_breath_body),
                textAlign = TextAlign.Center,
                fontSize = 14.sp,
                color = LocalCall.current.text,
            )

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = LocalCall.current.ink,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onDismiss),
            ) {
                Text(
                    text = stringResource(R.string.call_ready),
                    fontFamily = Baloo,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }
        }
    }
}

@Composable
internal fun CallActionButton(icon: String, label: String, backgroundColor: Color, highlight: Boolean = false, onClick: () -> Unit) {
    val pulse = if (highlight) rememberInfiniteTransition(label = "guide").animateFloat(1f, 1.18f, infiniteRepeatable(tween(600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse").value else 1f
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .clickable(onClick = onClick)
            .semantics { role = Role.Button; contentDescription = label },
    ) {
        Surface(
            shape = CircleShape,
            color = backgroundColor,
            border = if (highlight) BorderStroke(3.dp, Color.White) else null,
            modifier = Modifier.size(50.dp).scale(pulse),
            shadowElevation = 4.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                when (icon) {
                    "yes" -> Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
                    "no" -> Icon(Icons.Filled.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
                    "express" -> Icon(Icons.Filled.Favorite, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                    "breathe" -> Text("🧘", fontSize = 26.sp)
                    else -> CardImage(icon, Modifier.size(28.dp))
                }
            }
        }
        Text(
            text = label,
            fontFamily = Baloo,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = LocalCall.current.text,
        )
    }
}
