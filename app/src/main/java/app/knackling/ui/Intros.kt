package app.knackling.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.knackling.container
import app.knackling.domain.tr
import app.knackling.ui.art.Creature
import app.knackling.ui.components.Btn
import app.knackling.ui.components.tap
import app.knackling.ui.theme.C
import app.knackling.ui.theme.T

/** A first-visit introduction to one screen: who explains it, the title and a few short points. */
class Intro(val id: String, val kind: String, val title: String, val points: List<String>)

/** The introduction for what is on top now: the page over the tabs, or the tab itself. */
fun introFor(top: Screen?, tab: Int): Intro? = when (top) {
    null -> when (tab) {
        0 -> Intro("i.home", "embroidery", tr("Это твоя мастерская", "This is your workshop"), listOf(
            tr("У каждого хобби свой подмастерье. Он вылупляется после первого занятия и растёт с каждым часом практики.", "Each hobby has its own apprentice. It hatches after the first session and grows with every hour of practice."),
            tr("Красная подушечка внизу начинает занятие: засеки время или запиши прошедшее.", "The red pincushion below starts a session: time it or log one you've done."),
            tr("Полки снизу – твои хобби и недельные цели. Нажми на подмастерья, чтобы открыть его страницу.", "The shelf below holds your hobbies and weekly goals. Tap an apprentice to open its page."),
            tr("Искры за занятия, затеи и задания тратятся в лавке на уют.", "Sparks from sessions, challenges and tasks are spent in the shop on cosy things.")))
        1 -> Intro("i.journal", "reading", tr("Журнал", "The journal"), listOf(
            tr("Все занятия по дням, с заметками и фото.", "Every session by day, with notes and photos."),
            tr("«Витрина» – стена с фотографиями твоих работ.", "«Showcase» – a wall of photos of your work."),
            tr("«Проекты» – то, что сейчас в работе: шарф, песня, книга. Законченный проект даёт много опыта.", "«Projects» – what's in the making: a scarf, a song, a book. A finished project gives lots of experience.")))
        2 -> Intro("i.stats", "puzzles", tr("Ведомость", "The ledger"), listOf(
            tr("Лоскутное одеяло года: каждый день – лоскут цвета того хобби, которому ты отдал{|а} больше всего времени.", "The year's quilt: each day a patch in the colour of the hobby you gave most time to."),
            tr("Сколько часов у каждого хобби, когда ты обычно занимаешься и как идут недели.", "Hours per hobby, when you usually practise and how the weeks go."),
            tr("Открытки за месяц и за год – кнопкой «Поделиться».", "Postcards for the month and the year – with «Share».")))
        else -> Intro("i.album", "beading", tr("Альбом", "The album"), listOf(
            tr("Сорок подмастерьев для сорока хобби и пятнадцать свободных – для твоих собственных.", "Forty apprentices for forty hobbies and fifteen free ones for your own."),
            tr("Нажми на силуэт, чтобы узнать, кто это и как растёт.", "Tap a silhouette to see who it is and how it grows."),
            tr("Нашивки пришиваются на фартук сами – за часы, серии, проекты и затеи.", "Patches sew themselves onto the apron – for hours, streaks, projects and challenges.")))
    }
    is Screen.HobbyInfo -> Intro("i.hobby", "knitting", tr("Страница хобби", "A hobby's page"), listOf(
        tr("Лента под подмастерьем – сколько опыта до следующего звания.", "The ribbon under the apprentice shows the experience left to the next rank."),
        tr("Затея дня – маленький вызов. Возьмёшься – получишь больше опыта и искр.", "The challenge of the day is a small dare. Take it for more experience and sparks."),
        tr("Недельная цель меняется в карандаше сверху.", "Change the weekly goal with the pencil at the top.")))
    Screen.Live -> Intro("i.live", "guitar", tr("Идёт занятие", "Session running"), listOf(
        tr("Можно выйти из приложения – часы продолжат идти, они видны в шторке.", "You can leave the app – the clock keeps going, it's in the notification shade."),
        tr("Пауза, если отвлёкся{|ась}. «Готово» – и подмастерье получит опыт.", "Pause if you're interrupted. «Done» and your apprentice gets the experience.")))
    Screen.Shop -> Intro("i.shop", "woodwork", tr("Лавка", "The shop"), listOf(
        tr("Вещи для мастерской за искры. Некоторые откроются на следующих уровнях мастерской.", "Things for the workshop, paid in sparks. Some open at later workshop levels."),
        tr("Эликсир блеска перекрашивает подмастерья в редкие цвета.", "The shine elixir recolours an apprentice in rare colours.")))
    else -> null
}

/** The introduction as a card sliding up over the screen; «Got it» stores its id in Settings.seen. */
@Composable
fun IntroSheet(intro: Intro) {
    val c = LocalContext.current.container
    fun done() = c.write { settings.update { it.copy(seen = it.seen + intro.id) } }
    BackHandler { done() }
    val a = remember(intro.id) { Animatable(0f) }
    LaunchedEffect(intro.id) { a.animateTo(1f, tween(320)) }
    Box(Modifier.fillMaxSize().background(Color(0x99000000).copy(alpha = .6f * a.value)).tap {}, contentAlignment = Alignment.BottomCenter) {
        Column(Modifier.fillMaxWidth().offset(y = ((1 - a.value) * 220).dp).alpha(a.value).clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(C.bg)
            .navigationBarsPadding().padding(start = 22.dp, end = 22.dp, top = 8.dp, bottom = 18.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Creature(intro.kind, 4, 96.dp)
                Text(app.knackling.domain.Say.g(intro.title), style = T.h2, modifier = Modifier.weight(1f).padding(start = 6.dp, bottom = 14.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 4.dp)) {
                intro.points.forEachIndexed { i, s ->
                    Row(verticalAlignment = Alignment.Top) {
                        Box(Modifier.size(24.dp).clip(CircleShape).background(C.accSoft), contentAlignment = Alignment.Center) { Text("${i + 1}", style = T.smallB.copy(color = C.acc)) }
                        Spacer(Modifier.width(10.dp))
                        Text(app.knackling.domain.Say.g(s), style = T.body, modifier = Modifier.weight(1f))
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Btn(tr("Понятно", "Got it"), Modifier.fillMaxWidth()) { done() }
        }
    }
}
