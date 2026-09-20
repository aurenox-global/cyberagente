package com.cyberagent.app.core

import android.content.Context
import android.content.SharedPreferences

/**
 * Ajustes y listas del usuario.
 *
 * Los valores se cifran SIEMPRE con AES-256-GCM antes de escribirse, usando una
 * clave no exportable custodiada en el Android Keystore (ver [Crypto]).
 * El fichero de preferencias no contiene datos legibles.
 */
object Prefs {

    private const val FILE = "cyberagent_secure_prefs"

    private lateinit var sp: SharedPreferences

    fun init(ctx: Context) {
        if (::sp.isInitialized) return
        sp = ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    }

    // ── Primitivas (todo cifrado en reposo) ──────────────────────────
    private fun putEnc(k: String, v: String) {
        sp.edit().putString(k, Crypto.encrypt(v)).apply()
    }

    private fun getEnc(k: String, d: String = ""): String {
        val stored = sp.getString(k, null) ?: return d
        val plain = Crypto.decrypt(stored)
        return if (plain.isEmpty()) d else plain
    }

    private fun getBool(k: String, d: Boolean) = getEnc(k, if (d) "1" else "0") == "1"
    private fun setBool(k: String, v: Boolean) = putEnc(k, if (v) "1" else "0")

    private fun getLong(k: String, d: Long = 0L) = getEnc(k, d.toString()).toLongOrNull() ?: d
    private fun setLong(k: String, v: Long) = putEnc(k, v.toString())

    private fun getStrings(k: String): Set<String> {
        val raw = getEnc(k, "")
        if (raw.isEmpty()) return emptySet()
        return raw.split('\u001f').filter { it.isNotEmpty() }.toSet()
    }

    private fun setStrings(k: String, v: Set<String>) {
        putEnc(k, v.joinToString("\u001f"))
    }

    // ── Listas de bloqueo ────────────────────────────────────────────
    fun blockedCalls(): Set<String> = getStrings(K_BLOCK_CALLS)
    fun blockCall(number: String) = setStrings(K_BLOCK_CALLS, blockedCalls() + normalize(number))
    fun unblockCall(number: String) = setStrings(K_BLOCK_CALLS, blockedCalls() - normalize(number))

    fun allowedCalls(): Set<String> = getStrings(K_ALLOW_CALLS)
    fun allowCall(number: String) = setStrings(K_ALLOW_CALLS, allowedCalls() + normalize(number))

    fun blockedPackages(): Set<String> = getStrings(K_BLOCK_PKGS)
    fun blockPackage(pkg: String) = setStrings(K_BLOCK_PKGS, blockedPackages() + pkg)
    fun unblockPackage(pkg: String) = setStrings(K_BLOCK_PKGS, blockedPackages() - pkg)

    /**
     * Apps de confianza: son las ÚNICAS que mantienen red en modo pánico.
     * (Antes el modo pánico usaba la lista blanca de números de teléfono, así
     * que dejaba sin internet a todas las apps: corregido.)
     */
    fun trustedPackages(): Set<String> = getStrings(K_TRUSTED)
    fun trustPackage(pkg: String) = setStrings(K_TRUSTED, trustedPackages() + pkg)
    fun untrustPackage(pkg: String) = setStrings(K_TRUSTED, trustedPackages() - pkg)

    fun quarantine(): Set<String> = getStrings(K_QUARANTINE)
    fun addQuarantine(pkg: String) = setStrings(K_QUARANTINE, quarantine() + pkg)
    fun removeQuarantine(pkg: String) = setStrings(K_QUARANTINE, quarantine() - pkg)

    // ── Ajustes ──────────────────────────────────────────────────────
    /** Sensibilidad de alertas: baja | media | alta */
    var sensitivity: String
        get() = getEnc(K_SENSITIVITY, "media")
        set(v) = putEnc(K_SENSITIVITY, v)

    var smsShieldEnabled: Boolean
        get() = getBool(K_SMS_SHIELD, true)
        set(v) = setBool(K_SMS_SHIELD, v)

    var callShieldEnabled: Boolean
        get() = getBool(K_CALL_SHIELD, true)
        set(v) = setBool(K_CALL_SHIELD, v)

    var firewallEnabled: Boolean
        get() = getBool(K_FIREWALL, false)
        set(v) = setBool(K_FIREWALL, v)

    var panicMode: Boolean
        get() = getBool(K_PANIC, false)
        set(v) = setBool(K_PANIC, v)

    var noDisturbEnabled: Boolean
        get() = getBool(K_DND, false)
        set(v) = setBool(K_DND, v)

    /** Modo IA: local (offline, heurístico) | remoto (API compatible) */
    var aiMode: String
        get() = getEnc(K_AI_MODE, "local")
        set(v) = putEnc(K_AI_MODE, v)

    /** Endpoint compatible OpenAI. Vacío por defecto: sin llamadas salientes. */
    var aiEndpoint: String
        get() = getEnc(K_AI_ENDPOINT, "")
        set(v) = putEnc(K_AI_ENDPOINT, v)

    var aiModelName: String
        get() = getEnc(K_AI_MODEL, "gpt-4o-mini")
        set(v) = putEnc(K_AI_MODEL, v)

    /** Clave de API: cifrada en reposo con AES-GCM. */
    var aiApiKey: String
        get() = getEnc(K_AI_KEY_STORE, "")
        set(v) = putEnc(K_AI_KEY_STORE, v)

    var iocApiKey: String
        get() = getEnc(K_IOC_KEY_STORE, "")
        set(v) = putEnc(K_IOC_KEY_STORE, v)

    var firstRunDone: Boolean
        get() = getBool(K_FIRST_RUN, false)
        set(v) = setBool(K_FIRST_RUN, v)

    // ── Defensa automática (0 clics) ─────────────────────────────────
    /** Escaneo periódico + cuarentena automática. Activado por defecto. */
    var autoDefenseEnabled: Boolean
        get() = getBool(K_AUTO_DEFENSE, true)
        set(v) = setBool(K_AUTO_DEFENSE, v)

    /** Score a partir del cual se aísla una app automáticamente. */
    var autoQuarantineThreshold: Int
        get() = getLong(K_AUTO_THRESHOLD, 70L).toInt()
        set(v) = setLong(K_AUTO_THRESHOLD, v.toLong())

    /** Intervalo del escaneo automático, en minutos. */
    var autoScanIntervalMin: Int
        get() = getLong(K_AUTO_INTERVAL, 60L).toInt()
        set(v) = setLong(K_AUTO_INTERVAL, v.toLong())

    var lastAutoScan: Long
        get() = getLong(K_LAST_SCAN, 0L)
        set(v) = setLong(K_LAST_SCAN, v)

    /** Escaneos completados (contador del widget). */
    var scanCount: Long
        get() = getLong(K_SCAN_COUNT, 0L)
        set(v) = setLong(K_SCAN_COUNT, v)

    /** Apps auditadas en el último escaneo (contador del widget). */
    var lastAppsAnalyzed: Int
        get() = getLong(K_APPS_ANALYZED, 0L).toInt()
        set(v) = setLong(K_APPS_ANALYZED, v.toLong())

    var lastUsageTotal: Long
        get() = getLong(K_LAST_USAGE, 0L)
        set(v) = setLong(K_LAST_USAGE, v)

    // ── Herramientas avanzadas ───────────────────────────────────────
    /** Guardián de notificaciones (anti-phishing fuera de SMS). Por defecto: apagado. */
    var notifGuardEnabled: Boolean
        get() = getBool(K_NOTIF_GUARD, false)
        set(v) = setBool(K_NOTIF_GUARD, v)

    /** Consultas RDAP (fecha de creación del dominio). Solo con consentimiento. */
    var rdapEnabled: Boolean
        get() = getBool(K_RDAP, false)
        set(v) = setBool(K_RDAP, v)

    /** Rechazar llamadas que el operador no ha verificado (STIR/SHAKEN). */
    var strictVerifiedCalls: Boolean
        get() = getBool(K_STRICT_CALLS, false)
        set(v) = setBool(K_STRICT_CALLS, v)

    /** Estado del vigilante, para no repetir el mismo aviso. */
    var lastWatchKey: String
        get() = getEnc(K_WATCH_KEY, "")
        set(v) = putEnc(K_WATCH_KEY, v)

    /** Última foto de roles (sms|llamadas|accesibilidad) para detectar revocaciones. */
    var lastRoleState: String
        get() = getEnc(K_LAST_ROLES, "")
        set(v) = putEnc(K_LAST_ROLES, v)

    // ── Base de dominios actualizable ────────────────────────────────
    var iocFeedUrl: String
        get() = getEnc(K_FEED_URL, "")
        set(v) = putEnc(K_FEED_URL, v)

    /** Huella SHA-256 esperada de la lista (opcional, pero recomendable). */
    var iocFeedSha256: String
        get() = getEnc(K_FEED_SHA, "")
        set(v) = putEnc(K_FEED_SHA, v)

    var lastFeedUpdate: Long
        get() = getLong(K_FEED_TS, 0L)
        set(v) = setLong(K_FEED_TS, v)

    var lastFeedCount: Int
        get() = getLong(K_FEED_N, 0L).toInt()
        set(v) = setLong(K_FEED_N, v.toLong())

    // ── Bloqueo por horario ──────────────────────────────────────────
    var scheduleEnabled: Boolean
        get() = getBool(K_SCHED_ON, false)
        set(v) = setBool(K_SCHED_ON, v)

    var scheduleFrom: String
        get() = getEnc(K_SCHED_FROM, "23:30")
        set(v) = putEnc(K_SCHED_FROM, v)

    var scheduleTo: String
        get() = getEnc(K_SCHED_TO, "07:00")
        set(v) = putEnc(K_SCHED_TO, v)

    /** Estado aplicado actualmente (para no repetir el cambio). */
    var scheduleActive: Boolean
        get() = getBool(K_SCHED_ACTIVE, false)
        set(v) = setBool(K_SCHED_ACTIVE, v)

    // ── Anti-superposición (pantalla falsa) ──────────────────────────
    var overlayGuardEnabled: Boolean
        get() = getBool(K_OVERLAY, false)
        set(v) = setBool(K_OVERLAY, v)

    // ── Vigilante de red ─────────────────────────────────────────────
    /** Última alerta de red mostrada (para no repetir). */
    var lastNetKey: String
        get() = getEnc(K_NET_KEY, "")
        set(v) = putEnc(K_NET_KEY, v)

    /** Huella de los servidores DNS actuales. */
    var dnsFingerprint: String
        get() = getEnc(K_DNS_FP, "")
        set(v) = putEnc(K_DNS_FP, v)

    // ── Utilidades ───────────────────────────────────────────────────
    fun normalize(number: String): String = number.filter { it.isDigit() || it == '+' }

    private const val K_BLOCK_CALLS = "block_calls"
    private const val K_ALLOW_CALLS = "allow_calls"
    private const val K_BLOCK_PKGS = "block_pkgs"
    private const val K_TRUSTED = "trusted_pkgs"
    private const val K_QUARANTINE = "quarantine"
    private const val K_SENSITIVITY = "sensitivity"
    private const val K_SMS_SHIELD = "sms_shield"
    private const val K_CALL_SHIELD = "call_shield"
    private const val K_FIREWALL = "firewall"
    private const val K_PANIC = "panic"
    private const val K_DND = "dnd"
    private const val K_AI_MODE = "ai_mode"
    private const val K_AI_ENDPOINT = "ai_endpoint"
    private const val K_AI_MODEL = "ai_model"
    private const val K_AI_KEY_STORE = "ai_key_store"
    private const val K_IOC_KEY_STORE = "ioc_key_store"
    private const val K_FIRST_RUN = "first_run"
    private const val K_AUTO_DEFENSE = "auto_defense"
    private const val K_AUTO_THRESHOLD = "auto_threshold"
    private const val K_AUTO_INTERVAL = "auto_interval"
    private const val K_LAST_SCAN = "last_scan"
    private const val K_SCAN_COUNT = "scan_count"
    private const val K_APPS_ANALYZED = "apps_analyzed"
    private const val K_LAST_USAGE = "last_usage"
    private const val K_NOTIF_GUARD = "notif_guard"
    private const val K_RDAP = "rdap"
    private const val K_STRICT_CALLS = "strict_verified_calls"
    private const val K_WATCH_KEY = "watch_key"
    private const val K_LAST_ROLES = "last_roles"
    private const val K_FEED_URL = "feed_url"
    private const val K_FEED_SHA = "feed_sha"
    private const val K_FEED_TS = "feed_ts"
    private const val K_FEED_N = "feed_n"
    private const val K_SCHED_ON = "sched_on"
    private const val K_SCHED_FROM = "sched_from"
    private const val K_SCHED_TO = "sched_to"
    private const val K_SCHED_ACTIVE = "sched_active"
    private const val K_OVERLAY = "overlay_guard"
    private const val K_NET_KEY = "net_key"
    private const val K_DNS_FP = "dns_fp"
}
