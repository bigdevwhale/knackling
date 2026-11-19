package app.knackling.ui.more

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.knackling.BuildConfig
import app.knackling.container
import app.knackling.currentLanguage
import app.knackling.data.BadBackup
import app.knackling.data.Backup
import app.knackling.domain.Book
import app.knackling.domain.Decors
import app.knackling.domain.Level
import app.knackling.domain.Say
import app.knackling.domain.fmt
import app.knackling.domain.tr
import app.knackling.setLanguage
import app.knackling.ui.Nav
import app.knackling.ui.Screen
import app.knackling.ui.art.Creature
import app.knackling.ui.art.Ico
import app.knackling.ui.art.Pic
import app.knackling.ui.art.Workshop
import app.knackling.ui.art.frameOf
import app.knackling.ui.components.Btn
import app.knackling.ui.components.ChipRow
import app.knackling.ui.components.Ghost
import app.knackling.ui.components.Page
import app.knackling.ui.components.Popup
import app.knackling.ui.components.Segments
import app.knackling.ui.components.card
import app.knackling.ui.components.tap
import app.knackling.ui.theme.C
import app.knackling.ui.theme.T
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The shop: things for the workshop (tap to try one on the scene), and the shine elixir for an apprentice. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShopPage(b: Book, nav: Nav) {
    val c = LocalContext.current.container
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var trying by remember { mutableStateOf<String?>(null) }
    Page(tr("Лавка", "Shop"), { nav.pop() }, action = {
        Row(Modifier.padding(end = 10.dp), verticalAlignment = Alignment.CenterVertically) { Pic("ic_spark", 26.dp); Text(fmt(b.sparks), style = T.numS) }
    }) {
        // the workshop as it would be with the thing tried on
        val shown = if (trying != null) b.s.copy(items = b.s.items + trying!!) else b.s
        Workshop(app.knackling.domain.Book(b.hobbies, b.sessions, b.projects, shown, b.today), Modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(12.dp)))
        Text(tr("Мастерская: ${Level.title(b.level).lowercase()}, уровень ${b.level}", "Workshop: ${Level.title(b.level).lowercase()}, level ${b.level}"), style = T.small, modifier = Modifier.padding(top = 6.dp))
        Spacer(Modifier.height(10.dp))
        Segments(listOf(tr("Для мастерской", "For the workshop"), tr("Эликсир блеска", "Shine elixir")), tab) { tab = it }
        Spacer(Modifier.height(10.dp))
        if (tab == 0) FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), maxItemsInEachRow = 3) {
            Decors.all.forEach { d ->
                val own = d.id in b.s.items; val locked = b.level < d.level; val on = trying == d.id
                Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(if (on) C.accSoft else C.card).border(if (on) 2.dp else 1.dp, if (on) C.acc else C.line, RoundedCornerShape(12.dp))
                    .tap { if (!own) trying = if (on) null else d.id }.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Pic("decor_${d.id}", 64.dp, alpha = if (locked) .45f else 1f, grey = locked)
                    Text(d.title, style = T.tiny.copy(color = C.ink, textAlign = TextAlign.Center), maxLines = 2)
                    when {
                        own -> Text(tr("стоит", "placed"), style = T.smallB.copy(color = C.ok))
                        locked -> Text(tr("с ур. ${d.level}", "from lv ${d.level}"), style = T.smallB.copy(color = C.mute))
                        else -> Row(verticalAlignment = Alignment.CenterVertically) { Pic("ic_spark", 16.dp); Text("${d.price}", style = T.smallB.copy(color = if (b.sparks >= d.price) C.ink else C.mute)) }
                    }
                }
            }
        } else {
            Text(tr("Перекрашивает подмастерья в редкие цвета, навсегда. ${Decors.SHINY} искр.", "Recolours an apprentice in rare colours, for good. ${Decors.SHINY} sparks."), style = T.small)
            b.active.forEach { h ->
                val st = b.stage(h)
                Row(Modifier.fillMaxWidth().padding(top = 8.dp).card().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (st >= 0) Creature(h.species, frameOf(st), 56.dp, shiny = h.shiny) else Pic(app.knackling.ui.art.eggOf(h.species), 50.dp)
                    Column(Modifier.weight(1f).padding(start = 10.dp)) { Text(h.name, style = T.bodyB); Text(h.label, style = T.tiny) }
                    if (h.shiny) Text(tr("блестит", "shiny"), style = T.smallB.copy(color = C.gold))
                    else Btn("${Decors.SHINY}", small = true, enabled = b.sparks >= Decors.SHINY && st >= 0) {
                        c.write { if (shine(h)) say(tr("${h.name} заблестел!", "${h.name} shines!")) }
                    }
                }
            }
        }
        trying?.let { id -> Decors.byId[id]?.let { d ->
            val locked = b.level < d.level
            Btn(if (locked) tr("Откроется на уровне ${d.level}", "Opens at level ${d.level}") else tr("Купить «${d.title}» за ${d.price}", "Buy «${d.title}» for ${d.price}"),
                Modifier.fillMaxWidth().padding(top = 14.dp), enabled = !locked && b.sparks >= d.price) {
                c.write { if (buy(id)) say(tr("Теперь в мастерской: ${d.title.lowercase()}", "Now in the workshop: ${d.title.lowercase()}")) }; trying = null
            }
        } }
        Text(tr("Искры: 1 за каждые 15 минут, 1 за занятие, 2 за фото, 3 за затею, 10 за готовую работу, 5 за нашивку и ещё за задания.",
            "Sparks: 1 per 15 minutes, 1 per session, 2 per photo, 3 per challenge, 10 per finished work, 5 per patch, plus tasks."), style = T.tiny, modifier = Modifier.padding(top = 16.dp))
    }
}

@Composable
fun SettingsPage(b: Book, nav: Nav) {
    val c = LocalContext.current.container
    Page(tr("Настройки", "Settings"), { nav.pop() }) {
        Section(tr("Обращение", "How to call you"))
        Segments(listOf(tr("Мастер", "Maker (he)"), tr("Мастерица", "Maker (she)")), if (b.s.fem) 1 else 0) { i -> Say.fem = i == 1; c.write { settings.update { it.copy(fem = i == 1) } } }
        val gone = b.hobbies.filter { it.archived }
        if (gone.isNotEmpty()) {
            Section(tr("Архив", "Archive"))
            gone.forEach { h -> Tap("journal", h.label, h.name) { nav.push(Screen.EditHobby(h.id)) } }
        }
        Section(tr("Напоминание", "Reminder"))
        Text(tr("Если за день не было ни одного занятия, а кто-то из подмастерьев давно скучает, он напишет – не чаще раза в день.",
            "If there was no session today and an apprentice has missed its hobby for days, it writes – at most once a day."), style = T.small)
        val hours = listOf(-1, 10, 13, 17, 19, 20, 21)
        ChipRow(hours.map { if (it < 0) tr("Выкл.", "Off") else "$it:00" }, hours.indexOf(b.s.nudge).coerceAtLeast(0), Modifier.padding(top = 8.dp)) { i ->
            c.write { settings.update { it.copy(nudge = hours[i]) } }
        }
        Section(tr("Оформление", "Look"))
        val themes = listOf("system", "light", "dark")
        Segments(listOf(tr("Как в системе", "System"), tr("Светлая", "Light"), tr("Тёмная", "Dark")), themes.indexOf(b.s.theme).coerceAtLeast(0)) { i ->
            c.write { settings.update { it.copy(theme = themes[i]) } }
        }
        Section(tr("Язык", "Language"))
        val langs = listOf("", "ru", "en")
        Segments(listOf(tr("Как в системе", "System"), "Русский", "English"), langs.indexOf(currentLanguage()).coerceAtLeast(0)) { setLanguage(langs[it]) }
        Section(tr("Подсказки", "Hints"))
        Tap("bulb", tr("Показать знакомство с экранами заново", "Show the screen introductions again"), null) { c.write { settings.update { it.copy(seen = emptySet()) }; say(tr("Подсказки вернутся", "Hints will come back")) } }
        Section(tr("Данные", "Data"))
        Text(tr("Всё, что есть в приложении, вместе с фото работ – одним файлом: чтобы перенести на другой телефон или сохранить на всякий случай.",
            "Everything in the app, photos included, as one file: to move to another phone or to keep just in case."), style = T.small)
        var found by remember { mutableStateOf<Backup?>(null) }
        val failed = tr("Не получилось прочитать файл", "Couldn't read the file")
        val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
            if (uri != null) c.write {
                val ok = runCatching { val bk = backup(); withContext(Dispatchers.IO) { Backup.write(app, uri, bk) } }.isSuccess
                say(if (ok) tr("Копия сохранена", "The copy is saved") else tr("Не получилось сохранить файл", "Couldn't save the file"))
            }
        }
        val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) c.write {
                try { found = withContext(Dispatchers.IO) { Backup.read(app, uri) } }
                catch (e: BadBackup) { say(if (e.newer) tr("Файл из более новой версии – обнови приложение", "The file is from a newer version – update the app") else tr("Это не копия Knackling", "This isn't a Knackling copy")) }
                catch (e: Exception) { say(failed) }
            }
        }
        Tap("scroll", tr("Сохранить копию в файл", "Save a copy to a file"), tr("хобби, занятия, проекты, фото", "hobbies, sessions, projects, photos")) {
            runCatching { save.launch("knackling-${java.time.LocalDate.now()}.zip") }.onFailure { c.say(tr("Не нашлось приложения для файлов", "No app for files was found")) }
        }
        Tap("journal", tr("Восстановить из файла", "Restore from a file"), tr("заменит всё, что есть сейчас", "replaces everything here now")) {
            runCatching { open.launch(arrayOf("*/*")) }.onFailure { c.say(tr("Не нашлось приложения для файлов", "No app for files was found")) }
        }
        found?.let { bk ->
            Popup({ found = null }) {
                Text(tr("Заменить всё этим файлом?", "Replace everything with this file?"), style = T.h3)
                Text(tr("В копии хобби: ${bk.hobbies.size}, занятий: ${bk.sessions.size}, фото: ${bk.photos.size}.", "It holds hobbies: ${bk.hobbies.size}, sessions: ${bk.sessions.size}, photos: ${bk.photos.size}."),
                    style = T.small.copy(color = C.ink), modifier = Modifier.padding(top = 8.dp))
                Text(tr("Всё, что сейчас есть в приложении, исчезнет, и вернуть это будет нельзя.", "Everything in the app now will be gone, and there is no way back."), style = T.small, modifier = Modifier.padding(top = 6.dp))
                Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Ghost(tr("Отмена", "Cancel"), Modifier.weight(1f)) { found = null }
                    Btn(tr("Заменить", "Replace"), Modifier.weight(1f), color = C.bad, ink = Color.White) {
                        found = null
                        c.write { if (runCatching { restore(bk) }.isSuccess) { nav.stack.clear(); nav.tab = 0; say(tr("Всё на месте", "Everything is in place")) } else say(failed) }
                    }
                }
            }
        }
        Section(tr("Поддержать", "Support"))
        Text(tr("StillCraft держится на поддержке тех, кому нравятся эти приложения.", "StillCraft lives on the goodwill of people who like these apps."), style = T.small)
        Link("heart", tr("Поддержать разработку", "Support development"), null, Community.SUPPORT)
        Section("StillCraft")
        Text(tr("Knackling – одно из небольших приложений StillCraft.", "Knackling is one of the small StillCraft apps."), style = T.small)
        Link("bell", tr("Сообщество в Telegram", "Telegram community"), tr("новости, обсуждения, другие приложения", "news, talk and the other apps"), Community.CHAT)
        Link("pencil", tr("Сообщить об ошибке", "Report a bug"), tr("через бота StillCraft", "through the StillCraft bot"), Community.BUG)
        Link("bulb", tr("Предложить идею", "Suggest an idea"), null, Community.IDEA)
        Link("postcard", tr("Следить за обновлениями", "Follow updates"), tr("бот напишет, когда выйдет новая версия", "the bot writes when a new version is out"), Community.FOLLOW)
        Link("house", tr("Все приложения StillCraft", "All StillCraft apps"), null, Community.SITE)
        Link("gear", tr("Исходный код", "Source code"), "GitHub", Community.CODE)
        Section(tr("О приложении", "About"))
        Text(tr("Knackling – подмастерья для твоих хобби. Всё хранится только на телефоне: без аккаунтов, рекламы и интернета.",
            "Knackling – apprentices for your hobbies. Everything stays on the phone: no accounts, no ads, no internet."), style = T.small)
        Text("v" + BuildConfig.VERSION_NAME, style = T.tiny, modifier = Modifier.padding(top = 4.dp))
    }
}

/** Where the community lives: the StillCraft bot links carry the app's slug. */
object Community {
    private const val BOT = "https://t.me/ArcaneWhisperBot?start="
    const val SLUG = "knackling"
    const val BUG = "${BOT}bug_$SLUG"
    const val IDEA = "${BOT}wish_$SLUG"
    const val FOLLOW = "${BOT}f_$SLUG"
    const val CHAT = "https://t.me/+Q4gFEDk8O842OGVi"
    const val SUPPORT = "https://bigdevwhale.github.io/stillcraft/support/"
    const val SITE = "https://bigdevwhale.github.io/stillcraft/"
    const val CODE = "https://github.com/bigdevwhale/knackling"
}

@Composable
private fun Section(s: String) = Text(s.uppercase(), style = T.sec, modifier = Modifier.padding(top = 20.dp, bottom = 6.dp))

@Composable
private fun Link(icon: String, title: String, sub: String?, url: String) {
    val ctx = LocalContext.current
    Tap(icon, title, sub) {
        runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}

@Composable
private fun Tap(icon: String, title: String, sub: String?, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp).card().tap(onClick).padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Ico(icon, 30.dp)
        Column(Modifier.weight(1f).padding(start = 12.dp)) { Text(title, style = T.bodyB); if (sub != null) Text(sub, style = T.small) }
        Text("›", style = T.h2.copy(color = C.mute))
    }
}

