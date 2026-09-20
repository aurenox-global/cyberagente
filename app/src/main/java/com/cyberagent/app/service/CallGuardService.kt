package com.cyberagent.app.service

import android.app.role.RoleManager
import android.content.Context
import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import com.cyberagent.app.core.Db
import com.cyberagent.app.core.Prefs
import com.cyberagent.app.core.SecurityAlert

/**
 * Filtro de llamadas REAL mediante CallScreeningService (API 24+).
 * Requiere que el usuario conceda el rol de filtrado de llamadas
 * (ROLE_CALL_SCREENING) — se solicita desde la pantalla de blindaje.
 */
class CallGuardService : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        if (!Prefs.callShieldEnabled) {
            allow(callDetails)
            return
        }

        val number = callDetails.handle?.schemeSpecificPart ?: ""
        val normalized = Prefs.normalize(number)
        // getCallDirection() es API 29+. En versiones anteriores no podemos
        // distinguir la dirección: se asume entrante para no bloquear salientes.
        val incoming = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            callDetails.callDirection == Call.Details.DIRECTION_INCOMING
        } else {
            true
        }
        val permitted = incoming
        val isAllowed = Prefs.allowedCalls().contains(normalized)
        val isBlocked = Prefs.blockedCalls().contains(normalized)

        // Heurística anti-spam: números ocultos o de aspecto anómalo.
        // Solo números ocultos o con longitud imposible. Se quitó la regla de
        // "números cortos": bloqueaba códigos cortos legítimos (bancos, avisos).
        val looksSpam = normalized.isEmpty() || normalized.count { it.isDigit() } > 15

        // STIR/SHAKEN (API 30+): el operador marca si el número está verificado.
        val verification = try {
            if (android.os.Build.VERSION.SDK_INT >= 30) callDetails.callerNumberVerificationStatus
            else -1
        } catch (t: Throwable) {
            -1
        }
        // 0 = no verificado por el operador, 1 = verificado, 2 = fallo de verificación.
        val unverified = verification == 0

        val block = permitted && !isAllowed && (
            isBlocked || looksSpam ||
                (Prefs.strictVerifiedCalls && unverified && normalized.isNotEmpty())
            )

        if (block) {
            respondToCall(
                callDetails,
                CallResponse.Builder()
                    .setDisallowCall(true)
                    .setRejectCall(true)
                    .setSkipCallLog(false)
                    .setSkipNotification(true)
                    .build()
            )
            Db.addAlert(
                SecurityAlert(
                    severity = "ALTA",
                    module = "Blindaje de llamadas",
                    title = "Llamada rechazada",
                    detail = buildString {
                        append("Número: ").append(if (number.isEmpty()) "oculto" else number).append('\n')
                        append(
                            when {
                                isBlocked -> "Motivo: está en tu lista de bloqueo."
                                normalized.isEmpty() -> "Motivo: número oculto (patrón habitual de spam)."
                                Prefs.strictVerifiedCalls && unverified ->
                                    "Motivo: el operador NO pudo verificar el número (STIR/SHAKEN) " +
                                        "y tienes activado rechazar llamadas no verificadas."
                                else -> "Motivo: número con formato anómalo (posible spoofing)."
                            }
                        )
                        append("\nVerificación del operador: ").append(
                            when (verification) {
                                1 -> "verificada"
                                0 -> "NO verificada"
                                2 -> "fallo de verificación"
                                else -> "no disponible en este móvil"
                            }
                        )
                    },
                    mitre = "T1585"
                )
            )
        } else {
            allow(callDetails)
        }
    }

    private fun allow(details: Call.Details) {
        respondToCall(details, CallResponse.Builder().setDisallowCall(false).build())
    }

    companion object {
        /** ¿Tiene la app el rol de filtrado de llamadas? */
        fun hasRole(ctx: Context): Boolean {
            return try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val rm = ctx.getSystemService(RoleManager::class.java)
                    rm != null && rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) &&
                        rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
                } else {
                    false
                }
            } catch (t: Throwable) {
                false
            }
        }
    }
}
