package com.cyberagent.app.service

import android.app.job.JobParameters
import android.app.job.JobService
import com.cyberagent.app.core.AutoDefense
import com.cyberagent.app.core.Prefs

/**
 * Trabajo periódico del sistema que ejecuta la defensa automática.
 * No depende de que la app esté abierta.
 */
class ScanJobService : JobService() {

    override fun onStartJob(params: JobParameters?): Boolean {
        if (!Prefs.autoDefenseEnabled) {
            jobFinished(params, false)
            return false
        }
        Thread {
            try {
                AutoDefense.runOnce(this, notify = true)
            } catch (t: Throwable) {
                // no debe tumbar el job
            }
            jobFinished(params, false)
        }.start()
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean = true
}
