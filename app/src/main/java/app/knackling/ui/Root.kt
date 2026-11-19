package app.knackling.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.knackling.BuildConfig
import app.knackling.container
import app.knackling.data.Hobby
import app.knackling.data.Project
import app.knackling.data.Session
import app.knackling.data.Settings
import app.knackling.domain.Book
import app.knackling.domain.tr
import app.knackling.ui.art.Ico
import app.knackling.ui.theme.C
import app.knackling.ui.theme.T
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine

/** Where a demo/screenshot launch opens (set from the intent in demo builds only). */
object Start { var tab = 0; var open: String? = null }

/** A page over the tabs. */
sealed class Screen {
    data class HobbyInfo(val id: Long, val tab: Int = 0) : Screen()
    data object AddHobby : Screen()
    data class EditHobby(val id: Long) : Screen()
    data object Live : Screen()
    /** The form after a session (or for one from the past, or to edit one). */
    data class Log(val hobbyId: Long, val minutes: Int = 30, val start: Long = 0, val sessionId: Long = 0, val idea: Boolean = false) : Screen()
    data class ProjectInfo(val id: Long) : Screen()
    data class ProjectEdit(val id: Long = 0, val hobbyId: Long = 0) : Screen()
    data class Species(val id: String) : Screen()
    data object Shop : Screen()
    data object Settings : Screen()
}

class Nav {
    var tab by mutableIntStateOf(0)
    val stack = mutableStateListOf<Screen>()
    /** The postcard shelf, opened on this card key. */
    var share by mutableStateOf<String?>(null)
    /** The «start a session» sheet. */
    var starting by mutableStateOf(false)
    fun push(s: Screen) { stack.add(s) }
    fun pop() { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) }
    fun replace(s: Screen) { pop(); push(s) }
}

private data class All(val h: List<Hobby>, val s: List<Session>, val p: List<Project>)

@Composable
fun Root(s: Settings) {
    val c = LocalContext.current.container
    // one flow for the three tables, so the screens never see sessions without their hobby
    val all by remember { combine(c.dao.hobbies(), c.dao.sessions(), c.dao.projects()) { h, s, p -> All(h, s, p) } }.collectAsState(initial = null)
    val q = all ?: return
    // the minute hand: moods and the live clock follow the time even with nothing written
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(60_000); tick++ } }
    val b = remember(s, q, tick) { Book(q.h, q.s, q.p, s, c.clock()) }
    val nav = remember { Nav().apply { tab = Start.tab } }
    var note by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { c.news.collect { note = it; delay(2600); note = null } }
    LaunchedEffect(q.h.isNotEmpty()) {
        if (q.h.isEmpty()) return@LaunchedEffect
        when (val o = Start.open) {
            null -> {}
            "shop" -> nav.push(Screen.Shop); "settings" -> nav.push(Screen.Settings); "add" -> nav.push(Screen.AddHobby)
            "live" -> nav.push(Screen.Live); "share" -> nav.share = "month"; "start" -> nav.starting = true
            else -> when {
                o.startsWith("hobby:") -> nav.push(Screen.HobbyInfo(o.drop(6).toLongOrNull() ?: q.h.first().id))
                o.startsWith("species:") -> nav.push(Screen.Species(o.drop(8)))
                o.startsWith("log:") -> nav.push(Screen.Log(o.drop(4).toLongOrNull() ?: q.h.first().id))
                o.startsWith("project:") -> nav.push(Screen.ProjectInfo(o.drop(8).toLongOrNull() ?: q.p.first().id))
            }
        }
        Start.open = null
    }

    Box(Modifier.fillMaxSize().background(C.bg)) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).statusBarsPadding()) {
                when (nav.tab) {
                    0 -> app.knackling.ui.home.HomeScreen(b, nav)
                    1 -> app.knackling.ui.journal.JournalScreen(b, nav)
                    2 -> app.knackling.ui.stats.StatsScreen(b, nav)
                    else -> app.knackling.ui.album.AlbumScreen(b, nav)
                }
            }
            Dock(nav.tab, b.s.live != null, { nav.tab = it }) { if (b.s.live != null) nav.push(Screen.Live) else nav.starting = true }
        }
        for (scr in nav.stack) Box(Modifier.fillMaxSize()) {
            when (scr) {
                is Screen.HobbyInfo -> app.knackling.ui.hobby.HobbyPage(scr.id, scr.tab, b, nav)
                Screen.AddHobby -> app.knackling.ui.hobby.AddHobbyPage(b, nav)
                is Screen.EditHobby -> app.knackling.ui.hobby.EditHobbyPage(scr.id, b, nav)
                Screen.Live -> app.knackling.ui.session.LivePage(b, nav)
                is Screen.Log -> app.knackling.ui.session.LogPage(scr, b, nav)
                is Screen.ProjectInfo -> app.knackling.ui.journal.ProjectPage(scr.id, b, nav)
                is Screen.ProjectEdit -> app.knackling.ui.journal.ProjectEditPage(scr, b, nav)
                is Screen.Species -> app.knackling.ui.album.SpeciesPage(scr.id, b, nav)
                Screen.Shop -> app.knackling.ui.more.ShopPage(b, nav)
                Screen.Settings -> app.knackling.ui.more.SettingsPage(b, nav)
            }
        }
        if (nav.starting) app.knackling.ui.session.StartSheet(b, nav) { nav.starting = false }
        nav.share?.let { k -> app.knackling.ui.share.ShareSheet(k, b) { nav.share = null } }
        val shots = BuildConfig.SHOTS
        val news = if (shots || nav.starting || nav.share != null) null else app.knackling.ui.home.nextNews(b)
        if (news != null) app.knackling.ui.home.NewsSheet(news, b, nav)
        else if (!shots && !nav.starting && nav.share == null) introFor(nav.stack.lastOrNull(), nav.tab)?.takeIf { it.id !in s.seen }?.let { IntroSheet(it) }
        AnimatedVisibility(note != null, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 12.dp, start = 18.dp, end = 18.dp), enter = fadeIn(), exit = fadeOut()) {
            Text(note ?: "", style = T.bodyB.copy(color = C.bg), modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.ink).padding(horizontal = 16.dp, vertical = 11.dp))
        }
    }
}

/**
 * The dock: a tailor's measuring tape along the bottom with the four places as pixel things on it;
 * in the middle a big pincushion button starts a session (or shows the running one).
 */
@Composable
private fun Dock(sel: Int, live: Boolean, onSel: (Int) -> Unit, onStart: () -> Unit) {
    val tabs = listOf(tr("Мастерская", "Workshop") to "house", tr("Журнал", "Journal") to "journal", tr("Ведомость", "Ledger") to "chart", tr("Альбом", "Album") to "paw")
    val tape = Color(0xFFEBC765); val tape2 = Color(0xFFD9AE45); val ink = Color(0xFF3B2A12)
    val navPx = WindowInsets.navigationBars.getBottom(LocalDensity.current)
    Box(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(top = 16.dp).shadow(6.dp).drawBehind {
            drawRect(tape)
            drawRect(tape2, Offset(0f, size.height - navPx), Size(size.width, navPx.toFloat()))
            // the tape's marks: a tick every few pixels, longer ones every fifth
            val step = 7.dp.toPx(); var x = 0f; var i = 0
            while (x < size.width) {
                val len = if (i % 10 == 0) 10.dp.toPx() else if (i % 5 == 0) 7.dp.toPx() else 4.dp.toPx()
                drawLine(ink.copy(alpha = .55f), Offset(x, 0f), Offset(x, len), 1.2.dp.toPx()); x += step; i++
            }
            drawLine(ink.copy(alpha = .25f), Offset(0f, size.height - navPx - 1.dp.toPx()), Offset(size.width, size.height - navPx - 1.dp.toPx()), 1.dp.toPx())
        }.navigationBarsPadding().padding(top = 12.dp, bottom = 6.dp), verticalAlignment = Alignment.Bottom) {
            tabs.forEachIndexed { i, (label, ic) ->
                if (i == 2) Spacer(Modifier.weight(1.1f))
                val on = i == sel
                Column(Modifier.weight(1f).clickable(remember { MutableInteractionSource() }, null) { onSel(i) }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Ico(ic, if (on) 36.dp else 30.dp, dim = !on)
                    Text(label, style = T.tiny.copy(color = ink.copy(alpha = if (on) 1f else .62f), fontSize = 10.sp),
                        modifier = Modifier.padding(top = 1.dp).then(if (on) Modifier.clip(RoundedCornerShape(4.dp)).background(Color(0x33FFFFFF)).padding(horizontal = 5.dp) else Modifier.padding(horizontal = 5.dp)))
                }
            }
        }
        // the pincushion: red, round, with pins; a running session shows the stopwatch ticking
        Column(Modifier.align(Alignment.TopCenter).offset(y = (-6).dp).clickable(remember { MutableInteractionSource() }, null, onClick = onStart), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(66.dp).shadow(8.dp, CircleShape).clip(CircleShape).background(if (live) C.ok else C.acc).border(3.dp, Color(0xFFFFE9C9), CircleShape).drawBehind {
                // pin heads around the rim
                val r = size.minDimension / 2 - 6.dp.toPx()
                listOf(-60f, -20f, 25f, 70f, 200f, 240f, 290f).forEachIndexed { k, a ->
                    val rad = Math.toRadians(a.toDouble())
                    drawCircle(listOf(Color(0xFFFFD45E), Color(0xFF8FD3F4), Color(0xFFFFFFFF), Color(0xFFB8E07A))[k % 4], 3.dp.toPx(),
                        Offset(center.x + r * Math.cos(rad).toFloat(), center.y + r * Math.sin(rad).toFloat()))
                }
            }, contentAlignment = Alignment.Center) { Ico("stopwatch", 38.dp) }
            Text(if (live) tr("Идёт", "Running") else tr("Занятие", "Session"), style = T.tiny.copy(color = ink, fontSize = 10.sp))
        }
    }
}
