package id.biz.rrndev.autoclicker.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import id.biz.rrndev.autoclicker.service.AutoClickAccessibilityService

object PermissionUtils {

    /**
     * Returns true when the Auto Clicker accessibility service is enabled by the user.
     *
     * Uses a two-tier check:
     * 1. Live instance check — covers activation via accessibility shortcut (volume key /
     *    floating button) where Settings.Secure is NOT updated by the OS.
     * 2. Settings.Secure string check — covers the normal toggle via Accessibility settings
     *    and the case where the app is freshly opened BEFORE the service has ever bound
     *    (hasEverConnected == false). Once the service has connected at least once in this
     *    process lifetime, we rely solely on Tier 1 to avoid false-positives when the
     *    service was disabled via shortcut (shortcut disable does NOT remove the entry from
     *    Settings.Secure, so Tier 2 would incorrectly return true after a shortcut-disable).
     */
    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        // Tier 1: service is actually running (covers shortcut activation)
        if (AutoClickAccessibilityService.isServiceEnabled()) return true

        // Tier 2: used only before the service has ever connected in this process.
        // After the first bind, Settings.Secure is no longer a reliable indicator
        // (shortcut-disable does not update it).
        if (AutoClickAccessibilityService.hasEverConnected) return false

        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val serviceName =
            "${context.packageName}/${AutoClickAccessibilityService::class.java.name}"
        return enabledServices.contains(serviceName, ignoreCase = true)
    }

    /**
     * Opens accessibility settings deep-linked to this app's service.
     * On API 31+ uses the direct service detail page; on older APIs falls back to the
     * general accessibility list with the service pre-highlighted via a fragment argument.
     */
    fun openAccessibilitySettings(context: Context): Boolean {
        val serviceName =
            "${context.packageName}/${AutoClickAccessibilityService::class.java.name}"

        return runCatching {
            // API 31+: intent that opens the detail page for this specific service
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra(":settings:fragment_args_key", serviceName)
                    putExtra(
                        ":settings:show_fragment_args",
                        android.os.Bundle().also { it.putString(":settings:fragment_args_key", serviceName) }
                    )
                }
                if (intent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(intent)
                    return@runCatching true
                }
            }

            // Fallback: general accessibility settings, service highlighted where supported
            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(":settings:fragment_args_key", serviceName)
            })
            true
        }.getOrDefault(false)
    }

    /**
     * Returns true when the app can draw overlays (SYSTEM_ALERT_WINDOW).
     * Always true below API 23.
     */
    fun canDrawOverlays(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)

    /** Opens the "Draw over other apps" permission screen for this app. */
    fun openOverlayPermissionSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    /**
     * Returns true when the app has permission to post notifications (API 33+).
     * Always true below API 33.
     */
    fun hasNotificationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
    }
}
