package app.knackling.ui.journal

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.knackling.container
import app.knackling.data.Photos
import app.knackling.data.Project
import app.knackling.data.Session
import app.knackling.domain.Book
import app.knackling.domain.Grow
import app.knackling.domain.cap
import app.knackling.domain.count
import app.knackling.domain.fmt2
import app.knackling.domain.fmtDayYear
import app.knackling.domain.relDay
import app.knackling.domain.tr
import app.knackling.domain.weekday
import app.knackling.ui.Nav
import app.knackling.ui.Screen
import app.knackling.ui.art.Creature
import app.knackling.ui.art.Mini
import app.knackling.ui.art.Pic
import app.knackling.ui.art.frameOf
import app.knackling.ui.art.toolOf
import app.knackling.ui.components.Btn
import app.knackling.ui.components.Chip
import app.knackling.ui.components.ChipRow
import app.knackling.ui.components.Field
import app.knackling.ui.components.Ghost
import app.knackling.ui.components.Icon
import app.knackling.ui.components.Icons
import app.knackling.ui.components.Page
import app.knackling.ui.components.Popup
import app.knackling.ui.components.Segments
import app.knackling.ui.components.card
import app.knackling.ui.components.tap
import app.knackling.ui.hobby.colorOf
import app.knackling.ui.hobby.hours
import app.knackling.ui.session.unitOf
import app.knackling.ui.theme.C
import app.knackling.ui.theme.T

/** A photo from filesDir/photos, decoded off the main thread, cropped to fill. */
@Composable
fun Photo(name: String, modifier: Modifier, max: Int = 500) {
    val ctx = LocalContext.current
    val img by produceState<Bitmap?>(null, name) { value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Photos.load(ctx, name, max) } }
    Box(modifier.background(C.card2)) { img?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) } }
}

/** The journal: sessions by day, the showcase wall of photos, the projects. */
@Composable
fun JournalScreen(b: Book, nav: Nav) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var filter by rememberSaveable { mutableLongStateOf(0L) }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Pic("ic_journal", 34.dp)
            Text(tr("Журнал", "Journal"), style = T.title, modifier = Modifier.padding(start = 8.dp).weight(1f))
        }
        Segments(listOf(tr("Записи", "Sessions"), tr("Витрина", "Showcase"), tr("Проекты", "Projects")), tab) { tab = it }
        if (b.active.size > 1 && tab != 2) {
            Spacer(Modifier.height(8.dp))
            val ids = listOf(0L) + b.hobbies.filter { b.of[it.id] != null }.map { it.id }
            ChipRow(ids.map { if (it == 0L) tr("Все", "All") else b.byId[it]?.label ?: "" }, ids.indexOf(filter).coerceAtLeast(0)) { filter = ids[it] }
        }
        Spacer(Modifier.height(8.dp))
        val sessions = if (filter == 0L) b.sessions else b.of[filter].orEmpty()
        when (tab) {
            0 -> Sessions(sessions, b, nav)
            1 -> Showcase(sessions, b, nav)
            else -> Projects(b, nav)
        }
    }
}

@Composable
private fun Sessions(list: List<Session>, b: Book, nav: Nav) {
    if (list.isEmpty()) { Empty(tr("Здесь появятся занятия. Нажми на подушечку внизу – и начнём.", "Sessions will show up here. Tap the pincushion below to begin.")); return }
    val days = remember(list) { list.groupBy { it.day }.toList() }
    LazyColumn(Modifier.fillMaxSize()) {
        days.forEach { (day, l) ->
            item(key = "d$day") {
                Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 4.dp), verticalAlignment = Alignment.Bottom) {
                    Text(relDay(day).replaceFirstChar { it.uppercase() }, style = T.hand, modifier = Modifier.weight(1f))
                    Text(weekday(day) + " · " + hours(l.sumOf { it.minutes }), style = T.tiny)
                }
            }
            items(l, key = { it.id }) { s -> SessionRow(s, b) { nav.push(Screen.Log(s.hobbyId, sessionId = s.id)) } }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

/** One session in a list: the apprentice, the hobby, the time ([day]: the date too, where no day heading says it), how it went, its project, the note, the photo. */
@Composable
fun SessionRow(s: Session, b: Book, showHobby: Boolean = true, day: Boolean = false, showProject: Boolean = true, onClick: () -> Unit) {
    val h = b.byId[s.hobbyId] ?: return
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp).card().tap(onClick).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (showHobby) Mini(h.species, 44.dp, frame = frameOf(b.stage(h).coerceAtLeast(0)))
        else Box(Modifier.size(10.dp).clip(CircleShape).background(colorOf(h)))
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            val head = listOfNotNull(if (showHobby) h.label else null, hours(s.minutes), unitOf(h)?.takeIf { s.amount > 0 }?.of(s.amount)).joinToString(" · ")
            Text(head, style = T.bodyB, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val time = "%d:%02d".format(b.hourOf(s.start), java.time.Instant.ofEpochMilli(s.start).atZone(app.knackling.domain.zone).minute)
            val sub = listOfNotNull(
                if (day) relDay(s.day).cap() + ", " + time else time,
                when (s.flow) { 1 -> tr("тяжело шло", "heavy going"); 2 -> tr("ровно", "fine"); 3 -> tr("в потоке", "in the flow"); else -> null },
                if (s.idea >= 0) tr("затея", "challenge") else null,
                if (showProject) b.projects.firstOrNull { it.id == s.projectId }?.title else null)
            Text(sub.joinToString(" · "), style = T.tiny, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (s.note.isNotBlank()) Text(s.note, style = T.handS, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (s.photo.isNotEmpty()) Photo(s.photo, Modifier.padding(start = 8.dp).size(52.dp).clip(RoundedCornerShape(8.dp)), 200)
    }
}

/** The showcase: photos pinned on a cork wall, a little crooked, each with its hobby's tool. */
@Composable
private fun Showcase(list: List<Session>, b: Book, nav: Nav) {
    val pics = remember(list) { list.filter { it.photo.isNotEmpty() } }
    var open by remember { mutableStateOf<Session?>(null) }
    if (pics.isEmpty()) { Empty(tr("Сфотографируй работу после занятия – и она повиснет здесь.", "Photograph your work after a session and it will hang here.")); return }
    LazyVerticalGrid(GridCells.Fixed(3), Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)).background(Color(0xFFC79A64)).padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(pics, key = { it.id }) { s ->
            val tilt = ((s.id % 7) - 3) * .9f
            Column(Modifier.rotate(tilt).background(Color(0xFFFFFBF2)).padding(4.dp).tap { open = s }, horizontalAlignment = Alignment.CenterHorizontally) {
                Photo(s.photo, Modifier.fillMaxWidth().aspectRatio(1f))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                    b.byId[s.hobbyId]?.let { Pic(toolOf(it), 14.dp) }
                    Text(" " + app.knackling.domain.fmtDayShort(s.day), style = T.tiny.copy(color = Color(0xFF6B5A44)))
                }
            }
        }
    }
    open?.let { s -> Popup({ open = null }) {
        Photo(s.photo, Modifier.fillMaxWidth().aspectRatio(.8f).clip(RoundedCornerShape(10.dp)), 1200)
        val h = b.byId[s.hobbyId]
        Text("${h?.label ?: ""} · ${fmtDayYear(s.day)}", style = T.bodyB, modifier = Modifier.padding(top = 8.dp))
        if (s.note.isNotBlank()) Text(s.note, style = T.handS)
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Ghost(tr("Открытка", "Postcard"), Modifier.weight(1f), small = true) { open = null; nav.share = "work:${s.id}" }
            Btn(tr("Занятие", "Session"), Modifier.weight(1f), small = true) { open = null; nav.push(Screen.Log(s.hobbyId, sessionId = s.id)) }
        }
    } }
}

@Composable
private fun Projects(b: Book, nav: Nav) {
    var choose by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize()) {
        val going = b.projects.filter { it.going }; val plans = b.projects.filter { it.planned && it.done == 0L }; val done = b.projects.filter { it.done > 0 }
        item { Ghost("+ " + tr("Новый проект", "New project"), Modifier.fillMaxWidth(), small = true) { if (b.active.size == 1) nav.push(Screen.ProjectEdit(hobbyId = b.active[0].id)) else choose = true } }
        if (going.isNotEmpty()) item { Text(tr("В РАБОТЕ", "IN THE MAKING"), style = T.sec, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp)) }
        items(going, key = { it.id }) { p -> ProjectRow(p, b) { nav.push(Screen.ProjectInfo(p.id)) } }
        if (plans.isNotEmpty()) item { Text(tr("В ПЛАНАХ", "PLANNED"), style = T.sec, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp)) }
        items(plans, key = { it.id }) { p -> ProjectRow(p, b) { nav.push(Screen.ProjectInfo(p.id)) } }
        if (done.isNotEmpty()) item { Text(tr("ГОТОВО", "FINISHED"), style = T.sec, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp)) }
        items(done, key = { it.id }) { p -> ProjectRow(p, b) { nav.push(Screen.ProjectInfo(p.id)) } }
        if (b.projects.isEmpty()) item { Empty(tr("Проект – то, что ты делаешь: шарф, песня, картина, книга. Отмечай прогресс после занятий. А то, за что возьмёшься потом, можно положить в планы.", "A project is something you're making: a scarf, a song, a painting, a book. Mark progress after sessions. Things for later can wait in the plans.")) }
        item { Spacer(Modifier.height(20.dp)) }
    }
    if (choose) Popup({ choose = false }) {
        Text(tr("Для какого хобби?", "For which hobby?"), style = T.h2)
        b.active.forEach { h -> Row(Modifier.fillMaxWidth().tap { choose = false; nav.push(Screen.ProjectEdit(hobbyId = h.id)) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Pic(toolOf(h), 28.dp); Text(h.label, style = T.bodyB, modifier = Modifier.padding(start = 10.dp)) } }
    }
}

/** A project in a list: the cover, the title, the ribbon of progress. */
@Composable
fun ProjectRow(p: Project, b: Book, onClick: () -> Unit) {
    val h = b.byId[p.hobbyId] ?: return
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).card().tap(onClick).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (p.photo.isNotEmpty()) Photo(p.photo, Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)), 200)
        else Box(Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)).background(colorOf(h).copy(alpha = .18f)), contentAlignment = Alignment.Center) { Pic(toolOf(h), 40.dp) }
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(p.title, style = T.bodyB, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (p.planned && p.done == 0L) Text(h.label + tr(" · в планах с ", " · planned since ") + fmtDayYear(p.started), style = T.tiny)
            else {
                Text(h.label + " · " + hours(b.sessions.filter { it.projectId == p.id }.sumOf { it.minutes }), style = T.tiny)
                Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)).background(C.track)) {
                    Box(Modifier.fillMaxWidth(if (p.done > 0) 1f else (p.pct / 100).toFloat().coerceIn(0f, 1f)).height(6.dp).clip(RoundedCornerShape(50)).background(if (p.done > 0) C.ok else colorOf(h)))
                }
            }
        }
        Text(when { p.done > 0 -> "✓"; p.planned -> ""; p.staged -> "${p.stepsDone}/${p.steps}"; else -> pctText(p.progress) },
            style = T.numS.copy(color = if (p.done > 0) C.ok else C.ink), modifier = Modifier.padding(start = 10.dp))
    }
}

/** A project: the cover, its progress, the time it took, its sessions and photos; «finished» celebrates. */
@Composable
fun ProjectPage(id: Long, b: Book, nav: Nav) {
    val c = LocalContext.current.container
    val p = b.projects.firstOrNull { it.id == id } ?: run { LaunchedEffect(Unit) { nav.pop() }; return }
    val h = b.byId[p.hobbyId] ?: return
    val ss = b.sessions.filter { it.projectId == p.id }
    var finish by remember { mutableStateOf(false) }
    var reopen by remember { mutableStateOf(false) }
    Page(p.title, { nav.pop() }, action = {
        Box(Modifier.size(44.dp).clip(CircleShape).tap { nav.push(Screen.ProjectEdit(p.id, p.hobbyId)) }, contentAlignment = Alignment.Center) { Icon(Icons.EDIT, C.ink, 21.dp) }
    }) {
        val cover = p.photo.ifEmpty { ss.firstOrNull { it.photo.isNotEmpty() }?.photo ?: "" }
        if (cover.isNotEmpty()) Photo(cover, Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(12.dp)), 1200)
        else Box(Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(12.dp)).background(colorOf(h).copy(alpha = .15f)), contentAlignment = Alignment.Center) { Pic(toolOf(h), 110.dp) }
        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Pic(toolOf(h), 22.dp)
            Text(" " + h.label + (if (p.planned && p.done == 0L) tr(" · в планах с ", " · planned since ") else tr(" · начат ", " · started ")) + fmtDayYear(p.started), style = T.small)
        }
        if (p.done > 0) Text(tr("Закончен ${fmtDayYear(p.done)}", "Finished ${fmtDayYear(p.done)}"), style = T.bodyB.copy(color = C.ok))
        else if (!p.planned) ProgressEditor(p, Modifier.padding(top = 6.dp)) { np -> c.write { saveProject(np) } }
        if (!p.planned || p.done > 0) Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(hours(ss.sumOf { it.minutes }) to tr("вложено", "put in"), count(ss.size, "занятие", "занятия", "занятий", "session", "sessions") to tr("на проект", "on it"),
                ((if (p.done > 0) p.done else b.today) - p.started + 1).let { count(it.toInt(), "день", "дня", "дней", "day", "days") } to tr("от начала", "from the start")).forEach { (v, l) ->
                Column(Modifier.weight(1f).card().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(v, style = T.smallB, maxLines = 1); Text(l, style = T.tiny) }
            }
        }
        if (p.note.isNotBlank()) Text(p.note, style = T.hand, modifier = Modifier.padding(top = 12.dp))
        if (p.planned && p.done == 0L) {
            Btn(tr("Начать сегодня", "Start today"), Modifier.fillMaxWidth().padding(top = 14.dp)) { c.write { saveProject(p.copy(planned = false, started = clock())) } }
        } else if (p.done == 0L) {
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Ghost(tr("Записать занятие", "Log a session"), Modifier.weight(1f), small = true) { nav.push(Screen.Log(h.id)) }
                Btn(tr("Закончено!", "Finished!"), Modifier.weight(1f), color = C.ok) { finish = true }
            }
        } else Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Ghost(tr("Открытка", "Postcard"), Modifier.weight(1f), small = true) { nav.share = "project:${p.id}" }
            Ghost(tr("Вернуть в работу", "Back to work"), Modifier.weight(1f), small = true) { reopen = true }
        }
        if (ss.isNotEmpty()) {
            Text(tr("ЗАНЯТИЯ", "SESSIONS"), style = T.sec, modifier = Modifier.padding(top = 18.dp, bottom = 6.dp))
            ss.forEach { s -> SessionRow(s, b, showHobby = false, day = true, showProject = false) { nav.push(Screen.Log(h.id, sessionId = s.id)) } }
        }
    }
    if (finish) Popup({ finish = false }) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Creature(h.species, frameOf(b.stage(h).coerceAtLeast(2), cheer = true), 150.dp, shiny = h.shiny)
            Text(tr("Работа закончена?", "Is the work finished?"), style = T.h2, textAlign = TextAlign.Center)
            Text(tr("«${p.title}» переедет в готовые, а ${h.name} получит +${Grow.PROJECT} опыта и 10 искр. Передумаешь – проект можно вернуть в работу.",
                "«${p.title}» moves to the finished ones, and ${h.name} gets +${Grow.PROJECT} experience and 10 sparks. Changed your mind? It can go back to work."),
                style = T.body, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Ghost(tr("Ещё нет", "Not yet"), Modifier.weight(1f)) { finish = false }
                Btn(tr("Да", "Yes"), Modifier.weight(1f), color = C.ok) {
                    // the progress stays as it was: a finished project shows a tick, and one brought back to work goes on from there
                    finish = false; c.write { saveProject(p.copy(done = clock())) }; nav.share = "project:${p.id}"
                }
            }
        }
    }
    if (reopen) Popup({ reopen = false }) {
        Text(tr("Вернуть в работу?", "Back to work?"), style = T.h2)
        Text(tr("Проект снова будет в работе, а опыт и искры за готовую работу уйдут, пока не закончишь её ещё раз.",
            "The project is in the making again; the experience and sparks for finishing it go until you finish it once more."), style = T.body, modifier = Modifier.padding(vertical = 8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Ghost(tr("Нет", "No"), Modifier.weight(1f)) { reopen = false }
            Btn(tr("Вернуть", "Bring back"), Modifier.weight(1f)) { reopen = false; c.write { saveProject(p.copy(done = 0)) } }
        }
    }
}

/** A new project or its settings: the title, the cover, the note, delete. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProjectEditPage(scr: Screen.ProjectEdit, b: Book, nav: Nav) {
    val ctx = LocalContext.current
    val c = ctx.container
    val old = b.projects.firstOrNull { it.id == scr.id }
    val hid = old?.hobbyId ?: scr.hobbyId
    val h = b.byId[hid] ?: run { LaunchedEffect(Unit) { nav.pop() }; return }
    var title by rememberSaveable { mutableStateOf(old?.title ?: "") }
    var note by rememberSaveable { mutableStateOf(old?.note ?: "") }
    var photo by rememberSaveable { mutableStateOf(old?.photo ?: "") }
    var staged by rememberSaveable { mutableStateOf(old?.staged ?: false) }
    var steps by rememberSaveable { mutableStateOf(old?.steps?.takeIf { it > 0 }?.toString() ?: "") }
    var planned by rememberSaveable { mutableStateOf(old?.planned ?: false) }
    var del by remember { mutableStateOf(false) }
    val shot = remember { Photos.camera(ctx) }
    val cam = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok && shot != null) Photos.take(ctx, shot.second)?.let { photo = it } }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? -> uri?.let { u -> Photos.take(ctx, u)?.let { photo = it } } }
    Page(if (old == null) tr("Новый проект", "New project") else tr("Проект", "Project"), { nav.pop() }) {
        Row(verticalAlignment = Alignment.CenterVertically) { Pic(toolOf(h), 26.dp); Text(" " + h.label, style = T.bodyB) }
        Text(tr("НАЗВАНИЕ", "TITLE"), style = T.sec, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
        Field(title, { title = it.take(50) }, tr("Шарф для мамы, «Yesterday», «Мастер и Маргарита»…", "A scarf for mum, «Yesterday», a novel…"))
        Text(tr("ОБЛОЖКА", "COVER"), style = T.sec, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
        if (photo.isNotEmpty()) Box {
            Photo(photo, Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(12.dp)), 900)
            Box(Modifier.align(Alignment.TopEnd).padding(8.dp).size(36.dp).clip(CircleShape).background(Color(0x99000000)).tap { photo = "" }, contentAlignment = Alignment.Center) { Icon(Icons.CLOSE, Color.White, 18.dp) }
        } else Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Ghost(tr("Камера", "Camera"), Modifier.weight(1f), small = true) { shot?.let { runCatching { cam.launch(it.second) } } }
            Ghost(tr("Из галереи", "From gallery"), Modifier.weight(1f), small = true) { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
        }
        Text(tr("ЗАМЕТКА", "NOTE"), style = T.sec, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
        Field(note, { note = it }, tr("Схема, пряжа, ссылка на урок…", "Pattern, yarn, a link to a lesson…"), single = false)
        Text(tr("КАК СЧИТАТЬ ПРОГРЕСС", "HOW TO COUNT PROGRESS"), style = T.sec, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip(tr("Проценты", "Percent"), !staged) { staged = false }
            Chip(tr("Этапы", "Stages"), staged) { staged = true }
        }
        if (staged) {
            Text(tr("Например, 31 этап для челленджа на месяц: каждый день отмечаешь сделанный.", "E.g. 31 stages for a month's challenge: tick each day done."), style = T.small, modifier = Modifier.padding(top = 6.dp, bottom = 6.dp))
            Field(steps, { steps = it.filter(Char::isDigit).take(3) }, tr("Сколько этапов", "How many stages"), number = true)
        }
        if (old == null || old.planned && old.done == 0L) Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tr("Пока в планах", "Planned for later"), style = T.bodyB)
                Text(tr("Набор, схема или идея на потом. Начнёшь, когда дойдут руки.", "A kit, a pattern or an idea for later. Start it when you get to it."), style = T.small)
            }
            Switch(planned, { planned = it }, colors = SwitchDefaults.colors(checkedTrackColor = C.acc))
        }
        Spacer(Modifier.height(18.dp))
        val n = steps.toIntOrNull()?.takeIf { it > 0 }?.coerceAtMost(Project.MAX_STEPS)
        Btn(tr("Сохранить", "Save"), Modifier.fillMaxWidth(), enabled = title.isNotBlank() && (!staged || n != null)) {
            val base = old ?: Project(hobbyId = hid, title = "", started = c.clock())
            val counted = if (staged) base.withSteps(n ?: 1) else if (base.staged) base.copy(progress = Math.round(base.pct * 100) / 100.0, steps = 0, marks = "") else base
            val p = counted.copy(title = title.trim(), note = note.trim(), photo = photo, planned = planned,
                started = if (old != null && old.planned && !planned) c.clock() else base.started)
            c.write { if (old != null && old.photo.isNotEmpty() && old.photo != photo) Photos.delete(app, old.photo); saveProject(p) }
            nav.pop()
        }
        if (old != null) Row(Modifier.fillMaxWidth().padding(top = 18.dp).tap { del = true }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.TRASH, C.bad, 20.dp); Text(tr("Удалить проект", "Delete the project"), style = T.bodyB.copy(color = C.bad), modifier = Modifier.padding(start = 8.dp))
        }
    }
    if (del && old != null) Popup({ del = false }) {
        Text(tr("Удалить проект?", "Delete the project?"), style = T.h2)
        Text(tr("Занятия останутся, просто без проекта.", "The sessions stay, just without the project."), style = T.body, modifier = Modifier.padding(vertical = 8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Ghost(tr("Нет", "No"), Modifier.weight(1f)) { del = false }
            Btn(tr("Удалить", "Delete"), Modifier.weight(1f), color = C.bad) { del = false; c.write { deleteProject(old) }; nav.pop(); nav.pop() }
        }
    }
}

@Composable
fun Empty(text: String) = Text(text, style = T.body.copy(color = C.mute, textAlign = TextAlign.Center), modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp, horizontal = 20.dp))

/** «12,5%»: a project's percent, fractions kept. */
fun pctText(v: Double) = fmt2(v) + "%"

/**
 * A project's progress to move on: the percent (a slider by whole ones; a tap on the number types it exactly, fractions too)
 * or its stages as numbered cells to tick.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProgressEditor(p: Project, modifier: Modifier = Modifier, onChange: (Project) -> Unit) {
    if (p.staged) Column(modifier.fillMaxWidth()) {
        Text(tr("Этапов сделано: ${p.stepsDone} из ${p.steps}", "Stages done: ${p.stepsDone} of ${p.steps}"), style = T.smallB, modifier = Modifier.padding(bottom = 6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            (0 until p.steps).forEach { i ->
                val on = p.marked(i)
                Box(Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(if (on) C.acc else C.card2).border(1.dp, if (on) C.acc else C.line, RoundedCornerShape(8.dp))
                    .tap { onChange(p.toggle(i)) }, contentAlignment = Alignment.Center) {
                    if (on) Icon(Icons.CHECK, Color.White, 18.dp) else Text("${i + 1}", style = T.tiny)
                }
            }
        }
    } else Column(modifier.fillMaxWidth()) {
        var typing by remember { mutableStateOf(false) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(pctText(p.progress), style = T.num, maxLines = 1, modifier = Modifier.width(104.dp).clip(RoundedCornerShape(8.dp)).tap { typing = true }.padding(vertical = 4.dp))
            Slider(p.progress.toFloat(), { v -> onChange(p.copy(progress = Math.round(v).toDouble())) }, valueRange = 0f..100f, modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(thumbColor = C.acc, activeTrackColor = C.acc, inactiveTrackColor = C.track))
        }
        Text(tr("Нажми на число, чтобы ввести точно", "Tap the number to type it exactly"), style = T.tiny)
        if (typing) {
            var text by remember { mutableStateOf(fmt2(p.progress)) }
            val v = text.replace(',', '.').toDoubleOrNull()?.takeIf { it in 0.0..100.0 }
            Popup({ typing = false }) {
                Text(tr("Сколько готово, %", "How much is done, %"), style = T.h2)
                Field(text, { text = it.filter { ch -> ch.isDigit() || ch == ',' || ch == '.' }.take(6) }, "12,5", Modifier.padding(vertical = 12.dp), number = true, style = T.num, center = true, autoFocus = true)
                Btn(tr("Готово", "Done"), Modifier.fillMaxWidth(), enabled = v != null) {
                    typing = false; v?.let { onChange(p.copy(progress = Math.round(it * 100) / 100.0)) }
                }
            }
        }
    }
}
