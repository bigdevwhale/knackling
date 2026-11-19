package app.knackling

import android.graphics.Bitmap
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import app.knackling.data.DemoSeed
import app.knackling.domain.Book
import app.knackling.domain.Lang
import app.knackling.ui.Onboarding
import app.knackling.ui.Root
import app.knackling.ui.Start
import app.knackling.ui.theme.KnackTheme
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Every screen of the demo drawn to PNGs in app/build/art/screens (run with `-Prender`), at a phone's size. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "ru-w393dp-h873dp-xxhdpi")
class RenderScreens {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val out = File(System.getProperty("render.out") ?: "build/art", "screens").apply { mkdirs() }

    private fun shot(name: String) {
        repeat(6) { rule.mainClock.advanceTimeBy(400); Thread.sleep(150); org.robolectric.shadows.ShadowLooper.idleMainLooper() }
        val v = rule.activity.window.decorView
        val b = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { v.draw(android.graphics.Canvas(b)) }
        File(out, "${if (System.getProperty("render.lang") == "en") "en-" else ""}$name.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun render() {
        val en = System.getProperty("render.lang") == "en"
        if (en) org.robolectric.RuntimeEnvironment.setQualifiers("+en")
        Lang.ru = !en
        val c = ApplicationProvider.getApplicationContext<KnacklingApp>().container
        var i by mutableIntStateOf(-1)
        var dark by mutableStateOf(false)
        rule.mainClock.autoAdvance = false
        rule.setContent { KnackTheme(dark) { key(i) { if (i == -2) Onboarding() else { val s by c.settings.settings.collectAsState(null); s?.let { st -> if (i >= 0 && st.onboarded) Root(st) } } } } }
        rule.runOnUiThread { i = -2 }; shot("0-onboarding")
        runBlocking { DemoSeed.seedIfEmpty(c) }
        val h = runBlocking { c.dao.hobbyList() }
        val p = runBlocking { c.dao.projectList() }
        val shots = listOf(
            "1-home" to (0 to null), "2-journal" to (1 to null), "3-stats" to (2 to null), "4-album" to (3 to null),
            "5-hobby" to (0 to "hobby:${h[0].id}"), "5b-hobby" to (0 to "hobby:${h[3].id}"), "6-log" to (0 to "log:${h[1].id}"), "7-start" to (0 to "start"),
            "8-add" to (0 to "add"), "9-shop" to (0 to "shop"), "10-species" to (3 to "species:guitar"), "11-project" to (1 to "project:${p[0].id}"),
            "12-settings" to (0 to "settings"), "13-share" to (0 to "share"),
        )
        for ((n, sh) in shots.withIndex()) {
            Start.tab = sh.second.first; Start.open = sh.second.second
            rule.runOnUiThread { i = n }
            shot(sh.first)
        }
        runBlocking { c.startLive(h[0].id) }
        Start.tab = 0; Start.open = "live"; rule.runOnUiThread { i = 100 }; shot("14-live")
        Start.open = null; rule.runOnUiThread { i = 101 }; shot("1b-home-live")
        runBlocking { c.stopLive(); c.settings.update { it.copy(items = app.knackling.domain.Decors.all.map { d -> d.id }.toSet()) } }
        rule.runOnUiThread { i = 102 }; shot("1c-home-full")
        rule.runOnUiThread { dark = true; i = 103 }; shot("15-dark")
        Start.tab = 0; Start.open = "hobby:${h[0].id}"; rule.runOnUiThread { i = 104 }; shot("15b-dark-hobby")
        rule.runOnUiThread { dark = false; i = 105 }
        // every postcard
        val b = runBlocking { Book(c.dao.hobbyList(), c.dao.sessionList(), c.dao.projectList(), c.settings.now(), c.clock()) }
        val cards = File(out.parentFile, "cards").apply { mkdirs() }
        app.knackling.ui.share.cardsFor(b, "month").forEachIndexed { k, card ->
            val bmp = app.knackling.ui.share.Painter(rule.activity, b).render(card, story = k == 1)
            File(cards, "$k-${card.replace(':', '_')}.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 90, it) }
        }
    }
}
