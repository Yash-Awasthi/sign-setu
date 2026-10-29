package app.signsetu

import app.signsetu.gloss.Contributions
import app.signsetu.ui.session.SessionViewModel
import org.junit.Assert.assertTrue
import org.junit.Test

class InterviewUploadTest {
    /** The interviewed person never agreed to share: nothing in a session may hold the upload queue, so no
     * correction made there can reach it, whatever the phone's owner opted into. */
    @Test
    fun aSessionCannotReachTheUploadQueue() {
        val ctorParams = SessionViewModel::class.java.constructors.flatMap { it.parameterTypes.toList() }
        val fields = generateSequence<Class<*>>(SessionViewModel::class.java) { it.superclass }.flatMap { it.declaredFields.asSequence() }.map { it.type }.toList()
        assertTrue("constructor takes Contributions", ctorParams.none { Contributions::class.java.isAssignableFrom(it) })
        assertTrue("field holds Contributions", fields.none { Contributions::class.java.isAssignableFrom(it) })
    }
}
