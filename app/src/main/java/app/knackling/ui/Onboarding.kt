package app.knackling.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.knackling.container
import app.knackling.data.BadBackup
import app.knackling.data.Backup
import app.knackling.domain.Say
import app.knackling.domain.tr
import app.knackling.ui.art.Creature
import app.knackling.ui.components.Btn
import app.knackling.ui.components.Ghost
import app.knackling.ui.components.Segments
import app.knackling.ui.theme.C
import app.knackling.ui.theme.T
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** First run: who lives here, how to call you, the first hobby (its egg). A copy from another phone can be loaded instead. */
@Composable
fun Onboarding() {
    val c = LocalContext.current.container
    var step by rememberSaveable { mutableIntStateOf(0) }
    var fem by rememberSaveable { mutableStateOf(false) }
    val open = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) c.write {
            try { val b = withContext(Dispatchers.IO) { Backup.read(app, uri) }; restore(b) }
            catch (e: BadBackup) { say(tr("Это не копия Knackling", "This isn't a Knackling copy")) }
            catch (e: Exception) { say(tr("Не получилось прочитать файл", "Couldn't read the file")) }
        }
    }
    Box(Modifier.fillMaxSize().background(C.bg).statusBarsPadding().navigationBarsPadding()) {
        if (step == 0) Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy((-24).dp), verticalAlignment = Alignment.Bottom) {
                Creature("guitar", 4, 110.dp); Creature("embroidery", 3, 130.dp); Creature("reading", 2, 110.dp)
            }
            Text("Knackling", style = T.title, modifier = Modifier.padding(top = 14.dp))
            Text(tr("Подмастерья для твоих хобби", "Apprentices for your hobbies"), style = T.hand)
            Text(tr("У каждого хобби – вышивки, гитары, чтения, бега – свой маленький подмастерье. Он вылупляется из яйца на первом занятии и растёт, пока ты практикуешься: от малыша до великого мастера.",
                "Every hobby – embroidery, guitar, reading, running – has its own little apprentice. It hatches at your first session and grows as you practise: from hatchling to grand master."),
                style = T.body, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 14.dp))
            Text(tr("Никто не голодает и не умирает. Если долго не браться за дело, подмастерье просто заскучает и задремлет.",
                "Nobody starves or dies. If you leave a hobby for long, its apprentice just gets bored and dozes off."), style = T.small, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp))
            Text(tr("КАК К ТЕБЕ ОБРАЩАТЬСЯ", "HOW TO CALL YOU"), style = T.sec, modifier = Modifier.padding(top = 22.dp, bottom = 6.dp))
            Segments(listOf(tr("Мастер", "Maker (he)"), tr("Мастерица", "Maker (she)")), if (fem) 1 else 0) { fem = it == 1; Say.fem = fem }
            Spacer(Modifier.height(24.dp))
            Btn(tr("Выбрать первое хобби", "Pick the first hobby"), Modifier.fillMaxWidth()) { step = 1 }
            Ghost(tr("Перенести с другого телефона", "Move from another phone"), Modifier.fillMaxWidth().padding(top = 10.dp), small = true) { runCatching { open.launch(arrayOf("*/*")) } }
        } else {
            val nav = remember { Nav() }
            app.knackling.ui.hobby.AddHobbyPage(app.knackling.domain.Book(emptyList(), emptyList(), emptyList(), app.knackling.data.Settings(), c.clock()), nav, { step = 0 }) {
                c.write { settings.update { it.copy(onboarded = true, fem = fem) } }
            }
        }
    }
}
