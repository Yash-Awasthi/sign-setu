package app.signsetu.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.signsetu.R
import app.signsetu.data.settings.InterpreterApp
import app.signsetu.data.settings.interpreterUrl
import app.signsetu.ui.theme.Spacing
import app.signsetu.ui.components.GuardedAlertDialog

@Composable
internal fun <T> ChoiceDialog(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onPick: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    GuardedAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.selectableGroup()) {
                options.forEach { option ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .selectable(selected = option == selected, onClick = { onPick(option) }, role = Role.RadioButton),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = option == selected, onClick = null)
                        Text(label(option), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = Spacing.lg))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
internal fun ContactDialog(app: InterpreterApp, initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var value by rememberSaveable { mutableStateOf(initial) }
    val phone = app == InterpreterApp.WHATSAPP
    // A WhatsApp entry may also be an https link, which opens as given; nothing else is saved.
    val valid = if (phone) interpreterUrl(value) != null else value.isNotBlank()
    GuardedAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_interpreter_contact)) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                label = {
                    Text(stringResource(if (phone) R.string.settings_contact_whatsapp_hint else R.string.settings_contact_sandes_hint))
                },
                isError = phone && value.isNotBlank() && !valid,
                supportingText = if (phone && value.isNotBlank() && !valid) {
                    { Text(stringResource(R.string.settings_contact_invalid)) }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onSave(value.trim()) }, enabled = valid) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Names and licences stay in English: they are legal identifiers, not interface text. */
private val LICENCES = listOf(
    "OpenMoji 17.0.0" to "CC BY-SA 4.0. HfG Schwäbisch Gmünd, openmoji.org",
    "Mulberry Symbols" to "CC BY-SA 4.0. Steve Lee, mulberrysymbols.org",
    "Indic Parler-TTS (recorded prompts)" to "Apache 2.0. AI4Bharat, IIT Madras",
    "INCLUDE (sign reader training data)" to "CC BY 4.0. \"INCLUDE: A Large Scale Dataset for Indian Sign Language Recognition\", " +
        "A. Sridhar, R. G. Ganesan, P. Kumar, M. Khapra (IIT Madras, AI4Bharat), doi:10.5281/zenodo.4010759. " +
        "Changed: a sign classifier was trained on its landmarks.",
    "OpenHands pose release" to "CC BY 4.0. AI4Bharat, \"OpenHands v1: Labelled ISLR datasets, Poses\", zenodo.org/records/6674324. " +
        "Changed: poses resampled and normalised.",
    "PopSign ASL v1.0 (ASL reader training data, ASL signs)" to "CC BY 4.0. Starner, Forbes, So, Martin, Sridhar, Deshpande et al. (Georgia Tech, NTID), " +
        "NeurIPS 2023 Datasets and Benchmarks, signdata.cc.gatech.edu; frames from huggingface.co/datasets/sign/popsign-images. " +
        "Changed: landmarks extracted, a sign classifier trained, signers retargeted as keyframes on the app's characters, mirrored to right-hand dominant where the left hand led.",
    "Multimodal ISL alphabet dataset (ISL letter reader training data)" to "CC BY 4.0. Hambir, Khandagale, Cherian, Gaikwad " +
        "(Vishwakarma Institute of Information Technology, Pune), Mendeley Data, doi:10.17632/2tj6c7pzfk.1. " +
        "Changed: hand landmarks extracted from the English and number photographs, a letter classifier trained.",
    "ISLAN (ISL letter reader test data)" to "CC BY 4.0. Mendeley Data, doi:10.17632/rc349j45m5.1. " +
        "Changed: photographs turned upright and hand landmarks extracted to measure the letter reader; not shipped.",
    "ISL-CSLTR (ISL sentence clips)" to "CC BY 4.0. \"ISL-CSLTR: Indian Sign Language Dataset for Continuous Sign Language Translation and Recognition\", " +
        "Elakkiya R., Natarajan B. (SASTRA), Mendeley Data 2021, doi:10.17632/kcmpdxky7p.1. " +
        "Changed: sentence videos converted to landmark clips.",
    "ASLNow! fingerspelling landmarks" to "MIT. sid220, huggingface.co/datasets/sid220/asl-now-fingerspelling. " +
        "Changed: letters chained into synthetic spelling to train a reader, and posed as the ASL alphabet.",
    "MediaPipe and the Holistic landmarker model" to "Apache 2.0. Google LLC",
    "Sign Setu symbols (original drawings)" to "CC BY-SA 4.0. Drawn in OpenMoji style for this app",
    "SQLCipher for Android" to "BSD 3-Clause. Zetetic LLC",
    "PdfBox-Android" to "Apache 2.0. Tom Roush; Apache PDFBox",
    "AndroidX, Jetpack Compose, Media3" to "Apache 2.0. The Android Open Source Project",
    "Kotlin, kotlinx.coroutines, kotlinx.serialization" to "Apache 2.0. JetBrains",
    "CameraX" to "Apache 2.0. The Android Open Source Project",
    "Protocol Buffers for Java (protobuf-java)" to "BSD 3-Clause. Google LLC",
    "BouncyCastle" to "MIT-style Bouncy Castle licence. The Legion of the Bouncy Castle Inc.",
    "llama.cpp" to "MIT. Georgi Gerganov and contributors, ggml-org/llama.cpp",
    "Baloo 2 font" to "SIL Open Font Licence 1.1. Ek Type",
    "INCLUDE sign retargeting" to "CC BY 4.0. Motions retargeted from the INCLUDE landmark samples; see INCLUDE above.",
    "ISLRTC Indian Sign Language Dictionary" to "Government Open Data License - India. Indian Sign Language Research and Training Centre, " +
        "Government of India, data.gov.in. Changed: motions of words missing from INCLUDE retargeted onto the app's characters; no video is included.",
    "Indian Sign Language video dataset (sign reader training data)" to "MIT. Prasad Shet, kaggle.com/datasets/prasadshet/indian-sign-language-video-dataset. " +
        "Changed: landmarks extracted from the videos to train the reader; no video is included.",
    "ISL-Fingerspelling (ISL name spelling reader training data)" to "CC BY-NC 4.0. \"Continuous Fingerspelling Dataset for Indian Sign Language\", " +
        "WSLP at AACL-IJCNLP 2025. Changed: landmarks extracted from the videos to train the reader; no video is included. Non-commercial use only.",
    "Google Isolated Sign Language Recognition (ASL reader training data)" to "CC BY 4.0. Google, kaggle.com/competitions/asl-signs.",
    "Google ASL Fingerspelling (ASL spelling reader training data)" to "CC BY 4.0. Google, kaggle.com/competitions/asl-fingerspelling.",
)

@Composable
internal fun LicencesDialog(onDismiss: () -> Unit) {
    GuardedAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_licences)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                LICENCES.forEach { (name, licence) ->
                    Column {
                        Text(name, style = MaterialTheme.typography.titleSmall)
                        Text(licence, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text(
                    "Symbols are converted to bitmaps for this app; the converted symbol set is shared under CC BY-SA 4.0.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}
