package com.cyberagent.app.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.cyberagent.app.App
import com.cyberagent.app.MainActivity
import com.cyberagent.app.R
import com.cyberagent.app.core.Db
import com.cyberagent.app.core.Prefs
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Monitor de seguridad en primer plano: mantiene el estado del sistema y
 * registra eventos periódicos. Sin salida a Internet.
 */
class SecurityMonitorService : Service() {

    private var scheduler: ScheduledExecutorService? = null
    private var lastUsageTotal = 0L
    private var lastCheck = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        // Android 14+ exige declarar el tipo de FGS también al llamarlo.
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else 0
        try {
            ServiceCompat.startForeground(
                this, NOTIF_ID,
                buildNotification("Monitor activo", "Vigilando el dispositivo"),
                type
            )
        } catch (t: Throwable) {
            com.cyberagent.app.core.CrashLog.record(this, "SecurityMonitorService.startForeground", t)
            stopSelf()
            return
        }
        scheduler = Executors.newSingleThreadScheduledExecutor().also {
            it.scheduleWithFixedDelay(::tick, 5, 60, TimeUnit.SECONDS)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun tick() {
        try {
            // 1. Picos de consumo de red (requiere acceso al uso).
            val total = if (com.cyberagent.app.core.NetworkStatsReader.hasUsageAccess(this)) {
                com.cyberagent.app.core.NetworkStatsReader.totalForToday(this)
            } else 0L

            if (lastUsageTotal > 0 && total - lastUsageTotal > PEAK_THRESHOLD) {
                Db.addAlert(
                    com.cyberagent.app.core.SecurityAlert(
                        severity = "MEDIA",
                        module = "Red",
                        title = "Pico de tráfico detectado",
                        detail = "Se transfirieron más de " +
                            com.cyberagent.app.core.NetworkStatsReader.formatBytes(total - lastUsageTotal) +
                            " en un minuto. Revisa qué aplicación está enviando datos.",
                        mitre = "T1041"
                    )
                )
            }
            lastUsageTotal = total
            lastCheck = System.currentTimeMillis()

            updateNotification(
                "Monitor activo",
                "Hoy: ${com.cyberagent.app.core.NetworkStatsReader.formatBytes(total)} · " +
                    "${Db.countAlerts(lastCheck - 86_400_000L)} eventos en 24 h"
            )
        } catch (t: Throwable) {
            // nunca debe tumbar el servicio
        }
    }

    private fun buildNotification(title: String, text: String): Notification {
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, App.CHAN_MONITOR)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pi)
            .build()
    }

    private fun updateNotification(title: String, text: String) {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        nm.notify(NOTIF_ID, buildNotification(title, text))
    }

    override fun onDestroy() {
        scheduler?.shutdownNow()
        scheduler = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.cyberagent.app.action.STOP_MONITOR"
        private const val NOTIF_ID = 1001
        private const val PEAK_THRESHOLD = 25L * 1024 * 1024 // 25 MB en un minuto

        fun start(ctx: android.content.Context) {
            val i = Intent(ctx, SecurityMonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ctx.startForegroundService(i)
            } else {
                ctx.startService(i)
            }
        }

        fun stop(ctx: android.content.Context) {
            ctx.stopService(Intent(ctx, SecurityMonitorService::class.java))
        }

        fun enabled(): Boolean = true
    }
}
