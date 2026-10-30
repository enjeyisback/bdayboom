package app.bdayboom

import android.app.UiModeManager
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.bdayboom.data.ThemeMode
import app.bdayboom.ui.AppRoot
import app.bdayboom.ui.SplashOverlay
import app.bdayboom.ui.theme.AppTheme

class MainActivity : ComponentActivity() {
    private var openEntry by mutableStateOf<String?>(null)
    private var openRoute by mutableStateOf<String?>(null)
    /** The logo animation plays on every fresh start (not on rotation or coming back from the background). */
    private var splash by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) { readIntent(intent); splash = true }
        app.bdayboom.update.UpdateManager.get(this).checkIfDue()
        val settings = graph.settings
        setContent {
            val s by settings.flow.collectAsState(initial = null)
            val systemDark = isSystemInDarkTheme()
            val mode = s?.theme ?: ThemeMode.System
            LaunchedEffect(mode) {
                // Android 12+: the system's first (splash) frame then uses the app's own light/dark choice.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && s != null) {
                    getSystemService(UiModeManager::class.java)?.setApplicationNightMode(
                        when (mode) { ThemeMode.System -> UiModeManager.MODE_NIGHT_AUTO; ThemeMode.Light -> UiModeManager.MODE_NIGHT_NO; ThemeMode.Dark -> UiModeManager.MODE_NIGHT_YES },
                    )
                }
            }
            val dark = when (mode) {
                ThemeMode.System -> systemDark
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }
            LaunchedEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(style, style)
            }
            AppTheme(dark = dark) {
                Box {
                    AppRoot(openEntry, openRoute) { openEntry = null; openRoute = null }
                    if (splash) SplashOverlay { splash = false }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readIntent(intent)
    }

    private fun readIntent(intent: Intent) {
        openEntry = intent.getStringExtra(EXTRA_ENTRY)
        // Debug builds accept a route, so screens can be opened over adb for screenshots.
        if (BuildConfig.DEBUG) openRoute = intent.getStringExtra(EXTRA_ROUTE)
        intent.getStringExtra(EXTRA_WISH)?.let { openRoute = app.bdayboom.ui.Routes.wishes(it) }
    }

    companion object {
        const val EXTRA_ENTRY = "entryId"
        const val EXTRA_ROUTE = "route"
        const val EXTRA_WISH = "wishFor"
    }
}
