package app.bdayboom.ui.screens

import android.content.Intent
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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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

@Composable
fun ExportScreen() {
    val context = LocalContext.current
    val g = context.graph
    val chrome = LocalChrome.current
    val scope = rememberCoroutineScope()
    val c = AppTheme.colors
    val t = AppTheme.type

    val people by g.repo.people.collectAsState(initial = emptyList())
    val groups by g.repo.groups.collectAsState(initial = emptyList())

    fun say(msg: String) = scope.launch { chrome.snackbar.showSnackbar(msg) }

    fun generateCsv(): String {
        val groupMap = groups.associate { it.id to it.name }
        val sb = StringBuilder()
        sb.append("Name,Type,Date of Birth,Month,Day,Year,Second Event Label,Second Date,Second Month,Second Day,Second Year,Group,Notes\n")
        for (p in people) {
            val typeStr = p.type.name
            val groupStr = p.groupId?.let { groupMap[it] } ?: ""
            val dob = if (p.year != null) "%04d-%02d-%02d".format(p.year, p.month, p.day) else "--%02d-%02d".format(p.month, p.day)
            val secDob = if (p.secondMonth != null && p.secondDay != null) {
                if (p.secondYear != null) "%04d-%02d-%02d".format(p.secondYear, p.secondMonth, p.secondDay)
                else "--%02d-%02d".format(p.secondMonth, p.secondDay)
            } else ""
            val secLabel = p.secondLabel ?: ""
            val secM = p.secondMonth?.toString() ?: ""
            val secD = p.secondDay?.toString() ?: ""
            val secY = p.secondYear?.toString() ?: ""
            sb.append("${escapeCsv(p.name)},$typeStr,$dob,${p.month},${p.day},${p.year ?: ""},${escapeCsv(secLabel)},$secDob,$secM,$secD,$secY,${escapeCsv(groupStr)},${escapeCsv(p.notes)}\n")
        }
        return sb.toString()
    }

    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)!!.use { out ->
                        out.write(generateCsv().toByteArray(Charsets.UTF_8))
                    }
                }
            }
            say(if (result.isSuccess) "CSV file saved" else "Couldn't save the CSV file.")
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTop("Export as CSV", "Spreadsheet export")
        ScrollScreen {
            SectionHeader("Export birthdays", icon = Icons.Outlined.TableChart)
            Column(
                Modifier.padding(horizontal = Space.s4).fillMaxWidth().appCard().padding(Space.s4),
                verticalArrangement = Arrangement.spacedBy(Space.s3),
            ) {
                Text("Export all entries as a CSV file to open in Excel, Google Sheets, or other apps.", style = t.base, color = c.text)
                Text("${people.size} entries ready to export", style = t.sm, color = c.textMuted)

                Spacer(Modifier.height(Space.s1))

                AppButton(
                    label = "Save as CSV",
                    icon = Icons.Outlined.FileDownload,
                    kind = ButtonKind.Primary,
                    onClick = { saveLauncher.launch("birthdays-${LocalDate.now()}.csv") },
                    modifier = Modifier.fillMaxWidth(),
                )

                AppButton(
                    label = "Share CSV",
                    icon = Icons.Outlined.Share,
                    kind = ButtonKind.Secondary,
                    onClick = {
                        val csv = generateCsv()
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, csv)
                            putExtra(Intent.EXTRA_SUBJECT, "Birthdays Export")
                            type = "text/plain"
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "Share CSV"))
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private fun escapeCsv(value: String): String {
    if (value.contains(',') || value.contains('"') || value.contains('\n') || value.contains('\r')) {
        return "\"${value.replace("\"", "\"\"")}\""
    }
    return value
}
