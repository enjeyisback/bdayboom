package app.bdayboom.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.bdayboom.ui.components.AppButton
import app.bdayboom.ui.components.ButtonKind
import app.bdayboom.ui.theme.AppTheme
import app.bdayboom.ui.theme.Space

/** "Update available" dialog, shown over any screen once a newer GitHub release is found. */
@Composable
fun UpdatePrompt() {
    val um = UpdateManager.get(LocalContext.current)
    val state by um.state.collectAsState()
    val dismissed by um.dialogDismissed.collectAsState()
    val info = when (val s = state) {
        is UpdateState.Available -> s.info
        is UpdateState.Downloading -> s.info
        else -> null
    }
    if (info == null || dismissed) return
    val downloading = state as? UpdateState.Downloading
    val c = AppTheme.colors
    val t = AppTheme.type

    AlertDialog(
        onDismissRequest = { if (downloading == null) um.dismissDialog() },
        containerColor = c.surface,
        title = { Text("Update available · v${info.version}", style = t.lg, color = c.text) },
        text = {
            Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                Text(info.notes.ifBlank { "A new version is ready." }.take(800), style = t.sm, color = c.textMuted)
                if (downloading != null) {
                    LinearProgressIndicator(
                        progress = { downloading.progress },
                        modifier = Modifier.fillMaxWidth().padding(top = Space.s4),
                        color = c.accent, trackColor = c.surface2,
                    )
                    Text("Downloading ${(downloading.progress * 100).toInt()}%", style = t.xs, color = c.textMuted, modifier = Modifier.padding(top = Space.s2))
                }
            }
        },
        confirmButton = {
            AppButton(if (downloading == null) "Update now" else "Downloading…", { um.installDownloaded(info) }, loading = downloading != null)
        },
        dismissButton = {
            if (downloading == null) AppButton("Later", { um.dismissDialog() }, kind = ButtonKind.Text)
        },
    )
}
