package com.akjarvis.app.device

import android.app.Notification
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class NotifInfo(val packageName: String, val title: String, val text: String, val postedAt: Long)

/**
 * Reads notifications ONLY after the user explicitly enables Notification
 * Listener access for this app in system settings. The latest few notifications
 * are kept in memory so the user can ask "read my notifications".
 * Nothing is uploaded anywhere.
 */
class NotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val extras = sbn.notification?.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return
        val info = NotifInfo(sbn.packageName, title, text, sbn.postTime)
        synchronized(lock) {
            val updated = (listOf(info) + recentNotifications).take(MAX_KEEP)
            recentNotifications = updated
            _recentFlow.value = updated
        }
    }

    override fun onListenerDisconnected() {
        stopSelf()
    }

    companion object {
        private const val MAX_KEEP = 25
        private val lock = Any()
        private var recentNotifications: List<NotifInfo> = emptyList()
        private val _recentFlow = MutableStateFlow<List<NotifInfo>>(emptyList())
        val recentFlow: StateFlow<List<NotifInfo>> = _recentFlow

        fun recent(): List<NotifInfo> = synchronized(lock) { recentNotifications }

        /** True when the user has granted listener access in system settings. */
        fun hasAccess(context: Context): Boolean =
            NotificationManagerCompat.getEnabledListenerPackages(context)
                .contains(context.packageName)
    }
}
