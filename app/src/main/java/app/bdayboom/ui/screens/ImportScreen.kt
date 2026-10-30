package app.bdayboom.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.PersonSearch
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import app.bdayboom.data.ContactBirthday
import app.bdayboom.data.ContactsReader
import app.bdayboom.data.EntryType
import app.bdayboom.data.Person
import app.bdayboom.graph
import app.bdayboom.ui.LocalChrome
import app.bdayboom.ui.components.AppButton
import app.bdayboom.ui.components.ButtonKind
import app.bdayboom.ui.components.EmptyState
import app.bdayboom.ui.components.SelectableRow
import app.bdayboom.ui.monthName
import app.bdayboom.ui.theme.AppTheme
import app.bdayboom.ui.theme.Space
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class Status { New, Changed, Same }

/** S18 import from contacts. Running it again updates dates changed in Contacts (re-sync). */
@Composable
fun ImportScreen() {
    val context = LocalContext.current
    val g = context.graph
    val chrome = LocalChrome.current
    val scope = rememberCoroutineScope()
    val c = AppTheme.colors
    val t = AppTheme.type

    fun granted() = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
    var hasPermission by remember { mutableStateOf(granted()) }
    var askedOnce by remember { mutableStateOf(false) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> hasPermission = ok; askedOnce = true }

    var found by remember { mutableStateOf<List<Pair<ContactBirthday, Status>>?>(null) }
    val picked = remember { mutableStateListOf<String>() }
    var importing by remember { mutableStateOf(false) }

    LaunchedEffect(hasPermission) {
        if (!hasPermission) return@LaunchedEffect
        val list = withContext(Dispatchers.IO) { ContactsReader.read(context) }
        val existing = g.repo.importedContacts()
        found = list.map { cb ->
            val e: Person? = existing[cb.key]
            cb to when {
                e == null -> Status.New
                e.month != cb.month || e.day != cb.day || e.year != cb.year -> Status.Changed
                else -> Status.Same
            }
        }
        picked.clear()
        picked += found!!.filter { it.second != Status.Same }.map { it.first.key }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTop("Import", "From your contacts")
        when {
            !hasPermission -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    Icons.Outlined.Contacts, "Bring in birthdays you already have",
                    "We read only names and birthdays or anniversaries from your contacts. Nothing leaves your phone.",
                ) {
                    if (askedOnce) {
                        AppButton("Open app settings", {
                            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                        }, kind = ButtonKind.Secondary)
                    } else {
                        AppButton("Allow access to contacts", { ask.launch(Manifest.permission.READ_CONTACTS) })
                    }
                }
            }
            found == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = c.accent) }
            found!!.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(Icons.Outlined.PersonSearch, "No birthdays in your contacts yet", "None of your contacts has a birthday saved. Add people here instead: it takes seconds.")
            }
            else -> {
                val list = found!!
                val selectable = list.filter { it.second != Status.Same }
                LazyColumn(Modifier.weight(1f)) {
                    item {
                        Text(
                            "${list.size} found · ${selectable.size} new or changed",
                            style = t.sm, color = c.textMuted, modifier = Modifier.padding(horizontal = Space.s4, vertical = Space.s2),
                        )
                        if (selectable.isNotEmpty()) {
                            val all = picked.size == selectable.size
                            SelectableRow(if (all) "Deselect all" else "Select all", "${picked.size} selected", all, onToggle = {
                                picked.clear()
                                if (!all) picked += selectable.map { it.first.key }
                            })
                        }
                    }
                    items(list, key = { it.first.key }) { (cb, status) ->
                        val date = "${cb.day} ${monthName(cb.month, full = false)}" + (cb.year?.let { " $it" } ?: " · year unknown")
                        val sub = when (status) {
                            Status.Same -> "Already in your list"
                            Status.Changed -> "Date changed in Contacts: will update"
                            Status.New -> if (cb.type == EntryType.Anniversary) "Anniversary · $date" else date
                        }
                        SelectableRow(cb.name, sub, cb.key in picked, enabled = status != Status.Same) {
                            if (cb.key in picked) picked.remove(cb.key) else picked.add(cb.key)
                        }
                    }
                }
                AppButton(
                    if (picked.isEmpty()) "Nothing to import" else "Import ${picked.size}",
                    {
                        if (importing) return@AppButton // a second tap must not import twice
                        importing = true
                        val chosen = list.map { it.first }.filter { it.key in picked }
                        chrome.scope.launch { // app-wide: this screen closes below and would cancel the confirmation
                            val n = g.repo.importContacts(chosen)
                            withContext(Dispatchers.Main.immediate) { chrome.nav.popBackStack() } // navigation must happen on the main thread
                            chrome.snackbar.showSnackbar("$n added. We'll remind you on each day.")
                        }
                    },
                    Modifier.padding(Space.s4).fillMaxWidth().navigationBarsPadding(),
                    ButtonKind.Cta, Icons.Outlined.Download, enabled = picked.isNotEmpty(), loading = importing,
                )
            }
        }
    }
}
