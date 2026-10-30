package app.bdayboom.design

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Celebration
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.bdayboom.ui.components.AppButton
import app.bdayboom.ui.components.AppChip
import app.bdayboom.ui.components.AppTextField
import app.bdayboom.ui.components.AppTopBar
import app.bdayboom.ui.components.Avatar
import app.bdayboom.ui.components.Banner
import app.bdayboom.ui.components.BannerKind
import app.bdayboom.ui.components.BottomNav
import app.bdayboom.ui.components.ButtonKind
import app.bdayboom.ui.components.ChipRow
import app.bdayboom.ui.components.CountdownPill
import app.bdayboom.ui.components.DotKind
import app.bdayboom.ui.components.EmptyState
import app.bdayboom.ui.components.EntryKind
import app.bdayboom.ui.components.MonthGrid
import app.bdayboom.ui.components.MonthHeader
import app.bdayboom.ui.components.PersonRow
import app.bdayboom.ui.components.PersonRowModel
import app.bdayboom.ui.components.ReminderRow
import app.bdayboom.ui.components.SearchField
import app.bdayboom.ui.components.SectionHeader
import app.bdayboom.ui.components.SelectField
import app.bdayboom.ui.components.SelectableRow
import app.bdayboom.ui.components.SpotlightCard
import app.bdayboom.ui.components.StatBanner
import app.bdayboom.ui.components.Tag
import app.bdayboom.ui.components.TagTone
import app.bdayboom.ui.components.appCard
import app.bdayboom.ui.theme.AppTheme
import app.bdayboom.ui.theme.Radius
import app.bdayboom.ui.theme.Space
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

/**
 * Debug-only design system check: every UI primitive, light and dark.
 * adb: am start -n app.bdayboom.debug/app.bdayboom.design.DesignCatalogActivity
 *        --ez dark true --ef fontScale 2.0 --ei scrollTo 6
 */
class DesignCatalogActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val forcedDark = if (intent.hasExtra("dark")) intent.getBooleanExtra("dark", false) else null
        val fontScale = intent.getFloatExtra("fontScale", 0f)
        val scrollTo = intent.getIntExtra("scrollTo", 0)
        setContent {
            val systemDark = isSystemInDarkTheme()
            var dark by remember { mutableStateOf(forcedDark ?: systemDark) }
            var bigText by remember { mutableStateOf(fontScale >= 1.5f) }
            LaunchedEffect(dark) {
                // status/nav bar icons follow the catalog's theme, not the system's
                val style = if (dark) SystemBarStyle.dark(AndroidColor.TRANSPARENT) else SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                enableEdgeToEdge(style, style)
            }
            val density = LocalDensity.current
            val scaled = Density(density.density, if (bigText) maxOf(fontScale, 2f) else if (fontScale > 0f) fontScale else density.fontScale)
            CompositionLocalProvider(LocalDensity provides scaled) {
                AppTheme(dark = dark) {
                    Catalog(dark, { dark = !dark }, bigText, { bigText = !bigText }, scrollTo)
                }
            }
        }
    }
}

private val sampleRows = listOf(
    PersonRowModel("1", "Marcus Brody", "Oct 24 · Turning 32", "Colleague", "Oct 24", "Marcus Brody, colleague, turns 32 on 24 October, tomorrow", daysLeft = 1),
    PersonRowModel("2", "Chloe Bennett", "Oct 28 · Turning 27", "Close friend", "Oct 28", "Chloe Bennett, close friend, turns 27 on 28 October, in 5 days", daysLeft = 5),
    PersonRowModel("3", "Priya & Sam", "Dec 2 · 6 years", "Anniversary", "Dec 2", "Priya and Sam, anniversary, 6 years on 2 December, in 40 days", EntryKind.Anniversary, 40),
    PersonRowModel("4", "Grandpa Joe", "Jul 19 · Turning 80", "Family", "Jul 19", "Grandpa Joe, family, turns 80 on 19 July, in 269 days", daysLeft = 269),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Catalog(dark: Boolean, toggleDark: () -> Unit, bigText: Boolean, toggleBig: () -> Unit, scrollTo: Int) {
    val c = AppTheme.colors
    val t = AppTheme.type
    val listState = rememberLazyListState()
    // --ei scrollTo N jumps to LazyColumn item N, for adb screenshots on phones that block input injection
    LaunchedEffect(scrollTo) { if (scrollTo > 0) listState.scrollToItem(scrollTo) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var tab by remember { mutableIntStateOf(0) }
    var group by remember { mutableStateOf("All (14)") }
    var relation by remember { mutableStateOf("Best friend") }
    var showDialog by remember { mutableStateOf(false) }
    var selectedDay by remember { mutableStateOf<LocalDate?>(LocalDate.now()) }
    var search by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("Alex Morgan") }
    val cadence = remember { mutableStateListOf(true, true, false, true) }
    var withYear by remember { mutableStateOf(true) }
    val checks = remember { mutableStateListOf(true, false) }
    val rows = remember { mutableStateListOf(*sampleRows.toTypedArray()) }

    Box(Modifier.fillMaxSize().background(c.bg)) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.statusBarsPadding()) {
                AppTopBar("Design catalog", "BdayBoom · ${if (dark) "Midnight" else "Daylight"}") {
                    IconButton(toggleBig) { Icon(Icons.Outlined.TextFields, if (bigText) "Normal text size" else "Large text size", tint = c.text) }
                    IconButton(toggleDark) { Icon(Icons.Outlined.DarkMode, if (dark) "Light theme" else "Dark theme", tint = c.text) }
                    IconButton({}) { Icon(Icons.Outlined.NotificationsNone, "Notifications", tint = c.text) }
                }
            }
            LazyColumn(Modifier.weight(1f), state = listState) {
                // 0
                item { Spacer(Modifier.height(Space.s2)); SearchField(search, { search = it }, "Search friends & milestones", {}) }
                // 1
                item { Spacer(Modifier.height(Space.s3)); StatBanner("14 celebrations tracked this year", "On a roll", Icons.Outlined.LocalFireDepartment) }
                // 2
                item { Spacer(Modifier.height(Space.s2)); ChipRow(listOf("All (14)", "Family (4)", "Close friends (7)", "Work (3)"), group, { group = it }) }
                // 3
                item {
                    Spacer(Modifier.height(Space.s2))
                    SpotlightCard("Emma Watson", "Turning 28", "Celebrate today with a warm wish and a little surprise.", {}, {})
                }
                // 4
                item { SectionHeader("This week", icon = Icons.Outlined.Cake, trailing = "2 celebrations") }
                items(rows.filter { it.daysLeft <= 7 }, key = { it.id }) { r -> RowItem(r, rows, snackbar, scope) }
                item { SectionHeader("Later", icon = Icons.Outlined.CalendarMonth, trailing = "2 celebrations") }
                items(rows.filter { it.daysLeft > 7 }, key = { it.id }) { r -> RowItem(r, rows, snackbar, scope) }

                item { Title("Calendar") }
                item {
                    var month by remember { mutableStateOf(YearMonth.now()) }
                    Column(Modifier.padding(horizontal = Space.s4).appCard().padding(vertical = Space.s3)) {
                        MonthHeader(month, 4, { month = month.minusMonths(1) }, { month = month.plusMonths(1) })
                        MonthGrid(
                            month,
                            mapOf(3 to listOf(DotKind.Upcoming), 11 to listOf(DotKind.Milestone), 16 to listOf(DotKind.Planned), 24 to listOf(DotKind.Upcoming, DotKind.Planned)),
                            selectedDay, LocalDate.now(), { selectedDay = it },
                        )
                    }
                }

                item { Title("Buttons") }
                item {
                    FlowRow(Modifier.padding(horizontal = Space.s4), horizontalArrangement = Arrangement.spacedBy(Space.s2), verticalArrangement = Arrangement.spacedBy(Space.s2)) {
                        AppButton("Message", {}, icon = Icons.Outlined.ChatBubbleOutline)
                        AppButton("Call", {}, kind = ButtonKind.Neutral, icon = Icons.Outlined.Call)
                        AppButton("Gift guide", {}, kind = ButtonKind.Tonal, icon = Icons.Outlined.CardGiftcard)
                        AppButton("Import", {}, kind = ButtonKind.Secondary, icon = Icons.Outlined.Contacts)
                        AppButton("Cancel", {}, kind = ButtonKind.Text)
                        AppButton("Delete", { showDialog = true }, kind = ButtonKind.Danger)
                        AppButton("Saving", {}, loading = true)
                        AppButton("Disabled", {}, enabled = false)
                    }
                    Spacer(Modifier.height(Space.s4))
                    AppButton("Save & set reminders", {}, Modifier.padding(horizontal = Space.s4).fillMaxWidth(), ButtonKind.Cta, Icons.Outlined.Celebration)
                }

                item { Title("Tags, avatars, countdowns") }
                item {
                    Column(Modifier.padding(horizontal = Space.s4), verticalArrangement = Arrangement.spacedBy(Space.s4)) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s2), verticalArrangement = Arrangement.spacedBy(Space.s2)) {
                            Tag("Turning 28", TagTone.Celebrate, solid = true)
                            Tag("Annual", TagTone.Accent)
                            Tag("Gift ordered", TagTone.Celebrate)
                            Tag("Harmonious & creative", TagTone.Highlight)
                            Tag("Libra", TagTone.Neutral, Icons.Outlined.Star)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.s3)) {
                            Avatar("EW", badge = Icons.Outlined.Cake)
                            Avatar("", EntryKind.Anniversary)
                            Avatar("", EntryKind.Event)
                            Avatar("SR", size = 88.dp, ring = c.accent)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.s4)) {
                            CountdownPill(1, "Oct 24"); CountdownPill(5, "Oct 28"); CountdownPill(48, "Dec 10"); CountdownPill(257, "Jul 7")
                        }
                    }
                }

                item { Title("Who are we celebrating?") }
                item {
                    Column(Modifier.padding(horizontal = Space.s4).appCard().padding(Space.s4), verticalArrangement = Arrangement.spacedBy(Space.s4)) {
                        AppTextField("Full name or nickname", name, { name = it }, trailing = Icons.Outlined.Contacts)
                        Column {
                            Text("Relationship", style = t.label, color = c.textMuted, modifier = Modifier.padding(start = Space.s1))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                                listOf("Best friend", "Family", "Friend", "Partner", "Colleague").forEach {
                                    AppChip(it, relation == it, { relation = it }, icon = if (it == "Best friend") Icons.Outlined.Star else null)
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                            SelectField("Month", "Nov", {}, Modifier.weight(1.2f))
                            SelectField("Day", "14", {}, Modifier.weight(1f))
                            SelectField("Year", if (withYear) "1995" else "—", {}, Modifier.weight(1.2f))
                        }
                        ReminderRow("Include birth year", "Shows the age and star sign", withYear, { withYear = !withYear }, useSwitch = true)
                        AppTextField("Date", "", {}, placeholder = "Pick a day", error = "Choose a day")
                        AppTextField("Gift ideas and notes", "Mentioned wanting a filter coffee dripper", {}, minLines = 3)
                    }
                }

                item { Title("Reminder cadence") }
                item {
                    Column(Modifier.padding(horizontal = Space.s4), verticalArrangement = Arrangement.spacedBy(Space.s2)) {
                        ReminderRow("2 weeks before", "Time to shop for a gift", cadence[0], { cadence[0] = !cadence[0] }, tag = "Gifts")
                        ReminderRow("1 week before", "Send a card, make plans", cadence[1], { cadence[1] = !cadence[1] })
                        ReminderRow("3 days before", "A quick heads-up", cadence[2], { cadence[2] = !cadence[2] })
                        ReminderRow("On the day · 9:00 AM", "Wish them first thing", cadence[3], { cadence[3] = !cadence[3] }, tag = "Morning")
                        ReminderRow("Day before", "Tomorrow at 9:00 AM", true, {}, useSwitch = true, icon = Icons.Outlined.Alarm)
                    }
                }

                item { Title("Banners") }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(Space.s2)) {
                        Banner("2 new birthdays from your link", onClick = {})
                        Banner("Reminders may be blocked. Fix it", BannerKind.Warning, onClick = {})
                    }
                }

                item { Title("Import list") }
                item {
                    Column {
                        SelectableRow("Maya Chen", "11 Mar 1995", checks[0]) { checks[0] = !checks[0] }
                        SelectableRow("Rohan Iyer", "14 Mar · year unknown", checks[1]) { checks[1] = !checks[1] }
                        SelectableRow("Grandpa Joe", "Already in your list", false, enabled = false) {}
                        Column(Modifier.padding(Space.s4)) {
                            Text("Importing 12 of 40", style = t.sm, color = c.textMuted)
                            Spacer(Modifier.height(Space.s2))
                            LinearProgressIndicator({ 0.3f }, Modifier.fillMaxWidth(), color = c.accent, trackColor = c.surface2)
                        }
                    }
                }

                item { Title("Colour roles") }
                item {
                    val swatches = listOf(
                        "bg" to c.bg, "surface" to c.surface, "surface-2" to c.surface2, "border" to c.border,
                        "text" to c.text, "text-muted" to c.textMuted, "accent" to c.accent, "accent-soft" to c.accentSoft,
                        "celebrate" to c.celebrate, "celeb-soft" to c.celebrateSoft, "highlight" to c.highlight, "hl-soft" to c.highlightSoft,
                        "hero" to c.heroStart, "cta-end" to c.ctaEnd, "danger" to c.danger, "success" to c.success,
                    )
                    FlowRow(Modifier.padding(horizontal = Space.s4), horizontalArrangement = Arrangement.spacedBy(Space.s2), verticalArrangement = Arrangement.spacedBy(Space.s2)) {
                        swatches.forEach { (n, col) ->
                            Column(Modifier.width(76.dp)) {
                                Box(Modifier.size(76.dp, 36.dp).background(col, RoundedCornerShape(Radius.sm)).border(1.dp, c.border, RoundedCornerShape(Radius.sm)))
                                Text(n, style = t.xs, color = c.textMuted)
                            }
                        }
                    }
                }

                item { Title("Type") }
                item {
                    Column(Modifier.padding(horizontal = Space.s4)) {
                        Text("Display 36", style = t.display, color = c.text)
                        Text("XL 28 · Sofia Rodriguez", style = t.xl, color = c.text)
                        Text("LG 22 · Section title", style = t.lg, color = c.text)
                        Text("Name 20 · Marcus Brody", style = t.name, color = c.text)
                        Text("Title 16 · Manrope semibold", style = t.title, color = c.text)
                        Text("Base 16 · body copy", style = t.base, color = c.text)
                        Text("SM 14 · Oct 24 · Turning 32", style = t.sm, color = c.textMuted)
                        Text("LABEL 12 · 2 celebrations", style = t.label, color = c.textMuted)
                        Text("MICRO 10 · TODAY'S SPOTLIGHT", style = t.micro, color = c.accent)
                    }
                }

                item { Title("Empty state") }
                item {
                    EmptyState(Icons.Outlined.Cake, "No birthdays yet", "Add someone, or bring in the birthdays already saved in your contacts.") {
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                            AppButton("Add birthday", {})
                            AppButton("Import", {}, kind = ButtonKind.Secondary)
                        }
                    }
                }
                item { Spacer(Modifier.height(Space.s8)) }
            }
            BottomNav(tab, { tab = it }, { scope.launch { snackbar.showSnackbar("Add tapped") } })
        }
        SnackbarHost(snackbar, Modifier.align(androidx.compose.ui.Alignment.BottomCenter).padding(bottom = 96.dp))
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            containerColor = c.surface,
            title = { Text("Delete Emma Watson?", style = t.lg, color = c.text) },
            text = { Text("Their reminders stop too.", style = t.sm, color = c.textMuted) },
            confirmButton = { AppButton("Delete", { showDialog = false }, kind = ButtonKind.Danger) },
            dismissButton = { AppButton("Cancel", { showDialog = false }, kind = ButtonKind.Text) },
        )
    }
}

@Composable
private fun RowItem(r: PersonRowModel, rows: MutableList<PersonRowModel>, snackbar: SnackbarHostState, scope: CoroutineScope) {
    PersonRow(r, onOpen = {})
}

@Composable
private fun Title(text: String) {
    Text(
        text,
        style = AppTheme.type.lg,
        color = AppTheme.colors.text,
        modifier = Modifier.padding(start = Space.s4, end = Space.s4, top = Space.s8, bottom = Space.s3).semantics { heading() },
    )
}
