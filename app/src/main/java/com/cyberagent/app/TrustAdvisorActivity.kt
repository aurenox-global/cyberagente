package com.cyberagent.app

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.LinearLayout
import com.cyberagent.app.core.Bg
import com.cyberagent.app.core.Prefs
import com.cyberagent.app.core.QuarantineManager
import com.cyberagent.app.core.TrustAdvisor
import com.cyberagent.app.ui.Ui

/**
 * Panel de confianza: recomendaciones por aplicación con acciones directas.
 */
class TrustAdvisorActivity : SafeActivity() {

    override fun build(savedInstanceState: android.os.Bundle?) {
        render()
    }

    private fun render() {
        val root = Ui.screen(
            this,
            "PANEL DE CONFIANZA",
            "Cruza permisos, uso real y procedencia. Recomendaciones, no sentencias."
        )

        val head = Ui.section(this, "ANÁLISIS", Ui.CY)
        head.addView(Ui.label(this, "Analizando apps instaladas…", Ui.T3))
        root.addView(head)

        val listCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(listCol)

        Bg.run(this, "TrustAdvisor") {
            val advice = TrustAdvisor.advise(this)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                head.removeAllViews()
                val high = advice.count { it.severity == "ALTA" }
                head.addView(Ui.label(
                    this,
                    "${advice.size} recomendaciones · $high de prioridad alta",
                    if (high == 0) Ui.GRN else Ui.AMB, 13.5f
                ))
                head.addView(Ui.label(
                    this,
                    "Los datos de uso requieren el acceso al uso; sin él solo se analizan permisos y firma.",
                    Ui.T3, 10.5f
                ))
                if (advice.isEmpty()) {
                    listCol.addView(Ui.label(this, "Nada destacable: no hay recomendaciones.", Ui.GRN))
                    return@runOnUiThread
                }
                for (a in advice) listCol.addView(card(a))
            }
        }
    }

    private fun card(a: TrustAdvisor.Advice): LinearLayout {
        val c = Ui.card(this)
        val color = Ui.severityColor(a.severity)
        c.addView(Ui.chip(this, a.severity, color))
        c.addView(Ui.label(this, a.label, Ui.T1, 13.5f))
        c.addView(Ui.label(this, a.pkg, Ui.T3, 10.5f))
        c.addView(Ui.label(this, a.problem, Ui.T2, 12f))
        c.addView(Ui.label(this, "→ " + a.suggestion, Ui.CY, 11.5f))
        val blocked = Prefs.blockedPackages().contains(a.pkg)
        val trusted = Prefs.trustedPackages().contains(a.pkg)
        c.addView(Ui.row(
            this,
            Ui.button(this, "PERMISOS") {
                startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${a.pkg}")
                    )
                )
            },
            Ui.button(this, if (blocked) "PERMITIR RED" else "BLOQUEAR RED") {
                if (blocked) Prefs.unblockPackage(a.pkg) else Prefs.blockPackage(a.pkg)
                com.cyberagent.app.core.FirewallBridge.requestRestart(this)
                render()
            },
            Ui.button(this, if (trusted) "QUITAR CONFIANZA" else "MARCAR CONFIANZA") {
                if (trusted) Prefs.untrustPackage(a.pkg) else Prefs.trustPackage(a.pkg)
                render()
            },
            Ui.button(this, "DESINSTALAR") {
                startActivity(QuarantineManager.uninstallIntent(a.pkg))
            }
        ))
        return c
    }
}
