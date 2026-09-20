package com.cyberagent.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.cyberagent.app.core.Db
import com.cyberagent.app.core.Prefs

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        step("crashGuard") { installCrashGuard() }
        step("prefs") { Prefs.init(this) }
        step("db") { Db.init(this) }
        step("canales") { createChannels() }
        step("autoDefensa") { scheduleAutoDefense() }
    }

    /** Ejecuta un paso de arranque sin que un fallo tumbe toda la aplicación. */
    private inline fun step(name: String, block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            com.cyberagent.app.core.CrashLog.record(this, "App.onCreate/$name", t)
        }
    }

    /** Programa (o reprograma) la defensa automática según los ajustes. */
    private fun scheduleAutoDefense() {
        try {
            if (Prefs.autoDefenseEnabled) com.cyberagent.app.core.AutoDefense.schedule(this)
            else com.cyberagent.app.core.AutoDefense.cancel(this)
        } catch (t: Throwable) {
            // el sistema puede limitar la programación en segundo plano
        }
    }

    /**
     * Red de seguridad: si algo falla en runtime, se guarda el stack trace en
     * fichero y se avisa por notificación (no se oculta el problema).
     */
    private fun installCrashGuard() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val trace = android.util.Log.getStackTraceString(error)
                java.io.File(filesDir, "last-crash.txt").writeText(
                    "Thread: ${thread.name}\n\n$trace"
                )
                val nm = getSystemService(NotificationManager::class.java)
                nm?.notify(
                    9999,
                    android.app.Notification.Builder(this, CHAN_ALERTS)
                        .setSmallIcon(R.drawable.ic_launcher)
                        .setContentTitle("CyberAgent: error interno")
                        .setContentText(error.javaClass.simpleName + ": " +
                            (error.message ?: "sin detalle"))
                        .setStyle(
                            android.app.Notification.BigTextStyle()
                                .bigText(trace.takeLast(1500))
                        )
                        .build()
                )
            } catch (t: Throwable) {
                // nunca enmascarar el error original
            }
            previous?.uncaughtException(thread, error)
        }
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(
                CHAN_MONITOR,
                getString(R.string.chan_monitor),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.chan_monitor_desc)
                setShowBadge(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CHAN_ALERTS,
                getString(R.string.chan_alerts),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = getString(R.string.chan_alerts_desc) }
        )
    }

    companion object {
        const val CHAN_MONITOR = "cyberagent_monitor"
        const val CHAN_ALERTS = "cyberagent_alerts"

        lateinit var instance: App
            private set
    }
}
