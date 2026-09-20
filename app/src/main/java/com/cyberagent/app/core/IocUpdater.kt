package com.cyberagent.app.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Actualizador de la base de dominios/hashes.
 *
 * Descarga una lista en el MISMO formato que `assets/ioc_db.json` desde una URL
 * que configura el usuario, por HTTPS obligatorio.
 *
 * Integridad: si el usuario fija una huella SHA-256 de la lista, se exige que
 * coincida; si no coincide se descarta (protege de un servidor alterado o de un
 * ataque de red). Sin huella fijada se acepta, pero se registra el aviso.
 * Nada se descarga sin pulsar el botón de actualizar.
 */
object IocUpdater {

    data class Result(val ok: Boolean, val message: String, val hashes: Int, val domains: Int)

    fun update(ctx: Context): Result {
        val url = Prefs.iocFeedUrl.trim()
        if (url.isEmpty()) return Result(false, "No has configurado una URL de lista.", 0, 0)
        if (!url.startsWith("https://")) return Result(false, "La lista debe servirse por HTTPS.", 0, 0)

        return try {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 15_000
                setRequestProperty("Accept", "application/json")
            }
            val code = conn.responseCode
            if (code != 200) {
                conn.disconnect()
                return Result(false, "El servidor respondió HTTP $code.", 0, 0)
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()

            // Verificación por huella SHA-256 (si el usuario la ha fijado).
            val pin = Prefs.iocFeedSha256.trim().lowercase()
            if (pin.isNotEmpty()) {
                val md = java.security.MessageDigest.getInstance("SHA-256")
                val digest = md.digest(body.toByteArray(Charsets.UTF_8))
                    .joinToString("") { "%02x".format(it) }
                if (digest != pin) {
                    Db.addAlert(
                        SecurityAlert(
                            severity = "ALTA", module = "Actualizador IoC",
                            title = "Lista descartada: la huella no coincide",
                            detail = "Esperada: $pin\nRecibida: $digest\n" +
                                "La lista NO se ha aplicado. Puede que el servidor haya cambiado o " +
                                "que alguien haya alterado la descarga.",
                            mitre = "T1195"
                        )
                    )
                    return Result(false, "La huella SHA-256 no coincide. Lista descartada.", 0, 0)
                }
            }

            val root = JSONObject(body)
            val h = root.optJSONArray("hashes") ?: JSONArray()
            val d = root.optJSONArray("domains") ?: JSONArray()
            if (h.length() == 0 && d.length() == 0) {
                return Result(false, "La lista no contiene entradas válidas.", 0, 0)
            }

            val out = JSONObject()
            out.put("_comment", "Capa local actualizable. Descargada por CyberAgent.")
            out.put("version", root.optString("version", "externa-" + System.currentTimeMillis()))
            out.put("updatedAt", System.currentTimeMillis())
            out.put("source", url)
            out.put("hashes", h)
            out.put("domains", d)
            java.io.File(ctx.filesDir, IocDatabase.LOCAL_FILE)
                .writeText(out.toString())

            IocDatabase.reload(ctx)
            Prefs.lastFeedUpdate = System.currentTimeMillis()
            Prefs.lastFeedCount = IocDatabase.localSize()

            Db.addAlert(
                SecurityAlert(
                    severity = "INFO", module = "Actualizador IoC",
                    title = "Base de dominios actualizada",
                    detail = "${h.length()} hashes y ${d.length()} dominios añadidos. " +
                        "Total en uso: ${IocDatabase.size()} entradas."
                )
            )
            Result(true, "Actualizada: ${h.length()} hashes · ${d.length()} dominios", h.length(), d.length())
        } catch (t: Throwable) {
            Result(false, "No se pudo descargar: " + t.javaClass.simpleName, 0, 0)
        }
    }

    /** Vuelve a la base de fábrica (borra la capa local). */
    fun reset(ctx: Context): Result {
        return try {
            java.io.File(ctx.filesDir, IocDatabase.LOCAL_FILE).delete()
            IocDatabase.reload(ctx)
            Prefs.lastFeedCount = 0
            Result(true, "Capa local borrada. Se usa la base de fábrica.", 0, 0)
        } catch (t: Throwable) {
            Result(false, "No se pudo borrar: " + t.javaClass.simpleName, 0, 0)
        }
    }
}
