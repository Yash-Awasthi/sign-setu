package app.signsetu.ui

import app.signsetu.ui.components.BrandButton
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.signsetu.ui.theme.Baloo
import app.signsetu.ui.theme.LocalInk
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
fun LockScreen(
    onUnlock: (onFailed: () -> Unit) -> Unit,
    /** False on a device that has served as a second screen: only an explicit tap opens the database (D-26). */
    autoPrompt: Boolean = true,
    stageButton: (@Composable () -> Unit)? = null,
) {
    val context = LocalContext.current
    var secure by remember { mutableStateOf(isDeviceSecure(context)) }
    var failed by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        secure = isDeviceSecure(context)
        onPauseOrDispose { }
    }
    LaunchedEffect(secure) { if (secure && autoPrompt) onUnlock { failed = true } }

    val ink = LocalInk.current
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().padding(horizontal = Spacing.xl, vertical = Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
                Text(
                    stringResource(R.string.app_name),
                    fontFamily = Baloo,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 22.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.weight(1f))
                Box(Modifier.size(72.dp).background(ink.brush, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Lock, contentDescription = null, tint = ink.onBand, modifier = Modifier.size(28.dp))
                }
                Text(
                    stringResource(if (secure) R.string.lock_title else R.string.no_lock_title),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = Spacing.lg, bottom = Spacing.sm),
                )
                Text(
                    stringResource(if (secure) R.string.lock_body else R.string.no_lock_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                if (failed && secure) {
                    Text(stringResource(R.string.lock_failed), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center, modifier = Modifier.padding(top = Spacing.sm))
                }
                Spacer(Modifier.weight(1f))
                BrandButton(
                    onClick = {
                        if (secure) {
                            failed = false
                            onUnlock { failed = true }
                        } else {
                            context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) { Text(stringResource(if (secure) R.string.lock_button else R.string.no_lock_button), style = MaterialTheme.typography.titleMedium) }
                Spacer(Modifier.height(Spacing.sm))
                if (secure) stageButton?.invoke()
                Text(
                    stringResource(R.string.notice_aid),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = Spacing.lg),
                )
    }
}

private fun isDeviceSecure(context: Context) =
    (context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isDeviceSecure
