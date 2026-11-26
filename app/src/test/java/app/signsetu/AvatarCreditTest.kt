package app.signsetu

import app.signsetu.ui.settings.AvatarCredit
import app.signsetu.ui.settings.LICENCES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The avatar's metadata requires credit (creditNotation: required), so the dialog must carry it. */
class AvatarCreditTest {
    @Test
    fun licencesScreenCreditsTheShippedAvatar() {
        val licence = LICENCES.firstOrNull { (name, _) -> name == AvatarCredit.ENTRY_NAME }?.second
        assertEquals(
            "the licences dialog must list ${AvatarCredit.ENTRY_NAME}",
            AvatarCredit.TEXT,
            licence,
        )
    }

    @Test
    fun theCreditNamesTheAuthorAndTheLicenceUrl() {
        assertTrue(AvatarCredit.ENTRY_NAME.contains(AvatarCredit.AUTHOR))
        assertTrue(AvatarCredit.ENTRY_NAME.contains(AvatarCredit.TITLE))
        assertTrue(AvatarCredit.TEXT.contains(AvatarCredit.LICENCE_URL))
        assertTrue(AvatarCredit.TEXT.contains(AvatarCredit.LICENCE_NAME))
    }

    @Test
    fun theLicencesScreenStillListsTheHandWrittenEntries() {
        assertTrue(LICENCES.size >= 26)
        assertTrue(LICENCES.any { (name, _) -> name == "Baloo 2 font" })
    }
}
