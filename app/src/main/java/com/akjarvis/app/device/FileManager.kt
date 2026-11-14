package com.akjarvis.app.device

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

/**
 * File access goes through Android's Storage Access Framework: the user picks
 * the file themselves, so the app never browses private storage silently.
 */
object FileManager {

    fun describe(context: Context, uri: Uri): String {
        val name = runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
            }
        }.getOrNull() ?: uri.lastPathSegment ?: "file"
        val size = runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (idx >= 0 && cursor.moveToFirst()) cursor.getLong(idx) else -1L
            }
        }.getOrNull() ?: -1L
        return if (size >= 0) "$name (${size / 1024} KB)" else name
    }
}
