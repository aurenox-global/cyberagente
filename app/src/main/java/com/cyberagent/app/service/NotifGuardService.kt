package com.cyberagent.app.service

import android.app.Notification
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import com.cyberagent.app.core.Db
import com.cyberagent.app.core.LinkInspector
import com.cyberagent.app.core.Notify
import com.cyberagent.app.core.Prefs
import com.cyberagent.app.core.SecurityAlert

/**
 * Guardián de notificaciones (anti-phishing FUERA de los SMS).
 *
 * Revisa los enlaces que aparecen en las notificaciones de CUALQUIER app
 * (WhatsApp, correo, redes) contra la lista local de dominios maliciosos y las
 * heurísticas de [LinkInspector], y avisa si un enlace parece peligroso.
 *
 * Privacidad: solo se guarda el enlace y el nombre de la app, nunca el texto
 * completo del mensaje. Está DESACTIVADO por defecto y requiere que el usuario
 * conceda el acceso a notificaciones (lo pide el asistente de accesos).
 */
class NotifGuardService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        if (!Prefs.notifGuardEnabled) return
        // No analizamos nuestras propias notificaciones ni las del sistema.
        if (sbn.packageName == packageName) return

        val text = extractText(sbn) ?: return
        if (text.length < 4) return

        val urls = LinkInspector.findUrls(text)
        if (urls.isEmpty()) return

        for (url in urls.take(5)) {
            val v = LinkInspector.inspect(this, url)
            if (!v.risky) continue
            if (!shouldReport(v.host)) continue
            Db.addAlert(
                SecurityAlert(
                    severity = v.severity,
                    module = "Guardián de enlaces",
                    title = "Enlace sospechoso en una notificación de ${appLabel(sbn.packageName)}",
                    detail = "Dominio: ${v.host}\n" + v.reasons.joinToString("\n") +
                        "\nFuente: ${v.source}",
                    packageName = sbn.packageName,
                    mitre = "T1566"
                )
            )
            Notify.post(
                this,
                "Enlace sospechoso detectado",
                "${appLabel(sbn.packageName)}: ${v.host} — ${v.reasons.firstOrNull() ?: ""}"
            )
        }
    }

    private fun extractText(sbn: StatusBarNotification): String? {
        return try {
            val ex = sbn.notification?.extras ?: return null
            val sb = StringBuilder()
            for (k in arrayOf(
                Notification.EXTRA_TITLE,
                Notification.EXTRA_TEXT,
                Notification.EXTRA_BIG_TEXT,
                Notification.EXTRA_SUB_TEXT,
                Notification.EXTRA_SUMMARY_TEXT
            )) {
                val v = ex.getCharSequence(k) ?: continue
                if (v.isNotEmpty()) sb.append(v).append(' ')
            }
            if (sb.isEmpty()) null else sb.toString()
        } catch (t: Throwable) {
            null
        }
    }

    private fun appLabel(p: String): String = try {
        val pm = packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(p, 0)).toString()
    } catch (t: Throwable) {
        p
    }

    companion object {
        /** Evita repetir el mismo dominio muchas veces seguidas. */
        private val recent = HashMap<String, Long>()

        private fun shouldReport(host: String): Boolean {
            val now = System.currentTimeMillis()
            synchronized(recent) {
                val last = recent[host]
                if (last != null && now - last < 6 * 3600_000L) return false
                recent[host] = now
                if (recent.size > 200) recent.clear()
                return true
            }
        }

        /** ¿Tiene la app el acceso a notificaciones concedido? */
        fun isConnected(ctx: Context): Boolean = try {
            NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)
        } catch (t: Throwable) {
            false
        }
    }
}
