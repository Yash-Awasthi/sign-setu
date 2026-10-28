package app.signsetu.ui.session

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import app.signsetu.R
import app.signsetu.data.content.SignLanguage
import app.signsetu.data.session.EventType
import app.signsetu.data.session.Profile
import app.signsetu.data.session.Role as SessionRole
import app.signsetu.gloss.Gender
import app.signsetu.ui.components.ChoiceTile
import app.signsetu.ui.start.LanguagePicker
import app.signsetu.ui.theme.Spacing

private val Plain = listOf(
    R.string.qn_speak to listOf(R.string.qn_yes, R.string.qn_little, R.string.qn_no),
    R.string.qn_hear to listOf(R.string.qn_yes, R.string.qn_little, R.string.qn_no),
    R.string.qn_home to listOf(R.string.qn_near, R.string.qn_far, R.string.qn_unknown),
    R.string.qn_injuries to listOf(R.string.qn_none_seen, R.string.qn_visible, R.string.qn_says_hurt),
)

/** What the officer sees for themselves: one tap per answer. Plain answers go to the record as "Question: answer"
 * notes; role, age, sign language, gender and language set the session as the start screen used to. */
@Composable
internal fun QuickNotesSheet(
    ui: SessionUi,
    vm: SessionViewModel,
    /** Fixed while something is on a second screen. */
    locked: Boolean,
    note: String,
    onNote: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val res = LocalResources.current
    val notes = ui.events.filter { it.type == EventType.NOTE }.mapNotNull { it.text }
    fun picked(@StringRes q: Int): String? = res.getString(q).let { l -> notes.lastOrNull { it.startsWith("$l: ") }?.removePrefix("$l: ") }
    fun log(@StringRes q: Int, @StringRes a: Int) = vm.addNote("${res.getString(q)}: ${res.getString(a)}")
    val signs = picked(R.string.qn_signs)
    val signer = signs == res.getString(R.string.qn_isl) || signs == res.getString(R.string.qn_asl)
    val under18 = ui.juvenile
    // The stage follows the person: a signer gets the signing stage, a child the child's; a language search stays.
    fun restage(signs: Boolean, child: Boolean) {
        if (locked || ui.session.profile == Profile.OTHER_LANGUAGE) return
        val p = when { signs -> Profile.ISL_USER; child -> Profile.YOUNG_CHILD; else -> Profile.NOT_SURE }
        if (p != ui.session.profile) vm.setProfile(p)
    }

    OfficerSheet(onDismiss) {
        Column(
            Modifier.fillMaxHeight(0.92f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.lg).imePadding().navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            SheetTitle(stringResource(R.string.qn_title))
            Choices(R.string.qn_under18, listOf(R.string.qn_yes, R.string.qn_no), when {
                under18 -> R.string.qn_yes
                ui.events.any { it.type == EventType.SYSTEM && it.text == SYSTEM_JUVENILE_OFF } -> R.string.qn_no
                else -> null
            }) { a ->
                val child = a == R.string.qn_yes
                if (child != under18) { vm.setJuvenile(child); restage(signer, child) }
            }
            val roles = listOf(R.string.qn_victim to SessionRole.VICTIM, R.string.qn_accused to SessionRole.ACCUSED, R.string.qn_unknown to SessionRole.NOT_KNOWN)
            Choices(R.string.qn_role, roles.map { it.first }, roles.first { it.second == ui.session.role }.first) { a ->
                vm.setRole(roles.first { it.first == a }.second)
            }
            val signOptions = listOfNotNull(R.string.qn_isl, R.string.qn_asl.takeIf { !vm.content.aslDisabled && !locked }, R.string.qn_no)
            Choices(R.string.qn_signs, signOptions, signOptions.firstOrNull { res.getString(it) == signs }) { a ->
                log(R.string.qn_signs, a)
                if (!locked && a != R.string.qn_no) vm.setSignLanguage(if (a == R.string.qn_asl) SignLanguage.ASL else SignLanguage.ISL)
                restage(a != R.string.qn_no, under18)
            }
            Plain.forEach { (q, options) -> Choices(q, options, options.firstOrNull { res.getString(it) == picked(q) }) { log(q, it) } }
            val genders = listOf(R.string.gender_male to Gender.MASCULINE, R.string.gender_female to Gender.FEMININE, R.string.gender_unknown to Gender.UNKNOWN)
            Choices(R.string.qn_gender, genders.map { it.first }, genders.first { it.second == ui.speakerGender }.first) { a ->
                vm.setGender(genders.first { it.first == a }.second)
            }
            SectionLabel(stringResource(R.string.start_language))
            if (!locked) LanguagePicker(vm.content, ui.session.subjectLang, onPick = vm::setSubjectLang)
            Row(Modifier.fillMaxWidth().padding(vertical = Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = note,
                    onValueChange = onNote,
                    placeholder = { Text(stringResource(R.string.log_note_hint)) },
                    // the keyboard should not learn or suggest from what is noted about a subject.
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = { vm.addNote(note); onNote("") }, enabled = note.isNotBlank(), modifier = Modifier.padding(start = Spacing.sm)) {
                    Text(stringResource(R.string.log_add))
                }
            }
        }
    }
}

@Composable
private fun Choices(@StringRes question: Int, options: List<Int>, selected: Int?, onPick: (Int) -> Unit) {
    SectionLabel(stringResource(question))
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        options.forEach { a ->
            ChoiceTile(a == selected, onClick = { if (a != selected) onPick(a) }, modifier = Modifier.weight(1f).fillMaxHeight()) {
                Text(stringResource(a), style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
            }
        }
    }
}
