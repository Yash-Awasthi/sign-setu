package app.signsetu.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Colour tokens for the colours drawn outside the Material scheme: the express board's
 * card pairs, the avatar's paint, the stage's ink and the call chrome. A composable reads
 * these; tools/ux/check_tokens.py fails on a hex colour written anywhere else under ui/.
 * Colours that are already in a scheme or an [AnswerColors] value are not repeated here.
 */

/** A card's fill and the ink drawn on it, checked as a pair. */
data class Tone(val bg: Color, val ink: Color)

/** The express board: one tone per card, so a card reads the same wherever it appears. */
object ExpressTones {
    val pain = Tone(Color(0xFFFFD4D4), Color(0xFF8B1D1D))
    val scared = Tone(Color(0xFFFFF1C2), Color(0xFF8C5800))
    val sick = Tone(Color(0xFFFFE0D0), Color(0xFF8C2E00))
    val stop = Tone(Color(0xFFFFCDD2), Color(0xFFB71C1C))
    val police = Tone(Color(0xFFD6E4FF), Color(0xFF1D3B8B))
    val okay = Tone(Color(0xFFD1F2D9), Color(0xFF1B6B32))
    val sad = Tone(Color(0xFFEDE9FE), Color(0xFF5B21B6))
    val angry = Tone(Color(0xFFFFEDD5), Color(0xFF9A3412))
    val water = Tone(Color(0xFFD8F3FF), Color(0xFF026AA7))
    val toilet = Tone(Color(0xFFE8DEFF), Color(0xFF5521B5))
    val food = Tone(Color(0xFFFFECCC), Color(0xFF8C5500))
    val rest = Tone(Color(0xFFEFE3D3), Color(0xFF4F4539))
    val phone = Tone(Color(0xFFC7F9CC), Color(0xFF145A32))
    val medicine = Tone(Color(0xFFCCFBF1), Color(0xFF115E59))
    val mother = Tone(Color(0xFFFFD6E0), Color(0xFF881337))
    val father = Tone(Color(0xFFDBEAFE), Color(0xFF1E40AF))
    val brother = Tone(Color(0xFFD9E6DD), Color(0xFF2B4A3A))
    val sister = Tone(Color(0xFFFCE7F3), Color(0xFF9D174D))
    val friends = Tone(Color(0xFFDCFCE7), Color(0xFF166534))
    val someoneElse = Tone(Color(0xFFFEE2E2), Color(0xFF991B1B))
    val home = Tone(Color(0xFFFEF3C7), Color(0xFF92400E))
    val school = Tone(Color(0xFFE0F2FE), Color(0xFF0369A1))
    val hospital = Tone(Color(0xFFFFE4E6), Color(0xFF9F1239))
    val busStand = Tone(Color(0xFFF2EADF), Color(0xFF4F4539))
    val road = Tone(Color(0xFFF3F4F6), Color(0xFF374151))
    val yes = Tone(Color(0xFFC2F0C2), Color(0xFF0F5132))
    val no = Tone(Color(0xFFFFC5C5), Color(0xFF842029))
    val dontKnow = Tone(Color(0xFFEFE3D3), Color(0xFF4F4539))
    val dontUnderstand = Tone(Color(0xFFFFF3CD), Color(0xFF664D03))
    val thankYou = Tone(Color(0xFFE0F2FE), Color(0xFF0369A1))
    val doctor = Tone(Color(0xFFCCFBF1), Color(0xFF115E59))
    val cold = Tone(Color(0xFFD8F3FF), Color(0xFF026AA7))
    val hot = Tone(Color(0xFFFFEDD5), Color(0xFF9A3412))
    val breakTime = Tone(Color(0xFFEFE3D3), Color(0xFF4F4539))
    val help = Tone(Color(0xFFFFCDD2), Color(0xFFB71C1C))
    val alone = Tone(Color(0xFFEDE9FE), Color(0xFF5B21B6))
    val knife = Tone(Color(0xFFF3F4F6), Color(0xFF374151))
    val head = Tone(Color(0xFFFFE4E6), Color(0xFF9F1239))
    val throat = Tone(Color(0xFFFEF3C7), Color(0xFF92400E))
    val stomach = Tone(Color(0xFFFFECCC), Color(0xFF8C5500))
    val arm = Tone(Color(0xFFD9E6DD), Color(0xFF2B4A3A))
    val leg = Tone(Color(0xFFDCFCE7), Color(0xFF166534))
    val back = Tone(Color(0xFFF3E8FF), Color(0xFF6B21A8))
}

/** The drawn avatar's paint: two people, plus the shared marks on the face and hands. */
object AvatarTones {
    val childSkin = Color(0xFFC98D5E)
    val childSkinShade = Color(0xFFAE7345)
    val childLine = Color(0xFF7C4A27)
    val childOutline = Color(0xFF4A2A14)
    val childHair = Color(0xFF2A1F1B)
    val childHairShine = Color(0xFF4A3A33)
    val childTop = Color(0xFF2E9C93)
    val childTopShade = Color(0xFF227E77)
    val childTrim = Color(0xFFF6BE4B)
    val childTrousers = Color(0xFF3D4A6B)
    val childIris = Color(0xFF3A2416)
    val childLip = Color(0xFF8C3A32)
    val childBlush = Color(0x44F07C64)

    val adultSkin = Color(0xFFBC7E52)
    val adultSkinShade = Color(0xFF9F6842)
    val adultLine = Color(0xFF6E4123)
    val adultOutline = Color(0xFF3E2412)
    val adultHair = Color(0xFF1E1816)
    val adultHairShine = Color(0xFF3A302C)
    val adultTop = Color(0xFF3B4A6E)
    val adultTopShade = Color(0xFF2A3654)
    val adultTrim = Color(0xFF8394C2)
    val adultTrousers = Color(0xFF2B3448)
    val adultIris = Color(0xFF2E1D12)
    val adultLip = Color(0xFF7E3A33)
    val adultBlush = Color(0x22F07C64)

    val brow = Color(0xFF2B2F3A)
    val eyeWhite = Color(0xFFF6EFE6)
    val irisRim = Color(0xFF8A5A36)
    val mouth = Color(0xFFE58A86)
    val hairLine = Color(0xFF6B4423)
    val hairHighlight = Color(0xFF8A5A33)
    val irisRing = Color(0xFF9A6A45)
    val palm = Color(0xFFF3CFAE)
    val nail = Color(0xFFFFE9DA)
}

/** The stage's own ink and washes, and the call chrome drawn over the dark call scheme. */
object StageTones {
    val board = Color(0xFFFFFBF2)
    val bodySkin = Color(0xFFFFE8D2)
    val bodyMark = Color(0xFFD32F2F)
    val faceWash = Color(0xFFFFE0B2)
    val chosenFill = Color(0xFFFFE0B2)
    val familyFill = Color(0xFFD6E4FF)
    val lessonOff = Color(0xFFBFD8CB)
    val digitLit = Color(0xFFFFB300)
    val captionMuted = Color(0xFF6B5E50)
    val dimWord = Color(0xFFB8A99A)
    val starOn = Color(0xFFB86E00)
    val starOff = Color(0xFF8A7760)
    val tileEdge = Color(0x22000000)
    val crayonRed = Color(0xFFD32F2F)
    val crayonBlue = Color(0xFF1E5BD8)
    val crayonGreen = Color(0xFF2E7D32)
    val crayonBrown = Color(0xFF8D5524)

    val lowBattery = Color(0xFFFFC857)
    val onLowBattery = Color(0xFF3A2A00)

    val pauseTop = Color(0xEB0D1B3E)
    val pauseBottom = Color(0xEB1E3A6E)
    val pauseGlowSoft = Color(0x3384C5FF)
    val pauseGlow = Color(0x6684C5FF)
    val dotOn = Color(0xCCFFFFFF)
    val dotOff = Color(0x33FFFFFF)
    val bubble = Color(0x66FFFFFF)

    val chromeVeil = Color(0x99000000)
    val victimBorder = Color(0xFFFBBF24)
    val victimInk = Color(0xFFFDE68A)
    val signing = Color(0xFF10B981)
    val veil = Color(0xCC000000)
    val veilSoft = Color(0x88FFFFFF)
    val hairline = Color(0x44FFFFFF)

    val answerNo = Color(0xFFB91C1C)
    val answerWater = Color(0xFF0369A1)
    val answerFood = Color(0xFFD97706)
    val answerExpress = Color(0xFF7C3AED)

    /** The keyboard or switch focus ring, paired with white in [AnswerFlow.focusRing]. */
    val focus = Color(0xFF0B57D0)
}
