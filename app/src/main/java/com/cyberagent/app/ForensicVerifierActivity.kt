package com.cyberagent.app

import android.content.Intent
import android.net.Uri
import androidx.appcompat.widget.AppCompatCheckBox as CheckBox
import android.widget.LinearLayout
import android.widget.Toast
import com.cyberagent.app.core.ForensicVerifier
import com.cyberagent.app.core.IocDatabase
import com.cyberagent.app.core.Prefs
import com.cyberagent.app.ui.Ui

class ForensicVerifierActivity : SafeActivity() {

    private lateinit var root: LinearLayout
    private var onlineConsent = false

    override fun build(savedInstanceState: android.os.Bundle?) {
        IocDatabase.load(this)
        render()
    }

    private fun render() {
        root = Ui.screen(this, "VERIFICADOR FORENSE",
            "Hash SHA-256 local + contraste con la base de indicadores. La consulta externa es opcional y explícita.")
        val card = Ui.card(this)
        card.addView(Ui.label(this, "Base local de IoC: v${IocDatabase.version} · ${IocDatabase.size()} entradas",
            Ui.CY, 13f))
        card.addView(Ui.button(this, "SELECCIONAR FICHERO Y VERIFICAR", true) {
            val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            }
            startActivityForResult(i, REQ_PICK)
        })
        val cb = CheckBox(this).apply {
            text = "Autorizar consulta externa de este hash (VirusTotal) — requiere clave configurada"
            setTextColor(Ui.T2)
            textSize = 12f
            isChecked = onlineConsent
            setOnCheckedChangeListener { _, v -> onlineConsent = v }
        }
        card.addView(cb)
        card.addView(Ui.label(this, if (Prefs.iocApiKey.isEmpty())
            "Sin clave de VirusTotal configurada: se usará solo la base local." else
            "Clave configurada. La consulta solo se hará si la autorizas aquí.",
            Ui.T3, 11.5f))
        root.addView(card)

        val explain = Ui.card(this)
        explain.addView(Ui.heading(this, "QUÉ HACE"))
        explain.addView(Ui.label(this, "· Calcula el SHA-256 del fichero en el dispositivo.\n" +
            "· Lo compara con la base local de indicadores (funciona sin conexión).\n" +
            "· Si lo autorizas, consulta la reputación del hash por HTTPS.\n" +
            "· Registra el resultado en el historial de alertas.", Ui.T2, 12f))
        root.addView(explain)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_PICK || resultCode != RESULT_OK) return
        val uri: Uri = data?.data ?: return
        val name = queryName(uri)
        Toast.makeText(this, "Calculando SHA-256…", Toast.LENGTH_SHORT).show()

        ForensicVerifier.verify(this, name, uri, onlineConsent) { result ->
            runOnUiThread {
                val card = Ui.card(this)
                if (result == null) {
                    card.addView(Ui.label(this, "No se pudo leer el fichero.", Ui.RED, 13f))
                } else {
                    val bad = result.verdict.startsWith("MALICIOSO")
                    card.addView(Ui.label(this, result.verdict, if (bad) Ui.RED else Ui.GRN, 14f))
                    card.addView(Ui.label(this, "Fichero: ${result.fileName}", Ui.T1, 12.5f))
                    card.addView(Ui.label(this, "Tamaño: ${Ui.bytes(result.sizeBytes)}", Ui.T2, 12f))
                    card.addView(Ui.label(this, "SHA-256:", Ui.T3, 11.5f))
                    card.addView(Ui.label(this, result.sha256, Ui.CY, 11f))
                    card.addView(Ui.label(this, "Fuente: ${result.source}", Ui.T2, 11.5f))
                    card.addView(Ui.label(this, result.detail, Ui.T2, 12f))
                }
                root.addView(card)
            }
        }
    }

    private fun queryName(uri: Uri): String {
        return try {
            contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && c.moveToFirst()) c.getString(idx) else uri.lastPathSegment ?: "fichero"
            } ?: (uri.lastPathSegment ?: "fichero")
        } catch (t: Throwable) {
            uri.lastPathSegment ?: "fichero"
        }
    }

    companion object {
        private const val REQ_PICK = 4501
    }
}
