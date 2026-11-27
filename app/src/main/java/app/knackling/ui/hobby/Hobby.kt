package app.knackling.ui.hobby

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.knackling.container
import app.knackling.data.Hobby
import app.knackling.domain.Book
import app.knackling.domain.Grow
import app.knackling.domain.Kinds
import app.knackling.domain.count
import app.knackling.domain.dayShort
import app.knackling.domain.fmt
import app.knackling.domain.fmt1
import app.knackling.domain.relDay
import app.knackling.domain.tr
import app.knackling.ui.Nav
import app.knackling.ui.Screen
import app.knackling.ui.art.Creature
import app.knackling.ui.art.Pic
import app.knackling.ui.art.eggOf
import app.knackling.ui.art.frameOf
import app.knackling.ui.art.stageScale
import app.knackling.ui.art.toolOf
import app.knackling.ui.components.Btn
import app.knackling.ui.components.Ghost
import app.knackling.ui.components.Icon
import app.knackling.ui.components.Icons
import app.knackling.ui.components.Page
import app.knackling.ui.components.Segments
import app.knackling.ui.components.card
import app.knackling.ui.components.tap
import app.knackling.ui.journal.ProjectRow
import app.knackling.ui.journal.SessionRow
import app.knackling.ui.session.unitOf
import app.knackling.ui.theme.C
import app.knackling.ui.theme.T

fun colorOf(h: Hobby) = Color(0xFF000000 or Kinds.color(h.species))

/** A hobby and its apprentice: the rank ribbon, the week, the challenge, the projects and the sessions. */
@Composable
fun HobbyPage(id: Long, tab0: Int, b: Book, nav: Nav) {
    val c = LocalContext.current.container
    val h = b.byId[id] ?: run { LaunchedEffect(Unit) { nav.pop() }; return }
    var tab by rememberSaveable { mutableIntStateOf(tab0) }
    val st = b.stage(h)
    val col = colorOf(h)
    Page(h.label, { nav.pop() }, action = {
        Box(Modifier.size(44.dp).clip(CircleShape).tap { nav.share = "pet:${h.id}" }, contentAlignment = Alignment.Center) { Icon(Icons.SHARE, C.ink, 21.dp) }
        Box(Modifier.size(44.dp).clip(CircleShape).tap { nav.push(Screen.EditHobby(h.id)) }, contentAlignment = Alignment.Center) { Icon(Icons.EDIT, C.ink, 21.dp) }
    }) {
        // the apprentice on its round rug, the rank on a ribbon
        Box(Modifier.fillMaxWidth().height(230.dp), contentAlignment = Alignment.BottomCenter) {
            Canvas(Modifier.fillMaxWidth().height(70.dp)) {
                drawOval(col.copy(alpha = .22f), Offset(size.width * .18f, size.height * .25f), Size(size.width * .64f, size.height * .7f))
                drawOval(col.copy(alpha = .35f), Offset(size.width * .26f, size.height * .38f), Size(size.width * .48f, size.height * .45f))
            }
            if (st < 0) Pic(eggOf(h.species), 120.dp, Modifier.padding(bottom = 20.dp))
            else {
                if (st >= Grow.GLOW) app.knackling.ui.art.Glow(230.dp)
                Creature(h.species, frameOf(st, b.mood(h)), (220 * stageScale(st)).dp, Modifier.padding(bottom = 6.dp), shiny = h.shiny)
            }
        }
        Text(h.name, style = T.hand.copy(fontSize = T.hand.fontSize * 1.3f, textAlign = TextAlign.Center), modifier = Modifier.fillMaxWidth())
        Rank(b, h)
        // three paper tags: the week, all the time, the last time
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tag(if (h.goal > 0) "${b.weekMin(h)}/${h.goal}" else "${b.weekMin(h)}", tr("мин за неделю", "min this week"), Modifier.weight(1f), done = h.goal > 0 && b.weekMin(h) >= h.goal)
            Tag(hours(b.minutes(h)), tr("всего", "in all"), Modifier.weight(1f))
            Tag(b.lastDay(h)?.let { relDay(it) } ?: "–", b.mood(h).text, Modifier.weight(1f))
        }
        Spacer(Modifier.height(14.dp))
        Segments(listOf(tr("Обзор", "Overview"), tr("Проекты", "Projects"), tr("Записи", "Sessions")), tab) { tab = it }
        Spacer(Modifier.height(10.dp))
        when (tab) {
            0 -> Overview(b, h, nav)
            1 -> {
                val list = b.projectsOf(h)
                list.forEach { p -> ProjectRow(p, b) { nav.push(Screen.ProjectInfo(p.id)) } }
                if (list.isEmpty()) Text(tr("Проект – это то, что ты делаешь: шарф, песня, книга. Законченный даёт +100 опыта.", "A project is a thing you're making: a scarf, a song, a book. Finishing one gives +100 experience."),
                    style = T.small, modifier = Modifier.padding(vertical = 8.dp))
                Ghost("+ " + tr("Новый проект", "New project"), Modifier.fillMaxWidth().padding(top = 6.dp), small = true) { nav.push(Screen.ProjectEdit(hobbyId = h.id)) }
            }
            else -> {
                val list = b.of[h.id].orEmpty()
                if (list.isEmpty()) Text(tr("Пока ни одного занятия.", "No sessions yet."), style = T.small)
                list.take(60).forEach { s -> SessionRow(s, b, showHobby = false, day = true) { nav.push(Screen.Log(h.id, sessionId = s.id)) } }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Ghost(tr("Записать", "Log"), Modifier.weight(1f)) { nav.push(Screen.Log(h.id)) }
            Btn(if (b.s.live?.hobbyId == h.id) tr("Идёт…", "Running…") else tr("Начать", "Start"), Modifier.weight(1f)) {
                if (b.s.live == null) c.write { startLive(h.id) }
                nav.push(Screen.Live)
            }
        }
    }
}

fun hours(min: Int): String = if (min < 60) tr("$min мин", "$min min") else tr("${fmt1(min / 60.0)} ч", "${fmt1(min / 60.0)} h")

/**
 * The rank on a tape measure: the stretch from this rank to the next, and under it the whole ladder
 * of ten ranks up to ten thousand hours as pips.
 */
@Composable
private fun Rank(b: Book, h: Hobby) {
    val st = b.stage(h); val p = b.progress(h)
    val gold = C.gold; val track = C.track; val ink = C.ink
    Column(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(Grow.name(st).uppercase() + if (st >= 0) tr(" · ранг ${st + 1} из ${Grow.TOP + 1}", " · rank ${st + 1} of ${Grow.TOP + 1}") else "", style = T.sec.copy(color = C.acc))
        if (st in 0 until Grow.TOP) {
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(Grow.name(st), style = T.tiny, modifier = Modifier.width(78.dp), maxLines = 2)
                Canvas(Modifier.weight(1f).height(18.dp)) {
                    val y = size.height / 2
                    drawRoundRect(track, Offset(0f, y - 5.dp.toPx()), Size(size.width, 10.dp.toPx()), CornerRadius(5.dp.toPx()))
                    drawRoundRect(gold, Offset(0f, y - 5.dp.toPx()), Size(size.width * p, 10.dp.toPx()), CornerRadius(5.dp.toPx()))
                    var x = 0f; var i = 0
                    while (x <= size.width) { drawLine(ink.copy(alpha = .25f), Offset(x, y - 5.dp.toPx()), Offset(x, y - (if (i % 5 == 0) 1 else 3).dp.toPx()), 1.dp.toPx()); x += size.width / 40; i++ }
                }
                Text(Grow.name(st + 1), style = T.tiny.copy(textAlign = TextAlign.End), modifier = Modifier.width(78.dp), maxLines = 2)
            }
        }
        // the whole ladder: ten pips with the hours each rank takes
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.Top) {
            (0..Grow.TOP).forEach { k ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Box(Modifier.size(if (k == st) 14.dp else 10.dp).clip(CircleShape).background(if (k <= st) (if (k >= Grow.GLOW) C.gold else C.acc) else C.track))
                    val hrs = Grow.hours(k)
                    Text(if (k == 0) "0" else if (hrs >= 1000) "${hrs / 1000}k" else "$hrs", style = T.tiny.copy(fontSize = T.tiny.fontSize * .8f, color = if (k <= st) C.ink else C.mute))
                }
            }
        }
        Text(tr("часы практики", "hours of practice"), style = T.tiny.copy(fontSize = T.tiny.fontSize * .8f))
        val left = b.toNext(h)
        Text(when { st < 0 -> Grow.about(-1); st >= Grow.TOP -> tr("Высшее звание. Десять тысяч часов позади", "The highest rank. Ten thousand hours behind you")
            else -> tr("До звания «${Grow.name(st + 1)}»: ≈ ${hours(left)}", "≈ ${hours(left)} to «${Grow.name(st + 1)}»") },
            style = T.small, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
    }
}

/** A paper tag on a string: a big value and a word. */
@Composable
private fun Tag(value: String, label: String, modifier: Modifier, done: Boolean = false) {
    val frame = C.frame
    Column(modifier.padding(top = 6.dp).drawBehind {
        drawLine(frame, Offset(size.width / 2, -6.dp.toPx()), Offset(size.width / 2, 8.dp.toPx()), 1.5.dp.toPx())
    }.card(color = if (done) C.okSoft else null).padding(vertical = 8.dp, horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(C.bg).border(1.dp, frame, CircleShape))
        Text(value, style = T.numS, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
        Text(label, style = T.tiny, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** The overview: the challenge note, the week by days, the facts of the hobby, the road of ranks. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Overview(b: Book, h: Hobby, nav: Nav) {
    val c = LocalContext.current.container
    val col = colorOf(h)
    // the challenge, pinned like a note
    Column(Modifier.fillMaxWidth().card(color = C.goldSoft).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Pic("ic_bulb", 26.dp)
            Text(tr("Затея дня · ${h.name}", "Challenge of the day · ${h.name}"), style = T.smallB, modifier = Modifier.padding(start = 6.dp).weight(1f))
            if (!b.ideaDone(h)) Text(tr("другая", "another"), style = T.smallB.copy(color = C.mute), modifier = Modifier.tap { c.write { anotherIdea(h) } }.padding(4.dp))
        }
        Text(b.idea(h).text, style = T.hand, modifier = Modifier.padding(vertical = 6.dp))
        if (b.ideaDone(h)) Text(tr("✓ Сделано сегодня", "✓ Done today"), style = T.smallB.copy(color = C.ok))
        else Text(tr("Взяться: +20 опыта", "Take it: +20 experience"), style = T.smallB.copy(color = C.acc), modifier = Modifier.tap {
            if (b.s.live == null) c.write { startLive(h.id, idea = true) }; nav.push(Screen.Live) }.padding(vertical = 4.dp))
    }
    // the week: seven spools filled by the minutes of each day
    Text(tr("ЭТА НЕДЕЛЯ", "THIS WEEK"), style = T.sec, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
    val days = (0..6).map { k -> b.minutes(h, b.week + k, b.week + k) }
    val max = maxOf(days.max(), if (h.goal > 0) h.goal / 4 else 30)
    Row(Modifier.fillMaxWidth().card().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        days.forEachIndexed { k, m ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                val today = b.week + k == b.today
                Box(Modifier.height(70.dp).width(18.dp), contentAlignment = Alignment.BottomCenter) {
                    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(5.dp)).background(C.track))
                    if (m > 0) Box(Modifier.fillMaxWidth().height((70f * m / max).coerceIn(4f, 70f).dp).clip(RoundedCornerShape(5.dp)).background(col))
                }
                Text(dayShort(k), style = T.tiny.copy(color = if (today) C.acc else C.mute))
                Text(if (m > 0) "$m" else "", style = T.tiny.copy(color = C.ink))
            }
        }
    }
    // the facts
    val list = b.of[h.id].orEmpty()
    if (list.isNotEmpty()) {
        Text(tr("ВСЁ ВРЕМЯ", "ALL THE TIME"), style = T.sec, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
        val unit = unitOf(h)
        val amount = list.sumOf { it.amount }
        val avg = list.sumOf { it.minutes } / list.size
        val longest = list.maxOf { it.minutes }
        val wd = list.groupBy { b.date(it.day).dayOfWeek.value - 1 }.maxByOrNull { (_, l) -> l.sumOf { it.minutes } }?.key
        val flow = list.count { it.flow == 3 } * 100 / list.size
        val facts = listOfNotNull(
            count(list.size, "занятие", "занятия", "занятий", "session", "sessions") to tr("с ${app.knackling.domain.fmtDayYear(h.since)}", "since ${app.knackling.domain.fmtDayYear(h.since)}"),
            hours(avg) to tr("в среднем за раз", "on average"),
            hours(longest) to tr("самое долгое", "the longest"),
            if (amount > 0 && unit != null) unit.of(amount) to tr("сделано", "done") else null,
            wd?.let { app.knackling.domain.weekday(b.week + it) to tr("любимый день", "favourite day") },
            if (list.any { it.flow > 0 }) "$flow%" to tr("занятий в потоке", "sessions in flow") else null,
            count(list.count { it.photo.isNotEmpty() }, "фото", "фото", "фото", "photo", "photos") to tr("на витрине", "in the showcase"),
            count(b.projectsOf(h).count { it.done > 0 }, "работа", "работы", "работ", "work", "works") to tr("закончено", "finished"),
        )
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), maxItemsInEachRow = 2) {
            facts.forEach { (v, l) ->
                Column(Modifier.weight(1f).card().padding(horizontal = 12.dp, vertical = 9.dp)) {
                    Text(v, style = T.bodyB, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(l, style = T.tiny)
                }
            }
            if (facts.size % 2 == 1) Spacer(Modifier.weight(1f))
        }
    }
    // the road of ranks: what the apprentice looked like and will look like
    Text(tr("ПУТЬ ПОДМАСТЕРЬЯ", "THE APPRENTICE'S ROAD"), style = T.sec, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
    val st = b.stage(h)
    Row(Modifier.fillMaxWidth().card().padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
        listOf(-1, 0, 1, 2, 3, Grow.GLOW).forEach { k ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                val reached = k <= st
                if (k < 0) Pic(eggOf(h.species), 34.dp, alpha = if (reached) 1f else .35f)
                else Box(contentAlignment = Alignment.Center) {
                    if (k >= Grow.GLOW && reached) app.knackling.ui.art.Glow(48.dp)
                    app.knackling.ui.art.Mini(h.species, (30 + k.coerceAtMost(4) * 5).dp, dim = !reached, frame = when (k) { 0 -> 0; 1 -> 1; 2 -> 2; 3 -> 3; else -> 4 })
                }
                Text(if (k < 0) tr("яйцо", "egg") else if (k >= Grow.GLOW) tr("сияние", "glow") else "${Grow.hours(k)}" + tr(" ч", " h"), style = T.tiny.copy(color = if (reached) C.ink else C.mute))
            }
        }
    }
}
