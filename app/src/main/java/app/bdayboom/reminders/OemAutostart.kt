package app.bdayboom.reminders

import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * MIUI (Xiaomi, Redmi, POCO) refuses to start an app for the boot broadcast unless the user turns on
 * "Autostart", so reminders silently stop after a restart. Verified on a Redmi K20 / MIUI 12:
 * logcat shows "process is not permitted to auto start" for BOOT_COMPLETED.
 */
object OemAutostart {
    private const val MIUI_OP_AUTO_START = 10008

    val isXiaomi: Boolean
        get() = Build.MANUFACTURER.lowercase().let { it == "xiaomi" || it == "redmi" || it == "poco" }

    /**
     * true = on, false = off, null = not a Xiaomi phone or can't tell (never claim "off" when unsure).
     * MIUI keeps the switch as a private app-op; read via reflection, as open-source autostart helpers do.
     */
    fun isEnabled(context: Context): Boolean? {
        if (!isXiaomi) return null
        return runCatching {
            val ops = context.getSystemService(AppOpsManager::class.java)
            val check = AppOpsManager::class.java.getMethod("checkOpNoThrow", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, String::class.java)
            (check.invoke(ops, MIUI_OP_AUTO_START, context.applicationInfo.uid, context.packageName) as Int) == AppOpsManager.MODE_ALLOWED
        }.getOrNull()
    }

    /** MIUI's Autostart list if present, else this app's system info page. */
    fun settingsIntent(context: Context): Intent {
        val miui = Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"))
        return if (isXiaomi && miui.resolveActivity(context.packageManager) != null) miui
        else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
    }
}
