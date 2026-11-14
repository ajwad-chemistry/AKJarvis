package com.akjarvis.app.device

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * Bridge to the Termux:API ecosystem. AK Jarvis never runs shell commands
 * itself; it only forwards a small allow-list of safe, read-only termux-api
 * commands to the Termux RunCommandService, which the user must have
 * installed and configured (allow-external-apps) themselves.
 *
 * Setup required by the user (documented in docs/LIMITATIONS.md):
 *  1. Install Termux (F-Droid build) and Termux:API.
 *  2. In Termux run: echo "allow-external-apps=true" >> ~/.termux/termux.properties
 *  3. Grant AK Jarvis the RUN_COMMAND permission when Android asks.
 */
object TermuxBridge {

    const val PERMISSION = "com.termux.permission.RUN_COMMAND"
    private const val TERMUX_PACKAGE = "com.termux"
    private const val RUN_COMMAND_ACTION = "com.termux.RUN_COMMAND"
    private const val RUN_COMMAND_SERVICE = "com.termux.app.RunCommandService"
    private const val EXTRA_COMMAND_PATH = "com.termux.RUN_COMMAND_PATH"
    private const val EXTRA_ARGUMENTS = "com.termux.RUN_COMMAND_ARGUMENTS"
    private const val EXTRA_RUN_IN_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND"

    /** Only safe, non-destructive termux-api tools may be forwarded. */
    private val allowedBinaries = setOf(
        "termux-battery-status",
        "termux-toast",
        "termux-tts-speak",
        "termux-vibrate",
        "termux-volume",
        "termux-wifi-connectioninfo",
        "termux-telephony-deviceinfo",
        "termux-notification",
    )

    fun isTermuxInstalled(context: Context): Boolean = runCatching {
        context.packageManager.getPackageInfo(TERMUX_PACKAGE, 0)
        true
    }.getOrDefault(false)

    fun hasRunCommandPermission(context: Context): Boolean =
        context.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED

    sealed class Result {
        data object Sent : Result()
        data object TermuxMissing : Result()
        data object PermissionMissing : Result()
        data object Blocked : Result()
    }

    /**
     * Forwards a command to Termux. The first token must be one of the
     * allowed termux-api binaries; anything else is refused so the bridge
     * can never be used to run arbitrary shell code.
     */
    fun run(context: Context, args: List<String>): Result {
        val command = args.firstOrNull()?.trim()?.lowercase() ?: return Result.Blocked
        if (command !in allowedBinaries) return Result.Blocked
        if (!isTermuxInstalled(context)) return Result.TermuxMissing
        if (!hasRunCommandPermission(context)) return Result.PermissionMissing

        val binary = "/data/data/com.termux/files/usr/bin/$command"
        val rest = args.drop(1).filter { it.isNotBlank() }
        return runCatching {
            val intent = Intent(RUN_COMMAND_ACTION).apply {
                setClassName(TERMUX_PACKAGE, RUN_COMMAND_SERVICE)
                putExtra(EXTRA_COMMAND_PATH, binary)
                putExtra(EXTRA_ARGUMENTS, rest.toTypedArray())
                putExtra(EXTRA_RUN_IN_BACKGROUND, true)
            }
            context.startService(intent)
            Result.Sent
        }.getOrDefault(Result.Blocked)
    }
}
