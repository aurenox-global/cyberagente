package com.cyberagent.app.ui

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
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
 * Diseño «v3 — soft dark / menta»: superficies oscuras con tinte verdoso,
 * tarjetas redondeadas sobre un fondo con resplandor, acento menta (#3FE0A6),
 * píldoras, iconos vectoriales propios (ver [IconView]) y una barra de
 * navegación flotante. Mantiene la identidad de consola de defensa (datos en
 * monoespaciada) pero con acabado de app moderna.
 *
 * REGLA: nada de emoji como iconos de interfaz → usar [csIcon]/[iconBadge] con
 * un nombre de icono de [Icons].
 */
object Ui {

    // ── Paleta (soft dark + acento menta) ────────────────────────────
    const val BG = 0xFF070B0A.toInt()
    const val BG2 = 0xFF0A100E.toInt()
    const val PANEL = 0xFF141B19.toInt()
    const val SURF = 0xFF101614.toInt()
    const val SURF2 = 0xFF1B2420.toInt()
    const val LINE = 0x24FFFFFF.toInt()
    const val LINE_SOFT = 0x14FFFFFF.toInt()
    const val CY = 0xFF3FE0A6.toInt()
    const val CY_DIM = 0xFF2FB98A.toInt()
    const val GRN = 0xFF4ADE80.toInt()
    const val AMB = 0xFFFFC24D.toInt()
    const val RED = 0xFFFF5D6C.toInt()
    const val BLU = 0xFF5AA9FF.toInt()
    const val VIO = 0xFF9B8CFF.toInt()
    const val T1 = 0xFFF2FBF7.toInt()
    const val T2 = 0xFF9AA8A2.toInt()
    const val T3 = 0xFF5E6C67.toInt()

    /** Texto oscuro para colocar SOBRE un relleno de acento. */
    const val ON_ACCENT = 0xFF06110C.toInt()

    @JvmField
    val MONO: Typeface = Typeface.MONOSPACE

    @JvmField
    val SANS: Typeface = Typeface.create("sans-serif", Typeface.NORMAL)

    @JvmField
    val SANS_MED: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)

    fun dp(ctx: Context, v: Int): Int = (v * ctx.resources.displayMetrics.density).toInt()

    fun alpha(color: Int, a: Int): Int = (color and 0x00FFFFFF) or ((a and 0xFF) shl 24)

    fun rounded(ctx: Context, fill: Int, stroke: Int? = null, radius: Int = 8): GradientDrawable =
        GradientDrawable().apply {
            setColor(fill)
            if (stroke != null) setStroke(dp(ctx, 1), stroke)
            cornerRadius = dp(ctx, radius).toFloat()
        }

    fun backdrop(ctx: Context): Drawable {
        val base = ColorDrawable(BG)
        val glow = GradientDrawable().apply {
            gradientType = GradientDrawable.RADIAL_GRADIENT
            colors = intArrayOf(alpha(CY, 0x26), alpha(CY, 0x0A), 0x00000000)
            gradientRadius = dp(ctx, 460).toFloat()
            setGradientCenter(0.5f, 0.0f)
        }
        return LayerDrawable(arrayOf(base, glow))
    }

    // ── Iconos vectoriales (sustituyen a los emoji) ──────────────────

    /** Icono monocromo tintable. `name` = clave de [Icons]. */
    fun csIcon(ctx: Context, name: String, color: Int = T1, sizeDp: Int = 22, strokeDp: Float = 1.8f): IconView =
        IconView(ctx, name, color, strokeDp).apply {
            layoutParams = LinearLayout.LayoutParams(dp(ctx, sizeDp), dp(ctx, sizeDp))
        }

    /** Círculo con fondo de acento tenue y el icono dentro. */
    fun iconBadge(ctx: Context, icon: String, accent: Int = CY, sizeDp: Int = 40): FrameLayout {
        val box = FrameLayout(ctx).apply {
            background = rounded(ctx, alpha(accent, 0x1F), alpha(accent, 0x3A), sizeDp / 2)
            layoutParams = LinearLayout.LayoutParams(dp(ctx, sizeDp), dp(ctx, sizeDp))
        }
        val iv = csIcon(ctx, icon, accent, (sizeDp * 0.56f).toInt(), 1.9f)
        val lp = FrameLayout.LayoutParams(dp(ctx, (sizeDp * 0.56f).toInt()), dp(ctx, (sizeDp * 0.56f).toInt()))
        lp.gravity = Gravity.CENTER
        box.addView(iv, lp)
        return box
    }

    // ── Estructura de pantalla ───────────────────────────────────────

    fun statusBarHeight(ctx: Context): Int = systemDimen(ctx, "status_bar_height", 24)

    fun navBarHeight(ctx: Context): Int = systemDimen(ctx, "navigation_bar_height", 48)

    private fun systemDimen(ctx: Context, name: String, fallbackDp: Int): Int = try {
        val id = ctx.resources.getIdentifier(name, "dimen", "android")
        if (id > 0) ctx.resources.getDimensionPixelSize(id) else dp(ctx, fallbackDp)
    } catch (t: Throwable) {
        dp(ctx, fallbackDp)
    }

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

    fun screen(
        act: android.app.Activity,
        heading: String,
        sub: String? = null,
        nav: List<NavItem> = emptyList()
    ): LinearLayout {
        val scroll = ScrollView(act).apply {
            setBackgroundColor(0x00000000)
            isFillViewport = true
            clipToPadding = true
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        applySystemBarsPadding(scroll, dp(act, 10), if (nav.isEmpty()) dp(act, 28) else dp(act, 96))

        val col = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(act, 18), 0, dp(act, 18), 0)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        col.addView(pageHeader(act, heading, sub))
        scroll.addView(col)

        val root = FrameLayout(act).apply { background = backdrop(act) }
        root.addView(
            scroll,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        if (nav.isNotEmpty()) {
            val bar = bottomNav(act, nav)
            root.addView(
                bar,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                    bottomMargin = navBarHeight(act) + dp(act, 16)
                }
            )
        }
        act.setContentView(root)
        return col
    }

    private fun pageHeader(ctx: Context, text: String, sub: String?): LinearLayout {
        val wrap = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(ctx, 8), 0, dp(ctx, 10))
        }
        val rowHead = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        rowHead.addView(iconBadge(ctx, "shield", CY, 42))
        rowHead.addView(TextView(ctx).apply {
            this.text = text
            setTextColor(T1)
            textSize = 21f
            typeface = SANS_MED
            setPadding(dp(ctx, 12), 0, 0, 0)
        })
        wrap.addView(rowHead)
        if (sub != null) wrap.addView(TextView(ctx).apply {
            this.text = sub
            setTextColor(T3)
            textSize = 11.5f
            typeface = MONO
            setPadding(0, dp(ctx, 7), 0, 0)
        })
        return wrap
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
            textSize = 15.5f
            typeface = SANS_MED
            setPadding(0, dp(ctx, 14), 0, dp(ctx, 6))
        }

    // ── Contenedores ─────────────────────────────────────────────────

    fun card(ctx: Context): LinearLayout =
        LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(ctx, 16), dp(ctx, 14), dp(ctx, 16), dp(ctx, 14))
            background = rounded(ctx, PANEL, LINE, 18)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = dp(ctx, 12) }
        }

    fun section(ctx: Context, title: String, accent: Int = CY, sub: String? = null): LinearLayout {
        val c = card(ctx)
        val head = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        head.addView(View(ctx).apply {
            background = rounded(ctx, accent, null, 4)
            layoutParams = LinearLayout.LayoutParams(dp(ctx, 8), dp(ctx, 8))
                .also { it.rightMargin = dp(ctx, 10) }
        })
        head.addView(TextView(ctx).apply {
            text = title.uppercase()
            setTextColor(T2)
            textSize = 11.5f
            typeface = SANS_MED
            letterSpacing = 0.10f
        })
        c.addView(head)
        if (sub != null) c.addView(label(ctx, sub, T3, 11f))
        return c
    }

    fun hero(ctx: Context, accent: Int): LinearLayout =
        LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(ctx, 18), dp(ctx, 18), dp(ctx, 18), dp(ctx, 18))
            background = rounded(ctx, alpha(accent, 0x14), alpha(accent, 0x40), 22)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = dp(ctx, 12) }
        }

    /**
     * Tarjeta-métrica: icono circular + número grande + etiqueta. Es VERTICAL
     * (con una fila interna) para que se le puedan añadir filas debajo (chips,
     * notas…) sin que el hijo horizontal comprima el texto en vertical.
     */
    fun metricCard(
        ctx: Context,
        icon: String,
        value: String,
        unit: String,
        label: String,
        accent: Int = CY
    ): LinearLayout {
        val c = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(ctx, 16), dp(ctx, 16), dp(ctx, 16), dp(ctx, 16))
            background = rounded(ctx, alpha(accent, 0x16), alpha(accent, 0x3A), 22)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = dp(ctx, 12) }
        }
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(iconBadge(ctx, icon, accent, 46))
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(ctx, 14), 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val rowV = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
        }
        rowV.addView(TextView(ctx).apply {
            text = value
            setTextColor(T1)
            textSize = 27f
            typeface = SANS_MED
        })
        if (unit.isNotEmpty()) rowV.addView(TextView(ctx).apply {
            text = unit
            setTextColor(T2)
            textSize = 13f
            typeface = MONO
            setPadding(dp(ctx, 5), 0, 0, dp(ctx, 4))
        })
        col.addView(rowV)
        col.addView(TextView(ctx).apply {
            text = label
            setTextColor(T2)
            textSize = 12f
            typeface = MONO
            maxLines = 2
        })
        row.addView(col)
        c.addView(row)
        return c
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
            typeface = if (primary) SANS_MED else MONO
            isAllCaps = false
            minHeight = 0
            minimumHeight = 0
            setPadding(dp(ctx, 16), dp(ctx, 10), dp(ctx, 16), dp(ctx, 10))
            setTextColor(if (primary) ON_ACCENT else CY)
            background = if (primary) rounded(ctx, CY, null, 30) else rounded(ctx, SURF2, alpha(CY, 0x66), 30)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = dp(ctx, 8)
            lp.rightMargin = dp(ctx, 8)
            layoutParams = lp
            setOnClickListener { onClick() }
        }

    /**
     * Fila de controles que SALTA de línea si no caben (evita que los botones
     * partan su texto en vertical cuando la fila es estrecha).
     */
    fun row(ctx: Context, vararg views: View): FlowRow =
        FlowRow(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            for (v in views) addView(v)
        }

    fun chip(ctx: Context, text: String, color: Int, filled: Boolean = false): TextView =
        TextView(ctx).apply {
            this.text = text
            setTextColor(if (filled) ON_ACCENT else color)
            textSize = 11.5f
            typeface = MONO
            setPadding(dp(ctx, 12), dp(ctx, 6), dp(ctx, 12), dp(ctx, 6))
            background = if (filled) rounded(ctx, color, null, 30)
            else rounded(ctx, alpha(color, 0x1E), alpha(color, 0x44), 30)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.rightMargin = dp(ctx, 8)
            lp.topMargin = dp(ctx, 4)
            layoutParams = lp
            isClickable = false
        }

    fun bar(ctx: Context, done: Int, total: Int, accent: Int): LinearLayout {
        val wrap = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            background = rounded(ctx, 0xFF0E1613.toInt(), LINE, 12)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(ctx, 10)
            ).also { it.topMargin = dp(ctx, 10) }
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

    /** Fila de acción ancha con barra de acento (compatibilidad). */
    fun actionRow(
        ctx: Context,
        title: String,
        sub: String = "",
        accent: Int = CY,
        onClick: () -> Unit
    ): LinearLayout =
        actionRowBase(ctx, title, sub, accent, onClick) {
            View(ctx).apply {
                background = rounded(ctx, accent, null, 3)
                layoutParams = LinearLayout.LayoutParams(dp(ctx, 4), dp(ctx, 26))
                    .also { it.rightMargin = dp(ctx, 13) }
            }
        }

    /** Fila de acción con icono vectorial (recomendado). */
    fun actionRowIcon(
        ctx: Context,
        icon: String,
        title: String,
        sub: String = "",
        accent: Int = CY,
        onClick: () -> Unit
    ): LinearLayout =
        actionRowBase(ctx, title, sub, accent, onClick) {
            csIcon(ctx, icon, accent, 24, 1.9f)
        }

    private fun actionRowBase(
        ctx: Context,
        title: String,
        sub: String,
        accent: Int,
        onClick: () -> Unit,
        leading: () -> View
    ): LinearLayout =
        LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(ctx, 14), dp(ctx, 13), dp(ctx, 14), dp(ctx, 13))
            background = rounded(ctx, SURF2, LINE, 16)
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = dp(ctx, 10) }

            addView(leading())

            val col = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(ctx, 13), 0, 0, 0)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            col.addView(TextView(ctx).apply {
                text = title
                setTextColor(T1)
                textSize = 13.5f
                typeface = SANS_MED
            })
            if (sub.isNotEmpty()) col.addView(TextView(ctx).apply {
                text = sub
                setTextColor(T3)
                textSize = 11f
                typeface = MONO
                setPadding(0, dp(ctx, 3), 0, 0)
            })
            addView(col)
            addView(TextView(ctx).apply {
                text = "›"
                setTextColor(accent)
                textSize = 22f
                typeface = SANS
                setPadding(dp(ctx, 10), 0, 0, 0)
            })
        }

    /** Mosaico tocable de módulo (2 por fila). `icon` = clave de [Icons]. */
    fun tile(
        ctx: Context,
        icon: String,
        title: String,
        sub: String,
        accent: Int = CY,
        onClick: () -> Unit
    ): LinearLayout =
        LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(ctx, 14), dp(ctx, 14), dp(ctx, 14), dp(ctx, 14))
            background = rounded(ctx, PANEL, LINE, 18)
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                .also { it.rightMargin = dp(ctx, 7); it.topMargin = dp(ctx, 7) }

            addView(iconBadge(ctx, icon, accent, 42))
            addView(TextView(ctx).apply {
                text = title
                setTextColor(T1)
                textSize = 13.5f
                typeface = SANS_MED
                setPadding(0, dp(ctx, 9), 0, 0)
            })
            if (sub.isNotEmpty()) addView(TextView(ctx).apply {
                text = sub
                setTextColor(T3)
                textSize = 11f
                typeface = MONO
                maxLines = 3
                setPadding(0, dp(ctx, 3), 0, 0)
            })
        }

    data class TileItem(
        val icon: String,
        val title: String,
        val sub: String = "",
        val accent: Int = CY,
        val onClick: () -> Unit
    )

    fun tileGrid(ctx: Context, items: List<TileItem>): LinearLayout {
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        var i = 0
        while (i < items.size) {
            val r = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
            val a = items[i]
            r.addView(tile(ctx, a.icon, a.title, a.sub, a.accent, a.onClick))
            if (i + 1 < items.size) {
                val b = items[i + 1]
                r.addView(tile(ctx, b.icon, b.title, b.sub, b.accent, b.onClick))
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

    /** Tarjeta de estado con número grande, 2 por fila. */
    fun statTile(
        ctx: Context,
        value: String,
        label: String,
        sub: String = "",
        accent: Int = CY,
        onClick: () -> Unit
    ): LinearLayout =
        LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(ctx, 15), dp(ctx, 15), dp(ctx, 15), dp(ctx, 15))
            background = rounded(ctx, PANEL, LINE, 20)
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                .also { it.rightMargin = dp(ctx, 7); it.topMargin = dp(ctx, 7) }

            val rowV = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.BOTTOM
            }
            rowV.addView(TextView(ctx).apply {
                text = value
                setTextColor(T1)
                textSize = 25f
                typeface = SANS_MED
            })
            rowV.addView(TextView(ctx).apply {
                text = label
                setTextColor(T2)
                textSize = 11.5f
                typeface = MONO
                setPadding(dp(ctx, 6), 0, 0, dp(ctx, 4))
            })
            addView(rowV)
            if (sub.isNotEmpty()) addView(TextView(ctx).apply {
                text = sub
                setTextColor(T3)
                textSize = 11f
                typeface = MONO
                maxLines = 2
                setPadding(0, dp(ctx, 5), 0, 0)
            })
            addView(View(ctx).apply {
                background = rounded(ctx, accent, null, 6)
                layoutParams = LinearLayout.LayoutParams(dp(ctx, 28), dp(ctx, 6))
                    .also { it.topMargin = dp(ctx, 12) }
            })
        }

    // ── Pestañas (estilo navegador) ──────────────────────────────────

    /** Fondo de pestaña: esquinas superiores redondeadas; activa rellena. */
    private fun tabBg(ctx: Context, active: Boolean): GradientDrawable {
        val d = GradientDrawable()
        val r = dp(ctx, 13).toFloat()
        d.cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
        d.setColor(if (active) PANEL else 0x00000000)
        if (active) d.setStroke(dp(ctx, 1), alpha(CY, 0x55))
        return d
    }

    /**
     * Barra de pestañas de navegador: cada pestaña ocupa el mismo ancho; la
     * activa se rellena y lleva una línea de acento. Devuelve el contenedor
     * (pestañas + separador) para añadirlo a la pantalla.
     */
    fun tabStrip(
        ctx: Context,
        titles: List<String>,
        selected: Int,
        onSelect: (Int) -> Unit
    ): LinearLayout {
        val wrap = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = dp(ctx, 8) }
        }
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
        }
        titles.forEachIndexed { i, title ->
            val active = i == selected
            val tab = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = true
                setOnClickListener { onSelect(i) }
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            tab.addView(TextView(ctx).apply {
                text = title
                setTextColor(if (active) CY else T3)
                textSize = 12.5f
                typeface = if (active) SANS_MED else SANS
                gravity = Gravity.CENTER
                maxLines = 1
                setPadding(dp(ctx, 4), dp(ctx, 10), dp(ctx, 4), dp(ctx, 10))
                background = tabBg(ctx, active)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            })
            tab.addView(View(ctx).apply {
                setBackgroundColor(if (active) CY else 0x00000000)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(ctx, 2)
                )
            })
            row.addView(tab)
        }
        wrap.addView(row)
        wrap.addView(View(ctx).apply {
            setBackgroundColor(LINE)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(ctx, 1)
            )
        })
        return wrap
    }

    /**
     * Pantalla con pestañas FIJAS: la cabecera y la barra de pestañas no se
     * mueven; solo hace scroll el contenido entre las pestañas y el dock.
     * Devuelve las dos columnas: [headerCol] (fija, arriba) y [contentCol]
     * (la única que scrollea).
     */
    class TabbedScreen(val headerCol: LinearLayout, val contentCol: LinearLayout)

    fun tabbedScreen(
        act: android.app.Activity,
        heading: String,
        sub: String?,
        tabs: List<String>,
        selected: Int,
        onSelect: (Int) -> Unit,
        nav: List<NavItem> = emptyList()
    ): TabbedScreen {
        val root = FrameLayout(act).apply { background = backdrop(act) }

        val outer = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, statusBarHeight(act) + dp(act, 8), 0, 0)
        }

        val headerCol = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(act, 18), 0, dp(act, 18), 0)
        }
        headerCol.addView(pageHeader(act, heading, sub))
        outer.addView(headerCol)

        val tabHolder = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(act, 18), 0, dp(act, 18), 0)
        }
        tabHolder.addView(tabStrip(act, tabs, selected, onSelect))
        outer.addView(tabHolder)

        val scroll = ScrollView(act).apply {
            setBackgroundColor(0x00000000)
            isFillViewport = true
            clipToPadding = true
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val contentCol = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(act, 18), dp(act, 6), dp(act, 18), navBarHeight(act) + dp(act, 96))
        }
        scroll.addView(contentCol)
        outer.addView(
            scroll,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        )

        root.addView(
            outer,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        if (nav.isNotEmpty()) {
            val bar = bottomNav(act, nav)
            root.addView(
                bar,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                    bottomMargin = navBarHeight(act) + dp(act, 16)
                }
            )
        }

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val top = if (bars.top > 0) bars.top else statusBarHeight(act)
            val bottom = if (bars.bottom > 0) bars.bottom else navBarHeight(act)
            outer.setPadding(0, top + dp(act, 8), 0, 0)
            contentCol.setPadding(dp(act, 18), dp(act, 6), dp(act, 18), bottom + dp(act, 96))
            insets
        }
        ViewCompat.requestApplyInsets(root)

        act.setContentView(root)
        return TabbedScreen(headerCol, contentCol)
    }

    // ── Barra de navegación flotante ─────────────────────────────────

    data class NavItem(
        val icon: String,
        val label: String,
        val active: Boolean = false,
        val onClick: () -> Unit = {}
    )

    fun bottomNav(ctx: Context, items: List<NavItem>): LinearLayout {
        val bar = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(ctx, 10), dp(ctx, 8), dp(ctx, 10), dp(ctx, 8))
            background = rounded(ctx, alpha(SURF, 0xF2), LINE, 34)
            elevation = dp(ctx, 10).toFloat()
        }
        for (it0 in items) {
            val item = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                isClickable = true
                isFocusable = true
                setOnClickListener { it0.onClick() }
            }
            val box = FrameLayout(ctx).apply {
                background = if (it0.active)
                    rounded(ctx, CY, null, 20)
                else rounded(ctx, alpha(0xFFFFFFFF.toInt(), 0x0A), null, 20)
                layoutParams = LinearLayout.LayoutParams(dp(ctx, 40), dp(ctx, 40))
            }
            val iv = csIcon(ctx, it0.icon, if (it0.active) ON_ACCENT else T2, 21, 2.0f)
            val lp = FrameLayout.LayoutParams(dp(ctx, 21), dp(ctx, 21))
            lp.gravity = Gravity.CENTER
            box.addView(iv, lp)
            item.addView(box)
            if (it0.active) {
                item.addView(TextView(ctx).apply {
                    text = it0.label
                    setTextColor(T1)
                    textSize = 12f
                    typeface = SANS_MED
                    setPadding(dp(ctx, 8), 0, dp(ctx, 8), 0)
                })
            } else {
                item.setPadding(dp(ctx, 4), 0, dp(ctx, 4), 0)
            }
            bar.addView(item)
        }
        return bar
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
