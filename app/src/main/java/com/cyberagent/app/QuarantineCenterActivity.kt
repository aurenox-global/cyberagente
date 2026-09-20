package com.cyberagent.app

import com.cyberagent.app.core.Db
import com.cyberagent.app.core.Prefs
import com.cyberagent.app.core.QuarantineManager
import com.cyberagent.app.ui.Ui

class QuarantineCenterActivity : SafeActivity() {

    override fun build(savedInstanceState: android.os.Bundle?) {
        render()
    }

    private fun render() {
        val root = Ui.screen(this, "CENTRO DE CUARENTENA",
            "Aislamiento efectivo: la app queda sin acceso a la red vía cortafuegos local.")

        val q = Prefs.quarantine()
        val card = Ui.card(this)
        card.addView(Ui.label(this, "Apps en cuarentena: ${q.size}",
            if (q.isEmpty()) Ui.GRN else Ui.AMB, 14f))
        card.addView(Ui.label(this, "Cortafuegos: " +
            (if (Prefs.firewallEnabled) "activo" else "inactivo (actívalo para que el aislamiento tenga efecto)"),
            if (Prefs.firewallEnabled) Ui.GRN else Ui.AMB, 12f))
        card.addView(Ui.button(this, if (Prefs.firewallEnabled) "DETENER CORTAFUEGOS" else "ACTIVAR CORTAFUEGOS") {
            if (Prefs.firewallEnabled) {
                com.cyberagent.app.service.FirewallVpnService.stop(this)
            } else {
                val prep = android.net.VpnService.prepare(this)
                if (prep != null) startActivityForResult(prep, 4401)
                else com.cyberagent.app.service.FirewallVpnService.start(this)
            }
            render()
        })
        root.addView(card)

        if (q.isEmpty()) {
            val empty = Ui.card(this)
            empty.addView(Ui.label(this, "No hay aplicaciones aisladas.", Ui.T3))
            empty.addView(Ui.label(this, "Aísla una app desde su detalle o desde la lista de aplicaciones.",
                Ui.T2, 11.5f))
            root.addView(empty)
        }

        for (pkg in q) {
            val c = Ui.card(this)
            val label = try {
                packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
            } catch (t: Throwable) {
                pkg
            }
            c.addView(Ui.label(this, label, Ui.T1, 13.5f))
            c.addView(Ui.label(this, pkg, Ui.T3, 11f))
            c.addView(Ui.row(this,
                Ui.button(this, "LIBERAR") { QuarantineManager.release(this, pkg); render() },
                Ui.button(this, "DESINSTALAR") { startActivity(QuarantineManager.uninstallIntent(pkg)) },
                Ui.button(this, "DETALLE") {
                    startActivity(
                        android.content.Intent(this, AppDetailActivity::class.java).putExtra("pkg", pkg)
                    )
                }
            ))
            root.addView(c)
        }

        val log = Ui.card(this)
        log.addView(Ui.heading(this, "HISTORIAL DE CUARENTENA"))
        val events = Db.alerts(limit = 40).filter { it.module == "Cuarentena" }
        if (events.isEmpty()) {
            log.addView(Ui.label(this, "Sin movimientos registrados.", Ui.T3))
        } else {
            for (e in events) {
                log.addView(Ui.label(this, "${Ui.time(e.ts)} · [${e.severity}] ${e.title}",
                    Ui.severityColor(e.severity), 11.5f))
                log.addView(Ui.label(this, e.detail.lines().firstOrNull() ?: "", Ui.T2, 11f))
                log.addView(Ui.divider(this))
            }
        }
        root.addView(log)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 4401 && resultCode == RESULT_OK) {
            com.cyberagent.app.service.FirewallVpnService.start(this)
        }
        render()
    }
}
