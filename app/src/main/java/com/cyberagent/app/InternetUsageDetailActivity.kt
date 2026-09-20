package com.cyberagent.app

import android.widget.LinearLayout
import com.cyberagent.app.core.Bg
import com.cyberagent.app.core.NetworkStatsReader
import com.cyberagent.app.ui.Ui

/**
 * Detalle de consumo de una aplicación (últimos 7 días).
 * Serie calculada en segundo plano para no congelar la pantalla.
 */
class InternetUsageDetailActivity : SafeActivity() {

    override fun build(savedInstanceState: android.os.Bundle?) {
        val pkg = intent.getStringExtra("pkg") ?: run { finish(); return }
        val label = intent.getStringExtra("label") ?: pkg
        val root = Ui.screen(this, "CONSUMO POR APLICACIÓN", label)

        val uid = try {
            packageManager.getApplicationInfo(pkg, 0).uid
        } catch (t: Throwable) {
            -1
        }

        val card = Ui.section(this, "ÚLTIMOS 7 DÍAS", Ui.CY)
        card.addView(Ui.label(this, "Calculando…", Ui.T3))
        root.addView(card)

        if (uid < 0) {
            card.removeAllViews()
            card.addView(Ui.label(this, "Aplicación no encontrada (¿desinstalada?).", Ui.RED))
            return
        }

        Bg.run(this, "UsageDetail") {
            val series = NetworkStatsReader.dailySeriesForUid(this, uid, 7)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                card.removeAllViews()
                var totalRx = 0L
                var totalTx = 0L
                var max = 1L
                series.forEach { max = maxOf(max, it.second + it.third) }
                val fmt = java.text.SimpleDateFormat("dd/MM", java.util.Locale.getDefault())
                for ((start, rx, tx) in series) {
                    totalRx += rx
                    totalTx += tx
                    val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                    row.addView(Ui.label(this, fmt.format(java.util.Date(start)), Ui.T2, 11.5f))
                    val barCol = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    }
                    barCol.addView(Ui.bar(this, ((rx + tx) * 100 / max).toInt(), 100, if (rx + tx > 0) Ui.CY else Ui.LINE))
                    row.addView(barCol)
                    row.addView(Ui.label(this, "  ↓${Ui.bytes(rx)} ↑${Ui.bytes(tx)}",
                        if (rx + tx > 0) Ui.T1 else Ui.T3, 11f))
                    card.addView(row)
                }
                card.addView(Ui.divider(this))
                card.addView(Ui.row(
                    this,
                    Ui.chip(this, "Total ↓ ${Ui.bytes(totalRx)}", Ui.BLU),
                    Ui.chip(this, "Total ↑ ${Ui.bytes(totalTx)}", Ui.AMB),
                    Ui.chip(this, "Suma ${Ui.bytes(totalRx + totalTx)}", Ui.CY)
                ))
            }
        }

        val note = Ui.section(this, "NOTA", Ui.T3)
        note.addView(Ui.label(this,
            "Datos de las estadísticas del sistema. El bloqueo de red lo aplica el cortafuegos " +
                "VPN en el propio móvil: los paquetes se descartan, no se envían a ningún servidor.",
            Ui.T2, 11.5f))
        root.addView(note)
    }
}
