package com.cyberagent.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.cyberagent.app.InternetUsageActivity
import com.cyberagent.app.MetricsSummaryActivity
import com.cyberagent.app.R
import com.cyberagent.app.core.NetworkStatsReader
import com.cyberagent.app.ui.BarChartView

/** Widget 4x2: gráfica de los últimos 7 días. */
class InternetUsageDailyWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        Thread { render(context, manager, ids) }.start()
    }

    private fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val series = NetworkStatsReader.last7Days(context)
        val total = series.sumOf { it.second }

        val bmp = BarChartView.renderBitmap(context, series, 480, 180)
        val views = RemoteViews(context.packageName, R.layout.widget_daily)
        views.setImageViewBitmap(R.id.w_chart, bmp)
        views.setTextViewText(R.id.w_detail, "Total: " + NetworkStatsReader.formatBytes(total))
        views.setOnClickPendingIntent(
            R.id.w_chart,
            PendingIntent.getActivity(
                context, 0, Intent(context, MetricsSummaryActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        )
        // Fallback si el bitmap es demasiado grande para el widget
        for (id in ids) {
            manager.updateAppWidget(id, views)
        }
    }
}
