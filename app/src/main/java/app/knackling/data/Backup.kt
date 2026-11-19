package app.knackling.data

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** The file is not a copy of Knackling, or ([newer]) a later version of the app made it. */
class BadBackup(val newer: Boolean = false) : Exception()

/**
 * Everything the app keeps, as one zip: data.json and the photos. For moving to another phone or for keeping just in case.
 * Hobbies and projects keep their ids (sessions and settings point at them).
 */
class Backup(val hobbies: List<Hobby>, val sessions: List<Session>, val projects: List<Project>, val settings: Settings,
             val made: Long = System.currentTimeMillis(), val photos: Map<String, ByteArray> = emptyMap()) {

    fun json(): JSONObject = JSONObject().apply {
        put("format", FORMAT); put("version", VERSION); put("made", made)
        put("hobbies", JSONArray(hobbies.map { h -> JSONObject().apply {
            put("id", h.id); put("species", h.species); put("title", h.title); put("name", h.name); put("since", h.since); put("goal", h.goal)
            put("unit", h.unit); put("order", h.order); put("archived", h.archived); put("shiny", h.shiny) } }))
        put("sessions", JSONArray(sessions.map { s -> JSONObject().apply {
            put("hobbyId", s.hobbyId); put("start", s.start); put("day", s.day); put("minutes", s.minutes); put("amount", s.amount); put("flow", s.flow)
            put("note", s.note); put("photo", s.photo); put("projectId", s.projectId); put("idea", s.idea) } }))
        put("projects", JSONArray(projects.map { p -> JSONObject().apply {
            put("id", p.id); put("hobbyId", p.hobbyId); put("title", p.title); put("started", p.started); put("done", p.done)
            put("progress", p.progress); put("photo", p.photo); put("note", p.note) } }))
        put("settings", settings.json())
    }

    companion object {
        const val FORMAT = "knackling"
        const val VERSION = 1
        private const val MAX = 512L * 1024 * 1024
        private val NAME = Regex("[A-Za-z0-9-]{1,40}\\.jpg")

        /** Reads what [json] wrote. Nothing is trusted: what can't be read right is a [BadBackup], not a broken app later. */
        fun of(text: String, photos: Map<String, ByteArray> = emptyMap()): Backup {
            try {
                val j = JSONObject(text)
                if (j.optString("format") != FORMAT) throw BadBackup()
                if (j.optInt("version", 1) > VERSION) throw BadBackup(newer = true)
                fun rows(k: String) = j.optJSONArray(k)?.let { a -> (0 until a.length()).map { a.getJSONObject(it) } } ?: emptyList()
                fun pic(s: String) = s.takeIf { NAME.matches(it) } ?: ""
                val hobbies = rows("hobbies").map { o ->
                    Hobby(id = o.getLong("id"), species = o.getString("species"), title = o.optString("title"), name = o.getString("name"), since = o.getLong("since"),
                        goal = o.optInt("goal", 120).coerceIn(0, 7 * 24 * 60), unit = o.optString("unit"), order = o.optInt("order"),
                        archived = o.optBoolean("archived"), shiny = o.optBoolean("shiny"))
                }
                val ids = hobbies.map { it.id }.toSet()
                if (ids.size != hobbies.size || ids.any { it <= 0 }) throw BadBackup()
                val projects = rows("projects").map { o ->
                    Project(id = o.getLong("id"), hobbyId = o.getLong("hobbyId"), title = o.getString("title"), started = o.getLong("started"), done = o.optLong("done"),
                        progress = o.optInt("progress").coerceIn(0, 100), photo = pic(o.optString("photo")), note = o.optString("note"))
                }.filter { it.hobbyId in ids }
                val pids = projects.map { it.id }.toSet()
                if (pids.size != projects.size || pids.any { it <= 0 }) throw BadBackup()
                val sessions = rows("sessions").map { o ->
                    Session(hobbyId = o.getLong("hobbyId"), start = o.getLong("start"), day = o.getLong("day"), minutes = o.getInt("minutes").coerceIn(1, 24 * 60),
                        amount = o.optInt("amount").coerceAtLeast(0), flow = o.optInt("flow").coerceIn(0, 3), note = o.optString("note"), photo = pic(o.optString("photo")),
                        projectId = o.optLong("projectId").takeIf { it in pids } ?: 0, idea = o.optInt("idea", -1).coerceIn(-1, 9))
                }.filter { it.hobbyId in ids }
                val s = Settings.of(j.optJSONObject("settings") ?: JSONObject())
                return Backup(hobbies, sessions, projects, s.copy(onboarded = s.onboarded || hobbies.isNotEmpty(), live = null), j.optLong("made"),
                    photos.filterKeys { NAME.matches(it) })
            } catch (e: org.json.JSONException) {
                throw BadBackup()
            } catch (e: RuntimeException) {
                throw BadBackup()
            }
        }

        fun write(ctx: Context, uri: Uri, b: Backup) =
            (ctx.contentResolver.openOutputStream(uri) ?: throw java.io.IOException()).use { write(ctx, it, b) }

        /** The zip itself, wherever it goes: the settings' «Save a copy» or Lingvale's copy of the whole family. */
        fun write(ctx: Context, out: OutputStream, b: Backup) {
            ZipOutputStream(out).use { z ->
                z.putNextEntry(ZipEntry("data.json")); z.write(b.json().toString().toByteArray()); z.closeEntry()
                val names = (b.sessions.map { it.photo } + b.projects.map { it.photo }).filter { it.isNotEmpty() }.toSet()
                names.forEach { n -> val f = Photos.file(ctx, n); if (f.exists()) { z.putNextEntry(ZipEntry("photos/$n")); f.inputStream().use { it.copyTo(z) }; z.closeEntry() } }
            }
        }

        fun read(ctx: Context, uri: Uri): Backup =
            (ctx.contentResolver.openInputStream(uri) ?: throw java.io.IOException()).use { read(it) }

        fun read(input: InputStream): Backup {
            var json: String? = null; val photos = HashMap<String, ByteArray>(); var total = 0L
            try {
                ZipInputStream(input).use { z ->
                    while (true) {
                        val e = z.nextEntry ?: break
                        val out = ByteArrayOutputStream(); val chunk = ByteArray(64 * 1024)
                        while (true) { val n = z.read(chunk); if (n < 0) break; out.write(chunk, 0, n); total += n; if (total > MAX) throw BadBackup() }
                        when {
                            e.name == "data.json" -> json = out.toByteArray().decodeToString()
                            e.name.startsWith("photos/") -> photos[e.name.removePrefix("photos/")] = out.toByteArray()
                        }
                    }
                }
            } catch (e: java.util.zip.ZipException) { throw BadBackup() }
            return of(json ?: throw BadBackup(), photos)
        }

        /** Puts the backup's photos in place (after the rows are in). */
        fun unpackPhotos(ctx: Context, b: Backup) {
            val dir = Photos.dir(ctx)
            dir.listFiles()?.forEach { it.delete() }
            b.photos.forEach { (n, bytes) -> File(dir, n).writeBytes(bytes) }
        }
    }
}
