package app.knackling.ui.session

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.knackling.container
import app.knackling.data.Hobby
import app.knackling.data.Photos
import app.knackling.data.Project
import app.knackling.data.Session
import app.knackling.domain.Book
import app.knackling.domain.Catalog
import app.knackling.domain.Grow
import app.knackling.domain.Mood
import app.knackling.domain.dayOf
import app.knackling.domain.fmtDay
import app.knackling.domain.relDay
import app.knackling.domain.tr
import app.knackling.domain.zone
import app.knackling.ui.Nav
import app.knackling.ui.Screen
import app.knackling.ui.art.Creature
import app.knackling.ui.art.Pic
import app.knackling.ui.art.eggOf
import app.knackling.ui.art.frameOf
import app.knackling.ui.art.toolOf
import app.knackling.ui.components.Btn
import app.knackling.ui.components.Chip
import app.knackling.ui.components.Field
import app.knackling.ui.components.Ghost
import app.knackling.ui.components.Icon
import app.knackling.ui.components.Icons
import app.knackling.ui.components.Page
import app.knackling.ui.components.Popup
import app.knackling.ui.components.Sheet
import app.knackling.ui.components.card
import app.knackling.ui.components.tap
import app.knackling.ui.home.clock
import app.knackling.ui.theme.C
import app.knackling.ui.theme.T
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime

/** The unit a hobby counts in, or null: a catalogue unit, or the user's own words after «:». */
fun unitOf(h: Hobby): app.knackling.domain.Unit? {
    if (h.unit.startsWith(":")) { val w = h.unit.drop(1); return if (w.isBlank()) null else app.knackling.domain.Unit("own", w, w, w, w, w) }
    val id = h.unit.ifEmpty { if (h.own) "" else app.knackling.domain.Kinds.of(h.species)?.unit ?: "" }
    return if (id == "-") null else Catalog.units[id]
}

/** «Which hobby?» – then time it or write down one that's done. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StartSheet(b: Book, nav: Nav, onDone: () -> Unit) {
    val c = LocalContext.current.container
    var sel by rememberSaveable { mutableLongStateOf(b.active.singleOrNull()?.id ?: b.onStage.firstOrNull()?.id ?: 0L) }
    Sheet(onDone) {
        Text(tr("Чем займёмся?", "What shall we do?"), style = T.h2)
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), maxItemsInEachRow = 4) {
            b.active.forEach { h ->
                val on = h.id == sel
                Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(if (on) C.accSoft else C.card).border(if (on) 2.dp else 1.dp, if (on) C.acc else C.line, RoundedCornerShape(12.dp))
                    .tap { sel = h.id }.padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    val st = b.stage(h)
                    if (st < 0) Pic(eggOf(h.species), 46.dp) else Creature(h.species, frameOf(st), 50.dp, shiny = h.shiny)
                    Text(h.label, style = T.tiny.copy(color = C.ink), maxLines = 1, modifier = Modifier.padding(horizontal = 4.dp))
                }
            }
            repeat((4 - b.active.size % 4) % 4) { Spacer(Modifier.weight(1f)) }
        }
        if (b.active.isEmpty()) Text(tr("Сначала добавь хобби.", "Add a hobby first."), style = T.body)
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Ghost(tr("Записать прошедшее", "Log a past one"), Modifier.weight(1f), small = true) { if (sel != 0L) { onDone(); nav.push(Screen.Log(sel)) } }
            Btn(tr("Засечь время", "Start timer"), Modifier.weight(1f)) { if (sel != 0L) { onDone(); c.write { startLive(sel) }; nav.push(Screen.Live) } }
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** The running clock: the apprentice at work, the time, pause, done. */
@Composable
fun LivePage(b: Book, nav: Nav) {
    val c = LocalContext.current.container
    val l = b.s.live
    val h = l?.let { b.byId[it.hobbyId] }
    if (l == null || h == null) { LaunchedEffect(Unit) { nav.pop() }; return }
    var now by remember { mutableLongStateOf(l.ms()) }
    LaunchedEffect(l) { while (true) { now = l.ms(); delay(500) } }
    var quit by remember { mutableStateOf(false) }
    val st = b.stage(h)
    Page(tr("Занятие", "Session"), { nav.pop() }, scroll = false) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) { Pic(toolOf(h.species), 26.dp); Text(" " + h.label, style = T.h3) }
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(Modifier.size(260.dp).clip(CircleShape).background(C.card2), contentAlignment = Alignment.Center) {
                    if (st < 0) Pic(eggOf(h.species), 150.dp)
                    else Creature(h.species, if (l.paused) frameOf(st, Mood.CALM) else frameOf(st, Mood.WORKING), 230.dp, shiny = h.shiny)
                }
            }
            Text(clock(now), style = T.big.copy(fontSize = 56.sp, color = if (l.paused) C.mute else C.ink))
            val mins = (now / 60000).toInt()
            val left = b.toNext(h) - mins - 10
            Text(when {
                st < 0 -> tr("Яйцо тёплое: после занятия ${h.name} вылупится", "The egg is warm: ${h.name} hatches after this session")
                st >= Grow.TOP -> tr("${h.name} работает рядом", "${h.name} works beside you")
                left <= 0 -> tr("${h.name} получит новое звание, когда закончишь!", "${h.name} gets a new rank when you finish!")
                else -> tr("До звания «${Grow.name(st + 1)}» ещё ~${left} мин", "~$left min to «${Grow.name(st + 1)}»")
            }, style = T.small, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
            if (l.idea) Text(tr("Затея: ", "Challenge: ") + b.idea(h).text, style = T.handS, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 20.dp))
            Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Ghost(tr("Отменить", "Cancel"), Modifier.weight(.8f), small = true) { quit = true }
                Ghost(if (l.paused) tr("Дальше", "Resume") else tr("Пауза", "Pause"), Modifier.weight(1f)) { c.write { pauseLive(!l.paused) } }
                Btn(tr("Готово", "Done"), Modifier.weight(1f)) {
                    val m = ((l.ms() + 30_000) / 60000).toInt().coerceAtLeast(1)
                    val start = System.currentTimeMillis() - l.ms()
                    val idea = l.idea
                    c.write { stopLive() }
                    nav.replace(Screen.Log(h.id, m, start, idea = idea))
                }
            }
        }
    }
    if (quit) Popup({ quit = false }) {
        Text(tr("Отменить занятие?", "Cancel the session?"), style = T.h2)
        Text(tr("Время не запишется.", "The time won't be saved."), style = T.body, modifier = Modifier.padding(vertical = 8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Ghost(tr("Нет", "No"), Modifier.weight(1f)) { quit = false }
            Btn(tr("Отменить", "Cancel it"), Modifier.weight(1f), color = C.bad) { quit = false; c.write { stopLive() }; nav.pop() }
        }
    }
}

/** The session form: how long, how much, how it went, a photo, a note, a project. Also edits an old session. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LogPage(scr: Screen.Log, b: Book, nav: Nav) {
    val ctx = LocalContext.current
    val c = ctx.container
    val old = b.sessions.firstOrNull { it.id == scr.sessionId }
    val h = b.byId[old?.hobbyId ?: scr.hobbyId] ?: run { LaunchedEffect(Unit) { nav.pop() }; return }
    var minutes by rememberSaveable { mutableIntStateOf(old?.minutes ?: scr.minutes) }
    var start by rememberSaveable { mutableLongStateOf(old?.start ?: if (scr.start > 0) scr.start else System.currentTimeMillis() - scr.minutes * 60_000L) }
    var amount by rememberSaveable { mutableStateOf(old?.amount?.takeIf { it > 0 }?.toString() ?: "") }
    var flow by rememberSaveable { mutableIntStateOf(old?.flow ?: 0) }
    var note by rememberSaveable { mutableStateOf(old?.note ?: "") }
    var photo by rememberSaveable { mutableStateOf(old?.photo ?: "") }
    var project by rememberSaveable { mutableLongStateOf(old?.projectId ?: b.projectsOf(h).firstOrNull { it.done == 0L }?.id ?: 0L) }
    val proj = b.projects.firstOrNull { it.id == project }
    var progress by rememberSaveable(project) { mutableIntStateOf(proj?.progress ?: 0) }
    val ideaIdx = b.ideaIndex(h)
    var idea by rememberSaveable { mutableStateOf(old?.let { it.idea >= 0 } ?: scr.idea) }
    var del by remember { mutableStateOf(false) }
    val unit = unitOf(h)

    val shot = remember { Photos.camera(ctx) }
    val cam = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok && shot != null) Photos.take(ctx, shot.second)?.let { photo = it } }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? -> uri?.let { u -> Photos.take(ctx, u)?.let { photo = it } } }

    Page(if (old != null) tr("Занятие", "Session") else tr("Записать занятие", "Log a session"), { nav.pop() }, action = {
        if (old != null) Box(Modifier.size(44.dp).clip(CircleShape).tap { del = true }, contentAlignment = Alignment.Center) { Icon(Icons.TRASH, C.mute, 22.dp) }
    }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val st = b.stage(h)
            if (st < 0) Pic(eggOf(h.species), 56.dp) else Creature(h.species, frameOf(st, cheer = true), 64.dp, shiny = h.shiny)
            Column(Modifier.padding(start = 8.dp)) { Text(h.label, style = T.h3); Text(h.name, style = T.small) }
        }
        // how long
        Label(tr("Сколько длилось", "How long"))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().card().padding(10.dp)) {
            Step("−5") { minutes = (minutes - 5).coerceAtLeast(1) }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${minutes / 60}:${(minutes % 60).toString().padStart(2, '0')}", style = T.big.copy(fontSize = 38.sp))
                Text(tr("часы : минуты", "hours : minutes"), style = T.tiny)
            }
            Step("+5") { minutes = (minutes + 5).coerceAtMost(24 * 60) }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(10, 15, 20, 30, 45, 60, 90, 120).forEach { m -> Chip(if (m < 60) tr("$m мин", "$m min") else tr("${m / 60}${if (m % 60 > 0) ",5" else ""} ч", "${m / 60}${if (m % 60 > 0) ".5" else ""} h"), minutes == m) { minutes = m } }
        }
        // when
        Label(tr("Когда", "When"))
        val zdt = Instant.ofEpochMilli(start).atZone(zone)
        val day = zdt.toLocalDate().toEpochDay()
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(0L, 1L, 2L, 3L).forEach { back ->
                val d = b.today - back
                Chip(if (back < 2) relDay(d).replaceFirstChar { it.uppercase() } else fmtDay(d), day == d) { start = shift(start, d) }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Начало в ", "Started at ") + "%d:%02d".format(zdt.hour, zdt.minute), style = T.bodyB, modifier = Modifier.weight(1f))
            Step("−1 ч", small = true) { start -= 3_600_000 }
            Spacer(Modifier.width(6.dp))
            Step("+1 ч", small = true) { if (start + 3_600_000 < System.currentTimeMillis()) start += 3_600_000 }
        }
        // how much
        if (unit != null) {
            Label(tr("Сколько сделано", "How much") + " (${unit.many})")
            Field(amount, { amount = it.filter(Char::isDigit).take(6) }, tr("например, 12", "e.g. 12"), number = true)
        }
        // how it went
        Label(tr("Как шло", "How it went"))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1 to tr("Тяжело", "Heavy going"), 2 to tr("Ровно", "Fine"), 3 to tr("В потоке", "In the flow")).forEach { (v, label) ->
                val on = flow == v
                Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(if (on) C.accSoft else C.card).border(if (on) 2.dp else 1.dp, if (on) C.acc else C.line, RoundedCornerShape(12.dp))
                    .tap { flow = if (on) 0 else v }.padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    val st = b.stage(h).coerceAtLeast(2)
                    Creature(h.species, when (v) { 1 -> if (st >= 2) 1 else 0; 2 -> 2; else -> 4 }, 52.dp, shiny = h.shiny)
                    Text(label, style = T.smallB)
                }
            }
        }
        // the challenge
        Row(Modifier.fillMaxWidth().padding(top = 14.dp).card(color = if (idea) C.goldSoft else null).tap { idea = !idea }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(24.dp).clip(RoundedCornerShape(6.dp)).background(if (idea) C.acc else C.track), contentAlignment = Alignment.Center) { if (idea) Icon(Icons.CHECK, Color.White, 16.dp) }
            Column(Modifier.padding(start = 10.dp)) {
                Text(tr("Затея дня выполнена", "Did today's challenge"), style = T.smallB)
                Text(b.ideas(h)[if (old != null && old.idea >= 0) old.idea.coerceAtMost(b.ideas(h).size - 1) else ideaIdx].text, style = T.handS)
            }
        }
        // a project
        val open = b.projectsOf(h).filter { it.done == 0L || it.id == project }
        Label(tr("Проект", "Project"))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip(tr("Без проекта", "No project"), project == 0L) { project = 0L }
            open.forEach { p -> Chip(p.title, project == p.id) { project = p.id } }
            Chip("+ " + tr("новый", "new"), false) { nav.push(Screen.ProjectEdit(hobbyId = h.id)) }
        }
        if (proj != null) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                Text(tr("Готово на ", "Done: ") + "$progress%", style = T.smallB, modifier = Modifier.width(110.dp))
                Slider(progress.toFloat(), { progress = (it / 5).toInt() * 5 }, valueRange = 0f..100f, modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(thumbColor = C.acc, activeTrackColor = C.acc, inactiveTrackColor = C.track))
            }
        }
        // a photo
        Label(tr("Фото работы", "A photo of the work"))
        if (photo.isNotEmpty()) {
            val img by produceState<android.graphics.Bitmap?>(null, photo) { value = Photos.load(ctx, photo, 900) }
            Box(Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(12.dp)).background(C.card2)) {
                img?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                Box(Modifier.align(Alignment.TopEnd).padding(8.dp).size(36.dp).clip(CircleShape).background(Color(0x99000000)).tap { if (old?.photo != photo) Photos.delete(ctx, photo); photo = "" },
                    contentAlignment = Alignment.Center) { Icon(Icons.CLOSE, Color.White, 18.dp) }
            }
        } else Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Ghost(tr("Камера", "Camera"), Modifier.weight(1f), small = true) { shot?.let { runCatching { cam.launch(it.second) } } }
            Ghost(tr("Из галереи", "From gallery"), Modifier.weight(1f), small = true) { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
        }
        Text(tr("+15 опыта за фото", "+15 experience for a photo"), style = T.tiny, modifier = Modifier.padding(top = 4.dp))
        // a note
        Label(tr("Заметка", "A note"))
        Field(note, { note = it }, tr("Что получилось, что нет, что дальше", "What worked, what didn't, what's next"), single = false)
        Spacer(Modifier.height(18.dp))
        Btn(tr("Сохранить", "Save"), Modifier.fillMaxWidth()) {
            val s = Session(id = old?.id ?: 0, hobbyId = h.id, start = start, day = dayOf(start), minutes = minutes, amount = amount.toIntOrNull() ?: 0, flow = flow,
                note = note.trim(), photo = photo, projectId = project, idea = if (idea) (old?.idea?.takeIf { it >= 0 } ?: ideaIdx) else -1)
            val gain = Grow.xp(s)
            c.write {
                saveSession(s)
                if (proj != null && proj.progress != progress) saveProject(proj.copy(progress = progress))
                if (old?.photo?.isNotEmpty() == true && old.photo != photo) Photos.delete(app, old.photo)
                if (old == null) say(tr("+$gain опыта · ${h.name} доволен", "+$gain experience · ${h.name} is pleased"))
            }
            nav.pop()
        }
    }
    if (del && old != null) Popup({ del = false }) {
        Text(tr("Удалить занятие?", "Delete the session?"), style = T.h2)
        Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Ghost(tr("Нет", "No"), Modifier.weight(1f)) { del = false }
            Btn(tr("Удалить", "Delete"), Modifier.weight(1f), color = C.bad) { del = false; c.write { deleteSession(old) }; nav.pop() }
        }
    }
}

/** Moves a moment to another day, keeping its time of day. */
private fun shift(ms: Long, day: Long): Long {
    val t = Instant.ofEpochMilli(ms).atZone(zone).toLocalTime()
    val z = ZonedDateTime.of(LocalDate.ofEpochDay(day), t, zone).toInstant().toEpochMilli()
    return minOf(z, System.currentTimeMillis())
}

@Composable
private fun Label(s: String) = Text(s.uppercase(), style = T.sec, modifier = Modifier.padding(top = 18.dp, bottom = 6.dp))

@Composable
private fun Step(s: String, small: Boolean = false, onClick: () -> Unit) {
    Box(Modifier.size(if (small) 40.dp else 50.dp).clip(CircleShape).background(C.card2).border(1.dp, C.line, CircleShape).tap(onClick), contentAlignment = Alignment.Center) {
        Text(s, style = if (small) T.tiny.copy(color = C.ink) else T.bodyB)
    }
}

