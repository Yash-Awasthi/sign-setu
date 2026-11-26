package app.signsetu.ui.session

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.signsetu.R
import app.signsetu.data.content.ContentBundle
import app.signsetu.data.content.or
import app.signsetu.ui.components.CardImage
import app.signsetu.ui.theme.Baloo
import app.signsetu.ui.theme.Space
import app.signsetu.ui.theme.TypeSize
import app.signsetu.ui.theme.Radius
import app.signsetu.ui.theme.StageTones
import app.signsetu.ui.theme.StageScheme
import app.signsetu.ui.theme.ExpressTones
import app.signsetu.ui.theme.Tone

/** A picture card on the express board; its words come from the card's reviewed labels. */
private class ExpressCard(val id: String, val tab: String, val tone: Tone)

/** The board's tabs are content card categories, so their titles exist in every language. */
private val Tabs = listOf("feelings", "needs", "alert", "safety", "people", "places", "answers", "body")

/** The things a frightened child or a person who cannot speak most needs to say. */
private val ExpressCards = listOf(
    ExpressCard("pain", "feelings", ExpressTones.pain),
    ExpressCard("scared", "feelings", ExpressTones.scared),
    ExpressCard("sick", "feelings", ExpressTones.sick),
    ExpressCard("stop", "feelings", ExpressTones.stop),
    ExpressCard("police", "feelings", ExpressTones.police),
    ExpressCard("okay", "feelings", ExpressTones.okay),
    ExpressCard("sad", "feelings", ExpressTones.sad),
    ExpressCard("angry", "feelings", ExpressTones.angry),
    ExpressCard("water", "needs", ExpressTones.water),
    ExpressCard("toilet", "needs", ExpressTones.toilet),
    ExpressCard("food", "needs", ExpressTones.food),
    ExpressCard("rest", "needs", ExpressTones.rest),
    ExpressCard("phone", "needs", ExpressTones.phone),
    ExpressCard("medicine", "needs", ExpressTones.medicine),
    ExpressCard("mother", "people", ExpressTones.mother),
    ExpressCard("father", "people", ExpressTones.father),
    ExpressCard("brother", "people", ExpressTones.brother),
    ExpressCard("sister", "people", ExpressTones.sister),
    ExpressCard("friends", "people", ExpressTones.friends),
    ExpressCard("someone_else", "people", ExpressTones.someoneElse),
    ExpressCard("home", "places", ExpressTones.home),
    ExpressCard("school", "places", ExpressTones.school),
    ExpressCard("hospital", "places", ExpressTones.hospital),
    ExpressCard("bus_stand", "places", ExpressTones.busStand),
    ExpressCard("road", "places", ExpressTones.road),
    ExpressCard("yes", "answers", ExpressTones.yes),
    ExpressCard("no", "answers", ExpressTones.no),
    ExpressCard("dont_know", "answers", ExpressTones.dontKnow),
    ExpressCard("dont_understand", "answers", ExpressTones.dontUnderstand),
    ExpressCard("thank_you", "answers", ExpressTones.thankYou),
    ExpressCard("pain", "alert", ExpressTones.pain),
    ExpressCard("sick", "alert", ExpressTones.sick),
    ExpressCard("doctor", "alert", ExpressTones.doctor),
    ExpressCard("hospital", "alert", ExpressTones.hospital),
    ExpressCard("medicine", "alert", ExpressTones.medicine),
    ExpressCard("toilet", "alert", ExpressTones.toilet),
    ExpressCard("cold", "alert", ExpressTones.cold),
    ExpressCard("hot", "alert", ExpressTones.hot),
    ExpressCard("break", "alert", ExpressTones.breakTime),
    ExpressCard("help", "safety", ExpressTones.help),
    ExpressCard("stop", "safety", ExpressTones.stop),
    ExpressCard("police", "safety", ExpressTones.police),
    ExpressCard("scared", "safety", ExpressTones.scared),
    ExpressCard("alone", "safety", ExpressTones.alone),
    ExpressCard("someone_else", "safety", ExpressTones.someoneElse),
    ExpressCard("home", "safety", ExpressTones.home),
    ExpressCard("knife", "safety", ExpressTones.knife),
    ExpressCard("okay", "safety", ExpressTones.okay),
    ExpressCard("head", "body", ExpressTones.head),
    ExpressCard("throat", "body", ExpressTones.throat),
    ExpressCard("stomach", "body", ExpressTones.stomach),
    ExpressCard("arm", "body", ExpressTones.arm),
    ExpressCard("leg", "body", ExpressTones.leg),
    ExpressCard("back", "body", ExpressTones.back),
)

/** Speaks a tapped card aloud in the subject's language; silent when the phone has no offline voice for it. */
private class CardVoice(context: Context) {
    private val voice = app.signsetu.ui.components.OfflineVoice(context)
    fun speak(text: String, lang: String) { voice.speak(text, lang) }
    fun shutdown() = voice.shutdown()
}

/** The full-screen express board: large picture cards by category, each spoken aloud and sent as an answer. */
@Composable
fun FullChildMuteCommunicationBoard(
    content: ContentBundle,
    lang: String,
    chosenCard: String?,
    onCard: (String) -> Unit,
    onDismiss: () -> Unit,
    /** A touch on the body map; without it the body tab shows only its cards. */
    onBodyTouch: ((BodyTouch) -> Unit)? = null,
    /** A face and pain number from the stage's scale; without it the feelings tab shows only its cards. */
    onFeeling: ((Feeling) -> Unit)? = null,
    /** The person the subject points at as the one to call; without it the people tab shows only its cards. */
    onCall: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var tab by rememberSaveable { mutableStateOf(Tabs.first()) }
    var said by remember { mutableStateOf<ExpressCard?>(null) }
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val voice = remember(context) { CardVoice(context) }
    DisposableEffect(voice) { onDispose { voice.shutdown() } }
    val boardName = stringResource(R.string.board_name)
    val closeName = stringResource(R.string.close)
    fun label(id: String) = content.cards[id]?.label?.or(lang) ?: id

    Surface(color = StageTones.board, modifier = modifier.fillMaxSize().semantics { contentDescription = boardName }) {
        Column(Modifier.fillMaxSize().padding(Space.md)) {
            Row(Modifier.fillMaxWidth().padding(bottom = Space.sm), verticalAlignment = Alignment.CenterVertically) {
                AnimatedVisibility(said != null, Modifier.weight(1f), enter = fadeIn() + slideInVertically(), exit = fadeOut() + slideOutVertically()) {
                    said?.let { card ->
                        Surface(shape = RoundedCornerShape(Radius.r16), color = card.tone.bg, border = BorderStroke(Space.xxs, card.tone.ink)) {
                            Row(Modifier.padding(Space.s10), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s10)) {
                                CardImage(card.id, Modifier.size(Space.xxl))
                                Text(label(card.id), Modifier.weight(1f), fontFamily = Baloo, fontWeight = FontWeight.Bold, fontSize = TypeSize.s15, color = card.tone.ink)
                                Icon(Icons.Filled.Check, contentDescription = null, tint = card.tone.ink)
                            }
                        }
                    }
                }
                if (said == null) Spacer(Modifier.weight(1f))
                Surface(
                    shape = CircleShape,
                    color = StageScheme.surfaceVariant,
                    modifier = Modifier.padding(start = Space.sm).size(Space.s48).clickable { onDismiss() }.semantics { role = Role.Button; contentDescription = closeName },
                ) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.Close, contentDescription = null, tint = StageScheme.onSurfaceVariant) }
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(vertical = Space.s6).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                Tabs.forEach { id ->
                    val selected = tab == id
                    val title = content.categories.firstOrNull { it.id == id }?.label?.or(lang) ?: id
                    val fill by animateColorAsState(if (selected) StageScheme.primary else StageScheme.surfaceContainer)
                    Surface(
                        shape = RoundedCornerShape(Radius.r14),
                        color = fill,
                        modifier = Modifier.minimumInteractiveComponentSize().clickable { tab = id }.semantics { role = Role.Tab; contentDescription = title; this.selected = selected },
                    ) {
                        Text(
                            title,
                            fontFamily = Baloo,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = TypeSize.s13,
                            color = if (selected) Color.White else StageScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = Space.s14, vertical = Space.sm),
                        )
                    }
                }
            }

            val map = tab == "body" && onBodyTouch != null
            // The map takes most of the height; the body cards stay below it for a named answer.
            if (map) HurtMap({ t -> haptic.performHapticFeedback(HapticFeedbackType.LongPress); onBodyTouch(t) }, Modifier.fillMaxWidth().weight(0.62f)) { content.subjectText(it, lang) }
            // The scale and the family card sit above their tab's cards, so a face or a person is picked first.
            if (tab == "feelings") onFeeling?.let { pick -> FeelingPanel(content, lang, pick, Modifier.fillMaxWidth().weight(0.55f)) }
            if (tab == "people") onCall?.let { call -> FamilyCallCard(content, lang, call, Modifier.fillMaxWidth().weight(0.5f)) }
            val extra = map || (tab == "feelings" && onFeeling != null) || (tab == "people" && onCall != null)
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(top = Space.xs, bottom = Space.md),
                horizontalArrangement = Arrangement.spacedBy(Space.s10),
                verticalArrangement = Arrangement.spacedBy(Space.s10),
                modifier = Modifier.weight(if (extra) 0.38f else 1f),
            ) {
                items(ExpressCards.filter { it.tab == tab }, key = { it.id }) { card ->
                    val chosen = chosenCard == card.id
                    val text = label(card.id)
                    Surface(
                        shape = RoundedCornerShape(Radius.r20),
                        color = card.tone.bg,
                        border = BorderStroke(if (chosen) Space.s3 else Space.hair, if (chosen) card.tone.ink else card.tone.ink.copy(alpha = 0.2f)),
                        shadowElevation = if (chosen) Space.s6 else Space.xxs,
                        modifier = Modifier
                            .height(Space.s108)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                said = card
                                voice.speak(text, lang)
                                onCard(card.id)
                            }
                            .semantics { role = Role.Button; contentDescription = text },
                    ) {
                        Column(Modifier.fillMaxSize().padding(Space.s6), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            CardImage(card.id, Modifier.size(Space.s46))
                            Spacer(Modifier.height(Space.xs))
                            Text(
                                text,
                                fontFamily = Baloo,
                                fontWeight = FontWeight.Bold,
                                fontSize = TypeSize.s13,
                                color = card.tone.ink,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}
