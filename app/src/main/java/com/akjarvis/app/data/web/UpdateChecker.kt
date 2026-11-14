package com.akjarvis.app.data.web

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * In-app update checker (pattern borrowed from Mobile-Harness): reads a small
 * update.json from the project's GitHub repository and compares version codes.
 * No store, no tracking — just a plain file the owner bumps on release.
 */
object UpdateChecker {

    /** Keep in sync with versionCode/versionName in app/build.gradle.kts. */
    const val CURRENT_VERSION = "1.2.0"
    const val CURRENT_CODE = 3

    private const val URL =
        "https://raw.githubusercontent.com/ajwad-chemistry/AKJarvis/main/update.json"

    data class UpdateInfo(
        val versionName: String,
        val versionCode: Int,
        val notes: String,
        val apkUrl: String,
    )

    /** Returns [UpdateInfo] when a newer version exists, null when up to date or offline. */
    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val element = Http.getJson(URL) ?: return@runCatching null
            val code = element.int("versionCode") ?: return@runCatching null
            if (code <= CURRENT_CODE) return@runCatching null
            UpdateInfo(
                versionName = element.str("versionName") ?: "?",
                versionCode = code,
                notes = element.str("notes") ?: "",
                apkUrl = element.str("apkUrl") ?: "",
            )
        }.getOrNull()
    }
}
