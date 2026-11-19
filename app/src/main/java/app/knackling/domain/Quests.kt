package app.knackling.domain

import app.knackling.data.Session

/** A small task of the day or the week: done by itself when the sessions show it; claimed for sparks by a tap. */
class Quest(val id: String, val weekly: Boolean, val ru: String, val en: String, val reward: Int, val fits: (Book) -> Boolean = { true },
            val done: (Book, List<Session>) -> Boolean) {
    val title get() = Say.g(tr(ru, en))
}

object Quests {
    const val CHEST_DAY = 10
    const val CHEST_WEEK = 30

    private val daily = listOf(
        Quest("min15", false, "Позанимайся хотя бы 15 минут", "Practise for at least 15 minutes", 5) { _, l -> l.sumOf { it.minutes } >= 15 },
        Quest("min45", false, "Набери 45 минут за день", "Reach 45 minutes today", 8) { _, l -> l.sumOf { it.minutes } >= 45 },
        Quest("photo", false, "Сфотографируй, что получилось", "Take a photo of your work", 5) { _, l -> l.any { it.photo.isNotEmpty() } },
        Quest("note", false, "Оставь заметку к занятию", "Leave a note on a session", 5) { _, l -> l.any { it.note.isNotBlank() } },
        Quest("idea", false, "Возьмись за затею дня", "Take on the challenge of the day", 8) { _, l -> l.any { it.idea >= 0 } },
        Quest("two", false, "Займись двумя разными хобби", "Do two different hobbies", 8, fits = { it.active.size >= 2 }) { _, l -> l.map { it.hobbyId }.distinct().size >= 2 },
        Quest("morning", false, "Позанимайся до полудня", "Practise before noon", 6) { b, l -> l.any { b.hourOf(it.start) < 12 } },
        Quest("flow", false, "Поймай поток: отметь занятие «в потоке»", "Catch the flow: mark a session «in the flow»", 5) { _, l -> l.any { it.flow == 3 } },
        Quest("project", false, "Продвинь любой проект", "Move any project on", 6, fits = { b -> b.projects.any { it.done == 0L } }) { _, l -> l.any { it.projectId > 0 } },
        Quest("count", false, "Запиши, сколько сделано: страницы, ряды, песни", "Log how much you did: pages, rows, songs", 5) { _, l -> l.any { it.amount > 0 } },
    )
    private val weekly = listOf(
        Quest("goal", true, "Выполни недельную цель любого хобби", "Meet any hobby's weekly goal", 15) { b, _ -> b.rhythm(b.week) },
        Quest("goals", true, "Выполни недельные цели всех хобби", "Meet every hobby's weekly goal", 25, fits = { it.active.size >= 2 }) { b, _ -> b.active.all { it.goal <= 0 || b.weekMin(it) >= it.goal } },
        Quest("five", true, "Пять занятий за неделю", "Five sessions this week", 15) { _, l -> l.size >= 5 },
        Quest("hours3", true, "Три часа за неделю", "Three hours this week", 20) { _, l -> l.sumOf { it.minutes } >= 180 },
        Quest("days4", true, "Занимайся в четыре разных дня", "Practise on four different days", 15) { _, l -> l.map { it.day }.distinct().size >= 4 },
        Quest("ideas3", true, "Три затеи за неделю", "Three challenges this week", 15) { _, l -> l.count { it.idea >= 0 } >= 3 },
        Quest("photos3", true, "Три фото работ за неделю", "Three photos of work this week", 15) { _, l -> l.count { it.photo.isNotEmpty() } >= 3 },
        Quest("three", true, "Займись тремя разными хобби", "Do three different hobbies", 20, fits = { it.active.size >= 3 }) { _, l -> l.map { it.hobbyId }.distinct().size >= 3 },
    )
    val every: List<Quest> = daily + weekly
    val byId: Map<String, Quest> = every.associateBy { it.id }

    private fun pick(pool: List<Quest>, b: Book, seed: Int, n: Int): List<Quest> {
        val fit = pool.filter { it.fits(b) }
        val r = Rand(seed); return fit.sortedBy { r.next() }.take(n)
    }
    fun today(b: Book) = pick(daily, b, b.today.toInt() * 31 + 7, 3)
    fun week(b: Book) = pick(weekly, b, b.week.toInt() * 17 + 3, 3)

    fun key(q: Quest, b: Book) = if (q.weekly) "w:${b.week}:${q.id}" else "d:${b.today}:${q.id}"
    fun chestKey(weekly: Boolean, b: Book) = if (weekly) "w:${b.week}:chest" else "d:${b.today}:chest"
    fun isDone(q: Quest, b: Book) = q.done(b, if (q.weekly) b.sessions.filter { it.day >= b.week } else b.sessions.filter { it.day == b.today })
    fun claimed(q: Quest, b: Book) = key(q, b) in b.s.claimed
    /** Quests done and not yet claimed, for the red dot. */
    fun waiting(b: Book) = (today(b) + week(b)).count { isDone(it, b) && !claimed(it, b) } +
        listOf(false, true).count { w -> val l = if (w) week(b) else today(b); l.all { claimed(it, b) } && chestKey(w, b) !in b.s.claimed }
}

/** A decoration for the workshop. [x], [y] place its bottom centre on the scene (fractions); [w] its width. */
data class Decor(val id: String, val ru: String, val en: String, val price: Int, val level: Int, val x: Float, val y: Float, val w: Float, val back: Boolean = false) {
    val title get() = tr(ru, en)
}

object Decors {
    val all = listOf(
        Decor("plant", "Монстера в кадке", "Potted monstera", 60, 1, .06f, .995f, .15f),
        Decor("lamp", "Торшер", "Floor lamp", 80, 1, .05f, .665f, .10f, back = true),
        Decor("mannequin", "Манекен", "Dress form", 160, 3, .335f, .77f, .11f, back = true),
        Decor("easel", "Мольберт с пейзажем", "Easel with a landscape", 140, 2, .665f, .775f, .13f, back = true),
        Decor("gramophone", "Граммофон", "Gramophone", 180, 4, .935f, .995f, .13f),
        Decor("wheel", "Прялка", "Spinning wheel", 220, 5, .285f, .995f, .14f),
        Decor("yarn", "Корзина пряжи", "Basket of yarn", 50, 1, .40f, .995f, .10f),
        Decor("armchair", "Лоскутное кресло", "Patchwork armchair", 120, 2, .70f, .995f, .14f),
        Decor("clock", "Часы с кукушкой", "Cuckoo clock", 120, 2, .265f, .37f, .075f, back = true),
        Decor("globe", "Глобус", "Globe", 100, 2, .50f, .69f, .075f, back = true),
        Decor("fireflies", "Банка светлячков", "Jar of fireflies", 90, 3, .72f, .357f, .06f, back = true),
        Decor("fish", "Аквариум с рыбкой", "Goldfish bowl", 110, 3, .31f, .357f, .08f, back = true),
        Decor("lights", "Гирлянда", "Fairy lights", 70, 1, .50f, .125f, .40f, back = true),
        Decor("books", "Книжный шкафчик", "Little bookcase", 150, 4, .56f, .995f, .11f),
        Decor("cattree", "Домик-когтеточка", "Cat tree", 240, 6, .17f, .995f, .12f),
        Decor("kettle", "Чайник на печурке", "Kettle on a stove", 130, 5, .83f, .995f, .10f),
    )
    val byId = all.associateBy { it.id }
    /** A potion that turns an apprentice's colours rare. */
    const val SHINY = 300
}
