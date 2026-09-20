package com.cyberagent.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.cyberagent.app.core.AppAnalyzer
import com.cyberagent.app.core.Db
import com.cyberagent.app.core.SecurityAlert

/**
 * Revisión en la instalación: cuando se instala o actualiza una app, se
 * audita automáticamente y, si su score es alto, se registra una alerta.
 */
class PackageChangeReceiver : BroadcastReceiver() {

    override fun onReceive(ctx: Context, intent: Intent) {
        val pkg = intent.data?.schemeSpecificPart ?: return
        if (pkg == ctx.packageName) return

        try {
            @Suppress("DEPRECATION")
            val info = ctx.packageManager.getPackageInfo(
                pkg,
                PackageManager.GET_PERMISSIONS
            )
            val risk = AppAnalyzer.analyze(ctx, info)
            if (risk.score >= 35) {
                Db.addAlert(
                    SecurityAlert(
                        severity = if (risk.level == com.cyberagent.app.core.RiskLevel.CRITICO) "ALTA" else "MEDIA",
                        module = "Auditoría de apps",
                        title = "${risk.label} — riesgo ${risk.level}",
                        detail = "Score ${risk.score}/100 al instalarse.\n" +
                            risk.reasons.take(4).joinToString("\n"),
                        packageName = pkg,
                        mitre = "T1204"
                    )
                )
            }
        } catch (t: Throwable) {
            // paquete no consultable
        }
    }
}
