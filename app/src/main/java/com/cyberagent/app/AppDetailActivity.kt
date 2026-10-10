package com.cyberagent.app

import android.content.pm.PackageManager
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatCheckBox as CheckBox
import com.cyberagent.app.core.AppAnalyzer
import com.cyberagent.app.core.ApkReputation
import com.cyberagent.app.core.Bg
import com.cyberagent.app.core.Prefs
import com.cyberagent.app.core.QuarantineManager
import com.cyberagent.app.ui.Ui

class AppDetailActivity : SafeActivity() {

    private var pkg: String? = null

    override fun build(savedInstanceState: android.os.Bundle?) {
        pkg = intent.getStringExtra("pkg")
        render()
    }

    private fun render() {
        val p = pkg
        if (p == null) {
            finish()
            return
        }
        val root = Ui.screen(this, "DETALLE DE APLICACIÓN", p)

        val info = try {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(p, PackageManager.GET_PERMISSIONS or PackageManager.GET_SIGNATURES)
        } catch (t: Throwable) {
            null
        }
        if (info == null) {
            root.addView(Ui.label(this, "Paquete no encontrado (¿desinstalado?).", Ui.RED))
            return
        }

        // Análisis rápido (permisos + firma + postura). El hash del APK se añade
        // después en segundo plano para no bloquear la pantalla.
        var risk = AppAnalyzer.analyze(this, info)

        val headCard = Ui.card(this)
        val scoreLabel = Ui.label(this, "", Ui.T1, 14f)
        headCard.addView(scoreLabel)
        headCard.addView(
            Ui.label(
                this,
                "targetSdk ${risk.targetSdk}" + (if (risk.isSystem) " · app de sistema" else ""),
                Ui.T2
            )
        )
        headCard.addView(Ui.label(this, "Firma SHA-256: " + (risk.signerSha256.take(32).ifEmpty { "—" }) + "…", Ui.T3, 10.5f))
        root.addView(headCard)

        fun paint() {
            scoreLabel.text = "${risk.label} — ${risk.level} (${risk.score}/100)"
            scoreLabel.setTextColor(if (risk.score >= 45) Ui.RED else Ui.GRN)
        }
        paint()

        // Integración del hash del APK (IoC) en el score, en segundo plano.
        val src = try {
            packageManager.getApplicationInfo(p, 0).sourceDir
        } catch (t: Throwable) {
            ""
        }
        if (src.isNotEmpty()) {
            Bg.run(this, "AppDetail.hash") {
                val hash = ApkReputation.fileSha256(src)
                if (hash.isNotEmpty()) {
                    val withIoc = AppAnalyzer.analyze(this, info, emptyMap(), hash)
                    runOnUiThread {
                        if (isFinishing || isDestroyed) return@runOnUiThread
                        risk = withIoc
                        paint()
                    }
                }
            }
        }

        val reasons = Ui.card(this)
        reasons.addView(Ui.heading(this, "MOTIVOS DEL SCORE"))
        if (risk.reasons.isEmpty()) {
            reasons.addView(Ui.label(this, "Sin indicadores de riesgo relevantes.", Ui.GRN))
        } else {
            for (r in risk.reasons) reasons.addView(Ui.label(this, "· $r", Ui.T2, 12f))
        }
        root.addView(reasons)

        val perms = Ui.card(this)
        perms.addView(Ui.heading(this, "PERMISOS SENSIBLES DECLARADOS"))
        if (risk.dangerousPermissions.isEmpty()) {
            perms.addView(Ui.label(this, "Ninguno.", Ui.GRN))
        } else {
            for (d in risk.dangerousPermissions) {
                perms.addView(Ui.label(this, "· $d", Ui.AMB, 12f))
            }
        }
        root.addView(perms)

        // Reputación real del fichero instalado: hash del APK + firma.
        val rep = Ui.card(this)
        rep.addView(Ui.heading(this, "REPUTACIÓN DEL APK INSTALADO"))
        val outCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        var onlineConsent = Prefs.iocApiKey.isNotEmpty()
        val cb = CheckBox(this).apply {
            text = "Autorizar consulta online (VirusTotal, solo si hay clave)"
            isChecked = onlineConsent
            setTextColor(Ui.T2); textSize = 11.5f
            setOnCheckedChangeListener { _, v -> onlineConsent = v }
        }
        rep.addView(cb)
        rep.addView(Ui.button(this, "ANALIZAR HASH Y FIRMA") {
            outCol.removeAllViews()
            outCol.addView(Ui.label(this, "Calculando SHA-256 del APK…", Ui.T3, 11.5f))
            Bg.run(this, "ApkReputation") {
                val repInfo = ApkReputation.analyze(this, p, onlineConsent)
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    outCol.removeAllViews()
                    if (repInfo == null) {
                        outCol.addView(Ui.label(this, "No se pudo leer el paquete.", Ui.RED))
                        return@runOnUiThread
                    }
                    val risky = repInfo.verdict.contains("MALICIOSO") || repInfo.verdict.contains("SOSPECHOSO")
                    outCol.addView(Ui.chip(this, repInfo.verdict, if (risky) Ui.RED else Ui.GRN))
                    outCol.addView(Ui.label(this, "APK SHA-256:", Ui.T2, 11f))
                    outCol.addView(Ui.label(this, repInfo.apkSha256, Ui.T1, 10f))
                    outCol.addView(Ui.label(this, "Firma: " + repInfo.signerSubject.take(80), Ui.T2, 10.5f))
                    outCol.addView(Ui.label(this, repInfo.detail, Ui.T2, 11f))
                    outCol.addView(Ui.label(this, "Fuente: " + repInfo.source, Ui.T3, 10.5f))
                }
            }
        })
        rep.addView(outCol)
        root.addView(rep)

        val isBlocked = Prefs.blockedPackages().contains(p)
        val actions = Ui.card(this)
        actions.addView(Ui.heading(this, "ACCIONES"))
        actions.addView(
            Ui.row(
                this,
                Ui.button(this, if (isBlocked) "PERMITIR RED" else "BLOQUEAR RED") {
                    if (isBlocked) Prefs.unblockPackage(p) else Prefs.blockPackage(p)
                    com.cyberagent.app.core.FirewallBridge.requestRestart(this)
                    render()
                },
                Ui.button(this, "AISLAR (CUARENTENA)") {
                    QuarantineManager.quarantine(
                        this, p, "Aislamiento manual desde el detalle de la aplicación."
                    )
                    render()
                }
            )
        )
        actions.addView(Ui.button(this, "DESINSTALAR") {
            startActivity(QuarantineManager.uninstallIntent(p))
        })
        val trusted = Prefs.trustedPackages().contains(p)
        actions.addView(Ui.button(
            this,
            if (trusted) "QUITAR DE CONFIANZA" else "MARCAR DE CONFIANZA",
            false
        ) {
            if (trusted) Prefs.untrustPackage(p) else Prefs.trustPackage(p)
            render()
        })
        actions.addView(Ui.label(
            this,
            if (trusted) "App de confianza: mantiene red incluso en modo pánico."
            else "En modo pánico, solo las apps de confianza conservan internet.",
            Ui.T3, 10.5f
        ))

        // Whitelist por FIRMA: marcar al desarrollador (todas sus apps) de confianza.
        val signer = risk.signerSha256
        if (signer.isNotEmpty()) {
            val trustedSig = Prefs.isTrustedSigner(signer)
            actions.addView(Ui.button(
                this,
                if (trustedSig) "QUITAR FIRMA DE CONFIANZA" else "CONFIAR EN ESTA FIRMA",
                false
            ) {
                if (trustedSig) Prefs.untrustSigner(signer) else Prefs.trustSigner(signer)
                render()
            })
            actions.addView(Ui.label(
                this,
                if (trustedSig)
                    "Firma de confianza: cualquier app firmada por este desarrollador baja su riesgo."
                else
                    "Marca de confianza la FIRMA (no solo esta app) para reducir falsos positivos " +
                        "de futuras apps del mismo autor.",
                Ui.T3, 10.5f
            ))
        }
        root.addView(actions)
    }
}
