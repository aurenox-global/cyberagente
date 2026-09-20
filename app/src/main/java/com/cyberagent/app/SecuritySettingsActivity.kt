package com.cyberagent.app

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.appcompat.widget.AppCompatEditText as EditText
import android.widget.LinearLayout
import android.widget.Toast
import com.cyberagent.app.core.Db
import com.cyberagent.app.core.Bg
import com.cyberagent.app.core.Exporter
import com.cyberagent.app.core.HtmlReport
import com.cyberagent.app.core.IocDatabase
import com.cyberagent.app.core.IocUpdater
import com.cyberagent.app.core.NetworkStatsReader
import com.cyberagent.app.core.PanicModeManager
import com.cyberagent.app.core.Prefs
import com.cyberagent.app.core.ScheduleGuard
import com.cyberagent.app.service.OverlayGuardService
import com.cyberagent.app.ui.Ui

class SecuritySettingsActivity : SafeActivity() {

    private lateinit var root: LinearLayout
    private var backupPass = ""

    override fun build(savedInstanceState: android.os.Bundle?) {
        render()
    }

    private fun render() {
        root = Ui.screen(this, "AJUSTES DE SEGURIDAD",
            "Todo se guarda cifrado con AES-256 (clave en el Android Keystore).")

        val s = Ui.card(this)
        s.addView(Ui.heading(this, "SENSIBILIDAD DE ALERTAS"))
        s.addView(Ui.label(this, "Actual: ${Prefs.sensitivity}", Ui.CY, 12.5f))
        s.addView(Ui.row(this,
            Ui.button(this, "BAJA", Prefs.sensitivity == "baja") { Prefs.sensitivity = "baja"; render() },
            Ui.button(this, "MEDIA", Prefs.sensitivity == "media") { Prefs.sensitivity = "media"; render() },
            Ui.button(this, "ALTA", Prefs.sensitivity == "alta") { Prefs.sensitivity = "alta"; render() }
        ))
        root.addView(s)

        val t = Ui.card(this)
        t.addView(Ui.heading(this, "CONMUTADORES"))
        t.addView(Ui.button(this, "Filtro de llamadas: " + on(Prefs.callShieldEnabled), Prefs.callShieldEnabled) {
            Prefs.callShieldEnabled = !Prefs.callShieldEnabled; render()
        })
        t.addView(Ui.button(this, "Blindaje SMS: " + on(Prefs.smsShieldEnabled), Prefs.smsShieldEnabled) {
            Prefs.smsShieldEnabled = !Prefs.smsShieldEnabled; render()
        })
        t.addView(Ui.button(this, "Cortafuegos VPN: " + on(Prefs.firewallEnabled), Prefs.firewallEnabled) {
            if (Prefs.firewallEnabled) com.cyberagent.app.service.FirewallVpnService.stop(this)
            else {
                val prep = android.net.VpnService.prepare(this)
                if (prep != null) startActivityForResult(prep, 4701)
                else com.cyberagent.app.service.FirewallVpnService.start(this)
            }
            render()
        })
        t.addView(Ui.button(this, "Modo pánico: " + on(Prefs.panicMode), Prefs.panicMode) {
            if (Prefs.panicMode) PanicModeManager.disable(this) else PanicModeManager.enable(this)
            render()
        })
        t.addView(Ui.button(this, "No Molestar: " + on(Prefs.noDisturbEnabled), Prefs.noDisturbEnabled) {
            if (!Prefs.noDisturbEnabled) {
                startActivity(PanicModeManager.dndSettingsIntent())
            } else {
                PanicModeManager.setNoDisturb(this, false); render()
            }
        })
        root.addView(t)

        val auto = Ui.card(this)
        auto.addView(Ui.heading(this, "DEFENSA AUTOMÁTICA (0 CLICS)"))
        auto.addView(
            Ui.label(
                this,
                "Escaneo periódico en segundo plano con cuarentena automática. " + "No requiere abrir la app.",
                Ui.T2, 11.5f
            )
        )
        auto.addView(
            Ui.button(
                this,
                "Modo automático: " + on(Prefs.autoDefenseEnabled),
                Prefs.autoDefenseEnabled
            ) {
                Prefs.autoDefenseEnabled = !Prefs.autoDefenseEnabled
                if (Prefs.autoDefenseEnabled) com.cyberagent.app.core.AutoDefense.schedule(this)
                else com.cyberagent.app.core.AutoDefense.cancel(this)
                render()
            }
        )
        auto.addView(Ui.label(this, "Umbral de aislamiento automático: ${Prefs.autoQuarantineThreshold}/100", Ui.T2, 12f))
        auto.addView(
            Ui.row(
                this,
                Ui.button(this, "60", Prefs.autoQuarantineThreshold == 60) {
                    Prefs.autoQuarantineThreshold = 60; render()
                },
                Ui.button(this, "70", Prefs.autoQuarantineThreshold == 70) {
                    Prefs.autoQuarantineThreshold = 70; render()
                },
                Ui.button(this, "85", Prefs.autoQuarantineThreshold == 85) {
                    Prefs.autoQuarantineThreshold = 85; render()
                }
            )
        )
        auto.addView(Ui.label(this, "Intervalo de escaneo: cada ${Prefs.autoScanIntervalMin} min", Ui.T2, 12f))
        auto.addView(
            Ui.row(
                this,
                Ui.button(this, "15 min", Prefs.autoScanIntervalMin == 15) {
                    Prefs.autoScanIntervalMin = 15
                    com.cyberagent.app.core.AutoDefense.schedule(this)
                    render()
                },
                Ui.button(this, "60 min", Prefs.autoScanIntervalMin == 60) {
                    Prefs.autoScanIntervalMin = 60
                    com.cyberagent.app.core.AutoDefense.schedule(this)
                    render()
                },
                Ui.button(this, "6 h", Prefs.autoScanIntervalMin == 360) {
                    Prefs.autoScanIntervalMin = 360
                    com.cyberagent.app.core.AutoDefense.schedule(this)
                    render()
                }
            )
        )
        val lastScan = Prefs.lastAutoScan
        auto.addView(
            Ui.label(
                this,
                if (lastScan > 0) "Último escaneo: ${Ui.time(lastScan)}" else "Sin escaneos todavía.",
                Ui.T3, 11f
            )
        )
        root.addView(auto)

        val acc = Ui.card(this)
        acc.addView(Ui.heading(this, "ACCESOS DEL SISTEMA"))
        acc.addView(Ui.actionRow(
            this, "🔐  Asistente de permisos",
            "Recorre y concede todos los accesos paso a paso", Ui.AMB
        ) {
            startActivity(Intent(this, PermissionsActivity::class.java))
        })
        acc.addView(Ui.button(this, "Acceso al uso (estadísticas de red)") {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:$packageName")))
        })
        acc.addView(Ui.button(this, "Servicio de accesibilidad (guardia de instalación)") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        acc.addView(Ui.button(this, "Acceso a No Molestar") {
            startActivity(PanicModeManager.dndSettingsIntent())
        })
        acc.addView(Ui.button(this, "Permisos de la aplicación") {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
            )
        })
        root.addView(acc)

        val ai = Ui.card(this)
        ai.addView(Ui.heading(this, "ASISTENTE — MODO REMOTO (OPCIONAL)"))
        ai.addView(Ui.label(this, "Por defecto el asistente funciona en local, sin red. Si configuras un " +
            "endpoint compatible con OpenAI, cada consulta remota pedirá autorización explícita.",
            Ui.T2, 11.5f))

        val ep = EditText(this).apply {
            hint = "https://api.openai.com/v1/chat/completions"
            setText(Prefs.aiEndpoint); setTextColor(Ui.T1); setHintTextColor(Ui.T3); textSize = 12f
        }
        val model = EditText(this).apply {
            hint = "modelo"; setText(Prefs.aiModelName)
            setTextColor(Ui.T1); setHintTextColor(Ui.T3); textSize = 12f
        }
        val key = EditText(this).apply {
            hint = "clave de API (se guarda cifrada)"; setText(Prefs.aiApiKey)
            setTextColor(Ui.T1); setHintTextColor(Ui.T3); textSize = 12f
        }
        val iocKey = EditText(this).apply {
            hint = "clave de VirusTotal (opcional)"; setText(Prefs.iocApiKey)
            setTextColor(Ui.T1); setHintTextColor(Ui.T3); textSize = 12f
        }
        ai.addView(ep); ai.addView(model); ai.addView(key); ai.addView(iocKey)
        ai.addView(Ui.row(this,
            Ui.button(this, "GUARDAR (modo remoto)", true) {
                Prefs.aiEndpoint = ep.text.toString().trim()
                Prefs.aiModelName = model.text.toString().trim()
                Prefs.aiApiKey = key.text.toString().trim()
                Prefs.iocApiKey = iocKey.text.toString().trim()
                Prefs.aiMode = if (Prefs.aiEndpoint.isNotEmpty() && Prefs.aiApiKey.isNotEmpty()) "remoto" else "local"
                Db.addAlert(
                    com.cyberagent.app.core.SecurityAlert(
                        severity = "INFO", module = "Ajustes",
                        title = "Configuración del asistente actualizada",
                        detail = "Modo: ${Prefs.aiMode}. Endpoint: ${Prefs.aiEndpoint.ifEmpty { "—" }}"
                    )
                )
                render()
            },
            Ui.button(this, "VOLVER A MODO LOCAL") {
                Prefs.aiMode = "local"
                Prefs.aiEndpoint = ""
                Prefs.aiApiKey = ""
                render()
            }
        ))
        root.addView(ai)

        val data = Ui.card(this)
        data.addView(Ui.heading(this, "DATOS"))
        data.addView(Ui.label(this, "Eventos almacenados: ${Db.countAlerts()}", Ui.T2, 12f))
        data.addView(Ui.label(this, "Los títulos y detalles se cifran antes de guardarse. " +
            "No hay copia de seguridad en la nube (allowBackup=false).", Ui.T3, 11.5f))
        data.addView(Ui.button(this, "BORRAR HISTORIAL") {
            Db.purge(); render()
        })
        root.addView(data)

        val tools = Ui.card(this)
        tools.addView(Ui.heading(this, "PROTECCIÓN AMPLIADA Y HERRAMIENTAS"))
        tools.addView(Ui.button(
            this,
            "Guardián de enlaces (notificaciones): " + on(Prefs.notifGuardEnabled)
        ) {
            if (!Prefs.notifGuardEnabled &&
                !com.cyberagent.app.service.NotifGuardService.isConnected(this)
            ) {
                Toast.makeText(
                    this, "Concede el acceso a notificaciones y vuelve", Toast.LENGTH_LONG
                ).show()
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
            Prefs.notifGuardEnabled = !Prefs.notifGuardEnabled
            render()
        })
        tools.addView(Ui.label(
            this,
            "Revisa enlaces de avisos de cualquier app contra la lista local de dominios. " +
                "No guarda el texto de los mensajes.",
            Ui.T3, 10.5f
        ))
        tools.addView(Ui.button(this, "Consultas RDAP (fecha del dominio): " + on(Prefs.rdapEnabled)) {
            Prefs.rdapEnabled = !Prefs.rdapEnabled
            render()
        })
        tools.addView(Ui.button(
            this, "Rechazar llamadas NO verificadas: " + on(Prefs.strictVerifiedCalls)
        ) {
            Prefs.strictVerifiedCalls = !Prefs.strictVerifiedCalls
            render()
        })
        tools.addView(Ui.button(this, "Batería sin restricciones (defensa automática)") {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        })
        tools.addView(Ui.button(this, "Autodiagnóstico y comprobador de enlaces") {
            startActivity(Intent(this, SelfTestActivity::class.java))
        })
        val pass = EditText(this).apply {
            hint = "contraseña (6+ caracteres) para la copia cifrada"
            setTextColor(Ui.T1); setHintTextColor(Ui.T3); textSize = 12f
        }

        // ── Base de dominios actualizable ────────────────────────────
        tools.addView(Ui.heading(this, "BASE DE DOMINIOS (ACTUALIZABLE)"))
        tools.addView(Ui.label(
            this,
            "En uso: ${IocDatabase.size()} entradas · fábrica ${IocDatabase.factorySize()} " +
                "· local ${IocDatabase.localSize()}" +
                (if (IocDatabase.localSize() > 0) " (v${IocDatabase.feedVersion})" else ""),
            Ui.T2, 11f
        ))
        val feedUrl = EditText(this).apply {
            hint = "https://…/ioc_db.json"
            setText(Prefs.iocFeedUrl)
            setTextColor(Ui.T1); setHintTextColor(Ui.T3); textSize = 12f
        }
        val feedSha = EditText(this).apply {
            hint = "huella SHA-256 esperada (recomendado)"
            setText(Prefs.iocFeedSha256)
            setTextColor(Ui.T1); setHintTextColor(Ui.T3); textSize = 12f
        }
        tools.addView(feedUrl)
        tools.addView(feedSha)
        tools.addView(Ui.row(this,
            Ui.button(this, "GUARDAR URL") {
                Prefs.iocFeedUrl = feedUrl.text.toString().trim()
                Prefs.iocFeedSha256 = feedSha.text.toString().trim()
                Toast.makeText(this, "Guardado", Toast.LENGTH_SHORT).show()
                render()
            },
            Ui.button(this, "ACTUALIZAR", true) {
                Prefs.iocFeedUrl = feedUrl.text.toString().trim()
                Prefs.iocFeedSha256 = feedSha.text.toString().trim()
                Toast.makeText(this, "Descargando…", Toast.LENGTH_SHORT).show()
                Bg.run(this, "IocUpdater") {
                    val r = IocUpdater.update(this)
                    runOnUiThread {
                        Toast.makeText(this, r.message, Toast.LENGTH_LONG).show()
                        render()
                    }
                }
            },
            Ui.button(this, "VOLVER A FÁBRICA") {
                IocUpdater.reset(this); render()
            }
        ))
        tools.addView(Ui.label(
            this,
            "Sin huella fijada la lista se acepta igualmente; con huella, si no coincide se descarta. " +
                "Nunca se descarga nada sin pulsar el botón.",
            Ui.T3, 10.5f
        ))

        // ── Bloqueo por horario ──────────────────────────────────────
        tools.addView(Ui.heading(this, "BLOQUEO POR HORARIO (PERFIL NOCHE)"))
        tools.addView(Ui.label(
            this,
            ScheduleGuard.describe() + " · apps de confianza: ${Prefs.trustedPackages().size} " +
                "(solo ellas conservan red dentro de la franja)",
            Ui.T2, 11.5f
        ))
        val fromEt = EditText(this).apply {
            hint = "23:30"; setText(Prefs.scheduleFrom)
            setTextColor(Ui.T1); setHintTextColor(Ui.T3); textSize = 12f
        }
        val toEt = EditText(this).apply {
            hint = "07:00"; setText(Prefs.scheduleTo)
            setTextColor(Ui.T1); setHintTextColor(Ui.T3); textSize = 12f
        }
        tools.addView(Ui.row(this, fromEt, toEt))
        tools.addView(Ui.row(this,
            Ui.button(this, "Horario: " + on(Prefs.scheduleEnabled)) {
                Prefs.scheduleFrom = fromEt.text.toString().trim().ifEmpty { "23:30" }
                Prefs.scheduleTo = toEt.text.toString().trim().ifEmpty { "07:00" }
                Prefs.scheduleEnabled = !Prefs.scheduleEnabled
                ScheduleGuard.applyIfNeeded(this)
                render()
            },
            Ui.button(this, "APLICAR AHORA") {
                Prefs.scheduleFrom = fromEt.text.toString().trim().ifEmpty { "23:30" }
                Prefs.scheduleTo = toEt.text.toString().trim().ifEmpty { "07:00" }
                ScheduleGuard.applyIfNeeded(this)
                render()
            }
        ))

        // ── Anti-superposición ───────────────────────────────────────
        tools.addView(Ui.heading(this, "ANTI-SUPERPOSICIÓN (PANTALLA FALSA)"))
        tools.addView(Ui.button(
            this, "Guardián de superposiciones: " + on(Prefs.overlayGuardEnabled)
        ) {
            Prefs.overlayGuardEnabled = !Prefs.overlayGuardEnabled
            if (Prefs.overlayGuardEnabled && !OverlayGuardService.isEnabled(this)) {
                Toast.makeText(
                    this,
                    "Actívalo en Accesibilidad → CyberAgent (pantalla falsa)",
                    Toast.LENGTH_LONG
                ).show()
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            render()
        })
        tools.addView(Ui.label(
            this,
            "Avisa si una app dibuja una ventana encima de otra (posible pantalla falsa de banco). " +
                "Requiere accesibilidad con lectura de ventanas y puede avisar también de burbujas " +
                "de chat o ventanas flotantes legítimas.",
            Ui.T3, 10.5f
        ))
        tools.addView(Ui.button(this, "INFORME HTML (FORENSE)") { exportHtml() })

        tools.addView(pass)
        tools.addView(Ui.row(this,
            Ui.button(this, "EXPORTAR INFORME") { exportReport() },
            Ui.button(this, "COPIA CIFRADA") {
                backupPass = pass.text.toString()
                if (backupPass.length < 6) {
                    Toast.makeText(this, "Usa 6 caracteres o más", Toast.LENGTH_LONG).show()
                } else {
                    startActivityForResult(
                        Intent(Intent.ACTION_CREATE_DOCUMENT).setType("text/plain")
                            .putExtra(Intent.EXTRA_TITLE, "cyberagent-copia.txt"),
                        REQ_BACKUP
                    )
                }
            },
            Ui.button(this, "RESTAURAR") {
                backupPass = pass.text.toString()
                if (backupPass.isEmpty()) {
                    Toast.makeText(this, "Escribe la contraseña de la copia", Toast.LENGTH_LONG).show()
                } else {
                    startActivityForResult(
                        Intent(Intent.ACTION_OPEN_DOCUMENT).setType("text/plain")
                            .addCategory(Intent.CATEGORY_OPENABLE),
                        REQ_RESTORE
                    )
                }
            }
        ))
        root.addView(tools)

        val about = Ui.card(this)
        about.addView(Ui.heading(this, "ACERCA DE"))
        about.addView(Ui.label(this, getString(R.string.about_author), Ui.CY, 13.5f))
        about.addView(Ui.label(this, "CyberAgent ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})\n" +
            "Sin telemetría · sin analíticas · sin SDK de terceros\n" +
            "Consultas de red solo por HTTPS y solo si las autorizas", Ui.T2, 11.5f))
        about.addView(Ui.statusDot(this, NetworkStatsReader.hasUsageAccess(this), "Acceso al uso"))
        about.addView(Ui.label(this, getString(R.string.about_author_full), Ui.T3, 10.5f))
        root.addView(about)
    }

    private fun on(b: Boolean) = if (b) "ON" else "OFF"

    private fun exportReport() {
        try {
            startActivityForResult(
                Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/json")
                    .putExtra(Intent.EXTRA_TITLE, "cyberagent-informe.json"),
                REQ_REPORT
            )
        } catch (t: Throwable) {
            Toast.makeText(this, "No hay app para guardar ficheros", Toast.LENGTH_LONG).show()
        }
    }

    private fun exportHtml() {
        try {
            startActivityForResult(
                Intent(Intent.ACTION_CREATE_DOCUMENT).setType("text/html")
                    .putExtra(Intent.EXTRA_TITLE, "cyberagent-informe.html"),
                REQ_HTML
            )
        } catch (t: Throwable) {
            Toast.makeText(this, "No hay app para guardar ficheros", Toast.LENGTH_LONG).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 4701 && resultCode == RESULT_OK) {
            com.cyberagent.app.service.FirewallVpnService.start(this)
            render()
            return
        }
        val uri = data?.data
        if (resultCode == RESULT_OK && uri != null) {
            try {
                when (requestCode) {
                    REQ_REPORT -> {
                        val res = contentResolver.openOutputStream(uri)?.let {
                            Exporter.writeReport(this, it)
                        }
                        Toast.makeText(
                            this,
                            if (res != null) "Informe guardado (${res.first} eventos, ${Ui.bytes(res.second)})"
                            else "No se pudo escribir el informe",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    REQ_BACKUP -> {
                        val text = Exporter.backupEncrypted(this, backupPass)
                        contentResolver.openOutputStream(uri)?.use {
                            it.write(text.toByteArray(Charsets.UTF_8))
                        }
                        Toast.makeText(this, "Copia cifrada guardada", Toast.LENGTH_LONG).show()
                    }
                    REQ_RESTORE -> {
                        val text = contentResolver.openInputStream(uri)
                            ?.bufferedReader()?.use { it.readText() } ?: ""
                        val n = Exporter.restoreEncrypted(text, backupPass)
                        Toast.makeText(
                            this,
                            if (n >= 0) "Restaurados $n eventos" else "Copia inválida o contraseña incorrecta",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    REQ_HTML -> {
                        val html = HtmlReport.build(this)
                        contentResolver.openOutputStream(uri)?.use {
                            it.write(html.toByteArray(Charsets.UTF_8))
                        }
                        Toast.makeText(
                            this,
                            "Informe HTML guardado (${Ui.bytes(html.length.toLong())})",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (t: Throwable) {
                Toast.makeText(this, "Error: " + t.javaClass.simpleName, Toast.LENGTH_LONG).show()
            }
        }
        render()
    }

    companion object {
        private const val REQ_REPORT = 4711
        private const val REQ_BACKUP = 4712
        private const val REQ_RESTORE = 4713
        private const val REQ_HTML = 4714
    }
}
