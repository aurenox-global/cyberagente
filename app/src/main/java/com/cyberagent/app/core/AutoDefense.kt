package com.cyberagent.app.core

import android.app.role.RoleManager
import android.content.Context

/**
 * Defensa automática («0 clics»): escaneo programado del dispositivo y
 * actuación sin intervención del usuario.
 *
 * Qué hace en cada pasada:
 *  1. Audita las apps instaladas y **aísla automáticamente** las que superan el
 *     umbral de riesgo (cortándoles la red).
 *  2. Analiza la red Wi-Fi activa y avisa si es insegura.
 *  3. Vigila el consumo de red y detecta picos anómalos.
 *  4. Deja un resumen en el historial de alertas.
 *
 * Redes de seguridad: nunca actúa sobre apps de sistema ni sobre las que
 * ocupan un rol crítico (launcher, teléfono, SMS, teclado).
 */
object AutoDefense {

    const val DEFAULT_THRESHOLD = 70
    const val DEFAULT_INTERVAL_MIN = 60

    /** Ejecuta una pasada completa y devuelve un resumen legible. */
    fun runOnce(ctx: Context, notify: Boolean = true): String {
        val sb = StringBuilder()
        var actions = 0

        // ── 0. Horario y permisos que han cambiado ───────────────────
        ScheduleGuard.applyIfNeeded(ctx)
        val permChanges = PermissionWatcher.diff(ctx)
        if (permChanges.isNotEmpty()) {
            actions += permChanges.size
            sb.append("Permisos sensibles modificados en ${permChanges.size} app(s).\n")
        }

        // ── 1. Apps ──────────────────────────────────────────────────
        val apps = AppAnalyzer.analyzeAll(ctx)
        val threshold = Prefs.autoQuarantineThreshold
        var quarantined = 0
        for (a in apps) {
            if (a.packageName == ctx.packageName) continue
            if (isCriticalRole(ctx, a.packageName)) continue
            if (a.score < threshold) continue
            // Apps de tienda oficial: solo se aíslan con evidencia muy fuerte.
            // Su score alto suele venir de permisos legítimos, y cortarles la red
            // automáticamente sería un falso positivo molesto.
            if (a.fromStore && a.score < STORE_STRICT_SCORE) continue
            if (Prefs.quarantine().contains(a.packageName)) continue
            QuarantineManager.quarantine(
                ctx, a.packageName,
                "Aislamiento automático: score ${a.score}/100 (umbral $threshold). " +
                    a.reasons.take(2).joinToString("; ")
            )
            quarantined++
            actions++
        }
        sb.append("Apps auditadas: ${apps.size}. Aisladas: $quarantined.\n")
        Prefs.lastAppsAnalyzed = apps.size

        // ── 2. Wi-Fi ─────────────────────────────────────────────────
        val wifi = WifiAnalyzer.analyze(ctx)
        if (wifi.score >= 35) {
            Db.addAlert(
                SecurityAlert(
                    severity = "MEDIA",
                    module = "Auto-defensa",
                    title = "Red Wi-Fi insegura: ${wifi.ssid} (${wifi.security})",
                    detail = wifi.findings.joinToString("\n"),
                    mitre = "T1557"
                )
            )
            actions++
            sb.append("Wi-Fi insegura detectada: ${wifi.ssid} · ${wifi.security}.\n")
        }

        // ── 2b. Red: portal cautivo, DNS cambiado, Wi-Fi abierta ─────
        for (f in NetGuard.checkAndAlert(ctx)) {
            actions++
            sb.append("Red: ").append(f.lineSequence().first()).append('\n')
        }

        // ── 3. Red: pico de consumo ──────────────────────────────────
        if (NetworkStatsReader.hasUsageAccess(ctx)) {
            val now = NetworkStatsReader.totalForToday(ctx)
            val prev = Prefs.lastUsageTotal
            if (prev > 0 && now - prev > PEAK_BYTES) {
                Db.addAlert(
                    SecurityAlert(
                        severity = "MEDIA",
                        module = "Auto-defensa",
                        title = "Pico de tráfico desde la última revisión",
                        detail = NetworkStatsReader.formatBytes(now - prev) +
                            " transferidos. Revisa qué aplicación está enviando datos.",
                        mitre = "T1041"
                    )
                )
                actions++
                sb.append("Pico de tráfico: ${NetworkStatsReader.formatBytes(now - prev)}.\n")
            }
            Prefs.lastUsageTotal = now
            sb.append("Consumo de hoy: ${NetworkStatsReader.formatBytes(now)}.\n")
        } else {
            sb.append("Sin acceso al uso: el monitor de red no puede medir.\n")
        }

        Prefs.lastAutoScan = System.currentTimeMillis()
        Prefs.scanCount = Prefs.scanCount + 1
        sb.append("Acciones automáticas: $actions.")

        // Refresca los widgets con los contadores nuevos.
        com.cyberagent.app.widget.SecurityDashboardWidget.notifyUpdate(ctx)

        Db.addAlert(
            SecurityAlert(
                severity = "INFO",
                module = "Auto-defensa",
                title = "Escaneo automático completado",
                detail = sb.toString()
            )
        )

        if (notify) {
            Notify.post(ctx, "Escaneo automático", sb.toString())
        }
        return sb.toString()
    }

    // ── Programación con JobScheduler (sin dependencias extra) ───────
    fun schedule(ctx: Context) {
        val js = ctx.getSystemService(android.app.job.JobScheduler::class.java) ?: return
        val interval = Prefs.autoScanIntervalMin.toLong()
        val component = android.content.ComponentName(ctx, com.cyberagent.app.service.ScanJobService::class.java)
        val info = android.app.job.JobInfo.Builder(JOB_ID, component)
            .setRequiredNetworkType(android.app.job.JobInfo.NETWORK_TYPE_NONE)
            .setPersisted(true)
            .setPeriodic(interval * 60_000L)
            .setOverrideDeadline(interval * 60_000L)
            .build()
        try {
            js.schedule(info)
        } catch (t: Throwable) {
            // algunos fabricantes limitan setPeriodic; se reintenta en el próximo arranque
        }
    }

    fun cancel(ctx: Context) {
        val js = ctx.getSystemService(android.app.job.JobScheduler::class.java) ?: return
        js.cancel(JOB_ID)
    }

    /**
     * Lanza un escaneo inmediato (botón "Escanear" del widget) usando el
     * JobScheduler: el trabajo corre aunque el widget se cierre enseguida.
     */
    fun runNow(ctx: Context) {
        val js = ctx.getSystemService(android.app.job.JobScheduler::class.java) ?: return
        val info = android.app.job.JobInfo.Builder(
            JOB_ID_NOW,
            android.content.ComponentName(ctx, com.cyberagent.app.service.ScanJobService::class.java)
        )
            .setOverrideDeadline(0)
            .build()
        try {
            js.schedule(info)
        } catch (t: Throwable) {
            // si el sistema no lo acepta, no pasa nada: el periódico lo hará
        }
    }

    /** ¿La app ocupa un rol crítico que no debemos bloquear nunca? */
    fun isCriticalRole(ctx: Context, pkg: String): Boolean {
        return try {
            // SMS predeterminada
            @Suppress("DEPRECATION")
            if (android.provider.Telephony.Sms.getDefaultSmsPackage(ctx) == pkg) return true

            // Teléfono predeterminado
            val tm = ctx.getSystemService(android.telecom.TelecomManager::class.java)
            if (tm?.defaultDialerPackage == pkg) return true

            // Launcher
            @Suppress("DEPRECATION")
            val home = ctx.packageManager.resolveActivity(
                android.content.Intent(android.content.Intent.ACTION_MAIN)
                    .addCategory(android.content.Intent.CATEGORY_HOME), 0
            )?.activityInfo?.packageName
            home == pkg
        } catch (t: Throwable) {
            false
        }
    }

    private const val JOB_ID = 8801
    private const val JOB_ID_NOW = 8802
    private const val PEAK_BYTES = 100L * 1024 * 1024 // 100 MB entre pasadas

    /** Umbral extra para apps procedentes de una tienda de aplicaciones. */
    private const val STORE_STRICT_SCORE = 95
}

/** Notificaciones del sistema para los avisos automáticos. */
object Notify {
    fun post(ctx: Context, title: String, text: String) {
        try {
            val nm = ctx.getSystemService(android.app.NotificationManager::class.java) ?: return
            val pi = android.app.PendingIntent.getActivity(
                ctx, 0,
                android.content.Intent(ctx, com.cyberagent.app.MainActivity::class.java),
                android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
            )
            val n = androidx.core.app.NotificationCompat.Builder(ctx, com.cyberagent.app.App.CHAN_ALERTS)
                .setSmallIcon(com.cyberagent.app.R.drawable.ic_launcher)
                .setContentTitle(title)
                .setContentText(text.lineSequence().firstOrNull() ?: text)
                .setStyle(
                    androidx.core.app.NotificationCompat.BigTextStyle().bigText(text)
                )
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build()
            nm.notify((System.currentTimeMillis() % 100000).toInt(), n)
        } catch (t: Throwable) {
            // nunca romper por una notificación
        }
    }
}
