package com.cyberagent.app.core

import android.content.Context
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Ejecutor de trabajo en segundo plano.
 *
 * Motivo: leer estadísticas de red (NetworkStatsManager), analizar todas las
 * apps instaladas o descifrar el historial son operaciones LENTAS. Si se hacen
 * en el hilo principal la app se queda congelada ("se frisa") y Android puede
 * matarla por ANR. Todas esas tareas van aquí y el resultado se publica en el
 * hilo de interfaz.
 */
object Bg {

    private val pool: ExecutorService = Executors.newFixedThreadPool(3) { r ->
        Thread(r, "cyberagent-bg").apply { isDaemon = true }
    }

    fun run(ctx: Context, tag: String, work: () -> Unit) {
        pool.execute {
            try {
                work()
            } catch (t: Throwable) {
                CrashLog.record(ctx, "Bg/$tag", t)
            }
        }
    }

    fun io(work: () -> Unit) {
        pool.execute {
            try {
                work()
            } catch (t: Throwable) {
                // el llamador decide cómo avisar
            }
        }
    }
}
