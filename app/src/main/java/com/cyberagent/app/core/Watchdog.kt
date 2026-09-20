package com.cyberagent.app.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.PowerManager
import com.cyberagent.app.receiver.SmsDeliverReceiver
import com.cyberagent.app.service.CallGuardService
import com.cyberagent.app.service.FirewallVpnService

/**
 * Vigilante de estado: detecta cuándo la protección deja de funcionar de
 * verdad (y no solo en la pantalla) y lo avisa una vez por cambio:
 *
 *  · Cortafuegos activado pero SIN túnel (conflicto con otra VPN o consentimiento revocado).
 *  · Rol de SMS, rol de llamadas o accesibilidad revocados desde Ajustes.
 *  · App con la batería restringida (la defensa automática se dormiría).
 */
object Watchdog {

    fun checkAndAlert(ctx: Context) {
        try {
            firewallTunnel(ctx)
            roles(ctx)
            battery(ctx)
        } catch (t: Throwable) {
            CrashLog.record(ctx, "Watchdog", t)
        }
    }

    private fun firewallTunnel(ctx: Context) {
        val enabled = Prefs.firewallEnabled
        val running = FirewallVpnService.isTunnelRunning()
        if (enabled && !running) {
            val vpnActive = otherVpnLikelyActive(ctx)
            val key = if (vpnActive) "vpn_conflict" else "tunnel_down"
            if (Prefs.lastWatchKey == key) return
            Prefs.lastWatchKey = key
            Db.addAlert(
                SecurityAlert(
                    severity = "ALTA",
                    module = "Vigilante",
                    title = if (vpnActive) "Otra VPN está ocupando el túnel"
                    else "El cortafuegos no está en marcha",
                    detail = if (vpnActive)
                        "Android solo permite una VPN a la vez. El bloqueo por aplicación no se está " +
                            "aplicando. Desactiva la otra VPN o vuelve a activar CyberAgent."
                    else
                        "El cortafuegos figura activado pero no hay túnel establecido. " +
                            "Ábrelo otra vez para reconectar (o comprueba el consentimiento de VPN).",
                    mitre = "T1562"
                )
            )
            Notify.post(ctx, "CyberAgent: cortafuegos inactivo", "El bloqueo de red por app no se está aplicando.")
        } else if (Prefs.lastWatchKey == "vpn_conflict" || Prefs.lastWatchKey == "tunnel_down") {
            Prefs.lastWatchKey = ""
        }
    }

    private fun otherVpnLikelyActive(ctx: Context): Boolean {
        return try {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        } catch (t: Throwable) {
            false
        }
    }

    private fun roles(ctx: Context) {
        val sms = SmsDeliverReceiver.isDefaultSmsApp(ctx)
        val call = CallGuardService.hasRole(ctx)
        val acc = Perms.accessibilityEnabled(ctx)
        val now = "$sms|$call|$acc"
        val was = Prefs.lastRoleState
        if (was.isNotEmpty() && was != now) {
            val lost = ArrayList<String>()
            val w = was.split('|')
            if (w.size == 3) {
                if (w[0] == "true" && !sms) lost.add("app de SMS predeterminada")
                if (w[1] == "true" && !call) lost.add("rol de filtrado de llamadas")
                if (w[2] == "true" && !acc) lost.add("servicio de accesibilidad")
            }
            if (lost.isNotEmpty()) {
                Db.addAlert(
                    SecurityAlert(
                        severity = "ALTA",
                        module = "Vigilante",
                        title = "Protección desactivada desde Ajustes",
                        detail = "Se ha perdido: " + lost.joinToString(", ") +
                            ". Sin ello esa defensa deja de actuar. Vuelve a concederlo en el " +
                            "asistente de accesos.",
                        mitre = "T1562"
                    )
                )
                Notify.post(ctx, "CyberAgent: protección desactivada", "Se perdió: " + lost.joinToString(", "))
            }
        }
        Prefs.lastRoleState = now
    }

    private fun battery(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        val free = pm.isIgnoringBatteryOptimizations(ctx.packageName)
        if (!free && Prefs.lastWatchKey != "battery") {
            Prefs.lastWatchKey = "battery"
            Db.addAlert(
                SecurityAlert(
                    severity = "MEDIA",
                    module = "Vigilante",
                    title = "Batería optimizada: la defensa automática puede dormirse",
                    detail = "Android puede retrasar o cancelar los escaneos en segundo plano. " +
                        "En el asistente de accesos tienes el paso «Batería sin restricciones».",
                    mitre = "T1562"
                )
            )
        } else if (free && Prefs.lastWatchKey == "battery") {
            Prefs.lastWatchKey = ""
        }
    }
}
