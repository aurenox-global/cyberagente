package com.cyberagent.app

import android.widget.LinearLayout
import com.cyberagent.app.core.Bg
import com.cyberagent.app.core.Db
import com.cyberagent.app.core.Prefs
import com.cyberagent.app.core.SecurityAlert
import com.cyberagent.app.ui.Ui

/**
 * Historial completo de eventos (alertas).
 *
 * Antes solo se veían las últimas 12 en el panel y, si la carga pesaba, la
 * pantalla se quedaba congelada. Ahora se carga en segundo plano, con filtros
 * por severidad y paginación, así que la información siempre es accesible.
 */
class AlertsActivity : SafeActivity() {

    private lateinit var root: LinearLayout
    private lateinit var listCol: LinearLayout
    private lateinit var summary: LinearLayout
    private var filter: String = "TODAS"
    private var limit = 40
    private var loading = false

    override fun build(savedInstanceState: android.os.Bundle?) {
        render()
    }

    private fun render() {
        root = Ui.screen(
            this,
            "REGISTRO DE EVENTOS",
            "Historial cifrado en el dispositivo. Carga en segundo plano, sin bloqueos."
        )

        val head = Ui.section(this, "FILTROS", Ui.CY)
        val filters = listOf("TODAS", "ALTA", "MEDIA", "BAJA", "INFO")
        val rows = ArrayList<LinearLayout>()
        var current = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        current.addView(Ui.button(this, "TODAS", filter == "TODAS") { setFilter("TODAS") })
        for (f in filters.drop(1)) {
            current.addView(Ui.button(this, f, filter == f) { setFilter(f) })
            if (current.childCount >= 3) {
                rows.add(current)
                current = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            }
        }
        if (current.childCount > 0) rows.add(current)
        for (r in rows) head.addView(r)
        root.addView(head)

        summary = Ui.card(this)
        summary.addView(Ui.label(this, "Cargando eventos…", Ui.T3))
        root.addView(summary)

        listCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(listCol)

        load()
    }

    private fun setFilter(f: String) {
        filter = f
        limit = 40
        render()
    }

    private fun load() {
        if (loading) return
        loading = true
        summary.removeAllViews()
        summary.addView(Ui.label(this, "Cargando eventos…", Ui.T3))
        listCol.removeAllViews()
        Bg.run(this, "AlertsActivity") {
            val all = Db.alerts(limit = 500)
            val total = all.size
            val high = all.count { it.severity == "ALTA" || it.severity == "CRITICA" }
            val filtered = if (filter == "TODAS") all else all.filter { it.severity == filter }
            val shown = filtered.take(limit)
            runOnUiThread {
                loading = false
                if (isFinishing || isDestroyed) return@runOnUiThread
                fill(total, high, filtered.size, shown)
            }
        }
    }

    private fun fill(total: Int, high: Int, filteredCount: Int, shown: List<SecurityAlert>) {
        summary.removeAllViews()
        summary.addView(Ui.label(this, "$total eventos · $high de alta severidad", Ui.CY, 13.5f))
        summary.addView(Ui.label(this, "Filtro: $filter · mostrando ${shown.size} de $filteredCount", Ui.T2, 11.5f))
        if (filteredCount > shown.size) {
            summary.addView(Ui.button(this, "MOSTRAR MÁS", false) { limit += 40; load() })
        }

        if (shown.isEmpty()) {
            listCol.addView(Ui.label(this, "Sin eventos con este filtro.", Ui.T3))
            return
        }

        for (a in shown) {
            val card = Ui.card(this)
            val sev = Ui.severityColor(a.severity)
            val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            top.addView(Ui.chip(this, a.severity, sev))
            top.addView(Ui.label(this, Ui.time(a.ts) + " · " + a.module, Ui.T2, 11.5f))
            card.addView(top)
            card.addView(Ui.label(this, a.title, Ui.T1, 13f))
            if (a.detail.isNotEmpty()) card.addView(Ui.label(this, a.detail, Ui.T2, 11.5f))
            if (!a.packageName.isNullOrEmpty()) {
                card.addView(Ui.label(this, "Paquete: " + a.packageName, Ui.T3, 10.5f))
            }
            if (!a.mitre.isNullOrEmpty()) {
                card.addView(Ui.label(this, "MITRE ATT&CK: " + a.mitre, Ui.BLU, 10.5f))
            }
            listCol.addView(card)
        }
    }
}
