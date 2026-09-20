package com.cyberagent.app

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.appcompat.widget.AppCompatEditText as EditText
import android.widget.LinearLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.cyberagent.app.core.Db
import com.cyberagent.app.core.Prefs
import com.cyberagent.app.core.SecurityAlert
import com.cyberagent.app.receiver.SmsDeliverReceiver
import com.cyberagent.app.service.CallGuardService
import com.cyberagent.app.ui.Ui

class CallSmsShieldActivity : SafeActivity() {

    override fun build(savedInstanceState: android.os.Bundle?) {
        render()
    }

    private fun render() {
        val root = Ui.screen(
            this,
            "BLINDAJE DE LLAMADAS Y SMS",
            "Bloqueo real: rechazo de llamadas con CallScreeningService y descarte de SMS " +
                "cuando la app es la predeterminada."
        )

        val state = Ui.card(this)
        state.addView(
            Ui.statusDot(
                this, CallGuardService.hasRole(this),
                "Rol de filtrado de llamadas: " +
                    (if (CallGuardService.hasRole(this)) "concedido" else "pendiente")
            )
        )
        val smsRole = SmsDeliverReceiver.isDefaultSmsApp(this)
        state.addView(
            Ui.statusDot(
                this, smsRole,
                "App de SMS predeterminada: " +
                    (if (smsRole) "sí (puede descartar mensajes)" else "no (solo avisos)")
            )
        )
        state.addView(
            Ui.statusDot(
                this,
                ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) ==
                    PackageManager.PERMISSION_GRANTED,
                "Permiso de recepción de SMS"
            )
        )
        state.addView(
            Ui.row(
                this,
                Ui.button(this, "CONCEDER ROL DE LLAMADAS", true) { requestCallRole() },
                Ui.button(this, "SER APP DE SMS") { requestSmsRole() }
            )
        )
        state.addView(
            Ui.row(
                this,
                Ui.button(this, "PERMISOS DE SMS/LLAMADAS") {
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(
                            Manifest.permission.RECEIVE_SMS,
                            Manifest.permission.READ_SMS,
                            Manifest.permission.READ_PHONE_STATE,
                            Manifest.permission.READ_CALL_LOG,
                            Manifest.permission.READ_CONTACTS
                        ),
                        REQ_PERMS
                    )
                },
                Ui.button(this, "AJUSTES DE LLAMADAS") {
                    startActivity(
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                            Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
                        else Intent(Settings.ACTION_SETTINGS)
                    )
                }
            )
        )
        root.addView(state)

        val toggles = Ui.card(this)
        toggles.addView(Ui.heading(this, "CONMUTADORES"))
        toggles.addView(
            Ui.row(
                this,
                Ui.button(
                    this,
                    "Filtro de llamadas: " + if (Prefs.callShieldEnabled) "ON" else "OFF",
                    Prefs.callShieldEnabled
                ) {
                    Prefs.callShieldEnabled = !Prefs.callShieldEnabled
                    render()
                },
                Ui.button(
                    this,
                    "Blindaje SMS: " + if (Prefs.smsShieldEnabled) "ON" else "OFF",
                    Prefs.smsShieldEnabled
                ) {
                    Prefs.smsShieldEnabled = !Prefs.smsShieldEnabled
                    render()
                }
            )
        )
        root.addView(toggles)

        val input = Ui.card(this)
        input.addView(Ui.heading(this, "LISTA DE BLOQUEO DE NÚMEROS"))
        val et = EditText(this).apply {
            hint = "+34123456789"
            setTextColor(Ui.T1)
            setHintTextColor(Ui.T3)
            textSize = 13f
        }
        input.addView(et)
        input.addView(
            Ui.row(
                this,
                Ui.button(this, "BLOQUEAR") {
                    val n = et.text.toString()
                    if (n.isNotBlank()) {
                        Prefs.blockCall(n)
                        Db.addAlert(
                            SecurityAlert(
                                severity = "INFO",
                                module = "Blindaje de llamadas",
                                title = "Número añadido a la lista de bloqueo",
                                detail = Prefs.normalize(n)
                            )
                        )
                        et.setText("")
                        render()
                    }
                },
                Ui.button(this, "A LISTA BLANCA") {
                    val n = et.text.toString()
                    if (n.isNotBlank()) {
                        Prefs.allowCall(n)
                        et.setText("")
                        render()
                    }
                }
            )
        )
        val blocked = Prefs.blockedCalls()
        if (blocked.isEmpty()) {
            input.addView(Ui.label(this, "Sin números bloqueados.", Ui.T3))
        } else {
            for (n in blocked) {
                input.addView(
                    Ui.row(
                        this,
                        Ui.label(this, n, Ui.T1, 12.5f),
                        Ui.button(this, "quitar") { Prefs.unblockCall(n); render() }
                    )
                )
            }
        }
        root.addView(input)

        val history = Ui.card(this)
        history.addView(Ui.heading(this, "EVENTOS RECIENTES"))
        val events = Db.alerts(limit = 25).filter {
            it.module.contains("llamada") || it.module.contains("SMS")
        }
        if (events.isEmpty()) {
            history.addView(Ui.label(this, "Sin eventos de llamadas o SMS.", Ui.T3))
        } else {
            for (e in events) {
                history.addView(
                    Ui.label(
                        this, "[${e.severity}] ${Ui.time(e.ts)}",
                        Ui.severityColor(e.severity), 11.5f
                    )
                )
                history.addView(Ui.label(this, e.title, Ui.T1, 12.5f))
                history.addView(Ui.label(this, e.detail, Ui.T2, 11.5f))
                history.addView(Ui.divider(this))
            }
        }
        root.addView(history)
    }

    private fun requestCallRole() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            note("Rol no disponible", "El filtrado de llamadas requiere Android 10 o superior.")
            return
        }
        val rm = getSystemService(RoleManager::class.java)
        if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
            startActivityForResult(
                rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING), REQ_CALL_ROLE
            )
        } else {
            note("Rol no disponible", "Este dispositivo no ofrece el rol de filtrado de llamadas.")
        }
    }

    private fun requestSmsRole() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            note("Rol no disponible", "La selección de app de SMS requiere Android 10 o superior.")
            return
        }
        val rm = getSystemService(RoleManager::class.java)
        if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_SMS)) {
            startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_SMS), REQ_SMS_ROLE)
        } else {
            note("Rol no disponible", "Este dispositivo no permite solicitar el rol de SMS.")
        }
    }

    private fun note(title: String, detail: String) {
        Db.addAlert(
            SecurityAlert(severity = "INFO", module = "Blindaje", title = title, detail = detail)
        )
        render()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        render()
    }

    companion object {
        private const val REQ_PERMS = 4301
        private const val REQ_CALL_ROLE = 4302
        private const val REQ_SMS_ROLE = 4303
    }
}
