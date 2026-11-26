package app.signsetu.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Dimension tokens: every dp the app draws comes from [Space]; every font size from
 * [TypeSize]; every corner radius from [Radius]. A composable reads these instead of
 * writing a number, and tools/ux/check_tokens.py fails on a raw dp, sp, radius or hex
 * colour anywhere under ui/ except this directory.
 */
object Space {
    val none = 0.dp
    val hair = 1.dp
    val s1_5 = 1.5.dp
    val xxs = 2.dp
    val s2_5 = 2.5.dp
    val s3 = 3.dp
    val xs = 4.dp
    val s5 = 5.dp
    val s6 = 6.dp
    val sm = 8.dp
    val s9 = 9.dp
    val s10 = 10.dp
    val md = 12.dp
    val s13 = 13.dp
    val s14 = 14.dp
    val lg = 16.dp
    val s18 = 18.dp
    val s20 = 20.dp
    val s22 = 22.dp
    val xl = 24.dp
    val s26 = 26.dp
    val s27 = 27.dp
    val s28 = 28.dp
    val s30 = 30.dp
    val xxl = 32.dp
    val s34 = 34.dp
    val s36 = 36.dp
    val s40 = 40.dp
    val s44 = 44.dp
    val s46 = 46.dp
    val s48 = 48.dp
    val s49 = 49.dp
    val s50 = 50.dp
    val s52 = 52.dp
    val s56 = 56.dp
    val s58 = 58.dp
    val s64 = 64.dp
    val s72 = 72.dp
    val s76 = 76.dp
    val s80 = 80.dp
    val s84 = 84.dp
    val s88 = 88.dp
    val s90 = 90.dp
    val s92 = 92.dp
    val s96 = 96.dp
    val s104 = 104.dp
    val s108 = 108.dp
    val s112 = 112.dp
    val s120 = 120.dp
    val s128 = 128.dp
    val s130 = 130.dp
    val s140 = 140.dp
    val s148 = 148.dp
    val s156 = 156.dp
    val s160 = 160.dp
    val s180 = 180.dp
    val s200 = 200.dp
    val s220 = 220.dp
    val s230 = 230.dp
    val s240 = 240.dp
    val s243 = 243.dp
    val s260 = 260.dp
    val s272 = 272.dp
    val s277 = 277.dp
    val s300 = 300.dp
    val s360 = 360.dp
    val s420 = 420.dp
    val s520 = 520.dp
    val s720 = 720.dp
}

/** Font sizes in sp, named by their value so a screen keeps its exact type scale. */
object TypeSize {
    val s1 = 1.sp
    val s8 = 8.sp
    val s9 = 9.sp
    val s10 = 10.sp
    val s11 = 11.sp
    val s12 = 12.sp
    val s13 = 13.sp
    val s14 = 14.sp
    val s15 = 15.sp
    val s16 = 16.sp
    val s17 = 17.sp
    val s18 = 18.sp
    val s19 = 19.sp
    val s20 = 20.sp
    val s22 = 22.sp
    val s26 = 26.sp
    val s28 = 28.sp
    val s34 = 34.sp
    val s40 = 40.sp
    val s44 = 44.sp
    val s48 = 48.sp
}

/** Corner radii in dp; [pill] is the fully rounded 50 percent shape. */
object Radius {
    val r4 = 4.dp
    val r6 = 6.dp
    val r8 = 8.dp
    val r10 = 10.dp
    val r12 = 12.dp
    val r14 = 14.dp
    val r16 = 16.dp
    val r18 = 18.dp
    val r20 = 20.dp
    val r22 = 22.dp
    val r24 = 24.dp
    val r28 = 28.dp
    val r32 = 32.dp
    val pill: Shape = RoundedCornerShape(50)
}
