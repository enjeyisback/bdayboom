package app.bdayboom

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.bdayboom.data.ThemeMode
import app.bdayboom.ui.AppRoot
import app.bdayboom.ui.theme.AppTheme

class MainActivity : ComponentActivity() {
    private var openEntry by mutableStateOf<String?>(null)
    private var openRoute by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) readIntent(intent)
        app.bdayboom.update.UpdateManager.get(this).checkIfDue()
        val settings = graph.settings
        setContent {
            val s by settings.flow.collectAsState(initial = null)
            val systemDark = isSystemInDarkTheme()
            val dark = when (s?.theme ?: ThemeMode.System) {
                ThemeMode.System -> systemDark
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }
            LaunchedEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(style, style)
            }
            AppTheme(dark = dark) {
                AppRoot(openEntry, openRoute) { openEntry = null; openRoute = null }
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
    }

    companion object {
        const val EXTRA_ENTRY = "entryId"
        const val EXTRA_ROUTE = "route"
    }
}
