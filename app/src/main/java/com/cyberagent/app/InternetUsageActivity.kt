package com.cyberagent.app

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.LinearLayout
import com.cyberagent.app.core.Bg
import com.cyberagent.app.core.FirewallBridge
import com.cyberagent.app.core.NetworkStatsReader
import com.cyberagent.app.core.Prefs
import com.cyberagent.app.ui.Ui
import java.util.Calendar

/**
 * Consumo de red por aplicación.
 *
 * La consulta a NetworkStatsManager tarda segundos: se hace SIEMPRE en
 * segundo plano y la pantalla muestra "Calculando…" mientras tanto. Antes se
 * hacía en el hilo principal y la app se quedaba congelada.
 */
class InternetUsageActivity : SafeActivity() {

    private lateinit var root: LinearLayout

    override fun build(savedInstanceState: android.os.Bundle?) {
        render()
    }

    private fun render() {
        root = Ui.screen(
            this,
            "USO DE INTERNET",
            "Consumo real por aplicación, leído de las estadísticas del sistema."
        )

        if (!com.cyberagent.app.core.Perms.usageAccess(this)) {
            val warn = Ui.section(this, "FALTA UN ACCESO", Ui.AMB)
            warn.addView(Ui.label(this, "Sin «Acceso al uso», Android no permite leer el consumo por app.",
                Ui.T2, 12f))
            warn.addView(Ui.actionRow(this, "📊  Conceder acceso al uso",
                "Ajustes → Acceso al uso → CyberAgent", Ui.AMB) {
                startActivity(
                    Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:$packageName"))
                )
            })
            warn.addView(Ui.actionRow(this, "🔐  Asistente de permisos",
                "Recorrido completo de accesos", Ui.CY) {
                startActivity(Intent(this, PermissionsActivity::class.java))
            })
            root.addView(warn)
        }

        val head = Ui.card(this)
        head.addView(Ui.label(this, "Calculando consumo…", Ui.T3))
        root.addView(head)

        val listCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(listCol)

        Bg.run(this, "InternetUsage") { load(head, listCol) }
    }

    private fun load(head: LinearLayout, listCol: LinearLayout) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val rows = NetworkStatsReader.perAppUsage(this, cal.timeInMillis, System.currentTimeMillis())
        val blocked = Prefs.blockedPackages()
        val total = rows.sumOf { it.total }

        runOnUiThread {
            if (isFinishing || isDestroyed) return@runOnUiThread
            head.removeAllViews()
            head.addView(Ui.label(this, "Hoy: ${Ui.bytes(total)} en ${rows.size} apps", Ui.CY, 15f))
            head.addView(Ui.row(
                this,
                Ui.chip(this, "${blocked.size} apps sin red", Ui.BLU),
                Ui.chip(this, "actualizado " + Ui.time(System.currentTimeMillis()), Ui.T3)
            ))
            head.addView(Ui.actionRow(this, "🔄  Recalcular", "Vuelve a leer las estadísticas", Ui.CY) {
                NetworkStatsReader.invalidate()
                render()
            })

            listCol.removeAllViews()
            if (rows.isEmpty()) {
                listCol.addView(Ui.label(this, "Sin datos (concede el acceso al uso).", Ui.T3))
                return@runOnUiThread
            }
            for (r in rows.take(60)) {
                listCol.addView(rowFor(r, blocked.contains(r.packageName)))
            }
            if (rows.size > 60) {
                listCol.addView(Ui.label(this, "…y ${rows.size - 60} apps más con menos consumo.", Ui.T3, 11f))
            }
        }
    }

    private fun rowFor(r: com.cyberagent.app.core.UsageRow, blocked: Boolean): LinearLayout {
        val card = Ui.card(this)
        card.addView(Ui.label(
            this, "${r.label} — ${Ui.bytes(r.total)}", if (blocked) Ui.RED else Ui.T1, 13f
        ))
        card.addView(Ui.row(
            this,
            Ui.chip(this, "↓ ${Ui.bytes(r.rxBytes)}", Ui.BLU),
            Ui.chip(this, "↑ ${Ui.bytes(r.txBytes)}", Ui.AMB),
            if (blocked) Ui.chip(this, "SIN RED", Ui.RED) else Ui.chip(this, "con red", Ui.GRN)
        ))
        card.addView(Ui.row(
            this,
            Ui.button(this, "DETALLE") {
                startActivity(
                    Intent(this, InternetUsageDetailActivity::class.java)
                        .putExtra("pkg", r.packageName).putExtra("label", r.label)
                )
            },
            Ui.button(this, if (blocked) "PERMITIR RED" else "BLOQUEAR RED") {
                if (blocked) Prefs.unblockPackage(r.packageName) else Prefs.blockPackage(r.packageName)
                FirewallBridge.requestRestart(this)
                render()
            }
        ))
        return card
    }
}
