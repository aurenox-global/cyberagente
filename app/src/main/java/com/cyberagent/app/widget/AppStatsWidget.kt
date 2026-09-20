package com.cyberagent.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.cyberagent.app.MainActivity
import com.cyberagent.app.R
import com.cyberagent.app.core.Db

/** Widget 2x2/4x2: estado global y alertas de las últimas 24 h. */
class AppStatsWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        manager: AppWidgetManager,
        ids: IntArray
    ) {
        // En hilo de fondo: consultar la base de datos descifrando cada fila en
        // el receptor del widget puede superar el tiempo máximo del sistema.
        Thread {
            render(context, manager, ids)
        }.start()
    }

    private fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val high = Db.countHighAlerts(System.currentTimeMillis() - 86_400_000L)
        val total = Db.countAlerts(System.currentTimeMillis() - 86_400_000L)

        val views = RemoteViews(context.packageName, R.layout.widget_stats)
        views.setTextViewText(R.id.w_status, if (high == 0) "SEGURO" else "$high ALERTA(S)")
        views.setTextViewText(R.id.w_detail, "$total eventos / 24 h")
        views.setOnClickPendingIntent(
            R.id.w_status,
            PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        )
        for (id in ids) manager.updateAppWidget(id, views)
    }
}
