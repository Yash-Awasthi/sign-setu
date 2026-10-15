package app.signsetu.ui

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

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier.safeDrawingPadding().padding(Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.primaryContainer) {
                Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(Spacing.xl).size(40.dp))
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
            Button(
                onClick = {
                    if (secure) {
                        failed = false
                        onUnlock { failed = true }
                    } else {
                        context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
                    }
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) { Text(stringResource(if (secure) R.string.lock_button else R.string.no_lock_button)) }
            Text(
                stringResource(R.string.notice_aid),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun isDeviceSecure(context: Context) =
    (context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isDeviceSecure
