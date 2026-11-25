package app.signsetu

import android.graphics.BitmapFactory
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.signsetu.data.content.ContentBundle
import app.signsetu.ui.session.DrawStroke
import app.signsetu.ui.session.DrawingPanel
import app.signsetu.ui.theme.SignSetuTheme
import app.signsetu.ui.theme.SubjectTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicReference

/** A shape dragged on the board reaches the PNG the session keeps, and undo takes it back off. */
@RunWith(AndroidJUnit4::class)
class DrawingBoardDeviceTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var content: ContentBundle
    private val sent = AtomicReference<ByteArray?>(null)

    @Before
    fun load() {
        content = runBlocking { (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as SignSetuApp).container.content.load() }
    }

    private fun show(strokes: SnapshotStateList<DrawStroke>) {
        rule.setContent {
            SignSetuTheme {
                SubjectTheme {
                    DrawingPanel(content, "en", strokes, onDone = { sent.set(it) }, onBack = null, modifier = Modifier.size(360.dp, 420.dp))
                }
            }
        }
    }

    private val board get() = rule.onNodeWithContentDescription(rule.activity.getString(R.string.stage_draw_board))

    private fun done() {
        val word = content.subjectText("done", "en")
        if (word != null) rule.onNodeWithText(word).performClick() else rule.onNodeWithContentDescription(rule.activity.getString(R.string.stage_done)).performClick()
    }

    private fun undo() {
        val word = content.subjectText("undo", "en")
        if (word != null) rule.onNodeWithContentDescription(word).performClick() else rule.onNodeWithContentDescription("Undo").performClick()
    }

    private fun drag() = board.performTouchInput {
        swipe(Offset(width * 0.10f, height * 0.15f), Offset(width * 0.80f, height * 0.70f), 200)
    }

    private fun tool(name: Int) = rule.onNodeWithContentDescription(rule.activity.getString(name)).performClick()

    @Test
    fun aRectangleReachesTheKeptPngAndLeavesItsInsidePaper() {
        val strokes = mutableStateListOf<DrawStroke>()
        show(strokes)
        tool(R.string.stage_tool_rect)
        drag()
        rule.waitForIdle()
        assertEquals("one shape, not a freehand trail", 1, strokes.size)
        assertEquals("a rectangle is drawn as its four sides", 5, strokes.first().points.size)

        done()
        rule.waitForIdle()
        val png = sent.get()!!
        assertTrue("the sketch is what the session records", png.isNotEmpty())
        val bmp = BitmapFactory.decodeByteArray(png, 0, png.size)
        val x1 = (bmp.width * 0.10f).toInt()
        val y1 = (bmp.height * 0.15f).toInt()
        val x2 = (bmp.width * 0.80f).toInt()
        val y2 = (bmp.height * 0.70f).toInt()
        // The drag's slop trims a few pixels at the start; the box the shape covers is still the drag's box.
        val slack = 60
        val ink = ArrayList<Pair<Int, Int>>()
        for (y in 0 until bmp.height) for (x in 0 until bmp.width) if (isInk(bmp.getPixel(x, y))) ink.add(x to y)
        assertTrue("a shape was drawn", ink.size > 100)
        assertTrue("left side reaches the drag's start: ${ink.minOf { it.first }} vs $x1", kotlin.math.abs(ink.minOf { it.first } - x1) <= slack)
        assertTrue("right side reaches the drag's end: ${ink.maxOf { it.first }} vs $x2", kotlin.math.abs(ink.maxOf { it.first } - x2) <= slack)
        assertTrue("top side reaches the drag's start: ${ink.minOf { it.second }} vs $y1", kotlin.math.abs(ink.minOf { it.second } - y1) <= slack)
        assertTrue("bottom side reaches the drag's end: ${ink.maxOf { it.second }} vs $y2", kotlin.math.abs(ink.maxOf { it.second } - y2) <= slack)
        val xm = (x1 + x2) / 2
        val ym = (y1 + y2) / 2
        for (dy in -10..10) for (dx in -10..10) {
            val middle = bmp.getPixel(xm + dx, ym + dy)
            assertTrue("the inside is paper, not a filled box: $middle at $dx,$dy", !isInk(middle))
        }
    }

    private fun isInk(c: Int) = (c shr 16 and 0xFF) < 110 && (c shr 8 and 0xFF) < 110 && (c and 0xFF) < 110

    @Test
    fun undoTakesTheShapeBackOffTheBoard() {
        val strokes = mutableStateListOf<DrawStroke>()
        show(strokes)
        tool(R.string.stage_tool_oval)
        drag()
        rule.waitForIdle()
        assertEquals(1, strokes.size)
        undo()
        rule.waitForIdle()
        assertEquals("undo removes the whole shape", 0, strokes.size)
        done()
        rule.waitForIdle()
        assertNull("nothing left to send, so nothing is recorded", sent.get())
    }

    @Test
    fun anArrowLeavesItsShaftAndTwoHeadLines() {
        val strokes = mutableStateListOf<DrawStroke>()
        show(strokes)
        tool(R.string.stage_tool_arrow)
        drag()
        rule.waitForIdle()
        assertEquals("shaft and two head lines, undone as one gesture", 3, strokes.size)
    }
}
