package com.cyberagent.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.LinearLayout
import com.cyberagent.app.core.AutoDefense
import com.cyberagent.app.core.Bg
import com.cyberagent.app.core.CrashLog
import com.cyberagent.app.core.Db
import com.cyberagent.app.core.IocDatabase
import com.cyberagent.app.core.Notify
import com.cyberagent.app.core.PanicModeManager
import com.cyberagent.app.core.Perms
import com.cyberagent.app.core.Prefs
import com.cyberagent.app.core.Watchdog
import com.cyberagent.app.receiver.SmsDeliverReceiver
import com.cyberagent.app.service.CallGuardService
import com.cyberagent.app.service.FirewallVpnService
import com.cyberagent.app.service.SecurityMonitorService
import com.cyberagent.app.ui.Ui

/**
 * Panel principal.
 *
 * Rediseñado para que la información se entienda de un vistazo:
 *  1) estado general (héroe), 2) accesos pendientes, 3) módulos en mosaicos,
 *  4) acciones rápidas, 5) defensa automática y 6) últimos eventos.
 * Todo lo que tarda (historial) se carga en segundo plano: la pantalla ya no
 * se bloquea al abrirla ni al volver de otra sección.
 */
class MainActivity : SafeActivity() {

    private lateinit var root: LinearLayout

    override fun build(savedInstanceState: Bundle?) {
        Prefs.init(this)
        Db.init(this)
        IocDatabase.load(this)
    }

    override fun onResume() {
        super.onResume()
        renderSafe()
    }

    private fun renderSafe() {
        try {
            render()
        } catch (t: Throwable) {
            CrashLog.record(this, "MainActivity.render", t)
            try {
                render()
            } catch (t2: Throwable) {
                // la base SafeActivity mostrará el error
            }
        }
    }

    private fun render() {
        root = Ui.screen(
            this,
            "PANEL DE SEGURIDAD",
            "Estado real del dispositivo · ningún dato es simulado."
        )

        crashCard()
        permissionsCard()
        heroCard()
        modulesGrid()
        actionsCard()
        autoDefenseCard()
        alertsCard()
        footer()

        // Vigilante: avisa si la protección dejó de funcionar de verdad
        // (otra VPN ocupando el túnel, roles revocados, batería optimizada).
        Bg.run(this, "Watchdog") { Watchdog.checkAndAlert(this) }

        maybeOpenWizard()
    }

    // ── Diagnóstico ──────────────────────────────────────────────────

    private fun crashCard() {
        val crash = CrashLog.read(this)
        if (crash.isEmpty()) return
        val c = Ui.section(this, "FALLO INTERNO REGISTRADO", Ui.RED)
        c.addView(Ui.label(this, crash.take(1500), Ui.T2, 10.5f))
        c.addView(Ui.row(
            this,
            Ui.button(this, "BORRAR DIAGNÓSTICO") { CrashLog.clear(this); renderSafe() }
        ))
        root.addView(c)
    }

    // ── Accesos / permisos ───────────────────────────────────────────

    private fun permissionsCard() {
        val total = 7
        var done = 0
        if (Perms.notificationsAllowed(this)) done++
        if (Perms.missing(this, Perms.critical()).isEmpty()) done++
        if (Perms.usageAccess(this)) done++
        if (CallGuardService.hasRole(this)) done++
        if (SmsDeliverReceiver.isDefaultSmsApp(this)) done++
        if (Perms.accessibilityEnabled(this)) done++
        if (Prefs.firewallEnabled) done++

        val accent = if (done == total) Ui.GRN else Ui.AMB
        val c = Ui.section(
            this,
            if (done == total) "ACCESOS · TODO CONCEDIDO" else "ACCESOS · $done/$total CONCEDIDOS",
            accent
        )
        c.addView(Ui.bar(this, done, total, accent))
        c.addView(Ui.label(
            this,
            if (done == total)
                "SMS, llamadas, uso de red, notificaciones y VPN están operativos."
            else
                "Toca el asistente y concede TODO en un solo recorrido, sin buscar ajustes a mano.",
            Ui.T2, 11.5f
        ))
        c.addView(Ui.actionRow(
            this,
            "🔐  Asistente de permisos",
            "Los recorre uno a uno y vuelve solo al siguiente paso",
            accent
        ) { startActivity(Intent(this, PermissionsActivity::class.java)) })
        if (!Perms.usageAccess(this)) {
            c.addView(Ui.actionRow(
                this, "📊  Acceso al uso", "Sin él no hay consumo por aplicación", Ui.AMB
            ) {
                startActivity(
                    Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:$packageName"))
                )
            })
        }
        root.addView(c)
    }

    // ── Estado general ───────────────────────────────────────────────

    private fun heroCard() {
        val since = System.currentTimeMillis() - 86_400_000L
        val alerts24 = Db.countAlerts(since)
        val high = Db.countHighAlerts(since)
        val safe = high == 0
        val accent = if (safe) Ui.GRN else Ui.RED

        val hero = Ui.hero(this, accent)
        hero.addView(Ui.label(
            this,
            if (safe) "● SISTEMA SEGURO" else "● $high ALERTA(S) DE ALTA SEVERIDAD",
            accent, 17f
        ))
        hero.addView(Ui.label(
            this,
            if (Prefs.panicMode) "Modo pánico ACTIVO · monitor de fondo en marcha"
            else "Monitor de fondo " + if (Prefs.panicMode) "" else "activo",
            Ui.T2, 12f
        ))
        hero.addView(Ui.row(
            this,
            Ui.chip(this, "$alerts24 eventos / 24 h", Ui.CY),
            Ui.chip(this, "${Prefs.blockedPackages().size} apps sin red", Ui.BLU),
            Ui.chip(this, "${Prefs.quarantine().size} en cuarentena", Ui.AMB)
        ))
        root.addView(hero)
    }

    // ── Módulos ──────────────────────────────────────────────────────

    private fun modulesGrid() {
        val c = Ui.section(this, "MÓDULOS", Ui.CY, "Pulsa un mosaico para entrar.")
        c.addView(Ui.tileGrid(
            this,
            listOf(
                Ui.TileItem("📱", "Aplicaciones", "Permisos y riesgo por app", Ui.CY) {
                    startActivity(Intent(this, AppListActivity::class.java))
                },
                Ui.TileItem("📞", "Llamadas y SMS", "Lista negra y blindaje", Ui.CY) {
                    startActivity(Intent(this, CallSmsShieldActivity::class.java))
                },
                Ui.TileItem("🌐", "Uso de Internet", "Consumo por aplicación", Ui.CY) {
                    startActivity(Intent(this, InternetUsageActivity::class.java))
                },
                Ui.TileItem("🦠", "Cuarentena", "Apps aisladas y desinstalación", Ui.CY) {
                    startActivity(Intent(this, QuarantineCenterActivity::class.java))
                },
                Ui.TileItem("🔬", "Forense", "SHA-256 e IoC", Ui.CY) {
                    startActivity(Intent(this, ForensicVerifierActivity::class.java))
                },
                Ui.TileItem("📶", "Wi-Fi", "Cifrado y señales raras", Ui.CY) {
                    startActivity(Intent(this, WifiSecurityAnalysisActivity::class.java))
                },
                Ui.TileItem("📊", "Métricas", "Resumen y widgets", Ui.CY) {
                    startActivity(Intent(this, MetricsSummaryActivity::class.java))
                },
                Ui.TileItem("🤖", "Asistente IA", "Análisis defensivo local", Ui.CY) {
                    startActivity(Intent(this, AssistantActivity::class.java))
                },
                Ui.TileItem("⚠️", "Eventos", "Historial completo", Ui.CY) {
                    startActivity(Intent(this, AlertsActivity::class.java))
                },
                Ui.TileItem("🧪", "Herramientas", "Autodiagnóstico y enlaces", Ui.CY) {
                    startActivity(Intent(this, SelfTestActivity::class.java))
                },
                Ui.TileItem("🧠", "Confianza", "Recomendaciones por app", Ui.CY) {
                    startActivity(Intent(this, TrustAdvisorActivity::class.java))
                },
                Ui.TileItem("✈️", "Comprobar enlace", "Anti-phishing manual", Ui.CY) {
                    startActivity(Intent(this, ShareInspectActivity::class.java))
                },
                Ui.TileItem("⚙️", "Ajustes", "Sensibilidad y datos", Ui.CY) {
                    startActivity(Intent(this, SecuritySettingsActivity::class.java))
                }
            )
        ))
        root.addView(c)
    }

    // ── Acciones rápidas ─────────────────────────────────────────────

    private fun actionsCard() {
        val c = Ui.section(this, "ACCIONES RÁPIDAS", Ui.BLU)
        c.addView(Ui.actionRow(
            this,
            if (Prefs.panicMode) "🔴  Desactivar modo pánico" else "🔴  Activar modo pánico",
            if (Prefs.panicMode) "Vuelve al estado normal"
            else if (Prefs.trustedPackages().isEmpty())
                "⚠ Cortará la red de TODAS las apps (no hay apps de confianza)"
            else "Corta la red de todo menos ${Prefs.trustedPackages().size} app(s) de confianza",
            if (Prefs.panicMode) Ui.GRN else Ui.RED
        ) {
            if (Prefs.panicMode) PanicModeManager.disable(this) else PanicModeManager.enable(this)
            renderSafe()
        })
        c.addView(Ui.actionRow(
            this,
            if (Prefs.firewallEnabled) "🚧  Parar cortafuegos VPN" else "🚧  Activar cortafuegos VPN",
            "Bloqueo de red por aplicación, en el propio móvil",
            Ui.CY
        ) {
            if (Prefs.firewallEnabled) {
                FirewallVpnService.stop(this)
                renderSafe()
            } else {
                val prep = android.net.VpnService.prepare(this)
                if (prep != null) startActivityForResult(prep, REQ_VPN) else {
                    FirewallVpnService.start(this)
                    renderSafe()
                }
            }
        })
        c.addView(Ui.actionRow(
            this, "🛰️  Monitor de seguridad",
            if (Prefs.panicMode) "En marcha" else "Servicio en primer plano con aviso permanente",
            Ui.CY
        ) {
            SecurityMonitorService.start(this)
            renderSafe()
        })
        c.addView(Ui.actionRow(
            this, "🧪  Escanear ahora", "Análisis inmediato + cuarentena automática", Ui.CY
        ) {
            Bg.run(this, "scanManual") {
                val res = AutoDefense.runOnce(this, notify = true)
                runOnUiThread {
                    Notify.post(this, "Escaneo manual", res)
                    renderSafe()
                }
            }
        })
        root.addView(c)
    }

    // ── Defensa automática ───────────────────────────────────────────

    private fun autoDefenseCard() {
        val autoOn = Prefs.autoDefenseEnabled
        val c = Ui.section(this, "DEFENSA AUTOMÁTICA · 0 CLICS", if (autoOn) Ui.GRN else Ui.AMB)
        c.addView(Ui.label(
            this,
            if (autoOn)
                "ACTIVA · escaneo cada ${Prefs.autoScanIntervalMin} min · cuarentena a partir de " +
                    "${Prefs.autoQuarantineThreshold}/100"
            else "DESACTIVADA · solo análisis manual",
            if (autoOn) Ui.GRN else Ui.AMB, 12.5f
        ))
        val last = Prefs.lastAutoScan
        c.addView(Ui.label(
            this,
            if (last > 0) "Último escaneo: ${Ui.time(last)}" else "Aún no se ha ejecutado ningún escaneo.",
            Ui.T2, 11.5f
        ))
        c.addView(Ui.label(
            this,
            "Nunca actúa sobre apps de sistema, el launcher, el teléfono ni el SMS.",
            Ui.T3, 11f
        ))
        c.addView(Ui.row(
            this,
            Ui.button(this, if (autoOn) "DESACTIVAR" else "ACTIVAR", !autoOn) {
                Prefs.autoDefenseEnabled = !autoOn
                if (Prefs.autoDefenseEnabled) AutoDefense.schedule(this) else AutoDefense.cancel(this)
                renderSafe()
            },
            Ui.button(this, "AJUSTES") {
                startActivity(Intent(this, SecuritySettingsActivity::class.java))
            }
        ))
        root.addView(c)
    }

    // ── Eventos recientes (segundo plano) ────────────────────────────

    private fun alertsCard() {
        val c = Ui.section(this, "ÚLTIMOS EVENTOS", Ui.AMB)
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(Ui.label(this, "Cargando…", Ui.T3))
        c.addView(body)
        c.addView(Ui.actionRow(
            this, "⚠️  Ver todo el registro", "Historial completo con filtros", Ui.AMB
        ) { startActivity(Intent(this, AlertsActivity::class.java)) })
        root.addView(c)

        Bg.run(this, "MainActivity.alerts") {
            val recent = Db.alerts(limit = 12)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                body.removeAllViews()
                if (recent.isEmpty()) {
                    body.addView(Ui.label(this, "Sin eventos registrados todavía.", Ui.T3))
                    return@runOnUiThread
                }
                for (a in recent) {
                    val sev = Ui.severityColor(a.severity)
                    val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                    top.addView(Ui.chip(this, a.severity, sev))
                    top.addView(Ui.label(this, Ui.time(a.ts) + " · " + a.module, Ui.T2, 11f))
                    body.addView(top)
                    body.addView(Ui.label(this, a.title, Ui.T1, 12.5f))
                    if (a.detail.isNotEmpty()) {
                        body.addView(Ui.label(this, a.detail.take(240), Ui.T3, 11f))
                    }
                    body.addView(Ui.divider(this))
                }
            }
        }
    }

    private fun footer() {
        val f = Ui.card(this)
        f.addView(Ui.label(
            this, "CyberAgent ${BuildConfig.VERSION_NAME} · sin telemetría · sin SDK de terceros",
            Ui.T3, 10.5f
        ))
        f.addView(Ui.label(
            this, "Base de IoC local v${IocDatabase.version} · ${IocDatabase.size()} entradas",
            Ui.T3, 10.5f
        ))
        root.addView(f)
    }

    // ── Primer arranque: recorrido de accesos ────────────────────────

    private fun maybeOpenWizard() {
        if (Prefs.firstRunDone) return
        Prefs.firstRunDone = true
        if (Perms.missing(this, Perms.critical()).isNotEmpty()) {
            startActivity(Intent(this, PermissionsActivity::class.java))
        }
    }

    private fun isAccessibilityOn(): Boolean = Perms.accessibilityEnabled(this)

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_VPN && resultCode == RESULT_OK) {
            FirewallVpnService.start(this)
        }
        renderSafe()
    }

    companion object {
        private const val REQ_VPN = 4202
    }
}
