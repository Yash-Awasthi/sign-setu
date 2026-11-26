package app.signsetu

import app.signsetu.ui.session.relatedCards
import org.junit.Assert.assertEquals
import org.junit.Test

/** The six preset groups, matched whole-word in English, Devanagari and romanised spelling. */
class RelatedChipsTest {
    private val all = setOf("water", "food", "family", "home", "toilet", "medicine")

    @Test
    fun everyEnglishWordOffersItsCardInCanonicalOrder() {
        assertEquals(
            listOf("water", "food", "family", "home", "toilet", "medicine"),
            relatedCards("medicine toilet house home family food water", all),
        )
        assertEquals(listOf("home"), relatedCards("house", all))
    }

    @Test
    fun devanagariWordsMatch() {
        assertEquals(listOf("water", "home", "medicine"), relatedCards("मुझे पानी चाहिए, घर जाना है, दवा भी", all))
        assertEquals(listOf("home"), relatedCards("मकान", all))
        assertEquals(listOf("toilet"), relatedCards("टॉयलेट", all))
        assertEquals(listOf("toilet"), relatedCards("शौचालय", all))
    }

    @Test
    fun romanisedWordsMatch() {
        assertEquals(listOf("water", "food", "family", "home", "toilet", "medicine"), relatedCards("paani khaana parivar ghar shauchalay dawai", all))
        assertEquals(listOf("water"), relatedCards("pani", all))
    }

    @Test
    fun aWordThatOnlyContainsAnotherDoesNotMatch() {
        assertEquals(emptyList<String>(), relatedCards("watermelon watermelonish", all))
        assertEquals(listOf("water"), relatedCards("watermelon and water", all))
    }

    @Test
    fun theSameCardIsOfferedOnce() {
        assertEquals(listOf("water"), relatedCards("water paani पानी WATER", all))
    }

    @Test
    fun nothingRelatedMeansNoChips() {
        assertEquals(emptyList<String>(), relatedCards("", all))
        assertEquals(emptyList<String>(), relatedCards("   ", all))
        assertEquals(emptyList<String>(), relatedCards("banana rain", all))
    }

    @Test
    fun onlyCardsTheItemHasAreOffered() {
        assertEquals(listOf("food"), relatedCards("water food", setOf("food")))
        assertEquals(emptyList<String>(), relatedCards("family", setOf("water")))
    }

    @Test
    fun punctuationAndCaseAreIgnored() {
        assertEquals(listOf("water", "food", "medicine"), relatedCards("Water, FOOD! medicine?", all))
    }

    @Test
    fun theListIsCapped() {
        assertEquals(listOf("water", "food"), relatedCards("water food family home toilet medicine", all, limit = 2))
        assertEquals(listOf("water"), relatedCards("water food", all, limit = 1))
        assertEquals(emptyList<String>(), relatedCards("water", all, limit = 0))
    }
}
