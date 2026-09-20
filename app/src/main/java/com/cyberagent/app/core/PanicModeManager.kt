package com.cyberagent.app.core

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings

/**
 * Modo pánico. Acciones reales sobre el dispositivo:
 *  · Cortafuegos en modo estricto: bloquea la red de TODAS las apps que no
 *    estén en la lista blanca del usuario.
 *  · Activa No Molestar si el sistema lo permite.
 *  · Notificación persistente con el estado.
 */
object PanicModeManager {

    fun enable(ctx: Context) {
        Prefs.panicMode = true
        Prefs.firewallEnabled = true
        FirewallBridge.requestRestart(ctx)
        setNoDisturb(ctx, true)
        Db.addAlert(
            SecurityAlert(
                severity = "CRITICA",
                module = "Modo pánico",
                title = "Modo pánico activado",
                detail = "Cortafuegos en modo estricto: solo las apps marcadas como de " +
                    "confianza mantienen acceso a la red. No Molestar activado si estaba disponible.",
                mitre = "T1562"
            )
        )
    }

    fun disable(ctx: Context) {
        Prefs.panicMode = false
        setNoDisturb(ctx, false)
        FirewallBridge.requestRestart(ctx)
        Db.addAlert(
            SecurityAlert(
                severity = "INFO",
                module = "Modo pánico",
                title = "Modo pánico desactivado",
                detail = "Se restaura la política de red configurada por el usuario."
            )
        )
    }

    fun setNoDisturb(ctx: Context, enable: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        if (!nm.isNotificationPolicyAccessGranted) {
            Prefs.noDisturbEnabled = false
            return
        }
        try {
            nm.setInterruptionFilter(
                if (enable) NotificationManager.INTERRUPTION_FILTER_PRIORITY
                else NotificationManager.INTERRUPTION_FILTER_ALL
            )
            Prefs.noDisturbEnabled = enable
        } catch (t: Throwable) {
            Prefs.noDisturbEnabled = false
        }
    }

    fun dndSettingsIntent(): Intent =
        Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)

    /**
     * Lista efectiva de paquetes a bloquear: los marcados a mano más, en modo
     * pánico, todas las apps de usuario que no estén en la lista blanca.
     */
    fun effectiveBlockList(ctx: Context, installedUserApps: Set<String>): Set<String> {
        val base = Prefs.blockedPackages().toMutableSet()
        if (Prefs.panicMode || ScheduleGuard.isActiveNow()) {
            val allow = Prefs.trustedPackages() // apps de confianza (no teléfonos)
            for (p in installedUserApps) {
                if (!allow.contains(p)) base.add(p)
            }
        }
        return base
    }
}
