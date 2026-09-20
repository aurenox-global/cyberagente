package com.cyberagent.app

import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatEditText as EditText
import com.cyberagent.app.core.Bg
import com.cyberagent.app.core.Db
import com.cyberagent.app.core.LinkInspector
import com.cyberagent.app.core.SelfTest
import com.cyberagent.app.ui.Ui

/**
 * Herramientas: autodiagnóstico del motor defensivo y comprobador de enlaces.
 *
 * Esto es la parte "de verdad" del proyecto: no dice lo que la app cree, sino
 * lo que ha podido comprobar (cifrado, base de datos, lista de indicadores,
 * analizador DNS, firma de la propia app, accesos del sistema…).
 */
class SelfTestActivity : SafeActivity() {

    private lateinit var root: LinearLayout
    private lateinit var results: LinearLayout

    override fun build(savedInstanceState: android.os.Bundle?) {
        render()
    }

    private fun render() {
        root = Ui.screen(
            this,
            "HERRAMIENTAS Y AUTODIAGNÓSTICO",
            "Comprueba que los mecanismos reales funcionan en este móvil."
        )

        val head = Ui.section(this, "AUTODIAGNÓSTICO", Ui.CY)
        head.addView(Ui.label(this, "Ejecutando comprobaciones…", Ui.T3))
        root.addView(head)

        results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(results)

        Bg.run(this, "SelfTest") {
            val checks = SelfTest.run(this)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                head.removeAllViews()
                val ok = checks.count { it.ok }
                head.addView(Ui.label(
                    this, "$ok/${checks.size} comprobaciones correctas",
                    if (ok == checks.size) Ui.GRN else Ui.AMB, 14f
                ))
                head.addView(Ui.bar(this, ok, checks.size, if (ok == checks.size) Ui.GRN else Ui.AMB))
                head.addView(Ui.button(this, "VOLVER A EJECUTAR") { render() })
                Db.addAlert(
                    com.cyberagent.app.core.SecurityAlert(
                        severity = "INFO", module = "Autodiagnóstico",
                        title = "Autodiagnóstico ejecutado",
                        detail = checks.filter { !it.ok }.joinToString("\n") { "✗ " + it.name + ": " + it.detail }
                            .ifEmpty { "Todas las comprobaciones correctas ($ok/${checks.size})." }
                    )
                )

                for (c in checks) {
                    val card = Ui.card(this)
                    card.addView(Ui.chip(this, if (c.ok) "OK" else "REVISAR", if (c.ok) Ui.GRN else Ui.RED))
                    card.addView(Ui.label(this, c.name, Ui.T1, 13f))
                    card.addView(Ui.label(this, c.detail, Ui.T2, 11.5f))
                    results.addView(card)
                }
            }
        }

        val link = Ui.section(this, "COMPROBAR ENLACE O DOMINIO", Ui.AMB, "Anti-phishing offline (lista local + heurísticas).")
        val et = EditText(this).apply {
            hint = "http://ejemplo.com/oferta"
            setTextColor(Ui.T1); setHintTextColor(Ui.T3); textSize = 13f
        }
        link.addView(et)
        val outCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        link.addView(Ui.row(
            this,
            Ui.button(this, "COMPROBAR", true) {
                val text = et.text.toString().trim()
                if (text.isEmpty()) return@button
                Bg.run(this, "LinkCheck") {
                    val urls = if (LinkInspector.findUrls(text).isEmpty()) listOf(text)
                    else LinkInspector.findUrls(text)
                    val verdicts = urls.take(5).map { LinkInspector.inspect(this, it) }
                    runOnUiThread {
                        if (isFinishing || isDestroyed) return@runOnUiThread
                        outCol.removeAllViews()
                        for (v in verdicts) {
                            val c = Ui.card(this)
                            c.addView(Ui.chip(
                                this, if (v.risky) v.severity else "SIN RIESGO",
                                if (v.risky) Ui.severityColor(v.severity) else Ui.GRN
                            ))
                            c.addView(Ui.label(this, v.host, Ui.T1, 13f))
                            for (r in v.reasons) c.addView(Ui.label(this, "· $r", Ui.T2, 11.5f))
                            c.addView(Ui.label(this, "Fuente: ${v.source}", Ui.T3, 10.5f))
                            outCol.addView(c)
                        }
                    }
                }
            },
            Ui.button(this, "PROBAR EJEMPLO") { et.setText("http://bit.ly/oferta-banco") }
        ))
        link.addView(outCol)
        root.addView(link)
    }
}
