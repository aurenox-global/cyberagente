package com.cyberagent.app

import androidx.appcompat.widget.AppCompatCheckBox as CheckBox
import androidx.appcompat.widget.AppCompatEditText as EditText
import android.widget.LinearLayout
import android.widget.Toast
import com.cyberagent.app.core.Db
import com.cyberagent.app.core.Prefs
import com.cyberagent.app.core.SecurityAlert
import com.cyberagent.app.core.SecurityAssistant
import com.cyberagent.app.ui.Ui

class AssistantActivity : SafeActivity() {

    private lateinit var root: LinearLayout

    override fun build(savedInstanceState: android.os.Bundle?) {
        render()
    }

    private fun render() {
        root = Ui.screen(this, "ASISTENTE DEFENSIVO (IA)",
            "Motor local determinista: funciona siempre, sin descargas ni ficheros externos.")

        val info = Ui.card(this)
        info.addView(Ui.label(this, "Modo actual: " + if (Prefs.aiMode == "remoto")
            "remoto (endpoint configurado)" else "local (offline)", Ui.CY, 13f))
        info.addView(Ui.label(this, "· Ciclo Observar → Analizar → Recomendar.\n" +
            "· Clasificación por severidad y referencia MITRE ATT&CK.\n" +
            "· Guardrails: no genera contenido ofensivo.\n" +
            "· Ninguna operación de red se ejecuta sin tu autorización.", Ui.T2, 12f))
        root.addView(info)

        val q = Ui.card(this)
        q.addView(Ui.heading(this, "CONSULTA"))
        val et = EditText(this).apply {
            hint = "p. ej.: ¿qué app es más sospechosa?"
            setTextColor(Ui.T1); setHintTextColor(Ui.T3); textSize = 13f
        }
        q.addView(et)
        var allowNet = false
        val cb = CheckBox(this).apply {
            text = "Autorizar consulta de red (solo modo remoto)"
            setTextColor(Ui.T2); textSize = 12f
            setOnCheckedChangeListener { _, v -> allowNet = v }
        }
        q.addView(cb)
        q.addView(Ui.button(this, "EJECUTAR ANÁLISIS", true) {
            val query = et.text.toString()
            if (SecurityAssistant.isOffensive(query)) {
                showResult(SecurityAssistant.refusal())
            } else {
                Toast.makeText(this, "Analizando…", Toast.LENGTH_SHORT).show()
                SecurityAssistant.analyze(this, query, allowNet) { text ->
                    runOnUiThread { showResult(text) }
                }
            }
        })
        root.addView(q)

        val hint = Ui.card(this)
        hint.addView(Ui.label(this, "Pruébalo: escribe «exploit» o «payload» y verás el guardrail en acción.",
            Ui.T3, 11.5f))
        root.addView(hint)
    }

    private fun showResult(text: String) {
        Db.addAlert(
            SecurityAlert(
                severity = "INFO",
                module = "Asistente",
                title = "Análisis del asistente ejecutado",
                detail = text.take(400)
            )
        )
        val card = Ui.card(this)
        card.addView(Ui.heading(this, "RESULTADO"))
        card.addView(Ui.label(this, text, Ui.T1, 12.5f))
        root.addView(card)
    }
}
