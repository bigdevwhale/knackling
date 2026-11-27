package app.knackling.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.knackling.container
import app.knackling.data.Hobby
import app.knackling.domain.Badge
import app.knackling.domain.Book
import app.knackling.domain.Grow
import app.knackling.domain.Quest
import app.knackling.domain.Quests
import app.knackling.domain.tr
import app.knackling.ui.Nav
import app.knackling.ui.Screen
import app.knackling.ui.art.Creature
import app.knackling.ui.art.Ico
import app.knackling.ui.art.Pic
import app.knackling.ui.art.frameOf
import app.knackling.ui.art.toolOf
import app.knackling.ui.components.Btn
import app.knackling.ui.components.Ghost
import app.knackling.ui.components.Icon
import app.knackling.ui.components.Icons
import app.knackling.ui.components.Sheet
import app.knackling.ui.components.card
import app.knackling.ui.components.tap
import app.knackling.ui.theme.C
import app.knackling.ui.theme.T

/** Today's challenge from every apprentice: take one and the timer starts with it in mind. */
@Composable
fun IdeasSheet(b: Book, nav: Nav, onDone: () -> Unit) {
    val c = LocalContext.current.container
    Sheet(onDone) {
        Text(tr("Затеи дня", "Today's challenges"), style = T.h2)
        Text(tr("Каждый подмастерье предлагает свою. Возьмёшься – +20 опыта и 3 искры.", "Each apprentice suggests one. Take it on for +20 experience and 3 sparks."), style = T.small)
        Spacer(Modifier.height(10.dp))
        b.active.forEach { h ->
            val done = b.ideaDone(h)
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp).card().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                val st = b.stage(h)
                if (st < 0) Pic(app.knackling.ui.art.eggOf(h.species), 52.dp) else Creature(h.species, frameOf(st), 56.dp, shiny = h.shiny)
                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                    Text("${h.name} · ${h.label}", style = T.tiny)
                    Text(b.idea(h).text, style = T.hand.copy(fontSize = T.hand.fontSize * .95f), modifier = Modifier.padding(vertical = 2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (done) Text(tr("✓ сделано сегодня", "✓ done today"), style = T.smallB.copy(color = C.ok))
                        else {
                            Text(tr("Взяться", "Take it"), style = T.smallB.copy(color = C.acc), modifier = Modifier.clip(RoundedCornerShape(50)).tap {
                                onDone(); c.write { startLive(h.id, idea = true) }; nav.push(Screen.Live) }.padding(vertical = 4.dp, horizontal = 2.dp))
                            Spacer(Modifier.width(16.dp))
                            Text(tr("Другая", "Another"), style = T.smallB.copy(color = C.mute), modifier = Modifier.clip(RoundedCornerShape(50)).tap { c.write { anotherIdea(h) } }.padding(vertical = 4.dp, horizontal = 2.dp))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** Three tasks for today and three for the week, each claimed by a tap; all three open a chest. */
@Composable
fun QuestsSheet(b: Book, onDone: () -> Unit) {
    val c = LocalContext.current.container
    Sheet(onDone) {
        Text(tr("Задания", "Tasks"), style = T.h2)
        Text(tr("Выполняются сами, когда ты занимаешься. Забери награду – нажми на свиток.", "They complete as you practise. Tap the scroll to take the reward."), style = T.small)
        listOf(false, true).forEach { weekly ->
            val list = if (weekly) Quests.week(b) else Quests.today(b)
            Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text((if (weekly) tr("Неделя", "This week") else tr("Сегодня", "Today")).uppercase(), style = T.sec, modifier = Modifier.weight(1f))
                Text(tr("выполнено ${list.count { Quests.isDone(it, b) }} из ${list.size}", "${list.count { Quests.isDone(it, b) }} of ${list.size} done"), style = T.tiny)
            }
            list.forEach { q -> QuestRow(q, b) { c.write { claim(Quests.key(q, b), q.reward); say(tr("+${q.reward} искр", "+${q.reward} sparks")) } } }
            val all = list.all { Quests.claimed(it, b) }; val key = Quests.chestKey(weekly, b); val got = key in b.s.claimed
            val reward = if (weekly) Quests.CHEST_WEEK else Quests.CHEST_DAY
            Row(Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(12.dp)).background(if (all && !got) C.goldSoft else C.card2)
                .then(if (all && !got) Modifier.tap { c.write { claim(key, reward); say(tr("Сундучок: +$reward искр", "Chest: +$reward sparks")) } } else Modifier).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Ico("chest", 36.dp, dim = !all || got)
                Text(when { got -> tr("Сундучок открыт", "Chest opened"); all -> tr("Открой сундучок: +$reward искр", "Open the chest: +$reward sparks")
                    else -> tr("Сундучок за все три: +$reward искр", "A chest for all three: +$reward sparks") }, style = T.smallB.copy(color = if (all && !got) C.ink else C.mute), modifier = Modifier.padding(start = 8.dp))
            }
        }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun QuestRow(q: Quest, b: Book, onClaim: () -> Unit) {
    val done = Quests.isDone(q, b); val got = Quests.claimed(q, b)
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp).card(color = if (done && !got) C.goldSoft else null).then(if (done && !got) Modifier.tap(onClaim) else Modifier).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(26.dp).clip(CircleShape).background(if (done) C.ok else C.track), contentAlignment = Alignment.Center) { if (done) Icon(Icons.CHECK, Color.White, 16.dp) }
        Text(q.title, style = T.body.copy(color = if (got) C.mute else C.ink), modifier = Modifier.weight(1f).padding(horizontal = 10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Pic("ic_spark", 20.dp, grey = got)
            Text(if (got) tr("взято", "taken") else "+${q.reward}", style = T.smallB.copy(color = if (done && !got) C.acc else C.mute))
        }
    }
}

// ---------------- celebrations ----------------

sealed class News {
    data class Grew(val h: Hobby, val stage: Int) : News()
    data class Patches(val list: List<Badge>) : News()
}

/** The next thing to celebrate: an apprentice hatched or grew a rank, or new patches for the apron. */
fun nextNews(b: Book): News? {
    b.hobbies.forEach { h -> val st = b.stage(h); if (st >= 0 && st > (b.s.stages[h.id] ?: -1)) return News.Grew(h, st) }
    val fresh = b.badges.filter { it.id !in b.s.badges }
    return if (fresh.isNotEmpty()) News.Patches(fresh) else null
}

@Composable
fun NewsSheet(n: News, b: Book, nav: Nav) {
    val c = LocalContext.current.container
    when (n) {
        is News.Grew -> {
            fun done() = c.write { told(stage = n.h.id to n.stage) }
            Sheet(::done) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (n.stage == 0) tr("Вылупился!", "Hatched!") else tr("Новое звание!", "A new rank!"), style = T.title)
                    Box(Modifier.padding(vertical = 8.dp).size(200.dp).clip(CircleShape).background(C.goldSoft), contentAlignment = Alignment.Center) {
                        Creature(n.h.species, frameOf(n.stage, cheer = true), 180.dp, shiny = n.h.shiny)
                    }
                    Text("${n.h.name} – ${Grow.name(n.stage).lowercase()}", style = T.h2, textAlign = TextAlign.Center)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) { Pic(toolOf(n.h), 22.dp); Text(" " + n.h.label, style = T.smallB) }
                    Text(Grow.about(n.stage), style = T.body.copy(textAlign = TextAlign.Center), modifier = Modifier.padding(top = 8.dp))
                    Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Ghost(tr("Открытка", "Postcard"), Modifier.weight(1f)) { done(); nav.share = "pet:${n.h.id}" }
                        Btn(tr("Ура!", "Hooray!"), Modifier.weight(1f)) { done() }
                    }
                }
            }
        }
        is News.Patches -> {
            fun done() = c.write { told(badges = n.list.map { it.id }.toSet()) }
            Sheet(::done) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (n.list.size == 1) tr("Новая нашивка", "A new patch") else tr("Новые нашивки", "New patches"), style = T.title)
                    Text(tr("Пришиты на фартук в «Альбоме»", "Sewn onto the apron in the «Album»"), style = T.small)
                    Row(Modifier.padding(vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        n.list.take(3).forEach { bd ->
                            Column(Modifier.width(100.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Pic("patch_${bd.ladder.patch}", 88.dp)
                                Text(bd.title, style = T.smallB, textAlign = TextAlign.Center)
                                Text(bd.ladder.what(bd.goal), style = T.tiny, textAlign = TextAlign.Center)
                            }
                        }
                    }
                    if (n.list.size > 3) Text(tr("и ещё ${n.list.size - 3}", "and ${n.list.size - 3} more"), style = T.small)
                    Text(tr("+${n.list.size * 5} искр", "+${n.list.size * 5} sparks"), style = T.bodyB.copy(color = C.gold), modifier = Modifier.padding(top = 4.dp))
                    Btn(tr("Пришить", "Sew it on"), Modifier.fillMaxWidth().padding(top = 12.dp)) { done() }
                }
            }
        }
    }
}

/** A thin decorative border used by framed things. */
fun Modifier.framed() = this.border(1.5.dp, C.frame, RoundedCornerShape(10.dp))
