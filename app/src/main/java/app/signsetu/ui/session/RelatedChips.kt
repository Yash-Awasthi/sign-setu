package app.signsetu.ui.session

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.signsetu.R
import app.signsetu.data.content.ContentBundle
import app.signsetu.ui.components.CardImage
import app.signsetu.ui.theme.LocalCall
import java.util.Locale
import app.signsetu.ui.theme.Space
import app.signsetu.ui.theme.TypeSize
import app.signsetu.ui.theme.Radius

/** A related word and the preset card it offers. Six groups, in one canonical order. */
private val RelatedWords: Map<String, String> = linkedMapOf(
    "water" to "water", "paani" to "water", "pani" to "water", "पानी" to "water",
    "food" to "food", "khana" to "food", "खाना" to "food", "khaana" to "food",
    "family" to "family", "parivar" to "family", "परिवार" to "family",
    "home" to "home", "house" to "home", "ghar" to "home", "घर" to "home", "मकान" to "home",
    "toilet" to "toilet", "टॉयलेट" to "toilet", "शौचालय" to "toilet", "shauchalay" to "toilet",
    "medicine" to "medicine", "dawa" to "medicine", "दवा" to "medicine", "dawai" to "medicine",
)

/** The order the chips appear in, water first, however the words arrive. */
private val RelatedOrder = listOf("water", "food", "family", "home", "toilet", "medicine")

private val ChipGlyphs = mapOf(
    "water" to "\uD83D\uDCA7",
    "food" to "\uD83C\uDF5A",
    "family" to "\uD83D\uDC6A",
    "home" to "\uD83C\uDFE0",
    "toilet" to "\uD83D\uDEBB",
    "medicine" to "\uD83D\uDC8A",
)

/** Letters and Devanagari marks start a word; everything else (spaces, punctuation) ends it. */
private val WordBreak = Regex("[^\\p{L}\\p{M}]+")

/**
 * The preset cards a line of typed or spoken text asks for. A word is matched whole, so
 * "watermelon" offers no water; only cards the item actually has are returned.
 */
internal fun relatedCards(text: String, available: Set<String>, limit: Int = 6): List<String> {
    if (text.isBlank() || limit <= 0) return emptyList()
    val words = WordBreak.split(text.lowercase(Locale.ROOT)).filter { it.isNotEmpty() }.toSet()
    val hits = words.mapNotNull { RelatedWords[it] }.toSet()
    return RelatedOrder.filter { it in hits && it in available }.take(limit)
}

/** The chips themselves: preset glyph, the card's picture and its name, one tap to answer. */
@Composable
internal fun RelatedChipRow(
    cards: List<String>,
    content: ContentBundle,
    lang: String,
    onCard: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (cards.isEmpty()) return
    val call = LocalCall.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
        Text(stringResource(R.string.stage_chips_related), fontSize = TypeSize.s11, color = call.muted)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).testTag("stage.chips"),
            horizontalArrangement = Arrangement.spacedBy(Space.s6),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            cards.forEach { id ->
                Surface(
                    onClick = { onCard(id) },
                    shape = Radius.pill,
                    color = call.surface,
                    border = BorderStroke(Space.hair, call.line),
                    modifier = Modifier.heightIn(min = Space.s48).testTag("stage.chip." + id),
                ) {
                    Row(
                        modifier = Modifier.padding(start = Space.s10, end = Space.md, top = Space.s6, bottom = Space.s6),
                        horizontalArrangement = Arrangement.spacedBy(Space.s6),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(ChipGlyphs[id].orEmpty(), fontSize = TypeSize.s18)
                        CardImage(id, Modifier.size(Space.s28))
                        Text(
                            content.cards[id]?.label?.get(lang) ?: id,
                            fontSize = TypeSize.s13,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
