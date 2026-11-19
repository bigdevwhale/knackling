package app.knackling.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

/** A session running right now: which hobby, since when, and the minutes already counted before a pause. */
data class Live(val hobbyId: Long, val since: Long, val banked: Long = 0, val paused: Boolean = false, val idea: Boolean = false) {
    /** Milliseconds counted so far. */
    fun ms(now: Long = System.currentTimeMillis()) = banked + if (paused) 0 else (now - since).coerceAtLeast(0)
    fun json(): JSONObject = JSONObject().put("h", hobbyId).put("s", since).put("b", banked).put("p", paused).put("i", idea)
    companion object { fun of(j: JSONObject) = Live(j.getLong("h"), j.getLong("s"), j.optLong("b"), j.optBoolean("p"), j.optBoolean("i")) }
}

/** Everything that isn't a hobby, a session or a project. */
data class Settings(
    val onboarded: Boolean = false,
    val fem: Boolean = false,                    // «мастерица» rather than «мастер»
    val theme: String = "system",
    val seen: Set<String> = emptySet(),          // introductions read
    val spent: Int = 0,                          // sparks spent in the shop
    val bonus: Int = 0,                          // sparks from quests claimed
    val claimed: Set<String> = emptySet(),       // "d:<day>:<quest>", "w:<week>:<quest>", chests "d:<day>:chest"
    val items: Set<String> = emptySet(),         // decorations bought
    val stages: Map<Long, Int> = emptyMap(),     // the stage last celebrated, per hobby
    val badges: Set<String> = emptySet(),        // badges already celebrated
    val live: Live? = null,
    val nudge: Int = 19,                         // the hour of the «they miss you» nudge, −1 off
    val stage: List<Long> = emptyList(),         // hobbies chosen to sit in the workshop (empty = the most recent ones)
    val ideaSkip: Map<Long, Int> = emptyMap(),   // «another idea» taps today, per hobby, as day*10+n
) {
    fun json(): JSONObject = JSONObject().apply {
        put("onboarded", onboarded); put("fem", fem); put("theme", theme); put("seen", JSONArray(seen.toList()))
        put("spent", spent); put("bonus", bonus); put("claimed", JSONArray(claimed.toList())); put("items", JSONArray(items.toList()))
        put("stages", JSONObject().apply { stages.forEach { (k, v) -> put(k.toString(), v) } })
        put("badges", JSONArray(badges.toList())); live?.let { put("live", it.json()) }; put("nudge", nudge)
        put("stage", JSONArray(stage)); put("ideaSkip", JSONObject().apply { ideaSkip.forEach { (k, v) -> put(k.toString(), v) } })
    }

    companion object {
        fun of(j: JSONObject): Settings {
            fun list(k: String) = j.optJSONArray(k)?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()
            fun ints(k: String) = j.optJSONObject(k)?.let { o -> o.keys().asSequence().associate { it.toLong() to o.getInt(it) } } ?: emptyMap()
            return Settings(j.optBoolean("onboarded"), j.optBoolean("fem"), j.optString("theme", "system"), list("seen").toSet(),
                j.optInt("spent"), j.optInt("bonus"), list("claimed").toSet(), list("items").toSet(), ints("stages"), list("badges").toSet(),
                j.optJSONObject("live")?.let { runCatching { Live.of(it) }.getOrNull() }, j.optInt("nudge", 19),
                list("stage").mapNotNull { it.toLongOrNull() }, ints("ideaSkip"))
        }
    }
}

private val Context.store by preferencesDataStore("knackling")
private val KEY = stringPreferencesKey("settings")

class SettingsStore(private val ctx: Context) {
    val settings: Flow<Settings> = ctx.store.data.map { p -> p[KEY]?.let { runCatching { Settings.of(JSONObject(it)) }.getOrNull() } ?: Settings() }
    suspend fun now(): Settings = settings.first()
    suspend fun update(f: (Settings) -> Settings) { ctx.store.edit { p ->
        val cur = p[KEY]?.let { runCatching { Settings.of(JSONObject(it)) }.getOrNull() } ?: Settings()
        p[KEY] = f(cur).json().toString() } }
}
