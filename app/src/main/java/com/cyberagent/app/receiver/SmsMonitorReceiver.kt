package com.cyberagent.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.cyberagent.app.core.Db
import com.cyberagent.app.core.LinkInspector
import com.cyberagent.app.core.Prefs
import com.cyberagent.app.core.SecurityAlert

/**
 * Monitor de SMS: analiza los mensajes entrantes y avisa de los sospechosos.
 * No elimina nada (para filtrar de verdad hace falta ser la app de SMS
 * predeterminada; eso lo hace SmsDeliverReceiver).
 */
class SmsMonitorReceiver : BroadcastReceiver() {

    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        if (!Prefs.smsShieldEnabled) return

        val messages = try {
            Telephony.Sms.Intents.getMessagesFromIntent(intent)
        } catch (t: Throwable) {
            null
        } ?: return

        for (m in messages) {
            val sender = m.originatingAddress ?: "desconocido"
            val body = m.messageBody ?: ""
            val normalized = Prefs.normalize(sender)

            val blocked = Prefs.blockedCalls().contains(normalized)
            val suspicious = looksSuspicious(body)
            // Enlaces del mensaje: lista local de dominios + heurísticas anti-phishing.
            val riskyLinks = LinkInspector.findUrls(body)
                .map { LinkInspector.inspect(ctx, it) }
                .filter { it.risky }

            if (blocked || suspicious || riskyLinks.isNotEmpty()) {
                Db.addAlert(
                    SecurityAlert(
                        severity = if (blocked || riskyLinks.any { it.severity == "ALTA" }) "ALTA" else "MEDIA",
                        module = "Blindaje SMS",
                        title = if (blocked) "SMS de remitente bloqueado" else "SMS sospechoso",
                        detail = buildString {
                            append("Remitente: ").append(sender).append('\n')
                            if (body.isNotEmpty()) {
                                append("Contenido: ").append(body.take(140))
                            }
                            if (suspicious) {
                                append("\nMotivo: patrón típico de phishing/estafa " +
                                    "(enlace acortado, premio, banco, urgencia).")
                            } else {
                                append("\nMotivo: el remitente está en tu lista de bloqueo.")
                            }
                            for (v in riskyLinks.take(3)) {
                                append("\nEnlace: ").append(v.host).append(" — ")
                                    .append(v.reasons.firstOrNull() ?: "sospechoso")
                            }
                        },
                        mitre = if (suspicious) "T1566" else null
                    )
                )
            }
        }
    }

    companion object {
        private val PATTERNS = listOf(
            "bit.ly", "tinyurl", "t.co/", "cutt.ly", "acortar",
            "premio", "has ganado", "ganaste", "reclama",
            "paquete retenido", "hacienda", "agencia tributaria",
            "banco", "bbva", "santander", "caixabank", "tarjeta bloqueada",
            "verifica tu cuenta", "actualiza tus datos", "urgente",
            "pin", "clave", "contraseña", "código de seguridad"
        )

        fun looksSuspicious(body: String): Boolean {
            val b = body.lowercase()
            if (PATTERNS.any { b.contains(it) }) return true
            // Enlaces con IP directa
            val ipLink = Regex("""https?://\d{1,3}(\.\d{1,3}){3}""")
            return ipLink.containsMatchIn(b)
        }
    }
}
