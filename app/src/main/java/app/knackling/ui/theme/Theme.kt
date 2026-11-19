package app.knackling.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.knackling.R

/**
 * Colour tokens of the attic workshop: cream paper, red thread for action, gold for sparks, walnut wood.
 * [frame] is the ink line around paper things; [wood]/[wood2] the shelf and the workbench.
 */
class Pal(
    val dark: Boolean, val bg: Color, val card: Color, val card2: Color, val ink: Color, val mute: Color, val line: Color, val track: Color,
    val acc: Color, val accInk: Color, val accSoft: Color, val ok: Color, val okSoft: Color, val gold: Color, val goldSoft: Color, val bad: Color,
    val heat: List<Color>, val shadow: Color, val frame: Color, val wood: Color, val wood2: Color, val sky: Color, val night: Color, val denim: Color,
)

private fun c(hex: Long) = Color(0xFF000000 or hex)

val Light = Pal(false, c(0xF1E7D3), c(0xFCF7EC), c(0xF3EAD7), c(0x2F2419), c(0x88775F), c(0xE0D2B6), c(0xE7DCC4),
    c(0xB4473A), c(0xFFFFFF), c(0xF4DCD3), c(0x4F8A5B), c(0xDDEDDC), c(0xC98A2E), c(0xF6E7C8), c(0xC4533A),
    listOf(c(0xE7DCC4), c(0xF0CDB5), c(0xE29C7E), c(0xC9644B), c(0x9C3A2C)), Color(0x22402A1A), c(0xB59E78), c(0x8A5A34), c(0x6B4426),
    c(0x9CC8E6), c(0x2C3558), c(0x3E5A80))

val Dark = Pal(true, c(0x1A1511), c(0x261F19), c(0x30271F), c(0xF0E6D4), c(0xA69780), c(0x3D3228), c(0x372D24),
    c(0xE07A62), c(0x1E0F0B), c(0x47261E), c(0x76B583), c(0x22352A), c(0xE8B252), c(0x3E3119), c(0xEE7558),
    listOf(c(0x332A22), c(0x5A3427), c(0x8A4635), c(0xC0624A), c(0xF08E70)), Color(0x55000000), c(0x55463A), c(0x5A3B22), c(0x3E2816),
    c(0x3D5A7A), c(0x141A30), c(0x2E4466))

/** The palette in use; switched by the theme setting before the tree is drawn. */
object C {
    @Volatile var p: Pal = Light
    val dark get() = p.dark
    val bg get() = p.bg; val card get() = p.card; val card2 get() = p.card2; val ink get() = p.ink; val mute get() = p.mute
    val line get() = p.line; val track get() = p.track; val acc get() = p.acc; val accInk get() = p.accInk; val accSoft get() = p.accSoft
    val ok get() = p.ok; val okSoft get() = p.okSoft; val gold get() = p.gold; val goldSoft get() = p.goldSoft; val bad get() = p.bad
    val heat get() = p.heat; val shadow get() = p.shadow; val frame get() = p.frame; val wood get() = p.wood; val wood2 get() = p.wood2
    val sky get() = p.sky; val night get() = p.night; val denim get() = p.denim
}

val Nunito = FontFamily(Font(R.font.nunito_regular, FontWeight.Normal), Font(R.font.nunito_semibold, FontWeight.SemiBold), Font(R.font.nunito_bold, FontWeight.Bold), Font(R.font.nunito_bold, FontWeight.ExtraBold))
/** Handwriting for tags, notes and labels pinned in the workshop. */
val Hand = FontFamily(Font(R.font.caveat_medium, FontWeight.Normal), Font(R.font.caveat_bold, FontWeight.Bold))

object T {
    val title get() = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, lineHeight = 30.sp, color = C.ink)
    val h2 get() = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, lineHeight = 24.sp, color = C.ink)
    val h3 get() = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 21.sp, color = C.ink)
    val body get() = TextStyle(fontFamily = Nunito, fontSize = 15.sp, lineHeight = 21.sp, color = C.ink)
    val bodyB get() = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 21.sp, color = C.ink)
    val small get() = TextStyle(fontFamily = Nunito, fontSize = 13.sp, lineHeight = 17.sp, color = C.mute)
    val smallB get() = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 13.sp, lineHeight = 17.sp, color = C.ink)
    val tiny get() = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 14.sp, color = C.mute)
    val sec get() = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 1.2.sp, color = C.mute)
    val num get() = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, lineHeight = 26.sp, color = C.ink)
    val numS get() = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, lineHeight = 19.sp, color = C.ink)
    val big get() = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 44.sp, lineHeight = 50.sp, color = C.ink)
    val btn get() = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
    val hand get() = TextStyle(fontFamily = Hand, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 24.sp, color = C.ink)
    val handS get() = TextStyle(fontFamily = Hand, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 20.sp, color = C.ink)
}

@Composable
fun KnackTheme(dark: Boolean, content: @Composable () -> Unit) {
    C.p = if (dark) Dark else Light
    val p = C.p
    val scheme = if (dark) darkColorScheme(primary = p.acc, background = p.bg, surface = p.card, onSurface = p.ink, onBackground = p.ink)
    else lightColorScheme(primary = p.acc, background = p.bg, surface = p.card, onSurface = p.ink, onBackground = p.ink)
    MaterialTheme(colorScheme = scheme, content = content)
}
