package app.knackling

import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.knackling.data.DemoSeed
import app.knackling.data.Settings
import app.knackling.domain.Lang
import app.knackling.domain.Say
import app.knackling.notify.Nudge
import app.knackling.ui.Onboarding
import app.knackling.ui.Root
import app.knackling.ui.Start
import app.knackling.ui.theme.KnackTheme
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private val askNotes = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (BuildConfig.DEMO && BuildConfig.DEMO_LANG.isNotEmpty() && AppCompatDelegate.getApplicationLocales().toLanguageTags() != BuildConfig.DEMO_LANG) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(BuildConfig.DEMO_LANG)); return }
        Lang.ru = resources.configuration.locales[0].language == "ru"
        if (BuildConfig.DEMO) {
            container.scope.launch { DemoSeed.seedIfEmpty(container) }
            Start.tab = intent.getIntExtra("tab", 0); Start.open = intent.getStringExtra("open")
        }
        Nudge.schedule(this)
        setContent {
            val settings: Settings? by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
            val sys = isSystemInDarkTheme()
            val dark = if (BuildConfig.SHOTS) false else when (settings?.theme) { "dark" -> true; "light" -> false; else -> sys }
            LaunchedEffect(dark) {
                val t = android.graphics.Color.TRANSPARENT
                enableEdgeToEdge(statusBarStyle = if (dark) SystemBarStyle.dark(t) else SystemBarStyle.light(t, t), navigationBarStyle = if (dark) SystemBarStyle.dark(t) else SystemBarStyle.light(t, t))
            }
            // the running session's clock and the nudges need the notification permission; asked once, after the first hobby
            LaunchedEffect(settings?.onboarded) {
                if (settings?.onboarded == true && android.os.Build.VERSION.SDK_INT >= 33 && !BuildConfig.SHOTS &&
                    checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
                    askNotes.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
            KnackTheme(dark) {
                settings?.let { s -> Say.fem = s.fem; if (s.onboarded) Root(s) else Onboarding() }
            }
        }
    }
}

/** Switches the app language: "" follows the phone, "ru" or "en" pins it. The activity restarts in the new language. */
fun setLanguage(tag: String) = AppCompatDelegate.setApplicationLocales(if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag))
fun currentLanguage(): String = AppCompatDelegate.getApplicationLocales().toLanguageTags().substringBefore('-')
