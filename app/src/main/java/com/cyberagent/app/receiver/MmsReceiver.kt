package com.cyberagent.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.cyberagent.app.core.Db
import com.cyberagent.app.core.Notify
import com.cyberagent.app.core.SecurityAlert

/**
 * Recepción de MMS como app de SMS predeterminada.
 *
 * Aviso honesto: la **descarga y el almacenamiento de MMS no están
 * implementados** (requiere descargar el PDU del MMSC y escribir en el
 * proveedor de MMS). Se informa al usuario de que ha llegado un MMS para que
 * pueda revisarlo en otra aplicación, en lugar de descartarlo en silencio.
 */
class MmsReceiver : BroadcastReceiver() {

    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION) return

        val from = intent.getStringExtra("address") ?: "desconocido"
        Db.addAlert(
            SecurityAlert(
                severity = "MEDIA",
                module = "Blindaje SMS",
                title = "MMS recibido (descarga no soportada)",
                detail = "Remitente: $from.\nCyberAgent no descarga MMS: si usas MMS con " +
                    "frecuencia, no lo configures como app de SMS predeterminada.",
                mitre = "T1566"
            )
        )
        Notify.post(
            ctx,
            "MMS detectado",
            "De $from. La descarga de MMS no está soportada por CyberAgent."
        )
    }
}
