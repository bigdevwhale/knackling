package app.knackling.ui.art

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.knackling.BuildConfig
import app.knackling.data.Hobby
import app.knackling.domain.Book
import app.knackling.domain.Decors
import app.knackling.domain.Mood
import app.knackling.domain.Rand
import app.knackling.ui.components.tap
import java.time.LocalTime
import kotlin.math.roundToInt
import kotlin.math.sin

/** Where the apprentices sit in the room: x of the feet, y of the feet, size – all as fractions of the room's width/height. */
data class Spot(val x: Float, val y: Float, val size: Float, val flip: Boolean = false)

object Room {
    /** The rug (the first, the biggest), the left platform, by the armchair, the shelf end, the window sill. */
    val spots = listOf(Spot(.50f, .875f, .36f), Spot(.15f, .665f, .25f), Spot(.80f, .80f, .24f, flip = true), Spot(.21f, .432f, .15f), Spot(.52f, .357f, .16f, flip = true))
    /** The window's middle, for the sky and the night. */
    const val WX = .5f; const val WY = .222f; const val WR = .178f

    /** The sky by the hour: dawn, day, evening, night. */
    fun sky(hour: Int): List<Color> = when (hour) {
        in 5..7 -> listOf(Color(0xFFF6B98A), Color(0xFFF9DDB1))
        in 8..16 -> listOf(Color(0xFF7DB6E8), Color(0xFFCDE6F4))
        in 17..20 -> listOf(Color(0xFFE58C6A), Color(0xFF6E5A9A))
        else -> listOf(Color(0xFF151A36), Color(0xFF2C3558))
    }
    fun night(hour: Int) = hour >= 22 || hour < 6
}

/**
 * The attic workshop: the sky in the round window, the room, the decorations bought, and the apprentices of the
 * hobbies on stage, each in its spot, breathing, working when its session runs, asleep when it misses its hobby.
 */
@Composable
fun Workshop(b: Book, modifier: Modifier = Modifier, hour: Int = LocalTime.now().hour, onPet: (Hobby) -> Unit = {}) {
    val ctx = LocalContext.current
    val room = Art.get(ctx, "room_bg")
    val still = BuildConfig.SHOTS
    val t by rememberInfiniteTransition(label = "sky").animateFloat(0f, 1f, infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Restart), label = "t")
    val night = Room.night(hour)
    BoxWithConstraints(modifier) {
        val ratio = room?.let { it.height.toFloat() / it.width } ?: 1.5f
        // the room fits the box, centred, keeping its shape
        val w = minOf(maxWidth.value, maxHeight.value / ratio); val h = w * ratio
        val x0 = (maxWidth.value - w) / 2; val y0 = (maxHeight.value - h) / 2
        Box(Modifier.offset(x0.dp, y0.dp).size(w.dp, h.dp)) {
            Canvas(Modifier.fillMaxSize().clipToBounds()) {
                // the sky only shows through the round window: everything of it is clipped to that circle
                val win = androidx.compose.ui.graphics.Path().apply {
                    addOval(androidx.compose.ui.geometry.Rect(Offset(size.width * Room.WX, size.height * Room.WY), size.width * Room.WR))
                }
                clipPath(win) {
                    val sky = Room.sky(hour)
                    drawRect(Brush.verticalGradient(sky, size.height * (Room.WY - .12f), size.height * (Room.WY + .12f)))
                    if (night) {
                        val r = Rand(11)
                        repeat(24) {
                            val sx = size.width * (Room.WX - Room.WR) + r.next() * size.width * Room.WR * 2
                            val sy = size.height * Room.WY - size.width * Room.WR + r.next() * size.width * Room.WR * 2; val ph = r.next()
                            val a = if (still) .8f else (.4f + .6f * ((sin((t + ph) * 6.283f) + 1) / 2))
                            drawCircle(Color.White.copy(alpha = a), 1.4f * density, Offset(sx, sy))
                        }
                        drawCircle(Color(0xFFF4EBC8), size.width * .04f, Offset(size.width * (Room.WX + .07f), size.height * (Room.WY - .03f)))
                    } else {
                        // a cloud drifting past the window
                        val span = size.width * Room.WR * 2.8f
                        val cx = size.width * Room.WX - span / 2 + (if (still) .45f else t) * span
                        val cy = size.height * (Room.WY - .02f)
                        drawCircle(Color.White.copy(alpha = .85f), size.width * .05f, Offset(cx, cy))
                        drawCircle(Color.White.copy(alpha = .85f), size.width * .04f, Offset(cx + size.width * .05f, cy + size.height * .01f))
                        drawCircle(Color.White.copy(alpha = .85f), size.width * .035f, Offset(cx - size.width * .045f, cy + size.height * .015f))
                    }
                }
                if (room != null) drawImage(room, IntOffset.Zero, IntSize(room.width, room.height), IntOffset.Zero, IntSize(size.width.roundToInt(), size.height.roundToInt()), filterQuality = FilterQuality.Low)
                if (night) drawRect(Color(0xFF0A1030).copy(alpha = .28f))
            }
            // decorations: the ones against the wall first, then the floor
            val items = Decors.all.filter { it.id in b.s.items }.sortedBy { if (it.back) 0 else 1 }
            items.forEach { d ->
                val img = Art.get(ctx, "decor_${d.id}")
                val dw = w * d.w; val dh = img?.let { dw * it.height / it.width } ?: dw
                Pic("decor_${d.id}", dw.dp, Modifier.offset((w * d.x - dw / 2).dp, (h * d.y - dh).dp).size(dw.dp, dh.dp))
            }
            // the apprentices, back to front
            b.onStage.take(Room.spots.size).mapIndexed { i, hb -> i to hb }.sortedBy { Room.spots[it.first].y }.forEach { (i, hb) ->
                val sp = Room.spots[i]
                val st = b.stage(hb)
                val size = w * sp.size * stageScale(st)
                val mood = if (night && b.mood(hb) != Mood.WORKING) Mood.ASLEEP else b.mood(hb)
                val mod = Modifier.offset((w * sp.x - size / 2).dp, (h * sp.y - size).dp).tap { onPet(hb) }
                if (st < 0) Pic(eggOf(hb.species), size.dp, mod)
                else Box(mod.size(size.dp)) {
                    if (st >= app.knackling.domain.Grow.GLOW) Glow(size.dp)
                    Creature(hb.species, frameOf(st, mood), size.dp, shiny = hb.shiny, phase = i * .23f, flip = sp.flip)
                }
            }
        }
    }
}

/** How big an apprentice looks on a page: a baby small, a grand master full. */
fun stageScale(stage: Int): Float = when (stage) { -1 -> .55f; 0 -> .62f; 1 -> .78f; 2 -> .9f; else -> 1f }

/** A spot size helper for lists. */
fun Dp.scaled(f: Float) = (value * f).dp

/** The golden glow and sparkles behind an apprentice of the high ranks (five hundred hours and on). */
@Composable
fun Glow(size: Dp, modifier: Modifier = Modifier) {
    val t by rememberInfiniteTransition(label = "glow").animateFloat(0f, 1f, infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Restart), label = "g")
    val time = if (BuildConfig.SHOTS) .3f else t
    Canvas(modifier.size(size)) {
        val c = Offset(this.size.width / 2, this.size.height * .6f)
        drawCircle(Brush.radialGradient(listOf(Color(0x66FFD45E), Color(0x00FFD45E)), c, this.size.width * .5f), this.size.width * .5f, c)
        repeat(6) { k ->
            val a = (k / 6f + time) * 6.283f
            val r = this.size.width * (.32f + .06f * sin(a * 2))
            val p = Offset(c.x + r * kotlin.math.cos(a), c.y + r * sin(a) * .7f)
            drawCircle(Color(0xFFFFE9A0).copy(alpha = .5f + .5f * sin(a * 3).coerceAtLeast(0f)), 2.5f * density, p)
        }
    }
}
