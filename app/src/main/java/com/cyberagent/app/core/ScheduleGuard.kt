package com.cyberagent.app.core

import android.content.Context

/**
 * Bloqueo por horario (perfil noche / modo trabajo).
 *
 * Durante la ventana configurada (por ejemplo 23:30 → 07:00) solo las apps
 * marcadas como DE CONFIANZA mantienen acceso a la red: es la misma política
 * que el modo pánico, pero automática y por franja horaria. Fuera de la ventana
 * se vuelve exactamente al estado anterior.
 */
object ScheduleGuard {

    /** ¿Estamos dentro de la franja configurada? */
    fun isActiveNow(): Boolean {
        if (!Prefs.scheduleEnabled) return false
        val from = parse(Prefs.scheduleFrom)
        val to = parse(Prefs.scheduleTo)
        if (from < 0 || to < 0) return false
        val cal = java.util.Calendar.getInstance()
        val now = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
        return if (from <= to) {
            now >= from && now < to
        } else {
            // ventana que cruza la medianoche (23:30 → 07:00)
            now >= from || now < to
        }
    }

    /**
     * Aplica el cambio de estado si ha cambiado. Se llama al abrir la app, en
     * cada escaneo automático y desde el vigilante.
     */
    fun applyIfNeeded(ctx: Context) {
        val active = isActiveNow()
        if (active == Prefs.scheduleActive) return
        Prefs.scheduleActive = active

        if (!Prefs.firewallEnabled) {
            // Sin cortafuegos no hay nada que aplicar; se deja anotado una vez.
            Db.addAlert(
                SecurityAlert(
                    severity = "INFO", module = "Horario",
                    title = if (active) "Franja horaria activa (sin cortafuegos)"
                    else "Franja horaria terminada",
                    detail = "El bloqueo por horario necesita el cortafuegos VPN activo para aplicarse."
                )
            )
            return
        }

        FirewallBridge.requestRestart(ctx)
        val msg = if (active)
            "Franja activa: solo las apps de confianza mantienen red " +
                "(${Prefs.trustedPackages().size} marcadas)."
        else
            "Franja terminada: se restaura el acceso a la red."

        Db.addAlert(
            SecurityAlert(
                severity = if (active) "MEDIA" else "INFO",
                module = "Horario",
                title = if (active) "Bloqueo por horario activado" else "Bloqueo por horario desactivado",
                detail = msg,
                mitre = "T1562"
            )
        )
        Notify.post(ctx, "CyberAgent · horario", msg)
    }

    /** Texto de ayuda para la interfaz. */
    fun describe(): String {
        if (!Prefs.scheduleEnabled) return "Desactivado"
        val window = "${Prefs.scheduleFrom} → ${Prefs.scheduleTo}"
        return if (Prefs.scheduleActive) "Activo ahora ($window)" else "Programado ($window)"
    }

    private fun parse(hhmm: String): Int {
        return try {
            val parts = hhmm.trim().split(':')
            if (parts.size != 2) return -1
            val h = parts[0].toInt()
            val m = parts[1].toInt()
            if (h !in 0..23 || m !in 0..59) -1 else h * 60 + m
        } catch (t: Throwable) {
            -1
        }
    }
}
