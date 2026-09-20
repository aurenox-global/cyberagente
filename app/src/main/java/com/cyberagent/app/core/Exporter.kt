package com.cyberagent.app.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStream

/**
 * Exportación de informes y copia de seguridad.
 *
 *  · Informe JSON: historial de eventos + estado de módulos, para guardar o
 *    compartir con quien revise el dispositivo.
 *  · Copia cifrada: el historial empaquetado con AES-256-GCM y una contraseña
 *    (PBKDF2-HMAC-SHA256, 120 000 iteraciones) para poder restaurarlo incluso
 *    en otro móvil. La clave del Keystore no sirve para eso a propósito.
 */
object Exporter {

    const val BACKUP_MAGIC = "CYBERAGENT-BACKUP-1"

    fun reportJson(ctx: Context): String {
        val root = JSONObject()
        root.put("app", "CyberAgent")
        root.put("version", com.cyberagent.app.BuildConfig.VERSION_NAME)
        root.put("generatedAt", System.currentTimeMillis())
        root.put("iocVersion", IocDatabase.version)
        root.put("iocEntries", IocDatabase.size())

        val modules = JSONObject()
        modules.put("accesoAlUso", NetworkStatsReader.hasUsageAccess(ctx))
        modules.put("cortafuegosVpn", Prefs.firewallEnabled)
        modules.put("tunelActivo", com.cyberagent.app.service.FirewallVpnService.isTunnelRunning())
        modules.put("modoPanico", Prefs.panicMode)
        modules.put("blindajeSms", Prefs.smsShieldEnabled)
        modules.put("appSmsPredeterminada", com.cyberagent.app.receiver.SmsDeliverReceiver.isDefaultSmsApp(ctx))
        modules.put("filtroLlamadas", Prefs.callShieldEnabled)
        modules.put("rolLlamadas", com.cyberagent.app.service.CallGuardService.hasRole(ctx))
        modules.put("accesibilidad", Perms.accessibilityEnabled(ctx))
        modules.put("guardianNotificaciones", Prefs.notifGuardEnabled)
        modules.put("consultaRdap", Prefs.rdapEnabled)
        modules.put("defensaAutomatica", Prefs.autoDefenseEnabled)
        modules.put("appsSinRed", Prefs.blockedPackages().size)
        modules.put("appsConfianza", Prefs.trustedPackages().size)
        modules.put("appsEnCuarentena", Prefs.quarantine().size)
        root.put("modules", modules)

        val arr = JSONArray()
        for (a in Db.alerts(limit = 2000)) {
            val o = JSONObject()
            o.put("ts", a.ts)
            o.put("severity", a.severity)
            o.put("module", a.module)
            o.put("title", a.title)
            o.put("detail", a.detail)
            o.put("package", a.packageName ?: "")
            o.put("mitre", a.mitre ?: "")
            arr.put(o)
        }
        root.put("alerts", arr)
        return root.toString(2)
    }

    fun writeReport(ctx: Context, out: OutputStream): Pair<Int, Long> {
        val text = reportJson(ctx)
        val bytes = text.toByteArray(Charsets.UTF_8)
        out.use { it.write(bytes) }
        return Db.countAlerts() to bytes.size.toLong()
    }

    // ── Copia cifrada ────────────────────────────────────────────────

    fun backupEncrypted(ctx: Context, passphrase: String): String {
        val root = JSONObject()
        root.put("magic", BACKUP_MAGIC)
        root.put("generatedAt", System.currentTimeMillis())
        val arr = JSONArray()
        for (a in Db.alerts(limit = 5000)) {
            val o = JSONObject()
            o.put("ts", a.ts)
            o.put("severity", a.severity)
            o.put("module", a.module)
            o.put("title", a.title)
            o.put("detail", a.detail)
            o.put("package", a.packageName ?: "")
            arr.put(o)
        }
        root.put("alerts", arr)
        val plain = root.toString().toByteArray(Charsets.UTF_8)

        val salt = ByteArray(16).also { java.security.SecureRandom().nextBytes(it) }
        val key = pbkdf2(passphrase, salt)
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, key)
        val ct = cipher.doFinal(plain)
        val packed = salt + cipher.iv + ct
        return android.util.Base64.encodeToString(packed, android.util.Base64.NO_WRAP)
    }

    /** Restaura eventos desde una copia. Devuelve cuántos se han importado. */
    fun restoreEncrypted(text: String, passphrase: String): Int {
        val packed = android.util.Base64.decode(text.trim(), android.util.Base64.DEFAULT)
        if (packed.size < 16 + 12 + 1) return -1
        val salt = packed.copyOfRange(0, 16)
        val iv = packed.copyOfRange(16, 28)
        val ct = packed.copyOfRange(28, packed.size)
        val key = pbkdf2(passphrase, salt)
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            javax.crypto.Cipher.DECRYPT_MODE, key,
            javax.crypto.spec.GCMParameterSpec(128, iv)
        )
        val plain = String(cipher.doFinal(ct), Charsets.UTF_8)
        val root = JSONObject(plain)
        if (root.optString("magic") != BACKUP_MAGIC) return -1
        val arr = root.optJSONArray("alerts") ?: return 0
        var n = 0
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            Db.addAlert(
                SecurityAlert(
                    ts = o.optLong("ts", System.currentTimeMillis()),
                    severity = o.optString("severity", "INFO"),
                    module = o.optString("module", "Importado"),
                    title = o.optString("title", ""),
                    detail = o.optString("detail", ""),
                    packageName = o.optString("package").ifEmpty { null }
                )
            )
            n++
        }
        return n
    }

    private fun pbkdf2(pass: String, salt: ByteArray): javax.crypto.SecretKey {
        val spec = javax.crypto.spec.PBEKeySpec(pass.toCharArray(), salt, 120_000, 256)
        return javax.crypto.SecretKeyFactory
            .getInstance("PBKDF2WithHmacSHA256").generateSecret(spec)
    }
}
