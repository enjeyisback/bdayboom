package app.bdayboom.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SdCard
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
import app.bdayboom.BuildConfig
import app.bdayboom.data.AppSettings
import app.bdayboom.data.AutoBackup
import app.bdayboom.data.BackupException
import app.bdayboom.data.BackupPreview
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import app.bdayboom.graph
import app.bdayboom.ui.LocalChrome
import app.bdayboom.ui.components.AppButton
import app.bdayboom.ui.components.Banner
import app.bdayboom.ui.components.BannerKind
import app.bdayboom.ui.components.ButtonKind
import app.bdayboom.ui.components.SectionHeader
import app.bdayboom.ui.components.appCard
import app.bdayboom.ui.theme.AppTheme
import app.bdayboom.ui.theme.Space
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** S17 backup & restore to a file. Offline by choice: no cloud backup. */
@Composable
fun BackupScreen() {
    val context = LocalContext.current
    val g = context.graph
    val chrome = LocalChrome.current
    val scope = rememberCoroutineScope()
    val c = AppTheme.colors
    val t = AppTheme.type
    var busy by remember { mutableStateOf(false) }
    var pendingRestore by remember { mutableStateOf<Pair<Uri, BackupPreview>?>(null) }
    val settings by g.settings.flow.collectAsState(initial = AppSettings())
    val folder = settings.autoBackupFolder
    val folderName = remember(folder) { folder?.let { AutoBackup.folderName(context, it) } }

    fun say(msg: String) = scope.launch { chrome.snackbar.showSnackbar(msg) }

    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openOutputStream(uri)!!.use { g.backup.write(it, BuildConfig.VERSION_NAME) } }
            }
            busy = false
            say(if (result.isSuccess) "Backup saved" else "Couldn't save the backup. Try another folder.")
        }
    }
    // BUG-001: read the file first and show what's in it, so the wrong backup isn't restored by accident.
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val preview = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)!!.use { g.backup.preview(it) } }
            }
            preview.fold(
                onSuccess = { pendingRestore = uri to it },
                onFailure = { e -> say((e as? BackupException)?.message ?: "Couldn't read that file.") },
            )
        }
    }

    fun backUpNow(uri: String) {
        busy = true
        scope.launch {
            val ok = withContext(Dispatchers.IO) { AutoBackup.run(context, uri) }
            busy = false
            say(if (ok) "Backup saved in the folder" else "Couldn't save there. Choose the folder again.")
        }
    }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        // Keep access after a restart, so the weekly backup can write without asking again.
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        scope.launch {
            g.settings.setAutoBackupFolder(uri.toString())
            backUpNow(uri.toString())
        }
    }
    fun turnOff() = scope.launch {
        folder?.let { f -> runCatching { context.contentResolver.releasePersistableUriPermission(Uri.parse(f), Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) } }
        g.settings.setAutoBackupFolder(null)
        say("Automatic backup is off")
    }

    val safetyCopy = remember { java.io.File(context.filesDir, "before-restore.zip") }

    fun restore(uri: Uri, replace: Boolean) {
        pendingRestore = null
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    // A Replace wipes the list: keep a copy of it first so it can be undone.
                    if (replace) safetyCopy.outputStream().use { g.backup.write(it, BuildConfig.VERSION_NAME) }
                    context.contentResolver.openInputStream(uri)!!.use { g.backup.restore(it, replace) }
                }
            }
            busy = false
            result.fold(
                onSuccess = { r ->
                    val msg = buildString {
                        append(if (r.added == 1) "1 person restored" else "${r.added} people restored")
                        if (r.skipped > 0) append(" · ${r.skipped} already here")
                    }
                    if (!replace) {
                        say(msg)
                    } else {
                        val undo = chrome.snackbar.showSnackbar(msg, actionLabel = "Undo", duration = SnackbarDuration.Long)
                        if (undo == SnackbarResult.ActionPerformed) {
                            val back = withContext(Dispatchers.IO) { runCatching { safetyCopy.inputStream().use { g.backup.restore(it, replace = true) } } }
                            say(if (back.isSuccess) "Your list is back as it was" else "Couldn't undo. Your previous list is saved in the app.")
                        }
                    }
                },
                onFailure = { e -> say((e as? BackupException)?.message ?: "Couldn't read that file.") },
            )
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTop("Backup & restore", "Keep your list safe")
        ScrollScreen {
            SectionHeader("Backup file", icon = Icons.Outlined.SdCard)
            Column(Modifier.padding(horizontal = Space.s4).fillMaxWidth().appCard().padding(Space.s4), verticalArrangement = Arrangement.spacedBy(Space.s3)) {
                Text(
                    "Save everyone, their notes, groups and your reminder settings in one file. Keep it somewhere safe, " +
                        "like your Downloads or a USB drive, and open it on a new phone to bring everything back.",
                    style = t.sm, color = c.textMuted,
                )
                AppButton(
                    "Save a backup", {
                        save.launch("BdayBoom-backup-${java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm"))}.zip")
                    },
                    Modifier.fillMaxWidth(), icon = Icons.Outlined.FileDownload, loading = busy,
                )
                AppButton(
                    "Restore from a file", { open.launch(arrayOf("application/zip", "application/octet-stream")) },
                    Modifier.fillMaxWidth(), ButtonKind.Secondary, Icons.Outlined.FileUpload, enabled = !busy,
                )
            }

            SectionHeader("Automatic backup", icon = Icons.Outlined.Schedule)
            Column(Modifier.padding(horizontal = Space.s4).fillMaxWidth().appCard().padding(Space.s4), verticalArrangement = Arrangement.spacedBy(Space.s3)) {
                if (folder == null) {
                    Text(
                        "Pick a folder once and BdayBoom saves a backup there every week. It keeps the last ${AutoBackup.KEEP}. " +
                            "Tip: a folder your cloud app syncs gives you an off-phone copy too.",
                        style = t.sm, color = c.textMuted,
                    )
                    AppButton("Choose a folder", { pickFolder.launch(null) }, Modifier.fillMaxWidth(), icon = Icons.Outlined.Schedule, enabled = !busy)
                } else {
                    if (settings.autoBackupFailed) {
                        Banner("The last automatic backup couldn't be saved. Choose the folder again.", BannerKind.Warning) { pickFolder.launch(null) }
                    }
                    Text("Every week to ${folderName ?: "your folder"}", style = t.base, color = c.text)
                    Text(
                        if (settings.autoBackupLast == 0L) "No backup yet"
                        else "Last saved " + java.time.Instant.ofEpochMilli(settings.autoBackupLast).atZone(java.time.ZoneId.systemDefault())
                            .format(java.time.format.DateTimeFormatter.ofLocalizedDateTime(java.time.format.FormatStyle.MEDIUM, java.time.format.FormatStyle.SHORT)),
                        style = t.sm, color = c.textMuted,
                    )
                    AppButton("Back up now", { backUpNow(folder) }, Modifier.fillMaxWidth(), ButtonKind.Secondary, loading = busy)
                    AppButton("Change folder", { pickFolder.launch(null) }, Modifier.fillMaxWidth(), ButtonKind.Text, enabled = !busy)
                    AppButton("Turn off", { turnOff() }, Modifier.fillMaxWidth(), ButtonKind.Text, enabled = !busy)
                }
            }

            SectionHeader("Private by design", icon = Icons.Outlined.Lock)
            Text(
                "Your list lives only on this phone. A backup file goes only where you save it.",
                style = t.sm, color = c.textMuted, modifier = Modifier.padding(horizontal = Space.s4),
            )
            Spacer(Modifier.height(Space.s8))
        }
    }

    pendingRestore?.let { (uri, preview) ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            containerColor = c.surface,
            title = { Text("Restore this backup?", style = t.lg, color = c.text) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Space.s3)) {
                    Column(Modifier.fillMaxWidth().appCard(fill = c.surface2).padding(Space.s3)) {
                        Text(
                            if (preview.people == 1) "1 person" else "${preview.people} people",
                            style = t.title, color = c.text,
                        )
                        Text(
                            listOfNotNull(
                                savedOn(preview.exportedAt),
                                if (preview.photos > 0) "${preview.photos} ${if (preview.photos == 1) "photo" else "photos"}" else null,
                            ).joinToString(" · "),
                            style = t.sm, color = c.textMuted,
                        )
                    }
                    Text(
                        "Add only adds people who aren't already on your list. Replace clears your list first, and you can undo it right after.",
                        style = t.sm, color = c.textMuted,
                    )
                }
            },
            confirmButton = {
                Column(verticalArrangement = Arrangement.spacedBy(Space.s2)) {
                    AppButton("Add to my list", { restore(uri, replace = false) }, Modifier.fillMaxWidth())
                    AppButton("Replace my list", { restore(uri, replace = true) }, Modifier.fillMaxWidth(), ButtonKind.Danger)
                    AppButton("Cancel", { pendingRestore = null }, Modifier.fillMaxWidth(), ButtonKind.Text)
                }
            },
        )
    }
}

/** "Saved 30 Oct 2026, 1:58 pm" from the backup's exported_at, or null if it's missing or unreadable. */
private fun savedOn(iso: String): String? = runCatching {
    val t = java.time.LocalDateTime.parse(iso)
    "Saved " + t.format(java.time.format.DateTimeFormatter.ofLocalizedDateTime(java.time.format.FormatStyle.MEDIUM, java.time.format.FormatStyle.SHORT))
}.getOrNull()
