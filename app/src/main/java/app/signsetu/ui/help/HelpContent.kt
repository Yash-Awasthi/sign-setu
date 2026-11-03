package app.signsetu.ui.help

import androidx.annotation.StringRes
import app.signsetu.R

internal data class HelpEntry(@StringRes val heading: Int, @StringRes val body: Int)

internal data class HelpTopic(
    val id: String,
    @StringRes val title: Int,
    @StringRes val summary: Int,
    val entries: List<HelpEntry>,
    /** A tour is shown one entry at a time with Next and Back, not as a list. */
    val guided: Boolean = false,
)

internal object HelpContent {
    private fun e(h: Int, b: Int) = HelpEntry(h, b)

    val topics: List<HelpTopic> = listOf(
        HelpTopic(
            "tour", R.string.help_topic_tour, R.string.help_topic_tour_sum, guided = true, entries = listOf(
                e(R.string.help_tour_1_h, R.string.help_tour_1_b),
                e(R.string.help_tour_2_h, R.string.help_tour_2_b),
                e(R.string.help_tour_3_h, R.string.help_tour_3_b),
                e(R.string.help_tour_4_h, R.string.help_tour_4_b),
                e(R.string.help_tour_5_h, R.string.help_tour_5_b),
                e(R.string.help_tour_6_h, R.string.help_tour_6_b),
                e(R.string.help_tour_7_h, R.string.help_tour_7_b),
                e(R.string.help_tour_8_h, R.string.help_tour_8_b),
            ),
        ),
        HelpTopic(
            "call", R.string.help_topic_call, R.string.help_topic_call_sum, listOf(
                e(R.string.help_call_1_h, R.string.help_call_1_b),
                e(R.string.help_call_2_h, R.string.help_call_2_b),
                e(R.string.help_call_3_h, R.string.help_call_3_b),
                e(R.string.help_call_4_h, R.string.help_call_4_b),
                e(R.string.help_call_5_h, R.string.help_call_5_b),
                e(R.string.help_call_6_h, R.string.help_call_6_b),
                e(R.string.help_call_7_h, R.string.help_call_7_b),
                e(R.string.help_call_8_h, R.string.help_call_8_b),
                e(R.string.help_call_9_h, R.string.help_call_9_b),
                e(R.string.help_call_10_h, R.string.help_call_10_b),
                e(R.string.help_call_11_h, R.string.help_call_11_b),
                e(R.string.help_call_12_h, R.string.help_call_12_b),
                e(R.string.help_call_13_h, R.string.help_call_13_b),
                e(R.string.help_call_14_h, R.string.help_call_14_b),
                e(R.string.help_call_15_h, R.string.help_call_15_b),
                e(R.string.help_call_16_h, R.string.help_call_16_b),
            ),
        ),
        HelpTopic(
            "chat", R.string.help_topic_chat, R.string.help_topic_chat_sum, listOf(
                e(R.string.help_chat_1_h, R.string.help_chat_1_b),
                e(R.string.help_chat_2_h, R.string.help_chat_2_b),
                e(R.string.help_chat_3_h, R.string.help_chat_3_b),
                e(R.string.help_chat_4_h, R.string.help_chat_4_b),
                e(R.string.help_chat_5_h, R.string.help_chat_5_b),
                e(R.string.help_chat_6_h, R.string.help_chat_6_b),
                e(R.string.help_chat_7_h, R.string.help_chat_7_b),
                e(R.string.help_chat_8_h, R.string.help_chat_8_b),
            ),
        ),
        HelpTopic(
            "settings", R.string.help_topic_settings, R.string.help_topic_settings_sum, listOf(
                e(R.string.help_set_1_h, R.string.help_set_1_b),
                e(R.string.help_set_2_h, R.string.help_set_2_b),
                e(R.string.help_set_3_h, R.string.help_set_3_b),
                e(R.string.help_set_4_h, R.string.help_set_4_b),
                e(R.string.help_set_5_h, R.string.help_set_5_b),
                e(R.string.help_set_6_h, R.string.help_set_6_b),
                e(R.string.help_set_7_h, R.string.help_set_7_b),
                e(R.string.help_set_8_h, R.string.help_set_8_b),
                e(R.string.help_set_9_h, R.string.help_set_9_b),
                e(R.string.help_set_10_h, R.string.help_set_10_b),
                e(R.string.help_set_11_h, R.string.help_set_11_b),
                e(R.string.help_set_12_h, R.string.help_set_12_b),
                e(R.string.help_set_13_h, R.string.help_set_13_b),
                e(R.string.help_set_14_h, R.string.help_set_14_b),
                e(R.string.help_set_15_h, R.string.help_set_15_b),
                e(R.string.help_set_16_h, R.string.help_set_16_b),
                e(R.string.help_set_17_h, R.string.help_set_17_b),
                e(R.string.help_set_18_h, R.string.help_set_18_b),
                e(R.string.help_set_19_h, R.string.help_set_19_b),
                e(R.string.help_set_20_h, R.string.help_set_20_b),
                e(R.string.help_set_21_h, R.string.help_set_21_b),
                e(R.string.help_set_22_h, R.string.help_set_22_b),
                e(R.string.help_set_23_h, R.string.help_set_23_b),
                e(R.string.help_set_24_h, R.string.help_set_24_b),
                e(R.string.help_set_25_h, R.string.help_set_25_b),
                e(R.string.help_set_26_h, R.string.help_set_26_b),
            ),
        ),
        HelpTopic(
            "records", R.string.help_topic_records, R.string.help_topic_records_sum, listOf(
                e(R.string.help_rec_1_h, R.string.help_rec_1_b),
                e(R.string.help_rec_2_h, R.string.help_rec_2_b),
                e(R.string.help_rec_3_h, R.string.help_rec_3_b),
                e(R.string.help_rec_4_h, R.string.help_rec_4_b),
            ),
        ),
        HelpTopic(
            "practice", R.string.help_topic_practice, R.string.help_topic_practice_sum, listOf(
                e(R.string.help_prac_1_h, R.string.help_prac_1_b),
                e(R.string.help_prac_2_h, R.string.help_prac_2_b),
                e(R.string.help_prac_3_h, R.string.help_prac_3_b),
            ),
        ),
        HelpTopic(
            "brain", R.string.help_topic_brain, R.string.help_topic_brain_sum, listOf(
                e(R.string.help_brain_1_h, R.string.help_brain_1_b),
                e(R.string.help_brain_2_h, R.string.help_brain_2_b),
                e(R.string.help_brain_3_h, R.string.help_brain_3_b),
                e(R.string.help_brain_4_h, R.string.help_brain_4_b),
                e(R.string.help_brain_5_h, R.string.help_brain_5_b),
            ),
        ),
        HelpTopic(
            "privacy", R.string.help_topic_privacy, R.string.help_topic_privacy_sum, listOf(
                e(R.string.help_priv_1_h, R.string.help_priv_1_b),
                e(R.string.help_priv_2_h, R.string.help_priv_2_b),
                e(R.string.help_priv_3_h, R.string.help_priv_3_b),
                e(R.string.help_priv_4_h, R.string.help_priv_4_b),
                e(R.string.help_priv_5_h, R.string.help_priv_5_b),
            ),
        ),
    )

    fun topic(id: String?): HelpTopic? = topics.firstOrNull { it.id == id }
}
