package app.bdayboom.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Construction
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.foundation.layout.RowScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.bdayboom.ui.LocalChrome
import app.bdayboom.ui.components.AppTopBar
import app.bdayboom.ui.components.EmptyState
import app.bdayboom.ui.theme.AppTheme
import kotlinx.coroutines.launch

/** Top bar for pushed screens (back arrow) or tabs (drawer menu). */
@Composable
fun ScreenTop(title: String, subtitle: String? = null, tab: Boolean = false, actions: @Composable RowScope.() -> Unit = {}) {
    val chrome = LocalChrome.current
    val scope = rememberCoroutineScope()
    val c = AppTheme.colors
    Box(Modifier.statusBarsPadding()) {
        AppTopBar(
            title, subtitle,
            navigation = {
                if (tab) {
                    IconButton({ scope.launch { chrome.drawer.open() } }) { Icon(Icons.Outlined.Menu, "Open menu", tint = c.text) }
                } else {
                    IconButton({ chrome.nav.popBackStack() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = c.text) }
                }
            },
            actions = actions,
        )
    }
}

@Composable
fun ScrollScreen(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding(), content = content)
}

/** Bumps every time the screen resumes, e.g. back from system settings, so permission checks re-run. */
@Composable
fun rememberResumeTick(): Int {
    var tick by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    return tick
}

/** Placeholder for screens that come after the core slice; routes exist so navigation is complete. */
@Composable
fun StubScreen(title: String, subtitle: String, body: String, tab: Boolean = false) {
    Column(Modifier.fillMaxSize()) {
        ScreenTop(title, subtitle, tab = tab)
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(Icons.Outlined.Construction, "Coming soon", body)
        }
    }
}
