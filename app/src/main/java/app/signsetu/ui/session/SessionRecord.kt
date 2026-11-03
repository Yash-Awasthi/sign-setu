package app.signsetu.ui.session

import app.signsetu.data.content.ContentBundle
import app.signsetu.data.session.EventSource
import app.signsetu.data.session.EventType
import app.signsetu.data.session.SessionRepository

/** The row that says what went on the stage; its id is the item every answer to it names. */
internal suspend fun SessionRepository.logShown(sessionId: String, content: ContentBundle, officerLang: String, pending: Pending, lang: String): Long {
    return when (pending) {
        is Pending.FreeText if pending.ref != null -> log(
            sessionId, EventType.QUESTION_SHOWN, EventSource.SHOW_BACK, lang,
            text = pending.text, cardId = pending.cardIds.firstOrNull(), lexiconId = REF_PREFIX + pending.ref,
        )
        is Pending.Ask -> log(
            sessionId, EventType.QUESTION_SHOWN, EventSource.PRESET, lang,
            text = pending.question.text.get(officerLang), lexiconId = pending.question.lexicon,
            itemId = pending.question.id, reason = shownReason(content, pending.question, lang),
        )
        // A machine-made caption is logged next to the officer's own words, so the record shows both.
        is Pending.FreeText -> log(
            sessionId, EventType.QUESTION_SHOWN, if (pending.byAi) EventSource.MACHINE else EventSource.TEXT, lang,
            text = listOfNotNull(pending.text, pending.caption).joinToString("\n"), reason = SHOWN_AI.takeIf { pending.byAi },
        )
        is Pending.Cards -> log(
            sessionId, EventType.QUESTION_SHOWN, EventSource.BOARD, lang,
            text = pending.cardIds.joinToString { id -> content.cards[id]?.label?.get(officerLang) ?: id },
        )
        is Pending.ShowBack -> log(
            sessionId, EventType.QUESTION_SHOWN, EventSource.SHOW_BACK, lang,
            text = pending.entry.label.get(officerLang), cardId = pending.entry.card, lexiconId = pending.entry.id,
        )
        is Pending.PhraseAsk -> log(
            sessionId, EventType.QUESTION_SHOWN, EventSource.PHRASE, lang,
            text = pending.slot.text.get(officerLang), cardId = pending.slot.card,
        )
        Pending.LanguageFinder -> log(sessionId, EventType.SYSTEM, EventSource.OFFICER, text = SYSTEM_LANGUAGE_FINDER)
        Pending.Drawing -> log(sessionId, EventType.SYSTEM, EventSource.OFFICER, text = SYSTEM_DRAWING)
        is Pending.GroundRules -> log(
            sessionId, EventType.QUESTION_SHOWN, EventSource.PRESET, lang,
            text = ruleStep(pending)?.let { ruleText(content, it.key, officerLang) },
            // The introduction is a bank question; naming it lets auto mode see it was asked.
            itemId = ruleStep(pending)?.key?.takeIf { content.question(it) != null }, reason = SHOWN_OPENING,
        )
    }
}

/** Answers to a show-back or say-back. A meaning becomes the subject's only when they pick it among two or more
 * pictures; a Yes to a single reading is logged as their Yes, and the reading stays the officer's. */
internal suspend fun SessionRepository.logChecked(sessionId: String, content: ContentBundle, officerLang: String, item: Pending?, card: String?, yes: Boolean?, shownId: Long?, via: String? = null): Boolean {
    val meant: String?
    val text: String?
    val lexiconId: String?
    when (item) {
        is Pending.ShowBack -> { meant = item.entry.card; text = item.entry.label.get(officerLang); lexiconId = item.entry.id }
        is Pending.FreeText if item.ref != null -> { meant = item.meant; text = item.text; lexiconId = REF_PREFIX + item.ref }
        else -> return false
    }
    val choices = if (item is Pending.ShowBack) item.choices else (item as Pending.FreeText).cardIds
    val picked = item.asksChoice && card != null && card in choices
    when {
        yes == true -> log(sessionId, EventType.ANSWER_CARD, EventSource.SUBJECT, officerLang, text = content.cards["yes"]?.label?.get(officerLang), cardId = "yes", lexiconId = lexiconId, shownId = shownId, via = via)
        yes == false || (picked && card == SOMETHING_ELSE) ->
            log(sessionId, EventType.ANSWER_UNCLEAR, EventSource.SUBJECT, officerLang, text = text, cardId = "no", lexiconId = lexiconId, shownId = shownId, via = via)
        picked && card == meant -> log(sessionId, EventType.ANSWER_GESTURE, EventSource.SUBJECT, officerLang, text = text, cardId = card, lexiconId = lexiconId, shownId = shownId, via = via)
        picked -> log(sessionId, EventType.ANSWER_CARD, EventSource.SUBJECT, officerLang, text = content.cards[card]?.label?.get(officerLang), cardId = card, lexiconId = lexiconId, shownId = shownId, via = via)
        else -> return false
    }
    return true
}
