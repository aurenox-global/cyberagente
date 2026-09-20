package com.cyberagent.app.core

import android.content.Context

/**
 * Escaneo en lote de las aplicaciones instaladas por HUELLA (hash del APK).
 *
 * Orden del proceso (del más barato al más caro):
 *  1. Hash local del APK (no necesita red).
 *  2. Lista local de indicadores (gratis, ilimitada).
 *  3. VirusTotal, SOLO si el usuario ha puesto su clave y lo ha autorizado, y
 *     respetando su límite (plan gratuito ≈ 4 consultas/minuto): se deja una
 *     pausa configurable entre consultas y se corta si el servicio responde 429.
 *
 * Todo el bucle se ejecuta en segundo plano desde la interfaz.
 */
object BatchScan {

    data class Progress(
        val done: Int,
        val total: Int,
        val current: String,
        val localHits: Int,
        val onlineHits: Int,
        val skipped: Int,
        val message: String = "",
        val finished: Boolean = false
    )

    @Volatile
    var cancelled: Boolean = false

    fun run(
        ctx: Context,
        maxOnline: Int = 10,
        gapMs: Long = 15_000L,
        onProgress: (Progress) -> Unit
    ): Progress {
        cancelled = false
        val apps = AppAnalyzer.analyzeAll(ctx, includeSystem = false)
        val total = apps.size
        var done = 0
        var localHits = 0
        var onlineHits = 0
        var skipped = 0
        var onlineUsed = 0
        var last = Progress(0, total, "", 0, 0, 0, "Iniciando…")

        val hasKey = Prefs.iocApiKey.isNotEmpty()
        val findings = ArrayList<String>()

        for (a in apps) {
            if (cancelled) {
                last = last.copy(message = "Cancelado por el usuario.", finished = true)
                onProgress(last)
                return last
            }
            done++
            last = Progress(done, total, a.label, localHits, onlineHits, skipped, "Analizando ${a.label}…")
            onProgress(last)

            val src = try {
                ctx.packageManager.getApplicationInfo(a.packageName, 0).sourceDir
            } catch (t: Throwable) {
                ""
            }
            if (src.isEmpty()) {
                skipped++
                continue
            }
            val hash = ApkReputation.fileSha256(src)
            if (hash.isEmpty()) {
                skipped++
                continue
            }

            // 1. Lista local: gratis y sin límite.
            val known = IocDatabase.lookupHash(ctx, hash)
            if (known != null) {
                localHits++
                val name = known.optString("name", "?)")
                findings.add("${a.label}: MALICIOSO en lista local ($name)")
                Db.addAlert(
                    SecurityAlert(
                        severity = "ALTA", module = "Escaneo en lote",
                        title = "App maliciosa detectada: ${a.label}",
                        detail = "Coincide con la lista local: $name " +
                            "(familia ${known.optString("family", "?")}). " +
                            "APK: ${hash.take(16)}…",
                        packageName = a.packageName, mitre = "T1204"
                    )
                )
                continue
            }

            // 2. VirusTotal: solo con clave, con límite y con pausa.
            if (!hasKey || onlineUsed >= maxOnline) {
                if (!hasKey) skipped++
                continue
            }
            onlineUsed++
            val v = OnlineIoc.lookup(ctx, hash)
            if (v == null) continue
            when (v.verdict) {
                "MALICIOSO", "SOSPECHOSO" -> {
                    onlineHits++
                    findings.add("${a.label}: ${v.verdict} — ${v.detail}")
                    Db.addAlert(
                        SecurityAlert(
                            severity = if (v.verdict == "MALICIOSO") "ALTA" else "MEDIA",
                            module = "Escaneo en lote",
                            title = "${v.verdict}: ${a.label}",
                            detail = v.detail + "\nAPK: ${hash.take(16)}…",
                            packageName = a.packageName, mitre = "T1204"
                        )
                    )
                }
            }
            // Pausa para respetar la cuota del plan gratuito.
            try {
                Thread.sleep(gapMs)
            } catch (t: InterruptedException) {
                // si nos interrumpen, seguimos
            }
        }

        val msg = buildString {
            append("Analizadas $total apps. ")
            append("Coincidencias locales: $localHits. Consultas online: $onlineUsed (positivas: $onlineHits). ")
            if (skipped > 0) append("Sin datos: $skipped. ")
            if (!hasKey) append("Sin clave de VirusTotal: solo se usó la lista local. ")
            if (findings.isEmpty()) append("Sin hallazgos.") else append("Hallazgos:\n" + findings.joinToString("\n"))
        }
        Db.addAlert(
            SecurityAlert(
                severity = if (localHits + onlineHits > 0) "ALTA" else "INFO",
                module = "Escaneo en lote",
                title = "Escaneo por huella completado",
                detail = msg
            )
        )
        Notify.post(ctx, "Escaneo por huella", msg)
        val result = last.copy(message = msg, finished = true)
        onProgress(result)
        return result
    }
}
