package app.knackling

import android.app.Application
import android.content.Context
import androidx.room.withTransaction
import app.knackling.data.Backup
import app.knackling.data.Db
import app.knackling.data.Hobby
import app.knackling.data.Live
import app.knackling.data.Photos
import app.knackling.data.Project
import app.knackling.data.Session
import app.knackling.data.SettingsStore
import app.knackling.domain.Book
import app.knackling.domain.Decors
import app.knackling.domain.Lang
import app.knackling.domain.Quests
import app.knackling.domain.Say
import app.knackling.domain.dayOf
import app.knackling.domain.today
import app.knackling.notify.LiveNote
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random

class AppContainer(context: Context) {
    val app: Context = context.applicationContext
    val db = Db.create(app)
    val dao = db.dao()
    val settings = SettingsStore(app)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val news = MutableSharedFlow<String>(extraBufferCapacity = 8)
    private val lock = Mutex()
    /** The day the workshop lives by; the demo moves it while seeding. */
    @Volatile var clock: () -> Long = { today() }

    fun write(block: suspend AppContainer.() -> Unit) { scope.launch { block() } }
    fun say(s: String) { news.tryEmit(s) }

    suspend fun book(): Book = Book(dao.hobbyList(), dao.sessionList(), dao.projectList(), settings.now(), clock())

    // ---------- hobbies ----------

    suspend fun saveHobby(h: Hobby): Long = lock.withLock {
        if (h.id == 0L) {
            val order = (dao.hobbyList().maxOfOrNull { it.order } ?: -1) + 1
            dao.insert(h.copy(order = order, shiny = Random.nextInt(40) == 0))
        } else { dao.update(h); h.id }
    }
    suspend fun archive(h: Hobby, on: Boolean) = lock.withLock { dao.update(h.copy(archived = on)) }
    suspend fun deleteHobby(id: Long) = lock.withLock {
        dao.sessionList().filter { it.hobbyId == id }.forEach { Photos.delete(app, it.photo) }
        dao.projectList().filter { it.hobbyId == id }.forEach { Photos.delete(app, it.photo) }
        dao.deleteSessionsOf(id); dao.deleteProjectsOf(id); dao.deleteHobby(id)
        settings.update { it.copy(stage = it.stage - id, live = it.live?.takeIf { l -> l.hobbyId != id }) }
    }

    // ---------- sessions ----------

    suspend fun saveSession(s: Session): Long = lock.withLock {
        val fixed = s.copy(day = dayOf(s.start), minutes = s.minutes.coerceIn(1, 24 * 60))
        if (s.id == 0L) dao.insert(fixed) else { dao.update(fixed); s.id }
    }
    suspend fun deleteSession(s: Session) = lock.withLock { Photos.delete(app, s.photo); dao.deleteSession(s.id) }

    /** Starts the clock on a hobby (another running session is dropped into the past as it is). */
    suspend fun startLive(hobbyId: Long, idea: Boolean = false) {
        settings.update { it.copy(live = Live(hobbyId, System.currentTimeMillis(), idea = idea)) }
        LiveNote.show(app)
    }
    suspend fun pauseLive(on: Boolean) {
        settings.update { s -> val l = s.live ?: return@update s
            val now = System.currentTimeMillis()
            s.copy(live = if (on && !l.paused) l.copy(banked = l.ms(now), paused = true) else if (!on && l.paused) l.copy(since = now, paused = false) else l) }
        LiveNote.show(app)
    }
    suspend fun stopLive() { settings.update { it.copy(live = null) }; LiveNote.hide(app) }

    // ---------- projects ----------

    suspend fun saveProject(p: Project): Long = lock.withLock { if (p.id == 0L) dao.insert(p) else { dao.update(p); p.id } }
    suspend fun deleteProject(p: Project) = lock.withLock { Photos.delete(app, p.photo); dao.unlinkProject(p.id); dao.deleteProject(p.id) }

    // ---------- quests, the shop ----------

    suspend fun claim(key: String, reward: Int) = settings.update { if (key in it.claimed) it else it.copy(claimed = it.claimed + key, bonus = it.bonus + reward) }

    suspend fun buy(id: String): Boolean = lock.withLock {
        val d = Decors.byId[id] ?: return@withLock false
        val b = book()
        if (id in b.s.items || b.sparks < d.price || b.level < d.level) return@withLock false
        settings.update { it.copy(spent = it.spent + d.price, items = it.items + id) }
        true
    }
    suspend fun shine(h: Hobby): Boolean = lock.withLock {
        if (h.shiny || book().sparks < Decors.SHINY) return@withLock false
        settings.update { it.copy(spent = it.spent + Decors.SHINY) }
        dao.update(h.copy(shiny = true)); true
    }
    suspend fun anotherIdea(h: Hobby) = settings.update { s ->
        val t = clock().toInt(); val cur = s.ideaSkip[h.id]?.takeIf { it / 10 == t }?.rem(10) ?: 0
        s.copy(ideaSkip = s.ideaSkip + (h.id to t * 10 + (cur + 1) % 10))
    }

    suspend fun told(stage: Pair<Long, Int>? = null, badges: Set<String>? = null) = settings.update {
        it.copy(stages = if (stage != null) it.stages + stage else it.stages, badges = if (badges != null) it.badges + badges else it.badges)
    }

    // ---------- a copy in a file ----------

    suspend fun backup(): Backup = lock.withLock { Backup(dao.hobbyList(), dao.sessionList(), dao.projectList(), settings.now()) }

    /** Everything here gives way to what the file holds: all of it, or, if something fails, none of it. */
    suspend fun restore(b: Backup) = lock.withLock {
        db.withTransaction {
            dao.clearSessions(); dao.clearProjects(); dao.clearHobbies()
            dao.insertHobbies(b.hobbies); dao.insertSessions(b.sessions); dao.insertProjects(b.projects)
        }
        Backup.unpackPhotos(app, b)
        settings.update { b.settings.copy(live = null) }
        Say.fem = b.settings.fem
        LiveNote.hide(app)
    }
}

class KnacklingApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        Lang.ru = resources.configuration.locales[0].language == "ru"
        container = AppContainer(this)
        container.scope.launch { Say.fem = container.settings.now().fem }
    }
}

val Context.container: AppContainer get() = (applicationContext as KnacklingApp).container
