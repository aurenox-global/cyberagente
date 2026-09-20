package com.cyberagent.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.cyberagent.app.InternetUsageActivity
import com.cyberagent.app.R
import com.cyberagent.app.core.NetworkStatsReader

/** Widget: consumo de red de hoy. */
class InternetUsageWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        // La lectura de NetworkStatsManager tarda segundos: fuera del hilo principal.
        Thread { render(context, manager, ids) }.start()
    }

    companion object {
        fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val total = NetworkStatsReader.totalForToday(context)
            val has = NetworkStatsReader.hasUsageAccess(context)
            val views = RemoteViews(context.packageName, R.layout.widget_usage)
            views.setTextViewText(
                R.id.w_value,
                if (has) NetworkStatsReader.formatBytes(total) else "—"
            )
            views.setTextViewText(
                R.id.w_detail,
                if (has) "Wi-Fi + móvil" else "concede acceso al uso"
            )
            views.setOnClickPendingIntent(
                R.id.w_value,
                PendingIntent.getActivity(
                    context, 0, Intent(context, InternetUsageActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            for (id in ids) manager.updateAppWidget(id, views)
        }
    }
}
