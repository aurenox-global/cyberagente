package com.cyberagent.app.ui

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.widget.AppCompatButton
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Constructores de interfaz (100 % programáticos).
 *
 * Notas importantes:
 *  · Se usan widgets AppCompat explícitos para que el tema no infle componentes
 *    Material (causa del cierre en una build anterior).
 *  · Se aplican los insets de las barras del sistema, porque con targetSdk 35
 *    Android activa edge-to-edge y el contenido quedaba bajo la barra de estado.
 *  · Jerarquía visual clara: cada bloque es una tarjeta con barra de color,
 *    título en mayúsculas y cuerpo; los módulos son mosaicos tocables.
 */
object Ui {

    const val BG = 0xFF03060A.toInt()
    const val PANEL = 0xFF0A141F.toInt()
    const val SURF = 0xFF071019.toInt()
    const val LINE = 0xFF12303C.toInt()
    const val CY = 0xFF00FFD6.toInt()
    const val GRN = 0xFF00FF88.toInt()
    const val AMB = 0xFFFFC44D.toInt()
    const val RED = 0xFFFF3B5C.toInt()
    const val BLU = 0xFF3DA9FF.toInt()
    const val T1 = 0xFFE8FFF9.toInt()
    const val T2 = 0xFF8FB3BF.toInt()
    const val T3 = 0xFF4A6B77.toInt()
    @JvmField
    val MONO: Typeface = Typeface.MONOSPACE

    fun dp(ctx: Context, v: Int): Int = (v * ctx.resources.displayMetrics.density).toInt()

    /** Devuelve el mismo color con la transparencia indicada (0-255). */
    fun alpha(color: Int, a: Int): Int = (color and 0x00FFFFFF) or ((a and 0xFF) shl 24)

    /** Forma redondeada reutilizable. */
    fun rounded(ctx: Context, fill: Int, stroke: Int? = null, radius: Int = 8): GradientDrawable =
        GradientDrawable().apply {
            setColor(fill)
            if (stroke != null) setStroke(dp(ctx, 1), stroke)
            cornerRadius = dp(ctx, radius).toFloat()
        }

    // ── Estructura de pantalla ───────────────────────────────────────

    /** Altura real de la barra de estado (con respaldo si el recurso no existe). */
    fun statusBarHeight(ctx: Context): Int = systemDimen(ctx, "status_bar_height", 24)

    /** Altura real de la barra de navegación. */
    fun navBarHeight(ctx: Context): Int = systemDimen(ctx, "navigation_bar_height", 48)

    private fun systemDimen(ctx: Context, name: String, fallbackDp: Int): Int = try {
        val id = ctx.resources.getIdentifier(name, "dimen", "android")
        if (id > 0) ctx.resources.getDimensionPixelSize(id) else dp(ctx, fallbackDp)
    } catch (t: Throwable) {
        dp(ctx, fallbackDp)
    }

    /**
     * Padding de las barras del sistema.
     *
     * Se aplica DOS veces: primero un valor calculado al instante (recursos del
     * sistema) y después el de los insets reales. Así el contenido nunca queda
     * bajo la barra de notificaciones, ni siquiera cuando la pantalla se vuelve
     * a construir (por ejemplo al borrar el diagnóstico), que era el caso en el
     * que los insets no se reenviaban y el diseño se metía debajo de la barra.
     */
    fun applySystemBarsPadding(view: View, extraTop: Int = 0, extraBottom: Int = 0) {
        val ctx = view.context
        view.setPadding(0, statusBarHeight(ctx) + extraTop, 0, navBarHeight(ctx) + extraBottom)
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val top = if (bars.top > 0) bars.top else statusBarHeight(v.context)
            val bottom = if (bars.bottom > 0) bars.bottom else navBarHeight(v.context)
            v.setPadding(0, top + extraTop, 0, bottom + extraBottom)
            insets
        }
        ViewCompat.requestApplyInsets(view)
    }

    /** Crea la pantalla desplazable y devuelve el contenedor vertical. */
    fun screen(act: android.app.Activity, heading: String, sub: String? = null): LinearLayout {
        val scroll = ScrollView(act).apply {
            setBackgroundColor(BG)
            isFillViewport = true
            // clipToPadding = true: el contenido nunca se dibuja por debajo de
            // las barras del sistema (en Android 15 la barra de estado es
            // transparente y el texto se veía detrás de la hora y los iconos).
            clipToPadding = true
            isVerticalScrollBarEnabled = true
        }
        applySystemBarsPadding(scroll, dp(act, 14), dp(act, 28))

        val col = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(act, 16), 0, dp(act, 16), 0)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        col.addView(headingView(act, heading))
        if (sub != null) col.addView(label(act, sub, T3, 11.5f))
        scroll.addView(col)
        act.setContentView(scroll)
        return col
    }

    private fun headingView(ctx: Context, text: String): TextView =
        TextView(ctx).apply {
            this.text = text
            setTextColor(CY)
            textSize = 18f
            typeface = MONO
            letterSpacing = 0.04f
            setPadding(0, 0, 0, dp(ctx, 4))
        }

    // ── Texto ────────────────────────────────────────────────────────

    fun label(ctx: Context, text: String, color: Int = T2, size: Float = 12.5f): TextView =
        TextView(ctx).apply {
            this.text = text
            setTextColor(color)
            textSize = size
            typeface = MONO
            setPadding(0, dp(ctx, 3), 0, dp(ctx, 3))
        }

    fun mono(ctx: Context, text: String, color: Int = T1, size: Float = 13f): TextView =
        label(ctx, text, color, size)

    fun heading(ctx: Context, text: String): TextView =
        TextView(ctx).apply {
            this.text = text
            setTextColor(T1)
            textSize = 15f
            typeface = MONO
            setPadding(0, dp(ctx, 14), 0, dp(ctx, 6))
        }

    // ── Contenedores ─────────────────────────────────────────────────

    fun card(ctx: Context): LinearLayout =
        LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(ctx, 14), dp(ctx, 12), dp(ctx, 14), dp(ctx, 12))
            background = rounded(ctx, PANEL, LINE, 8)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = dp(ctx, 10) }
        }

    /**
     * Tarjeta de sección: barra de color + título + subtítulo opcional.
     * Devuelve la tarjeta para seguir añadiendo contenido.
     */
    fun section(ctx: Context, title: String, accent: Int = CY, sub: String? = null): LinearLayout {
        val c = card(ctx)
        val head = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        head.addView(View(ctx).apply {
            setBackgroundColor(accent)
            layoutParams = LinearLayout.LayoutParams(dp(ctx, 4), dp(ctx, 15))
                .also { it.rightMargin = dp(ctx, 9) }
        })
        head.addView(TextView(ctx).apply {
            text = title
            setTextColor(accent)
            textSize = 13.5f
            typeface = MONO
            letterSpacing = 0.06f
        })
        c.addView(head)
        if (sub != null) c.addView(label(ctx, sub, T3, 11f))
        return c
    }

    /** Tarjeta destacada (estado principal), con color de fondo tenue. */
    fun hero(ctx: Context, accent: Int): LinearLayout =
        LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(ctx, 16), dp(ctx, 16), dp(ctx, 16), dp(ctx, 16))
            background = rounded(ctx, alpha(accent, 0x14), accent, 10)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = dp(ctx, 12) }
        }

    fun divider(ctx: Context): View =
        View(ctx).apply {
            setBackgroundColor(LINE)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(ctx, 1)
            ).also { it.topMargin = dp(ctx, 12) }
        }

    fun space(ctx: Context, height: Int = 8): View =
        View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(ctx, height))
        }

    // ── Botones y controles ──────────────────────────────────────────

    fun button(
        ctx: Context,
        text: String,
        primary: Boolean = false,
        onClick: () -> Unit
    ): AppCompatButton =
        AppCompatButton(ctx).apply {
            this.text = text
            textSize = 12.5f
            typeface = MONO
            isAllCaps = false
            minHeight = 0
            minimumHeight = 0
            setTextColor(if (primary) 0xFF00140F.toInt() else CY)
            background = rounded(ctx, if (primary) CY else SURF, CY, 8)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = dp(ctx, 8)
            lp.rightMargin = dp(ctx, 6)
            layoutParams = lp
            setOnClickListener { onClick() }
        }

    fun row(ctx: Context, vararg views: View): LinearLayout =
        LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.START
            for (v in views) addView(v)
        }

    /** Etiqueta compacta con color (para severidad, estados, contadores). */
    fun chip(ctx: Context, text: String, color: Int): TextView =
        TextView(ctx).apply {
            this.text = text
            setTextColor(color)
            textSize = 11.5f
            typeface = MONO
            setPadding(dp(ctx, 8), dp(ctx, 4), dp(ctx, 8), dp(ctx, 4))
            background = rounded(ctx, alpha(color, 0x22), color, 20)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.rightMargin = dp(ctx, 6)
            lp.topMargin = dp(ctx, 4)
            layoutParams = lp
        }

    /** Barra de progreso fina (accesos concedidos, etc.). */
    fun bar(ctx: Context, done: Int, total: Int, accent: Int): LinearLayout {
        val wrap = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            background = rounded(ctx, 0xFF0A1A24.toInt(), LINE, 10)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(ctx, 8)
            ).also { it.topMargin = dp(ctx, 8) }
        }
        val t = if (total <= 0) 1 else total
        val d = done.coerceIn(0, t)
        if (d > 0) wrap.addView(View(ctx).apply {
            setBackgroundColor(accent)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, d.toFloat())
        })
        if (t - d > 0) wrap.addView(View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, (t - d).toFloat())
        })
        return wrap
    }

    /** Fila de acción ancha (título + explicación + flecha). */
    fun actionRow(
        ctx: Context,
        title: String,
        sub: String = "",
        accent: Int = CY,
        onClick: () -> Unit
    ): LinearLayout =
        LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(ctx, 12), dp(ctx, 11), dp(ctx, 12), dp(ctx, 11))
            background = rounded(ctx, SURF, alpha(accent, 0x66), 8)
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = dp(ctx, 8) }

            val col = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            col.addView(TextView(ctx).apply {
                text = title
                setTextColor(accent)
                textSize = 13f
                typeface = MONO
            })
            if (sub.isNotEmpty()) col.addView(TextView(ctx).apply {
                text = sub
                setTextColor(T3)
                textSize = 11f
                typeface = MONO
                setPadding(0, dp(ctx, 2), 0, 0)
            })
            addView(col)
            addView(TextView(ctx).apply {
                text = "›"
                setTextColor(accent)
                textSize = 20f
                typeface = MONO
                setPadding(dp(ctx, 8), 0, 0, 0)
            })
        }

    /** Mosaico tocable de módulo (2 por fila). */
    fun tile(
        ctx: Context,
        emoji: String,
        title: String,
        sub: String,
        accent: Int = CY,
        onClick: () -> Unit
    ): LinearLayout =
        LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(ctx, 12), dp(ctx, 12), dp(ctx, 12), dp(ctx, 12))
            background = rounded(ctx, SURF, LINE, 10)
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                .also { it.rightMargin = dp(ctx, 6); it.topMargin = dp(ctx, 6) }

            addView(TextView(ctx).apply {
                text = emoji
                textSize = 19f
            })
            addView(TextView(ctx).apply {
                text = title
                setTextColor(accent)
                textSize = 12.5f
                typeface = MONO
                setPadding(0, dp(ctx, 6), 0, 0)
            })
            if (sub.isNotEmpty()) addView(TextView(ctx).apply {
                text = sub
                setTextColor(T3)
                textSize = 10.5f
                typeface = MONO
                maxLines = 3
                setPadding(0, dp(ctx, 2), 0, 0)
            })
        }

    data class TileItem(
        val emoji: String,
        val title: String,
        val sub: String = "",
        val accent: Int = CY,
        val onClick: () -> Unit
    )

    /** Construye la rejilla de mosaicos (2 columnas). */
    fun tileGrid(ctx: Context, items: List<TileItem>): LinearLayout {
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        var i = 0
        while (i < items.size) {
            val r = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
            val a = items[i]
            r.addView(tile(ctx, a.emoji, a.title, a.sub, a.accent, a.onClick))
            if (i + 1 < items.size) {
                val b = items[i + 1]
                r.addView(tile(ctx, b.emoji, b.title, b.sub, b.accent, b.onClick))
            } else {
                r.addView(View(ctx).apply {
                    layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
                })
            }
            i += 2
            col.addView(r)
        }
        return col
    }

    // ── Utilidades de presentación ───────────────────────────────────

    fun statusDot(ctx: Context, ok: Boolean, text: String): TextView =
        label(ctx, (if (ok) "● " else "○ ") + text, if (ok) GRN else AMB, 12.5f)

    fun severityColor(sev: String): Int = when (sev) {
        "CRITICA", "ALTA" -> RED
        "MEDIA" -> AMB
        "BAJA" -> BLU
        else -> T3
    }

    fun bytes(b: Long): String = com.cyberagent.app.core.NetworkStatsReader.formatBytes(b)

    fun time(ts: Long): String =
        java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date(ts))
}
