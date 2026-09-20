package com.cyberagent.app.core

import android.content.Context
import android.net.Uri
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.Executors

/**
 * Verificador forense: hash SHA-256 de ficheros + comprobación contra la base
 * local de IoC y, si el usuario lo autoriza, contra servicios externos.
 */
object ForensicVerifier {

    private val io = Executors.newSingleThreadExecutor()

    fun sha256(ctx: Context, uri: Uri): Pair<String, Long>? {
        return try {
            val stream: InputStream = ctx.contentResolver.openInputStream(uri) ?: return null
            val md = MessageDigest.getInstance("SHA-256")
            val buf = ByteArray(64 * 1024)
            var total = 0L
            stream.use { s ->
                while (true) {
                    val n = s.read(buf)
                    if (n <= 0) break
                    md.update(buf, 0, n)
                    total += n
                }
            }
            md.digest().joinToString("") { "%02x".format(it) } to total
        } catch (t: Throwable) {
            null
        }
    }

    /**
     * Verificación completa. `onlineConsent` debe ser true SOLO si el usuario
     * lo ha autorizado explícitamente en esta operación.
     */
    fun verify(
        ctx: Context,
        displayName: String,
        uri: Uri,
        onlineConsent: Boolean,
        onDone: (ForensicResult?) -> Unit
    ) {
        io.execute {
            val hash = sha256(ctx, uri)
            if (hash == null) {
                onDone(null)
                return@execute
            }
            val (digest, size) = hash
            var verdict = "SIN COINCIDENCIAS"
            var source = "Base local (${IocDatabase.version})"
            var detail = "El hash no figura en la base local de indicadores."

            val local = IocDatabase.lookupHash(ctx, digest)
            if (local != null) {
                verdict = "MALICIOSO: ${local.optString("name")}"
                detail = buildString {
                    append("Familia: ").append(local.optString("family", "desconocida")).append('\n')
                    append("Severidad: ").append(local.optString("severity", "MEDIA")).append('\n')
                    append("MITRE ATT&CK: ").append(local.optString("mitre", "-")).append('\n')
                    append(local.optString("note", ""))
                }
            } else if (onlineConsent) {
                val online = OnlineIoc.lookup(ctx, digest)
                if (online != null) {
                    verdict = online.verdict
                    source = online.source
                    detail = online.detail
                }
            } else if (Prefs.iocApiKey.isNotEmpty()) {
                detail = "Sin coincidencias locales. La consulta online está desactivada " +
                    "(requiere autorización explícita)."
            }

            val result = ForensicResult(
                fileName = displayName,
                sha256 = digest,
                sizeBytes = size,
                verdict = verdict,
                source = source,
                detail = detail
            )
            Db.addAlert(
                SecurityAlert(
                    severity = if (verdict.startsWith("MALICIOSO")) "ALTA" else "INFO",
                    module = "Forense",
                    title = "Verificación: $displayName",
                    detail = "$verdict · $digest",
                    mitre = local?.optString("mitre")
                )
            )
            onDone(result)
        }
    }
}
