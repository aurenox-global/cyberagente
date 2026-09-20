package com.cyberagent.app

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.LinearLayout
import android.widget.Toast
import com.cyberagent.app.core.PanicModeManager
import com.cyberagent.app.core.Perms
import com.cyberagent.app.core.Prefs
import com.cyberagent.app.receiver.SmsDeliverReceiver
import com.cyberagent.app.service.CallGuardService
import com.cyberagent.app.service.NotifGuardService
import com.cyberagent.app.ui.Ui

/**
 * Asistente de accesos.
 *
 * Android NO permite pedir todos los permisos de una vez: los normales salen
 * en diálogos, pero el acceso al uso, la accesibilidad, los roles de SMS y
 * llamadas, No Molestar y el consentimiento de la VPN tienen cada uno su
 * propia pantalla del sistema. Hasta ahora había que concederlos a mano, uno a
 * uno y buscándolos.
 *
 * Este asistente los recorre TODOS en orden con un toque ("CONCEDER TODO") y
 * vuelve solo al siguiente paso, mostrando en la lista qué está concedido y
 * qué falta.
 */
class PermissionsActivity : SafeActivity() {

    private lateinit var root: LinearLayout
    private val handler = Handler(Looper.getMainLooper())

    private var wizard = false
    private var index = 0
    private var lastAdvance = 0L

    private enum class Kind {
        RUNTIME, USAGE, ROLE_CALL, ROLE_SMS, ACCESSIBILITY, DND, VPN, BATTERY, NOTIF_ACCESS, OVERLAY
    }

    private data class Step(
        val emoji: String,
        val title: String,
        val why: String,
        val kind: Kind,
        val perms: List<String> = emptyList()
    )

    private fun steps(): List<Step> = listOf(
        Step(
            "🔔", "Alertas y Wi-Fi",
            "Notificaciones de amenaza y lectura del estado de la red.",
            Kind.RUNTIME,
            listOfNotNull(
                if (Build.VERSION.SDK_INT >= 33) Manifest.permission.POST_NOTIFICATIONS else null,
                if (Build.VERSION.SDK_INT >= 33) Manifest.permission.NEARBY_WIFI_DEVICES else null
            )
        ),
        Step(
            "📞", "Teléfono y llamadas",
            "Estado de la SIM, historial de llamadas y contactos para el blindaje.",
            Kind.RUNTIME,
            listOf(
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.READ_CALL_LOG,
                Manifest.permission.READ_CONTACTS
            )
        ),
        Step(
            "✉️", "Mensajes (SMS/MMS)",
            "Analizar y descartar mensajes fraudulentos en el momento en que llegan.",
            Kind.RUNTIME,
            listOf(
                Manifest.permission.RECEIVE_SMS,
                Manifest.permission.READ_SMS,
                Manifest.permission.RECEIVE_MMS
            )
        ),
        Step(
            "📊", "Acceso al uso",
            "El consumo de datos por aplicación se lee con esta pantalla del sistema.",
            Kind.USAGE
        ),
        Step(
            "🛡️", "Rol de filtrado de llamadas",
            "Permite RECHAZAR llamadas de la lista negra u ocultas (Android no lo da por diálogo).",
            Kind.ROLE_CALL
        ),
        Step(
            "📥", "App de SMS predeterminada",
            "Solo con este rol la app puede DESCARtar un SMS fraudulento en vez de solo avisar.",
            Kind.ROLE_SMS
        ),
        Step(
            "🧩", "Servicio de accesibilidad",
            "Avisa cuando se abre el instalador de paquetes. No lee la pantalla.",
            Kind.ACCESSIBILITY
        ),
        Step(
            "🔕", "Acceso a No Molestar",
            "Necesario solo si usas el modo pánico (silencio de avisos).",
            Kind.DND
        ),
        Step(
            "🔋", "Batería sin restricciones",
            "Sin esto Android puede dormir los escaneos automáticos en segundo plano.",
            Kind.BATTERY
        ),
        Step(
            "👁️", "Acceso a notificaciones (opcional)",
            "Revisa enlaces sospechosos en avisos de otras apps: WhatsApp, correo, redes.",
            Kind.NOTIF_ACCESS
        ),
        Step(
            "🚧", "Cortafuegos (VPN)",
            "Consentimiento del túnel local que bloquea la red de las apps marcadas.",
            Kind.VPN
        ),
        Step(
            "🪟", "Anti-superposición (opcional)",
            "Avisa si una app dibuja una pantalla encima de otra (pantalla falsa de banco).",
            Kind.OVERLAY
        )
    )

    override fun build(savedInstanceState: Bundle?) {
        render()
    }

    override fun onResume() {
        super.onResume()
        com.cyberagent.app.core.NetworkStatsReader.invalidateUsageAccessCache()
        render()
        // Al volver de una pantalla del sistema, seguimos con el siguiente paso.
        if (wizard && System.currentTimeMillis() - lastAdvance > 1200) {
            handler.postDelayed({ advance() }, 600)
        }
    }

    private fun isOk(s: Step): Boolean = when (s.kind) {
        Kind.RUNTIME -> s.perms.all { Perms.granted(this, it) }
        Kind.USAGE -> Perms.usageAccess(this)
        Kind.ROLE_CALL -> CallGuardService.hasRole(this)
        Kind.ROLE_SMS -> SmsDeliverReceiver.isDefaultSmsApp(this)
        Kind.ACCESSIBILITY -> Perms.accessibilityEnabled(this)
        Kind.DND -> Perms.notificationPolicy(this)
        Kind.VPN -> Prefs.firewallEnabled || VpnService.prepare(this) == null
        Kind.BATTERY -> batteryFree()
        Kind.NOTIF_ACCESS -> NotifGuardService.isConnected(this)
        // Es opcional: si el usuario no lo quiere, no queda como pendiente.
        Kind.OVERLAY -> !Prefs.overlayGuardEnabled || com.cyberagent.app.service.OverlayGuardService.isEnabled(this)
    }

    private fun batteryFree(): Boolean = try {
        (getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager)
            .isIgnoringBatteryOptimizations(packageName)
    } catch (t: Throwable) {
        false
    }

    private fun render() {
        val all = steps()
        val done = all.count { isOk(it) }
        val criticalPending = Perms.missing(this, Perms.critical()).size

        root = Ui.screen(
            this,
            "ACCESOS Y PERMISOS",
            "La app funciona mejor con todos los accesos. Toca CONCEDER TODO y ve confirmando."
        )

        val hero = Ui.hero(this, if (done == all.size) Ui.GRN else Ui.AMB)
        hero.addView(Ui.label(
            this,
            if (done == all.size) "● TODO CONCEDIDO ($done/${all.size})"
            else "● $done/${all.size} ACCESOS CONCEDIDOS",
            if (done == all.size) Ui.GRN else Ui.AMB, 15f
        ))
        hero.addView(Ui.label(
            this,
            if (criticalPending == 0) "Lo esencial para analizar SMS y llamadas ya está activo."
            else "Faltan $criticalPending permisos esenciales (SMS / llamadas).",
            Ui.T2, 11.5f
        ))
        hero.addView(Ui.bar(this, done, all.size, if (done == all.size) Ui.GRN else Ui.AMB))
        hero.addView(Ui.row(
            this,
            Ui.button(this, "CONCEDER TODO", true) { startWizard(0) },
            Ui.button(this, "REVISAR") { index = 0; wizard = false; render() }
        ))
        root.addView(hero)

        val list = Ui.section(this, "LISTA DE ACCESOS", Ui.CY)
        for ((i, s) in all.withIndex()) {
            val ok = isOk(s)
            val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            col.addView(Ui.label(
                this,
                (if (ok) "● " else "○ ") + s.emoji + "  " + s.title,
                if (ok) Ui.GRN else Ui.AMB, 13f
            ))
            col.addView(Ui.label(this, s.why, Ui.T3, 11f))
            if (!ok) {
                col.addView(Ui.button(this, "CONCEDER", false) { startWizard(i) })
            }
            list.addView(col)
            list.addView(Ui.divider(this))
        }
        root.addView(list)

        val foot = Ui.card(this)
        foot.addView(Ui.label(
            this,
            "Consejo: algunos accesos (uso, accesibilidad, roles) NO salen como diálogo " +
                "normal; el sistema abre su propia pantalla. Concede y vuelve con el botón atrás: " +
                "el asistente continúa solo.",
            Ui.T3, 11f
        ))
        root.addView(foot)
    }

    // ── Recorrido guiado ─────────────────────────────────────────────

    private fun startWizard(from: Int) {
        wizard = true
        index = from
        advance()
    }

    private fun advance() {
        if (!wizard) return
        lastAdvance = System.currentTimeMillis()
        val all = steps()
        while (index < all.size) {
            val s = all[index]
            index++
            if (isOk(s)) continue
            perform(s)
            return
        }
        wizard = false
        render()
        val pending = steps().count { !isOk(it) }
        Toast.makeText(
            this,
            if (pending == 0) "Todos los accesos concedidos ✅" else "Recorrido terminado · faltan $pending",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun perform(s: Step) {
        when (s.kind) {
            Kind.RUNTIME -> requestPermissions(s.perms.toTypedArray(), REQ_PERMS)

            Kind.USAGE -> startActivityForResult(
                Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:$packageName")),
                REQ_SYSTEM
            )

            Kind.ACCESSIBILITY -> startActivityForResult(
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS), REQ_SYSTEM
            )

            Kind.DND -> startActivityForResult(PanicModeManager.dndSettingsIntent(), REQ_SYSTEM)

            Kind.BATTERY -> startActivityForResult(
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS), REQ_SYSTEM
            )

            Kind.NOTIF_ACCESS -> {
                Prefs.notifGuardEnabled = true
                startActivityForResult(
                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS), REQ_SYSTEM
                )
            }

            Kind.OVERLAY -> {
                Prefs.overlayGuardEnabled = true
                startActivityForResult(
                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS), REQ_SYSTEM
                )
            }

            Kind.VPN -> {
                val prep = VpnService.prepare(this)
                if (prep != null) startActivityForResult(prep, REQ_VPN)
                else {
                    Prefs.firewallEnabled = true
                    com.cyberagent.app.service.FirewallVpnService.start(this)
                    advance()
                }
            }

            Kind.ROLE_CALL -> {
                val rm = getSystemService(RoleManager::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && rm != null &&
                    rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)
                ) {
                    startActivityForResult(
                        rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING), REQ_ROLE
                    )
                } else {
                    Toast.makeText(this, "Este móvil no ofrece ese rol", Toast.LENGTH_SHORT).show()
                    handler.postDelayed({ advance() }, 200)
                }
            }

            Kind.ROLE_SMS -> {
                val rm = getSystemService(RoleManager::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && rm != null &&
                    rm.isRoleAvailable(RoleManager.ROLE_SMS)
                ) {
                    startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_SMS), REQ_ROLE)
                } else {
                    Toast.makeText(this, "Este móvil no permite ese rol", Toast.LENGTH_SHORT).show()
                    handler.postDelayed({ advance() }, 200)
                }
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_PERMS) {
            render()
            handler.postDelayed({ advance() }, 400)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_VPN && resultCode == RESULT_OK) {
            Prefs.firewallEnabled = true
            com.cyberagent.app.service.FirewallVpnService.start(this)
        }
        if (wizard) handler.postDelayed({ advance() }, 500)
    }

    companion object {
        private const val REQ_PERMS = 5101
        private const val REQ_ROLE = 5102
        private const val REQ_SYSTEM = 5103
        private const val REQ_VPN = 5104
    }
}
