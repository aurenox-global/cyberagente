package com.cyberagent.app.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.cyberagent.app.core.Db
import com.cyberagent.app.core.Prefs
import com.cyberagent.app.core.SecurityAlert

/**
 * Servicio de accesibilidad MINIMIZADO.
 *
 * Diferencias frente a la v2.0 (que pedía capacidades de nivel keylogger):
 *  · canRetrieveWindowContent = false  → NO lee el contenido de otras apps.
 *  · Sin FLAG_REQUEST_FILTER_KEY_EVENTS → NO captura eventos de tecla.
 *  · Solo recibe TYPE_WINDOW_STATE_CHANGED para avisar de instalaciones.
 */
class SecurityAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val e = event ?: return
        val pkg = e.packageName?.toString() ?: return
        if (e.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        // Guardia de instalación: avisa cuando se abre el instalador de paquetes.
        if (pkg == "com.android.packageinstaller" ||
            pkg == "com.google.android.packageinstaller" ||
            pkg.contains("packageinstaller")
        ) {
            Db.addAlert(
                SecurityAlert(
                    severity = "MEDIA",
                    module = "Guardia de instalación",
                    title = "Instalación de paquetes en curso",
                    detail = "Se ha abierto el instalador del sistema. Verifica el origen del APK " +
                        "y los permisos que solicita antes de continuar.",
                    mitre = "T1204"
                )
            )
        }
    }

    override fun onInterrupt() { /* no-op */ }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Db.addAlert(
            SecurityAlert(
                severity = "INFO",
                module = "Guardia de instalación",
                title = "Guardia de instalación activo",
                detail = "Capacidades limitadas a cambios de ventana. Sin lectura de contenido " +
                    "y sin captura de pulsaciones."
            )
        )
    }
}
