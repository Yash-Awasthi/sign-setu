package app.signsetu.camera

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test
import kotlin.math.max

/** The skeleton overlay's reused paths, strokes and lists: a frame refills what the last one drew. */
class SkeletonInkTest {
    private val viewW = 1000f
    private val viewH = 800f
    private val aspect = 9f / 16f

    // A face where no two landmarks share a spot, so a misread index shows up in the points.
    private fun face(seed: Int = 0) = List(468) { Offset((it % 37) / 37f, ((it / 37 + seed) % 23) / 23f) }

    @Test fun theOutlinePointsAreTheMappedLandmarks() {
        val face = face()
        val out = FloatArray(FaceOutlines.all.maxOf { it.size } * 2)
        for (crop in listOf(false, true)) {
            for (line in FaceOutlines.all) {
                val n = outlinePoints(line, face, aspect, viewW, viewH, mirrored = true, crop = crop, out = out)
                val expected = line.map { overlayPoint(face[it], aspect, viewW, viewH, mirrored = true, crop = crop) }
                assertEquals(expected.size * 2, n)
                expected.forEachIndexed { i, p ->
                    assertEquals("crop $crop point $i x", p.x, out[i * 2], 1e-4f)
                    assertEquals("crop $crop point $i y", p.y, out[i * 2 + 1], 1e-4f)
                }
            }
        }
    }

    @Test fun aSecondFrameReusesTheSamePathPerOutline() {
        val ink = SkeletonInk()
        val first = ink.outlinePath(FaceOutlines.oval, face(), aspect, viewW, viewH, mirrored = true, crop = false)
        val second = ink.outlinePath(FaceOutlines.oval, face(seed = 1), aspect, viewW, viewH, mirrored = true, crop = false)
        assertSame("a new frame builds the outline into the path it already has", first, second)
        assertSame(ink.facePath[FaceOutlines.all.indexOf(FaceOutlines.oval)], second)
        // Each outline keeps its own path, or one drawn earlier in the frame would be overwritten.
        assertNotSame(first, ink.outlinePath(FaceOutlines.lips, face(), aspect, viewW, viewH, mirrored = true, crop = false))
    }

    @Test fun theStrokesAreKeptUntilTheCanvasResizes() {
        val ink = SkeletonInk()
        ink.fit(800f)
        val face = ink.face
        val thin = ink.thin
        val guide = ink.guide
        ink.fit(800f)
        assertSame(face, ink.face)
        assertSame(thin, ink.thin)
        assertSame(guide, ink.guide)
        val stroke = max(4f, 800f / 90f)
        assertEquals(stroke * 0.5f, ink.face.width, 1e-4f)
        assertEquals(stroke * 0.6f, ink.thin.width, 1e-4f)
        assertEquals(stroke * 0.9f, ink.thick.width, 1e-4f)
        assertEquals(800f / 80f, ink.guide.width, 1e-4f)
        ink.fit(1200f)
        assertNotSame("a resize rebuilds the strokes for the new width", face, ink.face)
        assertEquals(max(4f, 1200f / 90f) * 0.5f, ink.face.width, 1e-4f)
    }

    @Test fun theHandListsAreRefilledNotRebuilt() {
        val ink = SkeletonInk()
        val left = List(21) { Offset(0.7f, it / 100f) }
        val right = List(21) { Offset(0.3f, it / 100f) }
        labelledHands(LandmarkFrame(0L, leftHand = left, rightHand = right, aspect = aspect), ink.handTags, ink.handPoints)
        assertEquals(listOf("L", "R"), ink.handTags)
        assertEquals(listOf(left, right), ink.handPoints)
        val tags = ink.handTags
        val hands = ink.handPoints
        // Next frame: the short left hand is no hand at all, and the right is gone.
        labelledHands(LandmarkFrame(1L, leftHand = left.take(5), aspect = aspect), ink.handTags, ink.handPoints)
        assertSame(tags, ink.handTags)
        assertSame(hands, ink.handPoints)
        assertEquals(emptyList<String>(), ink.handTags)
        assertEquals(emptyList<List<Offset>>(), ink.handPoints)
    }
}
