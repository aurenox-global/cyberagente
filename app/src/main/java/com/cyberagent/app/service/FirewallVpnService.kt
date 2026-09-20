package com.cyberagent.app.service

import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.cyberagent.app.App
import com.cyberagent.app.R
import com.cyberagent.app.core.Db
import com.cyberagent.app.core.DnsFilter
import com.cyberagent.app.core.FirewallBridge
import com.cyberagent.app.core.PanicModeManager
import com.cyberagent.app.core.Prefs
import com.cyberagent.app.core.SecurityAlert
import java.io.FileInputStream

/**
 * Cortafuegos local implementado como servicio VPN.
 *
 * Mecanismo REAL de bloqueo por aplicación:
 *  · Los paquetes de las apps a bloquear se enrutan al túnel y se DESCARTAN.
 *  · Las apps no incluidas siguen usando la red normal (no se ven afectadas),
 *    por lo que no se rompe la conectividad del resto del sistema.
 *  · No hay servidor remoto: no sale ni un byte del dispositivo.
 */
class FirewallVpnService : VpnService() {

    private var tun: ParcelFileDescriptor? = null
    private var worker: Thread? = null

    @Volatile private var running = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            FirewallBridge.ACTION_REFRESH -> {
                if (Prefs.firewallEnabled) {
                    stopTunnel(notify = false)
                    startTunnel()
                }
            }
            ACTION_STOP -> stopTunnel()
            else -> if (Prefs.firewallEnabled) startTunnel()
        }
        return START_STICKY
    }

    private fun startTunnel() {
        val blocked = PanicModeManager.effectiveBlockList(this, installedUserApps())
        if (blocked.isEmpty()) {
            stopSelf()
            return
        }

        val builder = Builder()
            .setSession("CyberAgent Firewall")
            .addAddress("10.111.222.1", 32)
            .addRoute("0.0.0.0", 0)
            .addDnsServer("10.111.222.1")
            // IPv6: sin esta ruta, una app bloqueada podía seguir saliendo por
            // IPv6 (fuga del bloqueo) en redes con direccionamiento IPv6.
            .addAddress("fd00:111:222::1", 128)
            .addRoute("::", 0)
            .addDnsServer("fd00:111:222::1")

        var count = 0
        for (pkg in blocked) {
            try {
                builder.addAllowedApplication(pkg)
                count++
            } catch (t: Throwable) {
                // paquete no instalable/visible: se ignora
            }
        }
        if (count == 0) {
            stopSelf()
            return
        }

        // Evita que nuestra propia app quede bloqueada.
        try {
            builder.addDisallowedApplication(packageName)
        } catch (t: Throwable) {
            // ignorar
        }

        if (Build.VERSION.SDK_INT >= 29) {
            builder.setMetered(false)
        }

        val fd = try {
            builder.establish()
        } catch (t: Throwable) {
            null
        }
        if (fd == null) {
            Db.addAlert(
                SecurityAlert(
                    severity = "MEDIA",
                    module = "Cortafuegos",
                    title = "No se pudo activar el cortafuegos",
                    detail = "El sistema denegó el permiso de VPN. Vuelve a concederlo desde la app."
                )
            )
            Prefs.firewallEnabled = false
            stopSelf()
            return
        }

        tun = fd
        running = true

        // Android 14+ (targetSdk 34+) EXIGE indicar el tipo de servicio en
        // primer plano. Sin esto el sistema lanza MissingForegroundServiceTypeException
        // y mata la app al activar el cortafuegos. VpnService -> systemExempted.
        val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED
        } else 0
        try {
            ServiceCompat.startForeground(
                this, NOTIF_ID,
                notify("Cortafuegos activo", "$count app(s) sin acceso a la red"),
                fgsType
            )
        } catch (t: Throwable) {
            running = false
            try {
                fd.close()
            } catch (ignored: Throwable) {
            }
            tun = null
            Prefs.firewallEnabled = false
            Db.addAlert(
                SecurityAlert(
                    severity = "MEDIA",
                    module = "Cortafuegos",
                    title = "No se pudo iniciar el cortafuegos",
                    detail = "El sistema rechazó el servicio en primer plano (" +
                        t.javaClass.simpleName + "). El cortafuegos queda desactivado."
                )
            )
            stopSelf()
            return
        }
        tunnelUp = true

        worker = Thread {
            val buf = ByteArray(32 * 1024)
            try {
                FileInputStream(fd.fileDescriptor).use { input ->
                    java.io.FileOutputStream(fd.fileDescriptor).use { output ->
                        while (running) {
                            val n = input.read(buf)
                            if (n <= 0) break
                            // Las consultas DNS de las apps bloqueadas se responden
                            // con NXDOMAIN: fallan al instante en vez de quedarse
                            // esperando (menos batería y datos). El resto del tráfico
                            // se descarta: es el bloqueo.
                            val q = DnsFilter.parseQueryName(buf, n)
                            val resp = if (q != null && DnsFilter.isBlocked(this, q)) {
                                DnsFilter.buildNxDomain(buf, n)
                            } else null
                            if (resp != null) {
                                blockedDomains++
                                try {
                                    output.write(resp)
                                } catch (ignored: Throwable) {
                                }
                            }
                            blockedPackets++
                            if (blockedPackets % 64 == 0L) {
                                updateNotify(
                                    "$count app(s) bloqueadas · $blockedPackets paquetes · " +
                                        "$blockedDomains dominios filtrados"
                                )
                            }
                        }
                    }
                }
            } catch (t: Throwable) {
                // el túnel se cerró
            }
        }.also { it.isDaemon = true; it.start() }

        Db.addAlert(
            SecurityAlert(
                severity = "INFO",
                module = "Cortafuegos",
                title = "Cortafuegos VPN activo",
                detail = "$count aplicación(es) con la red bloqueada. El tráfico bloqueado " +
                    "se descarta en el dispositivo; no se envía a ningún servidor.",
                mitre = "T1562"
            )
        )
    }

    private fun installedUserApps(): Set<String> {
        return try {
            val pm = packageManager
            @Suppress("DEPRECATION")
            pm.getInstalledApplications(0)
                .filter { (it.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0 }
                .map { it.packageName }
                .toSet()
        } catch (t: Throwable) {
            emptySet()
        }
    }

    private fun stopTunnel(notify: Boolean = true) {
        running = false
        tunnelUp = false
        try {
            worker?.interrupt()
        } catch (t: Throwable) {
        }
        worker = null
        try {
            tun?.close()
        } catch (t: Throwable) {
        }
        tun = null
        if (notify) stopForegroundCompat()
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION") stopForeground(true)
        }
    }

    private fun notify(title: String, text: String) =
        NotificationCompat.Builder(this, App.CHAN_MONITOR)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0, Intent(this, com.cyberagent.app.MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            .build()

    private fun updateNotify(text: String) {
        try {
            getSystemService(android.app.NotificationManager::class.java)
                ?.notify(NOTIF_ID, notify("Cortafuegos activo", text))
        } catch (t: Throwable) {
        }
    }

    override fun onRevoke() {
        Prefs.firewallEnabled = false
        stopTunnel()
        super.onRevoke()
    }

    override fun onDestroy() {
        stopTunnel(notify = false)
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.cyberagent.app.action.STOP_FIREWALL"
        private const val NOTIF_ID = 1002
        @Volatile var blockedPackets: Long = 0
        @Volatile var blockedDomains: Long = 0

        @Volatile private var tunnelUp = false

        /** ¿Hay túnel establecido ahora mismo? (lo comprueba el vigilante) */
        fun isTunnelRunning(): Boolean = tunnelUp

        fun start(ctx: android.content.Context) {
            val i = Intent(ctx, FirewallVpnService::class.java)
            Prefs.firewallEnabled = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ctx.startForegroundService(i)
            } else {
                ctx.startService(i)
            }
        }

        fun stop(ctx: android.content.Context) {
            Prefs.firewallEnabled = false
            ctx.stopService(Intent(ctx, FirewallVpnService::class.java))
        }
    }
}
