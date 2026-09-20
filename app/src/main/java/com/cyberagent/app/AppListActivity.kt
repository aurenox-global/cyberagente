package com.cyberagent.app

import android.content.Intent
import android.widget.LinearLayout
import com.cyberagent.app.core.AppAnalyzer
import com.cyberagent.app.core.AppRisk
import com.cyberagent.app.core.BatchScan
import com.cyberagent.app.core.Bg
import com.cyberagent.app.core.FirewallBridge
import com.cyberagent.app.core.Prefs
import com.cyberagent.app.core.QuarantineManager
import com.cyberagent.app.core.RiskLevel
import com.cyberagent.app.ui.Ui

/**
 * Auditoría de aplicaciones.
 *
 * Analizar todas las apps instaladas (permisos + instalador + targetSdk) tarda
 * bastante: se hace en segundo plano con aviso de carga, y los resultados se
 * guardan en memoria mientras dure la pantalla.
 */
class AppListActivity : SafeActivity() {

    private lateinit var root: LinearLayout
    private var showSystem = false
    private var limit = 30
    private var cached: List<AppRisk> = emptyList()
    private var loading = false

    override fun build(savedInstanceState: android.os.Bundle?) {
        render()
    }

    private fun render() {
        root = Ui.screen(
            this,
            "ANÁLISIS DE APLICACIONES",
            "Permisos reales declarados, procedencia y score de riesgo explicable."
        )

        val head = Ui.section(this, "RESUMEN", Ui.CY)
        head.addView(Ui.label(this, if (loading) "Analizando aplicaciones…" else "Listo.", Ui.T3))
        head.addView(Ui.row(
            this,
            Ui.button(this, if (showSystem) "OCULTAR SISTEMA" else "MOSTRAR SISTEMA") {
                showSystem = !showSystem; cached = emptyList(); render()
            },
            Ui.button(this, "REANALIZAR") { cached = emptyList(); render() }
        ))
        root.addView(head)

        val batch = Ui.section(
            this, "ESCANEO POR HUELLA", Ui.AMB,
            "Hash de cada APK: lista local siempre; VirusTotal solo si hay clave (con su cuota)."
        )
        val batchOut = Ui.label(this, "Sin ejecutar.", Ui.T3, 11.5f)
        batch.addView(batchOut)
        batch.addView(Ui.row(
            this,
            Ui.button(this, "ANALIZAR TODAS LAS APPS", true) {
                batchOut.text = "Iniciando…"
                Bg.run(this, "BatchScan") {
                    BatchScan.run(this, 10, 15_000L) { p ->
                        val text = "${p.done}/${p.total} · ${p.current}\n" +
                            "coincidencias locales: ${p.localHits} · online: ${p.onlineHits}" +
                            if (p.finished) "\n\n" + p.message else ""
                        runOnUiThread { batchOut.text = text }
                    }
                }
            },
            Ui.button(this, "CANCELAR") { BatchScan.cancelled = true }
        ))
        root.addView(batch)

        val listCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(listCol)

        if (cached.isEmpty()) {
            loading = true
            Bg.run(this, "AppList") {
                val all = AppAnalyzer.analyzeAll(this, includeSystem = showSystem)
                runOnUiThread {
                    loading = false
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    cached = all
                    fillHead(head)
                    fillList(listCol)
                }
            }
        } else {
            fillHead(head)
            fillList(listCol)
        }
    }

    private fun fillHead(head: LinearLayout) {
        head.removeAllViews()
        val risky = cached.count { it.level == RiskLevel.ALTO || it.level == RiskLevel.CRITICO }
        head.addView(Ui.label(
            this, "${cached.size} apps · $risky de riesgo alto o crítico",
            if (risky == 0) Ui.GRN else Ui.AMB, 14f
        ))
        head.addView(Ui.row(
            this,
            Ui.button(this, if (showSystem) "OCULTAR SISTEMA" else "MOSTRAR SISTEMA") {
                showSystem = !showSystem; cached = emptyList(); render()
            },
            Ui.button(this, "REANALIZAR") { cached = emptyList(); render() }
        ))
    }

    private fun fillList(listCol: LinearLayout) {
        listCol.removeAllViews()
        for (app in cached.take(limit)) listCol.addView(rowFor(app))
        if (cached.size > limit) {
            listCol.addView(Ui.button(this, "MOSTRAR MÁS (${cached.size - limit} restantes)") {
                limit += 30; fillList(listCol)
            })
        }
    }

    private fun rowFor(app: AppRisk): LinearLayout {
        val color = when (app.level) {
            RiskLevel.CRITICO, RiskLevel.ALTO -> Ui.RED
            RiskLevel.MEDIO -> Ui.AMB
            else -> Ui.GRN
        }
        val card = Ui.card(this)
        card.addView(Ui.row(
            this,
            Ui.chip(this, app.level.name, color),
            Ui.chip(this, "${app.score}/100", color)
        ))
        card.addView(Ui.label(this, app.label, Ui.T1, 13.5f))
        card.addView(Ui.label(
            this, app.packageName + if (app.isSystem) " · sistema" else "", Ui.T3, 11f
        ))
        card.addView(Ui.label(
            this, "targetSdk ${app.targetSdk} · ${app.dangerousPermissions.size} permisos sensibles",
            Ui.T2, 11.5f
        ))
        app.reasons.take(2).forEach { card.addView(Ui.label(this, "· $it", Ui.T2, 11.5f)) }
        card.addView(Ui.row(
            this,
            Ui.button(this, "DETALLE") {
                startActivity(
                    Intent(this, AppDetailActivity::class.java).putExtra("pkg", app.packageName)
                )
            },
            Ui.button(
                this,
                if (Prefs.blockedPackages().contains(app.packageName)) "PERMITIR RED" else "BLOQUEAR RED"
            ) {
                if (Prefs.blockedPackages().contains(app.packageName)) {
                    Prefs.unblockPackage(app.packageName)
                } else {
                    Prefs.blockPackage(app.packageName)
                }
                FirewallBridge.requestRestart(this)
                card.removeAllViews()
                card.addView(Ui.label(this, "Red actualizada para ${app.label}.", Ui.GRN, 12f))
            },
            Ui.button(this, "AISLAR") {
                QuarantineManager.quarantine(
                    this, app.packageName, "Aislamiento manual desde la lista de aplicaciones."
                )
                card.removeAllViews()
                card.addView(Ui.label(this, "${app.label} enviada a cuarentena.", Ui.AMB, 12f))
            }
        ))
        return card
    }
}
