package app.knackling.domain

import app.knackling.data.Hobby
import app.knackling.data.Project
import app.knackling.data.Session
import app.knackling.data.Settings
import java.time.Instant
import java.time.LocalDate
import kotlin.math.abs

/** How an apprentice grows: experience from time at the hobby and from the little extras. */
object Grow {
    /**
     * Experience for the ranks 0 hatchling … 9 legend. Experience is about a minute of practice plus small extras,
     * so the ladder runs from the first session to the famous ten thousand hours.
     */
    val steps = intArrayOf(0, 180, 900, 3000, 9000, 30_000, 60_000, 150_000, 300_000, 600_000)
    val TOP = steps.size - 1
    /** The ranks whose apprentices wear the golden glow. */
    const val GLOW = 5
    const val PROJECT = 100

    fun xp(s: Session): Int = s.minutes + 10 + (if (s.photo.isNotEmpty()) 15 else 0) + (if (s.note.isNotBlank()) 5 else 0) +
        (if (s.idea >= 0) 20 else 0) + (if (s.flow == 3) 10 else 0)

    /** −1 is still an egg (nothing done yet). */
    fun stage(xp: Int, sessions: Int): Int = if (sessions == 0) -1 else steps.indexOfLast { xp >= it }

    /** How far to the next rank, 0..1 (1 at the last). */
    fun progress(xp: Int, stage: Int): Float = if (stage < 0) 0f else if (stage >= TOP) 1f
        else ((xp - steps[stage]).toFloat() / (steps[stage + 1] - steps[stage])).coerceIn(0f, 1f)

    /** Roughly how many hours of practice a rank takes. */
    fun hours(stage: Int): Int = if (stage <= 0) 0 else steps[stage] / 60

    fun name(stage: Int): String = when (stage) {
        -1 -> tr("Яйцо", "Egg"); 0 -> tr("Малыш", "Hatchling"); 1 -> tr("Ученик", "Pupil"); 2 -> tr("Подмастерье", "Apprentice")
        3 -> tr("Мастер", "Master"); 4 -> tr("Великий мастер", "Grand master"); 5 -> tr("Мастер цеха", "Guild master")
        6 -> tr("Магистр", "Magister"); 7 -> tr("Виртуоз", "Virtuoso"); 8 -> tr("Гроссмейстер", "Grandmaster"); else -> tr("Легенда", "Legend")
    }
    fun about(stage: Int): String = when (stage) {
        -1 -> tr("Вылупится после первого занятия", "Hatches after the first session")
        0 -> tr("Только вылупился и смотрит, как ты работаешь", "Just hatched and watches you work")
        1 -> tr("Подаёт инструменты и запоминает приёмы", "Hands you the tools and learns the moves")
        2 -> tr("Уже берётся за дело сам", "Starts on the work by itself")
        3 -> tr("Работает уверенно, есть чем гордиться", "Works with confidence, has things to be proud of")
        4 -> tr("Полторы сотни часов за плечами", "A hundred and fifty hours behind you")
        5 -> tr("Пятьсот часов. Цех принимает в свои ряды", "Five hundred hours. The guild takes you in")
        6 -> tr("Тысяча часов – это уже твоё ремесло", "A thousand hours: this is your craft now")
        7 -> tr("Две с половиной тысячи часов. Руки всё делают сами", "Two and a half thousand hours. The hands know it all")
        8 -> tr("Пять тысяч часов. Таких мастеров немного", "Five thousand hours. Few makers get this far")
        else -> tr("Десять тысяч часов. Легенда мастерской", "Ten thousand hours. A legend of the workshop")
    }
}

/** How an apprentice feels, from the days since its hobby was last done. */
enum class Mood { WORKING, HAPPY, CALM, MISSING, ASLEEP;
    val text get() = when (this) {
        WORKING -> tr("за работой", "at work"); HAPPY -> tr("доволен сегодняшним", "happy with today")
        CALM -> tr("спокоен", "content"); MISSING -> tr("скучает", "misses you"); ASLEEP -> tr("задремал", "dozed off")
    }
}

/** The workshop's level from all the experience together. */
object Level {
    fun need(n: Int) = 400 * n * (n + 1) / 2   // experience to reach level n+1
    fun of(xp: Int): Int { var n = 0; while (xp >= need(n + 1)) n++; return n + 1 }
    fun progress(xp: Int): Float { val l = of(xp); val a = need(l - 1); val b = need(l); return ((xp - a).toFloat() / (b - a)).coerceIn(0f, 1f) }
    fun title(l: Int) = when {
        l <= 2 -> tr("Чулан", "Cupboard"); l <= 5 -> tr("Каморка", "Nook"); l <= 9 -> tr("Мастерская", "Workshop")
        l <= 14 -> tr("Студия", "Studio"); l <= 19 -> tr("Ателье", "Atelier"); else -> tr("Гильдия", "Guild")
    }
}

/**
 * Everything worked out from the hobbies, the sessions and the projects, once per change.
 * Nothing here is stored: the past is the sessions themselves.
 */
class Book(val hobbies: List<Hobby>, val sessions: List<Session>, val projects: List<Project>, val s: Settings, val today: Long,
           val now: Long = System.currentTimeMillis()) {
    val active = hobbies.filter { !it.archived }
    val byId = hobbies.associateBy { it.id }
    /** Each hobby's sessions, newest first. */
    val of: Map<Long, List<Session>> = sessions.groupBy { it.hobbyId }
    private val projectsDone = projects.filter { it.done > 0 }

    val xp: Map<Long, Int> = hobbies.associate { h -> h.id to ((of[h.id]?.sumOf { Grow.xp(it) } ?: 0) + projectsDone.count { it.hobbyId == h.id } * Grow.PROJECT) }
    fun stage(h: Hobby) = Grow.stage(xp[h.id] ?: 0, of[h.id]?.size ?: 0)
    fun progress(h: Hobby) = Grow.progress(xp[h.id] ?: 0, stage(h))
    /** Experience still to go to the next stage, or 0. */
    fun toNext(h: Hobby): Int { val st = stage(h); return if (st !in 0 until Grow.TOP) 0 else Grow.steps[st + 1] - (xp[h.id] ?: 0) }

    val totalXp = xp.values.sum()
    val level = Level.of(totalXp)

    fun minutes(h: Hobby? = null, from: Long = Long.MIN_VALUE, to: Long = Long.MAX_VALUE): Int =
        (if (h == null) sessions else of[h.id].orEmpty()).filter { it.day in from..to }.sumOf { it.minutes }
    val week = weekStart(today)
    /** Saturday or Sunday: a free morning. */
    val weekend: Boolean get() = LocalDate.ofEpochDay(today).dayOfWeek.value >= 6
    fun weekMin(h: Hobby, w: Long = week) = minutes(h, w, w + 6)
    fun lastDay(h: Hobby): Long? = of[h.id]?.maxOfOrNull { it.day }

    fun mood(h: Hobby): Mood {
        if (s.live?.hobbyId == h.id) return Mood.WORKING
        val last = lastDay(h) ?: return Mood.CALM
        val gap = today - last
        return when { gap == 0L -> Mood.HAPPY; gap <= 3 -> Mood.CALM; gap <= 13 -> Mood.MISSING; else -> Mood.ASLEEP }
    }

    /** Days in a row with any session, ending today or yesterday. */
    val dayStreak: Int = run {
        val days = sessions.map { it.day }.toHashSet()
        var d = if (today in days) today else today - 1; var n = 0
        while (d in days) { n++; d-- }; n
    }
    val bestDayStreak: Int = run {
        val days = sessions.map { it.day }.toSortedSet(); var best = 0; var run = 0; var prev = Long.MIN_VALUE
        for (d in days) { run = if (d == prev + 1) run + 1 else 1; best = maxOf(best, run); prev = d }; best
    }

    /** A week is «in rhythm» when at least one hobby met its weekly goal. */
    fun rhythm(w: Long) = active.any { it.goal > 0 && weekMin(it, w) >= it.goal }
    val weekStreak: Int = run {
        var w = if (rhythm(week)) week else week - 7; var n = 0
        val first = hobbies.minOfOrNull { it.since } ?: today
        while (w >= weekStart(first) - 7 && rhythm(w)) { n++; w -= 7 }; n
    }

    // ---------- sparks ----------
    val photos = sessions.count { it.photo.isNotEmpty() }
    val ideasDone = sessions.count { it.idea >= 0 }
    val badges: List<Badge> by lazy { Badges.earned(this) }
    val earned: Int by lazy { sessions.sumOf { it.minutes } / 15 + sessions.size + photos * 2 + ideasDone * 3 + projectsDone.size * 10 + s.bonus + badges.size * 5 }
    val sparks: Int get() = earned - s.spent

    // ---------- the idea of the day ----------
    fun ideas(h: Hobby): List<Idea> = if (h.own) Catalog.ownIdeas else Kinds.variant(h.species, h.variant)?.ideas ?: Kinds.of(h.species)?.ideas ?: Catalog.ownIdeas
    fun ideaIndex(h: Hobby): Int {
        val skip = s.ideaSkip[h.id]?.takeIf { it / 10 == today.toInt() }?.rem(10) ?: 0
        return abs(hash("${h.id}:$today") + skip) % ideas(h).size
    }
    fun idea(h: Hobby): Idea = ideas(h)[ideaIndex(h)]
    fun ideaDone(h: Hobby) = of[h.id].orEmpty().any { it.day == today && it.idea == ideaIndex(h) }

    /** The hobbies whose apprentices sit in the workshop: chosen ones, else the most recently done (up to five). */
    val onStage: List<Hobby> = run {
        val chosen = s.stage.mapNotNull { id -> active.firstOrNull { it.id == id } }
        if (chosen.isNotEmpty()) chosen.take(5) else active.sortedByDescending { lastDay(it) ?: it.since }.take(5)
    }

    fun projectsOf(h: Hobby) = projects.filter { it.hobbyId == h.id }
    fun hourOf(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).hour
    val firstDay: Long = minOf(hobbies.minOfOrNull { it.since } ?: today, sessions.minOfOrNull { it.day } ?: today)
    fun date(day: Long): LocalDate = LocalDate.ofEpochDay(day)
}
