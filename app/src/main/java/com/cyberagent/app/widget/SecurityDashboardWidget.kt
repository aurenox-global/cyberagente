package com.cyberagent.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.cyberagent.app.MainActivity
import com.cyberagent.app.R
import com.cyberagent.app.core.AutoDefense
import com.cyberagent.app.core.Db
import com.cyberagent.app.core.Prefs
import com.cyberagent.app.core.TrafficWatch
import com.cyberagent.app.ui.Ui

/**
 * Widget 4x4: panel de estado completo en la pantalla de inicio.
 *
 * Muestra, de un vistazo: escaneos realizados, apps analizadas, apps en
 * cuarentena, alertas de las últimas 24 h y qué aplicaciones han tenido
 * tráfico en los últimos 15 minutos. Botón "Escanear" que lanza un análisis
 * inmediato en segundo plano y "Abrir" que lleva al panel.
 *
 * Todo se calcula en un hilo de fondo (las estadísticas de red tardan) con
 * goAsync(), así el lanzador nunca se bloquea.
 */
class SecurityDashboardWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        Thread {
            try {
                render(context, manager, ids)
            } catch (t: Throwable) {
                // un widget nunca debe romper el lanzador
            } finally {
                try {
                    pending?.finish()
                } catch (ignored: Throwable) {
                }
            }
        }.start()
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_SCAN) {
            // Escaneo inmediato (JobScheduler) + refresco del widget.
            AutoDefense.runNow(context)
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, SecurityDashboardWidget::class.java)
            )
            if (ids.isNotEmpty()) onUpdate(context, manager, ids)
            return
        }
        super.onReceive(context, intent)
    }

    companion object {

        const val ACTION_SCAN = "com.cyberagent.app.action.WIDGET_SCAN"

        /** Pide a todos los widgets que se repinten (se llama tras un escaneo). */
        fun notifyUpdate(context: Context) {
            try {
                val manager = AppWidgetManager.getInstance(context)
                val ids = manager.getAppWidgetIds(
                    ComponentName(context, SecurityDashboardWidget::class.java)
                )
                if (ids.isEmpty()) return
                val i = Intent(context, SecurityDashboardWidget::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                }
                context.sendBroadcast(i)
            } catch (t: Throwable) {
                // sin widgets instalados
            }
        }

        fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val now = System.currentTimeMillis()
            val alerts24 = Db.countAlerts(now - 86_400_000L)
            val high24 = Db.countHighAlerts(now - 86_400_000L)
            val quarantine = Prefs.quarantine().size
            val scans = Prefs.scanCount
            val appsAnalyzed = Prefs.lastAppsAnalyzed
            val last = Prefs.lastAutoScan
            val traffic = TrafficWatch.recent(context, 15, 3)

            val views = RemoteViews(context.packageName, R.layout.widget_dashboard)
            views.setTextViewText(R.id.w_state, if (high24 > 0) "⚠ $high24 ALTA" else "SEGURO")
            views.setTextColor(
                R.id.w_state,
                if (high24 > 0) 0xFFFF3B5C.toInt() else 0xFF00FF88.toInt()
            )
            views.setTextViewText(R.id.w_c1, scans.toString())
            views.setTextViewText(R.id.w_c2, appsAnalyzed.toString())
            views.setTextViewText(R.id.w_c3, quarantine.toString())
            views.setTextViewText(R.id.w_c4, alerts24.toString())
            views.setTextViewText(
                R.id.w_footer,
                if (last > 0) "Último escaneo: " + Ui.time(last) else "Sin escaneos aún"
            )

            val rows = intArrayOf(R.id.w_t1, R.id.w_t2, R.id.w_t3)
            if (traffic.isEmpty()) {
                views.setTextViewText(
                    rows[0],
                    if (com.cyberagent.app.core.Perms.usageAccess(context)) "— sin tráfico reciente"
                    else "— concede el acceso al uso"
                )
                views.setViewVisibility(rows[1], View.GONE)
                views.setViewVisibility(rows[2], View.GONE)
            } else {
                for ((i, r) in rows.withIndex()) {
                    if (i < traffic.size) {
                        val a = traffic[i]
                        views.setViewVisibility(r, View.VISIBLE)
                        views.setTextViewText(
                            r, "• " + a.label.take(18) + " · " + Ui.bytes(a.total)
                        )
                    } else {
                        views.setViewVisibility(r, View.GONE)
                    }
                }
            }

            val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            views.setOnClickPendingIntent(
                R.id.w_root,
                PendingIntent.getActivity(context, 3, Intent(context, MainActivity::class.java), flags)
            )
            views.setOnClickPendingIntent(
                R.id.w_btn_open,
                PendingIntent.getActivity(context, 1, Intent(context, MainActivity::class.java), flags)
            )
            views.setOnClickPendingIntent(
                R.id.w_btn_scan,
                PendingIntent.getBroadcast(
                    context, 2,
                    Intent(context, SecurityDashboardWidget::class.java).setAction(ACTION_SCAN),
                    flags
                )
            )

            for (id in ids) manager.updateAppWidget(id, views)
        }
    }
}
