package app.bdayboom.ui.screens

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
import androidx.compose.material.icons.outlined.SdCard
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.bdayboom.BuildConfig
import app.bdayboom.data.BackupException
import app.bdayboom.graph
import app.bdayboom.ui.LocalChrome
import app.bdayboom.ui.components.AppButton
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
    var pendingRestore by remember { mutableStateOf<Uri?>(null) }

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
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> pendingRestore = uri }

    fun restore(uri: Uri, replace: Boolean) {
        pendingRestore = null
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)!!.use { g.backup.restore(it, replace) } }
            }
            busy = false
            result.fold(
                onSuccess = { r ->
                    say(
                        buildString {
                            append(if (r.added == 1) "1 person restored" else "${r.added} people restored")
                            if (r.skipped > 0) append(" · ${r.skipped} already here")
                        },
                    )
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
                    "Save a backup", { save.launch("birthdays-backup-${LocalDate.now()}.zip") },
                    Modifier.fillMaxWidth(), icon = Icons.Outlined.FileDownload, loading = busy,
                )
                AppButton(
                    "Restore from a file", { open.launch(arrayOf("application/zip", "application/octet-stream")) },
                    Modifier.fillMaxWidth(), ButtonKind.Secondary, Icons.Outlined.FileUpload, enabled = !busy,
                )
            }

            SectionHeader("Private by design", icon = Icons.Outlined.Lock)
            Text(
                "Your list lives only on this phone. A backup file goes only where you save it.",
                style = t.sm, color = c.textMuted, modifier = Modifier.padding(horizontal = Space.s4),
            )
            Spacer(Modifier.height(Space.s8))
        }
    }

    pendingRestore?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            containerColor = c.surface,
            title = { Text("Restore this backup?", style = t.lg, color = c.text) },
            text = {
                Text(
                    "Add only adds people who aren't already on your list. Replace clears your list first, then restores the backup.",
                    style = t.sm, color = c.textMuted,
                )
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
