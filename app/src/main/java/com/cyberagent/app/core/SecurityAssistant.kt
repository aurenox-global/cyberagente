package com.cyberagent.app.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Asistente de seguridad con ciclo Observar → Analizar → Recomendar.
 *
 * Funciona SIEMPRE, sin descargas ni ficheros externos:
 *  · Modo LOCAL (por defecto): motor heurístico determinista que analiza
 *    alertas, permisos y red, y produce hallazgos con severidad CVSS y
 *    referencia MITRE ATT&CK.
 *  · Modo REMOTO (opcional): si el usuario configura endpoint + clave, se
 *    consulta un modelo compatible OpenAI. Requiere consentimiento explícito.
 *
 * Guardrails: rechaza peticiones ofensivas y toda operación de red exige
 * confirmación humana previa.
 */
object SecurityAssistant {

    private val io = Executors.newSingleThreadExecutor()

    /** Palabras que activan el guardrail: el asistente no ayuda con esto. */
    private val OFFENSIVE = listOf(
        "exploit", "payload", "reverse shell", "keylogger", "ransomware", "botnet",
        "ddos", "sql injection", "inyección sql", "bypass", "crackear", "hackear",
        "robar contraseña", "stealer", "rootkit", "phishing kit", "c2 server",
        "meterpreter", "metasploit", "escalada de privilegios", "privilege escalation"
    )

    fun isOffensive(query: String): Boolean {
        val q = query.lowercase()
        return OFFENSIVE.any { q.contains(it) }
    }

    fun refusal(): String =
        "⛔ Fuera de alcance: este asistente es exclusivamente defensivo.\n" +
            "No genero exploits, payloads ni asistencia ofensiva.\n" +
            "Si estás sufriendo un incidente, puedo ayudarte a analizar los indicadores, " +
            "aislar la app afectada y reforzar la configuración del dispositivo."

    /** Ejecuta el ciclo completo. `allowNetwork` = consentimiento explícito. */
    fun analyze(
        ctx: Context,
        query: String,
        allowNetwork: Boolean,
        onDone: (String) -> Unit
    ) {
        if (isOffensive(query)) {
            onDone(refusal())
            return
        }
        io.execute {
            val local = localReport(ctx, query)
            val mode = Prefs.aiMode
            if (mode != "remoto" || Prefs.aiApiKey.isEmpty() || Prefs.aiEndpoint.isEmpty()) {
                onDone(local)
                return@execute
            }
            if (!allowNetwork) {
                onDone(local + "\n\n— — —\n🔒 El modo remoto está activo, pero no has autorizado " +
                    "la consulta de red en esta petición. Se muestra solo el análisis local.")
                return@execute
            }
            val remote = remoteReport(ctx, query)
            onDone(local + "\n\n— — —\n🤖 Análisis ampliado (remoto):\n" + (remote ?: "sin respuesta"))
        }
    }

    // ── Motor local ──────────────────────────────────────────────────
    private fun localReport(ctx: Context, query: String): String {
        val sb = StringBuilder()
        sb.append("[OBSERVAR]\n")

        val dayAlerts = Db.alerts(limit = 300)
        val high = dayAlerts.count { it.severity == "ALTA" || it.severity == "CRITICA" }
        val apps = AppAnalyzer.analyzeAll(ctx)
        val risky = apps.filter { it.level == RiskLevel.ALTO || it.level == RiskLevel.CRITICO }
        val wifi = WifiAnalyzer.analyze(ctx)
        val quarantine = Prefs.quarantine()

        sb.append("· Alertas registradas: ").append(dayAlerts.size)
            .append(" (").append(high).append(" de severidad alta)\n")
        sb.append("· Apps auditadas: ").append(apps.size)
            .append(" · de riesgo alto: ").append(risky.size).append('\n')
        sb.append("· Red activa: ").append(wifi.ssid).append(" · ").append(wifi.security).append('\n')
        sb.append("· Apps en cuarentena: ").append(quarantine.size).append('\n')

        sb.append("\n[ANALIZAR]\n")
        var findings = 0

        if (high > 0) {
            findings++
            sb.append("1) ").append(high).append(" alertas de severidad alta sin resolver.\n")
            sb.append("   Severidad CVSS ≈ 7.5 · MITRE ATT&CK T1071 (canal de control)\n")
            sb.append("   Acción: revisa las alertas y aisla la app implicada.\n")
        }

        risky.take(3).forEachIndexed { i, app ->
            findings++
            sb.append(findingIndex(findings)).append(' ')
                .append(app.label).append(" (").append(app.packageName).append("): score ")
                .append(app.score).append("/100 · ").append(app.level).append('\n')
            app.reasons.take(3).forEach { sb.append("   · ").append(it).append('\n') }
            sb.append("   Acción: revisa permisos o considera la cuarentena.\n")
        }

        if (wifi.score >= 35) {
            findings++
            sb.append(findingIndex(findings)).append(" Red Wi-Fi con debilidades (score ")
                .append(wifi.score).append("/100).\n")
            wifi.findings.forEach { sb.append("   · ").append(it).append('\n') }
            sb.append("   MITRE ATT&CK T1557 (adversario en el medio)\n")
        }

        if (quarantine.isNotEmpty()) {
            findings++
            sb.append(findingIndex(findings)).append(quarantine.size)
                .append(" app(s) en cuarentena con la red bloqueada.\n")
        }

        if (!NetworkStatsReader.hasUsageAccess(ctx)) {
            findings++
            sb.append(findingIndex(findings))
                .append(" Sin acceso a estadísticas de uso: el monitor de red no puede medir el consumo.\n")
            sb.append("   Acción: Ajustes → Acceso al uso → CyberAgent.\n")
        }

        if (findings == 0) {
            sb.append("Sin hallazgos relevantes. El dispositivo no presenta indicios anómalos " +
                "con los datos disponibles.\n")
        }

        // Búsqueda de la consulta del usuario en las alertas
        if (query.isNotBlank()) {
            val q = query.lowercase()
            val hits = dayAlerts.filter {
                it.title.lowercase().contains(q) || it.detail.lowercase().contains(q) ||
                    (it.packageName?.lowercase()?.contains(q) == true)
            }
            sb.append("\n[BÚSQUEDA] \"").append(query).append("\" → ")
            sb.append(hits.size).append(" coincidencia(s)\n")
            hits.take(5).forEach { sb.append("   · ").append(it.title).append('\n') }
        }

        sb.append("\n[RECOMENDAR]\n")
        sb.append("· Prioridad 1: ").append(
            when {
                high > 0 -> "resolver las alertas de severidad alta."
                risky.isNotEmpty() -> "revisar los permisos de las apps con mayor score."
                wifi.score >= 35 -> "evitar redes abiertas o con cifrado débil."
                else -> "mantener la monitorización activa."
            }
        ).append('\n')
        sb.append("· Ninguna operación de red se ejecuta sin tu confirmación explícita.\n")
        sb.append("· Motor local determinista · sin telemetría · sin modelos externos.\n")

        return sb.toString()
    }

    private fun findingIndex(n: Int): String = "$n)"

    // ── Motor remoto opcional ────────────────────────────────────────
    private fun remoteReport(ctx: Context, query: String): String? {
        return try {
            val url = URL(Prefs.aiEndpoint)
            if (!url.protocol.equals("https", true)) {
                return "Endpoint rechazado: solo se admite HTTPS."
            }
            val payload = JSONObject().apply {
                put("model", Prefs.aiModelName)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", "Eres un asistente de ciberseguridad DEFENSIVA. " +
                            "No generas código malicioso. Responde en español, breve y técnico.")
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", query.ifBlank { "Resume el estado de seguridad del dispositivo." })
                    })
                })
                put("temperature", 0.2)
            }.toString()

            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer " + Prefs.aiApiKey)
                connectTimeout = 20_000
                readTimeout = 30_000
            }
            conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use(BufferedReader::readText)
            conn.disconnect()
            if (code !in 200..299) return "El endpoint respondió HTTP $code."
            if (body == null) return null
            val root = JSONObject(body)
            root.optJSONArray("choices")?.optJSONObject(0)
                ?.optJSONObject("message")?.optString("content") ?: body.take(600)
        } catch (t: Throwable) {
            "Error de red: ${t.javaClass.simpleName}"
        }
    }
}
