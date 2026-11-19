package app.knackling.ui.album

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.knackling.domain.Badge
import app.knackling.domain.Badges
import app.knackling.domain.Book
import app.knackling.domain.Catalog
import app.knackling.domain.Grow
import app.knackling.domain.Kinds
import app.knackling.domain.Rand
import app.knackling.domain.tr
import app.knackling.ui.Nav
import app.knackling.ui.Screen
import app.knackling.ui.art.Creature
import app.knackling.ui.art.Mini
import app.knackling.ui.art.Pic
import app.knackling.ui.art.eggOf
import app.knackling.ui.art.toolOf
import app.knackling.ui.components.Btn
import app.knackling.ui.components.Page
import app.knackling.ui.components.Popup
import app.knackling.ui.components.Segments
import app.knackling.ui.components.card
import app.knackling.ui.components.tap
import app.knackling.ui.hobby.AdoptSheet
import app.knackling.ui.theme.C
import app.knackling.ui.theme.T

/** The album: every apprentice there is (met or a silhouette), and the apron with the patches earned. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AlbumScreen(b: Book, nav: Nav) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Pic("ic_paw", 34.dp)
            Text(tr("Альбом", "Album"), style = T.title, modifier = Modifier.padding(start = 8.dp).weight(1f))
        }
        Segments(listOf(tr("Подмастерья", "Apprentices"), tr("Фартук", "Apron")), tab) { tab = it }
        Spacer(Modifier.height(10.dp))
        if (tab == 0) Apprentices(b, nav) else Apron(b)
        Spacer(Modifier.height(24.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Apprentices(b: Book, nav: Nav) {
    val mine = b.hobbies.associateBy { it.species }
    Text(tr("Встречено ${mine.size} из ${Kinds.allSpecies.size}", "${mine.size} of ${Kinds.allSpecies.size} met"), style = T.small)
    Catalog.groups.forEach { g ->
        val list = Catalog.kinds.filter { it.group == g.id }
        Text(g.title.uppercase(), style = T.sec, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), maxItemsInEachRow = 4) {
            list.forEach { k -> Slot(k.id, mine[k.id]?.let { b.stage(it) }, mine[k.id]?.name ?: k.title, mine[k.id]?.shiny == true) { nav.push(Screen.Species(k.id)) } }
            repeat((4 - list.size % 4) % 4) { Spacer(Modifier.weight(1f)) }
        }
    }
    Text(tr("СВОБОДНЫЕ", "FREE ONES"), style = T.sec, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), maxItemsInEachRow = 4) {
        Catalog.own.forEach { o -> val h = mine[o.id]; Slot(o.id, h?.let { b.stage(it) }, h?.label ?: o.name, h?.shiny == true) { nav.push(Screen.Species(o.id)) } }
        repeat((4 - Catalog.own.size % 4) % 4) { Spacer(Modifier.weight(1f)) }
    }
}

/** A frame in the album: the apprentice as it is now, or a dark shape with the hobby's name. */
@Composable
private fun androidx.compose.foundation.layout.RowScope.Slot(species: String, stage: Int?, label: String, shiny: Boolean, onClick: () -> Unit) {
    Column(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if (stage != null) C.card else C.card2).border(1.dp, C.line, RoundedCornerShape(10.dp)).tap(onClick)
        .padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        when {
            stage == null -> Mini(species, 52.dp, dim = true)
            stage < 0 -> Pic(eggOf(species), 48.dp, Modifier.padding(2.dp))
            else -> Creature(species, app.knackling.ui.art.frameOf(stage), 52.dp, shiny = shiny)
        }
        Text(label, style = T.tiny.copy(color = if (stage != null) C.ink else C.mute, textAlign = TextAlign.Center), maxLines = 2, minLines = 2, modifier = Modifier.padding(horizontal = 3.dp))
    }
}

/**
 * The apron: denim with stitched pockets; earned patches sit on it at stable, slightly crooked places,
 * the rest wait as dashed outlines. Tap a patch to read it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Apron(b: Book) {
    val got = b.badges.map { it.id }.toSet()
    var open by remember { mutableStateOf<Badge?>(null) }
    Text(tr("Пришито ${got.size} из ${Badges.all.size}. Каждая нашивка – +5 искр.", "${got.size} of ${Badges.all.size} sewn on. Each patch is +5 sparks."), style = T.small)
    val denim = C.denim
    Column(Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp, bottomStart = 14.dp, bottomEnd = 14.dp)).background(denim)
        .drawBehind {
            // denim twill and the stitched hem
            val r = Rand(5)
            repeat(260) { drawLine(Color.White.copy(alpha = .05f), Offset(r.next() * size.width, r.next() * size.height), Offset(r.next() * size.width, r.next() * size.height), 1f) }
            val m = 8.dp.toPx()
            drawRoundRect(Color(0xFFE9B949), Offset(m, m), size.copy(width = size.width - 2 * m, height = size.height - 2 * m), androidx.compose.ui.geometry.CornerRadius(34.dp.toPx()),
                style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))))
        }.padding(horizontal = 14.dp, vertical = 22.dp)) {
        Badges.ladders.forEach { l ->
            Text(l.title, style = T.smallB.copy(color = Color(0xFFF3E7C9)), modifier = Modifier.padding(top = 6.dp, bottom = 2.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                l.goals.forEachIndexed { i, g ->
                    val bd = Badge(l, i, g)
                    val has = bd.id in got
                    val tilt = ((app.knackling.domain.hash(bd.id) % 9) - 4).toFloat()
                    Box(Modifier.size(46.dp).tap { open = bd }, contentAlignment = Alignment.Center) {
                        if (has) Pic("patch_${l.patch}", 44.dp, Modifier.rotate(tilt))
                        else Canvas(Modifier.size(38.dp)) { drawCircle(Color.White.copy(alpha = .35f), size.minDimension / 2 - 2, style = androidx.compose.ui.graphics.drawscope.Stroke(2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 5f)))) }
                    }
                }
            }
        }
    }
    open?.let { bd -> Popup({ open = null }) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            val has = bd.id in got
            Pic("patch_${bd.ladder.patch}", 110.dp, alpha = if (has) 1f else .35f, grey = !has)
            Text(bd.title, style = T.h2, textAlign = TextAlign.Center)
            Text(bd.ladder.what(bd.goal), style = T.body, textAlign = TextAlign.Center)
            val v = bd.ladder.value(b)
            Text(if (has) tr("Пришита!", "Sewn on!") else tr("Сейчас: $v из ${bd.goal}", "Now: $v of ${bd.goal}"), style = T.bodyB.copy(color = if (has) C.ok else C.mute), modifier = Modifier.padding(top = 6.dp))
            Btn(tr("Хорошо", "OK"), Modifier.fillMaxWidth().padding(top = 12.dp)) { open = null }
        }
    } }
}


/** One species: its sheet of poses, the hobby it belongs to, and where it is in the workshop (or how to get it). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SpeciesPage(id: String, b: Book, nav: Nav) {
    val h = b.hobbies.firstOrNull { it.species == id }
    val k = Kinds.of(id)
    var adopt by remember { mutableStateOf(false) }
    Page(h?.name ?: Kinds.petName(id), { nav.pop() }) {
        val st = h?.let { b.stage(it) } ?: -2
        Box(Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFF000000 or Kinds.color(id)).copy(alpha = .15f)), contentAlignment = Alignment.Center) {
            when {
                st == -2 -> Mini(id, 170.dp, dim = true)
                st < 0 -> Pic(eggOf(id), 120.dp)
                else -> Creature(id, app.knackling.ui.art.frameOf(st, cheer = true), 200.dp, shiny = h?.shiny == true)
            }
        }
        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Pic(toolOf(id), 26.dp)
            Text(" " + (h?.label ?: k?.title ?: tr("Свободный подмастерье", "A free apprentice")), style = T.h3)
        }
        Text(when {
            h == null && k != null -> tr("Вылупится, если взять хобби «${k.title}».", "Hatches if you take up «${k.title}».")
            h == null -> tr("Свободный: возьмёт любое хобби, которого нет в списке.", "Free: takes any hobby that isn't on the list.")
            else -> Grow.name(st) + " · " + Grow.about(st)
        }, style = T.body, modifier = Modifier.padding(top = 4.dp))
        Text(tr("ПОЗЫ", "POSES"), style = T.sec, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
        val seen = when { st >= 2 -> 6; st == 1 -> 2; st == 0 -> 1; else -> 0 }
        val labels = listOf(tr("малыш", "baby"), tr("ученик", "pupil"), tr("спокоен", "calm"), tr("за делом", "at work"), tr("рад", "cheering"), tr("спит", "asleep"))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), maxItemsInEachRow = 3) {
            (0..5).forEach { f ->
                Column(Modifier.weight(1f).card().padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Mini(id, 70.dp, dim = f >= seen, frame = f)
                    Text(if (f < seen) labels[f] else "?", style = T.tiny)
                }
            }
        }
        if (h != null) Btn(tr("Открыть хобби", "Open the hobby"), Modifier.fillMaxWidth().padding(top = 16.dp)) { nav.replace(Screen.HobbyInfo(h.id)) }
        else Btn(tr("Взять яйцо", "Take the egg"), Modifier.fillMaxWidth().padding(top = 16.dp)) { adopt = true }
    }
    if (adopt) AdoptSheet(id, nav, { adopt = false }) { adopt = false; nav.pop() }
}

