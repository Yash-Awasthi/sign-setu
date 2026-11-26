package app.signsetu.ui.session

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import app.signsetu.data.settings.SwitchAccess
import app.signsetu.ui.theme.Space
import app.signsetu.ui.theme.StageTones
import kotlinx.coroutines.delay

/** How often the dwell scan checks the clock; small enough to feel immediate, cheap enough to leave on. */
private const val SWITCH_TICK_MS = 60L

/**
 * Keyboard and switch state for one answer surface: where the highlight is, and what a key or
 * switch press does. The decisions live in [AnswerOrder] and [SwitchScan]; this only applies them
 * to focus and to the control's own tap.
 */
@Stable
internal class AnswerFlow(
    val order: AnswerOrder,
    val switch: SwitchAccess,
    private val onKeyboard: () -> Unit,
    private val onLeave: () -> Unit,
) {
    val requesters: Map<String, FocusRequester> = order.ids.associateWith { FocusRequester() }

    /** The control the keyboard or switch is on, or null before either has been used. */
    var focused by mutableStateOf<String?>(null)
        private set

    /** A control took or lost focus; the ring follows the real focus, not a request that failed. */
    fun noteFocus(id: String, has: Boolean) {
        if (has) focused = id else if (focused == id) focused = null
    }

    private val taps = mutableMapOf<String, () -> Unit>()
    private val scan = SwitchScan(order, switch)
    private var scanState: SwitchScan.State? = null

    fun bind(id: String, tap: () -> Unit) { taps[id] = tap }
    fun unbind(id: String) { taps.remove(id) }

    fun focus(id: String) {
        if (id !in requesters) return
        focused = id
        runCatching { requesters.getValue(id).requestFocus() }
    }

    fun choose(id: String) { taps[id]?.invoke() }

    /** Start the dwell scan on the first control, so a single switch has somewhere to land. */
    fun startScanning(now: Long) {
        scanState = scan.start(now)
        focus(scan.order.ids.first())
    }

    /** Move the dwell highlight on when its time is up; called on a timer, never by a key. */
    fun tick(now: Long) {
        val state = scanState ?: scan.start(now).also { scanState = it }
        val (next, action) = scan.tick(state, now)
        scanState = next
        if (action is SwitchAction.Move) focus(action.id)
    }

    /** A key on [id]: true when the flow took it. */
    fun onKey(id: String, event: KeyEvent): Boolean {
        val select = event.key == Key.Enter || event.key == Key.NumPadEnter || event.key == Key.Spacebar || event.key == Key.DirectionCenter
        if (switch != SwitchAccess.OFF && select) return switchKey(id, event)
        // Space and Enter also click a Surface on key-up; the flow answered on key-down, so swallow the up.
        if (event.type == KeyEventType.KeyUp) return select
        if (event.type != KeyEventType.KeyDown) return false
        val key = answerKey(event) ?: return false
        onKeyboard()
        return when (val action = order.key(id, key)) {
            is AnswerAction.Move -> { focus(action.id); true }
            is AnswerAction.Choose -> { choose(action.id); true }
            AnswerAction.Leave -> { onLeave(); true }
            AnswerAction.Ignore -> false
        }
    }

    /** One switch: a press chooses in dwell mode, and in step mode a tap advances and a hold chooses. */
    private fun switchKey(id: String, event: KeyEvent): Boolean {
        val now = System.currentTimeMillis()
        onKeyboard()
        val state = scanState ?: scan.start(now).also { scanState = it }
        val outcome = when (event.type) {
            KeyEventType.KeyDown -> scan.press(state, now)
            KeyEventType.KeyUp -> scan.up(state, now)
            else -> return false
        }
        scanState = outcome.first
        when (val action = outcome.second) {
            is SwitchAction.Move -> focus(action.id)
            is SwitchAction.Choose -> choose(action.id)
            null -> Unit
        }
        return true
    }
}

/** The keyboard's own keys, or null for one the answer flow leaves to the platform. */
internal fun answerKey(event: KeyEvent): AnswerKey? = when (event.key) {
    Key.Tab -> if (event.isShiftPressed) AnswerKey.PREVIOUS else AnswerKey.NEXT
    Key.DirectionRight -> AnswerKey.RIGHT
    Key.DirectionLeft -> AnswerKey.LEFT
    Key.DirectionDown -> AnswerKey.DOWN
    Key.DirectionUp -> AnswerKey.UP
    Key.Enter, Key.NumPadEnter, Key.Spacebar, Key.DirectionCenter -> AnswerKey.SELECT
    Key.Escape -> AnswerKey.ESCAPE
    else -> null
}

/** The flow for one answer surface, kept across recompositions and restarted only on a new set of controls. */
@Composable
internal fun rememberAnswerFlow(
    ids: List<String>,
    columns: Int,
    switch: SwitchAccess,
    onKeyboard: () -> Unit,
    onLeave: () -> Unit,
): AnswerFlow {
    val keyboard by rememberUpdatedState(onKeyboard)
    val leave by rememberUpdatedState(onLeave)
    val flow = remember(ids, columns, switch) {
        AnswerFlow(AnswerOrder(ids, columns), switch, { keyboard() }, { leave() })
    }
    // A switch needs a highlight before it is pressed: dwell then moves it, step leaves it on the first.
    LaunchedEffect(flow, switch) {
        if (switch == SwitchAccess.OFF || flow.order.ids.isEmpty()) return@LaunchedEffect
        flow.startScanning(System.currentTimeMillis())
        if (switch != SwitchAccess.DWELL) return@LaunchedEffect
        while (true) {
            delay(SWITCH_TICK_MS)
            flow.tick(System.currentTimeMillis())
        }
    }
    return flow
}

/**
 * Makes one answer control a stop on the keyboard and switch path: it takes focus, reports it to
 * [flow], and hands its keys to the flow. Put it before the control's own clickable, so keys the
 * control consumes (Enter on a Surface) are not counted twice.
 */
@Composable
internal fun Modifier.answerTarget(flow: AnswerFlow, id: String, onTap: () -> Unit): Modifier {
    val tap by rememberUpdatedState(onTap)
    DisposableEffect(flow, id) {
        flow.bind(id) { tap() }
        onDispose { flow.unbind(id) }
    }
    return this
        .focusRequester(flow.requesters.getValue(id))
        .onFocusChanged {
            flow.noteFocus(id, it.isFocused)
        }
        .onKeyEvent { event -> flow.onKey(id, event) }
}

/**
 * A ring round a control the keyboard or switch is on: white outside, blue inside, so it reads on
 * any answer colour and in both stage palettes. It follows the control's own shape.
 */
internal fun Modifier.focusRing(shown: Boolean, shape: Shape, colour: Color = StageTones.focus): Modifier =
    if (!shown) this else drawWithContent {
        drawContent()
        val width = Space.s6.toPx()
        // Inset by half the stroke: a stroke on the control's edge would be cut off by the clip.
        val outline = shape.createOutline(
            Size((size.width - width).coerceAtLeast(0f), (size.height - width).coerceAtLeast(0f)),
            layoutDirection,
            this,
        )
        translate(width / 2f, width / 2f) {
            drawOutline(outline, Color.White, style = Stroke(width = width))
            drawOutline(outline, colour, style = Stroke(width = Space.s3.toPx()))
        }
    }
