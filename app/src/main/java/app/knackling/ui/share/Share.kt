package app.knackling.ui.share

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import app.knackling.R
import app.knackling.data.Hobby
import app.knackling.data.Photos
import app.knackling.domain.Book
import app.knackling.domain.Grow
import app.knackling.domain.Kinds
import app.knackling.domain.Level
import app.knackling.domain.count
import app.knackling.domain.dayShort
import app.knackling.domain.fmtDayYear
import app.knackling.domain.monthName
import app.knackling.domain.tr
import app.knackling.domain.weekStart
import app.knackling.ui.art.Room
import app.knackling.ui.art.frameOf
import app.knackling.ui.art.toolOf
import app.knackling.ui.components.Btn
import app.knackling.ui.components.Segments
import app.knackling.ui.components.Sheet
import app.knackling.ui.hobby.hours
import app.knackling.ui.theme.C
import app.knackling.ui.theme.T
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

/** Every card the workshop can make right now, the asked one first. */
fun cardsFor(b: Book, start: String): List<String> = buildList {
    add(start)
    add("workshop"); add("month"); add("year")
    b.active.filter { b.stage(it) >= 0 }.forEach { add("pet:${it.id}") }
    b.projects.filter { it.done > 0 }.take(5).forEach { add("project:${it.id}") }
    b.sessions.filter { it.photo.isNotEmpty() }.take(5).forEach { add("work:${it.id}") }
}.distinct().filter { valid(b, it) }

private fun valid(b: Book, k: String): Boolean = when {
    k.startsWith("pet:") -> b.byId[k.drop(4).toLongOrNull()] != null
    k.startsWith("project:") -> b.projects.any { it.id == k.drop(8).toLongOrNull() }
    k.startsWith("work:") -> b.sessions.any { it.id == k.drop(5).toLongOrNull() }
    else -> k in setOf("workshop", "month", "year")
}

/**
 * Paints the postcards: cream linen with a stitched border in the hobby's thread colour, the apprentices from
 * their sheets, handwriting for the words that are the user's own. 1080×1350, or the same card on a 1080×1920 story.
 */
class Painter(private val ctx: Context, private val b: Book) {
    private val W = 1080; private val H = 1350
    private val bold: Typeface = ResourcesCompat.getFont(ctx, R.font.nunito_bold) ?: Typeface.DEFAULT_BOLD
    private val body: Typeface = ResourcesCompat.getFont(ctx, R.font.nunito_regular) ?: Typeface.DEFAULT
    private val hand: Typeface = ResourcesCompat.getFont(ctx, R.font.caveat_bold) ?: Typeface.DEFAULT
    private val ink = 0xFF2F2419.toInt(); private val mute = 0xFF88775F.toInt(); private val red = 0xFFB4473A.toInt(); private val gold = 0xFFC98A2E.toInt()
    private val paper = 0xFFFBF5E8.toInt(); private val linen = 0xFFEFE4CC.toInt()

    @SuppressLint("DiscouragedApi")
    private fun bmp(name: String): Bitmap? {
        val id = ctx.resources.getIdentifier(name, "drawable", ctx.packageName)
        return if (id == 0) null else BitmapFactory.decodeResource(ctx.resources, id, BitmapFactory.Options().apply { inScaled = false })
    }
    private fun col(h: Hobby?) = (0xFF000000 or Kinds.color(h?.species ?: "")).toInt()

    /** A picture by its bottom centre and height (or by its top when [top]). */
    private fun Canvas.pic(name: String, cx: Float, y: Float, h: Float, top: Boolean = false, bmp: Bitmap? = null) {
        val bm = bmp ?: bmp(name) ?: return
        val w = h * bm.width / bm.height
        val y0 = if (top) y else y - h
        drawBitmap(bm, null, RectF(cx - w / 2, y0, cx + w / 2, y0 + h), Paint().apply { isFilterBitmap = true })
    }
    private fun Canvas.text(s: String, x: Float, y: Float, size: Float, color: Int, face: Typeface, align: Paint.Align = Paint.Align.LEFT) =
        drawText(s, x, y, Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; this.color = color; typeface = face; textAlign = align })
    private fun Canvas.block(s: String, x: Float, y: Float, width: Int, size: Float, color: Int, face: Typeface, center: Boolean = false, max: Int = 4): Float {
        val p = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; this.color = color; typeface = face }
        val l = StaticLayout.Builder.obtain(s, 0, s.length, p, width).setAlignment(if (center) Layout.Alignment.ALIGN_CENTER else Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.05f).setMaxLines(max).setEllipsize(android.text.TextUtils.TruncateAt.END).build()
        save(); translate(x, y); l.draw(this); restore()
        return y + l.height
    }
    private fun Canvas.photo(name: String, r: RectF) {
        val bm = Photos.load(ctx, name, 1400) ?: return
        val s = maxOf(r.width() / bm.width, r.height() / bm.height)
        val sw = r.width() / s; val sh = r.height() / s
        val src = android.graphics.Rect(((bm.width - sw) / 2).toInt(), ((bm.height - sh) / 2).toInt(), ((bm.width + sw) / 2).toInt(), ((bm.height + sh) / 2).toInt())
        drawBitmap(bm, src, r, Paint().apply { isFilterBitmap = true })
    }

    /** Linen, a paper sheet, a running stitch around it in [thread], the signature. */
    private fun frame(c: Canvas, thread: Int) {
        c.drawColor(linen)
        val r = java.util.Random(3); val p = Paint().apply { color = 0x10000000 }
        repeat(500) { val x = r.nextFloat() * W; val y = r.nextFloat() * H; c.drawRect(x, y, x + 3, y + 1, p) }
        c.drawRoundRect(RectF(36f, 36f, W - 36f, H - 36f), 28f, 28f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = paper })
        c.drawRoundRect(RectF(58f, 58f, W - 58f, H - 58f), 20f, 20f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 7f; color = thread; pathEffect = DashPathEffect(floatArrayOf(26f, 16f), 0f); strokeCap = Paint.Cap.ROUND })
        c.pic("ic_paw", 120f, H - 78f, 50f)
        c.text("KNACKLING", 156f, H - 90f, 30f, mute, bold)
        c.text(tr("подмастерья для твоих хобби", "apprentices for your hobbies"), W - 100f, H - 90f, 30f, mute, body, Paint.Align.RIGHT)
    }

    fun render(key: String, story: Boolean = false): Bitmap {
        val card = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(card)
        when {
            key.startsWith("pet:") -> pet(c, b.byId[key.drop(4).toLong()]!!)
            key.startsWith("project:") -> project(c, key.drop(8).toLong())
            key.startsWith("work:") -> work(c, key.drop(5).toLong())
            key == "year" -> year(c)
            key == "workshop" -> workshop(c)
            else -> month(c)
        }
        if (!story) return card
        // the story: the card on a tall linen board with a stitched title above
        val out = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888)
        val s = Canvas(out)
        s.drawColor(0xFF3E5A80.toInt())
        val r = java.util.Random(9); val p = Paint().apply { color = 0x14FFFFFF }
        repeat(1400) { val x = r.nextFloat() * 1080; val y = r.nextFloat() * 1920; s.drawRect(x, y, x + 4, y + 1, p) }
        s.text("KNACKLING", 540f, 200f, 64f, 0xFFF3E7C9.toInt(), bold, Paint.Align.CENTER)
        s.text(tr("подмастерья для твоих хобби", "apprentices for your hobbies"), 540f, 262f, 40f, 0xFFE9B949.toInt(), hand, Paint.Align.CENTER)
        s.save(); s.rotate(-1.5f, 540f, 960f)
        s.drawBitmap(card, null, RectF(60f, 960f - 640f * 1350 / 1080 + 80f, 1020f, 960f + 640f * 1350 / 1080 - 120f + 80f), Paint().apply { isFilterBitmap = true })
        s.restore()
        return out
    }

    private fun pet(c: Canvas, h: Hobby) {
        val st = b.stage(h)
        frame(c, col(h))
        c.drawCircle(W / 2f, 470f, 330f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = (col(h) and 0x00FFFFFF) or 0x33000000 })
        c.pic("pet_${h.species}_${frameOf(st, cheer = true)}", W / 2f, 790f, 600f * (if (st <= 0) .7f else if (st == 1) .85f else 1f))
        c.text(h.name, W / 2f, 890f, 92f, ink, hand, Paint.Align.CENTER)
        c.text(Grow.name(st).uppercase(), W / 2f, 950f, 38f, red, bold, Paint.Align.CENTER)
        c.pic(toolOf(h.species), W / 2f - 160f, 1010f, 56f)
        c.text(h.label, W / 2f - 120f, 1000f, 42f, ink, bold)
        val ss = b.of[h.id].orEmpty()
        val facts = listOf(hours(ss.sumOf { it.minutes }) to tr("вместе", "together"), count(ss.size, "занятие", "занятия", "занятий", "session", "sessions") to "",
            count(((b.today - h.since) / 7 + 1).toInt(), "неделя", "недели", "недель", "week", "weeks") to tr("с первого дня", "since day one"))
        facts.forEachIndexed { i, (v, l) ->
            val x = 200f + i * 340f
            c.text(v, x, 1110f, 44f, ink, bold, Paint.Align.CENTER); c.text(l, x, 1158f, 30f, mute, body, Paint.Align.CENTER)
        }
    }

    private fun work(c: Canvas, id: Long) {
        val s = b.sessions.first { it.id == id }; val h = b.byId[s.hobbyId]
        frame(c, col(h))
        // a polaroid, a little tilted, pinned with a pin
        c.save(); c.rotate(-2.5f, W / 2f, 520f)
        c.drawRect(RectF(150f, 120f, W - 150f, 960f), Paint().apply { color = 0xFFFFFFFF.toInt(); setShadowLayer(18f, 0f, 8f, 0x33000000) })
        c.photo(s.photo, RectF(180f, 150f, W - 180f, 840f))
        c.text(fmtDayYear(s.day), W / 2f, 915f, 46f, ink, hand, Paint.Align.CENTER)
        c.restore()
        c.drawCircle(W / 2f, 140f, 18f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = red })
        h?.let { c.pic("pet_${it.species}_${frameOf(b.stage(it).coerceAtLeast(0), cheer = true)}", W - 190f, 1130f, 290f) }
        c.pic(toolOf(h?.species ?: ""), 140f, 1050f, 60f)
        c.text(h?.label ?: "", 186f, 1040f, 46f, ink, bold)
        c.text(hours(s.minutes) + (if (s.flow == 3) tr(" · в потоке", " · in the flow") else ""), 110f, 1100f, 36f, mute, body)
        if (s.note.isNotBlank()) c.block(s.note, 110f, 1120f, 700, 40f, ink, hand, max = 2)
    }

    private fun project(c: Canvas, id: Long) {
        val p = b.projects.first { it.id == id }; val h = b.byId[p.hobbyId]
        val ss = b.sessions.filter { it.projectId == p.id }
        frame(c, col(h))
        c.text(tr("ГОТОВО!", "FINISHED!"), W / 2f, 160f, 64f, red, bold, Paint.Align.CENTER)
        c.block(p.title, 110f, 185f, W - 220, 70f, ink, hand, center = true, max = 2)
        val cover = p.photo.ifEmpty { ss.firstOrNull { it.photo.isNotEmpty() }?.photo ?: "" }
        val r = RectF(190f, 340f, W - 190f, 900f)
        // a gilt frame around the work
        c.drawRect(RectF(r.left - 26, r.top - 26, r.right + 26, r.bottom + 26), Paint().apply { color = gold })
        c.drawRect(RectF(r.left - 12, r.top - 12, r.right + 12, r.bottom + 12), Paint().apply { color = 0xFF8A5A1C.toInt() })
        if (cover.isNotEmpty()) c.photo(cover, r) else { c.drawRect(r, Paint().apply { color = (col(h) and 0x00FFFFFF) or 0x33000000 }); c.pic(toolOf(h?.species ?: ""), W / 2f, r.top + 80f, 400f, top = true) }
        h?.let { c.pic("pet_${it.species}_4", W - 170f, 1180f, 280f) }
        val facts = listOf(hours(ss.sumOf { it.minutes }) to tr("вложено", "put in"), count(ss.size, "занятие", "занятия", "занятий", "session", "sessions") to "",
            count(((if (p.done > 0) p.done else b.today) - p.started + 1).toInt(), "день", "дня", "дней", "day", "days") to tr("от начала", "from the start"))
        facts.forEachIndexed { i, (v, l) -> c.text(v, 120f, 1000f + i * 70f, 44f, ink, bold); c.text(l, 120f + 330f, 1000f + i * 70f, 32f, mute, body) }
    }

    private fun month(c: Canvas) {
        frame(c, red)
        val d = LocalDate.ofEpochDay(b.today); val from = d.withDayOfMonth(1).toEpochDay(); val to = d.withDayOfMonth(d.lengthOfMonth()).toEpochDay()
        val ss = b.sessions.filter { it.day in from..to }
        c.text(monthName(d.monthValue).replaceFirstChar { it.uppercase() } + " " + d.year, W / 2f, 160f, 72f, ink, hand, Paint.Align.CENTER)
        c.text(hours(ss.sumOf { it.minutes }) + " · " + count(ss.size, "занятие", "занятия", "занятий", "session", "sessions"), W / 2f, 225f, 40f, red, bold, Paint.Align.CENTER)
        val bottom = quilt(c, from, to, 140f, 260f, W - 280f)
        skeins(c, ss, bottom + 10f, if (bottom > 760f) 3 else 4)
    }

    private fun year(c: Canvas) {
        frame(c, gold)
        val from = weekStart(b.today) - 52 * 7; val ss = b.sessions.filter { it.day >= from }
        c.text(tr("Мой год в мастерской", "My year in the workshop"), W / 2f, 160f, 72f, ink, hand, Paint.Align.CENTER)
        c.text(hours(ss.sumOf { it.minutes }) + " · " + count(ss.map { it.day }.distinct().size, "день", "дня", "дней", "day", "days") + tr(" за делом", " at it"),
            W / 2f, 225f, 40f, red, bold, Paint.Align.CENTER)
        // the year's quilt, weeks as columns
        val weeks = 53; val cell = (W - 220f) / weeks; val x0 = 110f; val y0 = 280f
        val byDay = ss.groupBy { it.day }
        for (w in 0 until weeks) for (k in 0..6) {
            val day = from + w * 7 + k; if (day > b.today) continue
            val l = byDay[day]; val color = l?.let { app.knackling.ui.stats.dayColor(b, it) }
            c.drawRoundRect(RectF(x0 + w * cell + 1, y0 + k * cell + 1, x0 + (w + 1) * cell - 1, y0 + (k + 1) * cell - 1), 3f, 3f,
                Paint().apply { this.color = color?.let { android.graphics.Color.argb(255, (it.red * 255).toInt(), (it.green * 255).toInt(), (it.blue * 255).toInt()) } ?: 0xFFE7DCC4.toInt() })
        }
        skeins(c, ss, 470f, 4)
        val ranked = b.active.filter { b.stage(it) >= 0 }.sortedByDescending { b.xp[it.id] ?: 0 }.take(5)
        val step = (W - 220f) / ranked.size.coerceAtLeast(1)
        ranked.forEachIndexed { i, h -> c.pic("pet_${h.species}_${frameOf(b.stage(h), cheer = i == 0)}", 110f + step * (i + .5f), 1180f, minOf(step * .95f, 230f)) }
        c.text(Level.title(b.level) + tr(", уровень ", ", level ") + b.level, W / 2f, 1220f, 34f, mute, body, Paint.Align.CENTER)
    }

    /** The month as a calendar quilt. */
    private fun quilt(c: Canvas, from: Long, to: Long, x0: Float, y0: Float, width: Float): Float {
        val start = weekStart(from); val weeks = ((to - start) / 7 + 1).toInt(); val cell = width / 7
        val byDay = b.sessions.filter { it.day in from..to }.groupBy { it.day }
        (0..6).forEach { c.text(dayShort(it), x0 + cell * (it + .5f), y0 + 30f, 28f, mute, body, Paint.Align.CENTER) }
        for (w in 0 until weeks) for (k in 0..6) {
            val day = start + w * 7 + k; if (day < from || day > to) continue
            val r = RectF(x0 + k * cell + 5, y0 + 45f + w * cell * .72f + 5, x0 + (k + 1) * cell - 5, y0 + 45f + (w + 1) * cell * .72f - 5)
            val l = byDay[day]; val color = l?.let { app.knackling.ui.stats.dayColor(b, it) }
            c.drawRoundRect(r, 8f, 8f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color?.let { android.graphics.Color.argb(255, (it.red * 255).toInt(), (it.green * 255).toInt(), (it.blue * 255).toInt()) } ?: 0xFFE7DCC4.toInt() })
            c.drawRoundRect(RectF(r.left + 5, r.top + 5, r.right - 5, r.bottom - 5), 6f, 6f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = 2.5f; this.color = 0x66FFFFFF; pathEffect = DashPathEffect(floatArrayOf(8f, 6f), 0f) })
            c.text("${LocalDate.ofEpochDay(day).dayOfMonth}", r.left + 12f, r.top + 30f, 24f, if (color != null) 0xCCFFFFFF.toInt() else mute, bold)
        }
        return y0 + 45f + weeks * cell * .72f
    }

    /** Top hobbies as yarn threads with their apprentices. */
    private fun skeins(c: Canvas, ss: List<app.knackling.data.Session>, y0: Float, n: Int = 4) {
        val per = ss.groupBy { it.hobbyId }.mapValues { (_, l) -> l.sumOf { it.minutes } }.toList().sortedByDescending { it.second }.take(n)
        val max = per.firstOrNull()?.second?.toFloat() ?: 1f
        per.forEachIndexed { i, (id, m) ->
            val h = b.byId[id] ?: return@forEachIndexed
            val y = y0 + i * 125f
            c.pic("pet_${h.species}_${frameOf(b.stage(h).coerceAtLeast(0))}", 175f, y + 105f, 115f)
            c.text(h.label, 260f, y + 40f, 38f, ink, bold)
            c.text(hours(m), W - 110f, y + 40f, 36f, mute, body, Paint.Align.RIGHT)
            val len = (W - 380f) * m / max
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col(h); strokeWidth = 9f; strokeCap = Paint.Cap.ROUND }
            var x = 260f; var up = true
            while (x < 260f + len - 20) { c.drawLine(x, y + 75f + if (up) -5 else 5, x + 18, y + 75f + if (up) 5 else -5, p); x += 18; up = !up }
            c.drawCircle(260f + len, y + 75f, 18f, p)
        }
    }

    private fun workshop(c: Canvas) {
        frame(c, red)
        c.text(Level.title(b.level), W / 2f, 150f, 72f, ink, hand, Paint.Align.CENTER)
        c.text(tr("уровень ${b.level} · ${b.active.size} хобби", "level ${b.level} · ${b.active.size} hobbies"), W / 2f, 205f, 36f, red, bold, Paint.Align.CENTER)
        val room = bmp("room_bg") ?: return
        val rh = 1000f; val rw = rh * room.width / room.height
        val r = RectF(W / 2f - rw / 2, 240f, W / 2f + rw / 2, 240f + rh)
        c.drawRect(RectF(r.left, r.top, r.right, r.top + rh * .5f), Paint().apply { color = 0xFF9CC8E6.toInt() })
        c.drawBitmap(room, null, r, Paint().apply { isFilterBitmap = true })
        app.knackling.domain.Decors.all.filter { it.id in b.s.items }.sortedBy { if (it.back) 0 else 1 }.forEach { d ->
            val bm = bmp("decor_${d.id}") ?: return@forEach
            val dw = rw * d.w; val dh = dw * bm.height / bm.width
            c.drawBitmap(bm, null, RectF(r.left + rw * d.x - dw / 2, r.top + rh * d.y - dh, r.left + rw * d.x + dw / 2, r.top + rh * d.y), Paint().apply { isFilterBitmap = true })
        }
        b.onStage.take(Room.spots.size).forEachIndexed { i, h ->
            val sp = Room.spots[i]; val st = b.stage(h)
            val size = rw * sp.size * app.knackling.ui.art.stageScale(st)
            val name = if (st < 0) app.knackling.ui.art.eggOf(h.species) else "pet_${h.species}_${frameOf(st, b.mood(h))}"
            c.pic(name, r.left + rw * sp.x, r.top + rh * sp.y, size * .92f)
        }
        c.drawRect(r, Paint().apply { style = Paint.Style.STROKE; strokeWidth = 6f; color = 0xFF6B4426.toInt() })
    }
}

/** Saves the card and opens the system share sheet. */
fun share(ctx: Context, bm: Bitmap) {
    val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
    val f = File(dir, "knackling.png"); f.outputStream().use { bm.compress(Bitmap.CompressFormat.PNG, 100, it) }
    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), tr("Поделиться открыткой", "Share the card")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** The card shelf: swipe between cards, post or story, share. */
@Composable
fun ShareSheet(start: String, b: Book, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val cards = remember(b, start) { cardsFor(b, start) }
    if (cards.isEmpty()) { onDone(); return }
    val pager = rememberPagerState(0) { cards.size }
    var fmt by remember { mutableIntStateOf(0) }
    Sheet(onDone) {
        Text(tr("Открытка", "A postcard"), style = T.h2)
        Text(tr("Листай – выбери, чем поделиться.", "Swipe to pick what to share."), style = T.small)
        Segments(listOf(tr("Пост 4:5", "Post 4:5"), tr("Сторис 9:16", "Story 9:16")), fmt, Modifier.padding(top = 8.dp)) { fmt = it }
        HorizontalPager(pager, Modifier.fillMaxWidth().padding(top = 10.dp), pageSpacing = 12.dp, contentPadding = PaddingValues(horizontal = if (fmt == 1) 70.dp else 36.dp)) { i ->
            val img by produceState<Bitmap?>(null, cards[i], fmt) { value = withContext(Dispatchers.Default) { runCatching { Painter(ctx, b).render(cards[i], fmt == 1) }.getOrNull() } }
            Box(Modifier.fillMaxWidth().aspectRatio(if (fmt == 1) 1080f / 1920f else 1080f / 1350f).clip(RoundedCornerShape(8.dp)).background(C.card2), contentAlignment = Alignment.Center) {
                img?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxWidth()) }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center) {
            cards.indices.forEach { i -> Box(Modifier.padding(3.dp).size(if (i == pager.currentPage) 9.dp else 6.dp).clip(CircleShape).background(if (i == pager.currentPage) C.acc else C.line)) }
        }
        Btn(tr("Поделиться", "Share"), Modifier.fillMaxWidth().padding(top = 10.dp)) { share(ctx, Painter(ctx, b).render(cards[pager.currentPage], fmt == 1)) }
        Spacer(Modifier.height(8.dp))
    }
}
