package app.knackling.bridge

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import app.knackling.data.BadBackup
import app.knackling.data.Backup
import app.knackling.BuildConfig
import app.knackling.KnacklingApp
import app.knackling.AppContainer
import app.knackling.domain.Lang
import app.knackling.domain.Book
import app.knackling.domain.Catalog
import app.knackling.domain.Kinds
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The bridge to Lingvale, the village where the pets of all the -ling apps live together.
 *
 * Lingvale asks through ContentResolver.call() and nobody else gets an answer: the caller must be Lingvale signed
 * with its release key (a debug or demo build also answers a Lingvale built with the same debug key).
 * Protocol v2, the same in every -ling app:
 *  - "hello"    → {v}
 *  - "snapshot" → {json}: pets, species, days of activity, the last week's events
 *  - "art"      → {bytes}: one picture from res/drawable-nodpi (pet_*, item_*, prop_*, *_bg)
 *  - "backup"   (extras {fd}: a file open for writing) → {ok, name}: writes the same backup file the settings save
 *  - "restore"  (extras {fd}: a file open for reading) → {ok, error}: loads such a file, as the settings' «Restore» does,
 *    replacing what is on the phone; error is "newer" (a file from a newer version), "busy" or "bad"
 * Only "restore" changes anything here, and Lingvale asks the player before it calls it.
 */
class VillageProvider : ContentProvider() {
    override fun onCreate() = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val ctx = context ?: return null
        if (!trusted(ctx)) throw SecurityException("Only Lingvale may ask")
        return when (method) {
            "hello" -> Bundle().apply { putInt("v", VERSION) }
            "snapshot" -> Bundle().apply { putString("json", runBlocking { snapshot(ctx, container(ctx)) }.toString()) }
            "art" -> art(ctx, arg)
            "backup" -> backup(ctx, extras)
            "restore" -> restore(ctx, extras)
            else -> null
        }
    }

    @Suppress("DEPRECATION")
    private fun fd(extras: Bundle?): ParcelFileDescriptor? = extras?.getParcelable("fd")

    /** Writes the settings' backup zip into the file Lingvale opened. */
    private fun backup(ctx: Context, extras: Bundle?): Bundle {
        val fd = fd(extras) ?: return Bundle().apply { putBoolean("ok", false) }
        val ok = runCatching {
            val b = runBlocking { container(ctx).backup() }
            ParcelFileDescriptor.AutoCloseOutputStream(fd).use { Backup.write(ctx, it, b) }
        }.onFailure { runCatching { fd.close() } }.isSuccess
        return Bundle().apply { putBoolean("ok", ok); putString("name", "knackling.zip") }
    }

    /** Loads a backup file from Lingvale, the way the settings do: all of it, or, if something fails, none of it. */
    private fun restore(ctx: Context, extras: Bundle?): Bundle {
        val fd = fd(extras) ?: return Bundle().apply { putBoolean("ok", false); putString("error", "bad") }
        val error = try {
            val b = ParcelFileDescriptor.AutoCloseInputStream(fd).use { Backup.read(it) }
            runBlocking { container(ctx).restore(b) }
            null
        } catch (e: BadBackup) {
            if (e.newer) "newer" else "bad"
        } catch (e: Exception) {
            runCatching { fd.close() }; "bad"
        }
        return Bundle().apply { putBoolean("ok", error == null); if (error != null) putString("error", error) }
    }

    private fun trusted(ctx: Context): Boolean {
        val pkg = callingPackage ?: return false
        if (pkg != HUB && !pkg.startsWith("$HUB.")) return false
        val pm = ctx.packageManager
        if (HUB_CERTS.any { pm.hasSigningCertificate(pkg, it, PackageManager.CERT_INPUT_SHA256) }) return true
        return BuildConfig.DEBUG && pm.checkSignatures(pkg, ctx.packageName) == PackageManager.SIGNATURE_MATCH
    }

    private fun art(ctx: Context, name: String?): Bundle? {
        if (name == null || !ART.matches(name)) return null
        // Lingvale asks for egg_<species>; here eggs are shared by the catalogue's shelves
        val file = if (name.startsWith("egg_") && name.drop(4).toIntOrNull() == null) app.knackling.ui.art.eggOf(name.drop(4)) else name
        @Suppress("DiscouragedApi")
        val id = ctx.resources.getIdentifier(file, "drawable", ctx.packageName)
        if (id == 0) return null
        return Bundle().apply { putByteArray("bytes", ctx.resources.openRawResource(id).use { it.readBytes() }) }
    }

    /** The provider can be asked before the application has finished starting; wait for it a little. */
    private fun container(ctx: Context): AppContainer {
        repeat(100) {
            runCatching { return (ctx.applicationContext as KnacklingApp).container }
            Thread.sleep(30)
        }
        error("not ready")
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0

    companion object {
        const val VERSION = 2
        const val HUB = "app.lingvale"
        /** SHA-256 of Lingvale's release certificate. */
        private val HUB_CERTS = listOf(hex("CEF40A1786896D829CFA53115693AC9A53FC0349EAB84BAF64EFA648592D1ABF"))
        private val ART = Regex("""(pet|egg|decor|tool|patch)_[a-z0-9_]+|[a-z]+_bg""")
        private fun hex(s: String) = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
        private fun color(c: Long) = "#%06X".format(c and 0xFFFFFF)
        private fun dayOf(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()

        /** The village counts 0 egg, 1 baby, 2 youngster, 3 grown, 4 master; here −1 egg … 4 grand master. */
        fun villageStage(stage: Int) = when { stage < 0 -> 0; stage == 0 -> 1; stage == 1 -> 2; stage <= 3 -> 3; else -> 4 }

        /** Everything Lingvale shows of Knackling: the apprentices (ranks as growth), the days at hobbies, the last week. */
        suspend fun snapshot(ctx: Context, c: AppContainer): JSONObject {
            val b = Book(c.dao.hobbyList(), c.dao.sessionList(), c.dao.projectList(), c.settings.now(), LocalDate.now().toEpochDay())
            val petsJson = JSONArray()
            b.active.forEachIndexed { i, h ->
                val st = b.stage(h)
                val first = b.of[h.id]?.minOfOrNull { it.start } ?: (h.since * 86400_000L)
                petsJson.put(JSONObject()
                    .put("id", h.id).put("species", h.species).put("name", h.name)
                    .put("stage", villageStage(st)).put("progress", b.progress(h).toDouble())
                    .put("slot", if (h in b.onStage.take(3)) i else -1).put("shiny", h.shiny).put("found", h.since * 86400_000L).put("hatched", first))
            }
            val speciesJson = JSONArray()
            Catalog.kinds.forEach { k ->
                speciesJson.put(JSONObject().put("id", k.id).put("ru", k.petRu).put("en", k.petEn).put("color", color(k.color))
                    .put("aboutRu", k.ru).put("aboutEn", k.en).put("rare", false))
            }
            Catalog.own.forEach { o ->
                speciesJson.put(JSONObject().put("id", o.id).put("ru", o.ru).put("en", o.en).put("color", color(o.color))
                    .put("aboutRu", "Своё хобби").put("aboutEn", "An own hobby").put("rare", false))
            }
            val daysJson = JSONArray()
            b.sessions.groupBy { it.day }.toSortedMap().forEach { (d, l) ->
                if (d > b.today - 400) daysJson.put(JSONObject().put("d", d).put("n", l.size).put("min", l.sumOf { it.minutes }))
            }
            val eventsJson = JSONArray()
            b.sessions.filter { it.day > b.today - 8 }.forEach { s ->
                val h = b.byId[s.hobbyId] ?: return@forEach
                val kind = Kinds.of(h.species)
                eventsJson.put(JSONObject().put("at", s.start).put("min", s.minutes)
                    .put("ru", "${h.name}: ${h.title.ifBlank { kind?.ru ?: "" }}").put("en", "${h.name}: ${h.title.ifBlank { kind?.en ?: "" }}"))
            }
            return JSONObject()
                .put("v", VERSION).put("app", "knackling").put("version", BuildConfig.VERSION_NAME)
                .put("ru", Lang.ru).put("scene", "room_bg")
                .put("pets", petsJson).put("species", speciesJson).put("days", daysJson).put("events", eventsJson)
        }
    }
}
