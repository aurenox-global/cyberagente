package com.cyberagent.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.cyberagent.app.core.Db
import com.cyberagent.app.core.Prefs
import com.cyberagent.app.core.SecurityAlert
import com.cyberagent.app.service.FirewallVpnService
import com.cyberagent.app.service.SecurityMonitorService

/** Arranque automático seguro (broadcasts protegidos del sistema). */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(ctx: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_LOCKED_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return

        try {
            SecurityMonitorService.start(ctx)
        } catch (t: Throwable) {
            // Android puede limitar el arranque en segundo plano; se reintenta al abrir la app.
        }

        if (Prefs.firewallEnabled) {
            try {
                FirewallVpnService.start(ctx)
            } catch (t: Throwable) {
                // ídem
            }
        }

        Db.addAlert(
            SecurityAlert(
                severity = "INFO",
                module = "Sistema",
                title = "Monitorización restaurada tras el arranque",
                detail = "Servicios reactivados: monitor " +
                    (if (Prefs.firewallEnabled) "+ cortafuegos" else "") + "."
            )
        )
    }
}
