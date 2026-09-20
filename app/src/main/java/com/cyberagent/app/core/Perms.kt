package com.cyberagent.app.core

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * Estado centralizado de TODOS los accesos que necesita CyberAgent.
 *
 * Android no permite pedir de golpe los permisos especiales (acceso al uso,
 * accesibilidad, roles de SMS/llamadas, No Molestar, VPN): cada uno tiene su
 * propia pantalla del sistema. Aquí se comprueban y [PermissionsActivity] los
 * recorre uno a uno al iniciar la app.
 */
object Perms {

    fun granted(ctx: Context, perm: String): Boolean =
        ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED

    fun missing(ctx: Context, perms: List<String>): List<String> = perms.filter { !granted(ctx, it) }

    /** Permisos de runtime (diálogo normal) que la app usa. */
    fun runtimeWanted(): List<String> {
        val out = ArrayList<String>()
        if (Build.VERSION.SDK_INT >= 33) out.add(Manifest.permission.POST_NOTIFICATIONS)
        if (Build.VERSION.SDK_INT >= 33) out.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        out.add(Manifest.permission.READ_PHONE_STATE)
        out.add(Manifest.permission.READ_CALL_LOG)
        out.add(Manifest.permission.READ_CONTACTS)
        out.add(Manifest.permission.RECEIVE_SMS)
        out.add(Manifest.permission.READ_SMS)
        out.add(Manifest.permission.RECEIVE_MMS)
        return out
    }

    /** Los que sin ellos la app queda a medias (los que bloqueaban el arranque). */
    fun critical(): List<String> {
        val out = ArrayList<String>()
        out.add(Manifest.permission.READ_PHONE_STATE)
        out.add(Manifest.permission.RECEIVE_SMS)
        out.add(Manifest.permission.READ_SMS)
        return out
    }

    fun usageAccess(ctx: Context): Boolean = NetworkStatsReader.hasUsageAccess(ctx)

    fun accessibilityEnabled(ctx: Context): Boolean {
        val svc = ctx.packageName + "/com.cyberagent.app.service.SecurityAccessibilityService"
        val enabled = Settings.Secure.getString(
            ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: ""
        return enabled.split(':').any { it.equals(svc, true) }
    }

    fun notificationPolicy(ctx: Context): Boolean = try {
        (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .isNotificationPolicyAccessGranted
    } catch (t: Throwable) {
        false
    }

    fun notificationsAllowed(ctx: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 33) granted(ctx, Manifest.permission.POST_NOTIFICATIONS) else true
}
