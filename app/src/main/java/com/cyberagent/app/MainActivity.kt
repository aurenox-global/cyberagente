package com.cyberagent.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
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
 * Panel principal (v3 — «soft dark + menta»).
 *
 * Rediseñado para que la información se entienda de un vistazo, siguiendo el
 * lenguaje visual de la referencia: cabecera con avatar/accesos, métrica
 * destacada, rejilla de estado, módulos en tarjetas redondeadas, acciones
 * rápidas, defensa automática, últimos eventos y una barra de navegación
 * flotante. Todo lo que tarda (historial) se carga en segundo plano.
 */
class MainActivity : SafeActivity() {

    private lateinit var root: LinearLayout
    private lateinit var header: LinearLayout

    /** Pestaña activa del panel (0=Inicio · 1=Módulos · 2=Acciones · 3=Eventos). */
    private var tab = 0

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
        // Cabecera y pestañas FIJAS; solo scrollea el contenido entre las
        // pestañas y el dock.
        val screen = Ui.tabbedScreen(
            this,
            "CyberAgent",
            "Consola de ciberdefensa · 100% local · sin telemetría",
            TAB_TITLES,
            tab,
            onSelect = { i -> if (i != tab) { tab = i; renderSafe() } },
            nav = navItems()
        )
        header = screen.headerCol
        root = screen.contentCol

        crashCard()
        quickHeader(header)

        when (tab) {
            0 -> {
                heroCard()
                statsGrid()
                permissionsCard()
                footer()
            }
            1 -> modulesGrid()
            2 -> {
                actionsCard()
                autoDefenseCard()
            }
            else -> alertsCard()
        }

        // Vigilante: avisa si la protección dejó de funcionar de verdad
        // (otra VPN ocupando el túnel, roles revocados, batería optimizada).
        Bg.run(this, "Watchdog") { Watchdog.checkAndAlert(this) }

        maybeOpenWizard()
    }

    // ── Barra de navegación flotante ─────────────────────────────────

    private fun navItems(): List<Ui.NavItem> = listOf(
        Ui.NavItem("home", "Inicio", active = tab == 0) {
            if (tab != 0) { tab = 0; renderSafe() }
        },
        Ui.NavItem("scan", "Escanear") { runScan() },
        Ui.NavItem("bot", "Asistente") {
            startActivity(Intent(this, AssistantActivity::class.java))
        },
        Ui.NavItem("settings", "Ajustes") {
            startActivity(Intent(this, SecuritySettingsActivity::class.java))
        }
    )

    // ── Cabecera con estado y avisos ─────────────────────────────────

    private fun quickHeader(target: LinearLayout) {
        val since = System.currentTimeMillis() - 86_400_000L
        val high = Db.countHighAlerts(since)
        val ok = high == 0
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(Ui.iconBadge(this, if (ok) "check" else "warning", if (ok) Ui.GRN else Ui.RED, 44))

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(this@MainActivity, 12), 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        col.addView(Ui.label(
            this,
            if (ok) "Sistema protegido" else "$high alerta(s) de alta severidad",
            if (ok) Ui.T1 else Ui.RED, 15f
        ))
        col.addView(Ui.label(
            this,
            "Monitor " + if (Prefs.panicMode) "en modo pánico" else "activo",
            Ui.T3, 11.5f
        ))
        row.addView(col)

        // Botón circular de avisos (campana), con punto si hay alertas altas.
        val bell = android.widget.FrameLayout(this).apply {
            background = Ui.rounded(this@MainActivity, Ui.SURF2, Ui.LINE, 22)
            layoutParams = LinearLayout.LayoutParams(
                Ui.dp(this@MainActivity, 44), Ui.dp(this@MainActivity, 44)
            )
            isClickable = true
            setOnClickListener { startActivity(Intent(this@MainActivity, AlertsActivity::class.java)) }
        }
        val bellIv = Ui.csIcon(this, "bell", if (ok) Ui.T2 else Ui.RED, 21, 1.9f)
        bell.addView(bellIv, android.widget.FrameLayout.LayoutParams(
            Ui.dp(this, 21), Ui.dp(this, 21)
        ).apply { gravity = Gravity.CENTER })
        row.addView(bell)
        target.addView(row)
    }

    // ── Diagnóstico ──────────────────────────────────────────────────

    private fun crashCard() {
        val crash = CrashLog.read(this)
        if (crash.isEmpty()) return
        val c = Ui.section(this, "Fallo interno registrado", Ui.RED)
        c.addView(Ui.label(this, crash.take(1500), Ui.T2, 10.5f))
        c.addView(Ui.row(
            this,
            Ui.button(this, "Borrar diagnóstico") { CrashLog.clear(this); renderSafe() }
        ))
        root.addView(c)
    }

    // ── Estado general (métrica destacada) ───────────────────────────

    private fun heroCard() {
        val since = System.currentTimeMillis() - 86_400_000L
        val alerts24 = Db.countAlerts(since)
        val high = Db.countHighAlerts(since)
        val safe = high == 0
        val accent = if (safe) Ui.GRN else Ui.RED

        val hero = Ui.metricCard(
            this,
            if (safe) "shield" else "warning",
            alerts24.toString(),
            if (alerts24 == 1) "evento · 24 h" else "eventos · 24 h",
            if (safe) "Sin incidentes graves en las últimas 24 h"
            else "$high alerta(s) de alta severidad · revisa el registro",
            accent
        )
        hero.addView(Ui.row(
            this,
            Ui.chip(this, "${Prefs.blockedPackages().size} apps sin red", Ui.BLU),
            Ui.chip(this, "${Prefs.quarantine().size} en cuarentena", Ui.AMB)
        ))
        root.addView(hero)
    }

    // ── Rejilla de estado (tarjetas con número grande) ───────────────

    private fun statsGrid() {
        val c = Ui.section(this, "Estado rápido", Ui.CY, "Pulsa para ver el detalle.")
        val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val r1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        r1.addView(Ui.statTile(
            this, Prefs.lastAppsAnalyzed.toString(), "apps", "analizadas",
            Ui.CY
        ) { startActivity(Intent(this, AppListActivity::class.java)) })
        r1.addView(Ui.statTile(
            this, Prefs.quarantine().size.toString(), "apps", "en cuarentena",
            Ui.AMB
        ) { startActivity(Intent(this, QuarantineCenterActivity::class.java)) })
        grid.addView(r1)

        val r2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        r2.addView(Ui.statTile(
            this, Prefs.blockedPackages().size.toString(), "apps", "sin acceso a red",
            Ui.BLU
        ) { startActivity(Intent(this, AppListActivity::class.java)) })
        r2.addView(Ui.statTile(
            this, Prefs.scanCount.toString(), "escaneos", "en total",
            Ui.VIO
        ) { startActivity(Intent(this, MetricsSummaryActivity::class.java)) })
        grid.addView(r2)

        c.addView(grid)
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
            if (done == total) "Accesos · todo concedido" else "Accesos · $done/$total",
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
        c.addView(Ui.actionRowIcon(
            this,
            "lock",
            "Asistente de permisos",
            "Los recorre uno a uno y vuelve solo al siguiente paso",
            accent
        ) { startActivity(Intent(this, PermissionsActivity::class.java)) })
        if (!Perms.usageAccess(this)) {
            c.addView(Ui.actionRowIcon(
                this, "chart", "Acceso al uso", "Sin él no hay consumo por aplicación", Ui.AMB
            ) {
                startActivity(
                    Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:$packageName"))
                )
            })
        }
        root.addView(c)
    }

    // ── Módulos ──────────────────────────────────────────────────────

    private fun modulesGrid() {
        val c = Ui.section(this, "Módulos", Ui.CY, "Pulsa una tarjeta para entrar.")
        c.addView(Ui.tileGrid(
            this,
            listOf(
                Ui.TileItem("apps", "Aplicaciones", "Permisos y riesgo por app", Ui.CY) {
                    startActivity(Intent(this, AppListActivity::class.java))
                },
                Ui.TileItem("phone", "Llamadas y SMS", "Lista negra y blindaje", Ui.CY) {
                    startActivity(Intent(this, CallSmsShieldActivity::class.java))
                },
                Ui.TileItem("globe", "Uso de Internet", "Consumo por aplicación", Ui.CY) {
                    startActivity(Intent(this, InternetUsageActivity::class.java))
                },
                Ui.TileItem("bug", "Cuarentena", "Apps aisladas y desinstalación", Ui.CY) {
                    startActivity(Intent(this, QuarantineCenterActivity::class.java))
                },
                Ui.TileItem("flask", "Forense", "SHA-256 e IoC", Ui.CY) {
                    startActivity(Intent(this, ForensicVerifierActivity::class.java))
                },
                Ui.TileItem("wifi", "Wi-Fi", "Cifrado y señales raras", Ui.CY) {
                    startActivity(Intent(this, WifiSecurityAnalysisActivity::class.java))
                },
                Ui.TileItem("chart", "Métricas", "Resumen y widgets", Ui.CY) {
                    startActivity(Intent(this, MetricsSummaryActivity::class.java))
                },
                Ui.TileItem("bot", "Asistente IA", "Análisis defensivo local", Ui.CY) {
                    startActivity(Intent(this, AssistantActivity::class.java))
                },
                Ui.TileItem("warning", "Eventos", "Historial completo", Ui.CY) {
                    startActivity(Intent(this, AlertsActivity::class.java))
                },
                Ui.TileItem("tools", "Herramientas", "Autodiagnóstico y enlaces", Ui.CY) {
                    startActivity(Intent(this, SelfTestActivity::class.java))
                },
                Ui.TileItem("star", "Confianza", "Recomendaciones por app", Ui.CY) {
                    startActivity(Intent(this, TrustAdvisorActivity::class.java))
                },
                Ui.TileItem("share", "Comprobar enlace", "Anti-phishing manual", Ui.CY) {
                    startActivity(Intent(this, ShareInspectActivity::class.java))
                },
                Ui.TileItem("settings", "Ajustes", "Sensibilidad y datos", Ui.CY) {
                    startActivity(Intent(this, SecuritySettingsActivity::class.java))
                }
            )
        ))
        root.addView(c)
    }

    // ── Acciones rápidas ─────────────────────────────────────────────

    private fun actionsCard() {
        val c = Ui.section(this, "Acciones rápidas", Ui.BLU)
        c.addView(Ui.actionRowIcon(
            this,
            "warning",
            if (Prefs.panicMode) "Desactivar modo pánico" else "Activar modo pánico",
            if (Prefs.panicMode) "Vuelve al estado normal"
            else if (Prefs.trustedPackages().isEmpty())
                "⚠ Cortará la red de TODAS las apps (no hay apps de confianza)"
            else "Corta la red de todo menos ${Prefs.trustedPackages().size} app(s) de confianza",
            if (Prefs.panicMode) Ui.GRN else Ui.RED
        ) {
            if (Prefs.panicMode) PanicModeManager.disable(this) else PanicModeManager.enable(this)
            renderSafe()
        })
        c.addView(Ui.actionRowIcon(
            this,
            "lock",
            if (Prefs.firewallEnabled) "Parar cortafuegos VPN" else "Activar cortafuegos VPN",
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
        c.addView(Ui.actionRowIcon(
            this, "activity", "Monitor de seguridad",
            if (Prefs.panicMode) "En marcha" else "Servicio en primer plano con aviso permanente",
            Ui.CY
        ) {
            SecurityMonitorService.start(this)
            renderSafe()
        })
        c.addView(Ui.actionRowIcon(
            this, "scan", "Escanear ahora", "Análisis inmediato + cuarentena automática", Ui.CY
        ) { runScan() })
        root.addView(c)
    }

    private fun runScan() {
        Bg.run(this, "scanManual") {
            val res = AutoDefense.runOnce(this, notify = true)
            runOnUiThread {
                Notify.post(this, "Escaneo manual", res)
                renderSafe()
            }
        }
    }

    // ── Defensa automática ───────────────────────────────────────────

    private fun autoDefenseCard() {
        val autoOn = Prefs.autoDefenseEnabled
        val c = Ui.section(this, "Defensa automática · 0 clics", if (autoOn) Ui.GRN else Ui.AMB)
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
        val c = Ui.section(this, "Últimos eventos", Ui.AMB)
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(Ui.label(this, "Cargando…", Ui.T3))
        c.addView(body)
        c.addView(Ui.actionRowIcon(
            this, "warning", "Ver todo el registro", "Historial completo con filtros", Ui.AMB
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_VPN && resultCode == RESULT_OK) {
            FirewallVpnService.start(this)
        }
        renderSafe()
    }

    companion object {
        private const val REQ_VPN = 4202
        private val TAB_TITLES = listOf("Inicio", "Módulos", "Acciones", "Eventos")
    }
}
