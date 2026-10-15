package app.signsetu.ui.start

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.signsetu.R
import app.signsetu.data.content.ContentBundle
import app.signsetu.data.session.Profile
import app.signsetu.data.session.Session
import app.signsetu.data.session.SessionRepository
import app.signsetu.ui.theme.Spacing
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StartScreen(
    repo: SessionRepository,
    content: ContentBundle,
    onOpenSession: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableStateOf(0) }
    val open by produceState<Session?>(null, refresh) { value = repo.openSession() }
    var profile by rememberSaveable { mutableStateOf<Profile?>(null) }
    var lang by rememberSaveable { mutableStateOf("hi") }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = { LargeTopAppBar(title = { Text(stringResource(R.string.start_title)) }, scrollBehavior = scroll) },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Button(
                    onClick = {
                        val p = profile ?: return@Button
                        scope.launch { onOpenSession(repo.start(p, lang)) }
                    },
                    enabled = profile != null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = Spacing.lg, vertical = Spacing.md)
                        .heightIn(min = 56.dp),
                ) { Text(stringResource(R.string.start_button), style = MaterialTheme.typography.titleMedium) }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            open?.let { session ->
                ResumeCard(
                    session = session,
                    languageName = content.language(session.subjectLang)?.name ?: session.subjectLang,
                    onResume = { onOpenSession(session.id) },
                    onEnd = { scope.launch { repo.purge(session.id); refresh++ } },
                )
            }

            Text(
                stringResource(R.string.start_who),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Profile.entries.forEach { p ->
                    ProfileOption(p, selected = profile == p, onSelect = { profile = p })
                }
            }

            Text(
                stringResource(R.string.start_language),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            if (profile == Profile.OTHER_LANGUAGE) {
                Text(
                    stringResource(R.string.start_language_finder),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LanguagePicker(content, lang, onPick = { lang = it })
            }

            Text(
                stringResource(R.string.notice_aid),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = Spacing.sm),
            )
            Spacer(Modifier.height(Spacing.lg))
        }
    }
}

@Composable
private fun ResumeCard(session: Session, languageName: String, onResume: () -> Unit, onEnd: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(stringResource(R.string.start_resume_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(
                    R.string.start_resume_body,
                    stringResource(profileTitle(session.profile)),
                    languageName,
                    DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(session.startedAt)),
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onEnd) { Text(stringResource(R.string.start_end)) }
                Button(onClick = onResume) { Text(stringResource(R.string.start_resume)) }
            }
        }
    }
}

@Composable
private fun ProfileOption(profile: Profile, selected: Boolean, onSelect: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (selected) colors.primaryContainer else colors.surfaceContainerLow,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) colors.primary else colors.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton),
    ) {
        Row(Modifier.padding(Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = null)
            Column(Modifier.padding(start = Spacing.md)) {
                Text(stringResource(profileTitle(profile)), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(profileDescription(profile)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguagePicker(content: ContentBundle, selected: String, onPick: (String) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val current = content.language(selected)
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = current?.let { "${it.name}  ·  ${it.english}" } ?: selected,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            content.languages.forEach { l ->
                DropdownMenuItem(
                    text = { Text("${l.name}  ·  ${l.english}") },
                    onClick = { onPick(l.code); expanded = false },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

fun profileTitle(p: Profile) = when (p) {
    Profile.YOUNG_CHILD -> R.string.profile_child
    Profile.ISL_USER -> R.string.profile_isl
    Profile.OTHER_LANGUAGE -> R.string.profile_other
    Profile.NOT_SURE -> R.string.profile_unsure
}

private fun profileDescription(p: Profile) = when (p) {
    Profile.YOUNG_CHILD -> R.string.profile_child_desc
    Profile.ISL_USER -> R.string.profile_isl_desc
    Profile.OTHER_LANGUAGE -> R.string.profile_other_desc
    Profile.NOT_SURE -> R.string.profile_unsure_desc
}

