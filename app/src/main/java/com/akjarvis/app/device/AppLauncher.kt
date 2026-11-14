package com.akjarvis.app.device

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.akjarvis.app.domain.model.InstalledApp

/** Lists and launches installed apps for "open WhatsApp" style commands. */
class AppLauncher(private val context: Context) {

    fun installedApps(): List<InstalledApp> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .map { InstalledApp(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    /** Launches the best fuzzy label match. Returns the matched app or null. */
    fun launchBestMatch(target: String): InstalledApp? {
        val wanted = normalize(target)
        if (wanted.isBlank()) return null
        val apps = installedApps()
        val match = apps.firstOrNull { normalize(it.label) == wanted }
            ?: apps.firstOrNull { normalize(it.label).startsWith(wanted) }
            ?: apps.firstOrNull { normalize(it.label).contains(wanted) }
            ?: apps.firstOrNull { wanted.contains(normalize(it.label)) }
        return match?.also { launch(it.packageName) }
    }

    fun launch(packageName: String): Boolean = runCatching {
        context.startActivity(
            context.packageManager.getLaunchIntentForPackage(packageName)
                ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ?: return false
        )
        true
    }.getOrDefault(false)

    private fun normalize(text: String) = text.lowercase().replace(Regex("[^a-z0-9]"), "")
}

/** Maps settings keywords to official Android Settings screens. */
object SettingsShortcuts {
    fun intentFor(key: String): Intent? = when (key) {
        "wifi" -> Intent(Settings.ACTION_WIFI_SETTINGS)
        "bluetooth" -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
        "airplane" -> Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)
        "display" -> Intent(Settings.ACTION_DISPLAY_SETTINGS)
        "sound" -> Intent(Settings.ACTION_SOUND_SETTINGS)
        "battery" -> Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
        "storage" -> Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)
        "location" -> Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
        "apps" -> Intent(Settings.ACTION_APPLICATION_SETTINGS)
        "security" -> Intent(Settings.ACTION_SECURITY_SETTINGS)
        "datetime" -> Intent(Settings.ACTION_DATE_SETTINGS)
        "accessibility" -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        "notifaccess" -> Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        "main" -> Intent(Settings.ACTION_SETTINGS)
        else -> null
    }
}

/** Opens the default browser with a search query. */
object WebSearchHelper {
    fun search(context: Context, query: String): Boolean {
        val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val fallback = Intent(Intent.ACTION_WEB_SEARCH)
            .putExtra("query", query)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return (runCatching { context.startActivity(web); true }.getOrElse { false } ||
            runCatching { context.startActivity(fallback); true }.getOrElse { false })
    }
}
