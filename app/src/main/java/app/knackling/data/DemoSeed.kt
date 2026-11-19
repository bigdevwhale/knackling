package app.knackling.data

import app.knackling.AppContainer
import app.knackling.domain.Badges
import app.knackling.domain.Book
import app.knackling.domain.Lang
import app.knackling.domain.today
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random

/**
 * Four months of a maker's life for the screenshots (demo builds only): six hobbies at different ranks,
 * sessions with notes and photos, projects done and in the making, a few things in the workshop.
 */
object DemoSeed {
    private class Plan(val species: String, val ru: String, val en: String, val perWeek: Double, val min: IntRange, val since: Int, val hour: IntRange,
                       val title: String = "", val unit: String = "", val amount: IntRange = 0..0, val photos: List<String> = emptyList())

    suspend fun seedIfEmpty(c: AppContainer) {
        if (c.dao.hobbyList().isNotEmpty()) return
        val ru = Lang.ru
        val t = today()
        val r = Random(7)
        val plans = listOf(
            Plan("embroidery", "Крестик", "Stitch", 4.5, 25..80, 125, 19..22, amount = 2..9, photos = listOf("embroidery1", "embroidery2")),
            Plan("guitar", "Струнка", "Strummy", 3.5, 15..45, 110, 18..21, amount = 0..1, photos = listOf("guitar1")),
            Plan("reading", "Закладка", "Bookmark", 5.0, 15..50, 120, 21..23, amount = 15..60, photos = listOf("reading1")),
            Plan("watercolor", "Размывка", "Bloom", 1.6, 30..90, 70, 10..16, amount = 1..3, photos = listOf("watercolor1", "watercolor2")),
            Plan("baking", "Плюшка", "Bun", .9, 60..150, 95, 10..13, amount = 1..2, photos = listOf("baking1", "baking2")),
            Plan("yoga", "Асана", "Asana", 2.2, 15..35, 40, 7..8, photos = listOf("yoga1")),
            Plan("own_ember", "Рыжик", "Ember", .8, 40..100, 25, 15..19, title = if (ru) "Макраме" else "Macramé", unit = if (ru) ":узлов" else ":knots", amount = 20..80, photos = listOf("macrame1")),
        )
        val notesRu = listOf("Наконец-то пошло!", "Сбилась со счёта, пришлось распускать", "Нашла классный урок на ютубе", "Сегодня просто для души",
            "Руки помнят лучше головы", "Устала, но довольна", "Попробовала новый приём – получилось с третьего раза", "Вечер с чаем и делом – лучший вечер")
        val notesEn = listOf("Finally clicked!", "Lost count, had to rip back", "Found a great lesson online", "Just for the joy today",
            "Hands remember better than the head", "Tired but happy", "Tried a new trick – third time lucky", "An evening of tea and craft is the best evening")
        val photoDir = Photos.dir(c.app)
        fun photo(name: String): String {
            val f = java.io.File(photoDir, "demo-$name.jpg")
            if (!f.exists()) runCatching { c.app.assets.open("photos/$name.jpg").use { inp -> f.outputStream().use { inp.copyTo(it) } } }
            return if (f.exists()) f.name else ""
        }
        val ids = HashMap<String, Long>()
        plans.forEachIndexed { i, p ->
            ids[p.species] = c.dao.insert(Hobby(species = p.species, title = p.title, name = if (ru) p.ru else p.en, since = t - p.since,
                goal = listOf(180, 120, 210, 120, 120, 90, 120)[i], unit = p.unit, order = i, shiny = false))
        }
        // projects
        val proj = HashMap<String, Long>()
        suspend fun project(sp: String, ru1: String, en1: String, started: Int, done: Int, progress: Int, cover: String = "") {
            proj["$sp:$ru1"] = c.dao.insert(Project(hobbyId = ids[sp]!!, title = if (ru) ru1 else en1, started = t - started, done = if (done > 0) t - done else 0, progress = progress,
                photo = if (cover.isNotEmpty()) photo(cover) else ""))
        }
        project("embroidery", "Лис в папоротнике", "Fox in the ferns", 60, 0, 65)
        project("embroidery", "Полевые цветы", "Wildflowers", 120, 70, 100, "embroidery2")
        project("guitar", "«Звезда по имени Солнце»", "«Wonderwall»", 40, 0, 40)
        project("reading", "«Мастер и Маргарита»", "«The Name of the Wind»", 30, 0, 70)
        project("watercolor", "Озёра Карелии", "Lakes of the North", 50, 8, 100, "watercolor1")
        project("baking", "Идеальная чиабатта", "The perfect ciabatta", 80, 0, 55)
        // sessions
        val zone = ZoneId.systemDefault()
        val all = ArrayList<Session>()
        plans.forEach { p ->
            val hid = ids[p.species]!!
            val projs = proj.filterKeys { it.startsWith(p.species + ":") }.values.toList()
            var photosLeft = p.photos.toMutableList()
            for (back in p.since downTo 0) {
                val day = t - back
                val dow = LocalDate.ofEpochDay(day).dayOfWeek.value
                val chance = p.perWeek / 7.0 * (if (dow >= 6) 1.4 else .9) * (if (back < 30) 1.15 else 1.0)
                if (r.nextDouble() > chance) continue
                if (back == 0 && p.species !in setOf("embroidery", "yoga", "reading")) continue
                val hour = p.hour.random(r).coerceAtMost(if (back == 0) 9 else 23)
                val start = LocalDate.ofEpochDay(day).atTime(hour, r.nextInt(0, 59)).atZone(zone).toInstant().toEpochMilli()
                val minutes = p.min.random(r)
                val pic = if (photosLeft.isNotEmpty() && r.nextInt(6) == 0 && back < p.since - 10) photo(photosLeft.removeAt(0)) else ""
                all += Session(hobbyId = hid, start = start, day = day, minutes = minutes, amount = if (p.amount.last > 0) p.amount.random(r) else 0,
                    flow = listOf(0, 1, 2, 2, 3, 3, 3)[r.nextInt(7)], note = if (r.nextInt(5) == 0) (if (ru) notesRu else notesEn).random(r) else "",
                    photo = pic, projectId = if (projs.isNotEmpty() && r.nextInt(3) > 0) projs.random(r) else 0, idea = if (r.nextInt(6) == 0) r.nextInt(4) else -1)
            }
        }
        c.dao.insertSessions(all.sortedBy { it.start })
        // what was already celebrated, so the demo opens on the workshop, not on a pile of news
        val b = Book(c.dao.hobbyList(), c.dao.sessionList(), c.dao.projectList(), c.settings.now(), t)
        c.settings.update { s ->
            s.copy(onboarded = true, fem = ru, items = setOf("plant", "yarn", "lights", "clock", "armchair", "fish"), spent = 210,
                stages = b.hobbies.associate { it.id to b.stage(it) }, badges = Badges.earned(b).map { it.id }.toSet(),
                seen = setOf("i.home", "i.journal", "i.stats", "i.album", "i.hobby", "i.live", "i.shop"), bonus = 140)
        }
    }
}
