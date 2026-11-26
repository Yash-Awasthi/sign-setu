package app.signsetu.ui.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.signsetu.R
import app.signsetu.data.content.ContentBundle
import app.signsetu.data.content.Question
import app.signsetu.data.session.Profile
import app.signsetu.data.session.Role
import app.signsetu.ui.theme.Spacing

/** The first [limit] of [questions] that were not already asked, in content order, with no id twice. */
internal fun threeSuggestions(questions: List<Question>, asked: Set<String>, limit: Int = 3): List<Question> {
    if (limit <= 0) return emptyList()
    val out = ArrayList<Question>(limit)
    for (q in questions) {
        if (q.id in asked || out.any { it.id == q.id }) continue
        out += q
        if (out.size == limit) break
    }
    return out
}

/** The next likely questions for this person: the chosen group first, then the role's other groups to fill the row. */
internal fun nextQuestions(
    content: ContentBundle,
    group: String,
    role: Role,
    profile: Profile,
    asked: Set<String>,
    limit: Int = 3,
): List<Question> {
    if (limit <= 0) return emptyList()
    val first = threeSuggestions(content.questionsIn(group, role, profile), asked, limit)
    if (first.size >= limit) return first
    // Fewer than three in this group: top up from the groups this role may see, still in content order.
    val rest = content.groups
        .filter { it.roles.isEmpty() || role in it.roles }
        .flatMap { content.questionsIn(it.id, role, profile) }
    val ids = asked + first.map { it.id }
    return first + threeSuggestions(rest, ids, limit - first.size)
}

/** Three questions one tap away; nothing at all when there is nothing to suggest. */
@Composable
internal fun SuggestionStrip(
    questions: List<Question>,
    officerLang: String,
    onAsk: (Question) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (questions.isEmpty()) return
    val colors = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth().testTag("stage.suggestions")) {
        Text(
            stringResource(R.string.suggest_title),
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(start = Spacing.lg, end = Spacing.lg, bottom = 2.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = Spacing.lg, end = Spacing.lg).padding(bottom = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            questions.forEach { q ->
                val label = q.text[officerLang] ?: q.text["en"] ?: q.id
                Surface(
                    onClick = { onAsk(q) },
                    shape = RoundedCornerShape(16.dp),
                    color = colors.secondaryContainer,
                    contentColor = colors.onSecondaryContainer,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("stage.suggestion." + q.id)
                        .semantics { contentDescription = label },
                ) {
                    Box(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}
