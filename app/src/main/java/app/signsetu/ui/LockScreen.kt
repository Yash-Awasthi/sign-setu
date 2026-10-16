package app.signsetu.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.signsetu.ui.avatar.Avatar
import app.signsetu.ui.avatar.Character
import app.signsetu.ui.session.Backdrop
import app.signsetu.ui.theme.Baloo
import app.signsetu.ui.theme.LocalInk
import app.signsetu.ui.theme.LocalStageColors
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.signsetu.R
import app.signsetu.ui.theme.Spacing

/** Shown whenever the database is closed. Without a secure lock screen the app refuses to store anything (SEC-20). */
@Composable
fun LockScreen(onUnlock: (onFailed: () -> Unit) -> Unit) {
    val context = LocalContext.current
    var secure by remember { mutableStateOf(isDeviceSecure(context)) }
    var failed by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        secure = isDeviceSecure(context)
        onPauseOrDispose { }
    }
    LaunchedEffect(secure) { if (secure) onUnlock { failed = true } }

    val ink = LocalInk.current
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Box(Modifier.weight(0.42f).fillMaxWidth()) {
            Backdrop(Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().statusBarsPadding()) {
                Avatar(Character.CHILD, gesture = null, playKey = null, modifier = Modifier.fillMaxHeight(0.8f).aspectRatio(1f).align(Alignment.BottomCenter))
                Text(
                    stringResource(R.string.app_name),
                    fontFamily = Baloo,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 24.sp,
                    color = LocalStageColors.current.title,
                    modifier = Modifier.padding(start = Spacing.lg, top = Spacing.md),
                )
            }
        }
        Surface(
            color = MaterialTheme.colorScheme.background,
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
            shadowElevation = 8.dp,
            modifier = Modifier.weight(0.58f).fillMaxWidth().offset(y = (-24).dp),
        ) {
            Column(
                Modifier.navigationBarsPadding().padding(horizontal = Spacing.xl, vertical = Spacing.xl),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Surface(shape = CircleShape, color = ink.band, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Filled.Lock, contentDescription = null, tint = ink.chipSelected, modifier = Modifier.padding(14.dp))
                }
                Text(
                    stringResource(if (secure) R.string.lock_title else R.string.no_lock_title),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                )
                Text(
                    stringResource(if (secure) R.string.lock_body else R.string.no_lock_body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                if (failed && secure) {
                    Text(stringResource(R.string.lock_failed), color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = {
                        if (secure) {
                            failed = false
                            onUnlock { failed = true }
                        } else {
                            context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
                        }
                    },
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier.fillMaxWidth().height(60.dp),
                ) { Text(stringResource(if (secure) R.string.lock_button else R.string.no_lock_button), style = MaterialTheme.typography.titleMedium) }
                Text(
                    stringResource(R.string.notice_aid),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private fun isDeviceSecure(context: Context) =
    (context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isDeviceSecure
