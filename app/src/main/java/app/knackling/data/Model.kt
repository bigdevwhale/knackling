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
) {
    val own get() = species.startsWith("own_")
    /** «Гитара» or the user's own words. */
    val label: String get() = title.ifBlank { Kinds.of(species)?.title ?: species }
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

/** A thing in the making: a scarf, a song to learn, a book. */
@Entity
data class Project(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val hobbyId: Long,
    val title: String,
    val started: Long,           // day
    val done: Long = 0,          // day finished, 0 = still going
    val progress: Int = 0,       // 0..100
    val photo: String = "",      // cover
    val note: String = "",
)

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

@Database(entities = [Hobby::class, Session::class, Project::class], version = 1, exportSchema = true)
abstract class Db : RoomDatabase() {
    abstract fun dao(): Shop

    companion object {
        fun create(ctx: Context) = Room.databaseBuilder(ctx, Db::class.java, "knackling.db").build()
    }
}
