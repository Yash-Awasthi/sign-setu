package app.signsetu.ui.start

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.signsetu.R
import app.signsetu.ui.theme.Spacing

private class Step(@StringRes val title: Int, @StringRes val body: Int, val icon: @Composable () -> Painter)

private val STEPS = listOf(
    Step(R.string.intro_aid_title, R.string.intro_aid_body) { rememberVectorPainter(Icons.Filled.Info) },
    Step(R.string.intro_private_title, R.string.intro_private_body) { rememberVectorPainter(Icons.Filled.Lock) },
    Step(R.string.intro_pinning_title, R.string.intro_pinning_body) { painterResource(R.drawable.ic_pin) },
)

/** Shown once, before the first unlock (FR-61: first run guides the officer to turn on app pinning). */
@Composable
fun FirstRunScreen(onDone: () -> Unit) {
    var index by rememberSaveable { mutableIntStateOf(0) }
    val step = STEPS[index]
    val last = index == STEPS.lastIndex
    val context = LocalContext.current
    BackHandler(enabled = index > 0) { index-- }

    Scaffold(
        bottomBar = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    STEPS.indices.forEach { i ->
                        Box(
                            Modifier
                                .size(if (i == index) 10.dp else 8.dp)
                                .background(
                                    if (i == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                    CircleShape,
                                ),
                        )
                    }
                }
                if (index > 0) TextButton(onClick = { index-- }) { Text(stringResource(R.string.back)) }
                Button(
                    onClick = { if (last) onDone() else index++ },
                    modifier = Modifier.heightIn(min = 48.dp).padding(start = Spacing.sm),
                ) { Text(stringResource(if (last) R.string.intro_done else R.string.intro_next)) }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Spacer(Modifier.size(Spacing.xxl * 2))
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(72.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(step.icon(), contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(36.dp))
                }
            }
            Text(
                stringResource(R.string.intro_step, index + 1, STEPS.size),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                stringResource(step.title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(stringResource(step.body), style = MaterialTheme.typography.bodyLarge)
            if (last) {
                OutlinedButton(onClick = { context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS)) }) {
                    Text(stringResource(R.string.no_lock_button))
                }
            }
        }
    }
}
