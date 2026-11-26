package app.signsetu

import app.signsetu.data.session.EventSeal
import app.signsetu.data.session.EventSource
import app.signsetu.data.session.EventType
import app.signsetu.data.session.Profile
import app.signsetu.data.session.SessionRepository
import app.signsetu.export.EvidenceIntegrity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What finishing, reopening and discarding do to the sealed record: the seal covers the rows that were saved, an
 * edit under it is found, and the unsaved tail leaves no trace. */
class SessionFinishTest {
    private val dao = MemoryDao()
    private var now = 1_000L
    private val repo = SessionRepository(dao, clock = { now })

    private suspend fun recorded(): String {
        val id = repo.start(Profile.ISL_USER, "hi")
        now = 2_000
        repo.log(id, EventType.QUESTION_SHOWN, EventSource.PRESET, "en", text = "Are you safe?", cardId = "safe")
        now = 3_000
        repo.log(id, EventType.ANSWER_CARD, EventSource.SUBJECT, "hi", text = "Yes", cardId = "yes")
        now = 4_000
        repo.log(id, EventType.NOTE, EventSource.OFFICER, "en", text = "calm")
        return id
    }

    private suspend fun chain(id: String) =
        EvidenceIntegrity.verifyChain(
            repo.session(id).first()!!, repo.eventsNow(id),
            { repo.audio(it) }, { repo.landmarks(it) },
        ) { repo.drawing(it) }

    @Test
    fun finishingSealsTheSameRowsAndWritesNoNewOne() = runBlocking {
        val id = recorded()
        val before = repo.currentSeal(id)!!
        assertEquals(3, before.count)
        assertEquals(repo.head(id), before.head)

        now = 5_000
        repo.end(id, "  FIR 214/2026 Raipur  ")
        val after = repo.currentSeal(id)!!
        assertEquals("ending adds no row", before.count, after.count)
        assertEquals("ending adds no row to the head either", before.head, after.head)

        val session = repo.session(id).first()!!
        assertEquals("FIR 214/2026 Raipur", session.title)
        assertEquals(5_000L, session.endedAt)
        assertEquals("the stored root is the seal's", after.root, session.rootHash)
        assertNull("no device key in a JVM test", session.rootSig)
        assertTrue("the row check still passes after finish", chain(id).intact)
        assertEquals(before.head, chain(id).head)
    }

    @Test
    fun anEditUnderAFinishedRecordIsFoundAtTheEditedRow() = runBlocking {
        val id = recorded()
        repo.end(id, "FIR 1")
        assertTrue(chain(id).intact)

        dao.rows.update { rows -> rows.map { if (it.type == EventType.ANSWER_CARD.name) it.copy(text = "No", cardId = "no") else it } }
        val checked = chain(id)
        assertFalse("an answer changed after the seal was checked", checked.intact)
        assertEquals("the first changed row", 2L, checked.brokenAt)
        assertEquals(
            "the stored root still names the seals it was given; only the row check catches the edit",
            repo.session(id).first()!!.rootHash, repo.currentSeal(id)!!.root,
        )
    }

    @Test
    fun reopeningDropsOnlyTheUnsavedTailAndTheChainStillVerifies() = runBlocking {
        val id = recorded()
        now = 5_000
        repo.end(id, "FIR 1")
        val saved = repo.currentSeal(id)!!

        repo.reopen(id)
        assertNull("a reopened session is not finished", repo.session(id).first()!!.endedAt)
        assertEquals("reopening remembers the last saved row", 3L, repo.session(id).first()!!.savedThrough)
        now = 6_000
        repo.log(id, EventType.NOTE, EventSource.OFFICER, "en", text = "after reopen")
        assertEquals("new rows extend the chain", saved.count + 1, repo.currentSeal(id)!!.count)

        repo.discardUnsaved(id, "FIR 1")
        assertEquals("only the tail is gone", 3, repo.eventsNow(id).size)
        val after = repo.currentSeal(id)!!
        assertEquals(saved.count, after.count)
        assertEquals(saved.head, after.head)
        assertEquals(saved.root, after.root)
        assertNotNull("the session is saved again", repo.session(id).first()!!.endedAt)
        assertEquals("FIR 1", repo.session(id).first()!!.title)
        assertTrue("the truncated chain still verifies", chain(id).intact)
    }

    @Test
    fun aSealWithNoRowsIsTheGenesisNotAnEmptyString() = runBlocking {
        val id = repo.start(Profile.ISL_USER, "hi")
        val seal = repo.currentSeal(id)!!
        assertEquals(0, seal.count)
        assertEquals(EventSeal.genesis(id, 1_000L), seal.head)
        assertEquals(EventSeal.merkleRoot(emptyList()), seal.root)
        assertEquals(EventSeal.sealLine(id, 0, seal.head, seal.root), seal.line)
    }
}
