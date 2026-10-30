package app.bdayboom.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import app.bdayboom.BuildConfig
import app.bdayboom.graph
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * Weekly backup into a folder the user picked once (a document tree, so it can be Downloads, an SD card or a
 * folder a sync app watches). Still offline: the app only writes a file. The daily worker calls [runIfDue].
 */
object AutoBackup {
    const val KEEP = 5
    private const val PREFIX = "BdayBoom-auto-"
    private val WEEK = TimeUnit.DAYS.toMillis(7) - TimeUnit.HOURS.toMillis(2) // slack: the worker doesn't run on the dot

    fun isDue(last: Long, now: Long) = now - last >= WEEK

    /** Our automatic backups beyond the newest [KEEP]. Names sort by date because the date is in them. */
    fun toPrune(names: List<String>): List<String> =
        names.filter { it.startsWith(PREFIX) && it.endsWith(".zip") }.sortedDescending().drop(KEEP)

    fun runIfDue(context: Context) {
        val s = context.graph.settings.now()
        val folder = s.autoBackupFolder ?: return
        if (isDue(s.autoBackupLast, System.currentTimeMillis())) run(context, folder)
    }

    /** Writes one backup now and prunes old ones. Records the result in settings; returns success. */
    fun run(context: Context, folder: String): Boolean {
        val g = context.graph
        val ok = runCatching {
            val tree = Uri.parse(folder)
            val r = context.contentResolver
            val treeId = DocumentsContract.getTreeDocumentId(tree)
            val parent = DocumentsContract.buildDocumentUriUsingTree(tree, treeId)
            val name = PREFIX + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm")) + ".zip"
            val doc = DocumentsContract.createDocument(r, parent, "application/zip", name) ?: error("folder refused the file")
            r.openOutputStream(doc)!!.use { g.backup.write(it, BuildConfig.VERSION_NAME) }

            val children = mutableMapOf<String, String>() // display name -> document id
            r.query(
                DocumentsContract.buildChildDocumentsUriUsingTree(tree, treeId),
                arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null, null, null,
            )?.use { c -> while (c.moveToNext()) children[c.getString(1)] = c.getString(0) }
            toPrune(children.keys.toList()).forEach { n ->
                runCatching { DocumentsContract.deleteDocument(r, DocumentsContract.buildDocumentUriUsingTree(tree, children.getValue(n))) }
            }
        }.isSuccess
        kotlinx.coroutines.runBlocking { g.settings.setAutoBackupResult(ok, System.currentTimeMillis()) }
        return ok
    }

    /** The folder's name as the file picker shows it, or null if it can't be read any more. */
    fun folderName(context: Context, folder: String): String? = runCatching {
        val tree = Uri.parse(folder)
        val doc = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        context.contentResolver.query(doc, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }.getOrNull()
}
