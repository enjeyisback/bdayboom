package app.bdayboom.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import app.bdayboom.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** A newer release found on GitHub. */
data class UpdateInfo(val version: String, val notes: String, val apkUrl: String, val sizeBytes: Long)

sealed class UpdateState {
    data object Idle : UpdateState()
    data object Checking : UpdateState()
    data object UpToDate : UpdateState()
    data class Available(val info: UpdateInfo) : UpdateState()
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateState()
    data class Failed(val message: String) : UpdateState()
}

/**
 * Same approach as Playgram: looks for a newer release in the public GitHub repo set as UPDATE_REPO in
 * local.properties ("owner/name"), downloads its .apk and hands it to Android's installer.
 * The release tag is the version ("v1.1.0" or "1.1.0"); the first .apk asset is the update.
 * The only network use in the app: release metadata and the APK. No user data is sent.
 */
class UpdateManager private constructor(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs = context.getSharedPreferences("updates", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /** "Later" hides the dialog until the next app start; Settings still shows the update. */
    private val _dialogDismissed = MutableStateFlow(false)
    val dialogDismissed: StateFlow<Boolean> = _dialogDismissed.asStateFlow()
    fun dismissDialog() { _dialogDismissed.value = true }

    val enabled: Boolean get() = BuildConfig.UPDATE_REPO.contains('/')

    /** Automatic check on app start, at most every 6 hours. */
    fun checkIfDue() {
        if (!enabled) return
        if (System.currentTimeMillis() - prefs.getLong("last_check", 0L) < 6 * 3600_000L) return
        check()
    }

    fun check() {
        if (!enabled || _state.value is UpdateState.Checking || _state.value is UpdateState.Downloading) return
        _dialogDismissed.value = false // a manual check brings the dialog back even after "Later"
        _state.value = UpdateState.Checking
        scope.launch {
            _state.value = try {
                val info = fetchLatest()
                prefs.edit().putLong("last_check", System.currentTimeMillis()).apply()
                if (info != null && isNewer(info.version, BuildConfig.VERSION_NAME)) UpdateState.Available(info) else UpdateState.UpToDate
            } catch (e: Exception) {
                Log.w(TAG, "Update check failed", e)
                UpdateState.Failed("Couldn't check for updates. Check your connection and try again.")
            }
        }
    }

    private fun fetchLatest(): UpdateInfo? {
        val conn = URL("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest").openConnection() as HttpURLConnection
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.setRequestProperty("User-Agent", "BdayBoom/${BuildConfig.VERSION_NAME}")
        conn.connectTimeout = 15_000; conn.readTimeout = 15_000
        if (conn.responseCode == 404) return null // no release yet, or the repo is private
        val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        val assets = json.optJSONArray("assets") ?: return null
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            if (a.getString("name").endsWith(".apk", ignoreCase = true)) {
                return UpdateInfo(
                    version = json.getString("tag_name").trim().removePrefix("v").removePrefix("V"),
                    notes = json.optString("body", "").trim(),
                    // The API asset URL is reachable on networks that block github.com itself; it redirects to the file.
                    apkUrl = a.getString("url"),
                    sizeBytes = a.optLong("size", 0L),
                )
            }
        }
        return null
    }

    fun download(info: UpdateInfo) {
        if (_state.value is UpdateState.Downloading) return
        _state.value = UpdateState.Downloading(info, 0f)
        scope.launch {
            try {
                val file = apkFile(info).apply { parentFile?.apply { mkdirs(); listFiles()?.forEach { it.delete() } } }
                val conn = URL(info.apkUrl).openConnection() as HttpURLConnection
                conn.setRequestProperty("User-Agent", "BdayBoom/${BuildConfig.VERSION_NAME}")
                conn.setRequestProperty("Accept", "application/octet-stream")
                conn.connectTimeout = 15_000; conn.readTimeout = 30_000
                val total = conn.contentLengthLong.takeIf { it > 0 } ?: info.sizeBytes
                conn.inputStream.use { input ->
                    file.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        var lastEmit = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            done += n
                            val now = System.currentTimeMillis()
                            if (total > 0 && now - lastEmit > 200) {
                                lastEmit = now
                                _state.value = UpdateState.Downloading(info, (done.toFloat() / total).coerceIn(0f, 1f))
                            }
                        }
                    }
                }
                if (info.sizeBytes > 0 && file.length() != info.sizeBytes) error("size mismatch: ${file.length()} != ${info.sizeBytes}")
                _state.value = UpdateState.Available(info)
                install(file)
            } catch (e: Exception) {
                Log.w(TAG, "Update download failed", e)
                _state.value = UpdateState.Failed("Download failed. Try again.")
            }
        }
    }

    /**
     * Hands the APK to Android's PackageInstaller (a session, not the "open this file" screen). That makes BdayBoom
     * the installer of record, so on Android 12+ updates install without a confirmation screen. Older Android, and
     * some OEM skins, still show one. Android itself checks the signature matches; Play Protect may still scan,
     * which an app can't (and shouldn't) skip.
     */
    private fun install(apk: File) {
        if (!context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return // back from that screen, "Update now" installs the file that is already downloaded
        }
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("base.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val result = PendingIntent.getBroadcast(
                context, id, Intent(context, InstallResultReceiver::class.java),
                // Mutable: the system adds the status extras to this intent.
                PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0),
            )
            session.commit(result.intentSender)
        }
    }

    internal fun installFailed() {
        _state.value = UpdateState.Failed("The update didn't install. Try again.")
    }

    /** Installs an already downloaded update (after the permission was granted), or downloads it again. */
    fun installDownloaded(info: UpdateInfo) {
        val file = apkFile(info)
        if (file.exists() && file.length() > 0 && (info.sizeBytes <= 0 || file.length() == info.sizeBytes)) install(file) else download(info)
    }

    private fun apkFile(info: UpdateInfo) = File(File(context.cacheDir, "updates"), "BdayBoom-${info.version}.apk")

    companion object {
        private const val TAG = "Updates"

        @Volatile private var instance: UpdateManager? = null
        fun get(context: Context): UpdateManager =
            instance ?: synchronized(this) { instance ?: UpdateManager(context.applicationContext).also { instance = it } }

        /** "1.10.0" is newer than "1.9.2": compares the dotted numbers one by one. */
        fun isNewer(candidate: String, current: String): Boolean {
            val a = candidate.split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
            val b = current.split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
            for (i in 0 until maxOf(a.size, b.size)) {
                val x = a.getOrElse(i) { 0 }
                val y = b.getOrElse(i) { 0 }
                if (x != y) return x > y
            }
            return false
        }
    }
}

/** Result of an install session: shows Android's confirm screen when it asks for one, reports failures. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            PackageInstaller.STATUS_SUCCESS -> Unit // this process is replaced; the app comes back on the next open
            else -> {
                Log.w("Updates", "install failed: $status ${intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)}")
                // Cancelled by the user is not an error worth a message.
                if (status != PackageInstaller.STATUS_FAILURE_ABORTED) UpdateManager.get(context).installFailed()
            }
        }
    }
}
