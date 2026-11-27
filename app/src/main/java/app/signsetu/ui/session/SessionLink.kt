package app.signsetu.ui.session

import androidx.lifecycle.viewModelScope
import app.signsetu.data.session.EventSource
import app.signsetu.data.session.EventType
import app.signsetu.pair.AnswerBody
import app.signsetu.pair.TEXT_CAP
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import app.signsetu.data.content.Given
import app.signsetu.data.content.Method
import app.signsetu.data.content.TEXT_MAX
import app.signsetu.data.content.reason
import java.util.Base64

internal class SessionLink(private val vm: SessionViewModel) {
    /** The single entry for answers from a second screen; returns after the row is committed so the link can ack.
     * Nothing is dropped: an answer to an earlier item is logged against that item and marked late. */
    suspend fun logRemote(itemId: Long, n: Int, body: AnswerBody, late: Boolean = false, from: Any? = null): Boolean {
        // A new link numbers its answers from 0 again, so only a repeat from the same link is a resend.
        if (from !== vm.remoteFrom) { vm.remoteLogged.clear(); vm.remoteFrom = from }
        if (itemId to n in vm.remoteLogged) return false
        // A session that has ended takes no answer: the row would sit outside the root the certificate covers.
        if (vm.repo.isEnded(vm.sessionId)) return false
        val current = itemId == vm.local.value.itemId
        val via = if (late || !current) VIA_STAGE_LATE else VIA_STAGE
        val q = itemId.takeIf { it in vm.shown }
        val lang = vm.officerLang
        when (body) {
            // The stage's water and food buttons are requests unless the item itself offered that picture.
            is AnswerBody.Card if body.id in NeedCards && !vm.shown[itemId].offers(body.id) ->
                vm.repo.log(vm.sessionId, EventType.REQUEST, EventSource.SUBJECT, lang, text = vm.content.cards[body.id]?.label?.get(lang), cardId = body.id, via = via)
            is AnswerBody.Card -> {
                if (!vm.repo.logChecked(vm.sessionId, vm.content, vm.officerLang, vm.shown[itemId], body.id, null, q, via)) {
                    vm.repo.log(vm.sessionId, EventType.ANSWER_CARD, EventSource.SUBJECT, lang, text = vm.content.cards[body.id]?.label?.get(lang), cardId = body.id, shownId = q, via = via, reason = body.cue?.let { "cue:$it" })
                }
                // Compared again after the write: the stage may have moved on while the row was sealed.
                if (itemId == vm.local.value.itemId) {
                    vm.local.update { it.copy(answeredCard = body.id, answeredYes = null) }
                    // The row is kept, so the run moves on as it does for an answer given on this phone.
                    if (!late) when (body.id) {
                        "stop" -> vm.endRun()
                        // The pictures did not hold the answer: the officer's own stage offers the rest, then the pad.
                        SOMETHING_ELSE -> vm.somethingElse()
                        else -> vm.advanceRun()
                    }
                }
            }
            is AnswerBody.YesNo -> {
                if (!vm.repo.logChecked(vm.sessionId, vm.content, vm.officerLang, vm.shown[itemId], null, body.yes, q, via)) {
                    val id = if (body.yes) "yes" else "no"
                    vm.repo.log(vm.sessionId, EventType.ANSWER_CARD, EventSource.SUBJECT, lang, text = vm.content.cards[id]?.label?.get(lang), cardId = id, shownId = q, via = via, reason = body.cue?.let { "cue:$it" })
                }
                if (itemId == vm.local.value.itemId) {
                    vm.local.update { it.copy(answeredYes = body.yes, answeredCard = null) }
                    // A No that reopens the input holds the item, exactly as it does on this phone.
                    if (!late && advancesRun(vm.shown[itemId], body.yes)) vm.advanceRun()
                }
            }
            is AnswerBody.Language -> {
                vm.repo.log(vm.sessionId, EventType.LANGUAGE_CHOSEN, EventSource.SUBJECT, body.code, text = vm.content.language(body.code)?.english, shownId = q, via = via)
                if (itemId == vm.local.value.itemId) {
                    vm.local.update { it.copy(chosenLanguage = body.code) }
                    // The pick stands after its settle, as it does on this phone, and brings the opening up in it.
                    if (!late) vm.settleLanguage(body.code)
                }
            }
            AnswerBody.Next -> if (current) vm.nextRule()
            is AnswerBody.Given -> {
                val method = Method.entries.firstOrNull { it.name == body.method }
                if (method != null) {
                    val g = Given(body.question, method, body.confirmed, body.value.take(TEXT_MAX), body.picks)
                    if (!current || !vm.hold(g, via)) vm.logGiven(g, q, via)
                    if (current && g.confirmed) vm.advanceRun()
                }
            }
            AnswerBody.Change -> if (current) vm.hold(null, via)
            is AnswerBody.CameraRead -> vm.cameraRead(body.card, body.cue, q)
            is AnswerBody.Text -> {
                vm.repo.log(vm.sessionId, EventType.ANSWER_TEXT, EventSource.SUBJECT, vm.ui.value?.session?.subjectLang, text = body.text.trim().take(TEXT_CAP), shownId = q, via = via)
                if (itemId == vm.local.value.itemId && !late) vm.advanceRun()
            }
            is AnswerBody.Drawing -> {
                val png = remotePng(body.png)
                if (png != null) {
                    vm.repo.log(vm.sessionId, EventType.ANSWER_DRAWING, EventSource.SUBJECT, drawingPng = png, shownId = q, via = via)
                } else {
                    vm.repo.log(vm.sessionId, EventType.ANSWER_UNCLEAR, EventSource.SUBJECT, shownId = q, via = via)
                }
                if (itemId == vm.local.value.itemId) {
                    vm.local.update { it.copy(onStage = null, itemId = null) }
                    if (!late) vm.advanceRun()
                }
            }
            // The stage's reader made these words, as this phone's reader makes its own: a reading, not the subject's answer.
            is AnswerBody.Signed -> {
                val text = body.words.joinToString(" ").take(TEXT_CAP)
                if (text.isNotBlank()) {
                    vm.repo.log(vm.sessionId, EventType.ANSWER_SIGNED, EventSource.MACHINE, lang, text = text, shownId = q, via = via)
                }
                body.skeletonPng?.let { vm.keepPng(remotePng(it), EventSource.MACHINE, q, via) }
            }
        }
        // A write dropped because the app locked is not remembered, so the stage's resend is logged later.
        if (vm.repo.closed) return false
        vm.remoteLogged += itemId to n
        return true
    }

    /** False once the app has locked: rows are no longer written, so nothing may be acknowledged as kept. */
    val storing: Boolean get() = !vm.repo.closed

    /** Only a bitmap this phone re-encoded itself reaches the database and the report. */
    private fun remotePng(base64: String): ByteArray? = runCatching { Base64.getDecoder().decode(base64) }.getOrNull()?.let(vm.reencodePng)

    /** Lifecycle of the second screen, in the encrypted log; [key] is a machine key shown in the officer's language. */
    fun logLink(key: String, detail: String? = null) = vm.viewModelScope.launch {
        vm.repo.log(vm.sessionId, EventType.LINK, EventSource.OFFICER, text = listOfNotNull(key, detail).joinToString(" "))
    }

    fun breakGiven() = vm.viewModelScope.launch { vm.repo.log(vm.sessionId, EventType.SYSTEM, EventSource.OFFICER, text = SYSTEM_BREAK_GIVEN) }

    /** The link failed: what was on the second screen goes to this phone's stage; the draft is dropped. */
    fun fallbackToLocal() {
        vm.paired = false
        vm.local.update { it.copy(pending = it.onStage) }
        vm.viewModelScope.launch { vm.repo.log(vm.sessionId, EventType.LINK, EventSource.OFFICER, text = LINK_FALLBACK) }
    }

    /** The second screen went away without a fallback: this phone stays the officer's, never a stage with their draft. */
    fun leavePaired() {
        vm.paired = false
        vm.local.update { it.copy(mode = Mode.OFFICER, onStage = null, itemId = null) }
    }
}
