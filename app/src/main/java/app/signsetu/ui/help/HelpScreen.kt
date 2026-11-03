package app.signsetu.ui.help

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.signsetu.R

private val Target = 56.dp

/**
 * In-app help: a searchable list of topics, each a list of short entries, and a guided tour.
 * [startTopic] opens that topic directly; Back then leaves help instead of returning to the list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(onBack: () -> Unit, startTopic: String? = null) {
    var open by rememberSaveable { mutableStateOf(HelpContent.topic(startTopic)?.id) }
    var fromList by rememberSaveable { mutableStateOf(false) }
    var step by rememberSaveable { mutableIntStateOf(0) }
    val topic = HelpContent.topic(open)

    val close: () -> Unit = {
        if (topic != null && fromList) { open = null; step = 0 } else onBack()
    }
    BackHandler(enabled = topic?.guided == true && step > 0) { step-- }
    BackHandler(enabled = topic != null && fromList && !(topic.guided && step > 0)) { close() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = {
                    Text(
                        stringResource(topic?.title ?: R.string.help_title),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.semantics { heading() },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = close, modifier = Modifier.size(Target)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).navigationBarsPadding()) {
            when {
                topic == null -> TopicList(onOpen = { open = it; fromList = true; step = 0 })
                topic.guided -> Tour(topic, step, onStep = { step = it }, onDone = close)
                else -> TopicPage(topic)
            }
        }
    }
}

@Composable
private fun TopicList(onOpen: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val res = LocalContext.current.resources
    val q = query.trim()
    // Each match keeps the headings of the entries that matched, so a search shows where the answer is.
    val matches = HelpContent.topics.mapNotNull { t ->
        if (q.isEmpty()) return@mapNotNull t to emptyList<String>()
        val inTopic = res.getString(t.title).contains(q, true) || res.getString(t.summary).contains(q, true)
        val hits = t.entries.filter { res.getString(it.heading).contains(q, true) || res.getString(it.body).contains(q, true) }
            .map { res.getString(it.heading) }
        if (inTopic || hits.isNotEmpty()) t to hits else null
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item(key = "search") {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                label = { Text(stringResource(R.string.help_search)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { query = "" }, modifier = Modifier.size(Target)) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.help_search_clear))
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).heightIn(min = Target),
            )
        }
        if (matches.isEmpty()) item(key = "none") {
            Text(
                stringResource(R.string.help_no_results),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp),
            )
        }
        items(matches, key = { it.first.id }) { (t, hits) ->
            ListItem(
                headlineContent = { Text(stringResource(t.title), style = MaterialTheme.typography.titleMedium) },
                supportingContent = {
                    Column {
                        Text(stringResource(t.summary))
                        hits.take(3).forEach { Text("· $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
                    }
                },
                trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
                modifier = Modifier.heightIn(min = Target).clickable { onOpen(t.id) },
            )
        }
    }
}

@Composable
private fun TopicPage(topic: HelpTopic) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp)) {
        item(key = "summary") {
            Text(
                stringResource(topic.summary),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        items(topic.entries, key = { it.heading }) { entry ->
            Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
                Text(
                    stringResource(entry.heading),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.semantics { heading() },
                )
                Text(stringResource(entry.body), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
private fun Tour(topic: HelpTopic, step: Int, onStep: (Int) -> Unit, onDone: () -> Unit) {
    val count = topic.entries.size
    val at = step.coerceIn(0, count - 1)
    val entry = topic.entries[at]
    val position = stringResource(R.string.help_step, at + 1, count)
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Text(position, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                stringResource(entry.heading),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(top = 8.dp).semantics { heading() },
            )
            Text(stringResource(entry.body), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 12.dp))
        }
        Row(
            Modifier.fillMaxWidth().padding(vertical = 16.dp).clearAndSetSemantics { contentDescription = position },
            horizontalArrangement = Arrangement.Center,
        ) {
            repeat(count) { i ->
                val colour = if (i == at) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                Box(Modifier.padding(4.dp).size(if (i == at) 10.dp else 8.dp).background(colour, CircleShape))
            }
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (at > 0) OutlinedButton(onClick = { onStep(at - 1) }, modifier = Modifier.heightIn(min = Target)) {
                Text(stringResource(R.string.help_previous))
            }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = { if (at < count - 1) onStep(at + 1) else onDone() },
                modifier = Modifier.heightIn(min = Target),
            ) {
                Text(stringResource(if (at < count - 1) R.string.help_next else R.string.help_done))
            }
        }
    }
}
