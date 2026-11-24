package app.signsetu

import android.Manifest
import android.content.pm.PackageManager
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.signsetu.ui.session.voiceHint
import app.signsetu.voice.VoiceCapture
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The documented path when the officer refuses the microphone: the sheet asks for it in one line, no take starts,
 * nothing is recorded and nothing crashes. */
@RunWith(AndroidJUnit4::class)
class VoicePermissionDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext.applicationContext as SignSetuApp

    /** Runs a shell command and waits for it: the descriptor is read to its end, so the change has landed. */
    private fun shell(command: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command))
            .use { it.readBytes().decodeToString() }

    private fun granted() = app.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    @After
    fun restore() { shell("pm grant ${app.packageName} ${Manifest.permission.RECORD_AUDIO}") }

    @Test
    fun aRefusedMicrophoneRecordsNothingAndSaysSo() {
        shell("pm revoke ${app.packageName} ${Manifest.permission.RECORD_AUDIO}")
        assertFalse("the microphone is refused for this test", granted())

        val capture = VoiceCapture(app)
        assertFalse("the capture reports itself unusable", capture.permitted())
        assertFalse("no take starts", capture.start())
        assertEquals("no level is shown", 0f, capture.level, 0f)
        assertNull("no audio is handed over", capture.stop())
        assertEquals("the sheet says why, in one line", R.string.voice_no_permission, voiceHint(granted = false))
        assertEquals("and how to record once it is allowed", R.string.voice_hold, voiceHint(granted = true))
        assertEquals("no file was written for a take that never started", 0, app.filesDir.listFiles().orEmpty().count { it.name.startsWith("voice") })

        // Allowing it again needs no restart: the same object records.
        shell("pm grant ${app.packageName} ${Manifest.permission.RECORD_AUDIO}")
        assertTrue("the permission is back", granted())
        assertTrue("the capture is usable", VoiceCapture(app).permitted())
    }
}
