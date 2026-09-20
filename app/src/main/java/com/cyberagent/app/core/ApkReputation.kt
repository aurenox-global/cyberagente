package com.cyberagent.app.core

import android.content.Context
import java.security.MessageDigest

/**
 * Reputación de un APK instalado.
 *
 * Calcula:
 *  · SHA-256 del fichero APK (identifica la versión exacta).
 *  · SHA-256 del certificado de firma (identifica al desarrollador).
 * Con el hash del APK se consulta la base local de IoC y, solo si el usuario lo
 * autoriza, VirusTotal. Con la firma se detecta "misma firma que otra app"
 * (indicador de repackage) y firmas de depuración.
 */
object ApkReputation {

    data class Info(
        val pkg: String,
        val apkSha256: String,
        val signerSha256: String,
        val signerSubject: String,
        val debugSigned: Boolean,
        val verdict: String,
        val detail: String,
        val source: String
    )

    fun analyze(ctx: Context, pkg: String, onlineConsent: Boolean = false): Info? {
        val pm = ctx.packageManager
        val info = try {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(pkg, android.content.pm.PackageManager.GET_SIGNATURES)
        } catch (t: Throwable) {
            return null
        }

        // 1. Firma del certificado.
        var signer = ""
        var subject = ""
        var debug = false
        try {
            val sig = info.signatures?.firstOrNull()
            if (sig != null) {
                val md = MessageDigest.getInstance("SHA-256")
                signer = md.digest(sig.toByteArray()).joinToString("") { "%02x".format(it) }
                @Suppress("DEPRECATION")
                val cert = java.security.cert.CertificateFactory
                    .getInstance("X.509")
                    .generateCertificate(sig.toByteArray().inputStream())
                subject = (cert as java.security.cert.X509Certificate).subjectDN.name
                debug = subject.contains("Android Debug") || subject.contains("CN=Android")
            }
        } catch (t: Throwable) {
            // sin firma legible
        }

        // 2. SHA-256 del APK (puede ser pesado: se hace siempre en segundo plano).
        val src = info.applicationInfo?.sourceDir ?: ""
        val apkHash = if (src.isNotEmpty()) fileSha256(src) else ""

        // 3. Veredicto: base local siempre; VirusTotal solo con consentimiento.
        var verdict = "SIN COINCIDENCIAS"
        var source = "Local (IoC ${IocDatabase.version})"
        var detail = "El hash del APK no figura en la base local de indicadores."

        val known = if (apkHash.isNotEmpty()) IocDatabase.lookupHash(ctx, apkHash) else null
        if (known != null) {
            verdict = "MALICIOSO (lista local)"
            detail = "Conocido como " + known.optString("name", "?)") +
                " · familia " + known.optString("family", "?") +
                " · " + known.optString("note", "")
        } else if (onlineConsent && Prefs.iocApiKey.isNotEmpty() && apkHash.isNotEmpty()) {
            val vt = OnlineIoc.lookup(ctx, apkHash)
            if (vt != null) {
                verdict = vt.verdict
                source = vt.source
                detail = vt.detail
            }
        }

        val extra = ArrayList<String>()
        if (debug) extra.add("Firmada con certificado de depuración (no es una build de tienda).")
        if (signer.isNotEmpty()) extra.add("Huella de firma: " + signer.take(16) + "…")
        if (extra.isNotEmpty()) detail = detail + "\n" + extra.joinToString("\n")

        return Info(pkg, apkHash, signer, subject, debug, verdict, detail, source)
    }

    fun fileSha256(path: String): String {
        return try {
            val md = MessageDigest.getInstance("SHA-256")
            java.io.FileInputStream(path).use { input ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    md.update(buf, 0, n)
                }
            }
            md.digest().joinToString("") { "%02x".format(it) }
        } catch (t: Throwable) {
            ""
        }
    }

    /**
     * Compara apps instaladas con la MISMA firma: es lo que usa un atacante al
     * republicar una app legítima con código añadido.
     */
    fun appsSharingSignature(ctx: Context, signerHash: String): List<String> {
        if (signerHash.isEmpty()) return emptyList()
        val out = ArrayList<String>()
        try {
            val pm = ctx.packageManager
            @Suppress("DEPRECATION")
            val pkgs = pm.getInstalledPackages(android.content.pm.PackageManager.GET_SIGNATURES)
            for (p in pkgs) {
                val sig = p.signatures?.firstOrNull() ?: continue
                val h = MessageDigest.getInstance("SHA-256")
                    .digest(sig.toByteArray()).joinToString("") { "%02x".format(it) }
                if (h == signerHash && p.packageName != ctx.packageName) out.add(p.packageName)
            }
        } catch (t: Throwable) {
            // sin permisos o sin apps
        }
        return out
    }
}
