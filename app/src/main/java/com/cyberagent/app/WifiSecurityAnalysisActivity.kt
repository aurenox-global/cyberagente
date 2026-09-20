package com.cyberagent.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.cyberagent.app.core.Db
import com.cyberagent.app.core.SecurityAlert
import com.cyberagent.app.core.WifiAnalyzer
import com.cyberagent.app.ui.Ui

class WifiSecurityAnalysisActivity : SafeActivity() {

    override fun build(savedInstanceState: android.os.Bundle?) {
        render()
    }

    private fun render() {
        val root = Ui.screen(this, "ANÁLISIS DE SEGURIDAD WI-FI",
            "Cifrado de la red activa, exposición y señales de interceptación.")
        val r = WifiAnalyzer.analyze(this)

        val card = Ui.card(this)
        card.addView(Ui.label(this, "Red: ${r.ssid}", Ui.T1, 14f))
        card.addView(Ui.label(this, "BSSID: ${r.bssid.ifEmpty { "—" }}", Ui.T2, 11.5f))
        card.addView(Ui.label(this, "Cifrado: ${r.security}",
            if (r.isOpen || r.isWeakCrypto) Ui.RED else Ui.GRN, 13f))
        card.addView(Ui.label(this, "Señal: ${if (r.rssi != 0) "${r.rssi} dBm" else "—"}", Ui.T2, 11.5f))
        card.addView(Ui.label(this, "Índice de riesgo de red: ${r.score}/100",
            if (r.score >= 35) Ui.AMB else Ui.GRN, 12.5f))
        root.addView(card)

        val f = Ui.card(this)
        f.addView(Ui.heading(this, "HALLAZGOS"))
        if (r.findings.isEmpty()) {
            f.addView(Ui.label(this, "Sin hallazgos.", Ui.GRN))
        } else {
            r.findings.forEach { f.addView(Ui.label(this, "· $it", Ui.T2, 12f)) }
        }
        root.addView(f)

        val perm = Ui.card(this)
        if (!WifiAnalyzer.hasNearbyWifiPermission(this)) {
            perm.addView(Ui.label(this, "Sin permiso de dispositivos cercanos: no se puede verificar el " +
                "cifrado por escaneo.", Ui.AMB, 12.5f))
            perm.addView(Ui.button(this, "CONCEDER PERMISO", true) {
                val p = if (android.os.Build.VERSION.SDK_INT >= 33)
                    Manifest.permission.NEARBY_WIFI_DEVICES
                else Manifest.permission.ACCESS_FINE_LOCATION
                ActivityCompat.requestPermissions(this, arrayOf(p), 4601)
            })
        } else {
            perm.addView(Ui.label(this, "Permiso de dispositivos cercanos concedido.", Ui.GRN))
        }
        perm.addView(Ui.row(this,
            Ui.button(this, "REANALIZAR") { render() },
            Ui.button(this, "AJUSTES WI-FI") {
                startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
            }
        ))
        root.addView(perm)

        Db.addAlert(
            SecurityAlert(
                severity = if (r.score >= 35) "MEDIA" else "INFO",
                module = "Wi-Fi",
                title = "Análisis de red: ${r.ssid} (${r.security})",
                detail = r.findings.joinToString("\n").ifEmpty { "Sin hallazgos relevantes." },
                mitre = if (r.score >= 35) "T1557" else null
            )
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        render()
    }
}
