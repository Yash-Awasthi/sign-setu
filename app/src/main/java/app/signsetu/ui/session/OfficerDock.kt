package app.signsetu.ui.session

import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.border
import androidx.compose.material.icons.filled.Star
import app.signsetu.assistant.Suggestion
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.role
import app.signsetu.R
import app.signsetu.ui.components.LocalOnlineAssistant
import app.signsetu.data.content.ContentBundle
import app.signsetu.data.content.Phrase
import app.signsetu.data.content.Question
import app.signsetu.data.content.SignMode
import androidx.compose.runtime.LaunchedEffect
import app.signsetu.data.session.EventType
import app.signsetu.data.session.Profile
import app.signsetu.ui.components.CardImage
import app.signsetu.ui.theme.Space
import app.signsetu.data.content.PhraseSlot
import app.signsetu.ui.theme.TypeSize
import app.signsetu.ui.theme.Radius

/** Prepared questions an officer needs most often, offered on the main page until each has been asked. */
private val QuickQuestions = listOf("s2", "n1", "n2", "n3", "h2", "f2", "i2", "l1")
private const val TRANSLATE_APP = "com.google.android.apps.translate"
/** Pictures carry these before anyone knows the person's language: who we are, safety, help coming, basic needs. */
private val NoLanguageQuestions = listOf("s1", "s5", "s6", "n1", "n2", "n3", "h2")

/** Quick questions and the composer: everything the officer reaches with a thumb. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun Dock(
    ui: SessionUi,
    content: ContentBundle,
    officerLang: String,
    text: String,
    onText: (String) -> Unit,
    assistant: Boolean,
    canRead: Boolean,
    thinking: Boolean,
    suggestion: Suggestion?,
    noHelp: Boolean,
    onAssist: () -> Unit,
    onDiscard: () -> Unit,
    draft: String?,
    onDraftOff: () -> Unit,
    modes: List<SignMode>,
    onMode: (SignMode) -> Unit,
    attached: List<String>,
    onPictures: () -> Unit,
    onQuestion: (Question) -> Unit,
    onClear: () -> Unit,
    onSend: () -> Unit,
    onRead: () -> Unit,
    /** Opens the voice sheet; null without a paired brain. */
    onVoice: (() -> Unit)? = null,
    run: List<Question> = emptyList(),
    onRunRemove: (Question) -> Unit = {},
    onRunAsk: (Question) -> Unit = {},
    onAskNow: (Question) -> Unit = {},
    onAddToRun: (Question) -> Unit = {},
    onRunBasics: () -> Unit = {},
    onTemplate: (Phrase, PhraseSlot) -> Unit = { _, _ -> },
) {
    val colors = MaterialTheme.colorScheme
    val typing = text.isNotEmpty()
    val addLabel = stringResource(R.string.run_add)
    val pending = ui.pending
    val subjectLang = ui.session.subjectLang
    val asked = remember(ui.events) { ui.events.filter { it.type == EventType.QUESTION_SHOWN }.mapNotNull { it.text }.toSet() }
    val noLanguage = ui.session.profile == Profile.OTHER_LANGUAGE
    val quick = remember(content, officerLang, asked, pending, noLanguage) {
        (if (noLanguage) (NoLanguageQuestions + QuickQuestions).distinct() else QuickQuestions).mapNotNull { id -> content.questions.firstOrNull { it.id == id } }
            .filter { q -> q.text.get(officerLang) !in asked || (pending as? Pending.Ask)?.question?.id == q.id }
    }
    // Once the subject has answered and nothing is prepared, the next unasked question is one tap away.
    val answeredLast = remember(ui.events) { ui.events.lastOrNull { it.type in AnswerEvents || it.type == EventType.QUESTION_SHOWN }?.type in AnswerEvents }
    // Suggestions name the item ids already on the stage, so a question is never offered twice.
    val askedIds = remember(ui.events) { ui.events.filter { it.type == EventType.QUESTION_SHOWN }.mapNotNull { it.itemId }.toSet() }
    val suggestions = remember(content, ui.selectedGroup, ui.session.role, ui.session.profile, askedIds) {
        nextQuestions(content, ui.selectedGroup, ui.session.role, ui.session.profile, askedIds)
    }
    val suggesting = answeredLast && pending == null && run.isEmpty() && !typing
    Surface(color = colors.surfaceContainerLowest, shape = RoundedCornerShape(topStart = Space.xl, topEnd = Space.xl), shadowElevation = Space.sm) {
        Column(Modifier.navigationBarsPadding().padding(top = Space.md)) {
            if (suggesting) SuggestionStrip(suggestions, officerLang, onAsk = onAskNow)
            AnimatedVisibility(!typing && quick.isNotEmpty(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(Space.sm), contentPadding = PaddingValues(horizontal = Space.lg), modifier = Modifier.padding(bottom = Space.sm)) {
                    if (noLanguage) item(key = "translate") {
                        val context = LocalContext.current
                        CategoryChip(stringResource(R.string.open_translate), false, locked = false) {
                            // Only opens the app; nothing from the session is passed to it.
                            context.packageManager.getLaunchIntentForPackage(TRANSLATE_APP)?.let(context::startActivity)
                                ?: Toast.makeText(context, R.string.open_translate_missing, Toast.LENGTH_LONG).show()
                        }
                    }
                    if (run.isEmpty()) item(key = "run_basics") { CategoryChip(stringResource(R.string.run_basics), false, locked = false, onClick = onRunBasics) }
                    items(quick, key = { it.id }) { q ->
                        CategoryChip(q.text.get(officerLang) ?: q.id, (pending as? Pending.Ask)?.question?.id == q.id, locked = false, onLongClick = { onAddToRun(q) }, longClickLabel = addLabel) { onQuestion(q) }
                    }
                }
            }

            // Follow-up prompts: on-device rules, or the optional cloud AI when the officer has switched it on.
            val cloud = LocalOnlineAssistant.current
            val lastAnswerEvent = remember(ui.events) { ui.events.lastOrNull { it.type in AnswerEvents } }
            // Keyed on the answer, not the log size: rows logged after an answer must not drop its suggestions.
            var aiFollowUps by remember(lastAnswerEvent?.id) { mutableStateOf<List<String>>(emptyList()) }

            LaunchedEffect(lastAnswerEvent?.id) {
                if (lastAnswerEvent != null && !typing) {
                    val suggestions = cloud.followUps(
                        lastAnswerEvent.text ?: lastAnswerEvent.cardId ?: "answered",
                        ui.child,
                        subjectNames(ui.events, content, officerLang),
                        subjectDetails(ui.events, content, officerLang),
                    )
                    aiFollowUps = suggestions.distinct()
                }
            }

            AnimatedVisibility(!typing && aiFollowUps.isNotEmpty(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column(Modifier.padding(horizontal = Space.lg).padding(bottom = Space.s6)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                        Icon(Icons.Filled.Star, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(Space.s13))
                        Text(
                            text = stringResource(R.string.ai_followups_title),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(Space.sm),
                        contentPadding = PaddingValues(top = Space.xs),
                    ) {
                        items(aiFollowUps, key = { it }) { prompt ->
                            Surface(
                                shape = RoundedCornerShape(Radius.r14),
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                border = BorderStroke(Space.hair, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f)),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .clickable { onText(prompt) }
                                    .semantics { role = Role.Button; contentDescription = prompt },
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = Space.s10, vertical = Space.s6),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(Space.xs),
                                ) {
                                    Text("✨", fontSize = TypeSize.s12)
                                    Text(
                                        text = prompt,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (typing || attached.isNotEmpty()) {
                FlowRow(
                    Modifier.padding(horizontal = Space.lg).padding(bottom = Space.sm),
                    horizontalArrangement = Arrangement.spacedBy(Space.s6),
                    verticalArrangement = Arrangement.spacedBy(Space.s6),
                ) {
                    if (assistant && typing) AssistChip(thinking, onAssist)
                    ModeLabels.forEach { (mode, label) -> ModeChip(stringResource(label), mode in modes) { onMode(mode) } }
                    attached.forEach { id ->
                        Surface(shape = Radius.pill, color = colors.secondaryContainer, contentColor = colors.onSecondaryContainer, modifier = Modifier.height(Space.xxl)) {
                            Row(Modifier.padding(horizontal = Space.s10), verticalAlignment = Alignment.CenterVertically) {
                                CardImage(id, Modifier.size(Space.s20))
                                Text(content.cards[id]?.label?.get(officerLang) ?: id, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = Space.xs))
                            }
                        }
                    }
                }
            } else if (run.isNotEmpty()) {
                RunTray(run, officerLang, onRunRemove, onRunAsk)
            } else if (pending != null) {
                Ready(pending, content, officerLang, subjectLang, ui.session.profile, onClear)
            }
            if (typing) CompletionRows(content, text, officerLang, onComplete = onText, onTemplate = onTemplate)
            if (typing && suggestion != null) {
                AssistantCard(suggestion.caption, suggestion.meaning, content.language(subjectLang)?.english ?: subjectLang, onDiscard)
            } else if (typing && draft != null) {
                AssistantCard(draft, null, content.language(subjectLang)?.english ?: subjectLang, onDraftOff)
            } else if (typing && noHelp) {
                Text(stringResource(R.string.ai_none), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, modifier = Modifier.padding(horizontal = Space.lg).padding(bottom = Space.sm))
            }

            Composer(
                text = text,
                onText = onText,
                onPictures = onPictures,
                canSend = text.isNotBlank() || pending != null || run.isNotEmpty(),
                onSend = onSend,
                onRead = onRead.takeIf { canRead },
                onVoice = onVoice,
                visualTransformation = rememberSignabilityTint(content),
            )
        }
    }
}

