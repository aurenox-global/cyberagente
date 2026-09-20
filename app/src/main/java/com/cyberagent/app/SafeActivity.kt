package com.cyberagent.app

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.cyberagent.app.core.CrashLog
import com.cyberagent.app.ui.Ui

/**
 * Actividad base con red de seguridad: si la construcción de la pantalla lanza
 * una excepción, se muestra el stack trace EN PANTALLA en lugar de cerrar la
 * aplicación. Sirve tanto para el usuario como para el diagnóstico.
 */
abstract class SafeActivity : AppCompatActivity() {

    final override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            build(savedInstanceState)
        } catch (t: Throwable) {
            CrashLog.record(this, this::class.java.simpleName + ".build", t)
            showCrash(t)
        }
    }

    /** Cada actividad implementa aquí su construcción de UI. */
    abstract fun build(savedInstanceState: Bundle?)

    private fun showCrash(t: Throwable) {
        val root = Ui.screen(this, "ERROR INTERNO", "Se ha registrado el fallo para poder corregirlo.")
        root.addView(Ui.label(this, t.javaClass.name + ": " + (t.message ?: ""), Ui.RED, 13f))
        root.addView(Ui.label(this, Log.getStackTraceString(t), Ui.T2, 10f))
        root.addView(Ui.label(this, "Copia esta pantalla y envíala al desarrollador.", Ui.T3, 11f))
    }
}
