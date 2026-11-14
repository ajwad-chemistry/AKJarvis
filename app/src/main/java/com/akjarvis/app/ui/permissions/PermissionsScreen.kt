package com.akjarvis.app.ui.permissions

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.akjarvis.app.device.AutomationService
import com.akjarvis.app.device.NotificationListener

private data class PermissionStatuses(
    val microphone: Boolean,
    val notifications: Boolean,
    val exactAlarms: Boolean,
    val notificationAccess: Boolean,
    val contacts: Boolean,
    val accessibility: Boolean,
)

private fun readStatuses(context: Context): PermissionStatuses {
    val mic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED
    val notifications = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
    val exact = if (Build.VERSION.SDK_INT < 31) true else {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.canScheduleExactAlarms()
    }
    val contacts = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
        PackageManager.PERMISSION_GRANTED
    return PermissionStatuses(
        mic, notifications, exact,
        NotificationListener.hasAccess(context),
        contacts,
        AutomationService.hasAccess(context),
    )
}

/**
 * Central permission management. Every sensitive capability is listed with its
 * live status, a one-tap grant path, and a note on how to revoke it.
 */
@Composable
fun PermissionsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var refreshKey by remember { mutableIntStateOf(0) }
    var statuses by remember { mutableStateOf(readStatuses(context)) }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                statuses = readStatuses(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        statuses = readStatuses(context)
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        statuses = readStatuses(context)
    }
    val contactsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        statuses = readStatuses(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text("Permissions", style = MaterialTheme.typography.titleLarge)
        }
        Text(
            "AK Jarvis only uses these permissions after you allow them, and every one of them can be revoked here or in Android settings.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp),
        )

        PermissionRow(
            title = "Microphone",
            description = "Voice input and optional wake word. The mic is active only during a listening session.",
            granted = statuses.microphone,
            onAction = { micLauncher.launch(Manifest.permission.RECORD_AUDIO) },
        )
        PermissionRow(
            title = "Notifications",
            description = "Needed for task reminders.",
            granted = statuses.notifications,
            onAction = {
                if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            },
        )
        PermissionRow(
            title = "Exact alarms",
            description = "Makes reminders fire on time. Without it, reminders may be a few minutes late.",
            granted = statuses.exactAlarms,
            onAction = {
                if (Build.VERSION.SDK_INT >= 31) {
                    runCatching {
                        context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                    }
                }
            },
        )
        PermissionRow(
            title = "Notification access",
            description = "Optional. Only used when you ask me to read notifications. Revoke any time.",
            granted = statuses.notificationAccess,
            onAction = {
                runCatching {
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }
            },
        )
        PermissionRow(
            title = "Contacts",
            description = "Optional. Only used to find a phone number when you say \"mama ko call karo\". The dialer still opens — nothing is called automatically.",
            granted = statuses.contacts,
            onAction = { contactsLauncher.launch(Manifest.permission.READ_CONTACTS) },
        )
        PermissionRow(
            title = "Mobile control (accessibility)",
            description = "Optional. Advanced control: back, home, recents, scroll, tap, type, read screen. Works only after you enable it in system settings, and only when you give a command. Password fields are never read.",
            granted = statuses.accessibility,
            onAction = {
                runCatching {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
            },
        )
        Text(
            text = "Nothing is recorded, uploaded or shared. Revoking a permission immediately disables the related feature.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Composable
private fun PermissionRow(
    title: String,
    description: String,
    granted: Boolean,
    onAction: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (granted) "✅" else "⭕",
                modifier = Modifier.padding(end = 8.dp),
            )
            Text(title, style = MaterialTheme.typography.titleMedium)
        }
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 34.dp, top = 2.dp, bottom = 6.dp),
        )
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            if (granted) {
                OutlinedButton(onClick = onAction) { Text("Manage") }
            } else {
                Button(onClick = onAction) { Text("Allow") }
            }
        }
    }
}
