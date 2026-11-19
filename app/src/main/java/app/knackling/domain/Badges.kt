package app.knackling.domain

/**
 * Embroidered patches for the apron: ladders of the same thing at growing heights.
 * [patch] is the picture (patch_0..15); a badge id is "<ladder>:<goal>".
 */
class Ladder(val id: String, val patch: Int, val ru: String, val en: String, val goals: List<Int>, val unitRu: Triple<String, String, String>, val unitEn: Pair<String, String>,
             val value: (Book) -> Int) {
    val title get() = tr(ru, en)
    fun what(goal: Int) = count(goal, unitRu.first, unitRu.second, unitRu.third, unitEn.first, unitEn.second)
}

data class Badge(val ladder: Ladder, val tier: Int, val goal: Int) {
    val id get() = "${ladder.id}:$goal"
    val title get() = ladder.title + " " + roman(tier + 1)
}

private fun roman(n: Int) = listOf("I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X").getOrElse(n - 1) { "$n" }

object Badges {
    private val HOURS = Triple("час", "часа", "часов") to ("hour" to "hours")
    val ladders = listOf(
        Ladder("hours", 10, "Часы мастерства", "Hours of craft", listOf(1, 10, 25, 50, 100, 250, 500, 1000), HOURS.first, HOURS.second) { it.sessions.sumOf { s -> s.minutes } / 60 },
        Ladder("sessions", 1, "Занятия", "Sessions", listOf(1, 10, 25, 50, 100, 250, 500, 1000), Triple("занятие", "занятия", "занятий"), "session" to "sessions") { it.sessions.size },
        Ladder("streak", 0, "Огонёк", "Spark streak", listOf(3, 7, 14, 30, 60, 100, 200, 365), Triple("день подряд", "дня подряд", "дней подряд"), "day in a row" to "days in a row") { it.bestDayStreak },
        Ladder("rhythm", 3, "Ритм", "Rhythm", listOf(2, 4, 8, 12, 26, 52), Triple("неделя в ритме", "недели в ритме", "недель в ритме"), "week in rhythm" to "weeks in rhythm") { bestRhythm(it) },
        Ladder("hobbies", 12, "Многогранность", "Many sides", listOf(2, 3, 5, 8, 12), Triple("хобби", "хобби", "хобби"), "hobby" to "hobbies") { b -> b.hobbies.count { b.of[it.id] != null } },
        Ladder("projects", 14, "Готовые работы", "Finished works", listOf(1, 3, 5, 10, 25, 50), Triple("работа", "работы", "работ"), "work" to "works") { b -> b.projects.count { it.done > 0 } },
        Ladder("photos", 13, "Витрина", "Showcase", listOf(1, 10, 25, 50, 100, 250), Triple("фото", "фото", "фото"), "photo" to "photos") { it.photos },
        Ladder("ideas", 9, "Затейник", "Up for it", listOf(1, 5, 15, 30, 60, 100), Triple("затея", "затеи", "затей"), "challenge" to "challenges") { it.ideasDone },
        Ladder("flow", 7, "Поток", "Flow", listOf(1, 10, 25, 50, 100), Triple("занятие в потоке", "занятия в потоке", "занятий в потоке"), "session in flow" to "sessions in flow") { b -> b.sessions.count { it.flow == 3 } },
        Ladder("notes", 11, "Летописец", "Chronicler", listOf(1, 10, 30, 100), Triple("заметка", "заметки", "заметок"), "note" to "notes") { b -> b.sessions.count { it.note.isNotBlank() } },
        Ladder("owl", 2, "Ночная сова", "Night owl", listOf(1, 10, 30), Triple("поздний вечер", "поздних вечера", "поздних вечеров"), "late evening" to "late evenings") { b -> b.sessions.count { b.hourOf(it.start) >= 22 || b.hourOf(it.start) < 4 } },
        Ladder("lark", 5, "Ранняя пташка", "Early bird", listOf(1, 10, 30), Triple("раннее утро", "ранних утра", "ранних утр"), "early morning" to "early mornings") { b -> b.sessions.count { b.hourOf(it.start) in 4..7 } },
        Ladder("marathon", 4, "Марафон", "Marathon", listOf(1, 5, 20), Triple("занятие от 2 часов", "занятия от 2 часов", "занятий от 2 часов"), "2-hour session" to "2-hour sessions") { b -> b.sessions.count { it.minutes >= 120 } },
        Ladder("masters", 8, "Наставник", "Mentor", listOf(1, 3, 5, 10), Triple("мастер", "мастера", "мастеров"), "master" to "masters") { b -> b.hobbies.count { b.stage(it) >= 3 } },
        Ladder("hatched", 6, "Семья", "Family", listOf(1, 3, 5, 10, 20), Triple("подмастерье", "подмастерья", "подмастерьев"), "apprentice" to "apprentices") { b -> b.hobbies.count { b.stage(it) >= 0 } },
        Ladder("allround", 15, "На все руки", "Jack of all trades", listOf(1, 10, 30, 100), Triple("день с двумя хобби", "дня с двумя хобби", "дней с двумя хобби"), "day of two hobbies" to "days of two hobbies") { b ->
            b.sessions.groupBy { it.day }.count { (_, l) -> l.map { it.hobbyId }.distinct().size >= 2 } },
    )

    val all: List<Badge> = ladders.flatMap { l -> l.goals.mapIndexed { i, g -> Badge(l, i, g) } }
    val byId = all.associateBy { it.id }

    fun earned(b: Book): List<Badge> = ladders.flatMap { l -> val v = l.value(b); l.goals.mapIndexedNotNull { i, g -> if (v >= g) Badge(l, i, g) else null } }

    /** Ladders with the next badge on each and how far it is (for «almost there»). */
    fun next(b: Book): List<Triple<Ladder, Int, Float>> = ladders.mapNotNull { l ->
        val v = l.value(b); val g = l.goals.firstOrNull { it > v } ?: return@mapNotNull null
        val prev = l.goals.lastOrNull { it <= v } ?: 0
        Triple(l, g, ((v - prev).toFloat() / (g - prev)).coerceIn(0f, 1f))
    }

    private fun bestRhythm(b: Book): Int {
        if (b.active.isEmpty()) return 0
        var w = weekStart(b.firstDay); var best = 0; var run = 0
        while (w <= b.week) { if (b.rhythm(w)) { run++; best = maxOf(best, run) } else if (w != b.week) run = 0; w += 7 }
        return best
    }
}
