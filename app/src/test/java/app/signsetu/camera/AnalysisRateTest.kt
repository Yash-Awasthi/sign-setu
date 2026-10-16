package app.signsetu.camera

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisRateTest {
    @Test fun firstFrameIsAnalysedWhateverItsTimestamp() {
        assertTrue(analysisDue(null, 23_277_574L))
        assertTrue(analysisDue(null, 0L))
    }

    @Test fun laterFramesWaitForTheInterval() {
        assertFalse(analysisDue(1_000L, 1_010L))
        assertTrue(analysisDue(1_000L, 1_030L))
    }
}
