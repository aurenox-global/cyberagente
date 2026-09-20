package com.cyberagent.app

import com.cyberagent.app.core.AppAnalyzer
import com.cyberagent.app.core.Db
import com.cyberagent.app.core.NetworkStatsReader
import com.cyberagent.app.core.Prefs
import com.cyberagent.app.core.RiskLevel
import com.cyberagent.app.ui.BarChartView
import com.cyberagent.app.ui.Ui

class MetricsSummaryActivity : SafeActivity() {

    override fun build(savedInstanceState: android.os.Bundle?) {
        val root = Ui.screen(this, "RESUMEN DE MÉTRICAS",
            "Datos reales del dispositivo: eventos, red, riesgo de apps y estado de los módulos.")

        val day = System.currentTimeMillis() - 86_400_000L
        val week = System.currentTimeMillis() - 7 * 86_400_000L

        val card = Ui.card(this)
        card.addView(Ui.heading(this, "EVENTOS"))
        card.addView(Ui.label(this, "Últimas 24 h: ${Db.countAlerts(day)}  ·  " +
            "alta severidad: ${Db.countHighAlerts(day)}", Ui.T1, 13f))
        card.addView(Ui.label(this, "Últimos 7 días: ${Db.countAlerts(week)}", Ui.T2))
        root.addView(card)

        val net = Ui.card(this)
        net.addView(Ui.heading(this, "RED"))
        net.addView(Ui.label(this, "Consumo de hoy: ${Ui.bytes(NetworkStatsReader.totalForToday(this))}",
            Ui.T1, 13f))
        root.addView(net)

        val chart = BarChartView(this).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this@MetricsSummaryActivity, 160)
            )
        }
        val series = NetworkStatsReader.last7Days(this)
        chart.setData(series)
        val chartCard = Ui.card(this)
        chartCard.addView(Ui.heading(this, "RED · ÚLTIMOS 7 DÍAS"))
        chartCard.addView(chart)
        chartCard.addView(Ui.label(this, "Total: ${Ui.bytes(series.sumOf { it.second })}", Ui.T2, 12f))
        root.addView(chartCard)

        val apps = AppAnalyzer.analyzeAll(this)
        val riskCard = Ui.card(this)
        riskCard.addView(Ui.heading(this, "RIESGO DE APLICACIONES"))
        riskCard.addView(Ui.label(this, "Auditadas: ${apps.size}", Ui.T1, 13f))
        riskCard.addView(Ui.label(this, "Crítico: ${apps.count { it.level == RiskLevel.CRITICO }}  ·  " +
            "Alto: ${apps.count { it.level == RiskLevel.ALTO }}  ·  " +
            "Medio: ${apps.count { it.level == RiskLevel.MEDIO }}", Ui.T2))
        root.addView(riskCard)

        val mods = Ui.card(this)
        mods.addView(Ui.heading(this, "ESTADO DE MÓDULOS"))
        mods.addView(Ui.statusDot(this, NetworkStatsReader.hasUsageAccess(this), "Acceso al uso (monitor de red)"))
        mods.addView(Ui.statusDot(this, Prefs.firewallEnabled, "Cortafuegos VPN"))
        mods.addView(Ui.statusDot(this, !Prefs.quarantine().isEmpty() || true,
            "Apps en cuarentena: ${Prefs.quarantine().size}"))
        mods.addView(Ui.statusDot(this, Prefs.panicMode, "Modo pánico"))
        mods.addView(Ui.statusDot(this, Prefs.noDisturbEnabled, "No Molestar"))
        root.addView(mods)
    }
}
