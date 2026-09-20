package com.cyberagent.app.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import com.cyberagent.app.core.Db
import com.cyberagent.app.core.Notify
import com.cyberagent.app.core.Prefs
import com.cyberagent.app.core.SecurityAlert

/**
 * Guardián de superposiciones (anti-clickjacking), OPCIONAL.
 *
 * Los troyanos bancarios suelen dibujar una pantalla falsa ENCIMA de la app
 * real para capturar tus claves. Este servicio detecta esa situación: dos
 * ventanas de aplicación a la vez, con la de arriba de un paquete distinto al
 * de la app en primer plano.
 *
 * Es una ALERTA, no un bloqueo, y puede avisar también de casos legítimos
 * (burbujas de chat, ventanas flotantes de reproducción). Está desactivado por
 * defecto porque necesita un permiso de accesibilidad con lectura de ventanas.
 */
class OverlayGuardService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        try {
            serviceInfo = serviceInfo.apply {
                eventTypes = AccessibilityEvent.TYPE_WINDOWS_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
                flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
                notificationTimeout = 300
            }
        } catch (t: Throwable) {
            // se queda con la configuración del XML
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (!Prefs.overlayGuardEnabled) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        ) return

        try {
            val appWindows = windows
                ?.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
                ?: return
            if (appWindows.size < 2) return

            // La lista viene ordenada por capa: la primera es la de ARRIBA.
            val top = appWindows.firstOrNull() ?: return
            val focused = appWindows.firstOrNull { it.root?.packageName != null && it.isFocused }
                ?: appWindows.firstOrNull { it.isActive }
            val topPkg = top.root?.packageName?.toString() ?: return
            val focusPkg = focused?.root?.packageName?.toString() ?: return

            if (topPkg == focusPkg) return
            if (topPkg == packageName || focusPkg == packageName) return
            if (topPkg.startsWith("com.android.")) return

            if (!shouldReport(topPkg, focusPkg)) return

            Db.addAlert(
                SecurityAlert(
                    severity = "ALTA",
                    module = "Anti-superposición",
                    title = "Posible pantalla falsa sobre ${label(focusPkg)}",
                    detail = "La app «${label(topPkg)}» está dibujando una ventana por encima de " +
                        "«${label(focusPkg)}» ($topPkg sobre $focusPkg).\n" +
                        "Si no esperabas una ventana flotante o una burbuja de chat, NO escribas " +
                        "datos ahí: cierra esa pantalla y comprueba qué app la está mostrando.",
                    packageName = topPkg,
                    mitre = "T1417"
                )
            )
            Notify.post(
                this,
                "Posible pantalla falsa",
                label(topPkg) + " se superpone a " + label(focusPkg)
            )
        } catch (t: Throwable) {
            // nunca romper la accesibilidad del sistema
        }
    }

    override fun onInterrupt() {
        // no hay nada que interrumpir
    }

    private fun label(pkg: String): String = try {
        val pm = packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (t: Throwable) {
        pkg
    }

    companion object {
        private val recent = HashMap<String, Long>()

        /** Un aviso por pareja de apps cada 30 minutos. */
        private fun shouldReport(top: String, focus: String): Boolean {
            val key = "$top>$focus"
            val now = System.currentTimeMillis()
            synchronized(recent) {
                val last = recent[key]
                if (last != null && now - last < 30 * 60_000L) return false
                recent[key] = now
                if (recent.size > 100) recent.clear()
                return true
            }
        }

        /** ¿Está activado este servicio de accesibilidad? */
        fun isEnabled(ctx: Context): Boolean {
            val svc = ctx.packageName + "/com.cyberagent.app.service.OverlayGuardService"
            val enabled = Settings.Secure.getString(
                ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: ""
            return enabled.split(':').any { it.equals(svc, true) }
        }
    }
}
