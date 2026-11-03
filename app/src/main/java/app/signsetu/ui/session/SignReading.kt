package app.signsetu.ui.session

import app.signsetu.camera.Head
import app.signsetu.camera.LandmarkFrame
import app.signsetu.data.content.ContentBundle
import app.signsetu.export.StationExport
import app.signsetu.net.BrainClient
import app.signsetu.sign.DominantHand
import app.signsetu.sign.FeatureRing
import app.signsetu.sign.HomeSigns
import app.signsetu.sign.LIVE_PEEK_MS
import app.signsetu.sign.LetterCommitter
import app.signsetu.sign.MotionGate
import app.signsetu.sign.SignCapture
import app.signsetu.sign.SignGuess
import app.signsetu.sign.SignReader
import app.signsetu.sign.SignSegment
import app.signsetu.sign.SignWindow
import app.signsetu.sign.decideRead
import app.signsetu.sign.handEnergy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/** A station read later than this is not waited for; the phone's own reading stands. */
private const val STATION_SIGN_MS = 2_500

/** How long before a sign window opens and after it closes a nod or shake still counts for its reading. */
internal const val HEAD_MARGIN_MS = 5_000L
private const val HEAD_KEEP_MS = 30_000L
private const val LIVE_MIN_FRAMES = 8
private const val LETTER_PEEK_MS = 150L
private const val LETTER_FRAMES = 8

/** The camera side of a session: frames in, sign windows cut and read, nods and shakes kept by camera time.
 * Frames arrive on the analysis thread; everything else runs on the view model's scope. */
internal class SignReading(
    private val scope: CoroutineScope,
    private val content: ContentBundle,
    reader: SignReader?,
    /** A segment was cut from the window; its reading follows through [onRead]. */
    private val onSegment: (SignSegment) -> Unit,
    /** The segment's reading replaces the placeholder [onSegment] added. */
    private val onRead: (placeholder: SignSegment, read: SignSegment) -> Unit,
) {
    /** Set once the reader is built off the main thread; reads before then get no machine guesses. */
    @Volatile var reader: SignReader? = reader

    /** The station, when paired: it reads each sign first once its heartbeat says it has a reader. */
    @Volatile var station: BrainClient? = null
    private val confirmedNames: MutableSet<String> = ConcurrentHashMap.newKeySet()
    // Reads the current reader, not the one the session started with: the sign language can change mid-session.
    private val homeSigns = HomeSigns { frames -> this.reader?.let { r -> runCatching { r.embed(frames) }.getOrNull() } }
    // The leading hand, learnt over the session: home signs are kept and matched as if signed with the right hand.
    private val dominant = DominantHand()
    private val ring = FeatureRing()
    private val gate = MotionGate()
    private val letters = LetterCommitter()
    @Volatile private var prevFrame: LandmarkFrame? = null
    @Volatile private var peeking = false
    private var lastPeekMs = 0L
    private var lastLetterMs = 0L
    private val liveFlow = MutableStateFlow(LiveRead())
    val live: StateFlow<LiveRead> = liveFlow

    @Volatile private var capture: SignCapture? = null
    // Cards that answer the item shown when the window opened: the reader's guesses that match them come first.
    @Volatile private var context: Set<String> = emptySet()
    // Added to on the camera's analysis thread, drained on the main thread when the window closes.
    private val reads = CopyOnWriteArrayList<Job>()
    private val readDispatcher = Dispatchers.Default.limitedParallelism(2)

    /** Every nod and shake by camera time, kept for [HEAD_KEEP_MS]: a head answer often comes before the hands are up
     * or after they drop, so a reading takes those from [HEAD_MARGIN_MS] either side of its window. */
    private val heads = ArrayDeque<Pair<Long, Head>>()
    @Volatile var lastFrameMs = 0L
        private set
    @Volatile var lastBody: LandmarkFrame? = null
        private set
    @Volatile var windowStartMs = 0L
        private set
    @Volatile var windowEndMs = Long.MIN_VALUE / 2

    val capturing: Boolean get() = capture != null

    /** A nod or shake now still belongs to the window open or just closed. */
    fun inHeadMargin(): Boolean = capture != null || lastFrameMs - windowEndMs <= HEAD_MARGIN_MS

    fun headsAround(from: Long, to: Long): Set<Head> = synchronized(heads) {
        heads.filter { (ms, _) -> ms in (from - HEAD_MARGIN_MS)..(to + HEAD_MARGIN_MS) }.map { it.second }.toSet()
    }

    /** Opens a window; false when one is already open. */
    fun start(window: SignWindow, cards: Set<String>): Boolean {
        if (capture != null) return false
        context = cards
        synchronized(ring) { ring.clear(); letters.clear(); gate.reset() }
        liveFlow.value = LiveRead()
        windowStartMs = lastFrameMs
        capture = SignCapture(window)
        return true
    }

    /** Every tracked camera frame, on the analysis thread; true when the open window should close. */
    fun onFrame(frame: LandmarkFrame): Boolean {
        lastFrameMs = frame.timestampMs
        if (frame.pose != null || frame.leftHand != null || frame.rightHand != null) lastBody = frame
        frame.head?.let { h ->
            synchronized(heads) {
                heads.addLast(frame.timestampMs to h)
                while (frame.timestampMs - heads.first().first > HEAD_KEEP_MS) heads.removeFirst()
            }
            if (app.signsetu.BuildConfig.DEBUG) android.util.Log.d("SignSetuGloss", "head $h at ${frame.timestampMs}")
        }
        val c = capture
        if (c != null) {
            dominant.see(handEnergy(prevFrame, frame))
            peek(frame)
        }
        prevFrame = frame
        c ?: return false
        // Three seconds with nothing raised, or two since the hands stopped: what was given is ingested, no tap needed.
        val (segments, done) = synchronized(c) {
            val wasFull = c.full
            val closed = c.add(frame)
            closed to (!wasFull && c.full || c.settled())
        }
        segments.forEach(::readSegment)
        return done
    }

    /** Closes the open window; the reads still running, or null when no window was open. */
    fun close(): List<Job>? {
        val c = capture ?: return null
        capture = null
        windowEndMs = lastFrameMs
        return synchronized(c) {
            c.finish().forEach(::readSegment)
            reads.toList().also { reads.clear() }
        }
    }

    /** The letters held one by one in the window just read; the live view is cleared. */
    fun takeSpelled(): String {
        val spelled = liveFlow.value.spelled
        liveFlow.value = LiveRead()
        return spelled
    }

    /** A spelled name the officer accepted is this person's: it ranks first the next time it is spelled. */
    fun confirmName(word: String) {
        confirmedNames += word.lowercase()
        reader?.sessionNames(confirmedNames.toSet())
    }

    /** The officer named what a sign meant; it is kept as this person's own sign. */
    fun teach(frames: List<LandmarkFrame>, word: String) = homeSigns.teach(dominant.canonical(frames), word)

    /** Landmarks are biometric: every frame held is dropped and every read in flight is cancelled. */
    fun wipe() {
        capture = null
        lastBody = null
        prevFrame = null
        synchronized(ring) { ring.clear(); letters.clear() }
        liveFlow.value = LiveRead()
        reads.forEach { it.cancel() }
        reads.clear()
    }

    /** The reader outlives the session; the names learned in it must not. */
    fun forgetNames() {
        confirmedNames.clear()
        reader?.sessionNames(emptySet())
    }

    /** On the analysis thread, inside a window: the last frames are read while the hands move, at most every
     * [LIVE_PEEK_MS], and a still handshape is read as a letter every [LETTER_PEEK_MS]. */
    private fun peek(frame: LandmarkFrame) {
        val r = reader ?: return
        val moving: Boolean
        val window: List<LandmarkFrame>
        synchronized(ring) {
            moving = gate.update(handEnergy(prevFrame, frame).total)
            ring.push(frame)
            val ms = frame.timestampMs
            val hands = frame.leftHand != null || frame.rightHand != null
            val wordDue = moving && ms - lastPeekMs >= LIVE_PEEK_MS && ring.size >= LIVE_MIN_FRAMES
            val letterDue = !moving && hands && ms - lastLetterMs >= LETTER_PEEK_MS && ring.size >= LETTER_FRAMES
            if (peeking || !(wordDue || letterDue)) {
                // Moving or lowering the hand ends a held letter, so the same letter may follow it.
                if (!hands || moving) letters.see(ms, null)
                return
            }
            if (wordDue) lastPeekMs = ms else lastLetterMs = ms
            window = ring.frames(if (wordDue) ring.size else LETTER_FRAMES)
            peeking = true
        }
        val job = scope.launch(readDispatcher) {
            try {
                if (moving) {
                    val top = runCatching { r.read(window) }.getOrNull().orEmpty().sortedByDescending { it.score }.take(3)
                    if (capture != null) liveFlow.update { it.copy(guesses = top) }
                } else {
                    val g = runCatching { r.letter(window) }.getOrNull()
                    val committed = synchronized(ring) { letters.see(window.last().timestampMs, g) }
                    if (committed != null && capture != null) liveFlow.update { it.copy(spelled = it.spelled + committed) }
                }
            } finally { peeking = false }
        }
        // A launch on a cancelled scope never runs its finally; the flag must not stay up.
        if (job.isCancelled) peeking = false
    }

    private fun readSegment(frames: List<LandmarkFrame>) {
        val cards = context
        val segment = SignSegment(frames, context = cards)
        onSegment(segment)
        reads += scope.launch(readDispatcher) {
            // The station's reader goes first and this phone's runs beside it, so a slow or failed station costs nothing.
            // Only a connected station with a reader that is answering is waited for; otherwise the phone reads alone, at once.
            val st = station?.takeIf { frames.isNotEmpty() && it.can(BrainClient.Feature.ISL) }
            // A failure stays inside the request: thrown from async it would fail this read and the window with it.
            val remote = st?.let { s ->
                async(Dispatchers.IO) { runCatching { s.sign(JsonArray(frames.map(StationExport::frameJson)), frames.first().aspect, STATION_SIGN_MS) }.getOrNull() }
            }
            val r = reader
            val mine = r?.let { runCatching { it.read(frames) }.getOrNull() }.orEmpty()
            // A late station request is cancelled, so closing the window never waits on it.
            val theirs = remote?.let { d -> withTimeoutOrNull(STATION_SIGN_MS.toLong()) { d.await() }.also { if (it == null) d.cancel() } }
                ?.map { (w, p) -> SignGuess(w, p) }
            val all = (theirs ?: mine).sortedByDescending { it.score }
            // The phone's conformal thresholds were fitted to the phone's reader, not the station's.
            val read = decideRead(frames, all, homeSigns.match(dominant.canonical(frames)), cards, r?.words.orEmpty(), content.words, r?.cards.orEmpty(), if (theirs != null) null else r?.conformal)
            onRead(segment, read)
        }
    }
}
