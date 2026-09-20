package com.cyberagent.app.core

/**
 * Actividad de red reciente por aplicación.
 *
 * Permite responder a "¿qué se está conectando ahora?" usando las estadísticas
 * del sistema: se listan las apps (no de sistema) con tráfico en la ventana de
 * los últimos minutos. No hay inspección de paquetes — eso exigiría una VPN que
 * reenvíe el tráfico — así que se informa de lo que sí es medible: quién se
 * conectó, cuánto y hace cuánto.
 */
object TrafficWatch {

    /** Ventana corta (por defecto 15 min) usada por el widget. */
    fun recent(ctx: android.content.Context, minutes: Long = 15, limit: Int = 5): List<UsageRow> {
        if (!NetworkStatsReader.hasUsageAccess(ctx)) return emptyList()
        val now = System.currentTimeMillis()
        val from = now - minutes * 60_000L
        return try {
            NetworkStatsReader.perAppUsage(ctx, from, now)
                .filter { it.uid >= 10000 && it.total > 0 } // fuera las apps de sistema
                .take(limit)
        } catch (t: Throwable) {
            emptyList()
        }
    }

    /** Total de la ventana (bytes) para el resumen del panel. */
    fun recentTotal(ctx: android.content.Context, minutes: Long = 15): Long =
        recent(ctx, minutes, 50).sumOf { it.total }
}
