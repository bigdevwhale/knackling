package app.knackling.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import app.knackling.domain.Kinds
import kotlinx.coroutines.flow.Flow

/**
 * A hobby in the workshop and its apprentice. [species] is a preset hobby id («guitar») or a free apprentice («own_ember»);
 * a preset hobby's [title] is empty (the catalogue names it), an own hobby's is the user's words.
 */
@Entity
data class Hobby(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val species: String,
    val title: String = "",
    val name: String,            // the apprentice's name
    val since: Long,             // the day it moved in
    val goal: Int = 120,         // minutes a week
    val unit: String = "",       // what is counted besides minutes: a key of Catalog.units, "" none, or own words after «:»
    val order: Int = 0,
    val archived: Boolean = false,
    val shiny: Boolean = false,
    val variant: String = "",    // a preset hobby's kind of it («satin» embroidery), "" the main one
) {
    val own get() = species.startsWith("own_")
    /** «Гитара», «Вышивка гладью» or the user's own words («Испанский» for a second «Languages»). */
    val label: String get() = title.ifBlank { Kinds.variant(species, variant)?.title ?: Kinds.of(species)?.title ?: species }
}

/** One time at the hobby. Minutes count; the rest is optional. */
@Entity
data class Session(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val hobbyId: Long,
    val start: Long,             // ms
    val day: Long,               // epoch day of [start]
    val minutes: Int,
    val amount: Int = 0,         // pages, rows, songs… in the hobby's unit
    val flow: Int = 0,           // 0 not said, 1 heavy going, 2 fine, 3 in the flow
    val note: String = "",
    val photo: String = "",      // a file name in filesDir/photos
    val projectId: Long = 0,
    val idea: Int = -1,          // the index of the idea tried, −1 none
)

/**
 * A thing in the making: a scarf, a song to learn, a book. Progress is either a percentage ([progress], fractions allowed)
 * or [steps] stages ticked one by one ([marks]: «1» for a stage done, «0» not yet), like a month's daily drawings.
 * A [planned] project waits for its turn (a kit put by); it starts when the user says so and [started] becomes that day.
 */
@Entity
data class Project(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val hobbyId: Long,
    val title: String,
    val started: Long,           // day (for a planned one, the day it was put down)
    val done: Long = 0,          // day finished, 0 = still going
    val progress: Double = 0.0,  // 0..100, in percent mode
    val photo: String = "",      // cover
    val note: String = "",
    val steps: Int = 0,          // 0 = percent mode, else the number of stages
    val marks: String = "",      // one character per stage
    val planned: Boolean = false,
) {
    /** Still in the making: neither finished nor waiting in the plans. */
    val going get() = done == 0L && !planned
    val staged get() = steps > 0
    fun marked(i: Int) = marks.getOrNull(i) == '1'
    val stepsDone get() = (0 until steps).count(::marked)
    /** How far along, 0..100, in either mode. */
    val pct: Double get() = if (staged) stepsDone * 100.0 / steps else progress
    fun toggle(i: Int): Project = copy(marks = (0 until steps).joinToString("") { j -> if (j == i) (if (marked(j)) "0" else "1") else (if (marked(j)) "1" else "0") })
    /** The same marks for another number of stages: the first ones kept, the rest not done. */
    fun withSteps(n: Int): Project = copy(steps = n, marks = (0 until n).joinToString("") { if (marked(it)) "1" else "0" })

    companion object { const val MAX_STEPS = 366 }
}

@Dao
interface Shop {
    @Query("SELECT * FROM Hobby ORDER BY `order`, id") fun hobbies(): Flow<List<Hobby>>
    @Query("SELECT * FROM Hobby ORDER BY `order`, id") suspend fun hobbyList(): List<Hobby>
    @Query("SELECT * FROM Session ORDER BY start DESC") fun sessions(): Flow<List<Session>>
    @Query("SELECT * FROM Session ORDER BY start DESC") suspend fun sessionList(): List<Session>
    @Query("SELECT * FROM Project ORDER BY started DESC, id DESC") fun projects(): Flow<List<Project>>
    @Query("SELECT * FROM Project ORDER BY started DESC, id DESC") suspend fun projectList(): List<Project>

    @Insert suspend fun insert(h: Hobby): Long
    @Update suspend fun update(h: Hobby)
    @Insert suspend fun insert(s: Session): Long
    @Update suspend fun update(s: Session)
    @Insert suspend fun insert(p: Project): Long
    @Update suspend fun update(p: Project)

    @Query("DELETE FROM Hobby WHERE id = :id") suspend fun deleteHobby(id: Long)
    @Query("DELETE FROM Session WHERE hobbyId = :id") suspend fun deleteSessionsOf(id: Long)
    @Query("DELETE FROM Project WHERE hobbyId = :id") suspend fun deleteProjectsOf(id: Long)
    @Query("DELETE FROM Session WHERE id = :id") suspend fun deleteSession(id: Long)
    @Query("DELETE FROM Project WHERE id = :id") suspend fun deleteProject(id: Long)
    @Query("UPDATE Session SET projectId = 0 WHERE projectId = :id") suspend fun unlinkProject(id: Long)

    @Query("DELETE FROM Hobby") suspend fun clearHobbies()
    @Query("DELETE FROM Session") suspend fun clearSessions()
    @Query("DELETE FROM Project") suspend fun clearProjects()
    @Insert suspend fun insertHobbies(l: List<Hobby>)
    @Insert suspend fun insertSessions(l: List<Session>)
    @Insert suspend fun insertProjects(l: List<Project>)
}

@Database(entities = [Hobby::class, Session::class, Project::class], version = 2, exportSchema = true)
abstract class Db : RoomDatabase() {
    abstract fun dao(): Shop

    companion object {
        fun create(ctx: Context) = Room.databaseBuilder(ctx, Db::class.java, "knackling.db").addMigrations(V2).build()

        /** 1.1: a hobby's variant; a project's fractional progress (the column becomes REAL), stages and plans. */
        val V2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `Hobby` ADD COLUMN `variant` TEXT NOT NULL DEFAULT ''")
                db.execSQL(PROJECT_V2)
                db.execSQL("INSERT INTO `Project_new` (`id`, `hobbyId`, `title`, `started`, `done`, `progress`, `photo`, `note`, `steps`, `marks`, `planned`) " +
                    "SELECT `id`, `hobbyId`, `title`, `started`, `done`, CAST(`progress` AS REAL), `photo`, `note`, 0, '', 0 FROM `Project`")
                db.execSQL("DROP TABLE `Project`")
                db.execSQL("ALTER TABLE `Project_new` RENAME TO `Project`")
            }
        }
        private const val PROJECT_V2 = "CREATE TABLE IF NOT EXISTS `Project_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `hobbyId` INTEGER NOT NULL, " +
            "`title` TEXT NOT NULL, `started` INTEGER NOT NULL, `done` INTEGER NOT NULL, `progress` REAL NOT NULL, `photo` TEXT NOT NULL, `note` TEXT NOT NULL, " +
            "`steps` INTEGER NOT NULL, `marks` TEXT NOT NULL, `planned` INTEGER NOT NULL)"
    }
}
