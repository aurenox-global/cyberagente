package com.cyberagent.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

/**
 * Icono vectorial dibujado a mano (estilo línea, lienzo 24×24).
 *
 * Motivo: los emoji (🏠 🛡 ✅ …) se dibujan con la fuente a color del sistema y
 * rompen la estética de la app (parecen pegatinas, cambian entre fabricantes y
 * no se pueden tintar). Aquí los iconos son trazos monocromos, tintables y
 * consistentes en cualquier dispositivo.
 *
 * REGLA PARA FUTUROS PROYECTOS: no usar emoji como iconos de interfaz. Usar
 * iconos vectoriales (VectorDrawable XML o, si la UI es programática, este
 * patrón: trazos sobre un lienzo de 24×24 tintados con el color de acento).
 */
class IconView(
    ctx: Context,
    private val name: String,
    private val tint: Int,
    private val strokeDp: Float = 1.8f
) : View(ctx) {

    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = tint
    }
    private val solid = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = tint
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size = minOf(width, height).toFloat()
        if (size <= 0f) return
        val pad = size * 0.14f
        val inner = size - 2f * pad
        val k = inner / 24f
        val (strokes, fills) = Icons.build(name)
        val m = Matrix().apply { setScale(k, k) }
        strokes.transform(m)
        fills.transform(m)
        stroke.strokeWidth = strokeDp * resources.displayMetrics.density
        canvas.save()
        canvas.translate(pad, pad)
        canvas.drawPath(fills, solid)
        canvas.drawPath(strokes, stroke)
        canvas.restore()
    }
}

/** Biblioteca de iconos (trazos 24×24). Devuelve (contorno, relleno). */
object Icons {

    private fun Path.arc(cx: Float, cy: Float, r: Float, start: Float, sweep: Float) {
        addArc(RectF(cx - r, cy - r, cx + r, cy + r), start, sweep)
    }

    fun build(name: String): Pair<Path, Path> {
        val s = Path()
        val f = Path()
        when (name) {
            "shield" -> {
                s.moveTo(12f, 2.6f); s.lineTo(20f, 5.8f); s.lineTo(20f, 11.6f)
                s.cubicTo(20f, 16.6f, 16.7f, 20.7f, 12f, 21.9f)
                s.cubicTo(7.3f, 20.7f, 4f, 16.6f, 4f, 11.6f)
                s.lineTo(4f, 5.8f); s.close()
            }
            "check" -> {
                s.arc(12f, 12f, 8.6f, 0f, 360f)
                s.moveTo(7.8f, 12.3f); s.lineTo(10.8f, 15.3f); s.lineTo(16.4f, 8.8f)
            }
            "warning" -> {
                s.moveTo(12f, 3.4f); s.lineTo(21.8f, 20.4f); s.lineTo(2.2f, 20.4f); s.close()
                s.moveTo(12f, 9.6f); s.lineTo(12f, 14.2f)
                f.arc(12f, 17.2f, 1.15f, 0f, 360f)
            }
            "bell" -> {
                s.moveTo(6.2f, 17.4f); s.lineTo(17.8f, 17.4f)
                s.moveTo(8.2f, 17.4f); s.lineTo(8.2f, 11.8f)
                s.arc(12f, 11.8f, 3.8f, 180f, 180f)
                s.moveTo(15.8f, 11.8f); s.lineTo(15.8f, 17.4f)
                s.moveTo(10f, 19.2f); s.quadTo(12f, 21.2f, 14f, 19.2f)
                f.arc(12f, 6.9f, 1.15f, 0f, 360f)
            }
            "home" -> {
                s.moveTo(3.4f, 11f); s.lineTo(12f, 3.6f); s.lineTo(20.6f, 11f)
                s.moveTo(5.6f, 9.9f); s.lineTo(5.6f, 20.4f); s.lineTo(18.4f, 20.4f); s.lineTo(18.4f, 9.9f)
                s.moveTo(10f, 20.4f); s.lineTo(10f, 14.6f); s.lineTo(14f, 14.6f); s.lineTo(14f, 20.4f)
            }
            "scan" -> {
                s.arc(10.8f, 10.8f, 6.4f, 0f, 360f)
                s.moveTo(15.4f, 15.4f); s.lineTo(20.4f, 20.4f)
            }
            "bot" -> {
                s.addRoundRect(RectF(4f, 8.2f, 20f, 20f), 3.2f, 3.2f, Path.Direction.CW)
                s.moveTo(12f, 8.2f); s.lineTo(12f, 4.4f)
                f.arc(12f, 3.5f, 1.2f, 0f, 360f)
                f.arc(9.2f, 13.4f, 1.25f, 0f, 360f)
                f.arc(14.8f, 13.4f, 1.25f, 0f, 360f)
                s.moveTo(9f, 16.9f); s.lineTo(15f, 16.9f)
            }
            "settings" -> {
                s.arc(12f, 12f, 6.4f, 0f, 360f)
                s.arc(12f, 12f, 2.4f, 0f, 360f)
                for (i in 0 until 8) {
                    val a = Math.toRadians((i * 45).toDouble())
                    val c = cos(a).toFloat(); val sn = sin(a).toFloat()
                    s.moveTo(12f + c * 6.4f, 12f + sn * 6.4f)
                    s.lineTo(12f + c * 9.2f, 12f + sn * 9.2f)
                }
            }
            "apps" -> {
                s.addRoundRect(RectF(3.6f, 3.6f, 10.4f, 10.4f), 2.2f, 2.2f, Path.Direction.CW)
                s.addRoundRect(RectF(13.6f, 3.6f, 20.4f, 10.4f), 2.2f, 2.2f, Path.Direction.CW)
                s.addRoundRect(RectF(3.6f, 13.6f, 10.4f, 20.4f), 2.2f, 2.2f, Path.Direction.CW)
                s.addRoundRect(RectF(13.6f, 13.6f, 20.4f, 20.4f), 2.2f, 2.2f, Path.Direction.CW)
            }
            "phone" -> {
                s.addRoundRect(RectF(6.8f, 3f, 17.2f, 21f), 2.4f, 2.4f, Path.Direction.CW)
                s.moveTo(10.6f, 18.2f); s.lineTo(13.4f, 18.2f)
            }
            "globe" -> {
                s.arc(12f, 12f, 8.8f, 0f, 360f)
                s.addOval(RectF(8f, 3.2f, 16f, 20.8f), Path.Direction.CW)
                s.moveTo(3.2f, 12f); s.lineTo(20.8f, 12f)
                s.moveTo(4.8f, 7.6f); s.lineTo(19.2f, 7.6f)
                s.moveTo(4.8f, 16.4f); s.lineTo(19.2f, 16.4f)
            }
            "bug" -> {
                s.arc(12f, 13.2f, 4.8f, 0f, 360f)
                s.moveTo(12f, 9.2f); s.lineTo(12f, 16.8f)
                s.moveTo(8.6f, 9.4f); s.lineTo(6.2f, 6.6f)
                s.moveTo(15.4f, 9.4f); s.lineTo(17.8f, 6.6f)
                s.moveTo(9.2f, 8.2f); s.lineTo(8.2f, 4.6f)
                s.moveTo(14.8f, 8.2f); s.lineTo(15.8f, 4.6f)
                s.moveTo(7.2f, 13.2f); s.lineTo(3.4f, 13.2f)
                s.moveTo(16.8f, 13.2f); s.lineTo(20.6f, 13.2f)
                s.moveTo(8.6f, 17.4f); s.lineTo(6.2f, 20.2f)
                s.moveTo(15.4f, 17.4f); s.lineTo(17.8f, 20.2f)
            }
            "flask" -> {
                s.moveTo(9.4f, 2.8f); s.lineTo(14.6f, 2.8f)
                s.moveTo(10.6f, 2.8f); s.lineTo(10.6f, 9f)
                s.moveTo(13.4f, 2.8f); s.lineTo(13.4f, 9f)
                s.moveTo(10.6f, 9f); s.lineTo(5.4f, 18.6f)
                s.quadTo(4.6f, 20.6f, 6.6f, 20.6f)
                s.lineTo(17.4f, 20.6f)
                s.quadTo(19.4f, 20.6f, 18.6f, 18.6f)
                s.lineTo(13.4f, 9f)
                s.moveTo(7.6f, 15.8f); s.lineTo(16.4f, 15.8f)
            }
            "wifi" -> {
                s.arc(12f, 19f, 11f, 225f, 90f)
                s.arc(12f, 19f, 7.4f, 225f, 90f)
                s.arc(12f, 19f, 3.8f, 225f, 90f)
                f.arc(12f, 19f, 1.5f, 0f, 360f)
            }
            "chart" -> {
                s.moveTo(3.6f, 4f); s.lineTo(3.6f, 20.4f); s.lineTo(20.4f, 20.4f)
                s.moveTo(7f, 16.6f); s.lineTo(11f, 10.6f); s.lineTo(14.4f, 13.8f); s.lineTo(19.6f, 6.4f)
                f.arc(19.6f, 6.4f, 1.1f, 0f, 360f)
            }
            "tools" -> {
                s.moveTo(4f, 7f); s.lineTo(20f, 7f)
                s.moveTo(4f, 12f); s.lineTo(20f, 12f)
                s.moveTo(4f, 17f); s.lineTo(20f, 17f)
                f.arc(9f, 7f, 2f, 0f, 360f)
                f.arc(15f, 12f, 2f, 0f, 360f)
                f.arc(8f, 17f, 2f, 0f, 360f)
            }
            "star" -> {
                s.moveTo(12f, 3.4f); s.lineTo(14.6f, 9f); s.lineTo(20.7f, 9.8f)
                s.lineTo(16.2f, 14f); s.lineTo(17.3f, 20.1f); s.lineTo(12f, 17.1f)
                s.lineTo(6.7f, 20.1f); s.lineTo(7.8f, 14f); s.lineTo(3.3f, 9.8f)
                s.lineTo(9.4f, 9f); s.close()
            }
            "share" -> {
                s.arc(6f, 6f, 2.4f, 0f, 360f)
                s.arc(6f, 18f, 2.4f, 0f, 360f)
                s.arc(18f, 12f, 2.4f, 0f, 360f)
                s.moveTo(8.1f, 7.1f); s.lineTo(15.9f, 10.9f)
                s.moveTo(8.1f, 16.9f); s.lineTo(15.9f, 13.1f)
            }
            "lock" -> {
                s.addRoundRect(RectF(4.6f, 10.2f, 19.4f, 20.6f), 3f, 3f, Path.Direction.CW)
                s.moveTo(8.2f, 10.2f); s.lineTo(8.2f, 7.4f)
                s.arc(12f, 7.4f, 3.8f, 180f, 180f)
                s.moveTo(15.8f, 7.4f); s.lineTo(15.8f, 10.2f)
                f.arc(12f, 15.2f, 1.5f, 0f, 360f)
            }
            "activity" -> {
                s.moveTo(3f, 12f); s.lineTo(7.6f, 12f); s.lineTo(10f, 5.6f)
                s.lineTo(14f, 18.4f); s.lineTo(16.4f, 12f); s.lineTo(21f, 12f)
            }
            "plus" -> {
                s.moveTo(12f, 5f); s.lineTo(12f, 19f)
                s.moveTo(5f, 12f); s.lineTo(19f, 12f)
            }
            else -> {
                s.arc(12f, 12f, 8.6f, 0f, 360f)
                s.moveTo(12f, 8f); s.lineTo(12f, 13f)
                f.arc(12f, 16f, 1.1f, 0f, 360f)
            }
        }
        return s to f
    }
}
