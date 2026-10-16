package app.signsetu.ui.session

/** Picture cards that answer this item, which limit what the sign reader may suggest; empty means any. */
internal fun Pending?.answerCards(): Set<String> = when (this) {
    is Pending.Ask -> question.cards
    is Pending.FreeText -> cardIds
    is Pending.Cards -> cardIds
    is Pending.PhraseAsk -> listOf(slot.card)
    else -> emptyList()
}.filter { it != "draw" }.toSet()
