package com.akjarvis.app.device

import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.view.KeyEvent
import android.os.BatteryManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Small, permission-free device tools exposed as voice/text commands:
 * flashlight, battery status, volume control, clipboard and sharing.
 * All use official Android APIs; nothing is read silently in the background.
 */
object DeviceTools {

    // ---- Flashlight -------------------------------------------------------

    private var torchCameraId: String? = null
    private var torchOn = false

    /** @return true if the torch state changed, false if no flash unit exists. */
    fun setTorch(context: Context, on: Boolean?): Boolean {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            ?: return false
        val cameraId = torchCameraId ?: findFlashCamera(manager) ?: return false
        torchCameraId = cameraId
        val target = on ?: !torchOn
        return runCatching {
            manager.setTorchMode(cameraId, target)
            torchOn = target
            true
        }.getOrDefault(false)
    }

    private fun findFlashCamera(manager: CameraManager): String? =
        runCatching {
            manager.cameraIdList.firstOrNull { id ->
                manager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        }.getOrNull()

    // ---- Battery ----------------------------------------------------------

    data class BatteryInfo(val percent: Int, val charging: Boolean, val healthOk: Boolean)

    fun readBattery(context: Context): BatteryInfo? {
        val intent = context.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return null
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return null
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        val health = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)
        return BatteryInfo(
            percent = level * 100 / scale,
            charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL,
            healthOk = health != BatteryManager.BATTERY_HEALTH_DEAD &&
                health != BatteryManager.BATTERY_HEALTH_OVERHEAT,
        )
    }

    // ---- Volume -----------------------------------------------------------

    enum class VolumeAction { UP, DOWN, MUTE, UNMUTE }

    fun changeVolume(context: Context, action: VolumeAction) {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val direction = when (action) {
            VolumeAction.UP -> AudioManager.ADJUST_RAISE
            VolumeAction.DOWN -> AudioManager.ADJUST_LOWER
            VolumeAction.MUTE -> AudioManager.ADJUST_MUTE
            VolumeAction.UNMUTE -> AudioManager.ADJUST_UNMUTE
        }
        runCatching {
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
        }
    }

    fun currentVolumePercent(context: Context): Int? {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return null
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (max <= 0) return null
        return audio.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / max
    }

    // ---- Clipboard --------------------------------------------------------

    fun copyToClipboard(context: Context, text: String): Boolean = runCatching {
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return false
        manager.setPrimaryClip(ClipData.newPlainText("AK Jarvis", text))
        true
    }.getOrDefault(false)

    // ---- Share ------------------------------------------------------------

    fun shareText(context: Context, text: String): Boolean = runCatching {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    }.getOrDefault(false)

    // ---- Contacts (READ_CONTACTS required, checked by the caller) ----------

    fun hasContactsAccess(context: Context): Boolean =
        context.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    /** Looks up a phone number by (partial) contact name; returns "name|number" or null. */
    fun findContactNumber(context: Context, name: String): Pair<String, String>? {
        if (!hasContactsAccess(context) || name.isBlank()) return null
        return runCatching {
            val cursor = context.contentResolver.query(
                android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER,
                ),
                null,
                null,
                android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC",
            )
            cursor?.use { c ->
                val nameIdx = c.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numIdx = c.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (c.moveToNext()) {
                    val contactName = c.getString(nameIdx) ?: continue
                    if (contactName.contains(name, ignoreCase = true)) {
                        val number = c.getString(numIdx) ?: continue
                        return contactName to number
                    }
                }
            }
            null
        }.getOrNull()
    }

    // ---- Media transport (play/pause/next/previous via official media key events) ----

    enum class MediaAction { PLAY_PAUSE, NEXT, PREVIOUS }

    /** @return true when a media session received the key event. */
    fun mediaControl(context: Context, action: MediaAction): Boolean = runCatching {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        val keyCode = when (action) {
            MediaAction.PLAY_PAUSE -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            MediaAction.NEXT -> KeyEvent.KEYCODE_MEDIA_NEXT
            MediaAction.PREVIOUS -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
        }
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        true
    }.getOrDefault(false)

    // ---- Haptic feedback (used to confirm voice commands) ------------------

    fun buzz(context: Context, millis: Long = 40L) {
        runCatching {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            vibrator?.vibrate(VibrationEffect.createOneShot(millis, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    // ---- Do-not-disturb state (read-only, used by battery/quiet helpers) ---

    fun isDndActive(context: Context): Boolean = runCatching {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        nm?.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
    }.getOrDefault(false)
}
