package app.bdayboom.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.HealthAndSafety
import app.bdayboom.ui.screens.CheckupScreen
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.bdayboom.ui.components.BottomNav
import app.bdayboom.ui.screens.BackupScreen
import app.bdayboom.ui.screens.CalendarScreen
import app.bdayboom.ui.screens.ContactsScreen
import app.bdayboom.ui.screens.DetailScreen
import app.bdayboom.ui.screens.EditScreen
import app.bdayboom.ui.screens.ExportScreen
import app.bdayboom.ui.screens.GroupsScreen
import app.bdayboom.ui.screens.WishScreen
import app.bdayboom.ui.screens.HelpScreen
import app.bdayboom.ui.screens.HomeScreen
import app.bdayboom.ui.screens.ImportScreen
import app.bdayboom.ui.screens.SettingsScreen
import app.bdayboom.ui.screens.StubScreen
import app.bdayboom.ui.theme.AppTheme
import app.bdayboom.ui.theme.Space
import kotlinx.coroutines.launch

/** Route names. */
object Routes {
    const val HOME = "home" // S01-S03
    const val CALENDAR = "calendar" // S08
    const val CONTACTS = "contacts"
    const val WISHES = "wishes?id={id}" // S09, S10; id = who the wish is for, empty for the library
    const val LINK = "link" // S11-S13
    const val PERSON = "person/{id}" // S05
    const val EDIT = "edit?id={id}&month={month}&day={day}" // S06, S07
    const val SETTINGS = "settings" // S15
    const val HELP = "help" // S16
    const val BACKUP = "backup" // S17
    const val IMPORT = "import" // S18
    const val GROUPS = "groups" // S19
    const val EXPORT = "export" // S20
    const val CHECKUP = "checkup" // fixes.md #1

    fun person(id: String) = "person/$id"
    fun wishes(personId: String? = null) = "wishes?id=${personId ?: ""}"

    fun edit(id: String? = null, month: Int? = null, day: Int? = null) =
        "edit?id=${id ?: ""}&month=${month ?: 0}&day=${day ?: 0}"

    val tabs = listOf(HOME, CALENDAR, CONTACTS, SETTINGS)
}

/** Shared app chrome that screens reach for. */
/** [scope] outlives any single screen: use it for messages shown after leaving one (e.g. "removed · Undo"). */
class Chrome(val nav: NavHostController, val snackbar: SnackbarHostState, val drawer: DrawerState, val scope: kotlinx.coroutines.CoroutineScope)

val LocalChrome = staticCompositionLocalOf<Chrome> { error("no chrome") }

@Composable
fun AppRoot(openEntry: String?, openRoute: String?, onOpened: () -> Unit) {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val c = AppTheme.colors
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route

    LaunchedEffect(openEntry, openRoute) {
        when {
            openEntry != null -> nav.navigate(Routes.person(openEntry))
            openRoute != null -> nav.navigate(openRoute)
            else -> return@LaunchedEffect
        }
        onOpened()
    }

    fun go(r: String) {
        scope.launch { drawer.close() }
        nav.navigate(r) { launchSingleTop = true }
    }

    CompositionLocalProvider(LocalChrome provides Chrome(nav, snackbar, drawer, scope)) {
        ModalNavigationDrawer(
            drawerState = drawer,
            gesturesEnabled = route in Routes.tabs,
            drawerContent = {
                ModalDrawerSheet(drawerContainerColor = c.surface) {
                    Spacer(Modifier.statusBarsPadding().height(Space.s4))
                    Text("BdayBoom", style = AppTheme.type.xl, color = c.text, modifier = Modifier.padding(horizontal = Space.s6, vertical = Space.s3))
                    val colors = NavigationDrawerItemDefaults.colors(
                        unselectedContainerColor = c.surface,
                        unselectedTextColor = c.text,
                        unselectedIconColor = c.accent,
                    )
                    listOf(
                        Triple("Reminder check-up", Icons.Outlined.HealthAndSafety, Routes.CHECKUP),
                        Triple("Import from contacts", Icons.Outlined.Contacts, Routes.IMPORT),
                        Triple("Groups", Icons.Outlined.Group, Routes.GROUPS),
                        Triple("Wishes", Icons.Outlined.Mail, Routes.wishes()),
                        Triple("Backup & restore", Icons.Outlined.Backup, Routes.BACKUP),
                        Triple("Export as CSV", Icons.Outlined.FileDownload, Routes.EXPORT),
                        Triple("Reminders not arriving?", Icons.AutoMirrored.Outlined.HelpOutline, Routes.HELP),
                    ).forEach { (label, icon, r) ->
                        NavigationDrawerItem(
                            label = { Text(label, style = AppTheme.type.base) },
                            icon = { Icon(icon, null) },
                            selected = false,
                            onClick = { go(r) },
                            colors = colors,
                            modifier = Modifier.padding(horizontal = Space.s3),
                        )
                    }
                }
            },
        ) {
            Box(Modifier.fillMaxSize().background(c.bg)) {
                Column(Modifier.fillMaxSize()) {
                    NavHost(nav, startDestination = Routes.HOME, modifier = Modifier.weight(1f)) {
                        composable(Routes.HOME) { HomeScreen() }
                        composable(Routes.CALENDAR) { CalendarScreen() }
                        composable(Routes.CONTACTS) { ContactsScreen() }
                        composable(Routes.WISHES, listOf(navArgument("id") { type = NavType.StringType; defaultValue = "" })) {
                            WishScreen(it.arguments?.getString("id").orEmpty())
                        }
                        composable(Routes.LINK) {
                            StubScreen("Share link", "Friends add themselves", "Your personal link lets friends add their own birthday. It needs a small server, so it comes after the core app.", tab = true)
                        }
                        composable(Routes.PERSON, listOf(navArgument("id") { type = NavType.StringType })) {
                            DetailScreen(it.arguments?.getString("id").orEmpty())
                        }
                        composable(
                            Routes.EDIT,
                            listOf(
                                navArgument("id") { type = NavType.StringType; defaultValue = "" },
                                navArgument("month") { type = NavType.IntType; defaultValue = 0 },
                                navArgument("day") { type = NavType.IntType; defaultValue = 0 },
                            ),
                        ) {
                            val a = it.arguments
                            EditScreen(
                                id = a?.getString("id")?.ifBlank { null },
                                presetMonth = a?.getInt("month")?.takeIf { m -> m > 0 },
                                presetDay = a?.getInt("day")?.takeIf { d -> d > 0 },
                            )
                        }
                        composable(Routes.SETTINGS) { SettingsScreen() }
                        composable(Routes.HELP) { HelpScreen() }
                        composable(Routes.CHECKUP) { CheckupScreen() }
                        composable(Routes.IMPORT) { ImportScreen() }
                        composable(Routes.BACKUP) { BackupScreen() }
                        composable(Routes.GROUPS) { GroupsScreen() }
                        composable(Routes.EXPORT) { ExportScreen() }
                    }
                    if (route in Routes.tabs) {
                        BottomNav(
                            selected = Routes.tabs.indexOf(route),
                            onSelect = { i ->
                                nav.navigate(Routes.tabs[i]) {
                                    popUpTo(Routes.HOME) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            onAdd = { nav.navigate(Routes.edit()) },
                        )
                    }
                }
                app.bdayboom.update.UpdatePrompt()
                SnackbarHost(
                    snackbar,
                    Modifier.align(Alignment.BottomCenter).padding(bottom = if (route in Routes.tabs) 96.dp else 24.dp),
                )
            }
        }
    }
}
