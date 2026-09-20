package com.cyberagent.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/** Gráfica de barras minimalista, sin dependencias externas. */
class BarChartView @JvmOverloads constructor(
    ctx: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(ctx, attrs, defStyle) {

    private var data: List<Pair<String, Long>> = emptyList()

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Ui.CY }
    private val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Ui.LINE }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Ui.T3
        textSize = 22f
        textAlign = Paint.Align.CENTER
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF0E2029.toInt() }

    fun setData(series: List<Pair<String, Long>>) {
        data = series
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val pad = 28f
        val bottom = h - pad
        val usableH = bottom - 10f

        // Rejilla horizontal
        for (i in 0..3) {
            val y = 10f + usableH * i / 3f
            canvas.drawLine(0f, y, w, y, gridPaint)
        }

        if (data.isEmpty()) {
            canvas.drawText("sin datos", w / 2f, h / 2f, textPaint)
            return
        }

        val max = (data.maxOfOrNull { it.second } ?: 0L).coerceAtLeast(1L)
        val slot = w / data.size
        val barW = slot * 0.55f

        data.forEachIndexed { i, (name, value) ->
            val cx = slot * i + slot / 2f
            val bh = (value.toFloat() / max) * (usableH - 18f)
            val top = bottom - bh
            canvas.drawRect(
                cx - barW / 2f, top, cx + barW / 2f, bottom,
                if (value > 0) barPaint else emptyPaint
            )
            canvas.drawText(name, cx, h - 8f, textPaint)
        }
    }

    companion object {
        /** Renderiza la misma gráfica a un Bitmap (para los widgets). */
        fun renderBitmap(ctx: Context, series: List<Pair<String, Long>>, w: Int, h: Int): android.graphics.Bitmap {
            val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            c.drawColor(Color.TRANSPARENT)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Ui.CY }
            val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Ui.T3
                textSize = if (h < 90) 18f else 22f
                textAlign = Paint.Align.CENTER
            }
            if (series.isEmpty()) return bmp
            val max = (series.maxOfOrNull { it.second } ?: 0L).coerceAtLeast(1L)
            val slot = w.toFloat() / series.size
            val barW = slot * 0.55f
            val bottom = h - 26f
            series.forEachIndexed { i, (name, v) ->
                val cx = slot * i + slot / 2f
                val bh = (v.toFloat() / max) * (bottom - 12f)
                paint.color = if (v > 0) Ui.CY else Ui.LINE
                c.drawRect(cx - barW / 2f, bottom - bh, cx + barW / 2f, bottom, paint)
                c.drawText(name, cx, h - 6f, label)
            }
            return bmp
        }
    }
}
