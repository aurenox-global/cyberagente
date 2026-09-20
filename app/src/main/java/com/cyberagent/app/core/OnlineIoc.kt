package com.cyberagent.app.core

import android.content.Context
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/** Datos de una consulta externa de IoC. */
data class OnlineVerdict(val verdict: String, val source: String, val detail: String)

/**
 * Consultas de reputación a VirusTotal / AbuseIPDB.
 * Solo se ejecutan si el usuario ha introducido su clave y ha autorizado la
 * operación (el módulo de IA y el verificador piden confirmación explícita).
 * El tráfico va siempre por HTTPS (network_security_config lo impone).
 */
object OnlineIoc {

    private val io = Executors.newSingleThreadExecutor()

    fun lookup(ctx: Context, sha256: String, callback: (OnlineVerdict?) -> Unit) {
        io.execute { callback(lookup(ctx, sha256)) }
    }

    fun lookup(ctx: Context, sha256: String): OnlineVerdict? {
        val key = Prefs.iocApiKey
        if (key.isEmpty()) return null
        return try {
            val url = URL("https://www.virustotal.com/api/v3/files/$sha256")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("x-apikey", key)
                connectTimeout = 12_000
                readTimeout = 12_000
            }
            val code = conn.responseCode
            if (code != 200) {
                conn.disconnect()
                return OnlineVerdict(
                    "SIN DATOS",
                    "VirusTotal",
                    "El servicio respondió HTTP $code (clave inválida, cuota agotada o sin conexión)."
                )
            }
            val body: String = conn.inputStream.bufferedReader().use(BufferedReader::readText)
            conn.disconnect()

            val root = JSONObject(body)
            val stats = root.optJSONObject("data")
                ?.optJSONObject("attributes")
                ?.optJSONObject("last_analysis_stats")
            if (stats == null) {
                OnlineVerdict("SIN DATOS", "VirusTotal", "Respuesta sin estadísticas de análisis.")
            } else {
                val mal = stats.optInt("malicious", 0)
                val sus = stats.optInt("suspicious", 0)
                val total = mal + sus + stats.optInt("harmless", 0) +
                    stats.optInt("undetected", 0) + stats.optInt("timeout", 0)
                val verdict = when {
                    mal > 0 -> "MALICIOSO"
                    sus > 0 -> "SOSPECHOSO"
                    else -> "LIMPIO"
                }
                OnlineVerdict(
                    verdict,
                    "VirusTotal",
                    "$verdict — $mal detecciones maliciosas y $sus sospechosas de $total motores."
                )
            }
        } catch (t: Throwable) {
            OnlineVerdict("ERROR", "VirusTotal", "No se pudo completar la consulta: ${t.javaClass.simpleName}")
        }
    }
}
