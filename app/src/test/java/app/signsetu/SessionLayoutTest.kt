package app.signsetu

import app.signsetu.ui.session.SessionShape
import app.signsetu.ui.session.sessionShape
import org.junit.Assert.assertEquals
import org.junit.Test

/** The shape is a pure decision, so the dock can be proven for every configuration without a second device. */
class SessionLayoutTest {
    @Test
    fun phonePortraitIsPhone() = assertEquals(SessionShape.PHONE, sessionShape(360, 800, paired = false))

    @Test
    fun tabletPortraitIsTablet() = assertEquals(SessionShape.TABLET, sessionShape(800, 1280, paired = false))

    @Test
    fun pairedTabletPortraitStaysTablet() = assertEquals(SessionShape.TABLET, sessionShape(800, 1280, paired = true))

    @Test
    fun pairedLandscapeIsDock() = assertEquals(SessionShape.DOCK, sessionShape(1280, 800, paired = true))

    @Test
    fun unpairedLandscapeIsTabletNotDock() = assertEquals(SessionShape.TABLET, sessionShape(1280, 800, paired = false))

    @Test
    fun sixHundredIsTheTabletBoundary() = assertEquals(SessionShape.TABLET, sessionShape(600, 900, paired = false))
}
