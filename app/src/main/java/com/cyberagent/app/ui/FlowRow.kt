package com.cyberagent.app.ui

import android.content.Context
import android.view.View
import android.view.ViewGroup

/**
 * Contenedor «fila que salta de línea» (flow layout).
 *
 * Motivo: cuando varios botones no caben en una fila horizontal, un
 * LinearLayout los comprime y el texto parte letra a letra («AISLA / R»,
 * «VOLVER A FÁBRICA» en vertical). Este contenedor mide los hijos y, si no
 * caben, los baja a la línea siguiente, de modo que cada botón conserva su
 * texto intacto.
 */
class FlowRow(ctx: Context) : ViewGroup(ctx) {

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val avail = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
        val limit = if (avail > 0) avail else Int.MAX_VALUE

        var lineW = 0
        var lineH = 0
        var totalH = paddingTop + paddingBottom
        var maxW = 0

        for (i in 0 until childCount) {
            val c = getChildAt(i)
            if (c.visibility == GONE) continue
            measureChildWithMargins(c, widthMeasureSpec, 0, heightMeasureSpec, 0)
            val lp = c.layoutParams as MarginLayoutParams
            val cw = c.measuredWidth + lp.leftMargin + lp.rightMargin
            val ch = c.measuredHeight + lp.topMargin + lp.bottomMargin
            if (lineW > 0 && lineW + cw > limit) {
                totalH += lineH
                maxW = maxOf(maxW, lineW)
                lineW = 0
                lineH = 0
            }
            lineW += cw
            lineH = maxOf(lineH, ch)
        }
        totalH += lineH
        maxW = maxOf(maxW, lineW)

        setMeasuredDimension(
            resolveSize(maxW + paddingLeft + paddingRight, widthMeasureSpec),
            resolveSize(totalH, heightMeasureSpec)
        )
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val avail = (r - l) - paddingLeft - paddingRight
        var x = paddingLeft
        var y = paddingTop
        var lineH = 0
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            if (c.visibility == GONE) continue
            val lp = c.layoutParams as MarginLayoutParams
            val cw = c.measuredWidth + lp.leftMargin + lp.rightMargin
            val ch = c.measuredHeight + lp.topMargin + lp.bottomMargin
            if (x > paddingLeft && (x - paddingLeft) + cw > avail) {
                x = paddingLeft
                y += lineH
                lineH = 0
            }
            c.layout(
                x + lp.leftMargin,
                y + lp.topMargin,
                x + lp.leftMargin + c.measuredWidth,
                y + lp.topMargin + c.measuredHeight
            )
            x += cw
            lineH = maxOf(lineH, ch)
        }
    }

    override fun generateDefaultLayoutParams(): MarginLayoutParams =
        MarginLayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)

    override fun generateLayoutParams(p: LayoutParams): MarginLayoutParams = MarginLayoutParams(p)

    override fun checkLayoutParams(p: LayoutParams): Boolean = p is MarginLayoutParams
}
