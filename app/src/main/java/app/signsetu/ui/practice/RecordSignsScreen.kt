package app.signsetu.ui.practice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.signsetu.BuildConfig
import app.signsetu.R
import app.signsetu.camera.CameraMirror
import app.signsetu.camera.LandmarkFrame
import app.signsetu.camera.LandmarkTracker
import app.signsetu.camera.verifiedAsset
import app.signsetu.gloss.CONSENT_VERSION
import app.signsetu.gloss.Contributions
import app.signsetu.gloss.DebugRecorder
import app.signsetu.sign.SignSegment
import app.signsetu.ui.components.CardImage
import app.signsetu.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.util.concurrent.CopyOnWriteArrayList

/** One thing to sign: a word or a sentence from the app's own lists, with the gloss the signer confirms. */
data class SignPrompt(val en: String, val hi: String, val gloss: List<String>, val card: String?)

private const val PROMPTS_PER_SESSION = 10
private const val COUNTDOWN_S = 3
private const val RECORD_MS = 5_000L

/** Words with a sign and a picture from words.json, and the intents of intents.json that need no slot filled. */
internal fun signPrompts(wordsJson: String, intentsJson: String): List<SignPrompt> {
    val json = Json { ignoreUnknownKeys = true }
    val devanagari = Regex("[\\u0900-\\u097F]")
    val words = json.parseToJsonElement(wordsJson).jsonObject["words"]?.jsonArray.orEmpty().mapNotNull { e ->
        val o = e.jsonObject
        val forms = o["w"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()
        val card = o["card"]?.jsonPrimitive?.content
        if (o["sign"] == null || card == null || forms.isEmpty()) return@mapNotNull null
        SignPrompt(forms[0], forms.firstOrNull { devanagari.containsMatchIn(it) } ?: forms[0], listOf(forms[0]), card)
    }
    val sentences = json.parseToJsonElement(intentsJson).jsonObject["intents"]?.jsonArray.orEmpty().mapNotNull { e ->
        val o = e.jsonObject
        val pattern = o["pattern"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()
        if (pattern.isEmpty() || pattern.any { it.startsWith("$") }) return@mapNotNull null
        SignPrompt(o["en"]?.jsonPrimitive?.content ?: return@mapNotNull null, o["hi"]?.jsonPrimitive?.content ?: "", pattern.map { it.removeSuffix("?") }, null)
    }
    return words + sentences
}

/** Settings, "Record signs for training": a guided session of prompts, each counted down and recorded as landmarks
 * only, through the same tracker the session uses, then confirmed or corrected by the signer and saved on the phone
 * in the sign recorder's format. The consent card decides whether saved records also leave the phone. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RecordSignsScreen(tracker: LandmarkTracker?, contributions: Contributions, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prompts by produceState(emptyList<SignPrompt>()) {
        value = withContext(Dispatchers.IO) {
            fun text(path: String) = verifiedAsset(context, path)?.let { b -> ByteArray(b.remaining()).also { b.duplicate().get(it) }.decodeToString() }
            runCatching { signPrompts(text("words.json")!!, text("intents.json")!!) }.getOrDefault(emptyList()).shuffled().take(PROMPTS_PER_SESSION)
        }
    }
    var consent by remember { mutableStateOf(contributions.consented) }
    var hand by remember { mutableStateOf("right") }
    var light by remember { mutableStateOf("bright") }
    var started by remember { mutableStateOf(false) }
    var index by remember { mutableIntStateOf(0) }
    var phase by remember { mutableStateOf("ready") } // ready, countdown, recording, review, done
    var count by remember { mutableIntStateOf(COUNTDOWN_S) }
    var glossText by remember { mutableStateOf("") }
    var deleted by remember { mutableStateOf<Boolean?>(null) }
    val frames = remember { CopyOnWriteArrayList<LandmarkFrame>() }
    val sessionId = remember { System.currentTimeMillis() }

    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(R.string.record_title)) }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) }
        })
    }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = Spacing.lg).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            if (!started) {
                // The consent card: plain English and Hindi, what leaves the phone, for how long, and how to undo it.
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Text(stringResource(R.string.record_consent_title), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.record_consent_en), style = MaterialTheme.typography.bodyMedium)
                        Text(stringResource(R.string.record_consent_hi), style = MaterialTheme.typography.bodyMedium)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            Switch(checked = consent, onCheckedChange = { consent = it; contributions.setConsent(it) })
                            Text(stringResource(if (consent) R.string.record_consent_on else R.string.record_consent_off))
                        }
                        OutlinedButton(onClick = { scope.launch { deleted = withContext(Dispatchers.IO) { contributions.deleteMine() } } }) {
                            Text(stringResource(R.string.record_delete_mine))
                        }
                        deleted?.let { Text(stringResource(if (it) R.string.record_deleted else R.string.record_delete_failed), color = MaterialTheme.colorScheme.tertiary) }
                    }
                }
                Text(stringResource(R.string.record_hand))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("right" to R.string.record_hand_right, "left" to R.string.record_hand_left).forEach { (k, s) ->
                        FilterChip(selected = hand == k, onClick = { hand = k }, label = { Text(stringResource(s)) })
                    }
                }
                Text(stringResource(R.string.record_light))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("bright" to R.string.record_light_bright, "dim" to R.string.record_light_dim, "backlit" to R.string.record_light_backlit).forEach { (k, s) ->
                        FilterChip(selected = light == k, onClick = { light = k }, label = { Text(stringResource(s)) })
                    }
                }
                Button(onClick = { started = true }, enabled = prompts.isNotEmpty() && tracker != null, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Text(stringResource(R.string.record_start))
                }
                return@Column
            }
            val prompt = prompts.getOrNull(index)
            if (prompt == null || phase == "done") {
                Text(stringResource(R.string.record_done, index), style = MaterialTheme.typography.titleMedium)
                if (contributions.consented) Text(stringResource(R.string.record_queued, contributions.pending()))
                Button(onClick = onBack) { Text(stringResource(R.string.back)) }
                return@Column
            }
            Text("${index + 1} / ${prompts.size}", style = MaterialTheme.typography.labelLarge)
            prompt.card?.let { CardImage(it, Modifier.size(140.dp)) }
            Text(prompt.en, style = MaterialTheme.typography.headlineSmall)
            Text(prompt.hi, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            CameraMirror(
                tracker = tracker,
                modifier = Modifier.fillMaxWidth().height(260.dp),
                onFrame = { f -> if (phase == "recording") frames += f },
                placeholder = {},
            )
            // Keyed on the phase, so leaving the countdown does not cancel the recording's timer with it.
            LaunchedEffect(index, phase) {
                if (phase == "countdown") {
                    while (count > 0) { delay(1_000); count-- }
                    phase = "recording"
                } else if (phase == "recording") {
                    delay(RECORD_MS)
                    glossText = prompt.gloss.joinToString(" ")
                    phase = "review"
                }
            }
            when (phase) {
                "ready" -> Button(onClick = { frames.clear(); count = COUNTDOWN_S; phase = "countdown" }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.record_go)) }
                "countdown" -> Text("$count", style = MaterialTheme.typography.displayMedium)
                "recording" -> Button(onClick = { glossText = prompt.gloss.joinToString(" "); phase = "review" }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.record_stop)) }
                "review" -> {
                    OutlinedTextField(glossText, { glossText = it }, label = { Text(stringResource(R.string.record_gloss)) }, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        OutlinedButton(onClick = { phase = "ready" }) { Text(stringResource(R.string.record_redo)) }
                        Button(onClick = {
                            val gloss = glossText.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
                            val taken = frames.toList()
                            if (gloss.isNotEmpty() && taken.isNotEmpty()) {
                                val eventId = sessionId * 100 + index
                                val window = DebugRecorder.record(eventId, null, listOf(SignSegment(taken)), BuildConfig.VERSION_NAME)
                                contributions.save("prompt", sessionMeta(contributions, sessionId, index, prompt, hand, light), window, gloss)
                            }
                            index++
                            phase = if (index >= prompts.size) "done" else "ready"
                        }) { Text(stringResource(R.string.record_save)) }
                    }
                }
            }
        }
    }
}

private fun sessionMeta(c: Contributions, sessionId: Long, index: Int, p: SignPrompt, hand: String, light: String): JsonObject = buildJsonObject {
    put("signer", c.signer)
    put("session", sessionId)
    put("prompt", index)
    put("en", p.en)
    put("hi", p.hi)
    put("handedness", hand)
    put("lighting", light)
    put("phone", "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
    put("consent", if (c.consented) JsonPrimitive(CONSENT_VERSION) else JsonPrimitive("none"))
}
