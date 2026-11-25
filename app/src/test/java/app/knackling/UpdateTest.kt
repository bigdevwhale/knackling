package app.knackling

import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import app.knackling.data.Backup
import app.knackling.data.Db
import app.knackling.data.Hobby
import app.knackling.data.Project
import app.knackling.data.Settings
import app.knackling.domain.Book
import app.knackling.domain.Catalog
import app.knackling.domain.Kinds
import app.knackling.domain.Quests
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 1.1: the database moves to version 2; projects in stages, with fractions, in the plans; hobbies taken twice. */
@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34])
class UpdateTest {
    private val day = 20_000L // a Wednesday

    @org.junit.Before fun russian() { app.knackling.domain.Lang.ru = true }

    @Test fun migratesVersionOne() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val file = ctx.getDatabasePath("knackling.db"); file.parentFile?.mkdirs(); file.delete()
        // a database as 1.0 left it (the SQL of app/schemas/…/1.json)
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL("CREATE TABLE IF NOT EXISTS `Hobby` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `species` TEXT NOT NULL, `title` TEXT NOT NULL, `name` TEXT NOT NULL, `since` INTEGER NOT NULL, `goal` INTEGER NOT NULL, `unit` TEXT NOT NULL, `order` INTEGER NOT NULL, `archived` INTEGER NOT NULL, `shiny` INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `Session` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `hobbyId` INTEGER NOT NULL, `start` INTEGER NOT NULL, `day` INTEGER NOT NULL, `minutes` INTEGER NOT NULL, `amount` INTEGER NOT NULL, `flow` INTEGER NOT NULL, `note` TEXT NOT NULL, `photo` TEXT NOT NULL, `projectId` INTEGER NOT NULL, `idea` INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `Project` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `hobbyId` INTEGER NOT NULL, `title` TEXT NOT NULL, `started` INTEGER NOT NULL, `done` INTEGER NOT NULL, `progress` INTEGER NOT NULL, `photo` TEXT NOT NULL, `note` TEXT NOT NULL)")
            db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'f4bac679e593f0706310dd8bd1370a04')")
            db.execSQL("INSERT INTO Hobby VALUES (1, 'embroidery', '', 'Крестик', $day, 120, '', 0, 0, 0)")
            db.execSQL("INSERT INTO Project VALUES (5, 1, 'Маки', $day, 0, 35, 'p.jpg', 'мулине')")
            db.version = 1
        }
        val db = Db.create(ctx)
        val (h, p) = runBlocking { db.dao().hobbyList() to db.dao().projectList() }
        db.close()
        assertEquals("", h.single().variant)
        assertEquals("Вышивка", h.single().label)
        val pr = p.single()
        assertEquals(Project(id = 5, hobbyId = 1, title = "Маки", started = day, progress = 35.0, photo = "p.jpg", note = "мулине"), pr)
        assertTrue(pr.going)
    }

    @Test fun stagesAndFractions() {
        val p = Project(id = 1, hobbyId = 1, title = "Месяц рисунков", started = day).withSteps(31)
        assertEquals("0".repeat(31), p.marks)
        val t = p.toggle(0).toggle(4).toggle(30)
        assertEquals(3, t.stepsDone); assertTrue(t.marked(4)); assertFalse(t.marked(1))
        assertEquals(300.0 / 31, t.pct, 1e-9)
        assertEquals(2, t.toggle(4).stepsDone)
        // fewer stages keep the first marks
        assertEquals("10001", t.withSteps(5).marks)
        assertEquals(12.5, Project(id = 2, hobbyId = 1, title = "x", started = day, progress = 12.5).pct, 0.0)
    }

    @Test fun plannedProjectsWait() {
        val h = Hobby(id = 1, species = "embroidery", name = "Крестик", since = day)
        val kit = Project(id = 1, hobbyId = 1, title = "Набор «Маки»", started = day, planned = true)
        assertFalse(kit.going)
        // with only a planned project, «move a project on» is not a task to give
        val b = Book(listOf(h), emptyList(), listOf(kit), Settings(), day)
        assertFalse(Quests.byId["project"]!!.fits(b))
        assertTrue(Quests.byId["project"]!!.fits(Book(listOf(h), emptyList(), listOf(kit.copy(planned = false)), Settings(), day)))
    }

    @Test fun morningTaskOnlyOnDaysOff() {
        val h = Hobby(id = 1, species = "reading", name = "Закладка", since = day - 400)
        val morning = Quests.byId["morning"]!!
        val weekdays = (0L until 7L).filter { !Book(listOf(h), emptyList(), emptyList(), Settings(), day + it).weekend }
        assertEquals(5, weekdays.size)
        (0L until 7L).forEach { d ->
            val b = Book(listOf(h), emptyList(), emptyList(), Settings(), day + d)
            assertEquals(b.weekend, morning.fits(b))
            if (!b.weekend) assertFalse(Quests.today(b).any { it.id == "morning" })
        }
    }

    @Test fun hobbyTakenTwiceAndSatinStitch() {
        val en = Hobby(id = 1, species = "languages", title = "Английский", name = "Болтушка", since = day)
        val es = Hobby(id = 2, species = "languages", title = "Испанский", name = "Ола", since = day)
        assertEquals("Испанский", es.label); assertTrue(en.label != es.label)
        val satin = Hobby(id = 3, species = "embroidery", name = "Гладь", since = day, variant = "satin")
        val cross = Hobby(id = 4, species = "embroidery", name = "Крестик", since = day, variant = "cross")
        assertEquals("Вышивка гладью", satin.label); assertEquals("Вышивка крестиком", cross.label)
        val b = Book(listOf(satin, cross), emptyList(), emptyList(), Settings(), day)
        assertEquals(Kinds.variant("embroidery", "satin")!!.ideas, b.ideas(satin))
        assertEquals(Kinds.of("embroidery")!!.ideas, b.ideas(cross))
        assertTrue(b.ideas(satin).none { "крестик" in it.ru })
        assertTrue(Catalog.kinds.all { k -> k.variants.drop(1).all { !it.ideas.isNullOrEmpty() } })
    }

    @Test fun backupKeepsTheNewThings() {
        val h = Hobby(id = 3, species = "embroidery", name = "Гладь", since = day, variant = "satin", title = "Гладь")
        val ps = listOf(Project(id = 1, hobbyId = 3, title = "Маки", started = day, progress = 12.75),
            Project(id = 2, hobbyId = 3, title = "Месяц", started = day).withSteps(4).toggle(1),
            Project(id = 3, hobbyId = 3, title = "Набор", started = day, planned = true))
        val r = Backup.of(Backup(listOf(h), emptyList(), ps, Settings(onboarded = true)).json().toString())
        assertEquals(listOf(h), r.hobbies); assertEquals(ps, r.projects)
        // a 1.0 file: whole percents, nothing more
        val old = """{"format":"knackling","version":1,"hobbies":[{"id":3,"species":"reading","name":"З","since":$day}],
            "projects":[{"id":1,"hobbyId":3,"title":"Книга","started":$day,"progress":40}]}"""
        val o = Backup.of(old)
        assertEquals("", o.hobbies[0].variant); assertEquals(40.0, o.projects[0].pct, 0.0); assertFalse(o.projects[0].planned)
    }
}
