package com.cyberagent.app.core

import android.content.Context
import android.util.Log

/**
 * Registro de fallos internos. Si algo revienta en runtime, se guarda aquí y
 * la aplicación lo muestra en pantalla en el siguiente arranque, en lugar de
 * cerrarse en silencio.
 */
object CrashLog {

    @Volatile
    var last: String? = null
        private set

    fun record(ctx: Context?, where: String, t: Throwable) {
        val trace = Log.getStackTraceString(t)
        val text = "[$where]\n$trace"
        last = text
        try {
            if (ctx != null) {
                java.io.File(ctx.filesDir, FILE).writeText(text)
            }
        } catch (ignored: Throwable) {
            // nunca fallar al registrar un fallo
        }
        Log.e("CyberAgent", text, t)
    }

    fun read(ctx: Context): String {
        last?.let { return it }
        return try {
            val f = java.io.File(ctx.filesDir, FILE)
            if (f.exists()) f.readText() else ""
        } catch (t: Throwable) {
            ""
        }
    }

    fun clear(ctx: Context) {
        last = null
        try {
            java.io.File(ctx.filesDir, FILE).delete()
        } catch (t: Throwable) {
            // ignorar
        }
    }

    private const val FILE = "last-crash.txt"
}
