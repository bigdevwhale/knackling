package app.knackling.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.knackling.data.Hobby
import app.knackling.domain.Book
import app.knackling.domain.Level
import app.knackling.domain.Mood
import app.knackling.domain.Quests
import app.knackling.domain.fmt
import app.knackling.domain.tr
import app.knackling.ui.Nav
import app.knackling.ui.Screen
import app.knackling.ui.art.Ico
import app.knackling.ui.art.Pic
import app.knackling.ui.art.Workshop
import app.knackling.ui.art.toolOf
import app.knackling.ui.components.Icon
import app.knackling.ui.components.Icons
import app.knackling.ui.components.card
import app.knackling.ui.components.tap
import app.knackling.ui.theme.C
import app.knackling.ui.theme.T
import kotlinx.coroutines.delay

/** One screen: the workshop with its apprentices, the shelf of hobbies with the week's goals, three doors. */
@Composable
fun HomeScreen(b: Book, nav: Nav) {
    var sheet by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize()) {
        Hud(b, nav)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Workshop(b, Modifier.fillMaxSize()) { nav.push(Screen.HobbyInfo(it.id)) }
            b.s.live?.let { l -> b.byId[l.hobbyId]?.let { h -> LiveTag(h, l.ms(), l.paused, Modifier.align(Alignment.TopCenter).padding(top = 8.dp)) { nav.push(Screen.Live) } } }
                ?: Bubble(b, Modifier.align(Alignment.TopCenter).padding(top = 6.dp, start = 30.dp, end = 30.dp)) { nav.push(Screen.HobbyInfo(it.id)) }
        }
        Shelf(b, nav)
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Door("bulb", tr("Затеи", "Challenges"), Modifier.weight(1f)) { sheet = "ideas" }
            Door("scroll", tr("Задания", "Tasks"), Modifier.weight(1f), dot = Quests.waiting(b)) { sheet = "quests" }
            Door("chest", tr("Лавка", "Shop"), Modifier.weight(1f)) { nav.push(Screen.Shop) }
        }
    }
    when (sheet) {
        "ideas" -> IdeasSheet(b, nav) { sheet = null }
        "quests" -> QuestsSheet(b) { sheet = null }
    }
}

/** The top line: the workshop's name and level ring, sparks, the day streak, the settings. */
@Composable
private fun Hud(b: Book, nav: Nav) {
    Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
            val p = Level.progress(b.totalXp); val acc = C.acc; val track = C.track
            Canvas(Modifier.fillMaxSize()) {
                drawArc(track, -90f, 360f, false, style = Stroke(4.dp.toPx()))
                drawArc(acc, -90f, 360f * p, false, style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
            }
            Text("${b.level}", style = T.numS)
        }
        Column(Modifier.weight(1f).padding(start = 8.dp)) {
            Text(Level.title(b.level), style = T.h3, maxLines = 1)
            Text(tr("уровень ${b.level} · ${b.active.size} хобби", "level ${b.level} · ${b.active.size} hobbies"), style = T.tiny)
        }
        Chip2("ic_spark", fmt(b.sparks))
        Spacer(Modifier.width(6.dp))
        Chip2("patch_0", "${b.dayStreak}", dim = b.dayStreak == 0)
        Box(Modifier.size(42.dp).clip(CircleShape).tap { nav.push(Screen.Settings) }, contentAlignment = Alignment.Center) { Icon(Icons.GEAR, C.mute, 22.dp) }
    }
}

@Composable
private fun Chip2(icon: String, text: String, dim: Boolean = false) {
    Row(Modifier.clip(RoundedCornerShape(50)).background(C.card).border(1.dp, C.line, RoundedCornerShape(50)).padding(start = 4.dp, end = 10.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Pic(icon, 24.dp, grey = dim)
        Text(text, style = T.smallB, modifier = Modifier.padding(start = 2.dp))
    }
}

/** A running session, pinned over the scene. */
@Composable
private fun LiveTag(h: Hobby, ms: Long, paused: Boolean, modifier: Modifier, onClick: () -> Unit) {
    var now by remember { mutableStateOf(ms) }
    androidx.compose.runtime.LaunchedEffect(ms, paused) { now = ms; if (!paused) while (true) { delay(1000); now += 1000 } }
    Row(modifier.clip(RoundedCornerShape(50)).background(if (paused) C.ink else C.ok).tap(onClick).padding(horizontal = 14.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.TIMER, Color.White, 18.dp)
        Text("${h.name} · ${clock(now)}" + if (paused) tr(" · пауза", " · paused") else "", style = T.smallB.copy(color = Color.White), modifier = Modifier.padding(start = 6.dp))
    }
}

fun clock(ms: Long): String { val s = ms / 1000; val h = s / 3600; val m = s % 3600 / 60; val ss = s % 60
    return if (h > 0) "$h:${m.toString().padStart(2, '0')}:${ss.toString().padStart(2, '0')}" else "$m:${ss.toString().padStart(2, '0')}" }

/** One apprentice says something: it misses its hobby, it liked today, or it has a challenge. */
@Composable
private fun Bubble(b: Book, modifier: Modifier, onClick: (Hobby) -> Unit) {
    val list = b.onStage.filter { b.stage(it) >= -1 }
    if (list.isEmpty()) return
    val missing = list.filter { b.mood(it) == Mood.MISSING || b.mood(it) == Mood.ASLEEP }.minByOrNull { b.lastDay(it) ?: it.since }
    val h = missing ?: list[(b.today % list.size).toInt()]
    val text = when {
        b.stage(h) < 0 -> tr("${h.name} ещё в яйце. Первое занятие – и вылупится!", "${h.name} is still in the egg. One session and it hatches!")
        missing != null -> tr("${h.name}: «${h.label} давно ждёт… Может, десять минут?»", "${h.name}: «${h.label} has been waiting… ten minutes?»")
        b.mood(h) == Mood.HAPPY -> tr("${h.name}: «Хорошо сегодня поработали!»", "${h.name}: «Good work today!»")
        else -> "${h.name}: «${b.idea(h).text}»"
    }
    Text(text, style = T.handS.copy(textAlign = TextAlign.Center), maxLines = 2, overflow = TextOverflow.Ellipsis,
        modifier = modifier.clip(RoundedCornerShape(16.dp)).background(C.card.copy(alpha = .93f)).border(1.5.dp, C.frame, RoundedCornerShape(16.dp)).tap { onClick(h) }
            .padding(horizontal = 14.dp, vertical = 6.dp))
}

/**
 * The shelf: one jar per hobby with its tool, filling up to the week's goal; the last place adds a hobby.
 * A wooden board runs under the jars.
 */
@Composable
private fun Shelf(b: Book, nav: Nav) {
    val wood = C.wood; val wood2 = C.wood2
    Box(Modifier.fillMaxWidth().height(100.dp)) {
        Box(Modifier.fillMaxWidth().height(16.dp).align(Alignment.BottomCenter).drawBehind {
            drawRect(wood); drawRect(wood2, Offset(0f, size.height * .6f), Size(size.width, size.height * .4f))
        })
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 0.dp).padding(bottom = 12.dp),
            verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            b.active.forEach { h -> Jar(b, h) { nav.push(Screen.HobbyInfo(h.id)) } }
            Column(Modifier.width(64.dp).tap { nav.push(Screen.AddHobby) }, horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).border(2.dp, C.line, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) { Icon(Icons.PLUS, C.mute, 24.dp) }
                Text(tr("хобби", "hobby"), style = T.tiny, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

/** A glass jar: the tool inside, the week's minutes rising in the hobby's colour. */
@Composable
private fun Jar(b: Book, h: Hobby, onClick: () -> Unit) {
    val col = Color(0xFF000000 or app.knackling.domain.Kinds.color(h.species))
    val f = if (h.goal <= 0) 0f else (b.weekMin(h).toFloat() / h.goal).coerceIn(0f, 1f)
    val done = h.goal > 0 && f >= 1f
    val glass = C.card; val frame = C.frame
    Column(Modifier.width(66.dp).tap(onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(56.dp, 62.dp).drawBehind {
            val r = 10.dp.toPx(); val top = 6.dp.toPx()
            drawRoundRect(glass.copy(alpha = .75f), Offset(0f, top), Size(size.width, size.height - top), androidx.compose.ui.geometry.CornerRadius(r))
            val fh = (size.height - top) * f
            if (fh > 0) drawRoundRect(col.copy(alpha = .55f), Offset(0f, size.height - fh), Size(size.width, fh), androidx.compose.ui.geometry.CornerRadius(r))
            drawRoundRect(frame, Offset(0f, top), Size(size.width, size.height - top), androidx.compose.ui.geometry.CornerRadius(r), style = Stroke(1.5.dp.toPx()))
            drawRoundRect(if (done) Color(0xFFE2B33C) else frame, Offset(size.width * .15f, 0f), Size(size.width * .7f, top + 2.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
        }, contentAlignment = Alignment.Center) {
            Pic(toolOf(h.species), 40.dp, Modifier.padding(top = 6.dp))
        }
        Text(if (h.goal > 0) "${b.weekMin(h)}/${h.goal}" else "${b.weekMin(h)}", style = T.tiny.copy(color = if (done) C.ok else C.mute), maxLines = 1)
    }
}

/** A door at the bottom: a pixel thing and a word, a red dot when something waits. */
@Composable
private fun Door(icon: String, label: String, modifier: Modifier, dot: Int = 0, onClick: () -> Unit) {
    Box(modifier) {
        Row(Modifier.fillMaxWidth().padding(bottom = 3.dp).card().tap(onClick).padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Ico(icon, 30.dp)
            Text(label, style = T.smallB, modifier = Modifier.padding(start = 4.dp), maxLines = 1)
        }
        if (dot > 0) Box(Modifier.align(Alignment.TopEnd).padding(top = 2.dp, end = 4.dp).size(18.dp).clip(CircleShape).background(C.acc), contentAlignment = Alignment.Center) {
            Text("$dot", style = T.tiny.copy(color = Color.White))
        }
    }
}
