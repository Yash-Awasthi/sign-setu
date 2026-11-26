package app.signsetu

import android.content.ContextWrapper
import androidx.lifecycle.viewModelScope
import app.signsetu.data.session.EventSource
import app.signsetu.data.session.EventType
import app.signsetu.data.session.Profile
import app.signsetu.data.session.SessionEvent
import app.signsetu.data.session.SessionRepository
import app.signsetu.export.EvidenceIntegrity
import app.signsetu.export.SessionReport
import app.signsetu.ui.session.SessionViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/** A recorded voice is kept only after the person's consent, and that consent is one sealed SYSTEM row by the officer
 * so a court can see it was given. Without it nothing of the voice is written. */
@OptIn(ExperimentalCoroutinesApi::class)
class VoiceConsentTest {
    private val content = shipped
    private val vms = mutableListOf<SessionViewModel>()

    @Before fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun unmain() {
        runBlocking { vms.forEach { it.viewModelScope.coroutineContext.job.cancelAndJoin() } }
        Dispatchers.resetMain()
    }

    private fun TestScope.waitFor(what: String, cond: () -> Boolean) {
        val end = System.currentTimeMillis() + 30_000
        while (true) {
            runCurrent()
            if (cond()) return
            if (System.currentTimeMillis() > end) fail("timed out waiting for $what")
            Thread.sleep(5)
        }
    }

    private fun SessionViewModel.events(): List<SessionEvent> = ui.value?.events.orEmpty()

    /** A session with the opening on the stage, as the officer leaves it to prepare the first question. */
    private suspend fun TestScope.opened(dao: MemoryDao = MemoryDao()): Pair<SessionViewModel, SessionRepository> {
        var clock = 1_000L
        val repo = SessionRepository(dao, clock = { clock += 1_000; clock })
        val vm = SessionViewModel(repo.start(Profile.ISL_USER, "hi"), repo, content, "en", SessionReport(ContextWrapper(null))).also { vms += it }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.ui.collect {} }
        waitFor("the opening on the stage") { vm.ui.value?.onStage is app.signsetu.ui.session.Pending.GroundRules }
        vm.returnToOfficer()
        return vm to repo
    }

    private fun wav(seconds: Int) = ByteArray(44 + 2 * 16_000 * seconds) { (it % 251).toByte() }

    @Test
    fun speechBeforeConsentIsNeitherKeptNorCountedAsAnAnswer() = runTest {
        val (vm, _) = opened()
        val before = vm.events().size
        vm.recordSpeech(wav(1), null)
        runCurrent()
        assertFalse("no consent was given", vm.voiceConsented)
        assertEquals("nothing was written", emptyList<SessionEvent>(), vm.events().drop(before))
        assertTrue(vm.events().none { it.type == EventType.ANSWER_SPOKEN })
    }

    @Test
    fun consentIsOneSealedSystemRowWrittenByTheOfficer() = runTest {
        val (vm, repo) = opened()
        vm.consentToVoice()
        waitFor("the consent row") { vm.events().any { it.text == "voice_consent" } }
        vm.consentToVoice()
        vm.consentToVoice()
        runCurrent()

        val rows = vm.events().filter { it.type == EventType.SYSTEM && it.text == "voice_consent" }
        assertEquals("consent is asked once per session", 1, rows.size)
        val row = rows.single()
        assertEquals("the officer confirms it", EventSource.OFFICER, row.source)
        assertEquals("in the officer's language", "en", row.lang)
        assertTrue("the row is part of the chain", row.hash != null)
        assertTrue(vm.voiceConsented)

        val chain = EvidenceIntegrity.verifyChain(repo.session(vm.sessionId).first()!!, repo.eventsNow(vm.sessionId), drawing = { null })
        assertTrue("the consent row is sealed like every other", chain.intact)
    }

    @Test
    fun speechAfterConsentIsTheSubjectsAnswerAndItsBytesAreSealed() = runTest {
        val (vm, repo) = opened()
        vm.consentToVoice()
        waitFor("the consent") { vm.voiceConsented }

        val recording = wav(1)
        vm.recordSpeech(recording, null)
        waitFor("the spoken answer") { vm.events().any { it.type == EventType.ANSWER_SPOKEN } }

        val row = vm.events().single { it.type == EventType.ANSWER_SPOKEN }
        assertEquals("the person's own answer", EventSource.SUBJECT, row.source)
        assertEquals("in the person's language", "hi", row.lang)
        assertEquals("one second of speech", "1.0", row.text)
        assertNotNull("the row names the recording", row.audioId)
        assertArrayEquals("the bytes kept are the bytes sealed", recording, repo.audio(row.audioId!!))

        val chain = EvidenceIntegrity.verifyChain(
            repo.session(vm.sessionId).first()!!, repo.eventsNow(vm.sessionId),
            { repo.audio(it) }, { repo.landmarks(it) },
        ) { repo.drawing(it) }
        assertTrue("the recording's bytes are part of the chain", chain.intact)
    }
}
