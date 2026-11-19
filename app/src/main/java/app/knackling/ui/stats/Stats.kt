package app.knackling.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.knackling.data.Session
import app.knackling.domain.Badges
import app.knackling.domain.Book
import app.knackling.domain.Kinds
import app.knackling.domain.count
import app.knackling.domain.dayShort
import app.knackling.domain.fmtDay
import app.knackling.domain.monthName
import app.knackling.domain.monthShort
import app.knackling.domain.tr
import app.knackling.domain.weekStart
import app.knackling.domain.weekday
import app.knackling.ui.Nav
import app.knackling.ui.Screen
import app.knackling.ui.art.Mini
import app.knackling.ui.art.Pic
import app.knackling.ui.art.frameOf
import app.knackling.ui.components.Icon
import app.knackling.ui.components.Icons
import app.knackling.ui.components.Segments
import app.knackling.ui.components.card
import app.knackling.ui.components.ruled
import app.knackling.ui.components.tap
import app.knackling.ui.hobby.hours
import app.knackling.ui.theme.C
import app.knackling.ui.theme.T
import java.time.LocalDate

/** A period of the ledger: its first and last day and its name. */
class Period(val from: Long, val to: Long, val name: String)

fun period(b: Book, k: Int): Period = when (k) {
    0 -> Period(b.week, b.week + 6, tr("эта неделя", "this week"))
    1 -> LocalDate.ofEpochDay(b.today).withDayOfMonth(1).let { Period(it.toEpochDay(), it.plusMonths(1).toEpochDay() - 1, monthName(it.monthValue)) }
    else -> Period(weekStart(b.today) - 52 * 7, b.today, tr("последний год", "the last year"))
}

/** Colour of the hobby a day belongs to: the one with most minutes that day. */
fun dayColor(b: Book, l: List<Session>): Color? {
    val top = l.groupBy { it.hobbyId }.maxByOrNull { (_, s) -> s.sumOf { it.minutes } }?.key ?: return null
    return Color(0xFF000000 or Kinds.color(b.byId[top]?.species ?: ""))
}

/** The ledger: the quilt of days, skeins of hours per hobby, when you work, the weeks, the facts, the next patches. */
@Composable
fun StatsScreen(b: Book, nav: Nav) {
    var k by rememberSaveable { mutableIntStateOf(1) }
    val p = period(b, k)
    val ss = remember(b, k) { b.sessions.filter { it.day in p.from..p.to } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Pic("ic_chart", 34.dp)
            Text(tr("Ведомость", "Ledger"), style = T.title, modifier = Modifier.padding(start = 8.dp).weight(1f))
            Box(Modifier.size(44.dp).clip(CircleShape).tap { nav.share = if (k == 2) "year" else "month" }, contentAlignment = Alignment.Center) { Icon(Icons.SHARE, C.ink, 21.dp) }
        }
        Segments(listOf(tr("Неделя", "Week"), tr("Месяц", "Month"), tr("Год", "Year")), k) { k = it }
        // the headline numbers, written by hand
        Row(Modifier.fillMaxWidth().padding(top = 12.dp).card().ruled(26.dp, 40.dp).padding(start = 50.dp, end = 12.dp, top = 10.dp, bottom = 10.dp)) {
            Column(Modifier.weight(1f)) {
                Text(hours(ss.sumOf { it.minutes }), style = T.hand.copy(fontSize = T.hand.fontSize * 1.5f))
                Text(p.name, style = T.tiny)
            }
            Column(Modifier.weight(1f)) {
                Text(count(ss.size, "занятие", "занятия", "занятий", "session", "sessions"), style = T.hand)
                Text(count(ss.map { it.day }.distinct().size, "день", "дня", "дней", "day", "days") + tr(" за делом", " at it"), style = T.tiny)
            }
        }
        Section(tr("Лоскутное одеяло", "The quilt"), tr("день – лоскут цвета главного хобби", "each day a patch in its main hobby's colour"))
        Quilt(b, p, k)
        Legend(b, ss)
        Section(tr("Мотки часов", "Skeins of hours"), tr("сколько времени получило каждое хобби", "how much time each hobby got"))
        Skeins(b, ss, nav)
        Section(tr("Когда ты мастеришь", "When you make things"), tr("дни недели и время суток", "weekdays and times of day"))
        When(b, if (ss.size >= 5) ss else b.sessions)
        Section(tr("Двенадцать недель", "Twelve weeks"), tr("минуты по неделям, по хобби", "minutes by week, by hobby"))
        Weeks(b)
        Section(tr("Записи в ведомости", "Ledger notes"), null)
        Facts(b, ss, p)
        val next = Badges.next(b).sortedByDescending { it.third }.take(3)
        if (next.isNotEmpty()) {
            Section(tr("Почти нашивка", "Almost a patch"), null)
            next.forEach { (l, goal, f) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp).card().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Pic("patch_${l.patch}", 40.dp, alpha = .55f)
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Text(l.title, style = T.bodyB); Text(l.what(goal), style = T.tiny)
                        Box(Modifier.padding(top = 4.dp).fillMaxWidth().height(5.dp).clip(RoundedCornerShape(50)).background(C.track)) {
                            Box(Modifier.fillMaxWidth(f).height(5.dp).clip(RoundedCornerShape(50)).background(C.gold)) }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Section(title: String, sub: String?) {
    Column(Modifier.padding(top = 20.dp, bottom = 8.dp)) {
        Text(title, style = T.h3)
        if (sub != null) Text(sub, style = T.tiny)
    }
}

/**
 * The quilt: a week is a row of seven patches (a month) or a column (the year). Empty days are plain linen
 * with a dashed seam; a day of work is a patch in its hobby's colour, deeper for more minutes.
 */
@Composable
private fun Quilt(b: Book, p: Period, k: Int) {
    val byDay = remember(b) { b.sessions.groupBy { it.day } }
    val linen = if (C.dark) Color(0xFF3A3129) else Color(0xFFEDE2CC); val seam = C.frame; val today = C.acc
    val start = weekStart(p.from); val weeks = ((p.to - start) / 7 + 1).toInt()
    val year = k == 2
    Column(Modifier.fillMaxWidth().card().padding(10.dp)) {
        if (!year) Row(Modifier.fillMaxWidth()) { (0..6).forEach { Text(dayShort(it), style = T.tiny, textAlign = TextAlign.Center, modifier = Modifier.weight(1f)) } }
        Canvas(Modifier.fillMaxWidth().then(if (year) Modifier.height(118.dp) else Modifier.aspectRatio(7f / weeks.coerceAtLeast(1)))) {
            val cols = if (year) weeks else 7; val rows = if (year) 7 else weeks
            val cell = minOf(size.width / cols, size.height / rows); val gap = cell * .1f
            val x0 = (size.width - cell * cols) / 2
            for (w in 0 until weeks) for (d in 0..6) {
                val day = start + w * 7 + d
                val (cx, cy) = if (year) w to d else d to w
                val o = Offset(x0 + cx * cell + gap / 2, cy * cell + gap / 2); val s = Size(cell - gap, cell - gap)
                if (day < p.from || day > p.to || day > b.today) continue
                val l = byDay[day]
                val col = l?.let { dayColor(b, it) }
                if (col == null) {
                    drawRoundRect(linen, o, s, CornerRadius(cell * .12f))
                } else {
                    val m = l.sumOf { it.minutes }
                    drawRoundRect(col.copy(alpha = (.45f + m / 180f).coerceAtMost(1f)), o, s, CornerRadius(cell * .12f))
                    if (l.map { it.hobbyId }.distinct().size > 1) drawCircle(Color.White.copy(alpha = .8f), cell * .09f, Offset(o.x + s.width * .78f, o.y + s.height * .22f))
                }
                if (cell > 14) drawRoundRect(seam.copy(alpha = .55f), Offset(o.x + 2, o.y + 2), Size(s.width - 4, s.height - 4), CornerRadius(cell * .1f),
                    style = Stroke(1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 3f))))
                if (day == b.today) drawRoundRect(today, o, s, CornerRadius(cell * .12f), style = Stroke(2.5f))
            }
        }
        if (year) Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            val f = LocalDate.ofEpochDay(p.from); val t = LocalDate.ofEpochDay(p.to)
            Text(monthShort(f.monthValue), style = T.tiny); Text(monthShort(f.plusMonths(6).monthValue), style = T.tiny); Text(monthShort(t.monthValue), style = T.tiny)
        }
    }
}

@Composable
private fun Legend(b: Book, ss: List<Session>) {
    val used = ss.map { it.hobbyId }.distinct().mapNotNull { b.byId[it] }
    if (used.isEmpty()) return
    Row(Modifier.fillMaxWidth().padding(top = 6.dp).horizontalScrollSafe(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        used.forEach { h -> Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFF000000 or Kinds.color(h.species))))
            Text(" " + h.label, style = T.tiny, maxLines = 1)
        } }
    }
}

@Composable
private fun Modifier.horizontalScrollSafe() = this.then(Modifier.horizontalScroll(rememberScrollState()))

/** Hours per hobby as skeins of yarn: a ball at the start, the thread as long as the time. */
@Composable
private fun Skeins(b: Book, ss: List<Session>, nav: Nav) {
    val per = ss.groupBy { it.hobbyId }.mapValues { (_, l) -> l.sumOf { it.minutes } }.toList().sortedByDescending { it.second }
    if (per.isEmpty()) { Text(tr("В этом периоде пока пусто.", "Nothing in this period yet."), style = T.small); return }
    val max = per.first().second.toFloat()
    val total = per.sumOf { it.second }.coerceAtLeast(1)
    Column(Modifier.fillMaxWidth().card().padding(10.dp)) {
        per.forEach { (id, m) ->
            val h = b.byId[id] ?: return@forEach
            val col = Color(0xFF000000 or Kinds.color(h.species))
            Row(Modifier.fillMaxWidth().tap { nav.push(Screen.HobbyInfo(h.id)) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Mini(h.species, 38.dp, frame = frameOf(b.stage(h).coerceAtLeast(0)))
                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                    Row { Text(h.label, style = T.smallB, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis); Text(hours(m) + " · ${m * 100 / total}%", style = T.tiny) }
                    Canvas(Modifier.fillMaxWidth().height(14.dp)) {
                        val y = size.height / 2; val len = size.width * (m / max).coerceAtLeast(.04f)
                        // the thread, a little wavy, then the ball of yarn at its end
                        var x = 0f; var up = true
                        while (x < len - 8) { drawLine(col, Offset(x, y + if (up) -1.5f else 1.5f), Offset(x + 6, y + if (up) 1.5f else -1.5f), 3f); x += 6; up = !up }
                        drawCircle(col, size.height / 2, Offset(len - 4, y))
                        drawLine(Color.White.copy(alpha = .5f), Offset(len - 9, y - 3), Offset(len + 1, y + 3), 1.5f)
                    }
                }
            }
        }
    }
}

/** A board of weekdays by times of day; the darker the square, the more time there. */
@Composable
private fun When(b: Book, ss: List<Session>) {
    val bands = listOf(tr("утро", "morning") to 5..11, tr("день", "day") to 12..17, tr("вечер", "evening") to 18..22, tr("ночь", "night") to -1..-1)
    fun band(h: Int) = when (h) { in 5..11 -> 0; in 12..17 -> 1; in 18..22 -> 2; else -> 3 }
    val grid = Array(4) { IntArray(7) }
    ss.forEach { s -> grid[band(b.hourOf(s.start))][b.date(s.day).dayOfWeek.value - 1] += s.minutes }
    val max = grid.maxOf { it.max() }.coerceAtLeast(1)
    val heat = C.heat
    Column(Modifier.fillMaxWidth().card().padding(10.dp)) {
        Row { Spacer(Modifier.width(56.dp)); (0..6).forEach { Text(dayShort(it), style = T.tiny, textAlign = TextAlign.Center, modifier = Modifier.weight(1f)) } }
        bands.forEachIndexed { i, (name, _) ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                Text(name, style = T.tiny, modifier = Modifier.width(56.dp))
                (0..6).forEach { d ->
                    val v = grid[i][d]
                    Box(Modifier.weight(1f).padding(2.dp).aspectRatio(1.4f).clip(RoundedCornerShape(5.dp)).background(if (v == 0) heat[0] else heat[(1 + v * 3.99 / max).toInt().coerceAtMost(4)]))
                }
            }
        }
        val best = (0..3).flatMap { i -> (0..6).map { d -> Triple(i, d, grid[i][d]) } }.maxByOrNull { it.third }
        if (best != null && best.third > 0) Text(tr("Чаще всего – ${weekday(b.week + best.second)}, ${bands[best.first].first}", "Most often: ${weekday(b.week + best.second)}, ${bands[best.first].first}"),
            style = T.handS, modifier = Modifier.padding(top = 6.dp))
    }
}

/** Twelve weeks as stacks of coloured blocks, one colour per hobby; the goal line of all hobbies together. */
@Composable
private fun Weeks(b: Book) {
    val weeks = (11 downTo 0).map { b.week - it * 7L }
    val stacks = weeks.map { w -> b.sessions.filter { it.day in w..w + 6 }.groupBy { it.hobbyId }.mapValues { (_, l) -> l.sumOf { it.minutes } } }
    val goal = b.active.sumOf { it.goal }
    val max = maxOf(stacks.maxOf { it.values.sum() }, goal, 60).toFloat()
    val line = C.acc; val track = C.track
    Column(Modifier.fillMaxWidth().card().padding(10.dp)) {
        Canvas(Modifier.fillMaxWidth().height(130.dp)) {
            val bw = size.width / 12
            stacks.forEachIndexed { i, st ->
                var y = size.height
                if (st.isEmpty()) drawRoundRect(track, Offset(i * bw + bw * .15f, size.height - 4), Size(bw * .7f, 4f), CornerRadius(2f))
                st.entries.sortedBy { it.key }.forEach { (id, m) ->
                    val hgt = size.height * m / max
                    drawRoundRect(Color(0xFF000000 or Kinds.color(b.byId[id]?.species ?: "")), Offset(i * bw + bw * .15f, y - hgt), Size(bw * .7f, hgt - 1.5f), CornerRadius(3f))
                    y -= hgt
                }
            }
            if (goal > 0) { val gy = size.height * (1 - goal / max); drawLine(line, Offset(0f, gy), Offset(size.width, gy), 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(fmtDay(weeks.first()), style = T.tiny); if (goal > 0) Text(tr("пунктир – сумма целей", "dashes: all goals together"), style = T.tiny.copy(color = C.acc)); Text(tr("эта", "this"), style = T.tiny)
        }
    }
}

/** The facts as lines of a handwritten ledger. */
@Composable
private fun Facts(b: Book, ss: List<Session>, p: Period) {
    val lines = buildList {
        if (ss.isNotEmpty()) {
            add(tr("Среднее занятие", "Average session") to hours(ss.sumOf { it.minutes } / ss.size))
            val longest = ss.maxBy { it.minutes }
            add(tr("Самое долгое", "The longest") to hours(longest.minutes) + " · " + (b.byId[longest.hobbyId]?.label ?: ""))
            ss.groupBy { it.day }.maxByOrNull { (_, l) -> l.sumOf { it.minutes } }?.let { (d, l) -> add(tr("Лучший день", "Best day") to fmtDay(d) + " · " + hours(l.sumOf { it.minutes })) }
            if (ss.any { it.flow > 0 }) add(tr("В потоке", "In the flow") to "${ss.count { it.flow == 3 } * 100 / ss.size}%")
            add(tr("Затей", "Challenges") to "${ss.count { it.idea >= 0 }}")
            add(tr("Фото работ", "Photos of work") to "${ss.count { it.photo.isNotEmpty() }}")
        }
        add(tr("Работ закончено", "Works finished") to "${b.projects.count { it.done in p.from..p.to }}")
        add(tr("Серия сейчас", "Streak now") to count(b.dayStreak, "день", "дня", "дней", "day", "days"))
        add(tr("Лучшая серия", "Best streak") to count(b.bestDayStreak, "день", "дня", "дней", "day", "days"))
        add(tr("Недель в ритме подряд", "Weeks in rhythm") to "${b.weekStreak}")
    }
    Column(Modifier.fillMaxWidth().card().ruled(30.dp, 30.dp).padding(start = 40.dp, end = 12.dp, top = 12.dp, bottom = 8.dp)) {
        lines.forEach { (l, v) ->
            Row(Modifier.fillMaxWidth().height(30.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(l, style = T.small.copy(color = C.ink), modifier = Modifier.weight(1f))
                Text(v, style = T.handS, maxLines = 1)
            }
        }
    }
}

