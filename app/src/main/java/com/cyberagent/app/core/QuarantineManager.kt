package com.cyberagent.app.core

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Centro de cuarentena. Acciones REALES (no simuladas):
 *  1. Aísla la app cortándole la red mediante el cortafuegos VPN.
 *  2. Registra el evento en la base de alertas.
 *  3. Ofrece la desinstalación.
 *  4. Si la app es administradora del dispositivo, suspende el paquete.
 */
object QuarantineManager {

    fun quarantine(ctx: Context, pkg: String, reason: String) {
        Prefs.addQuarantine(pkg)
        Prefs.blockPackage(pkg)
        Db.addAlert(
            SecurityAlert(
                severity = "ALTA",
                module = "Cuarentena",
                title = "App aislada: $pkg",
                detail = "$reason\nRed bloqueada por cortafuegos local. " +
                    "Se puede desinstalar o restaurar desde el Centro de cuarentena.",
                packageName = pkg,
                mitre = "T1071"
            )
        )
        FirewallBridge.requestRestart(ctx)
    }

    fun release(ctx: Context, pkg: String) {
        Prefs.removeQuarantine(pkg)
        Prefs.unblockPackage(pkg)
        Db.addAlert(
            SecurityAlert(
                severity = "INFO",
                module = "Cuarentena",
                title = "App liberada: $pkg",
                detail = "Se restauró el acceso a la red para $pkg.",
                packageName = pkg
            )
        )
        FirewallBridge.requestRestart(ctx)
    }

    fun suspendIfPossible(ctx: Context, pkg: String, suspended: Boolean): Boolean {
        return try {
            val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            if (!dpm.isDeviceOwnerApp(ctx.packageName)) return false
            dpm.setPackagesSuspended(ComponentName(ctx, AdminReceiver::class.java), arrayOf(pkg), suspended)
            true
        } catch (t: Throwable) {
            false
        }
    }

    fun uninstallIntent(pkg: String): Intent =
        Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
}

/** Receptor de administración, necesario para la suspensión opcional. */
class AdminReceiver : android.app.admin.DeviceAdminReceiver()

/** Puente para pedir al cortafuegos que recalcule su lista. */
object FirewallBridge {
    const val ACTION_REFRESH = "com.cyberagent.app.action.FIREWALL_REFRESH"

    fun requestRestart(ctx: Context) {
        if (!Prefs.firewallEnabled) return
        val i = Intent(ctx, com.cyberagent.app.service.FirewallVpnService::class.java)
        i.action = ACTION_REFRESH
        try {
            ctx.startService(i)
        } catch (t: Throwable) {
            // El servicio se reiniciará la próxima vez que se abra la app.
        }
    }
}
