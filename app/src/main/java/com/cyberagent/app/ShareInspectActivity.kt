package com.cyberagent.app

import android.content.Intent
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatEditText as EditText
import com.cyberagent.app.core.Bg
import com.cyberagent.app.core.Db
import com.cyberagent.app.core.LinkInspector
import com.cyberagent.app.core.SecurityAlert
import com.cyberagent.app.ui.Ui

/**
 * Comprobador de enlaces como destino de "Compartir".
 *
 * Desde WhatsApp, correo, el navegador o cualquier app: Compartir → CyberAgent.
 * Analiza los enlaces del texto compartido con la lista local y las heurísticas
 * anti-phishing, y los deja también en el historial (queda constancia de lo que
 * te intentaron enviar).
 */
class ShareInspectActivity : SafeActivity() {

    private lateinit var outCol: LinearLayout

    override fun build(savedInstanceState: android.os.Bundle?) {
        render()
    }

    private fun render() {
        val shared = sharedText()
        val root = Ui.screen(
            this,
            "COMPROBAR ENLACE",
            "Anti-phishing offline: lista local de dominios + heurísticas."
        )

        val card = Ui.section(this, "TEXTO A ANALIZAR", Ui.CY)
        val et = EditText(this).apply {
            hint = "Pega un enlace o el texto completo del mensaje"
            setText(shared)
            setTextColor(Ui.T1); setHintTextColor(Ui.T3); textSize = 13f
            maxLines = 6
        }
        card.addView(et)
        outCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        card.addView(Ui.row(
            this,
            Ui.button(this, "ANALIZAR", true) { analyze(et.text.toString()) },
            Ui.button(this, "COPIAR RESULTADO") {
                val text = outCol.getTag()?.toString() ?: ""
                if (text.isNotEmpty()) {
                    val cm = getSystemService(android.content.ClipboardManager::class.java)
                    cm?.setPrimaryClip(android.content.ClipData.newPlainText("CyberAgent", text))
                    android.widget.Toast.makeText(this, "Resultado copiado", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        ))
        card.addView(outCol)
        root.addView(card)

        if (shared.isNotEmpty()) analyze(shared)
    }

    private fun sharedText(): String {
        val i: Intent? = intent
        if (i == null) return ""
        if (i.action == Intent.ACTION_SEND && i.type?.startsWith("text/") == true) {
            return i.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        }
        // También acepta ACTION_VIEW con un enlace.
        return i.dataString.orEmpty()
    }

    private fun analyze(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        outCol.removeAllViews()
        outCol.addView(Ui.label(this, "Analizando…", Ui.T3, 11.5f))
        Bg.run(this, "ShareInspect") {
            val urls = LinkInspector.findUrls(clean).ifEmpty { listOf(clean) }
            val verdicts = urls.take(6).map { LinkInspector.inspect(this, it) }
            val summary = verdicts.joinToString("\n") {
                (if (it.risky) "⚠ ${it.severity} " else "✓ ") + it.host + " — " +
                    it.reasons.firstOrNull().orEmpty()
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                outCol.removeAllViews()
                outCol.setTag(summary)
                val risky = verdicts.count { it.risky }
                outCol.addView(Ui.label(
                    this,
                    if (risky == 0) "Sin indicadores de riesgo en ${verdicts.size} enlace(s)."
                    else "$risky de ${verdicts.size} enlace(s) con indicios de riesgo.",
                    if (risky == 0) Ui.GRN else Ui.RED, 13f
                ))
                for (v in verdicts) {
                    val c = Ui.card(this)
                    c.addView(Ui.chip(
                        this, if (v.risky) v.severity else "SIN RIESGO",
                        if (v.risky) Ui.severityColor(v.severity) else Ui.GRN
                    ))
                    c.addView(Ui.label(this, v.host, Ui.T1, 12.5f))
                    for (r in v.reasons) c.addView(Ui.label(this, "· $r", Ui.T2, 11.5f))
                    c.addView(Ui.label(this, "Fuente: ${v.source}", Ui.T3, 10.5f))
                    outCol.addView(c)
                }
                if (risky > 0) {
                    Db.addAlert(
                        SecurityAlert(
                            severity = "MEDIA",
                            module = "Guardián de enlaces",
                            title = "Enlace compartido con indicios de riesgo",
                            detail = summary.take(500),
                            mitre = "T1566"
                        )
                    )
                }
            }
        }
    }
}
