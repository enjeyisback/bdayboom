package app.bdayboom.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.bdayboom.data.Group
import app.bdayboom.graph
import app.bdayboom.ui.LocalChrome
import app.bdayboom.ui.components.AppButton
import app.bdayboom.ui.components.AppTextField
import app.bdayboom.ui.components.ButtonKind
import app.bdayboom.ui.components.SectionHeader
import app.bdayboom.ui.components.SettingsItem
import app.bdayboom.ui.components.appCard
import app.bdayboom.ui.theme.AppTheme
import app.bdayboom.ui.theme.Space
import kotlinx.coroutines.launch

/** S16 groups: add, rename, delete. */
@Composable
fun GroupsScreen() {
    val g = LocalContext.current.graph
    val chrome = LocalChrome.current
    val scope = rememberCoroutineScope()
    val c = AppTheme.colors
    val t = AppTheme.type
    val groups by g.repo.groups.collectAsState(initial = emptyList())
    val people by g.repo.people.collectAsState(initial = emptyList())
    var editing by remember { mutableStateOf<Group?>(null) }
    var adding by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<Group?>(null) }

    fun count(gr: Group) = people.count { it.groupId == gr.id }.let { if (it == 1) "1 person" else "$it people" }

    Column(Modifier.fillMaxSize()) {
        ScreenTop("Groups", "Family, friends, work")
        ScrollScreen {
            SectionHeader("Your groups", icon = Icons.Outlined.Group, trailing = "${groups.size}")
            Column(Modifier.padding(horizontal = Space.s4).fillMaxWidth().appCard()) {
                if (groups.isEmpty()) {
                    Text("No groups yet.", style = t.sm, color = c.textMuted, modifier = Modifier.padding(Space.s4))
                }
                groups.forEach { gr -> SettingsItem(gr.name, count(gr), Icons.Outlined.Group) { editing = gr } }
            }
            Spacer(Modifier.height(Space.s3))
            AppButton("New group", { adding = true }, Modifier.padding(horizontal = Space.s4).fillMaxWidth(), ButtonKind.Secondary, Icons.Outlined.Add)
            Spacer(Modifier.height(Space.s8))
        }
    }

    if (adding || editing != null) {
        val current = editing
        var name by remember(current) { mutableStateOf(current?.name ?: "") }
        var error by remember(current) { mutableStateOf<String?>(null) }
        val close = { adding = false; editing = null }
        AlertDialog(
            onDismissRequest = close,
            containerColor = c.surface,
            title = { Text(if (current == null) "New group" else "Edit group", style = t.lg, color = c.text) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Space.s2)) {
                    AppTextField("Name", name, { name = it; error = null }, placeholder = "e.g. School friends")
                    error?.let { Text(it, style = t.xs, color = c.danger) }
                    if (current != null) {
                        AppButton("Delete group", { confirmDelete = current; close() }, kind = ButtonKind.Text)
                    }
                }
            },
            confirmButton = {
                AppButton(if (current == null) "Create" else "Save", {
                    scope.launch {
                        if (current == null) { g.repo.addGroup(name); close() }
                        else if (g.repo.renameGroup(current.id, name)) close()
                        else error = "There's already a group called ${name.trim()}"
                    }
                }, enabled = name.isNotBlank())
            },
            dismissButton = { AppButton("Cancel", close, kind = ButtonKind.Text) },
        )
    }

    confirmDelete?.let { gr ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            containerColor = c.surface,
            title = { Text("Delete ${gr.name}?", style = t.lg, color = c.text) },
            text = { Text("The ${count(gr)} in it stay on your list, just without a group.", style = t.sm, color = c.textMuted) },
            confirmButton = {
                AppButton("Delete", {
                    confirmDelete = null
                    scope.launch {
                        g.repo.deleteGroup(gr.id)
                        chrome.snackbar.showSnackbar("${gr.name} deleted")
                    }
                }, kind = ButtonKind.Danger)
            },
            dismissButton = { AppButton("Cancel", { confirmDelete = null }, kind = ButtonKind.Text) },
        )
    }
}
