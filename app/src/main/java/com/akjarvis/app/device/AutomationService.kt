package com.akjarvis.app.device

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Advanced mobile control, powered by Android's official AccessibilityService
 * APIs. It only acts when the user explicitly enables the service in system
 * settings AND issues a command. It never types, taps or reads the screen on
 * its own, never reads password/OTP fields, and can be disabled at any time
 * from Settings -> Permissions or system Accessibility settings.
 */
class AutomationService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _enabled.value = true
        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            notificationTimeout = 100
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (instance == this) {
            instance = null
            _enabled.value = false
        }
        super.onDestroy()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        _enabled.value = false
        return super.onUnbind(intent)
    }

    // ---- Global navigation -------------------------------------------------

    fun goBack(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)
    fun goHome(): Boolean = performGlobalAction(GLOBAL_ACTION_HOME)
    fun openRecents(): Boolean = performGlobalAction(GLOBAL_ACTION_RECENTS)
    fun openNotificationShade(): Boolean = performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
    fun openQuickSettings(): Boolean = performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)

    // ---- Screen reading ----------------------------------------------------

    /** Reads visible on-screen text; password fields are excluded by the system. */
    fun readScreenText(): String? {
        val root = rootInActiveWindow ?: return null
        val parts = mutableListOf<String>()
        collectText(root, parts, depth = 0)
        root.recycle()
        val text = parts.filter { it.isNotBlank() }.joinToString("\n").trim()
        return text.ifBlank { null }
    }

    private fun collectText(node: AccessibilityNodeInfo, out: MutableList<String>, depth: Int) {
        if (depth > 25 || out.size > 120) return
        node.text?.let { if (it.isNotBlank()) out.add(it.toString()) }
        node.contentDescription?.let { if (it.isNotBlank()) out.add(it.toString()) }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                collectText(child, out, depth + 1)
                child.recycle()
            }
        }
    }

    // ---- Screen interaction ------------------------------------------------

    /** Taps the first clickable node whose text/description matches [target]. */
    fun tapOn(target: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val node = findClickableWithText(root, target.lowercase())
        if (node == null) {
            root.recycle()
            return false
        }
        val ok = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        node.recycle()
        root.recycle()
        return ok
    }

    private fun findClickableWithText(node: AccessibilityNodeInfo, target: String): AccessibilityNodeInfo? {
        val text = node.text?.toString()?.lowercase().orEmpty()
        val desc = node.contentDescription?.toString()?.lowercase().orEmpty()
        if ((text.contains(target) || desc.contains(target)) && node.isClickable) return node
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                val found = findClickableWithText(child, target)
                if (found != null) return found
                child.recycle()
            }
        }
        return null
    }

    /** Types [text] into the currently focused editable field (never password fields). */
    fun typeText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val target = findEditableField(root) ?: run { root.recycle(); return false }
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        val ok = target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        target.recycle()
        root.recycle()
        return ok
    }

    private fun findEditableField(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable && !node.isPassword) return node
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                val found = findEditableField(child)
                if (found != null) return found
                child.recycle()
            }
        }
        return null
    }

    /** Vertical swipe gesture on the active window (scroll). */
    fun scroll(up: Boolean): Boolean {
        val metrics = resources.displayMetrics
        val cx = metrics.widthPixels / 2f
        val top = metrics.heightPixels * 0.65f
        val bottom = metrics.heightPixels * 0.30f
        val path = Path().apply {
            moveTo(cx, if (up) top else bottom)
            lineTo(cx, if (up) bottom else top)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 260))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    companion object {
        private val _enabled = MutableStateFlow(false)
        val enabled: StateFlow<Boolean> get() = _enabled
        var instance: AutomationService? = null
            private set

        fun hasAccess(context: Context): Boolean = runCatching {
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
                ?: return false
            val expected = AccessibilityServiceInfo.FEEDBACK_GENERIC
            am.getEnabledAccessibilityServiceList(expected).any {
                it.resolveInfo.serviceInfo.packageName == context.packageName
            }
        }.getOrDefault(false)

        fun openSystemSettings(context: Context): Boolean = runCatching {
            context.startActivity(
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            true
        }.getOrDefault(false)
    }
}
