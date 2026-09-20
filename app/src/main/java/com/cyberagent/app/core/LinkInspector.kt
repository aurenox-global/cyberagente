package com.cyberagent.app.core

/**
 * Analizador de enlaces y dominios (anti-phishing).
 *
 * Se usa desde tres sitios: los SMS entrantes, el guardián de notificaciones y
 * la pantalla "Comprobar enlace". Todo el análisis heurístico es OFFLINE:
 * lista local de dominios maliciosos/rastreadores, acortadores, IP directa,
 * punycode y suplantación de marcas conocidas. La consulta RDAP (fecha de
 * creación del dominio) es OPCIONAL y solo con consentimiento explícito.
 */
object LinkInspector {

    data class Verdict(
        val url: String,
        val host: String,
        val risky: Boolean,
        val severity: String,
        val reasons: List<String>,
        val source: String
    )

    /** Acortadores: el destino real queda oculto. */
    private val SHORTENERS = setOf(
        "bit.ly", "tinyurl.com", "t.co", "cutt.ly", "goo.gl", "ow.ly", "is.gd",
        "buff.ly", "shorte.st", "adf.ly", "rebrand.ly", "shorturl.at", "rb.gy",
        "tiny.cc", "urlz.fr", "linktr.ee", "s.id", "cutt.us"
    )

    /** Marcas que suelen suplantarse; si el dominio las contiene pero NO es el oficial. */
    private val BRANDS = mapOf(
        "bbva" to setOf("bbva.es", "bbva.com"),
        "santander" to setOf("santander.es", "santander.com"),
        "caixabank" to setOf("caixabank.es", "caixabank.com"),
        "sabadell" to setOf("bancsabadell.com"),
        "ing" to setOf("ing.es", "ing.nl"),
        "openbank" to setOf("openbank.es"),
        "unicaja" to setOf("unicaja.es"),
        "correos" to setOf("correos.es"),
        "seur" to setOf("seur.com"),
        "dhl" to setOf("dhl.com", "dhl.es"),
        "amazon" to setOf("amazon.es", "amazon.com"),
        "netflix" to setOf("netflix.com"),
        "paypal" to setOf("paypal.com"),
        "hacienda" to setOf("sede.agenciatributaria.gob.es", "agenciatributaria.es"),
        "agenciatributaria" to setOf("sede.agenciatributaria.gob.es"),
        "seguridadsocial" to setOf("seg-social.es"),
        "renfe" to setOf("renfe.com"),
        "movistar" to setOf("movistar.es"),
        "vodafone" to setOf("vodafone.es"),
        "orange" to setOf("orange.es")
    )

    private val URL_RE = Regex("""(?i)\b((?:https?://|www\.)[^\s<>"'()]+|\b[a-z0-9][a-z0-9.-]{1,60}\.(?:com|es|net|org|info|xyz|top|shop|site|online|club|live|icu|ru|cn|tk|gq|cf|ml|biz|link|click|app)(?:/[^\s<>"'()]*)?)""")

    /** Extrae enlaces de un texto (SMS, notificación, apunte). */
    fun findUrls(text: String): List<String> =
        URL_RE.findAll(text).map { it.value.trimEnd('.', ',', ';', ')') }.distinct().toList()

    fun hostOf(url: String): String {
        val u = url.trim().removePrefix("http://").removePrefix("https://")
        return u.substringBefore('/').substringBefore('?').substringBefore(':').lowercase()
    }

    /** Análisis offline. Devuelve veredicto con motivos legibles. */
    fun inspect(ctx: android.content.Context, url: String): Verdict {
        val host = hostOf(url)
        val reasons = ArrayList<String>()
        var severity = "INFO"

        if (host.isEmpty()) {
            return Verdict(url, host, false, "INFO", listOf("Enlace vacío o ilegible."), "—")
        }

        // 1. Lista local de dominios maliciosos (IoC).
        val ioc = IocDatabase.lookupDomain(ctx, host)
        if (ioc != null) {
            reasons.add("Dominio en la lista local de indicadores: " + ioc.optString("note", ioc.optString("family", "malicioso")))
            severity = "ALTA"
        }

        // 2. Coincidencia por dominio padre (sub.dominio.malicioso.com).
        if (ioc == null) {
            val parts = host.split('.')
            for (i in 1 until parts.size - 1) {
                val parent = parts.subList(i, parts.size).joinToString(".")
                if (IocDatabase.lookupDomain(ctx, parent) != null) {
                    reasons.add("Subdominio de un dominio bloqueado ($parent).")
                    severity = "ALTA"
                    break
                }
            }
        }

        // 3. Acortador.
        if (SHORTENERS.contains(host) || SHORTENERS.any { host.endsWith(".$it") }) {
            reasons.add("Acortador de enlaces: el destino real está oculto.")
            if (severity == "INFO") severity = "MEDIA"
        }

        // 4. IP directa.
        if (Regex("""^\d{1,3}(\.\d{1,3}){3}$""").matches(host)) {
            reasons.add("Enlace a una dirección IP en lugar de un dominio.")
            if (severity == "INFO") severity = "MEDIA"
        }

        // 5. Suplantación de marca.
        for ((brand, official) in BRANDS) {
            if (host.contains(brand) && !official.contains(host)) {
                reasons.add("Posible suplantación de «$brand» (el dominio no es el oficial).")
                severity = "ALTA"
                break
            }
        }

        // 6. Punycode / caracteres no ASCII (homógrafos).
        if (host.contains("xn--") || host.any { it.code > 127 }) {
            reasons.add("Dominio con caracteres no latinos (técnica de homógrafo).")
            if (severity != "ALTA") severity = "MEDIA"
        }

        // 7. Dominio recién creado, consulta RDAP OPTATIVA.
        if (ctx != null && Prefs.rdapEnabled) {
            val ageDays = rdapAgeDays(host)
            if (ageDays in 0..30) {
                reasons.add("Dominio creado hace $ageDays días (los de phishing son nuevos).")
                if (severity == "INFO") severity = "MEDIA"
            }
        }

        // 8. TLD de riesgo alto.
        val tld = host.substringAfterLast('.')
        if (tld in setOf("tk", "gq", "cf", "ml", "xyz", "top", "click", "icu", "shop")) {
            reasons.add("Extensión de dominio con alta proporción de abuso (.$tld).")
            if (severity == "INFO") severity = "MEDIA"
        }

        if (reasons.isEmpty()) reasons.add("Sin indicadores de riesgo en la lista local ni en las heurísticas.")

        return Verdict(
            url, host, severity != "INFO", severity, reasons,
            "Local (IoC ${IocDatabase.version})" + if (Prefs.rdapEnabled) " + RDAP" else ""
        )
    }

    /**
     * Edad del dominio en días vía RDAP (HTTPS, sin clave). Solo se llama si el
     * usuario activa las consultas externas. Devuelve -1 si no se puede saber.
     */
    private fun rdapAgeDays(host: String): Long {
        return try {
            val root = host.split('.').takeLast(2).joinToString(".")
            val conn = (java.net.URL("https://rdap.org/domain/$root").openConnection()
                as java.net.HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 6000
                readTimeout = 6000
                setRequestProperty("Accept", "application/rdap+json")
            }
            if (conn.responseCode != 200) {
                conn.disconnect()
                return -1
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            val obj = org.json.JSONObject(body)
            val events = obj.optJSONArray("events") ?: return -1
            var registered = 0L
            for (i in 0 until events.length()) {
                val e = events.getJSONObject(i)
                if (e.optString("eventAction") == "registration") {
                    val d = e.optString("eventDate")
                    if (d.isNotEmpty()) {
                        registered = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
                            .parse(d.take(19))?.time ?: 0L
                    }
                }
            }
            if (registered <= 0) -1 else (System.currentTimeMillis() - registered) / 86_400_000L
        } catch (t: Throwable) {
            -1
        }
    }
}
