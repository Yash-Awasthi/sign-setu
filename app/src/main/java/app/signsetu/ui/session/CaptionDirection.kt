package app.signsetu.ui.session

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.style.TextDirection

/** Urdu is the only right-to-left language in the bundle; every other language a subject can pick reads left to right. */
internal fun captionDirection(lang: String?): TextDirection =
    if (lang?.substringBefore('-')?.lowercase() == "ur") TextDirection.Rtl else TextDirection.Ltr

/** The direction the subject's answer bar draws its words in, set once above the bar. */
internal val LocalSubjectTextDirection = staticCompositionLocalOf { TextDirection.Ltr }
