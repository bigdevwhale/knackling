package app.knackling.ui.hobby

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.knackling.container
import app.knackling.data.Hobby
import app.knackling.domain.Book
import app.knackling.domain.Catalog
import app.knackling.domain.Kinds
import app.knackling.domain.tr
import app.knackling.ui.Nav
import app.knackling.ui.Screen
import app.knackling.ui.art.Creature
import app.knackling.ui.art.OWN_ICONS
import app.knackling.ui.art.Pic
import app.knackling.ui.art.eggOf
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
import app.knackling.ui.components.Sheet
import app.knackling.ui.components.tap
import app.knackling.ui.theme.C
import app.knackling.ui.theme.T

val GOALS = listOf(0, 60, 120, 180, 300, 420)
fun goalText(m: Int) = if (m == 0) tr("без цели", "no goal") else if (m < 60) tr("$m мин", "$m min") else tr("${m / 60} ч", "${m / 60} h")

/** The catalogue: forty hobbies on shelves by kind, each with the apprentice that hatches for it, and «my own». */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddHobbyPage(b: Book, nav: Nav, onBack: () -> Unit = { nav.pop() }, onAdded: (() -> Unit)? = null) {
    var group by rememberSaveable { mutableIntStateOf(0) }
    var pick by remember { mutableStateOf<String?>(null) }
    val have = b.hobbies.map { it.species }.toSet()
    Page(tr("Новое хобби", "A new hobby"), onBack) {
        Text(tr("У каждого хобби свой подмастерье. Выбери – и получишь яйцо.", "Every hobby has its own apprentice. Pick one and you get an egg."), style = T.small)
        Spacer(Modifier.height(10.dp))
        ChipRow(listOf(tr("Все", "All")) + Catalog.groups.map { it.title } + tr("Своё", "My own"), group) { group = it }
        Spacer(Modifier.height(12.dp))
        val own = group == Catalog.groups.size + 1
        if (!own) {
            val list = if (group == 0) Catalog.kinds else Catalog.kinds.filter { it.group == Catalog.groups[group - 1].id }
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), maxItemsInEachRow = 3) {
                list.forEach { k ->
                    val got = k.id in have
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(C.card).border(1.dp, C.line, RoundedCornerShape(12.dp))
                        .tap { pick = k.id }.padding(vertical = 8.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(contentAlignment = Alignment.BottomEnd) {
                            Creature(k.id, 0, 64.dp)
                            Pic(toolOf(k.id), 28.dp)
                        }
                        Text(k.title, style = T.smallB.copy(textAlign = TextAlign.Center, color = if (got) C.mute else C.ink), maxLines = 2, modifier = Modifier.padding(top = 2.dp))
                        Text(if (got) tr("ещё одно?", "one more?") else k.pet, style = T.tiny, maxLines = 1)
                    }
                }
                repeat((3 - list.size % 3) % 3) { Spacer(Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(12.dp))
        }
        // my own hobby: fifteen free apprentices
        Text(tr("СВОЁ ХОББИ", "MY OWN HOBBY"), style = T.sec, modifier = Modifier.padding(top = 6.dp, bottom = 6.dp))
        Text(tr("Нет в списке? Выбери свободного подмастерья и назови хобби сам{|а}.".let(app.knackling.domain.Say::g), "Not on the list? Pick a free apprentice and name the hobby yourself."), style = T.small)
        Spacer(Modifier.height(8.dp))
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), maxItemsInEachRow = 5) {
            Catalog.own.forEach { o ->
                val got = o.id in have
                Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(C.card).border(1.dp, C.line, RoundedCornerShape(12.dp)).tap { if (!got) pick = o.id }
                    .padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Creature(o.id, 0, 48.dp)
                    Text(if (got) "✓" else o.name, style = T.tiny.copy(color = if (got) C.ok else C.ink), maxLines = 1)
                }
            }
        }
    }
    pick?.let { sp -> AdoptSheet(sp, if (onAdded == null) nav else null, { pick = null }, taken = b.hobbies.filter { it.species == sp }.map { it.label }.toSet()) {
        pick = null; if (onAdded == null) nav.pop() else onAdded() } }
}

/**
 * «Take this hobby»: the apprentice's name, the hobby's words for an own one, the weekly goal.
 * A preset hobby can be taken more than once (two languages, cross and satin stitch): [taken] are the names of the ones
 * already there, and the new one must be called differently.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdoptSheet(species: String, nav: Nav?, onDismiss: () -> Unit, taken: Set<String> = emptySet(), onDone: () -> Unit) {
    val c = LocalContext.current.container
    val k = Kinds.of(species)
    var name by rememberSaveable(species) { mutableStateOf(Kinds.petName(species)) }
    var title by rememberSaveable(species) { mutableStateOf("") }
    var unit by rememberSaveable(species) { mutableStateOf("") }
    var goal by rememberSaveable(species) { mutableIntStateOf(120) }
    var variant by rememberSaveable(species) { mutableStateOf(k?.variants?.firstOrNull()?.id ?: "") }
    var icon by rememberSaveable(species) { mutableStateOf("ic_gear") }
    val label = Hobby(species = species, title = title.trim(), name = "", since = 0, variant = variant).label
    val again = k != null && taken.isNotEmpty()
    Sheet(onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(96.dp).clip(CircleShape).background(C.card2), contentAlignment = Alignment.Center) { Pic(eggOf(species), 70.dp) }
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(k?.title ?: tr("Своё хобби", "My own hobby"), style = T.h2)
                Text(tr("Из яйца вылупится ${Kinds.petName(species)}. Яйцо согреется на первом занятии.", "${Kinds.petName(species)} will hatch. The egg warms up at the first session."), style = T.small)
            }
        }
        if (k == null) {
            Lbl(tr("Что за хобби", "What hobby"))
            Field(title, { title = it.take(40) }, tr("например, Макраме", "e.g. Macramé"))
            Lbl(tr("Значок", "Icon"))
            IconPicker(icon) { icon = it }
            Lbl(tr("Что считать, кроме минут (необязательно)", "What to count besides minutes (optional)"))
            Field(unit, { unit = it.take(20) }, tr("например, узлов", "e.g. knots"))
        } else {
            if (k.variants.isNotEmpty()) {
                Lbl(tr("Какая", "Which kind"))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { k.variants.forEach { v -> Chip(v.title, variant == v.id) { variant = v.id } } }
            }
            Lbl(if (again) tr("Как назвать", "What to call it") else tr("Своё название (необязательно)", "Own name (optional)"))
            Field(title, { title = it.take(40) }, if (species == "languages") tr("например, Испанский", "e.g. Spanish") else label)
            if (again) Text((if (label in taken) tr("«$label» уже есть – назови это хобби по-другому", "«$label» is already there: give this one another name")
                else tr("Будет отдельным хобби со своим подмастерьем и своей историей", "It will be a separate hobby with its own apprentice and history")),
                style = T.small.copy(color = if (label in taken) C.bad else C.mute), modifier = Modifier.padding(top = 4.dp))
        }
        Lbl(tr("Имя подмастерья", "The apprentice's name"))
        Field(name, { name = it.take(24) }, Kinds.petName(species))
        Lbl(tr("Цель на неделю", "Weekly goal"))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { GOALS.forEach { g -> Chip(goalText(g), goal == g) { goal = g } } }
        Spacer(Modifier.height(18.dp))
        Btn(tr("Взять яйцо", "Take the egg"), Modifier.fillMaxWidth(), enabled = if (k == null) title.isNotBlank() else label !in taken) {
            val h = Hobby(species = species, title = title.trim(), name = name.trim().ifBlank { Kinds.petName(species) }, since = c.clock(), goal = goal,
                unit = if (k == null) (if (unit.isBlank()) "" else ":" + unit.trim()) else "", variant = variant,
                icon = if (k == null && icon != "ic_gear") icon else "")
            c.write { val id = saveHobby(h); say(tr("${h.name} в мастерской!", "${h.name} moved in!")); if (nav != null) kotlinx.coroutines.delay(150) ; nav?.push(Screen.HobbyInfo(id)) }
            onDone()
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** An own hobby's picture: a strip of them to scroll, the chosen one framed. */
@Composable
private fun IconPicker(chosen: String, onPick: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        OWN_ICONS.forEach { ic ->
            val on = ic == chosen
            Box(Modifier.size(50.dp).clip(RoundedCornerShape(10.dp)).background(if (on) C.card2 else C.card).border(if (on) 2.dp else 1.dp, if (on) C.acc else C.line, RoundedCornerShape(10.dp))
                .tap { onPick(ic) }, contentAlignment = Alignment.Center) { Pic(ic, 34.dp) }
        }
    }
}

@Composable
private fun Lbl(s: String) = Text(s.uppercase(), style = T.sec, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))

/** A hobby's settings: names, the goal, what's counted, the workshop spot, archive, delete. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditHobbyPage(id: Long, b: Book, nav: Nav) {
    val c = LocalContext.current.container
    val h = b.byId[id] ?: run { LaunchedEffect(Unit) { nav.pop() }; return }
    var name by rememberSaveable { mutableStateOf(h.name) }
    var title by rememberSaveable { mutableStateOf(h.title) }
    var goal by rememberSaveable { mutableIntStateOf(h.goal) }
    var unit by rememberSaveable { mutableStateOf(if (h.unit.startsWith(":")) h.unit.drop(1) else "") }
    var variant by rememberSaveable { mutableStateOf(h.variant) }
    var icon by rememberSaveable { mutableStateOf(h.icon.takeIf { it in OWN_ICONS } ?: "ic_gear") }
    var countIt by rememberSaveable { mutableStateOf(h.unit != "-") }
    var del by remember { mutableStateOf(false) }
    val onStage = b.s.stage.contains(h.id)
    Page(tr("Настройки хобби", "Hobby settings"), { nav.pop() }) {
        Lbl(tr("Имя подмастерья", "The apprentice's name"))
        Field(name, { name = it.take(24) }, Kinds.petName(h.species))
        if (h.own) {
            Lbl(tr("Хобби", "Hobby"))
            Field(title, { title = it.take(40) }, tr("Название", "Name"))
            Lbl(tr("Значок", "Icon"))
            IconPicker(icon) { icon = it }
            Lbl(tr("Что считать, кроме минут", "What to count besides minutes"))
            Field(unit, { unit = it.take(20) }, tr("например, узлов", "e.g. knots"))
        } else Kinds.of(h.species)?.let { k ->
            if (k.variants.isNotEmpty()) {
                Lbl(tr("Какая", "Which kind"))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { k.variants.forEach { v -> Chip(v.title, variant == v.id) { variant = v.id } } }
            }
            Lbl(tr("Своё название (необязательно)", "Own name (optional)"))
            Field(title, { title = it.take(40) }, h.copy(title = "", variant = variant).label)
        }
        if (!h.own) Kinds.of(h.species)?.unit?.let { u ->
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Считать ", "Count ") + (Catalog.units[u]?.many ?: u), style = T.bodyB, modifier = Modifier.weight(1f))
                Switch(countIt, { countIt = it }, colors = SwitchDefaults.colors(checkedTrackColor = C.acc))
            }
        }
        Lbl(tr("Цель на неделю", "Weekly goal"))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { (GOALS + listOf(600, 900)).forEach { g -> Chip(goalText(g), goal == g) { goal = g } } }
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tr("Всегда в мастерской", "Always in the workshop"), style = T.bodyB)
                Text(tr("Иначе там сидят пятеро тех, с кем занимаешься чаще", "Otherwise the five you practise with most sit there"), style = T.small)
            }
            Switch(onStage, { on -> c.write { settings.update { it.copy(stage = if (on) (it.stage + h.id).distinct().takeLast(5) else it.stage - h.id) } } },
                colors = SwitchDefaults.colors(checkedTrackColor = C.acc))
        }
        Spacer(Modifier.height(18.dp))
        Btn(tr("Сохранить", "Save"), Modifier.fillMaxWidth()) {
            c.write { saveHobby(h.copy(name = name.trim().ifBlank { h.name }, title = if (h.own) title.trim().ifBlank { h.title } else title.trim(), goal = goal, variant = variant,
                icon = if (h.own && icon != "ic_gear") icon else "",
                unit = if (h.own) (if (unit.isBlank()) "" else ":" + unit.trim()) else if (countIt) "" else "-")) }
            nav.pop()
        }
        Spacer(Modifier.height(24.dp))
        Ghost(if (h.archived) tr("Вернуть из архива", "Bring back from the archive") else tr("Убрать в архив", "Archive"), Modifier.fillMaxWidth(), small = true) {
            c.write { archive(h, !h.archived) }; nav.pop()
        }
        Text(tr("В архиве хобби не видно в мастерской, но вся история остаётся.", "An archived hobby leaves the workshop, but its history stays."), style = T.tiny, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
        Row(Modifier.fillMaxWidth().tap { del = true }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.TRASH, C.bad, 20.dp); Text(tr("Удалить хобби и всю его историю", "Delete the hobby and all its history"), style = T.bodyB.copy(color = C.bad), modifier = Modifier.padding(start = 8.dp))
        }
    }
    if (del) Popup({ del = false }) {
        Text(tr("Удалить «${h.label}»?", "Delete «${h.label}»?"), style = T.h2)
        Text(tr("Занятия, проекты и фото этого хобби удалятся. Это не отменить.", "Its sessions, projects and photos go too. This can't be undone."), style = T.body, modifier = Modifier.padding(vertical = 8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Ghost(tr("Нет", "No"), Modifier.weight(1f)) { del = false }
            Btn(tr("Удалить", "Delete"), Modifier.weight(1f), color = C.bad) { del = false; c.write { deleteHobby(h.id) }; nav.stack.clear() }
        }
    }
}
