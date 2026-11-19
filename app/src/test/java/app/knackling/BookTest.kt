package app.knackling

import app.knackling.data.Backup
import app.knackling.data.Hobby
import app.knackling.data.Project
import app.knackling.data.Session
import app.knackling.data.Settings
import app.knackling.domain.Badges
import app.knackling.domain.Book
import app.knackling.domain.Grow
import app.knackling.domain.Quests
import app.knackling.domain.Say
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34])
class BookTest {
    private val day = 20_000L
    private fun s(h: Long, d: Long, m: Int, photo: String = "") = Session(hobbyId = h, start = d * 86_400_000L + 3_600_000L * 12, day = d, minutes = m, photo = photo)

    @Test fun eggHatchesAndGrows() {
        val h = Hobby(id = 1, species = "guitar", name = "Струнка", since = day - 30)
        assertEquals(-1, Book(listOf(h), emptyList(), emptyList(), Settings(), day).stage(h))
        val b = Book(listOf(h), listOf(s(1, day, 30)), emptyList(), Settings(), day)
        assertEquals(0, b.stage(h))
        val many = (0 until 20).map { s(1, day - it, 60) }
        assertEquals(2, Book(listOf(h), many, emptyList(), Settings(), day).stage(h))
    }

    @Test fun projectsGiveExperience() {
        val h = Hobby(id = 1, species = "knitting", name = "Петелька", since = day)
        val p = Project(id = 1, hobbyId = 1, title = "Шарф", started = day, done = day)
        val b = Book(listOf(h), listOf(s(1, day, 10)), listOf(p), Settings(), day)
        assertEquals(10 + 10 + Grow.PROJECT, b.xp[1L])
    }

    @Test fun streaksAndBadges() {
        val h = Hobby(id = 1, species = "reading", name = "Закладка", since = day - 10, goal = 60)
        val b = Book(listOf(h), (0..6).map { s(1, day - it, 30) }, emptyList(), Settings(), day)
        assertEquals(7, b.dayStreak)
        assertTrue(b.badges.any { it.id == "streak:7" })
        assertTrue(Badges.all.size > 60)
        assertEquals(3, Quests.today(b).size)
    }

    @Test fun genderMarks() {
        Say.fem = true; assertEquals("поняла", Say.g("понял{|а}")); assertEquals("остановилась", Say.g("остановил{ся|ась}"))
        Say.fem = false; assertEquals("понял", Say.g("понял{|а}"))
    }

    @Test fun backupRoundTrip() {
        val h = Hobby(id = 3, species = "own_ember", title = "Макраме", name = "Рыжик", since = day, unit = ":узлов")
        val b = Backup(listOf(h), listOf(s(3, day, 40, "abc123.jpg")), listOf(Project(id = 2, hobbyId = 3, title = "Кашпо", started = day)), Settings(onboarded = true))
        val r = Backup.of(b.json().toString())
        assertEquals("Макраме", r.hobbies[0].title); assertEquals("abc123.jpg", r.sessions[0].photo); assertEquals(1, r.projects.size)
    }
}
