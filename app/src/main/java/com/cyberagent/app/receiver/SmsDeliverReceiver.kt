package com.cyberagent.app.receiver

import android.app.role.RoleManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContentValues
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import com.cyberagent.app.core.AutoDefense
import com.cyberagent.app.core.Db
import com.cyberagent.app.core.LinkInspector
import com.cyberagent.app.core.Notify
import com.cyberagent.app.core.Prefs
import com.cyberagent.app.core.SecurityAlert

/**
 * Recepción REAL de SMS como aplicación predeterminada.
 *
 *  · Mensaje legítimo → se **guarda en la bandeja de entrada** del sistema
 *    (content://sms/inbox) para que aparezca en cualquier app de mensajes.
 *  · Mensaje bloqueado o fraudulento → se **descarta** (no se guarda) y se
 *    registra la alerta.
 *
 * Solo se invoca cuando el usuario ha concedido el rol de SMS predeterminada.
 */
class SmsDeliverReceiver : BroadcastReceiver() {

    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return
        if (!isDefaultSmsApp(ctx)) return

        val messages = try {
            Telephony.Sms.Intents.getMessagesFromIntent(intent)
        } catch (t: Throwable) {
            null
        } ?: return

        for (m in messages) {
            val sender = m.originatingAddress ?: "desconocido"
            val body = m.messageBody ?: ""
            val normalized = Prefs.normalize(sender)

            val inBlocklist = Prefs.blockedCalls().contains(normalized)
            val riskyLink = LinkInspector.findUrls(body)
                .map { LinkInspector.inspect(ctx, it) }
                .firstOrNull { it.risky }
            val fraud = SmsMonitorReceiver.looksSuspicious(body) || riskyLink != null
            val shouldBlock = Prefs.smsShieldEnabled && (inBlocklist || fraud)

            if (shouldBlock) {
                // No se guarda: el mensaje queda descartado.
                Db.addAlert(
                    SecurityAlert(
                        severity = "ALTA",
                        module = "Blindaje SMS",
                        title = if (inBlocklist) "SMS bloqueado (remitente en lista negra)"
                        else "SMS fraudulento descartado",
                        detail = buildString {
                            append("Remitente: ").append(sender).append('\n')
                            if (body.isNotEmpty()) append("Contenido: ").append(body.take(160)).append('\n')
                            append(
                                if (inBlocklist) "El remitente está en tu lista de bloqueo."
                                else if (riskyLink != null)
                                    "Enlace peligroso (${riskyLink.host}): " +
                                        riskyLink.reasons.firstOrNull().orEmpty()
                                else "Patrón de phishing o estafa detectado por la heurística."
                            )
                        },
                        mitre = "T1566"
                    )
                )
                Notify.post(
                    ctx,
                    "SMS bloqueado",
                    "De $sender: " + body.take(120)
                )
                continue
            }

            // Legítimo: se guarda en la bandeja del sistema.
            val stored = storeInbox(ctx, sender, body, m.timestampMillis)
            Db.addAlert(
                SecurityAlert(
                    severity = "INFO",
                    module = "Blindaje SMS",
                    title = if (stored) "SMS entregado y guardado" else "SMS entregado",
                    detail = "Remitente: $sender" +
                        (if (stored) "" else "\nNo se pudo escribir en la bandeja del sistema.")
                )
            )
        }
    }

    /** Escribe el mensaje en la bandeja de entrada del sistema. */
    private fun storeInbox(ctx: Context, address: String, body: String, dateMs: Long): Boolean {
        return try {
            val values = ContentValues().apply {
                put("address", address)
                put("body", body)
                put("date", if (dateMs > 0) dateMs else System.currentTimeMillis())
                put("read", 0)
                put("seen", 0)
                put("type", 1) // MESSAGE_TYPE_INBOX
            }
            ctx.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values) != null
        } catch (t: Throwable) {
            // En dual-SIM puede faltar sub_id: se reintenta sin esa columna
            try {
                val values = ContentValues().apply {
                    put("address", address)
                    put("body", body)
                    put("date", System.currentTimeMillis())
                    put("read", 0)
                    put("seen", 0)
                    put("type", 1)
                    put("sub_id", -1)
                }
                ctx.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values) != null
            } catch (t2: Throwable) {
                false
            }
        }
    }

    companion object {
        fun isDefaultSmsApp(ctx: Context): Boolean {
            return try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val rm = ctx.getSystemService(RoleManager::class.java)
                    rm != null && rm.isRoleHeld(RoleManager.ROLE_SMS)
                } else {
                    Telephony.Sms.getDefaultSmsPackage(ctx) == ctx.packageName
                }
            } catch (t: Throwable) {
                false
            }
        }

        /** Atajo usado por la auto-defensa para no aislar la app de SMS. */
        fun critical(ctx: Context, pkg: String): Boolean = AutoDefense.isCriticalRole(ctx, pkg)
    }
}
